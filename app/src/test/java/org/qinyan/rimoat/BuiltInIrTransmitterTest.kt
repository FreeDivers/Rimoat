package org.qinyan.rimoat

import org.qinyan.rimoat.hardware.BuiltInIrTransmitter
import org.qinyan.rimoat.hardware.IrProtocolEncoder
import org.qinyan.rimoat.hardware.IrSignal
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInIrTransmitterTest {

    @Test
    fun testEmptyPatternFails() {
        val result = BuiltInIrTransmitter.sanitizeAndValidate(38000, intArrayOf())
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertEquals("Empty pulse pattern", ex?.message)
    }

    @Test
    fun testZeroPulseDurationFails() {
        val result = BuiltInIrTransmitter.sanitizeAndValidate(38000, intArrayOf(9000, 0, 560, 560))
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex?.message?.contains("Invalid non-positive pulse duration") == true)
    }

    @Test
    fun testNegativePulsesConvertedToPositive() {
        // Many IR formats/decoders represent spaces as negative numbers (e.g. 5996, -7319, 549, -3423)
        val rawPulses = intArrayOf(5996, -7319, 549, -3423)
        val result = BuiltInIrTransmitter.sanitizeAndValidate(38000, rawPulses)
        assertTrue(result.isSuccess)
        val (freq, pattern) = result.getOrThrow()
        assertEquals(38000, freq)
        assertArrayEquals(intArrayOf(5996, 7319, 549, 3423), pattern)
        assertEquals(4, pattern.size)
    }

    @Test
    fun testOddLengthPatternPadding() {
        // Standard NEC pulse array has 67 pulses (odd)
        val necSignal = IrSignal.Nec(address = 0x00FF, command = 0x45)
        val rawNec = IrProtocolEncoder.encodeNec(necSignal)
        assertEquals(67, rawNec.patternMicros.size)

        val result = BuiltInIrTransmitter.sanitizeAndValidate(rawNec.frequencyHz, rawNec.patternMicros)
        assertTrue(result.isSuccess)
        val (freq, pattern) = result.getOrThrow()
        assertEquals(38000, freq)
        // 67 pulses + 1 trailing space = 68 pulses (even length)
        assertEquals(68, pattern.size)
        // Verify trailing space is exactly DEFAULT_TRAILING_SPACE_US (40000 us)
        assertEquals(BuiltInIrTransmitter.DEFAULT_TRAILING_SPACE_US, pattern.last())
        assertEquals(40000, pattern.last())
    }

    @Test
    fun testEvenLengthPatternNotPadded() {
        val evenPattern = intArrayOf(9000, 4500, 560, 1690)
        val result = BuiltInIrTransmitter.sanitizeAndValidate(38000, evenPattern)
        assertTrue(result.isSuccess)
        val (_, pattern) = result.getOrThrow()
        assertEquals(4, pattern.size)
        assertEquals(1690, pattern.last())
    }

    @Test
    fun testCarrierFrequencyDefaulting() {
        // Non-positive carrier frequency should default to 38000 Hz
        val resultZero = BuiltInIrTransmitter.sanitizeAndValidate(0, intArrayOf(1000, 1000))
        assertTrue(resultZero.isSuccess)
        assertEquals(38000, resultZero.getOrThrow().first)

        val resultNegative = BuiltInIrTransmitter.sanitizeAndValidate(-1, intArrayOf(1000, 1000))
        assertTrue(resultNegative.isSuccess)
        assertEquals(38000, resultNegative.getOrThrow().first)

        // Valid carrier frequency preserved
        val resultCustom = BuiltInIrTransmitter.sanitizeAndValidate(40000, intArrayOf(1000, 1000))
        assertTrue(resultCustom.isSuccess)
        assertEquals(40000, resultCustom.getOrThrow().first)
    }

    @Test
    fun testTransmissionDurationLimit() {
        // Exceeding 2,000,000 µs (Android limit) must fail
        val tooLongPulses = intArrayOf(1_000_000, 1_000_000, 1000, 1000)
        val result = BuiltInIrTransmitter.sanitizeAndValidate(38000, tooLongPulses)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex?.message?.contains("exceeds framework limit") == true)

        // Just under 2 seconds should pass
        val safePulses = intArrayOf(500_000, 500_000, 500_000, 400_000)
        val safeResult = BuiltInIrTransmitter.sanitizeAndValidate(38000, safePulses)
        assertTrue(safeResult.isSuccess)
    }
}
