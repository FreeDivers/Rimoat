package org.qinyan.rimoat.hardware.usb

import org.qinyan.rimoat.testing.AppStringsResourceRule
import org.qinyan.rimoat.hardware.IrSignal
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TiqiaaSessionTest {
    @get:org.junit.Rule val strings = AppStringsResourceRule()

    private class Channel : UsbBulkChannel {
        val input = ArrayDeque<ByteArray>()
        val writes = mutableListOf<ByteArray>()
        var sequence: Byte = 0
        var shortWrite = false
        var noAck = false
        var wrongSequence = false
        var errorAck = false
        var cancelWrite = false
        override fun read(buffer: ByteArray, timeoutMs: Int): Int {
            val data = input.removeFirstOrNull() ?: return 0
            data.copyInto(buffer)
            return data.size
        }
        override fun write(data: ByteArray, timeoutMs: Int): Int {
            if (cancelWrite) throw CancellationException("cancelled")
            writes += data.copyOf()
            if (shortWrite) return data.size - 1
            if (data[4].toInt() == 1) sequence = data[7]
            if (data[4] == data[3] && !noAck) {
                // Physical device returns 63 bytes with the 7-byte ST...O09EN frame
                // followed by old bytes; framing must honor the declared length.
                val packet = ByteArray(63) { 0x5F }
                byteArrayOf(1, 10, 6, 1, 1, 0x53, 0x54,
                    if (wrongSequence) (sequence + 1).toByte() else sequence,
                    if (errorAck) 0x42 else 0x4F, 9, 0x45, 0x4E).copyInto(packet)
                input.add(packet)
            }
            return data.size
        }
    }
    private val signal = IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560))

    @Test fun waitsForMatchingRealDeviceReplyAndIgnoresPadding() = runBlocking {
        val channel = Channel()
        TiqiaaSession(channel).transmit(signal)
        assertEquals(1, channel.writes.size)
        assertTrue(channel.input.isEmpty())
    }

    @Test fun missingWrongOrErrorRepliesNeverReportSuccess() = runBlocking {
        for (channel in listOf(Channel().apply { noAck = true }, Channel().apply { wrongSequence = true },
            Channel().apply { errorAck = true })) {
            try { TiqiaaSession(channel, 30).transmit(signal); fail("Expected device response failure") }
            catch (_: IOException) {}
        }
    }

    @Test fun partialWriteIsNotRetriedAndCancellationPropagates() = runBlocking {
        val channel = Channel().apply { shortWrite = true }
        try { TiqiaaSession(channel).transmit(signal); fail() } catch (_: IOException) {}
        assertEquals(1, channel.writes.size)
        channel.cancelWrite = true
        try { TiqiaaSession(channel).transmit(signal); fail() } catch (_: CancellationException) {}
    }
}
