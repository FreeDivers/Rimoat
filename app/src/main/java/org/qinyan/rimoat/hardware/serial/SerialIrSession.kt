package org.qinyan.rimoat.hardware.serial

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.hardware.IrProtocolEncoder
import org.qinyan.rimoat.hardware.IrSignal
import java.io.IOException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** A driver must either write ALL bytes or throw; usb-serial-for-android supplies that contract. */
internal interface SerialPortIo {
    fun write(data: ByteArray, timeoutMs: Int)
    fun read(buffer: ByteArray, timeoutMs: Int): Int
}

internal class SerialIrSession(private val port: SerialPortIo) {
    // FTDI includes a two-byte USB status header; tiny reads fail there and may also
    // truncate coalesced CDC replies. Read whole packets, retain unused protocol bytes.
    private val pendingInput = ArrayDeque<Byte>()
    private val readBuffer = ByteArray(4096)
    private suspend fun write(data: ByteArray) {
        currentCoroutineContext().ensureActive()
        port.write(data, 1000)
    }

    private suspend fun readExact(size: Int, timeoutMs: Int = 1500): ByteArray {
        val result = ByteArray(size)
        var offset = 0
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (offset < size && System.nanoTime() < deadline) {
            currentCoroutineContext().ensureActive()
            while (pendingInput.isNotEmpty() && offset < size) result[offset++] = pendingInput.removeFirst()
            if (offset == size) break
            val remainingMs = ((deadline - System.nanoTime()) / 1_000_000).coerceAtLeast(1).toInt()
            val count = port.read(readBuffer, minOf(remainingMs, 100))
            if (count < 0 || count > readBuffer.size) throw IOException(AppStrings.get(R.string.msg_serial_read_failed, "串口读取失败，设备可能已拔出"))
            if (count == 0) { delay(1); continue }
            for (i in 0 until count) pendingInput.addLast(readBuffer[i])
        }
        if (offset != size) throw IOException(AppStrings.get(R.string.msg_serial_response_timeout, "红外模块应答超时，请检查模块、波特率和连接"))
        return result
    }

    private suspend fun drain() {
        pendingInput.clear()
        repeat(4) {
            currentCoroutineContext().ensureActive()
            if (port.read(readBuffer, 10) <= 0) return
        }
        throw IOException(AppStrings.get(R.string.msg_serial_unknown_data, "串口持续收到未知数据，请检查模块配置"))
    }

    suspend fun transmit(settings: SerialIrSettings, signal: IrSignal) {
        settings.validate()
        when (settings.module) {
            SerialIrModule.YS_IRTM -> {
                val frame = UsbUartProtocol.buildYsIrtmPacket(signal, settings.moduleAddress)
                drain()
                write(frame)
                if (readExact(1)[0] != 0xF1.toByte()) throw IOException(AppStrings.get(R.string.msg_ysirtm_no_ack, "YS-IRTM 未确认发送，模块应答无效"))
                // ACK confirms command acceptance, allow the NEC waveform to finish before closing.
                delay(120)
            }
            SerialIrModule.IR_TOY -> transmitIrToy(when (signal) {
                is IrSignal.Raw -> signal
                is IrSignal.Nec -> {
                    require(signal.address in 0..65535 && signal.command in 0..255) { AppStrings.get(R.string.msg_nec_parameters_invalid, "NEC 参数无效") }
                    IrProtocolEncoder.encodeNec(signal)
                }
            })
            SerialIrModule.UNCONFIGURED -> error(AppStrings.get(R.string.msg_configure_serial_first, "请先配置串口红外模块"))
        }
    }

    private suspend fun resetToy() {
        write(ByteArray(5))
        delay(50)
        drain()
    }

    private suspend fun samplingMode() {
        resetToy()
        write(byteArrayOf('S'.code.toByte()))
        val protocol = readExact(3).toString(Charsets.US_ASCII)
        if (protocol != "S01") throw IOException(AppStrings.get(R.string.msg_irtoy_protocol_unsupported, "IR Toy 采样协议不受支持：%1\$s", (protocol).toString()))
    }

    private suspend fun transmitIrToy(signal: IrSignal.Raw) {
        val payload = IrToyProtocol.encode(signal)
        resetToy()
        write(byteArrayOf('v'.code.toByte()))
        val version = readExact(4).toString(Charsets.US_ASCII)
        if (!version.matches(Regex("V2[0-9]{2}")) || version.substring(2).toInt() < 22) {
            throw IOException(AppStrings.get(R.string.msg_irtoy_firmware_required, "需要 USB IR Toy v2、固件 22 或更高版本，收到：%1\$s", (version).toString()))
        }
        var streaming = false
        try {
            samplingMode()
            write(IrToyProtocol.carrierCommand(signal.frequencyHz))
            streaming = true
            write(byteArrayOf(0x26, 0x25, 0x24, 0x03))
            var capacity = readExact(1)[0].toInt() and 255
            var offset = 0
            while (offset < payload.size) {
                if (capacity !in 2..62) throw IOException(AppStrings.get(R.string.msg_irtoy_buffer_invalid, "IR Toy 缓冲区应答无效"))
                val length = minOf(32, capacity and 0xFE, payload.size - offset)
                write(payload.copyOfRange(offset, offset + length))
                offset += length
                capacity = readExact(1, 2500)[0].toInt() and 255
            }
            val report = readExact(4, 2500)
            streaming = false
            val count = ((report[1].toInt() and 255) shl 8) or (report[2].toInt() and 255)
            if (report[0] != 't'.code.toByte() || count != payload.size || report[3] !in byteArrayOf(0x43, 0x63)) {
                throw IOException(AppStrings.get(R.string.msg_irtoy_send_incomplete, "IR Toy 未确认完整发送（%1\$s/%2\$s 字节）", (count).toString(), (payload.size).toString()))
            }
            samplingMode()
        } catch (failure: Exception) {
            // Never replay a partially sent command. Best-effort terminator and reset only,
            // including cancellation; the driver is closed by the caller afterwards.
            withContext(NonCancellable) {
                try {
                    if (streaming) {
                        port.write(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), 200)
                        delay(signal.patternMicros.sumOf { kotlin.math.abs(it.toLong()) } / 1000 + 100)
                    }
                    samplingMode()
                } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            }
            throw failure
        }
    }
}
