package org.qinyan.rimoat.hardware.serial

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.hardware.IrSignal
import kotlin.math.abs
import kotlin.math.roundToInt

/** USB IR Toy sampling firmware: 48 MHz / 4 / 256 timer, BE words, FFFF terminator. */
internal object IrToyProtocol {
    fun carrierCommand(frequencyHz: Int): ByteArray {
        require(frequencyHz in 20_000..100_000) { AppStrings.get(R.string.msg_irtoy_carrier_range) }
        val period = (3_000_000.0 / frequencyHz).roundToInt() - 1
        return byteArrayOf(0x06, period.toByte(), 0) // firmware derives 50% duty from period
    }

    fun encode(signal: IrSignal.Raw): ByteArray {
        carrierCommand(signal.frequencyHz)
        val pulses = signal.patternMicros.map { abs(it.toLong()) }
        require(pulses.isNotEmpty() && pulses.sum() < 2_000_000L) { AppStrings.get(R.string.msg_waveform_empty_long) }
        require((pulses.size + 1) * 2 <= 65535) { AppStrings.get(R.string.msg_irtoy_waveform_long) }
        val out = ByteArray((pulses.size + 1) * 2)
        pulses.forEachIndexed { i, micros ->
            val ticks = ((micros * 3 + 32) / 64).toInt()
            require(micros > 0 && ticks in 1..65534) { AppStrings.get(R.string.msg_irtoy_pulse_range) }
            out[i * 2] = (ticks ushr 8).toByte()
            out[i * 2 + 1] = ticks.toByte()
        }
        out[out.lastIndex - 1] = 0xFF.toByte()
        out[out.lastIndex] = 0xFF.toByte()
        return out
    }
}
