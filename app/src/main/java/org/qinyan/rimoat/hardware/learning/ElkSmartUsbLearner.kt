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

        /** Called on Dispatchers.IO while the learning session owns USB access. */
        suspend fun open(usb: UsbManager, device: UsbDevice): ElkSmartUsbLearner? {
            if (!UsbDeviceFilter.isElkSmart(device)) return null
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                for (transferType in listOf(UsbConstants.USB_ENDPOINT_XFER_BULK, UsbConstants.USB_ENDPOINT_XFER_INT)) {
                    for (pair in usbEndpointPairs(intf, transferType)) {
                        currentCoroutineContext().ensureActive()
                        val conn = usb.openDevice(device) ?: return null
                        var claimed = false
                        var selected = false
                        try {
                            claimed = conn.claimInterface(intf, true)
                            if (!claimed) continue
                            val learner = ElkSmartUsbLearner(
                                device = device,
                                connection = conn,
                                claimedInterface = intf,
                                outEndpoint = pair.outEp,
                                inEndpoint = pair.inEp
                            )
                            try {
                                learner.flushInput(250)
                                // Learning uses the FC response prefix, independent of transmit encoding subtype.
                                if (!learner.sendControl(ElkSmartProtocol.AUTHORIZE) ||
                                    !learner.awaitAuthorizationResponse(System.currentTimeMillis() + 500L)
                                ) continue
                            } catch (_: IOException) {
                                continue
                            }
                            learner.authorizationPending.set(true)
                            currentCoroutineContext().ensureActive()
                            selected = true
                            return learner
                        } finally {
                            if (!selected) {
                                if (claimed) runCatching { conn.releaseInterface(intf) }
                                runCatching { conn.close() }
                            }
                        }
                    }
                }
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
    private val authorizationPending = AtomicBoolean(false)

    override suspend fun startLearning(timeoutSeconds: Int): Result<IrSignal.Raw> = withContext(Dispatchers.IO) {
        if (closed.get() || !_isAvailable.value) {
            val err = AppStrings.get(R.string.msg_elksmart_closed)
            _state.value = LearningState.Error(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        cancelled.set(false)
        val totalSec = timeoutSeconds.coerceAtLeast(1)
        _state.value = LearningState.Listening(remainingSeconds = totalSec, totalSeconds = totalSec)

        val deadline = System.currentTimeMillis() + totalSec * 1000L

        try {
            // Complete the opening FC exchange with FA once; later learning attempts need a fresh exchange.
            val authorized = if (authorizationPending.getAndSet(false)) {
                sendControl(ElkSmartProtocol.AUTHORIZE_ACK)
            } else {
                flushInput(250)
                authorize(deadline)
            }
            if (!authorized) {
                val err = if (closed.get() || !_isAvailable.value) {
                    AppStrings.get(R.string.msg_usb_disconnected)
                } else {
                    AppStrings.get(R.string.msg_elksmart_handshake_failed)
                }
                _state.value = LearningState.Error(err)
                return@withContext Result.failure(IOException(err))
            }

            // Send START_LEARN command (0xFE 0xFE 0xFE 0xFE)
            if (!sendControl(ElkSmartProtocol.START_LEARN)) {
                val err = if (closed.get() || !_isAvailable.value) {
                    AppStrings.get(R.string.msg_usb_disconnected)
                } else {
                    AppStrings.get(R.string.msg_elksmart_learn_start_failed)
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
        repeat(AUTHORIZE_RETRY_COUNT) { attempt ->
            if (cancelled.get() || closed.get() || System.currentTimeMillis() >= sessionDeadline) return false
            if (!sendControl(ElkSmartProtocol.AUTHORIZE)) return false
            if (awaitAuthorizationResponse(sessionDeadline)) {
                Log.i(TAG, "ElkSmart authorized on attempt ${attempt + 1}")
                return sendControl(ElkSmartProtocol.AUTHORIZE_ACK)
            }
            flushInput(150)
        }
        return false
    }

    private suspend fun awaitAuthorizationResponse(sessionDeadline: Long): Boolean {
        val buf = ByteArray(64)
        // Accumulate the complete response header across reads; discard USB packet padding.
        val response = ByteArray(6)
        var received = 0
        val attemptDeadline = minOf(sessionDeadline, System.currentTimeMillis() + 500L)
        while (!cancelled.get() && !closed.get() && System.currentTimeMillis() < attemptDeadline) {
            currentCoroutineContext().ensureActive()
            val rc = connection.bulkTransfer(inEndpoint, buf, buf.size, 100)
            yield()
            if (rc > 0) {
                val count = minOf(rc, response.size - received)
                buf.copyInto(response, received, 0, count)
                received += count
                if (received == response.size) {
                    if (ElkSmartProtocol.isAuthorizeResponse(response)) return true
                    received = 0
                }
            }
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
        for (i in 6 until minOf(firstPacket.size, 6 + expected)) {
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

        // 学习响应只提供载荷长度和 16 µs 脉冲时序，不提供实测载波频率，因此使用 38 kHz 默认值。
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
