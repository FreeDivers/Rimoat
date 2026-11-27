package org.qinyan.rimoat.hardware.usb

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.hardware.IrSignal
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.IOException

/** Blocking USB calls belong on Dispatchers.IO; this boundary also allows transcript tests. */
internal interface UsbBulkChannel {
    fun read(buffer: ByteArray, timeoutMs: Int): Int
    fun write(data: ByteArray, timeoutMs: Int): Int
}

internal class ElkSmartSession(private val channel: UsbBulkChannel) {
    suspend fun identify(): ElkSmartProtocolFormatter.Subtype {
        val buffer = ByteArray(64)
        // Bounded drain, including devices that continuously produce input.
        for (i in 0 until 4) {
            currentCoroutineContext().ensureActive()
            if (channel.read(buffer, 10) <= 0) break
        }
        currentCoroutineContext().ensureActive()
        val probe = ElkSmartProtocolFormatter.HANDSHAKE_PROBE
        if (channel.write(probe, 200) != probe.size) throw IOException(AppStrings.get(R.string.msg_elksmart_probe_incomplete))
        val response = java.io.ByteArrayOutputStream()
        repeat(3) {
            currentCoroutineContext().ensureActive()
            val count = channel.read(buffer, 150)
            if (count > 0) {
                require(count <= buffer.size)
                response.write(buffer, 0, count)
                if (response.size() >= 6) return ElkSmartProtocolFormatter.identify(response.toByteArray())
                    ?: throw IOException(AppStrings.get(R.string.msg_elksmart_model_unknown))
            }
        }
        throw IOException(AppStrings.get(R.string.msg_elksmart_probe_timeout))
    }

    suspend fun transmit(signal: IrSignal.Raw, subtype: ElkSmartProtocolFormatter.Subtype) {
        for (packet in ElkSmartProtocolFormatter.encode(signal.frequencyHz, signal.patternMicros, subtype)) {
            currentCoroutineContext().ensureActive()
            // A partial packet cannot be retried as a new frame: its checksum boundary would change.
            if (channel.write(packet, 1000) != packet.size) throw IOException(AppStrings.get(R.string.msg_elksmart_write_incomplete))
            delay(2)
        }
        delay(ElkSmartProtocolFormatter.calculatePostTransmitDelayMs(signal.patternMicros))
        val buffer = ByteArray(64)
        for (i in 0 until 6) {
            currentCoroutineContext().ensureActive()
            if (channel.read(buffer, 20) <= 0) break
        }
    }
}
