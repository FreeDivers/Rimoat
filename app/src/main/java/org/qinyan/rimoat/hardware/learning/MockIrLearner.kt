package org.qinyan.rimoat.hardware.learning

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.UiTextException
import org.qinyan.rimoat.hardware.IrProtocolEncoder
import org.qinyan.rimoat.hardware.IrSignal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mock IR Learner for emulator, tests, and devices lacking physical IR learning hardware.
 *
 * Supports configurable simulation modes (NEC 38kHz, Sony, Timeout, Error) and manual triggers.
 */
class MockIrLearner : IrLearner {

    enum class SimulationMode {
        SUCCESS_NEC,
        SUCCESS_SONY,
        TIMEOUT,
        ERROR
    }

    override val type: LearnerType = LearnerType.MOCK

    private val _isAvailable = MutableStateFlow(true)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _state = MutableStateFlow<LearningState>(LearningState.Idle)
    override val state: StateFlow<LearningState> = _state.asStateFlow()

    private var simulationMode: SimulationMode = SimulationMode.SUCCESS_NEC
    private var simulatedDelayMs: Long = 1000L
    private val manualSignalRef = java.util.concurrent.atomic.AtomicReference<IrSignal.Raw?>(null)
    private val cancelled = AtomicBoolean(false)
    private val stateLock = Any()
    private var cancelSignal = CompletableDeferred<Unit>()

    fun setSimulationMode(mode: SimulationMode) {
        this.simulationMode = mode
    }

    fun setSimulatedDelayMs(delayMs: Long) {
        this.simulatedDelayMs = delayMs.coerceAtLeast(0L)
    }

    fun triggerManualSignal(signal: IrSignal.Raw) {
        manualSignalRef.set(signal)
    }

    override suspend fun startLearning(timeoutSeconds: Int): Result<IrSignal.Raw> {
        val totalSec = timeoutSeconds.coerceAtLeast(1)
        synchronized(stateLock) {
            cancelled.set(false)
            cancelSignal = CompletableDeferred()
            _state.value = LearningState.Listening(remainingSeconds = totalSec, totalSeconds = totalSec)
        }

        return withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + totalSec * 1000L
            val targetCompleteTime = System.currentTimeMillis() + simulatedDelayMs
            var lastRemaining = totalSec

            try {
                while (!cancelled.get() && System.currentTimeMillis() < deadline) {
                    val manual = manualSignalRef.getAndSet(null)
                    if (manual != null) {
                        return@withContext synchronized(stateLock) {
                            if (cancelled.getAndSet(false)) {
                                _state.value = LearningState.Idle
                                Result.failure(CancellationException("Mock learning cancelled by user"))
                            } else {
                                _state.value = LearningState.Captured(manual)
                                Result.success(manual)
                            }
                        }
                    }

                    val currentRemaining = ((deadline - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)
                    if (currentRemaining != lastRemaining) {
                        lastRemaining = currentRemaining
                        synchronized(stateLock) {
                            if (!cancelled.get()) {
                                _state.value = LearningState.Listening(remainingSeconds = currentRemaining, totalSeconds = totalSec)
                            }
                        }
                    }

                    if (System.currentTimeMillis() >= targetCompleteTime) {
                        return@withContext synchronized(stateLock) {
                            if (cancelled.getAndSet(false)) {
                                _state.value = LearningState.Idle
                                Result.failure(CancellationException("Mock learning cancelled by user"))
                            } else {
                                when (simulationMode) {
                                    SimulationMode.SUCCESS_NEC -> {
                                        val signal = getDummyNecSignal()
                                        _state.value = LearningState.Captured(signal)
                                        Result.success(signal)
                                    }
                                    SimulationMode.SUCCESS_SONY -> {
                                        val signal = getDummySonySignal()
                                        _state.value = LearningState.Captured(signal)
                                        Result.success(signal)
                                    }
                                    SimulationMode.ERROR -> {
                                        val err = AppStrings.get(R.string.msg_mock_failure, "Mock hardware simulation failure")
                                        _state.value = LearningState.Error(err)
                                        Result.failure(UiTextException(UiText.Resource(R.string.msg_mock_failure)))
                                    }
                                    SimulationMode.TIMEOUT -> {
                                        _state.value = LearningState.Timeout
                                        Result.failure(LearningTimeoutException("Mock learning timed out after $totalSec seconds"))
                                    }
                                }
                            }
                        }
                    }

                    withTimeoutOrNull(50) {
                        cancelSignal.await()
                    }
                }

                synchronized(stateLock) {
                    if (cancelled.getAndSet(false)) {
                        _state.value = LearningState.Idle
                        Result.failure(CancellationException("Mock learning cancelled by user"))
                    } else {
                        _state.value = LearningState.Timeout
                        Result.failure(LearningTimeoutException("Mock learning timed out after $totalSec seconds"))
                    }
                }
            } catch (e: CancellationException) {
                synchronized(stateLock) {
                    cancelled.set(false)
                    _state.value = LearningState.Idle
                }
                Result.failure(e)
            }
        }
    }

    override fun cancel() {
        synchronized(stateLock) {
            if (_state.value !is LearningState.Idle) {
                cancelled.set(true)
                _state.value = LearningState.Idle
                cancelSignal.complete(Unit)
            }
        }
    }

    companion object {
        fun getDummyNecSignal(): IrSignal.Raw {
            // Standard NEC Power code: Address 0x00FF, Command 0x45
            val nec = IrSignal.Nec(address = 0x00FF, command = 0x45)
            val encoded = IrProtocolEncoder.encodeNec(nec)
            val sanitized = IrSignalSanitizer.sanitize(encoded.frequencyHz, encoded.patternMicros)
            return sanitized.getOrThrow()
        }

        fun getDummySonySignal(): IrSignal.Raw {
            // Sony 12-bit: 40000Hz, Leader 2400 mark, 600 space, 12 bits data (e.g. 100000100000)
            val pulses = mutableListOf(2400, 600)
            val dataBits = listOf(1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0)
            for (bit in dataBits) {
                val mark = if (bit == 1) 1200 else 600
                pulses.add(mark)
                pulses.add(600)
            }
            val sanitized = IrSignalSanitizer.sanitize(40000, pulses.toIntArray())
            return sanitized.getOrThrow()
        }
    }
}
