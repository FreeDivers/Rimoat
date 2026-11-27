package org.qinyan.rimoat.data

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID

@Serializable
data class RemoteBackup(
    val format: String = "rimoat.backup",
    // Version 2 remains readable by older releases; key metadata is additive and optional.
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val remotes: List<RemoteProfile>,
    val rooms: List<String> = emptyList(),
    val preferences: PortablePreferences? = null
)

/** Only portable appearance choices; never device bindings, authorizations or Widget IDs. */
@Serializable
data class PortablePreferences(val dynamicColors: Boolean? = null, val pureBlack: Boolean? = null)

enum class ImportMode { MERGE, COPIES }
enum class ConflictChoice { KEEP, OVERWRITE, COPY }
data class ImportDocument(val backup: RemoteBackup, val skippedSignals: List<SkippedIrSignal> = emptyList())
data class SkippedIrSignal(val name: String, val protocol: String)
data class ImportSummary(val added: Int, val conflicts: Int, val skipped: Int, val overwritten: Int)

fun importSummary(backup: RemoteBackup, existing: Set<String>, choices: Map<String, ConflictChoice>): ImportSummary {
    val conflicts = backup.remotes.count { it.id in existing }
    val kept = backup.remotes.count { it.id in existing && choices[it.id] != ConflictChoice.COPY && choices[it.id] != ConflictChoice.OVERWRITE }
    val overwritten = backup.remotes.count { it.id in existing && choices[it.id] == ConflictChoice.OVERWRITE }
    return ImportSummary(backup.remotes.size - conflicts + backup.remotes.count { it.id in existing && choices[it.id] == ConflictChoice.COPY }, conflicts, kept, overwritten)
}

/** Validate the whole document before the repository writes any files. */
object RemoteBackupCodec {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_PROFILES = 500
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val snapshotJson = Json(json) { prettyPrint = false }
    fun encode(profiles: List<RemoteProfile>): String = encode(RemoteBackup(remotes = profiles))
    fun encode(backup: RemoteBackup): String = json.encodeToString(RemoteBackup.serializer(), backup)
    // Internal snapshots cover the entire local library, not a single external import.
    internal fun encodeRecoverySnapshot(backup: RemoteBackup): String =
        snapshotJson.encodeToString(RemoteBackup.serializer(), backup)

    internal fun decodeRecoverySnapshot(text: String): RemoteBackup =
        snapshotJson.decodeFromString(RemoteBackup.serializer(), text).also(::validateContents)

    fun decode(text: String): List<RemoteProfile> = decodeBackup(text).remotes
    fun decodeBackup(text: String): RemoteBackup {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { AppStrings.get(R.string.msg_backup_too_large, "备份超过 8 MB") }
        val root = json.parseToJsonElement(text.removePrefix("\uFEFF")).jsonObject
        val backup = if ("remotes" in root) {
            json.decodeFromJsonElement(RemoteBackup.serializer(), root).also {
                require(it.format == "rimoat.backup" && it.version in 1..2) { AppStrings.get(R.string.msg_backup_format_unsupported, "不支持此备份格式或版本") }
            }
        } else RemoteBackup(version = 1, remotes = listOf(json.decodeFromJsonElement(RemoteProfile.serializer(), root)))
        validateBackup(backup)
        return backup.copy(
            remotes = backup.remotes.map { LegacyRemoteMigration.normalizeKeys(LegacyRemoteMigration.upgradeLegacyAc(it)) },
            rooms = (backup.rooms + backup.remotes.map { it.room }).filter { it.isNotBlank() }.distinct())
    }
    fun validateBackup(backup: RemoteBackup) {
        require(backup.remotes.size <= MAX_PROFILES) { AppStrings.get(R.string.msg_backup_profile_count, "备份须包含 1–%1\$s 个遥控器", MAX_PROFILES.toString()) }
        require(backup.rooms.size <= 500) { AppStrings.get(R.string.msg_name_room_invalid, "名称或房间不合法") }
        validateContents(backup)
    }

    private fun validateContents(backup: RemoteBackup) {
        require(backup.format == "rimoat.backup" && backup.version in 1..2) { AppStrings.get(R.string.msg_backup_format_unsupported) }
        require(backup.remotes.map { it.id }.distinct().size == backup.remotes.size) { AppStrings.get(R.string.msg_backup_duplicate_ids, "备份包含重复的遥控器标识") }
        require(backup.rooms.all { it.isNotBlank() && it.length <= 60 } && backup.rooms.distinct().size == backup.rooms.size) { AppStrings.get(R.string.msg_name_room_invalid, "名称或房间不合法") }
        backup.remotes.forEach(::validate)
    }
    fun plan(backup: RemoteBackup, existingIds: Set<String>, choices: Map<String, ConflictChoice>): List<RemoteProfile> =
        backup.remotes.mapNotNull { profile ->
            if (profile.id !in existingIds) profile else when (choices[profile.id] ?: ConflictChoice.KEEP) {
                ConflictChoice.KEEP -> null
                ConflictChoice.OVERWRITE -> profile
                ConflictChoice.COPY -> copyOf(profile)
            }
        }
    private fun copyOf(profile: RemoteProfile): RemoteProfile {
        val suffix = AppStrings.get(R.string.msg_copy_suffix, "（副本）")
        return profile.copy(id = UUID.randomUUID().toString(), name = profile.name.take(100 - suffix.length) + suffix, lastUsedAt = 0)
    }

    fun validate(profile: RemoteProfile) {
        require(profile.id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { AppStrings.get(R.string.msg_remote_id_invalid, "遥控器标识无效") }
        require(profile.name.isNotBlank() && profile.name.length <= 100 && profile.room.length <= 60) { AppStrings.get(R.string.msg_name_room_invalid, "名称或房间不合法") }
        require(profile.buttons.size <= 256 && profile.buttons.map { it.id }.distinct().size == profile.buttons.size) { AppStrings.get(R.string.msg_buttons_invalid, "按键过多或标识重复") }
        require(profile.acSettings.temperature in 16..30) { AppStrings.get(R.string.msg_ac_temperature_invalid, "空调温度无效") }
        require(profile.acSettings.timerMinutes in 0..1440) { AppStrings.get(R.string.msg_ac_timer_invalid, "空调定时无效") }
        profile.buttons.forEach { button ->
            require(button.label.isNotBlank() && button.label.length <= 100 && button.id.length in 1..200) { AppStrings.get(R.string.msg_button_id_invalid, "按键名称或标识无效") }
            require(button.row in 0..256 && button.col in 0..32 && button.spanCol in 1..32) { AppStrings.get(R.string.msg_button_layout_invalid, "按键布局无效") }
            require(button.alternateFrequencyHz in 1000..1_000_000 && button.alternateRawPattern.size <= 20000 &&
                button.alternateRawPattern.all { it > 0 } && button.alternateRawPattern.sumOf { it.toLong() } <= 2_000_000) { AppStrings.get(R.string.msg_alternate_signal_invalid, "交替红外信号无效") }
            when (button.signalType) {
                SignalType.NEC -> require(button.necAddress in 0..65535 && button.necCommand in 0..255) { AppStrings.get(R.string.msg_nec_button_invalid, "NEC 按键参数无效") }
                SignalType.RAW -> {
                    require(button.frequencyHz in 1000..1_000_000 && button.rawPattern.size <= 20000) { AppStrings.get(R.string.msg_frequency_pulses_invalid, "红外频率或脉冲数量无效") }
                    require(button.rawPattern.all { it != 0 && it != Int.MIN_VALUE } &&
                        button.rawPattern.sumOf { kotlin.math.abs(it.toLong()) } <= 2_000_000) { AppStrings.get(R.string.msg_pulses_invalid, "红外脉冲无效或过长") }
                }
            }
        }
    }

    fun plan(profiles: List<RemoteProfile>, existingIds: Set<String>, mode: ImportMode): List<RemoteProfile> = when (mode) {
        ImportMode.MERGE -> profiles.filter { it.id !in existingIds }
        ImportMode.COPIES -> profiles.map {
            val suffix = AppStrings.get(R.string.msg_copy_suffix, "（副本）")
            it.copy(id = UUID.randomUUID().toString(), name = it.name.take(minOf(95, 100 - suffix.length)) + suffix, lastUsedAt = 0)
        }
    }
}
