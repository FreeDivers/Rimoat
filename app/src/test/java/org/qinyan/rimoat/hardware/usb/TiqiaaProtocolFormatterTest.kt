package org.qinyan.rimoat.hardware.usb

import org.qinyan.rimoat.testing.AppStringsResourceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class TiqiaaProtocolFormatterTest {
    @get:org.junit.Rule val strings = AppStringsResourceRule()


    @Test fun usesNativeCarrierTableInsteadOfAlwaysSelecting38kHz() {
        // Carrier-table entries must map back to their protocol indices.
        for ((frequency, index) in listOf(38000 to 0, 36000 to 3, 40000 to 4,
            33000 to 19, 45000 to 29, 38431 to 12)) {
            assertEquals(index.toByte(), TiqiaaProtocolFormatter.buildTransmissionPayload(
                frequency, intArrayOf(560), seq = 1)[4])
        }
        assertThrows(IllegalArgumentException::class.java) {
            TiqiaaProtocolFormatter.buildTransmissionPayload(56000, intArrayOf(560))
        }
    }

    @Test fun normalizesSignedPulsesAndRejectsFramesTheFirmwareWouldTruncate() {
        assertArrayEquals(
            TiqiaaProtocolFormatter.buildTransmissionPayload(38000, intArrayOf(9000, 4500, 560), seq = 1),
            TiqiaaProtocolFormatter.buildTransmissionPayload(38000, intArrayOf(9000, -4500, 560), seq = 1)
        )
        for (pattern in listOf(intArrayOf(), intArrayOf(0), intArrayOf(Int.MIN_VALUE),
            intArrayOf(2_000_000), IntArray(1020) { 16 })) {
            assertThrows(IllegalArgumentException::class.java) {
                TiqiaaProtocolFormatter.buildTransmissionPayload(38000, pattern)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            TiqiaaProtocolFormatter.wrapInEnvelope(ByteArray(1025), 1)
        }
    }

    @Test
    fun testBuildTransmissionPayloadBasicStructure() {
        val pattern = intArrayOf(560, 560) // 1 mark, 1 space
        val payload = TiqiaaProtocolFormatter.buildTransmissionPayload(38000, pattern, seq = 0x05)

        assertTrue(payload.size >= 7)
        assertEquals('S'.code.toByte(), payload[0])
        assertEquals('T'.code.toByte(), payload[1])
        assertEquals(0x05.toByte(), payload[2])
        assertEquals('D'.code.toByte(), payload[3])
        assertEquals(0x00.toByte(), payload[4]) // Meta

        // Timing bytes:
        // 560us -> 560 / 16 = 35 units.
        // Mark has 0x80 bit: 0x80 | 35 = 0xA3 (163)
        assertEquals((0x80 or 35).toByte(), payload[5])
        // Space has 0x80 cleared: 35
        assertEquals(35.toByte(), payload[6])

        // Tail: 'E', 'N'
        assertEquals('E'.code.toByte(), payload[payload.size - 2])
        assertEquals('N'.code.toByte(), payload[payload.size - 1])
    }

    @Test
    fun testLongPulseChunking() {
        // 9000us leader pulse: 9000 / 16 = 562.5 -> 563 units
        // Must chunk into 127 + 127 + 127 + 127 + 55 = 563 units
        val pattern = intArrayOf(9000)
        val payload = TiqiaaProtocolFormatter.buildTransmissionPayload(38000, pattern, seq = 1)

        val timingBytes = payload.copyOfRange(5, payload.size - 2)
        assertEquals(5, timingBytes.size)

        // All chunks must have 0x80 set (Mark)
        for (i in 0 until 4) {
            assertEquals((0x80 or 127).toByte(), timingBytes[i])
        }
        assertEquals((0x80 or 55).toByte(), timingBytes[4])
    }

    @Test
    fun testEnvelopeWrappingSingleAndMultiChunk() {
        // Small payload <= 56 bytes
        val smallPayload = ByteArray(30) { it.toByte() }
        val chunksSmall = TiqiaaProtocolFormatter.wrapInEnvelope(smallPayload, envelopeSeq = 2)
        assertEquals(1, chunksSmall.size)
        assertEquals(0x02.toByte(), chunksSmall[0][0])
        assertEquals((30 + 3).toByte(), chunksSmall[0][1])
        assertEquals(2.toByte(), chunksSmall[0][2])
        assertEquals(1.toByte(), chunksSmall[0][3]) // Total chunks
        assertEquals(1.toByte(), chunksSmall[0][4]) // Chunk index

        // Multi-chunk payload > 56 bytes (e.g. 100 bytes -> 56 + 44)
        val largePayload = ByteArray(100) { it.toByte() }
        val chunksLarge = TiqiaaProtocolFormatter.wrapInEnvelope(largePayload, envelopeSeq = 3)
        assertEquals(2, chunksLarge.size)
        assertEquals(2.toByte(), chunksLarge[0][3]) // Total chunks = 2
        assertEquals(1.toByte(), chunksLarge[0][4]) // Chunk 1
        assertEquals(2.toByte(), chunksLarge[1][4]) // Chunk 2
    }

    @Test
    fun testCalculatePostTransmitDelayMs() {
        val pattern = intArrayOf(9000, 4500, 560, 560)
        val totalUs = 9000 + 4500 + 560 + 560 // 14620us -> 14ms + 2ms = 16ms
        val delayMs = TiqiaaProtocolFormatter.calculatePostTransmitDelayMs(pattern)
        assertEquals(16L, delayMs)
    }
}

