package org.qinyan.rimoat.hardware.usb

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.learning.TiqiaaProtocol
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Tview replies are USB envelopes; bytes after the declared payload can contain stale data. */
internal class TiqiaaSession(private val channel: UsbBulkChannel, private val replyTimeoutMs: Int? = null) {
    suspend fun transmit(signal: IrSignal.Raw) {
        val sequence = TiqiaaProtocolFormatter.nextCommandSeq()
        val payload = TiqiaaProtocolFormatter.buildTransmissionPayload(signal.frequencyHz, signal.patternMicros, sequence)
        val packets = TiqiaaProtocolFormatter.wrapInEnvelope(payload, TiqiaaProtocolFormatter.nextEnvelopeSeq())
        val buffer = ByteArray(64)
        var drained = false
        repeat(8) {
            if (!drained) {
                currentCoroutineContext().ensureActive()
                drained = channel.read(buffer, 10) <= 0
            }
        }
        if (!drained) throw IOException(AppStrings.get(R.string.msg_tview_stale_data))
        val startedAt = System.nanoTime()
        for (packet in packets) {
            currentCoroutineContext().ensureActive()
            val written = channel.write(packet, 1000)
            if (written != packet.size) throw IOException(AppStrings.get(R.string.msg_tview_write_incomplete, (written).toString(), (packet.size).toString()))
        }
        val durationMs = TiqiaaProtocolFormatter.calculatePostTransmitDelayMs(signal.patternMicros)
        val timeout = replyTimeoutMs?.toLong() ?: (durationMs + 500).coerceAtLeast(600)
        val deadline = System.nanoTime() + timeout * 1_000_000
        val assembler = TiqiaaProtocol.InboundPacketAssembler(TiqiaaProtocolFormatter.MAX_APPLICATION_FRAME)
        while (System.nanoTime() < deadline) {
            currentCoroutineContext().ensureActive()
            val count = channel.read(buffer, 100)
            if (count <= 0) { delay(1); continue }
            val frame = assembler.processPacket(buffer, count) ?: continue
            if (frame.size < 7 || frame[2] != sequence) continue
            val parsed = TiqiaaProtocol.parseFrame(frame) ?: continue
            // Observed on the physical 10c4:8468 Tview: D -> O, meta 09 (send state).
            // A write-only success or an unrelated earlier mode reply is insufficient.
            if (parsed.type != 4 || parsed.meta != 0x09) {
                throw IOException(AppStrings.get(R.string.msg_tview_send_not_confirmed, (frame[3].toInt().toChar()).toString(), (parsed.meta).toString()))
            }
            val remaining = durationMs - (System.nanoTime() - startedAt) / 1_000_000
            if (remaining > 0) delay(remaining)
            try { android.util.Log.d("TiqiaaSession", "TX acknowledged: seq=${sequence.toInt() and 255}, type=O, meta=09") }
            catch (_: Exception) {}
            return
        }
        throw IOException(AppStrings.get(R.string.msg_tview_send_timeout))
    }
}
