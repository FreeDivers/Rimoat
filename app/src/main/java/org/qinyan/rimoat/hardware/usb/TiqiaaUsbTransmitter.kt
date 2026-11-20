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

/** USB transport validates complete writes and the matching device completion response. */
class TiqiaaUsbTransmitter(
    context: Context,
    refreshImmediately: Boolean = true
) : IrTransmitter {
    private val usbManager by lazy { context.getSystemService(Context.USB_SERVICE) as? UsbManager }
    override val type = TransmitterType.USB_TIQIAA
    private val _isReady = MutableStateFlow(false)
    override val isReady = _isReady.asStateFlow()
    private val mutex = Mutex()

    init {
        if (refreshImmediately) refreshState()
    }

    fun findDevice(devicesSnapshot: Collection<UsbDevice>? = null): UsbDevice? {
        val usb = usbManager ?: return null
        val devices = (devicesSnapshot ?: usb.deviceList.values).filter { UsbDeviceFilter.isTiqiaa(it) }
        return devices.firstOrNull { usb.hasPermission(it) } ?: devices.firstOrNull()
    }

    fun refreshState(devicesSnapshot: Collection<UsbDevice>? = null) {
        _isReady.value = runCatching { findDevice(devicesSnapshot)?.let { usbManager?.hasPermission(it) } == true }.getOrDefault(false)
    }

    override suspend fun transmit(signal: IrSignal): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val usb = usbManager ?: error(AppStrings.get(R.string.msg_usb_manager_unavailable, "UsbManager 不可用"))
                val device = findDevice() ?: error(AppStrings.get(R.string.msg_tiqiaa_missing, "未找到遥控精灵 USB 设备"))
                check(usb.hasPermission(device)) { AppStrings.get(R.string.msg_usb_permission_missing, "未获得 USB 设备使用权限") }
                val raw = when (signal) {
                    is IrSignal.Raw -> signal
                    is IrSignal.Nec -> {
                        require(signal.address in 0..65535 && signal.command in 0..255) { AppStrings.get(R.string.msg_nec_parameters_range, "NEC 参数超出范围") }
                        IrProtocolEncoder.encodeNec(signal)
                    }
                }
                // Validate input before opening a hardware connection.
                TiqiaaProtocolFormatter.buildTransmissionPayload(raw.frequencyHz, raw.patternMicros, 1)
                for (i in 0 until device.interfaceCount) {
                    val intf = device.getInterface(i)
                    val pair = usbEndpointPairs(intf).firstOrNull() ?: continue
                    val out = pair.outEp
                    val input = pair.inEp
                    val connection = usb.openDevice(device) ?: error(AppStrings.get(R.string.msg_tview_open_failed, "无法打开 Tview USB 连接"))
                    var claimed = false
                    try {
                        claimed = connection.claimInterface(intf, true)
                        if (!claimed) continue
                        TiqiaaSession(object : UsbBulkChannel {
                            override fun read(buffer: ByteArray, timeoutMs: Int) =
                                connection.bulkTransfer(input, buffer, buffer.size, timeoutMs)
                            override fun write(data: ByteArray, timeoutMs: Int) =
                                connection.bulkTransfer(out, data, data.size, timeoutMs)
                        }).transmit(raw)
                        return@withLock Result.success(Unit)
                    } finally {
                        try { if (claimed) connection.releaseInterface(intf) }
                        finally { connection.close() }
                    }
                }
                error(AppStrings.get(R.string.msg_tview_claim_failed, "无法认领 Tview 的 USB 输入/输出接口"))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
            finally { refreshState() }
        }
    }
}
