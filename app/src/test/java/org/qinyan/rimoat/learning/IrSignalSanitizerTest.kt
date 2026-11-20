package org.qinyan.rimoat.learning

import org.qinyan.rimoat.data.SignalType
import org.qinyan.rimoat.hardware.learning.IrSignalSanitizer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrSignalSanitizerTest {

    @Test
    fun testEmptyPatternFails() {
        val result = IrSignalSanitizer.sanitize(38000, intArrayOf())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("empty") == true)
    }

    @Test
    fun testAllZeroPatternFails() {
        val result = IrSignalSanitizer.sanitize(38000, intArrayOf(0, 0, 0, 0, 0, 0))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid pulse duration") == true)
    }

    @Test
    fun testNegativePulsesConvertedToPositive() {
        val input = intArrayOf(-9000, -4500, -560, -560, -560, -1690)
        val result = IrSignalSanitizer.sanitize(38000, input)
        assertTrue(result.isSuccess)
        val signal = result.getOrThrow()
        assertArrayEquals(intArrayOf(9000, 4500, 560, 560, 560, 1690), signal.patternMicros)
    }

    @Test
    fun testGlitchFilterDropsLeadingOpticalNoiseSpike() {
        // Glitch of 80µs mark and 100µs space followed by true NEC leader (9000µs, 4500µs)
        val input = intArrayOf(80, 100, 9000, 4500, 560, 560, 560, 1690)
        val result = IrSignalSanitizer.sanitize(38000, input)
        assertTrue(result.isSuccess)
        val signal = result.getOrThrow()
        assertEquals(9000, signal.patternMicros[0])
        assertEquals(4500, signal.patternMicros[1])
        assertEquals(6, signal.patternMicros.size)
    }

    @Test
    fun testMinimumPulseCountThreshold() {
        // Less than 6 pulses must be rejected as spurious trigger
        val shortNoise = intArrayOf(500, 500, 500, 500)
        val result = IrSignalSanitizer.sanitize(38000, shortNoise)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("below minimum threshold") == true)
    }

    @Test
    fun testMinimumTotalDurationThreshold() {
        // 6 pulses each 100 µs = 600 µs (< 1000 µs threshold)
        val subMillisecond = intArrayOf(160, 160, 160, 160, 160, 160)
        val result = IrSignalSanitizer.sanitize(38000, subMillisecond)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Total pulse duration") == true)
    }

    @Test
    fun testOddLengthParityPadding() {
        // 7 pulses (odd) should be padded with 40,000 µs trailing space to make 8
        val oddPattern = intArrayOf(9000, 4500, 560, 560, 560, 560, 560)
        val result = IrSignalSanitizer.sanitize(38000, oddPattern)
        assertTrue(result.isSuccess)
        val signal = result.getOrThrow()
        assertEquals(8, signal.patternMicros.size)
        assertEquals(IrSignalSanitizer.TRAILING_SPACE_PADDING_US, signal.patternMicros.last())
        assertEquals(40000, signal.patternMicros.last())
    }

    @Test
    fun testEvenLengthNotPadded() {
        val evenPattern = intArrayOf(9000, 4500, 560, 560, 560, 1690)
        val result = IrSignalSanitizer.sanitize(38000, evenPattern)
        assertTrue(result.isSuccess)
        val signal = result.getOrThrow()
        assertEquals(6, signal.patternMicros.size)
        assertEquals(1690, signal.patternMicros.last())
    }

    @Test
    fun testCarrierFrequencyClamping() {
        // <= 0 defaults to 38000
        val resZero = IrSignalSanitizer.sanitize(0, intArrayOf(9000, 4500, 560, 560, 560, 1690)).getOrThrow()
        assertEquals(38000, resZero.frequencyHz)

        // Below 15000 clamped to 15000
        val resLow = IrSignalSanitizer.sanitize(10000, intArrayOf(9000, 4500, 560, 560, 560, 1690)).getOrThrow()
        assertEquals(15000, resLow.frequencyHz)

        // Above 60000 clamped to 60000
        val resHigh = IrSignalSanitizer.sanitize(75000, intArrayOf(9000, 4500, 560, 560, 560, 1690)).getOrThrow()
        assertEquals(60000, resHigh.frequencyHz)

        // Valid preserved
        val resValid = IrSignalSanitizer.sanitize(40000, intArrayOf(9000, 4500, 560, 560, 560, 1690)).getOrThrow()
        assertEquals(40000, resValid.frequencyHz)
    }

    @Test
    fun testMaximumDurationLimitFails() {
        // Exceeds 2,000,000 µs
        val hugePattern = intArrayOf(1_000_000, 1_000_000, 500, 500, 500, 500)
        val result = IrSignalSanitizer.sanitize(38000, hugePattern)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("exceeds framework limit") == true)
    }

    @Test
    fun testToRemoteButtonConversion() {
        val pattern = intArrayOf(9000, 4500, 560, 560, 560, 1690)
        val raw = IrSignalSanitizer.sanitize(38000, pattern).getOrThrow()
        val button = IrSignalSanitizer.toRemoteButton(
            signal = raw,
            label = "Power",
            id = "btn_power_1"
        )
        assertEquals("btn_power_1", button.id)
        assertEquals("Power", button.label)
        assertEquals(SignalType.RAW, button.signalType)
        assertEquals(38000, button.frequencyHz)
        assertEquals(6, button.rawPattern.size)
        assertEquals(9000, button.rawPattern[0])
    }

    @Test
    fun testProtocolGuess() {
        val necPattern = IntArray(68) { if (it == 0) 9000 else if (it == 1) 4500 else 560 }
        assertEquals("NEC (38kHz)", IrSignalSanitizer.guessProtocol(necPattern))

        val sonyPattern = IntArray(26) { if (it == 0) 2400 else if (it == 1) 600 else 600 }
        assertEquals("SONY (40kHz)", IrSignalSanitizer.guessProtocol(sonyPattern))

        val acPattern = IntArray(120) { 500 }
        assertEquals("AC/HVAC (Raw)", IrSignalSanitizer.guessProtocol(acPattern))

        val genericPattern = intArrayOf(1000, 1000, 1000, 1000, 1000, 1000)
        assertEquals("RAW", IrSignalSanitizer.guessProtocol(genericPattern))
    }
}
