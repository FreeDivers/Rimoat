package org.qinyan.rimoat

import org.qinyan.rimoat.hardware.IrProtocolEncoder
import org.qinyan.rimoat.hardware.IrSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrProtocolEncoderTest {

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

