package org.qinyan.rimoat.hardware.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ElkSmartProtocolFormatterTest {

    @Test
    fun testMangleByteMathematicalProperties() {
        // v = 0: bit reverse 0 -> ~0 = 0xFF
        assertEquals(0xFF.toByte(), ElkSmartProtocolFormatter.mangleByte(0))
        // v = 0xFF: bit reverse 0xFF -> ~0xFF = 0x00
        assertEquals(0x00.toByte(), ElkSmartProtocolFormatter.mangleByte(0xFF))
        // v = 0x01 (00000001b): reversed = 10000000b (0x80) -> inverted = 01111111b (0x7F)
        assertEquals(0x7F.toByte(), ElkSmartProtocolFormatter.mangleByte(0x01))
    }

    @Test
    fun testCompressValueUsSmallAndLarge() {
        val outSmall = ByteArrayOutputStream()
        ElkSmartProtocolFormatter.compressValueUs(560, outSmall)
        // 560 / 16 = 35
        assertEquals(1, outSmall.size())
        assertEquals(35.toByte(), outSmall.toByteArray()[0])

        val outLarge = ByteArrayOutputStream()
        // 9000us > 2032us -> varint
        ElkSmartProtocolFormatter.compressValueUs(9000, outLarge)
        assertTrue(outLarge.size() >= 2)
    }

    @Test
    fun testCompressPulsePairsDictionaryHeaderAndBody() {
        // Create NEC-like sequence:
        // Leader: 9000, 4500
        // Repeated Bit 0: 560, 560 (8 times)
        // Repeated Bit 1: 560, 1690 (8 times)
        val list = ArrayList<Int>()
        list.add(9000); list.add(4500)
        repeat(8) { list.add(560); list.add(560) }
        repeat(8) { list.add(560); list.add(1690) }

        val compressed = ElkSmartProtocolFormatter.compressPulsePairs(list.toIntArray())

        // Must contain dictionary delimiter: 0xFF, 0xFF, 0xFF
        var delimiterIndex = -1
        for (i in 0 until compressed.size - 2) {
            if ((compressed[i].toInt() and 0xFF) == 0xFF &&
                (compressed[i + 1].toInt() and 0xFF) == 0xFF &&
                (compressed[i + 2].toInt() and 0xFF) == 0xFF
            ) {
                delimiterIndex = i
                break
            }
        }
        assertTrue("Dictionary delimiter 0xFF,0xFF,0xFF must be present", delimiterIndex > 0)

        // Body begins after delimiter: should contain frequent pair indices 0x00 and 0x01
        val body = compressed.copyOfRange(delimiterIndex + 3, compressed.size)
        assertTrue(body.contains(0x00.toByte()))
        assertTrue(body.contains(0x01.toByte()))
    }

    @Test
    fun testCalculateChecksum62() {
        val chunk62 = ByteArray(62) { 1 } // sum = 62 (0x3E)
        // x = (62 & 0xF0) | ((62 >> 8) & 0x0F) = 0x30 | 0 = 0x30 (00110000b)
        // reversed = 00001100b (0x0C) -> inverted = 11110011b (0xF3)
        val expected = ElkSmartProtocolFormatter.mangleByte(0x30)
        val actual = ElkSmartProtocolFormatter.calculateChecksum62(chunk62)
        assertEquals(expected, actual)
    }

    @Test
    fun testEncodeFullChunkGenerates63Bytes() {
        // Generate enough pulses to exceed 62 bytes payload
        val longPattern = IntArray(150) { 560 }
        val packets = ElkSmartProtocolFormatter.encode(38000, longPattern)

        assertTrue(packets.isNotEmpty())
        if (packets.size > 1) {
            // First chunk must be 63 bytes (62 bytes + checksum62)
            assertEquals(63, packets[0].size)
        }
    }
}

