package org.qinyan.rimoat.hardware.serial

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import android.hardware.usb.UsbDevice
import org.qinyan.rimoat.hardware.IrSignal
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlin.math.abs

/** The bridge identifies a UART, never the infrared protocol connected to it. */
object UsbUartProtocol {
    fun isSerialDevice(device: UsbDevice): Boolean = probe(device) != null

    internal fun probe(device: UsbDevice) = runCatching {
        if ((device.vendorId == 0x10C4 || device.vendorId == 0x045E) && device.productId == 0x8468) null
        else UsbSerialProber.getDefaultProber().probeDevice(device)
    }.getOrNull()

    fun buildYsIrtmNecPacket(
        address: Int,
        command: Int,
        moduleAddress: Int = 0xA1,
        extended: Boolean = (address and 0xFF00) != 0
    ): ByteArray {
        require(address in 0..65535 && command in 0..255) { AppStrings.get(R.string.msg_nec_address_command_range) }
        val first = address and 255
        val second = if (extended) address ushr 8 else first xor 255
        return ysFrame(moduleAddress, first, second, command)
    }

    fun buildYsIrtmPacket(signal: IrSignal, moduleAddress: Int = 0xA1): ByteArray = when (signal) {
        is IrSignal.Nec -> buildYsIrtmNecPacket(signal.address, signal.command, moduleAddress, signal.extended)
        is IrSignal.Raw -> {
            require(signal.frequencyHz in 36_000..40_000) { AppStrings.get(R.string.msg_ysirtm_carrier) }
            val p = signal.patternMicros.map { abs(it.toLong()) }
            require(p.size == 67 || p.size == 68) { AppStrings.get(R.string.msg_ysirtm_raw_unsupported) }
            fun near(actual: Long, expected: Int) = abs(actual - expected) <= expected * 0.25
            require(near(p[0], 9000) && near(p[1], 4500) && near(p[66], 560)) { AppStrings.get(R.string.msg_nec_frame_incomplete) }
            if (p.size == 68) require(p[67] in 1..200_000) { AppStrings.get(R.string.msg_nec_end_gap_invalid) }
            val bytes = IntArray(4)
            for (bit in 0 until 32) {
                require(near(p[2 + bit * 2], 560)) { AppStrings.get(R.string.msg_nec_pulse_invalid) }
                val space = p[3 + bit * 2]
                val value = when {
                    near(space, 560) -> 0
                    near(space, 1690) -> 1
                    else -> error(AppStrings.get(R.string.msg_nec_bit_gap_invalid))
                }
                bytes[bit / 8] = bytes[bit / 8] or (value shl (bit % 8))
            }
            require(bytes[2] xor bytes[3] == 255) { AppStrings.get(R.string.msg_nec_checksum_failed) }
            ysFrame(moduleAddress, bytes[0], bytes[1], bytes[2])
        }
    }

    private fun ysFrame(moduleAddress: Int, first: Int, second: Int, command: Int): ByteArray {
        require(moduleAddress in 0..255 && moduleAddress != 0xFA) { AppStrings.get(R.string.msg_module_address_range) }
        return byteArrayOf(moduleAddress.toByte(), 0xF1.toByte(), first.toByte(), second.toByte(), command.toByte())
    }
}
