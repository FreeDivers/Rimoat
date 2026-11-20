package org.qinyan.rimoat.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Mock IR transmitter for development and testing on emulators or devices without hardware.
 */
class MockIrTransmitter(
    initialReady: Boolean = true
) : IrTransmitter {

    override val type: TransmitterType = TransmitterType.MOCK

    private val _isReady = MutableStateFlow(initialReady)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _lastSignal = MutableStateFlow<IrSignal?>(null)
    val lastSignal: StateFlow<IrSignal?> = _lastSignal.asStateFlow()

    private val _transmitCount = MutableStateFlow(0)
    val transmitCount: StateFlow<Int> = _transmitCount.asStateFlow()

    var simulateDelayMs: Long = 40L
    var shouldFail: Boolean = false
    var failureReason: String = "Simulated hardware error"

    fun setReady(ready: Boolean) {
        _isReady.value = ready
    }

    override suspend fun transmit(signal: IrSignal): Result<Unit> {
        if (!_isReady.value) {
            return Result.failure(IllegalStateException("MockIrTransmitter is not ready"))
        }

        if (simulateDelayMs > 0) {
            delay(simulateDelayMs)
        }

        if (shouldFail) {
            return Result.failure(RuntimeException(failureReason))
        }

        val rawSignal = when (signal) {
            is IrSignal.Raw -> signal
            is IrSignal.Nec -> IrProtocolEncoder.encodeNec(signal)
        }

        val validationResult = BuiltInIrTransmitter.sanitizeAndValidate(rawSignal.frequencyHz, rawSignal.patternMicros)
        if (validationResult.isFailure) {
            val err = validationResult.exceptionOrNull() ?: IllegalArgumentException("Invalid pulse pattern")
            try {
                android.util.Log.w("MockIrTransmitter", "Simulated transmission rejected: ${err.message}")
            } catch (_: Throwable) {}
            return Result.failure(err)
        }

        val (freq, pattern) = validationResult.getOrThrow()
        try {
            android.util.Log.d("MockIrTransmitter", "Simulated transmission: freq=$freq Hz, pulses=${pattern.size}")
        } catch (_: Throwable) {}

        _lastSignal.value = signal
        _transmitCount.update { it + 1 }
        return Result.success(Unit)
    }

    fun reset() {
        _lastSignal.value = null
        _transmitCount.update { 0 }
    }
}

