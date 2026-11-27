package org.qinyan.rimoat.hardware.learning

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.hardware.usb.usbEndpointPairs
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import org.qinyan.rimoat.hardware.IrSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * USB infrared receiver and learner backend for compatible CP210x-based dongles.
 */
class TiqiaaUsbLearner private constructor(
    val device: UsbDevice,
    private val connection: UsbDeviceConnection,
    private val claimedInterface: UsbInterface,
    private val outEndpoint: UsbEndpoint,
    private val inEndpoint: UsbEndpoint
) : IrLearner, AutoCloseable {

    companion object {
        private const val TAG = "TiqiaaUsbLearner"

        fun open(usb: UsbManager, device: UsbDevice): TiqiaaUsbLearner? {
            if (!UsbDeviceFilter.isTiqiaa(device)) return null
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                val pair = usbEndpointPairs(intf).firstOrNull() ?: continue
                val conn = usb.openDevice(device) ?: return null
                if (!conn.claimInterface(intf, true)) {
                    try {
                        conn.close()
                    } catch (_: Throwable) {
                    }
                    continue
                }
                return TiqiaaUsbLearner(
                    device = device,
                    connection = conn,
                    claimedInterface = intf,
                    outEndpoint = pair.outEp,
                    inEndpoint = pair.inEp
                )
            }
            return null
        }

    }

    override val type: LearnerType = LearnerType.USB_TIQIAA

    private val _isAvailable = MutableStateFlow(true)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _state = MutableStateFlow<LearningState>(LearningState.Idle)
    override val state: StateFlow<LearningState> = _state.asStateFlow()

    private val cancelled = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private var seq: Byte = 0
    private var envelopeSeq: Byte = 1

    private fun nextSeq(): Byte {
        seq = ((seq + 1) and 0xFF).toByte()
        if (seq == 0.toByte()) seq = 1
        return seq
    }

    private fun nextEnvelopeSeq(): Byte {
        envelopeSeq = if (envelopeSeq < 0x0F) ((envelopeSeq + 1).toByte()) else 0x01
        return envelopeSeq
    }

    override suspend fun startLearning(timeoutSeconds: Int): Result<IrSignal.Raw> = withContext(Dispatchers.IO) {
        if (closed.get() || !_isAvailable.value) {
            val err = AppStrings.get(R.string.msg_tiqiaa_closed)
            _state.value = LearningState.Error(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        cancelled.set(false)
        val totalSec = timeoutSeconds.coerceAtLeast(1)
        _state.value = LearningState.Listening(remainingSeconds = totalSec, totalSeconds = totalSec)

        try {
            val staleBuffer = ByteArray(maxOf(inEndpoint.maxPacketSize, 64))
            var drained = false
            repeat(8) {
                currentCoroutineContext().ensureActive()
                if (!drained) drained = connection.bulkTransfer(inEndpoint, staleBuffer, staleBuffer.size, 10) <= 0
            }
            if (!drained) {
                val error = AppStrings.get(R.string.msg_tview_stale_data)
                _state.value = LearningState.Error(error)
                return@withContext Result.failure(IOException(error))
            }

            // 1. Send Mode 2 ('R' = Learn mode)
            val modeCmd = TiqiaaProtocol.buildModeCommand(2, nextSeq())
            val wrappedModeCmd = TiqiaaProtocol.wrapPayload(modeCmd, nextEnvelopeSeq())
            for (chunk in wrappedModeCmd) {
                currentCoroutineContext().ensureActive()
                val rc = connection.bulkTransfer(outEndpoint, chunk, chunk.size, 400)
                if (rc != chunk.size) {
                    val err = if (closed.get() || !_isAvailable.value) {
                        AppStrings.get(R.string.msg_usb_disconnected)
                    } else {
                        AppStrings.get(R.string.msg_tiqiaa_learn_start_failed)
                    }
                    _state.value = LearningState.Error(err)
                    return@withContext Result.failure(IOException(err))
                }
            }

            val assembler = TiqiaaProtocol.InboundPacketAssembler()
            val readBuf = ByteArray(64)
            val deadline = System.currentTimeMillis() + totalSec * 1000L
            var lastRemaining = totalSec

            while (!cancelled.get() && !closed.get() && System.currentTimeMillis() < deadline) {
                currentCoroutineContext().ensureActive()
                val currentRemaining = ((deadline - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)
                if (currentRemaining != lastRemaining) {
                    lastRemaining = currentRemaining
                    _state.value = LearningState.Listening(remainingSeconds = currentRemaining, totalSeconds = totalSec)
                }

                val readRc = connection.bulkTransfer(inEndpoint, readBuf, readBuf.size, 100)
                if (readRc > 0) {
                    val completeFrame = assembler.processPacket(readBuf, readRc)
                    if (completeFrame != null) {
                        val parsed = TiqiaaProtocol.parseFrame(completeFrame)
                        if (parsed != null && parsed.type == 5) {
                            val pattern = TiqiaaProtocol.decodeTimingPayload(parsed.payload)
                            if (pattern.isNotEmpty()) {
                                // 学习响应只提供 16 µs 脉冲时序，不提供实测载波频率，因此使用 38 kHz 默认值。
                                val sanitizeResult = IrSignalSanitizer.sanitize(38000, pattern)
                                if (sanitizeResult.isSuccess) {
                                    val signal = sanitizeResult.getOrThrow()
                                    _state.value = LearningState.Captured(signal)
                                    return@withContext Result.success(signal)
                                }
                            }
                        }
                    }
                } else {
                    delay(10)
                }
                yield()
            }

            if (closed.get() || !_isAvailable.value) {
                val err = AppStrings.get(R.string.msg_usb_disconnected)
                _state.value = LearningState.Error(err)
                return@withContext Result.failure(IOException(err))
            }

            if (cancelled.get()) {
                _state.value = LearningState.Idle
                return@withContext Result.failure(Exception("IR learning cancelled by user"))
            }

            _state.value = LearningState.Timeout
            return@withContext Result.failure(LearningTimeoutException("IR learning timed out after $totalSec seconds"))
        } finally {
            // Restore hardware only when the USB connection has not been detached/closed.
            if (!closed.get()) {
                try {
                    val cancelCmd = TiqiaaProtocol.buildModeCommand(6, nextSeq())
                    for (chunk in TiqiaaProtocol.wrapPayload(cancelCmd, nextEnvelopeSeq())) {
                        connection.bulkTransfer(outEndpoint, chunk, chunk.size, 300)
                    }
                    val idleCmd = TiqiaaProtocol.buildModeCommand(0, nextSeq())
                    for (chunk in TiqiaaProtocol.wrapPayload(idleCmd, nextEnvelopeSeq())) {
                        connection.bulkTransfer(outEndpoint, chunk, chunk.size, 300)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Error restoring USB IR dongle state: ${t.message}")
                }
            }
        }
    }

    override fun cancel() {
        cancelled.set(true)
        if (!closed.get()) _state.value = LearningState.Idle
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        _isAvailable.value = false
        try {
            connection.releaseInterface(claimedInterface)
        } catch (_: Throwable) {
        }
        try {
            connection.close()
        } catch (_: Throwable) {
        }
    }
}
