package org.qinyan.rimoat.ui.components

import androidx.annotation.StringRes
import org.qinyan.rimoat.R
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.hardware.UsbAccessState
import org.qinyan.rimoat.hardware.serial.SerialIrModule
import org.qinyan.rimoat.hardware.serial.SerialIrSettings

internal enum class ChannelStatus(@param:StringRes val labelRes: Int) {
    AUDIO_CONNECTED(R.string.control_audio_connected),
    AVAILABLE(R.string.control_available),
    WAITING_PERMISSION(R.string.control_waiting_system_permission),
    PERMISSION_DENIED(R.string.control_permission_denied),
    USB_PERMISSION_REQUIRED(R.string.control_usb_permission_required),
    NO_BUILTIN_IR(R.string.control_no_builtin_ir),
    DEVICE_DISCONNECTED(R.string.control_device_disconnected),
    NOT_CONNECTED(R.string.control_not_connected),
    LOADING_CONFIG(R.string.control_loading_config),
    MODULE_CONFIG_REQUIRED(R.string.control_module_config_required),
    DEVICE_NOT_READY(R.string.control_device_not_ready)
}

internal data class TransmitterChannelState(
    val type: TransmitterType,
    val available: Boolean,
    val selected: Boolean,
    val status: ChannelStatus,
    val devices: List<UsbAccessState>
) {
    // An unplugged manual selection must remain visible so the user can recover.
    val needsAttention: Boolean get() = !available && (selected || devices.isNotEmpty())
}

internal fun transmitterChannels(
    availableTypes: Set<TransmitterType>,
    selectedType: TransmitterType?,
    usbDevices: List<UsbAccessState>,
    serialSettings: SerialIrSettings,
    serialSettingsLoaded: Boolean
): List<TransmitterChannelState> = listOf(
    TransmitterType.USB_TIQIAA,
    TransmitterType.USB_ELKSMART,
    TransmitterType.USB_SERIAL,
    TransmitterType.AUDIO,
    TransmitterType.BUILT_IN
).map { type ->
    val available = type in availableTypes
    val selected = type == selectedType
    val devices = usbDevices.filter { it.transmitterType == type }
    val status = when {
        available && type == TransmitterType.AUDIO -> ChannelStatus.AUDIO_CONNECTED
        available -> ChannelStatus.AVAILABLE
        devices.any { it.pending } -> ChannelStatus.WAITING_PERMISSION
        devices.any { it.denied } -> ChannelStatus.PERMISSION_DENIED
        devices.any { !it.granted } -> ChannelStatus.USB_PERMISSION_REQUIRED
        type == TransmitterType.BUILT_IN -> ChannelStatus.NO_BUILTIN_IR
        devices.isEmpty() -> if (selected) ChannelStatus.DEVICE_DISCONNECTED else ChannelStatus.NOT_CONNECTED
        type == TransmitterType.USB_SERIAL && !serialSettingsLoaded -> ChannelStatus.LOADING_CONFIG
        type == TransmitterType.USB_SERIAL &&
            (serialSettings.module == SerialIrModule.UNCONFIGURED || serialSettings.vendorId == null) -> ChannelStatus.MODULE_CONFIG_REQUIRED
        else -> ChannelStatus.DEVICE_NOT_READY
    }
    TransmitterChannelState(type, available, selected, status, devices)
}

@StringRes
internal fun channelTitleRes(type: TransmitterType): Int = when (type) {
    TransmitterType.USB_TIQIAA -> R.string.control_tiqiaa_accessory
    TransmitterType.USB_ELKSMART -> R.string.control_elksmart_accessory
    TransmitterType.USB_SERIAL -> R.string.control_serial_accessory
    TransmitterType.AUDIO -> R.string.control_audio_accessory
    TransmitterType.BUILT_IN -> R.string.control_builtin_accessory
    TransmitterType.USB -> R.string.control_usb
    TransmitterType.MOCK -> R.string.control_demo_mode
    TransmitterType.NONE -> R.string.control_no_hardware
}

@StringRes
internal fun transmitterShortLabelRes(
    type: TransmitterType?,
    isReady: Boolean = true,
    forceMock: Boolean = false
): Int = when {
    forceMock || type == TransmitterType.MOCK -> R.string.control_demo_mode
    type == TransmitterType.USB_TIQIAA -> R.string.control_tiqiaa
    type == TransmitterType.USB_ELKSMART -> R.string.control_elksmart
    type == TransmitterType.USB_SERIAL -> R.string.control_serial_ir
    type == TransmitterType.USB -> R.string.control_usb
    type == TransmitterType.AUDIO -> R.string.control_audio_ir
    type == TransmitterType.BUILT_IN && isReady -> R.string.control_builtin_ir
    type == TransmitterType.BUILT_IN -> R.string.control_no_ir
    else -> R.string.control_no_hardware
}
