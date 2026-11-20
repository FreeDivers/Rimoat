package org.qinyan.rimoat.learning

import org.qinyan.rimoat.hardware.learning.ElkSmartProtocol
import org.qinyan.rimoat.hardware.learning.UsbDeviceFilter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElkSmartProtocolTest {

    @Test
    fun testHasPrefix() {
        val auth = ElkSmartProtocol.AUTHORIZE
        assertTrue(ElkSmartProtocol.hasPrefix(auth, 0xFC))
        assertFalse(ElkSmartProtocol.hasPrefix(auth, 0xFE))

        val startLearn = ElkSmartProtocol.START_LEARN
        assertTrue(ElkSmartProtocol.hasPrefix(startLearn, 0xFE))
    }

    @Test
    fun testIsAuthorizeResponse() {
        // 6-byte packet with 0xFC prefix and 2-byte subtype (e.g. 0x70 0x01)
        val validResp = byteArrayOf(0xFC.toByte(), 0xFC.toByte(), 0xFC.toByte(), 0xFC.toByte(), 0x70, 0x01)
        assertTrue(ElkSmartProtocol.isAuthorizeResponse(validResp))

        // Wrong length
        val shortResp = byteArrayOf(0xFC.toByte(), 0xFC.toByte(), 0xFC.toByte(), 0xFC.toByte())
        assertFalse(ElkSmartProtocol.isAuthorizeResponse(shortResp))

        // Wrong prefix
        val wrongPrefix = byteArrayOf(0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0x70, 0x01)
        assertFalse(ElkSmartProtocol.isAuthorizeResponse(wrongPrefix))
    }

    @Test
    fun testIsLearnResponseAndExpectedLength() {
        val packet = byteArrayOf(
            0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(),
            0x01, 0x2C.toByte(), // Big-endian 0x012C = 300 bytes
            10, 20, 30
        )
        assertTrue(ElkSmartProtocol.isLearnResponse(packet))
        assertEquals(300, ElkSmartProtocol.getExpectedPayloadLength(packet))
    }

    @Test
    fun testDecodeLearnPayloadCarry() {
        // 9000 µs = 2 * 4080 (0xFF0) + 840 (840 / 16 = 52.5 -> approx 52 * 16 = 832, 2 * 4080 + 840)
        // Testing carry decoding with 0xFF bytes
        // 1st run: 0xFF (+4080), 0xFF (+4080), 0x34 (52 * 16 = 832 + 8160 = 8992)
        val payload = listOf(0xFF.toByte(), 0xFF.toByte(), 0x34.toByte())
        val decodedBytes = ElkSmartProtocol.decodeLearnPayload(payload)
        assertNotNull(decodedBytes)
        assertEquals(4, decodedBytes?.size)

        val pattern = ElkSmartProtocol.decodeOpaqueFrameToPattern(decodedBytes!!)
        assertEquals(1, pattern.size)
        assertEquals(8992, pattern[0])
    }

    @Test
    fun testDecodeLearnPayloadUnresolvedCarryReturnsNull() {
        // Trailing 0xFF without subsequent byte to resolve carry -> truncated payload
        val truncatedPayload = listOf(0x20.toByte(), 0xFF.toByte())
        val result = ElkSmartProtocol.decodeLearnPayload(truncatedPayload)
        assertNull(result)
    }

    @Test
    fun testDecodeOpaqueFrameToPattern() {
        // Little-endian words:
        // [0x50, 0x00, 0x00, 0x00] = 80
        // [0xE8, 0x03, 0x00, 0x00] = 1000
        val frame = byteArrayOf(
            0x50, 0x00, 0x00, 0x00,
            0xE8.toByte(), 0x03, 0x00, 0x00
        )
        val pattern = ElkSmartProtocol.decodeOpaqueFrameToPattern(frame)
        assertEquals(2, pattern.size)
        assertEquals(80, pattern[0])
        assertEquals(1000, pattern[1])
    }

    @Test
    fun testRoundTripEncodeAndDecode() {
        val testPulses = intArrayOf(9000, 4500, 560, 1690, 560, 560)
        val payloadBytes = ElkSmartProtocol.encodePatternToPayload(testPulses)

        val decodedOpaque = ElkSmartProtocol.decodeLearnPayload(payloadBytes.toList())
        assertNotNull(decodedOpaque)

        val decodedPulses = ElkSmartProtocol.decodeOpaqueFrameToPattern(decodedOpaque!!)
        assertEquals(testPulses.size, decodedPulses.size)

        // Verify accurate within quantization step (16 µs)
        for (i in testPulses.indices) {
            assertTrue(
                "Pulse $i mismatch: expected=${testPulses[i]}, actual=${decodedPulses[i]}",
                Math.abs(testPulses[i] - decodedPulses[i]) <= 16
            )
        }
    }

    @Test
    fun testUsbDeviceFilterDetection() {
        // Supported USB receiver identifiers
        assertTrue(UsbDeviceFilter.isTiqiaa(0x10C4, 0x8468))
        assertTrue(UsbDeviceFilter.isTiqiaa(0x045E, 0x8468))
        assertFalse(UsbDeviceFilter.isTiqiaa(0x1234, 0x8468))

        // ElkSmart
        assertTrue(UsbDeviceFilter.isElkSmart(0x045C, 0x0131))
        assertTrue(UsbDeviceFilter.isElkSmart(0x045C, 0x0132))
        assertTrue(UsbDeviceFilter.isElkSmart(0x045C, 0x0134))
        assertTrue(UsbDeviceFilter.isElkSmart(0x045C, 0x014A))
        assertTrue(UsbDeviceFilter.isElkSmart(0x045C, 0x0184))
        assertTrue(UsbDeviceFilter.isElkSmart(0x045C, 0x0195))
        assertTrue(UsbDeviceFilter.isElkSmart(0x045C, 0x02AA))
        assertFalse(UsbDeviceFilter.isElkSmart(0x045C, 0x9999))

        // Supported check
        assertTrue(UsbDeviceFilter.isSupported(0x10C4, 0x8468))
        assertTrue(UsbDeviceFilter.isSupported(0x045C, 0x02AA))
        assertFalse(UsbDeviceFilter.isSupported(0x1234, 0x5678))
    }
}
