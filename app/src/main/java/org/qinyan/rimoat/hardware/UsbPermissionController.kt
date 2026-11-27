package org.qinyan.rimoat.hardware

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.qinyan.rimoat.hardware.learning.UsbDeviceFilter
import org.qinyan.rimoat.hardware.serial.UsbUartProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UsbAccessState(
    val deviceName: String,
    val label: String,
    val granted: Boolean,
    val pending: Boolean = false,
    val denied: Boolean = false,
    val transmitterType: TransmitterType = TransmitterType.USB
)

/** Permission discovery never opens or claims a USB interface. */
class UsbPermissionController(private val context: Context, private val onChanged: () -> Unit) {
    companion object { const val ACTION = "org.qinyan.rimoat.USB_PERMISSION" }
    private val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val _devices = MutableStateFlow<List<UsbAccessState>>(emptyList())
    val devices = _devices.asStateFlow()
    private var pendingName: String? = null
    private val deniedNames = mutableSetOf<String>()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION) {
                pendingName?.let { name ->
                    val device = manager?.deviceList?.get(name)
                    if (device != null && manager?.hasPermission(device) != true) deniedNames.add(name)
                }
                pendingName = null
            }
            refresh()
            onChanged()
        }
    }

    init {
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(ACTION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    @Synchronized
    fun refresh() {
        val connected = manager?.deviceList?.values.orEmpty().filter {
            UsbDeviceFilter.isSupported(it) || UsbUartProtocol.isSerialDevice(it)
        }
        val names = connected.map { it.deviceName }.toSet()
        deniedNames.retainAll(names)
        if (pendingName !in names) pendingName = null
        _devices.value = connected.map { device ->
            val granted = manager?.hasPermission(device) == true
            UsbAccessState(device.deviceName, device.productName ?: AppStrings.get(R.string.msg_usb_ir_device), granted,
                pending = device.deviceName == pendingName,
                denied = !granted && device.deviceName in deniedNames,
                transmitterType = when {
                    UsbDeviceFilter.isTiqiaa(device) -> TransmitterType.USB_TIQIAA
                    UsbDeviceFilter.isElkSmart(device) -> TransmitterType.USB_ELKSMART
                    else -> TransmitterType.USB_SERIAL
                })
        }
    }

    @Synchronized
    fun request(deviceName: String) {
        val usb = manager ?: return
        if (pendingName != null) return
        val device = usb.deviceList[deviceName] ?: run { refresh(); return }
        if (usb.hasPermission(device)) { refresh(); onChanged(); return }
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val reply = PendingIntent.getBroadcast(context, device.deviceId,
            Intent(ACTION).setPackage(context.packageName), PendingIntent.FLAG_UPDATE_CURRENT or mutable)
        pendingName = deviceName
        deniedNames.remove(deviceName)
        refresh()
        try {
            usb.requestPermission(device, reply)
        } catch (_: Exception) {
            pendingName = null
            deniedNames.add(deviceName)
            refresh()
        }
    }

    fun close() { context.unregisterReceiver(receiver) }
}
