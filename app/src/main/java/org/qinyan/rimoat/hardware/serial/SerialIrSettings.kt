package org.qinyan.rimoat.hardware.serial

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import kotlinx.serialization.Serializable

@Serializable
enum class SerialIrModule(private val sourceLabel: String) {
    UNCONFIGURED("未配置"), YS_IRTM("YS-IRTM（NEC）"), IR_TOY("USB IR Toy v2（RAW）");

    val label: String
        get() = if (this == UNCONFIGURED) AppStrings.get(R.string.msg_serial_unconfigured) else sourceLabel
}

@Serializable
data class SerialIrSettings(
    val module: SerialIrModule = SerialIrModule.UNCONFIGURED,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val serialNumber: String? = null,
    val portIndex: Int = 0,
    val baudRate: Int = 9600,
    val moduleAddress: Int = 0xA1
) {
    fun validate() {
        require((vendorId == null) == (productId == null)) { AppStrings.get(R.string.msg_usb_ids_incomplete) }
        require(vendorId == null || vendorId in 0..65535 && productId!! in 0..65535) { AppStrings.get(R.string.msg_usb_ids_invalid) }
        require(portIndex in 0..31) { AppStrings.get(R.string.msg_serial_port_range) }
        require(moduleAddress in 0..255 && moduleAddress != 0xFA) { AppStrings.get(R.string.msg_module_address_range) }
        require(if (module == SerialIrModule.IR_TOY) baudRate == 115200 else baudRate in listOf(4800, 9600, 19200, 57600)) {
            AppStrings.get(R.string.msg_serial_baud_rates)
        }
    }
}

data class SerialDeviceOption(
    val deviceName: String, val label: String, val vendorId: Int, val productId: Int,
    val portCount: Int, val granted: Boolean, val serialNumber: String?,
    /** In-memory connection identity; never written to serial_ir.json. */
    val connectionId: String = ""
)

/** No persisted Linux USB path: it can be reused for a different device after reconnecting. */
internal fun selectSerialDevice(settings: SerialIrSettings, devices: List<SerialDeviceOption>, selectedPath: String?): SerialDeviceOption? {
    if (settings.module == SerialIrModule.UNCONFIGURED) return null
    val matching = devices.filter {
        it.vendorId == settings.vendorId && it.productId == settings.productId &&
            settings.portIndex < it.portCount &&
            (settings.serialNumber == null || settings.serialNumber == it.serialNumber)
    }
    return matching.firstOrNull { it.deviceName == selectedPath } ?: matching.singleOrNull()
}


internal fun serialPermissionCandidates(settings: SerialIrSettings, devices: List<SerialDeviceOption>): List<SerialDeviceOption> =
    if (settings.module == SerialIrModule.UNCONFIGURED) emptyList() else devices.filter {
        !it.granted && it.vendorId == settings.vendorId && it.productId == settings.productId &&
            settings.portIndex < it.portCount
    }
