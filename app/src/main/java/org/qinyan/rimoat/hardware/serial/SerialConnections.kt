package org.qinyan.rimoat.hardware.serial

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import java.util.UUID

/** Distinguishes a live connection from a later device that reuses its Linux path. */
internal class SerialConnections {
    private val devices = mutableMapOf<String, SerialDeviceOption>()

    @Synchronized
    fun snapshot(connected: List<SerialDeviceOption>): List<SerialDeviceOption> {
        devices.keys.retainAll(connected.map { it.deviceName }.toSet())
        return connected.map { current ->
            val previous = devices[current.deviceName]
            val sameDevice = previous != null && previous.vendorId == current.vendorId &&
                previous.productId == current.productId && previous.portCount == current.portCount &&
                (previous.serialNumber == null || current.serialNumber == null || previous.serialNumber == current.serialNumber)
            current.copy(connectionId = if (sameDevice) previous!!.connectionId else UUID.randomUUID().toString())
                .also {
                    // Authorization can temporarily hide the serial number; retain known identity.
                    devices[current.deviceName] = if (sameDevice && it.serialNumber == null)
                        it.copy(serialNumber = previous?.serialNumber) else it
                }
        }
    }

    /** Invalidate immediately on the broadcast, even if detach/attach precede the next scan. */
    @Synchronized
    fun detached(deviceName: String?) {
        if (deviceName == null) devices.clear() else devices.remove(deviceName)
    }
}

internal fun selectedSerialConnection(devices: List<SerialDeviceOption>, connectionId: String): SerialDeviceOption =
    devices.singleOrNull { it.connectionId.isNotBlank() && it.connectionId == connectionId }
        ?: throw IllegalArgumentException(AppStrings.get(R.string.msg_selected_usb_disconnected, "所选 USB 设备已断开，请重新选择"))
