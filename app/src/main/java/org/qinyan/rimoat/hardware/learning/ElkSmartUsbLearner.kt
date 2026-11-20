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
 * USB Infrared Receiver and Learner backend for ElkSmart USB IR dongles (VID 0x045C).
 */
class ElkSmartUsbLearner private constructor(
    val device: UsbDevice,
    private val connection: UsbDeviceConnection,
    private val claimedInterface: UsbInterface,
    private val outEndpoint: UsbEndpoint,
    private val inEndpoint: UsbEndpoint
) : IrLearner, AutoCloseable {

    companion object {
        private const val TAG = "ElkSmartUsbLearner"
        private const val AUTHORIZE_RETRY_COUNT = 3

        fun open(usb: UsbManager, device: UsbDevice): ElkSmartUsbLearner? {
            if (!UsbDeviceFilter.isElkSmart(device)) return null
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
                return ElkSmartUsbLearner(
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

    override val type: LearnerType = LearnerType.USB_ELKSMART

    private val _isAvailable = MutableStateFlow(true)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _state = MutableStateFlow<LearningState>(LearningState.Idle)
    override val state: StateFlow<LearningState> = _state.asStateFlow()

    private val cancelled = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    override suspend fun startLearning(timeoutSeconds: Int): Result<IrSignal.Raw> = withContext(Dispatchers.IO) {
        if (closed.get() || !_isAvailable.value) {
            val err = AppStrings.get(R.string.msg_elksmart_closed, "ElkSmart USB device is not available or closed")
            _state.value = LearningState.Error(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        cancelled.set(false)
        val totalSec = timeoutSeconds.coerceAtLeast(1)
        _state.value = LearningState.Listening(remainingSeconds = totalSec, totalSeconds = totalSec)

        val deadline = System.currentTimeMillis() + totalSec * 1000L

        try {
            // 1. Flush stale input packets
            flushInput(250)

            // 2. Perform 4-byte authorization handshake
            if (!authorize(deadline)) {
                val err = if (closed.get() || !_isAvailable.value) {
                    AppStrings.get(R.string.msg_usb_disconnected, "USB 设备已断开")
                } else {
                    AppStrings.get(R.string.msg_elksmart_handshake_failed, "ElkSmart USB authorization handshake failed")
                }
                _state.value = LearningState.Error(err)
                return@withContext Result.failure(IOException(err))
            }

            // 3. Send START_LEARN command (0xFE 0xFE 0xFE 0xFE)
            if (!sendControl(ElkSmartProtocol.START_LEARN)) {
                val err = if (closed.get() || !_isAvailable.value) {
                    AppStrings.get(R.string.msg_usb_disconnected, "USB 设备已断开")
                } else {
                    AppStrings.get(R.string.msg_elksmart_learn_start_failed, "Failed to send START_LEARN command to ElkSmart")
                }
                _state.value = LearningState.Error(err)
                return@withContext Result.failure(IOException(err))
            }

            var lastRemaining = totalSec
            val buf = ByteArray(4096)

            while (!cancelled.get() && !closed.get() && System.currentTimeMillis() < deadline) {
                currentCoroutineContext().ensureActive()
                val currentRemaining = ((deadline - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)
                if (currentRemaining != lastRemaining) {
                    lastRemaining = currentRemaining
                    _state.value = LearningState.Listening(remainingSeconds = currentRemaining, totalSeconds = totalSec)
                }

                val readRc = connection.bulkTransfer(inEndpoint, buf, buf.size, 100)
                if (readRc > 0) {
                    val packet = buf.copyOf(readRc)
                    if (ElkSmartProtocol.isLearnResponse(packet)) {
                        val signal = assembleAndDecodePayload(packet, deadline)
                        if (signal != null) {
                            _state.value = LearningState.Captured(signal)
                            return@withContext Result.success(signal)
                        }
                    }
                } else {
                    delay(10)
                }
                yield()
            }

            if (closed.get() || !_isAvailable.value) {
                val err = AppStrings.get(R.string.msg_usb_disconnected, "USB 设备已断开")
                _state.value = LearningState.Error(err)
                return@withContext Result.failure(IOException(err))
            }

            if (cancelled.get()) {
                _state.value = LearningState.Idle
                return@withContext Result.failure(Exception("IR learning cancelled by user"))
            }

            _state.value = LearningState.Timeout
            return@withContext Result.failure(Exception("IR learning timed out after $totalSec seconds"))
        } finally {
            // Restore hardware only when connection is still open: Send STOP_LEARN command (0xFD 0xFD 0xFD 0xFD)
            if (!closed.get()) {
                try {
                    sendControl(ElkSmartProtocol.STOP_LEARN)
                } catch (t: Throwable) {
                    Log.w(TAG, "Error stopping ElkSmart learning: ${t.message}")
                }
            }
        }
    }

    private suspend fun authorize(sessionDeadline: Long): Boolean {
        val buf = ByteArray(64)
        repeat(AUTHORIZE_RETRY_COUNT) { attempt ->
            if (cancelled.get() || closed.get() || System.currentTimeMillis() >= sessionDeadline) return false
            if (!sendControl(ElkSmartProtocol.AUTHORIZE)) return false

            val attemptDeadline = minOf(sessionDeadline, System.currentTimeMillis() + 500L)
            while (!cancelled.get() && !closed.get() && System.currentTimeMillis() < attemptDeadline) {
                currentCoroutineContext().ensureActive()
                val rc = connection.bulkTransfer(inEndpoint, buf, buf.size, 100)
                yield()
                if (rc == 6 && ElkSmartProtocol.isAuthorizeResponse(buf.copyOf(rc))) {
                    Log.i(TAG, "ElkSmart authorized on attempt ${attempt + 1}")
                    return sendControl(ElkSmartProtocol.AUTHORIZE_ACK)
                }
            }
            flushInput(150)
        }
        return false
    }

    private suspend fun flushInput(timeoutMs: Long) {
        val buf = ByteArray(maxOf(inEndpoint.maxPacketSize, 64))
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!cancelled.get() && !closed.get() && System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            val rc = connection.bulkTransfer(inEndpoint, buf, buf.size, 20)
            if (rc <= 0) break
            yield()
        }
    }

    private fun sendControl(bytes: ByteArray): Boolean {
        val rc = try {
            connection.bulkTransfer(outEndpoint, bytes, bytes.size, 300)
        } catch (t: Throwable) {
            Log.w(TAG, "sendControl failed: ${t.message}")
            -1
        }
        return rc == bytes.size
    }

    private suspend fun assembleAndDecodePayload(firstPacket: ByteArray, deadline: Long): IrSignal.Raw? {
        val expected = ElkSmartProtocol.getExpectedPayloadLength(firstPacket)
        if (expected <= 0) return null

        val payload = ArrayList<Byte>(expected)
        for (i in 6 until firstPacket.size) {
            payload.add(firstPacket[i])
        }

        val buf = ByteArray(4096)
        while (payload.size < expected && !cancelled.get() && !closed.get() && System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            val rc = connection.bulkTransfer(inEndpoint, buf, buf.size, 200)
            if (rc > 0) {
                for (i in 0 until rc) {
                    payload.add(buf[i])
                    if (payload.size == expected) break
                }
            } else {
                break
            }
        }

        if (payload.size != expected) {
            Log.w(TAG, "Incomplete ElkSmart payload: expected=$expected actual=${payload.size}")
            return null
        }

        val opaque = ElkSmartProtocol.decodeLearnPayload(payload) ?: return null
        val rawPattern = ElkSmartProtocol.decodeOpaqueFrameToPattern(opaque)
        if (rawPattern.isEmpty()) return null

        val sanitizeResult = IrSignalSanitizer.sanitize(38000, rawPattern)
        return sanitizeResult.getOrNull()
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
