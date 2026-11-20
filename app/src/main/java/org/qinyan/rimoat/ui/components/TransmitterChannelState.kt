package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.hardware.UsbAccessState
import org.qinyan.rimoat.hardware.serial.SerialIrModule
import org.qinyan.rimoat.hardware.serial.SerialIrSettings

internal data class TransmitterChannelState(
    val type: TransmitterType,
    val available: Boolean,
    val selected: Boolean,
    val status: String,
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
        available && type == TransmitterType.AUDIO -> AppStrings.get(R.string.control_audio_connected, "音频已连接")
        available -> AppStrings.get(R.string.control_available, "可用")
        devices.any { it.pending } -> AppStrings.get(R.string.control_waiting_system_permission, "等待系统授权")
        devices.any { it.denied } -> AppStrings.get(R.string.control_permission_denied, "授权未通过")
        devices.any { !it.granted } -> AppStrings.get(R.string.control_usb_permission_required, "需要 USB 授权")
        type == TransmitterType.BUILT_IN -> AppStrings.get(R.string.control_no_builtin_ir, "此设备无内置红外")
        devices.isEmpty() -> if (selected) AppStrings.get(R.string.control_device_disconnected, "设备已断开") else AppStrings.get(R.string.control_not_connected, "未连接")
        type == TransmitterType.USB_SERIAL && !serialSettingsLoaded -> AppStrings.get(R.string.control_loading_config, "正在读取配置")
        type == TransmitterType.USB_SERIAL &&
            (serialSettings.module == SerialIrModule.UNCONFIGURED || serialSettings.vendorId == null) -> AppStrings.get(R.string.control_module_config_required, "需要配置模块")
        else -> AppStrings.get(R.string.control_device_not_ready, "设备未就绪")
    }
    TransmitterChannelState(type, available, selected, status, devices)
}

internal fun channelTitle(type: TransmitterType): String = when (type) {
    TransmitterType.USB_TIQIAA -> AppStrings.get(R.string.control_tiqiaa_accessory, "遥控精灵 USB 配件")
    TransmitterType.USB_ELKSMART -> AppStrings.get(R.string.control_elksmart_accessory, "小蛮腰 USB 配件")
    TransmitterType.USB_SERIAL -> AppStrings.get(R.string.control_serial_accessory, "USB 串口红外模块")
    TransmitterType.AUDIO -> AppStrings.get(R.string.control_audio_accessory, "3.5 mm 音频发射头")
    TransmitterType.BUILT_IN -> AppStrings.get(R.string.control_builtin_accessory, "手机内置红外")
    else -> type.displayName
}
