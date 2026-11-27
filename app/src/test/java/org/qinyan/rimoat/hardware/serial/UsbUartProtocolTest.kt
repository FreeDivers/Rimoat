package org.qinyan.rimoat.hardware.serial

import org.qinyan.rimoat.testing.AppStringsResourceRule
import org.qinyan.rimoat.hardware.IrProtocolEncoder
import org.qinyan.rimoat.hardware.IrSignal
import org.junit.Assert.*
import org.junit.Test

class UsbUartProtocolTest {
    @get:org.junit.Rule val strings = AppStringsResourceRule()

    private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()
    @Test fun ysIrtmHonorsExplicitNecAddressFormat() {
        val extended = IrSignal.Nec(0x00FE, 0x45, extended = true)
        val expected = bytes(0xB2, 0xF1, 0xFE, 0x00, 0x45)
        assertArrayEquals(expected, UsbUartProtocol.buildYsIrtmNecPacket(0x00FE, 0x45, 0xB2, extended = true))
        assertArrayEquals(expected, UsbUartProtocol.buildYsIrtmPacket(extended, 0xB2))
        assertArrayEquals(expected, UsbUartProtocol.buildYsIrtmPacket(IrProtocolEncoder.encodeNec(extended), 0xB2))
        assertArrayEquals(bytes(0xB2, 0xF1, 0xFE, 0x01, 0x45),
            UsbUartProtocol.buildYsIrtmPacket(extended.copy(extended = false), 0xB2))
    }

    @Test fun ysIrtmStructuredAndRawPacketsAgreeForExplicitFormats() {
        for (address in listOf(0x0000, 0x00FE, 0x00FF, 0x0100, 0x1234, 0xFFFF)) {
            for (extended in listOf(false, true)) {
                val signal = IrSignal.Nec(address, 0x45, extended)
                assertArrayEquals(UsbUartProtocol.buildYsIrtmPacket(signal),
                    UsbUartProtocol.buildYsIrtmPacket(IrProtocolEncoder.encodeNec(signal)))
            }
        }
    }

    @Test fun ysIrtmStandardExtendedAndModuleAddressVectors() {
        assertArrayEquals(bytes(0xA1, 0xF1, 0x12, 0xED, 0x34), UsbUartProtocol.buildYsIrtmNecPacket(0x12, 0x34))
        assertArrayEquals(bytes(0xB2, 0xF1, 0x34, 0x12, 0x56), UsbUartProtocol.buildYsIrtmNecPacket(0x1234, 0x56, 0xB2))
        assertThrows(IllegalArgumentException::class.java) { UsbUartProtocol.buildYsIrtmNecPacket(65536, 1) }
        assertThrows(IllegalArgumentException::class.java) { UsbUartProtocol.buildYsIrtmNecPacket(0, 256) }
    }
    @Test fun ysIrtmDecodesNecRawIncludingSignedTimingsAndTrailingGap() {
        for (address in listOf(0, 0x12, 0x1234, 0xFFFF)) {
            val nec = IrSignal.Nec(address, 0x45)
            val raw = IrProtocolEncoder.encodeNec(nec)
            val signed = raw.copy(patternMicros = raw.patternMicros.mapIndexed { i, v -> if (i % 2 == 1) -v else v }.toIntArray() + -40_000)
            assertArrayEquals(UsbUartProtocol.buildYsIrtmPacket(nec), UsbUartProtocol.buildYsIrtmPacket(signed))
        }
    }
    @Test fun ysIrtmRejectsOtherProtocolsRepeatsAndCorruptedNec() {
        val raw = IrProtocolEncoder.encodeNec(IrSignal.Nec(0x12, 0x34))
        val corrupt = raw.patternMicros.copyOf().apply { this[65] = if (this[65] == 560) 1690 else 560 }
        for (signal in listOf(raw.copy(frequencyHz = 56000), raw.copy(patternMicros = corrupt),
            raw.copy(patternMicros = intArrayOf(9000, 2250, 560)), raw.copy(patternMicros = intArrayOf(2400, 600, 1200)))) {
            assertThrows(IllegalArgumentException::class.java) { UsbUartProtocol.buildYsIrtmPacket(signal) }
        }
    }
    @Test fun irToyTimerAndCarrierMatchFirmwareRegisters() {
        assertArrayEquals(bytes(6, 78, 0), IrToyProtocol.carrierCommand(38000))
        assertArrayEquals(bytes(0, 3, 0, 6, 0xFF, 0xFF), IrToyProtocol.encode(IrSignal.Raw(patternMicros = intArrayOf(64, -128))))
        assertArrayEquals(bytes(0x01, 0xA6, 0, 0xD3, 0, 26, 0xFF, 0xFF), IrToyProtocol.encode(IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560))))
        for (bad in listOf(intArrayOf(), intArrayOf(0), intArrayOf(1), intArrayOf(1_398_100), intArrayOf(Int.MIN_VALUE))) {
            assertThrows(IllegalArgumentException::class.java) { IrToyProtocol.encode(IrSignal.Raw(patternMicros = bad)) }
        }
    }
}
