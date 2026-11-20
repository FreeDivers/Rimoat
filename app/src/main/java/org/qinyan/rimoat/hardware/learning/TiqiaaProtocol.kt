package org.qinyan.rimoat.hardware.learning

/**
 * Protocol framing, packetization, and pulse stream decoding for supported USB IR dongles.
 */
object TiqiaaProtocol {
    const val MAX_CHUNK = 0x38 // 56 bytes max payload per USB packet

    val MODE_BYTES = byteArrayOf(
        'L'.code.toByte(), // 0: Local / Idle
        'S'.code.toByte(), // 1: Send / Transmit
        'R'.code.toByte(), // 2: Receive / Learn
        'H'.code.toByte(), // 3: Handshake
        'O'.code.toByte(), // 4: Open
        'L'.code.toByte(), // 5: Secondary Local
        'C'.code.toByte(), // 6: Cancel
        'V'.code.toByte(), // 7: Version
    )

    data class ParsedFrame(
        val type: Int,
        val status: Int,
        val payload: ByteArray,
        val quality: Int = 0,
        val meta: Int = 0
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ParsedFrame
            return type == other.type &&
                    status == other.status &&
                    quality == other.quality &&
                    meta == other.meta &&
                    payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = type
            result = 31 * result + status
            result = 31 * result + quality
            result = 31 * result + meta
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    /**
     * Builds a command payload to switch device mode.
     */
    fun buildModeCommand(mode: Int, seq: Byte): ByteArray {
        require(mode in MODE_BYTES.indices) { "Invalid mode index: $mode" }
        return byteArrayOf(
            'S'.code.toByte(),
            'T'.code.toByte(),
            seq,
            MODE_BYTES[mode],
            'E'.code.toByte(),
            'N'.code.toByte()
        )
    }

    /**
     * Wraps an arbitrary payload into 0x02 envelope chunks (max 56 bytes payload each).
     */
    fun wrapPayload(payload: ByteArray, envelopeSeq: Byte): List<ByteArray> {
        val total = ((payload.size + MAX_CHUNK - 1) / MAX_CHUNK).coerceAtLeast(1)
        val frames = ArrayList<ByteArray>(total)
        var offset = 0
        var index = 1
        while (offset < payload.size) {
            val take = minOf(MAX_CHUNK, payload.size - offset)
            val frame = ByteArray(5 + take)
            frame[0] = 0x02
            frame[1] = (take + 3).toByte()
            frame[2] = envelopeSeq
            frame[3] = total.toByte()
            frame[4] = index.toByte()
            System.arraycopy(payload, offset, frame, 5, take)
            frames.add(frame)
            offset += take
            index++
        }
        return frames
    }

    /**
     * State machine to reassemble inbound 0x01 USB packets into complete frames.
     */
    class InboundPacketAssembler(private val maxBufferSize: Int = 1024) {
        private val aggregate = ByteArray(maxBufferSize)
        private var aggregateLen = 0
        private var inboundEnvelope = 0
        private var inboundCount = 0
        private var inboundNextIndex = 0

        fun reset() {
            aggregateLen = 0
            inboundEnvelope = 0
            inboundCount = 0
            inboundNextIndex = 0
        }

        /**
         * Processes one raw packet received from bulk IN endpoint.
         *
         * @return Complete aggregated frame if all chunks have arrived, or null if more chunks needed or invalid.
         */
        fun processPacket(data: ByteArray, receivedLength: Int = data.size): ByteArray? {
            if (receivedLength < 5 || receivedLength > data.size) return null
            if (data[0].toInt() != 0x01) return null

            val chunkLen = (data[1].toInt() and 0xFF) - 3
            if (chunkLen <= 0 || chunkLen > MAX_CHUNK || 5 + chunkLen > receivedLength) {
                return null
            }

            val envelope = data[2].toInt() and 0xFF
            val count = data[3].toInt() and 0xFF
            val index = data[4].toInt() and 0xFF
            if (count == 0 || index !in 1..count) return null

            val offset = if (index == 1) {
                inboundEnvelope = envelope
                inboundCount = count
                0
            } else {
                if (aggregateLen == 0 || envelope != inboundEnvelope ||
                    count != inboundCount || index != inboundNextIndex
                ) {
                    reset()
                    return null
                }
                aggregateLen
            }

            if (offset + chunkLen > aggregate.size) {
                reset()
                return null
            }

            System.arraycopy(data, 5, aggregate, offset, chunkLen)
            inboundNextIndex = index + 1
            val newLen = offset + chunkLen
            aggregateLen = newLen

            if (index == count) {
                val completeFrame = aggregate.copyOf(newLen)
                reset()
                return completeFrame
            }
            return null
        }
    }

    /**
     * Parses an aggregated frame and extracts type, status, and payload.
     */
    fun parseFrame(frame: ByteArray): ParsedFrame? {
        if (frame.size < 7 ||
            frame[0] != 'S'.code.toByte() ||
            frame[1] != 'T'.code.toByte() ||
            frame[frame.size - 2] != 'E'.code.toByte() ||
            frame.last() != 'N'.code.toByte()
        ) {
            return null
        }

        val meta = frame[4].toInt() and 0xFF
        val type = when (frame[3].toInt().toChar()) {
            'L' -> 0
            'S' -> 1
            'R' -> 2
            'H' -> 3
            'O' -> 4
            'D' -> 5
            'C' -> 6
            'V' -> 7
            'B' -> 8
            else -> return null
        }

        if (type == 5) {
            val preview = decodeTimingPayload(frame)
            if (preview.isEmpty()) return null
            return ParsedFrame(type = type, status = 2, payload = frame.copyOf(), quality = 0, meta = meta)
        }

        val innerPayload = if (frame.size > 7) frame.copyOfRange(5, frame.size - 2) else ByteArray(0)
        return ParsedFrame(type = type, status = (meta shr 3) and 0x3, payload = innerPayload, quality = 0, meta = meta)
    }

    /**
     * Decodes 16µs polarity-encoded timing bytes from a Type 'D' frame into alternating microsecond pulse durations.
     */
    fun decodeTimingPayload(frame: ByteArray): IntArray {
        if (frame.size < 8) return IntArray(0)
        val bodyEndExclusive = frame.size - 2
        if (bodyEndExclusive <= 5) return IntArray(0)

        val out = ArrayList<Int>(64)
        var currentPolarity = true
        var units = 0

        for (i in 5 until bodyEndExclusive) {
            val b = frame[i].toInt() and 0xFF
            val polarity = (b and 0x80) != 0
            val value = b and 0x7F

            // A zero-duration byte must not create a mark/space transition.
            if (value == 0) continue

            // The very first pulse MUST be a MARK (carrier ON). If SPACE comes first, invalid.
            if (units == 0 && out.isEmpty() && !polarity) return IntArray(0)

            if (polarity == currentPolarity) {
                units += value
            } else {
                if (units > 0) {
                    out.add((units * 16).coerceAtLeast(1))
                }
                currentPolarity = polarity
                units = value
            }
        }

        if (units > 0) {
            out.add((units * 16).coerceAtLeast(1))
        }

        return out.toIntArray()
    }

    /**
     * Helper to encode microsecond pulses into a Type 'D' frame (useful for simulation & testing).
     */
    fun encodePatternToFrame(patternMicros: IntArray, seq: Byte = 1, meta: Byte = 0): ByteArray {
        val bytes = ArrayList<Byte>()
        bytes.add('S'.code.toByte())
        bytes.add('T'.code.toByte())
        bytes.add(seq)
        bytes.add('D'.code.toByte())
        bytes.add(meta)

        var isMark = true
        for (p in patternMicros) {
            var units = (p + 8) / 16
            if (units <= 0) units = 1
            while (units > 0) {
                val take = minOf(127, units)
                val b = if (isMark) (0x80 or take) else take
                bytes.add(b.toByte())
                units -= take
            }
            isMark = !isMark
        }

        bytes.add('E'.code.toByte())
        bytes.add('N'.code.toByte())
        return bytes.toByteArray()
    }
}
