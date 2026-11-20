package org.qinyan.rimoat.hardware.usb

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import java.io.ByteArrayOutputStream

/**
 * Protocol framing for USB IR dongles with handshake and pulse dictionary compression.
 *
 * Implements:
 * - 4-byte 0xFC handshake probe
 * - MangleByte bit-reversal and one's complement obfuscation
 * - Pulse pair dictionary statistical compression
 * - 62-byte chunking with 63rd byte checksum62 verification
 */
object ElkSmartProtocolFormatter {
    enum class Subtype { D552, D226 }
    val HANDSHAKE_PROBE = byteArrayOf(0xFC.toByte(), 0xFC.toByte(), 0xFC.toByte(), 0xFC.toByte())

    fun identify(response: ByteArray): Subtype? {
        if (response.size < 6 || (0..3).any { response[it] != 0xFC.toByte() }) return null
        return when ((response[4].toInt() and 255) * 256 + (response[5].toInt() and 255)) {
            0x7001 -> Subtype.D552
            0x02AA -> Subtype.D226
            else -> null
        }
    }

    data class PulsePair(val onUs: Int, val offUs: Int)

    /**
     * Obfuscation transformation: Reverses bits of an 8-bit integer and inverts them (one's complement).
     * Formula: mangleByte(v) = (~bitReverse8(v)) & 0xFF
     */
    fun mangleByte(v: Int): Byte {
        var value = v and 0xFF
        var reversed = 0
        repeat(8) {
            reversed = (reversed shl 1) or (value and 1)
            value = value ushr 1
        }
        return (reversed.inv() and 0xFF).toByte()
    }

    /**
     * Compresses a microsecond duration according to ElkSmart varint / 16µs quantization rules.
     */
    fun compressValueUs(valueUs: Int, out: ByteArrayOutputStream) {
        val us = valueUs.coerceAtLeast(0)
        if (us <= 2032) {
            val q = if (us <= 1) us else ((us / 16.0) + 0.5).toInt()
            out.write(q and 0xFF)
            return
        }

        var v = us
        while (true) {
            var b = v and 0x7F
            v = v ushr 7
            if (v != 0) b = b or 0x80
            // 0xFF is reserved as dictionary delimiter, clamp to 0xFE
            if ((b and 0xFF) == 0xFF) b = 0xFE
            out.write(b and 0xFF)
            if (v == 0) break
        }
    }

    /**
     * Compresses raw microsecond pulse pattern using pulse-pair frequency dictionary.
     */
    fun compressPulsePairs(patternMicros: IntArray): ByteArray {
        if (patternMicros.isEmpty()) return ByteArray(0)

        // Parse alternating mark/space into pairs; pad trailing space if odd count
        val pairs = ArrayList<PulsePair>((patternMicros.size + 1) / 2)
        var i = 0
        while (i < patternMicros.size) {
            val on = patternMicros[i]
            val off = if (i + 1 < patternMicros.size) patternMicros[i + 1] else 10000
            pairs.add(PulsePair(on, off))
            i += 2
        }

        // Count frequency of pairs
        val freqMap = HashMap<PulsePair, Int>()
        for (p in pairs) {
            freqMap[p] = (freqMap[p] ?: 0) + 1
        }

        // Identify top 2 frequent pairs
        val sortedPairs = freqMap.entries.sortedByDescending { it.value }
        val p1 = sortedPairs.getOrNull(0)?.key ?: PulsePair(560, 560)
        val p2 = sortedPairs.getOrNull(1)?.key ?: (if (sortedPairs.isNotEmpty()) sortedPairs[0].key else PulsePair(560, 1690))

        val out = ByteArrayOutputStream()

        // 1. Dictionary Header: P2.on, P2.off, P1.on, P1.off, then 0xFF, 0xFF, 0xFF
        compressValueUs(p2.onUs, out)
        compressValueUs(p2.offUs, out)
        compressValueUs(p1.onUs, out)
        compressValueUs(p1.offUs, out)
        out.write(0xFF)
        out.write(0xFF)
        out.write(0xFF)

        // 2. Body: 0x00 for P1, 0x01 for P2, otherwise raw pair values
        for (p in pairs) {
            if (p == p1) {
                out.write(0x00)
            } else if (p == p2) {
                out.write(0x01)
            } else {
                compressValueUs(p.onUs, out)
                compressValueUs(p.offUs, out)
            }
        }

        return out.toByteArray()
    }

    /**
     * Builds the complete ElkSmart payload with 9-byte header and compressed data.
     */
    fun buildPayload(frequencyHz: Int, patternMicros: IntArray, subtype: Subtype = Subtype.D552): ByteArray {
        require(frequencyHz in 1..1_000_000) { AppStrings.get(R.string.msg_ir_carrier_invalid, "无效的红外载波频率") }
        require(patternMicros.isNotEmpty() && patternMicros.all { it > 0 }) { AppStrings.get(R.string.msg_ir_pulses_positive, "红外脉冲不能为空或包含非正数") }
        require(patternMicros.sumOf { it.toLong() } < 2_000_000) { AppStrings.get(R.string.msg_ir_frame_long, "红外帧超过 2 秒") }
        val dictionary = compressPulsePairs(patternMicros)
        val compressed = if (subtype == Subtype.D226) ElkSmartD226Codec.encode(dictionary) else dictionary
        require(compressed.size <= 65535) { AppStrings.get(R.string.msg_ir_packet_long, "红外数据包过长") }
        val f = frequencyHz + 0x7FFFF
        val len = compressed.size

        val out = ByteArrayOutputStream(9 + len)
        out.write(0xFF)
        out.write(0xFF)
        out.write(0xFF)
        out.write(0xFF)
        out.write(mangleByte(f ushr 8).toInt() and 0xFF)
        out.write(mangleByte(f ushr 16).toInt() and 0xFF)
        out.write(mangleByte(f).toInt() and 0xFF)
        out.write(mangleByte(len ushr 8).toInt() and 0xFF)
        out.write(mangleByte(len).toInt() and 0xFF)
        out.write(compressed)

        return out.toByteArray()
    }

    /**
     * Calculates 62-byte chunk checksum:
     * sum = sum of all 62 bytes
     * x = (sum & 0xF0) | ((sum >> 8) & 0x0F)
     * checksum = mangleByte(x)
     */
    fun calculateChecksum62(chunk: ByteArray): Byte {
        require(chunk.size == 62) { "Chunk must be exactly 62 bytes for checksum62 calculation" }
        var sum = 0
        for (b in chunk) {
            sum += (b.toInt() and 0xFF)
        }
        val x = (sum and 0xF0) or ((sum ushr 8) and 0x0F)
        return mangleByte(x)
    }

    /**
     * Chunks payload into USB transmission packets (63 bytes for full 62-byte chunk with checksum62,
     * actual size for trailing chunk < 62 bytes).
     */
    fun encode(frequencyHz: Int, patternMicros: IntArray, subtype: Subtype = Subtype.D552): List<ByteArray> {
        val payload = buildPayload(frequencyHz, patternMicros, subtype)
        val packets = ArrayList<ByteArray>()

        var offset = 0
        while (offset < payload.size) {
            val remaining = payload.size - offset
            if (remaining >= 62) {
                val packet = ByteArray(63)
                System.arraycopy(payload, offset, packet, 0, 62)
                packet[62] = calculateChecksum62(packet.copyOfRange(0, 62))
                packets.add(packet)
                offset += 62
            } else {
                val packet = ByteArray(remaining)
                System.arraycopy(payload, offset, packet, 0, remaining)
                packets.add(packet)
                offset += remaining
            }
        }

        return packets
    }

    fun calculatePostTransmitDelayMs(patternMicros: IntArray): Long {
        var totalMicros = 0L
        for (d in patternMicros) {
            totalMicros += d.coerceAtLeast(0)
        }
        return (totalMicros / 1000L + 25L).coerceAtLeast(80L)
    }
}

