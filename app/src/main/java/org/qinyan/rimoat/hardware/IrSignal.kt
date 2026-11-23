package org.qinyan.rimoat.hardware

import java.util.Arrays
import kotlinx.serialization.Serializable

sealed interface IrSignal {
    /** Raw alternating on/off pulse durations in microseconds */
    @Serializable
    data class Raw(
        val frequencyHz: Int = 38000,
        val patternMicros: IntArray
    ) : IrSignal {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Raw
            if (frequencyHz != other.frequencyHz) return false
            if (!patternMicros.contentEquals(other.patternMicros)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = frequencyHz
            result = 31 * result + patternMicros.contentHashCode()
            return result
        }

        override fun toString(): String {
            return "IrSignal.Raw(frequency=${frequencyHz}Hz, pulses=${patternMicros.size})"
        }
    }

    /** NEC with an 8-bit command and either an 8-bit or extended 16-bit address. */
    data class Nec(
        val address: Int,
        val command: Int,
        /** Defaults to the legacy address convention. Set true for extended addresses below 0x0100. */
        val extended: Boolean = (address and 0xFF00) != 0
    ) : IrSignal {
        override fun toString(): String {
            val addrHex = "0x" + address.toString(16).padStart(4, '0').uppercase()
            val cmdHex = "0x" + command.toString(16).padStart(2, '0').uppercase()
            return "IrSignal.Nec(addr=$addrHex, cmd=$cmdHex)"
        }
    }
}

