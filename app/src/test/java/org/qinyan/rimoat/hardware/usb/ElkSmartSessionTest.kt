package org.qinyan.rimoat.hardware.usb

import org.qinyan.rimoat.testing.AppStringsResourceRule
import org.qinyan.rimoat.hardware.IrSignal
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ElkSmartSessionTest {
    @get:org.junit.Rule val strings = AppStringsResourceRule()

    private class Channel(vararg responses: ByteArray) : UsbBulkChannel {
        val reads = ArrayDeque(responses.toList())
        val writes = mutableListOf<ByteArray>()
        var shortWrite = false
        var cancelWrite = false
        override fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val response = reads.removeFirstOrNull() ?: return 0
            response.copyInto(buffer)
            return response.size
        }
        override fun write(data: ByteArray, timeoutMs: Int): Int {
            if (cancelWrite) throw CancellationException("detached")
            writes += data.copyOf()
            return data.size - if (shortWrite) 1 else 0
        }
    }
    private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()

    @Test fun recognizesBothModelsAndSplitReplyBeforeSending() = runTest {
        for ((reply, subtype) in listOf(
            bytes(0x70, 1) to ElkSmartProtocolFormatter.Subtype.D552,
            bytes(2, 0xAA) to ElkSmartProtocolFormatter.Subtype.D226
        )) {
            val channel = Channel(byteArrayOf(), bytes(0xFC, 0xFC, 0xFC), bytes(0xFC) + reply)
            val session = ElkSmartSession(channel)
            assertEquals(subtype, session.identify())
            assertEquals(1, channel.writes.size)
            assertArrayEquals(ElkSmartProtocolFormatter.HANDSHAKE_PROBE, channel.writes.single())
            session.transmit(IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560)), subtype)
            assertTrue(channel.writes.size > 1)
        }
    }

    @Test fun unknownAndMissingRepliesDoNotFallBackOrSendData() = runTest {
        for (channel in listOf(Channel(), Channel(byteArrayOf(), bytes(0xFC, 0xFC, 0xFC, 0xFC, 7, 8)))) {
            try { ElkSmartSession(channel).identify(); fail("Expected identification error") } catch (_: IOException) {}
            assertEquals(1, channel.writes.size)
        }
    }

    @Test fun shortWriteStopsAtFirstPacketAndCancellationPropagates() = runTest {
        val channel = Channel().apply { shortWrite = true }
        try { ElkSmartSession(channel).identify(); fail() } catch (_: IOException) {}
        channel.writes.clear()
        try {
            ElkSmartSession(channel).transmit(IrSignal.Raw(patternMicros = IntArray(150) { 560 }), ElkSmartProtocolFormatter.Subtype.D552)
            fail()
        } catch (_: IOException) {}
        assertEquals(1, channel.writes.size)
        channel.cancelWrite = true
        try { ElkSmartSession(channel).identify(); fail() } catch (_: CancellationException) {}
    }

    @Test fun d226ReferenceVectorsAndAllPaddingCounts() {
        assertArrayEquals(bytes(0, 2, 0, 0, 2, 1, 0, 1, 5, 0xA0), ElkSmartD226Codec.encode(bytes(0, 1, 0)))
        assertArrayEquals(bytes(0, 1, 7, 0, 3, 5, 0), ElkSmartD226Codec.encode(bytes(7, 7, 7)))
        // Equal-frequency symbols exercise Java heap tie ordering used by the firmware reference.
        assertArrayEquals(bytes(0, 4, 0, 0, 1, 1, 0, 1, 2, 0, 1, 3, 0, 1, 0, 0x39), ElkSmartD226Codec.encode(bytes(0, 1, 2, 3)))
        for (length in 2..9) {
            val output = ElkSmartD226Codec.encode(ByteArray(length) { (it % 2).toByte() })
            val padding = output[8].toInt()
            assertEquals((8 - length % 8) % 8, padding)
            assertEquals(length, (output.size - 9) * 8 - padding)
            assertEquals(0, output.last().toInt() and ((1 shl padding) - 1))
        }
    }

    @Test fun headerUsesCompressedLengthAndRejectsInvalidPulses() {
        val p = intArrayOf(9000, 4500, 560, 560, 560, 1690, 560)
        val payload = ElkSmartProtocolFormatter.buildPayload(38000, p, ElkSmartProtocolFormatter.Subtype.D226)
        val length = ((ElkSmartProtocolFormatter.mangleByte(payload[7].toInt()).toInt() and 255) shl 8) or
            (ElkSmartProtocolFormatter.mangleByte(payload[8].toInt()).toInt() and 255)
        assertEquals(payload.size - 9, length)
        assertArrayEquals(ElkSmartD226Codec.encode(ElkSmartProtocolFormatter.compressPulsePairs(p)), payload.copyOfRange(9, payload.size))
        for (bad in listOf(intArrayOf(), intArrayOf(0), intArrayOf(-1), intArrayOf(Int.MIN_VALUE), intArrayOf(2_000_000))) {
            assertThrows(IllegalArgumentException::class.java) { ElkSmartProtocolFormatter.buildPayload(38000, bad) }
        }
    }
}
