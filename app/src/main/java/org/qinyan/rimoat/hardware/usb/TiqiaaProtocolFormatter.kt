package org.qinyan.rimoat.hardware.usb

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
/**
 * Protocol framing for USB IR dongles with quantized pulse transmission.
 *
 * Implements 16µs pulse quantization, Type 'D' transmission frame packing,
 * and 0x02 outer transport envelope chunking (max 56 bytes payload per USB packet).
 */
object TiqiaaProtocolFormatter {
    const val MAX_CHUNK_PAYLOAD = 56 // Max payload bytes per 0x02 USB envelope
    const val MAX_APPLICATION_FRAME = 1024

    // The byte following 'D' selects an entry in the firmware carrier table.
    private val carrierFrequencies = intArrayOf(
        38000, 37900, 37917, 36000, 40000, 39700, 35750, 36400, 36700, 37000,
        37700, 38380, 38400, 38462, 38740, 39200, 42000, 43600, 44000, 33000,
        33500, 34000, 34500, 35000, 40500, 41000, 41500, 42500, 43000, 45000
    )

    fun carrierIndex(frequencyHz: Int): Int {
        require(frequencyHz in 33000..45000) { AppStrings.get(R.string.msg_tview_carrier_range, "Tview 支持的载波范围为 33–45 kHz") }
        return carrierFrequencies.indices.minBy { kotlin.math.abs(carrierFrequencies[it] - frequencyHz) }
    }

    private var envelopeSequence: Int = 1
    private var commandSequence: Int = 1

    @Synchronized
    fun nextEnvelopeSeq(): Byte {
        envelopeSequence = if (envelopeSequence < 14) envelopeSequence + 1 else 1
        return envelopeSequence.toByte()
    }

    @Synchronized
    fun nextCommandSeq(): Byte {
        commandSequence = if (commandSequence < 0x7E) commandSequence + 1 else 1
        return commandSequence.toByte()
    }

    /**
     * Encodes alternating on/off microsecond pulses into ready-to-transmit USB packet chunks.
     */
    fun encode(frequencyHz: Int, patternMicros: IntArray): List<ByteArray> {
        val appFrame = buildTransmissionPayload(frequencyHz, patternMicros)
        return wrapInEnvelope(appFrame, nextEnvelopeSeq())
    }

    /**
     * Builds the application-level Type 'D' command frame:
     * 'S', 'T', seq, 'D', meta, [16µs timing bytes...], 'E', 'N'
     */
    fun buildTransmissionPayload(
        frequencyHz: Int,
        patternMicros: IntArray,
        seq: Byte = nextCommandSeq()
    ): ByteArray {
        val carrier = carrierIndex(frequencyHz)
        val pulses = patternMicros.map { kotlin.math.abs(it.toLong()) }
        require(pulses.isNotEmpty() && pulses.all { it > 0 } && pulses.sum() < 2_000_000) {
            AppStrings.get(R.string.msg_ir_pulses_empty_zero_long, "红外脉冲不能为空、包含零或超过 2 秒")
        }
        val out = ArrayList<Byte>()
        out.add('S'.code.toByte())
        out.add('T'.code.toByte())
        out.add(seq)
        out.add('D'.code.toByte())
        out.add(carrier.toByte())

        var isMark = true
        for (us in pulses) {
            var units = ((us + 8) / 16).toInt()
            if (units <= 0) units = 1

            while (units > 0) {
                val take = minOf(127, units)
                val byteVal = if (isMark) (0x80 or take) else take
                out.add(byteVal.toByte())
                require(out.size + 2 <= MAX_APPLICATION_FRAME) { AppStrings.get(R.string.msg_tview_frame_long, "Tview 红外帧超过 1024 字节") }
                units -= take
            }
            isMark = !isMark
        }

        out.add('E'.code.toByte())
        out.add('N'.code.toByte())
        return out.toByteArray()
    }

    /**
     * Wraps an application layer payload into 0x02 transport envelope chunks:
     * [0x02, chunkSize + 3, envelopeSeq, totalChunks, chunkIndex, payload...]
     */
    fun wrapInEnvelope(payload: ByteArray, envelopeSeq: Byte): List<ByteArray> {
        if (payload.isEmpty()) return emptyList()
        require(payload.size <= MAX_APPLICATION_FRAME) { AppStrings.get(R.string.msg_tview_frame_long, "Tview 红外帧超过 1024 字节") }
        val totalChunks = ((payload.size + MAX_CHUNK_PAYLOAD - 1) / MAX_CHUNK_PAYLOAD).coerceAtLeast(1)
        val chunks = ArrayList<ByteArray>(totalChunks)

        var offset = 0
        var chunkIndex = 1
        while (offset < payload.size) {
            val chunkSize = minOf(MAX_CHUNK_PAYLOAD, payload.size - offset)
            val packet = ByteArray(5 + chunkSize)
            packet[0] = 0x02
            packet[1] = (chunkSize + 3).toByte()
            packet[2] = envelopeSeq
            packet[3] = totalChunks.toByte()
            packet[4] = chunkIndex.toByte()
            System.arraycopy(payload, offset, packet, 5, chunkSize)
            chunks.add(packet)
            offset += chunkSize
            chunkIndex++
        }

        return chunks
    }

    /**
     * Calculates required sleep duration in milliseconds after transmitting the signal
     * to avoid overflowing hardware buffers.
     */
    fun calculatePostTransmitDelayMs(patternMicros: IntArray): Long {
        var totalMicros = 0L
        for (d in patternMicros) {
            totalMicros += kotlin.math.abs(d.toLong())
        }
        return (totalMicros / 1000L) + 2L
    }
}

