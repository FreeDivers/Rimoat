package org.qinyan.rimoat.data

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import java.security.MessageDigest

/** Independent reader of the published Flipper IR signals format; no upstream code or database is bundled. */
object FlipperIrImporter {
    fun decode(text: String, fileName: String): ImportDocument {
        fun invalid(): Nothing = error(AppStrings.get(R.string.migration_ir_invalid))
        require(text.toByteArray(Charsets.UTF_8).size <= RemoteBackupCodec.MAX_BYTES) { AppStrings.get(R.string.msg_backup_too_large) }
        val entries = mutableListOf<MutableMap<String, String>>()
        val header = mutableMapOf<String, String>()
        var current: MutableMap<String, String>? = null
        for (source in text.removePrefix("\uFEFF").lineSequence()) {
            val line = source.trim()
            if (line.isEmpty() || line.startsWith('#')) continue
            val delimiter = line.indexOf(':')
            if (delimiter <= 0) invalid()
            val key = line.substring(0, delimiter).trim()
            val value = line.substring(delimiter + 1).trim()
            if (key == "name") {
                current = mutableMapOf(); entries += current
                if (entries.size > 4096) invalid()
            }
            val target = current ?: header
            if (target.put(key, value) != null) invalid()
        }
        if (header["Filetype"] !in setOf("IR signals file", "IR library file") || header["Version"] != "1" || entries.isEmpty()) invalid()
        val skipped = mutableListOf<SkippedIrSignal>()
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(32)
        fun hex(value: String?): Long {
            val bytes = value?.split(Regex("\\s+")) ?: invalid()
            if (bytes.size != 4 || bytes.any { !it.matches(Regex("[0-9a-fA-F]{2}")) }) invalid()
            return bytes.mapIndexed { index, byte -> byte.toLong(16) shl (index * 8) }.sum()
        }
        val buttons = entries.mapIndexedNotNull { index, entry ->
            val label = entry["name"]?.takeIf { it.isNotBlank() && it.length <= 100 } ?: invalid()
            val id = "import_${digest}_$index"
            when (entry["type"]) {
                "raw" -> {
                    val frequency = entry["frequency"]?.toIntOrNull() ?: invalid()
                    val duty = entry["duty_cycle"]?.toDoubleOrNull() ?: invalid()
                    val pulses = entry["data"]?.split(Regex("\\s+"))?.map { it.toIntOrNull() ?: invalid() } ?: invalid()
                    if (frequency !in 1000..1_000_000 || !duty.isFinite() || duty <= 0 || duty >= 1 ||
                        pulses.size !in 1..1024 || pulses.any { it <= 0 } || pulses.sumOf { it.toLong() } > 2_000_000) invalid()
                    RemoteButton(id, label, frequencyHz = frequency, rawPattern = pulses, isLearned = true)
                }
                "parsed" -> {
                    val protocol = entry["protocol"] ?: invalid()
                    if (protocol !in setOf("NEC", "NECext")) { skipped += SkippedIrSignal(label, protocol); null }
                    else {
                        val address = hex(entry["address"]); val command = hex(entry["command"])
                        if (protocol == "NEC") {
                            if (address !in 0..255 || command !in 0..255) invalid()
                            RemoteButton(id, label, signalType = SignalType.NEC, necAddress = address.toInt(), necCommand = command.toInt(), isLearned = true)
                        } else {
                            if (address !in 0..65535 || command !in 0..65535) invalid()
                            val bits = address or (command shl 16)
                            val pulses = mutableListOf(9000, 4500)
                            repeat(32) { bit -> pulses += 560; pulses += if (bits and (1L shl bit) != 0L) 1690 else 560 }
                            pulses += 560
                            RemoteButton(id, label, frequencyHz = 38000, rawPattern = pulses, isLearned = true)
                        }
                    }
                }
                else -> invalid()
            }
        }
        val baseName = fileName.substringBeforeLast('.').take(90).ifBlank { "IR" }
        val profiles = buttons.chunked(256).mapIndexed { index, keys ->
            RemoteProfile("ir_${digest}_$index", if (buttons.size > 256) "$baseName ${index + 1}" else baseName,
                category = DeviceCategory.CUSTOM, buttons = keys)
        }
        return ImportDocument(RemoteBackup(remotes = profiles), skipped)
    }
}
