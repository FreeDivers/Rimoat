package org.qinyan.rimoat.hardware.usb

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import org.qinyan.rimoat.hardware.IrProtocolEncoder
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.IrTransmitter
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.hardware.learning.UsbDeviceFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.abs

/** Identifies the firmware on each newly opened USB connection before formatting IR data. */
class ElkSmartUsbTransmitter(
    context: Context,
    refreshImmediately: Boolean = true
) : IrTransmitter {
    private val usbManager by lazy { context.getSystemService(Context.USB_SERVICE) as? UsbManager }
    override val type = TransmitterType.USB_ELKSMART
    private val _isReady = MutableStateFlow(false)
    override val isReady = _isReady.asStateFlow()
    private val mutex = Mutex()

    init {
        if (refreshImmediately) refreshState()
    }

    fun findDevice(devicesSnapshot: Collection<UsbDevice>? = null): UsbDevice? {
        val manager = usbManager ?: return null
        val candidates = (devicesSnapshot ?: manager.deviceList.values).filter { UsbDeviceFilter.isElkSmart(it) }
        return candidates.firstOrNull { manager.hasPermission(it) } ?: candidates.firstOrNull()
    }

    fun refreshState(devicesSnapshot: Collection<UsbDevice>? = null) {
        _isReady.value = runCatching { findDevice(devicesSnapshot)?.let { usbManager?.hasPermission(it) } == true }.getOrDefault(false)
    }

    override suspend fun transmit(signal: IrSignal): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val usb = usbManager ?: error(AppStrings.get(R.string.msg_usb_manager_unavailable, "UsbManager 不可用"))
                val device = findDevice() ?: error(AppStrings.get(R.string.msg_elksmart_missing, "未找到小蛮腰 USB 设备"))
                check(usb.hasPermission(device)) { AppStrings.get(R.string.msg_usb_permission_missing, "未获得 USB 设备使用权限") }
                val raw = when (signal) {
                    is IrSignal.Raw -> signal.copy(patternMicros = signal.patternMicros.map(::abs).toIntArray())
                    is IrSignal.Nec -> {
                        require(signal.address in 0..65535 && signal.command in 0..255) {
                            AppStrings.get(R.string.msg_nec_parameters_range)
                        }
                        IrProtocolEncoder.encodeNec(signal)
                    }
                }
                // Validate before opening the device, without assuming its firmware subtype.
                ElkSmartProtocolFormatter.buildPayload(raw.frequencyHz, raw.patternMicros)
                var lastError: Exception = IOException(AppStrings.get(R.string.msg_usb_endpoints_missing, "未找到匹配的 USB 输入/输出端点"))
                for (i in 0 until device.interfaceCount) {
                    val intf = device.getInterface(i)
                    for (transferType in listOf(UsbConstants.USB_ENDPOINT_XFER_BULK, UsbConstants.USB_ENDPOINT_XFER_INT)) {
                        for (pair in usbEndpointPairs(intf, transferType)) {
                            val out = pair.outEp
                            val input = pair.inEp
                            val connection = usb.openDevice(device) ?: throw IOException(AppStrings.get(R.string.msg_elksmart_open_failed, "无法打开小蛮腰 USB 连接"))
                            var claimed = false
                            try {
                                claimed = connection.claimInterface(intf, true)
                                if (!claimed) { lastError = IOException(AppStrings.get(R.string.msg_elksmart_claim_failed, "无法认领小蛮腰 USB 接口")); continue }
                                val session = ElkSmartSession(object : UsbBulkChannel {
                                    override fun read(buffer: ByteArray, timeoutMs: Int) =
                                        connection.bulkTransfer(input, buffer, buffer.size, timeoutMs)
                                    override fun write(data: ByteArray, timeoutMs: Int) =
                                        connection.bulkTransfer(out, data, data.size, timeoutMs)
                                })
                                val subtype = try { session.identify() }
                                    catch (e: IOException) { lastError = e; continue }
                                session.transmit(raw, subtype)
                                return@withLock Result.success(Unit)
                            } finally {
                                try { if (claimed) connection.releaseInterface(intf) }
                                finally { connection.close() }
                            }
                        }
                    }
                }
                Result.failure(lastError)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
            finally { refreshState() }
        }
    }
}
