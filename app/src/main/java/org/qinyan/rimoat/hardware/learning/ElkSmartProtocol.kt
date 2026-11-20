package org.qinyan.rimoat.hardware.learning

/**
 * Protocol commands, handshake framing, and carry-run decoding for ElkSmart USB IR dongles.
 */
object ElkSmartProtocol {
    val AUTHORIZE = byteArrayOf(0xFC.toByte(), 0xFC.toByte(), 0xFC.toByte(), 0xFC.toByte())
    val AUTHORIZE_ACK = byteArrayOf(0xFA.toByte(), 0xFA.toByte(), 0xFA.toByte(), 0xFA.toByte())
    val START_LEARN = byteArrayOf(0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte())
    val STOP_LEARN = byteArrayOf(0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte(), 0xFD.toByte())

    /**
     * Checks if packet has a 4-byte prefix matching the specified byte.
     */
    fun hasPrefix(packet: ByteArray, byteVal: Int): Boolean {
        return packet.size >= 4 &&
                (packet[0].toInt() and 0xFF) == byteVal &&
                (packet[1].toInt() and 0xFF) == byteVal &&
                (packet[2].toInt() and 0xFF) == byteVal &&
                (packet[3].toInt() and 0xFF) == byteVal
    }

    /**
     * Checks if packet is a valid 6-byte authorization response.
     */
    fun isAuthorizeResponse(packet: ByteArray): Boolean {
        return packet.size == 6 && hasPrefix(packet, 0xFC)
    }

    /**
     * Checks if packet is the initial packet of a learned IR signal.
     */
    fun isLearnResponse(packet: ByteArray): Boolean {
        return packet.size >= 6 && hasPrefix(packet, 0xFE)
    }

    /**
     * Reads expected big-endian payload length from header bytes 4 and 5.
     */
    fun getExpectedPayloadLength(packet: ByteArray): Int {
        if (packet.size < 6) return -1
        return ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
    }

    /**
     * Decodes carry-based variable-length runs into 32-bit little-endian microsecond words.
     *
     * In ElkSmart protocol:
     * - Each byte < 0xFF represents (byte * 16 + carry) microseconds.
     * - A byte of 0xFF adds 0xFF0 (4080 µs) to the accumulator carry.
     * - An unresolved carry at the end indicates truncated transmission and returns null.
     */
    fun decodeLearnPayload(payload: List<Byte>): ByteArray? {
        val out = ArrayList<Byte>(payload.size * 4)
        var carry = 0

        for (b in payload) {
            val value = b.toInt() and 0xFF
            if (value < 0xFF) {
                val decoded = value * 16 + carry
                if (decoded == 0) return null
                out.add((decoded and 0xFF).toByte())
                out.add(((decoded ushr 8) and 0xFF).toByte())
                out.add(((decoded ushr 16) and 0xFF).toByte())
                out.add(((decoded ushr 24) and 0xFF).toByte())
                carry = 0
            } else {
                carry += 0xFF0
            }
        }

        if (carry != 0) return null
        return out.toByteArray()
    }

    /**
     * Converts a 32-bit LE byte array directly into alternating microsecond pulse durations.
     */
    fun decodeOpaqueFrameToPattern(frame: ByteArray): IntArray {
        if (frame.isEmpty()) return IntArray(0)
        require(frame.size % 4 == 0) { "Frame size must be a multiple of 4 bytes" }
        val out = IntArray(frame.size / 4)
        var src = 0
        var dst = 0
        while (src < frame.size) {
            out[dst++] = (frame[src].toInt() and 0xFF) or
                    ((frame[src + 1].toInt() and 0xFF) shl 8) or
                    ((frame[src + 2].toInt() and 0xFF) shl 16) or
                    ((frame[src + 3].toInt() and 0xFF) shl 24)
            src += 4
        }
        return out
    }

    /**
     * Helper to encode microsecond pulses into an ElkSmart carry-encoded payload (useful for simulation & testing).
     */
    fun encodePatternToPayload(patternMicros: IntArray): ByteArray {
        val bytes = ArrayList<Byte>()
        for (dur in patternMicros) {
            var remaining = dur
            while (remaining >= 0xFF0) {
                bytes.add(0xFF.toByte())
                remaining -= 0xFF0
            }
            val b = (remaining / 16).coerceIn(0, 0xFE)
            bytes.add(b.toByte())
        }
        return bytes.toByteArray()
    }
}
