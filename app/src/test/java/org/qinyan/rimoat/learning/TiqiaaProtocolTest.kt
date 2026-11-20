package org.qinyan.rimoat.learning

import org.qinyan.rimoat.hardware.learning.TiqiaaProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TiqiaaProtocolTest {

    @Test
    fun testBuildModeCommand() {
        val idleCmd = TiqiaaProtocol.buildModeCommand(0, 5)
        assertEquals(6, idleCmd.size)
        assertEquals('S'.code.toByte(), idleCmd[0])
        assertEquals('T'.code.toByte(), idleCmd[1])
        assertEquals(5.toByte(), idleCmd[2])
        assertEquals('L'.code.toByte(), idleCmd[3]) // Mode 0 = 'L'
        assertEquals('E'.code.toByte(), idleCmd[4])
        assertEquals('N'.code.toByte(), idleCmd[5])

        val learnCmd = TiqiaaProtocol.buildModeCommand(2, 8)
        assertEquals('R'.code.toByte(), learnCmd[3]) // Mode 2 = 'R'

        val cancelCmd = TiqiaaProtocol.buildModeCommand(6, 12)
        assertEquals('C'.code.toByte(), cancelCmd[3]) // Mode 6 = 'C'
    }

    @Test
    fun testWrapPayloadSingleChunk() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val chunks = TiqiaaProtocol.wrapPayload(payload, envelopeSeq = 1)
        assertEquals(1, chunks.size)
        val chunk = chunks[0]
        assertEquals(0x02.toByte(), chunk[0]) // Outbound indicator
        assertEquals((5 + 3).toByte(), chunk[1]) // chunkLen + 3
        assertEquals(1.toByte(), chunk[2]) // Envelope seq
        assertEquals(1.toByte(), chunk[3]) // Total chunks
        assertEquals(1.toByte(), chunk[4]) // Chunk index
        for (i in payload.indices) {
            assertEquals(payload[i], chunk[5 + i])
        }
    }

    @Test
    fun testWrapPayloadMultipleChunks() {
        val payload = ByteArray(120) { it.toByte() }
        val chunks = TiqiaaProtocol.wrapPayload(payload, envelopeSeq = 3)
        // 120 bytes / 56 = 3 chunks (56, 56, 8)
        assertEquals(3, chunks.size)

        // Chunk 1: 56 bytes payload
        assertEquals(0x02.toByte(), chunks[0][0])
        assertEquals((56 + 3).toByte(), chunks[0][1])
        assertEquals(3.toByte(), chunks[0][2])
        assertEquals(3.toByte(), chunks[0][3])
        assertEquals(1.toByte(), chunks[0][4])

        // Chunk 2: 56 bytes payload
        assertEquals(2.toByte(), chunks[1][4])

        // Chunk 3: 8 bytes payload
        assertEquals((8 + 3).toByte(), chunks[2][1])
        assertEquals(3.toByte(), chunks[2][4])
    }

    @Test
    fun testInboundPacketAssemblerSingleChunk() {
        val assembler = TiqiaaProtocol.InboundPacketAssembler()
        // Packet: [0x01, len+3, env, count, index, payload...]
        val packet = byteArrayOf(
            0x01, 8, 1, 1, 1,
            'S'.code.toByte(), 'T'.code.toByte(), 1, 'L'.code.toByte(), 'E'.code.toByte()
        )
        val complete = assembler.processPacket(packet)
        assertNotNull(complete)
        assertEquals(5, complete?.size)
        assertEquals('S'.code.toByte(), complete?.get(0))
    }

    @Test
    fun testInboundPacketAssemblerMultiChunk() {
        val assembler = TiqiaaProtocol.InboundPacketAssembler()
        val chunk1 = byteArrayOf(0x01, 6, 2, 2, 1, 10, 20, 30)
        val chunk2 = byteArrayOf(0x01, 5, 2, 2, 2, 40, 50)

        val firstResult = assembler.processPacket(chunk1)
        assertNull(firstResult) // Incomplete, needs chunk 2

        val secondResult = assembler.processPacket(chunk2)
        assertNotNull(secondResult)
        assertEquals(5, secondResult?.size)
        assertArrayEquals(byteArrayOf(10, 20, 30, 40, 50), secondResult)
    }

    @Test
    fun testInboundPacketAssemblerCorruptedSequenceResets() {
        val assembler = TiqiaaProtocol.InboundPacketAssembler()
        val chunk1 = byteArrayOf(0x01, 6, 2, 3, 1, 10, 20, 30)
        assertNull(assembler.processPacket(chunk1))

        // Out-of-order chunk (received index 3 instead of 2)
        val corruptChunk = byteArrayOf(0x01, 6, 2, 3, 3, 40, 50, 60)
        assertNull(assembler.processPacket(corruptChunk))

        // Assembler should have reset and accept a fresh new message
        val freshChunk = byteArrayOf(0x01, 5, 4, 1, 1, 99, 100)
        val freshResult = assembler.processPacket(freshChunk)
        assertNotNull(freshResult)
        assertArrayEquals(byteArrayOf(99, 100), freshResult)
    }

    @Test
    fun testParseFrameAndTimingDecode() {
        val rawPulses = intArrayOf(9000, 4500, 560, 1690, 560, 560)
        val frame = TiqiaaProtocol.encodePatternToFrame(rawPulses, seq = 3, meta = 0)

        val parsed = TiqiaaProtocol.parseFrame(frame)
        assertNotNull(parsed)
        assertEquals(5, parsed?.type) // Type 'D' = 5
        assertEquals(2, parsed?.status)

        val decoded = TiqiaaProtocol.decodeTimingPayload(parsed!!.payload)
        assertEquals(rawPulses.size, decoded.size)

        // Within 16 µs sampling tick error margin
        for (i in rawPulses.indices) {
            assertTrue(
                "Pulse $i mismatch: expected=${rawPulses[i]}, actual=${decoded[i]}",
                Math.abs(rawPulses[i] - decoded[i]) <= 16
            )
        }
    }

    @Test
    fun testDecodeTimingPayloadRejectsInitialSpace() {
        // First byte has MSB = 0 (SPACE), which is invalid for an IR capture
        val invalidFrame = byteArrayOf(
            'S'.code.toByte(), 'T'.code.toByte(), 1, 'D'.code.toByte(), 0,
            0x20.toByte(), // Polarity bit 0 -> Space!
            'E'.code.toByte(), 'N'.code.toByte()
        )
        val decoded = TiqiaaProtocol.decodeTimingPayload(invalidFrame)
        assertEquals(0, decoded.size)
    }
}
