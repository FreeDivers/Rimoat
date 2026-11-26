package org.qinyan.rimoat.hardware.serial

import org.qinyan.rimoat.hardware.IrSignal
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SerialIrSessionTest {
    private class Port(val toy: Boolean = false) : SerialPortIo {
        val writes = mutableListOf<ByteArray>()
        val replies = ArrayDeque<Byte>()
        var fragmented = true
        var wrongAck = false
        var missingAck = false
        var failWrite = false
        var cancelWrite = false
        var wrongCount = false
        var failComplete = false
        var badCapacity = false
        var firmware = "V222"
        var bytesSent = 0
        var transmitting = false
        fun enqueue(vararg bytes: Int) { replies.addAll(bytes.map { it.toByte() }) }
        override fun write(data: ByteArray, timeoutMs: Int) {
            if (cancelWrite) throw CancellationException("cancelled")
            if (failWrite) throw IOException("USB short write")
            writes += data.copyOf()
            if (!toy) {
                if (!missingAck) enqueue(if (wrongAck) 0xF2 else 0xF1)
                return
            }
            when {
                data.contentEquals(ByteArray(5)) -> { transmitting = false; replies.clear() }
                data.contentEquals(byteArrayOf(0x76)) -> replies.addAll(firmware.toByteArray().toList())
                data.contentEquals(byteArrayOf(0x53)) -> replies.addAll("S01".toByteArray().toList())
                data.contentEquals(byteArrayOf(0x26, 0x25, 0x24, 0x03)) -> { transmitting = true; enqueue(if (badCapacity) 1 else 62) }
                transmitting -> {
                    assertTrue(data.size <= 32 && data.size % 2 == 0)
                    bytesSent += data.size
                    enqueue(62)
                    if (data.takeLast(2) == listOf((-1).toByte(), (-1).toByte())) {
                        val count = bytesSent + if (wrongCount) 2 else 0
                        enqueue('t'.code, count ushr 8, count and 255, if (failComplete) 'F'.code else 'C'.code)
                        transmitting = false
                    }
                }
            }
        }
        override fun read(buffer: ByteArray, timeoutMs: Int): Int {
            if (replies.isEmpty()) return 0
            assertTrue("Driver reads must allow USB status headers and whole packets", buffer.size >= 64)
            val count = if (fragmented) 1 else minOf(buffer.size, replies.size)
            repeat(count) { buffer[it] = replies.removeFirst() }
            return count
        }
    }
    private val ys = SerialIrSettings(module = SerialIrModule.YS_IRTM)
    private val toy = SerialIrSettings(module = SerialIrModule.IR_TOY, baudRate = 115200)
    private val nec = IrSignal.Nec(0x12, 0x34)

    @Test fun ysWaitsForModuleAck() = runBlocking {
        val port = Port()
        SerialIrSession(port).transmit(ys, nec)
        assertEquals(1, port.writes.size)
        for (bad in listOf(Port().apply { wrongAck = true }, Port().apply { missingAck = true }, Port().apply { failWrite = true })) {
            try { SerialIrSession(bad).transmit(ys, nec); fail("Expected failure") } catch (_: IOException) {}
            assertTrue(bad.writes.size <= 1)
        }
    }

    @Test fun ysToleratesAsyncReceivedReportBeforeAck() = runBlocking {
        for (fragmented in listOf(true, false)) {
            val port = object : SerialPortIo {
                val writes = mutableListOf<ByteArray>()
                val replies = ArrayDeque<Byte>()
                override fun write(data: ByteArray, timeoutMs: Int) {
                    writes += data.copyOf()
                    // Module reports a 3-byte received NEC frame (addr0, addr1, cmd) before the 0xF1 transmit ACK.
                    replies.addAll(listOf(0x00, 0xFF, 0x45, 0xF1).map { it.toByte() })
                }
                override fun read(buffer: ByteArray, timeoutMs: Int): Int {
                    if (replies.isEmpty()) return 0
                    val count = if (fragmented) 1 else minOf(buffer.size, replies.size)
                    repeat(count) { buffer[it] = replies.removeFirst() }
                    return count
                }
            }
            SerialIrSession(port).transmit(ys, nec)
            assertEquals(1, port.writes.size)
        }
    }

    @Test fun ysRejectsExcessiveUnexpectedBytesEvenIfAckByteAppearsLater() = runBlocking {
        val port = object : SerialPortIo {
            val replies = ArrayDeque<Byte>()
            override fun write(data: ByteArray, timeoutMs: Int) {
                repeat(13) { replies.addLast(0x55) }
                replies.addLast(0xF1.toByte())
            }
            override fun read(buffer: ByteArray, timeoutMs: Int): Int {
                if (replies.isEmpty()) return 0
                val count = minOf(buffer.size, replies.size)
                repeat(count) { buffer[it] = replies.removeFirst() }
                return count
            }
        }
        try {
            SerialIrSession(port).transmit(ys, nec)
            fail("Expected failure on excessive unexpected bytes")
        } catch (_: IOException) {}
    }
    @Test fun invalidSignalNeverWritesAndCancellationPropagates() = runBlocking {
        val port = Port()
        try { SerialIrSession(port).transmit(ys, IrSignal.Raw(patternMicros = intArrayOf(9000, 4500))); fail() } catch (_: IllegalArgumentException) {}
        assertTrue(port.writes.isEmpty())
        port.cancelWrite = true
        try { SerialIrSession(port).transmit(ys, nec); fail() } catch (_: CancellationException) {}
    }
    @Test fun toyIdentifiesFirmwareStreamsWithHandshakeAndChecksReport() = runBlocking {
        for (fragmented in listOf(true, false)) {
            val port = Port(toy = true).apply { this.fragmented = fragmented }
            SerialIrSession(port).transmit(toy, nec)
            assertEquals(136, port.bytesSent)
            assertFalse(port.transmitting)
            assertTrue(port.writes.last().contentEquals(byteArrayOf(0x53)))
        }
    }
    @Test fun toyRejectsWrongFirmwareBufferCountsAndCompletion() = runBlocking {
        for (bad in listOf(Port(true).apply { firmware = "V221" }, Port(true).apply { badCapacity = true },
            Port(true).apply { wrongCount = true }, Port(true).apply { failComplete = true })) {
            try { SerialIrSession(bad).transmit(toy, nec); fail("Expected failure") } catch (_: IOException) {}
        }
    }
}
