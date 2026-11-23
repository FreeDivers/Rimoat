package org.qinyan.rimoat

import org.qinyan.rimoat.hardware.IrProtocolEncoder
import org.qinyan.rimoat.hardware.IrSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrProtocolEncoderTest {

    @Test
    fun extendedNecPreservesZeroHighAddressByte() {
        val raw = IrProtocolEncoder.encodeNec(IrSignal.Nec(0x00FE, 0x45, extended = true))

        assertFrameBytes(raw, 0xFE, 0x00, 0x45, 0xBA)
    }

    @Test
    fun standardAndExtendedFormatsCoverAllLowAddresses() {
        for (address in 0..0xFF) {
            val standard = IrSignal.Nec(address, 0x01)
            assertFrameBytes(IrProtocolEncoder.encodeNec(standard), address, address xor 0xFF, 0x01, 0xFE)
            assertFrameBytes(IrProtocolEncoder.encodeNec(standard.copy(extended = true)), address, 0x00, 0x01, 0xFE)
        }
    }

    @Test
    fun omittedFormatPreservesExtendedAddressEncoding() {
        for (address in listOf(0x0100, 0x01FE, 0x1234, 0xFFFF)) {
            val signal = IrSignal.Nec(address, 0xA5)
            assertTrue(signal.extended)
            assertFrameBytes(IrProtocolEncoder.encodeNec(signal), address and 0xFF, address ushr 8, 0xA5, 0x5A)
        }
    }

    @Test
    fun explicitStandardFormatUsesInvertedLowAddressByte() {
        val raw = IrProtocolEncoder.encodeNec(IrSignal.Nec(0x1234, 0x00, extended = false), frequencyHz = 40000)

        assertEquals(40000, raw.frequencyHz)
        assertFrameBytes(raw, 0x34, 0xCB, 0x00, 0xFF)
    }

    private fun assertFrameBytes(raw: IrSignal.Raw, vararg bytes: Int) {
        assertEquals(67, raw.patternMicros.size)
        assertEquals(9000, raw.patternMicros[0])
        assertEquals(4500, raw.patternMicros[1])
        assertEquals(560, raw.patternMicros[66])
        bytes.forEachIndexed { byteIndex, value ->
            for (bit in 0 until 8) {
                val index = 2 + (byteIndex * 8 + bit) * 2
                assertEquals("Byte $byteIndex bit $bit mark", 560, raw.patternMicros[index])
                val space = if (value and (1 shl bit) == 0) 560 else 1690
                assertEquals("Byte $byteIndex bit $bit space", space, raw.patternMicros[index + 1])
            }
        }
    }

    @Test
    fun testNecEncodingStructure() {
        val necSignal = IrSignal.Nec(address = 0x00FF, command = 0x45)
        val raw = IrProtocolEncoder.encodeNec(necSignal)

        assertEquals(38000, raw.frequencyHz)

        // Structure:
        // Leader: 2 pulses (9000, 4500)
        // 32 data bits * 2 pulses = 64 pulses
        // Stop bit: 1 pulse (560)
        // Total = 67 pulses
        assertEquals(67, raw.patternMicros.size)

        // Check leader mark and space
        assertEquals(9000, raw.patternMicros[0])
        assertEquals(4500, raw.patternMicros[1])

        // Check stop mark
        assertEquals(560, raw.patternMicros[66])
    }

    @Test
    fun testNecBitEncoding() {
        // Command 0x01: LSB is 1, next 7 bits are 0
        // Address 0x00
        val necSignal = IrSignal.Nec(address = 0x00, command = 0x01)
        val raw = IrProtocolEncoder.encodeNec(necSignal)

        // All mark pulses should be 560
        for (i in 2 until 66 step 2) {
            assertEquals("Mark at index $i", 560, raw.patternMicros[i])
        }

        // Spaces should either be 560 (zero) or 1690 (one)
        for (i in 3 until 66 step 2) {
            val space = raw.patternMicros[i]
            assertTrue("Space at index $i should be 560 or 1690, was $space", space == 560 || space == 1690)
        }
    }
}

