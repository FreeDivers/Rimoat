package org.qinyan.rimoat.data.db

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.SignalType
import org.qinyan.rimoat.data.CatalogUpgrade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.math.abs

interface IrCatalog {
    suspend fun getCategories(): List<IrCategory>
    suspend fun getBrandsForCategory(categoryId: Int, searchQuery: String = ""): List<IrBrand>
    suspend fun getRemotesForBrand(categoryId: Int, brandIdx: Int): List<IrRemoteModel>
    suspend fun getTestKeysForRemote(remoteId: String): Pair<RemoteButton?, RemoteButton?>
    suspend fun buildRemoteProfile(model: IrRemoteModel, customName: String? = null): RemoteProfile
    suspend fun testSignalsFor(model: IrRemoteModel, button: RemoteButton): List<org.qinyan.rimoat.hardware.IrSignal> = listOf(button.toIrSignal())
}

class IrDatabaseRepository(
    private val context: Context,
    private val acSignals: org.qinyan.rimoat.data.AcSignalEncoder? = null
) : IrCatalog {

    private val dbFileName = "catalog_v1.db"
    private var database: SQLiteDatabase? = null
    @Volatile private var cachedCategories: List<IrCategory>? = null
    private val mutex = Mutex()

    private suspend fun getDb(): SQLiteDatabase = withContext(Dispatchers.IO) {
        mutex.withLock {
            database?.let { if (it.isOpen) return@withLock it }

            val dbFile = context.getDatabasePath(dbFileName)
            val prefs = context.getSharedPreferences("ir_db_meta", Context.MODE_PRIVATE)
            val currentDbVersion = CatalogUpgrade.VERSION
            val installedVersion = prefs.getInt("installed_db_version", 0)

            if (!dbFile.exists() || dbFile.length() == 0L || installedVersion < currentDbVersion) {
                copyDatabaseFromAssets(dbFile)
                prefs.edit().putInt("installed_db_version", currentDbVersion).apply()
            }

            val db = SQLiteDatabase.openDatabase(
                dbFile.path,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            database = db
            cachedCategories = null
            db
        }
    }

    private fun copyDatabaseFromAssets(targetFile: File) {
        val parent = targetFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        val temporary = File(targetFile.path + ".tmp")
        try {
            context.assets.open(dbFileName).use { input ->
                FileOutputStream(temporary).use { output -> input.copyTo(output); output.fd.sync() }
            }
            SQLiteDatabase.openDatabase(temporary.path, null, SQLiteDatabase.OPEN_READONLY).use { copied ->
                check(copied.version == 1) { AppStrings.get(R.string.msg_catalog_format_unsupported, "不支持的码库格式") }
                copied.rawQuery("PRAGMA quick_check", null).use { check(it.moveToFirst() && it.getString(0) == "ok") }
            }
            check(temporary.renameTo(targetFile)) { AppStrings.get(R.string.msg_catalog_update_failed, "码库更新失败，原码库已保留") }
        } finally { temporary.delete() }
    }

    override suspend fun getCategories(): List<IrCategory> {
        cachedCategories?.let { return it }
        return withContext(Dispatchers.IO) {
            val db = getDb()
            cachedCategories?.let { return@withContext it }
            val list = mutableListOf<IrCategory>()
            val cursor = db.rawQuery("SELECT id, name_cn, name_en, code FROM categories ORDER BY id ASC", null)
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        IrCategory(
                            id = it.getInt(0),
                            nameCn = it.getString(1) ?: "",
                            nameEn = it.getString(2) ?: "",
                            code = it.getString(3) ?: ""
                        )
                    )
                }
            }
            list.also { cachedCategories = it }
        }
    }

    override suspend fun getBrandsForCategory(
        categoryId: Int,
        searchQuery: String
    ): List<IrBrand> = withContext(Dispatchers.IO) {
        val db = getDb()
        val list = mutableListOf<IrBrand>()

        val sql = if (searchQuery.isBlank()) {
            """
            SELECT g.brand_idx, g.brand_cn, g.brand_en, b.pinyin, g.cnt
            FROM (
                SELECT brand_idx, brand_cn, brand_en, COUNT(remote_id) AS cnt
                FROM remotes
                WHERE category_id = ?
                GROUP BY brand_idx, brand_cn, brand_en
            ) g
            LEFT JOIN brands b ON g.brand_idx = b.brand_idx
            ORDER BY 
                CASE WHEN g.brand_cn = '其它' THEN 1 ELSE 0 END ASC,
                g.cnt DESC,
                g.brand_cn ASC
            """.trimIndent()
        } else {
            """
            SELECT r.brand_idx, r.brand_cn, r.brand_en, b.pinyin, COUNT(r.remote_id) as cnt
            FROM remotes r
            LEFT JOIN brands b ON r.brand_idx = b.brand_idx
            WHERE r.category_id = ? AND (
                r.brand_cn LIKE ? ESCAPE '\' OR
                r.brand_en LIKE ? ESCAPE '\' OR
                b.pinyin LIKE ? ESCAPE '\'
            )
            GROUP BY r.brand_idx, r.brand_cn, r.brand_en
            ORDER BY cnt DESC, r.brand_cn ASC
            """.trimIndent()
        }

        val args = if (searchQuery.isBlank()) {
            arrayOf(categoryId.toString())
        } else {
            val escaped = searchQuery.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            val q = "%$escaped%"
            arrayOf(categoryId.toString(), q, q, q)
        }

        val cursor = db.rawQuery(sql, args)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    IrBrand(
                        brandIdx = it.getInt(0),
                        nameCn = it.getString(1) ?: "未知品牌",
                        nameEn = it.getString(2) ?: "",
                        pinyin = it.getString(3) ?: "",
                        remoteCount = it.getInt(4)
                    )
                )
            }
        }
        list
    }

    override suspend fun getRemotesForBrand(
        categoryId: Int,
        brandIdx: Int
    ): List<IrRemoteModel> = withContext(Dispatchers.IO) {
        val db = getDb()
        val list = mutableListOf<IrRemoteModel>()
        val sql = """
            SELECT remote_id, category_id, category_name, brand_idx, brand_cn, brand_en, model_id, model_name, key_count
            FROM remotes
            WHERE category_id = ? AND brand_idx = ?
            ORDER BY model_id ASC
        """.trimIndent()

        val cursor = db.rawQuery(sql, arrayOf(categoryId.toString(), brandIdx.toString()))
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    IrRemoteModel(
                        remoteId = it.getString(0),
                        categoryId = it.getInt(1),
                        categoryName = it.getString(2) ?: "",
                        brandIdx = it.getInt(3),
                        brandCn = it.getString(4) ?: "",
                        brandEn = it.getString(5) ?: "",
                        modelId = it.getInt(6),
                        modelName = it.getString(7) ?: "",
                        keyCount = it.getInt(8)
                    )
                )
            }
        }
        list
    }

    suspend fun getRemoteKeys(remoteId: String, legacy: Boolean = false): List<IrKeyDetail> = withContext(Dispatchers.IO) {
        val db = getDb()
        fun inflate(bytes: ByteArray) = InflaterInputStream(ByteArrayInputStream(bytes)).bufferedReader().use { it.readText() }
        val packed = db.rawQuery("SELECT data FROM key_sets WHERE remote_id=? AND legacy=?",
            arrayOf(remoteId, if (legacy) "1" else "0")).use { if (it.moveToFirst()) it.getBlob(0) else null }
            ?: return@withContext emptyList()
        val rows = Json.parseToJsonElement(inflate(packed)).jsonArray.map { it.jsonArray }
        val ids = rows.flatMap { listOfNotNull(it[2].jsonPrimitive.contentOrNull, it[4].jsonPrimitive.contentOrNull) }.distinct()
        val signals = mutableMapOf<String, Pair<Int, String>>()
        for (batch in ids.chunked(400)) {
            db.rawQuery("SELECT signal_id,frequency,pulses FROM signals WHERE signal_id IN (${batch.joinToString(",") { "?" }})",
                batch.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) signals[cursor.getString(0)] = cursor.getInt(1) to inflate(cursor.getBlob(2))
            }
        }
        rows.map { row ->
            val signal = checkNotNull(signals[row[2].jsonPrimitive.content]) { AppStrings.get(R.string.msg_catalog_signal_missing, "码库信号缺失") }
            val alternate = row[4].jsonPrimitive.contentOrNull?.let { checkNotNull(signals[it]) }
            IrKeyDetail(keyName = row[1].jsonPrimitive.contentOrNull ?: "按键", keyType = row[0].jsonPrimitive.int,
                carrierFreq = signal.first, pulsesCsv = signal.second, sourceSlot = row[3].jsonPrimitive.contentOrNull,
                alternateFrequency = alternate?.first ?: 38000, alternatePulses = alternate?.second.orEmpty())
        }
    }

    override suspend fun getTestKeysForRemote(remoteId: String): Pair<RemoteButton?, RemoteButton?> = withContext(Dispatchers.IO) {
        val protocol = acSignals?.protocolFor(remoteId)
        if (protocol != null) {
            val buttons = acButtons(remoteId, protocol)
            return@withContext buttons.firstOrNull { it.acAction == org.qinyan.rimoat.data.AcAction.POWER_ON } to
                buttons.firstOrNull { it.acAction == org.qinyan.rimoat.data.AcAction.TEMP_UP }
        }
        val keys = getRemoteKeys(remoteId)
        if (keys.isEmpty()) return@withContext Pair(null, null)

        val powerKey = keys.firstOrNull { it.keyType == 800 || it.keyName.contains("电源") || it.keyName.contains("开机") || it.keyName.contains("关机") }
        val volUpKey = keys.firstOrNull { it.keyType == 809 || it.keyName.contains("音量+") || it.keyName.contains("模式") }
        val muteKey = keys.firstOrNull { it.keyType == 804 || it.keyName.contains("静音") }

        val primaryDetail = powerKey ?: keys.first()
        val secondaryDetail = volUpKey ?: muteKey ?: keys.getOrNull(1)

        val primaryButton = parseButton(remoteId, primaryDetail)
        val secondaryButton = if (secondaryDetail != null && secondaryDetail != primaryDetail) {
            parseButton(remoteId, secondaryDetail)
        } else null

        Pair(primaryButton, secondaryButton)
    }

    override suspend fun testSignalsFor(model: IrRemoteModel, button: RemoteButton): List<org.qinyan.rimoat.hardware.IrSignal> {
        val action = button.acAction ?: return listOf(button.toIrSignal())
        val encoder = acSignals ?: error(AppStrings.get(R.string.msg_ac_encoder_missing, "空调编码器不可用"))
        val protocol = encoder.protocolFor(model.remoteId) ?: error(AppStrings.get(R.string.msg_ac_protocol_missing, "未找到空调协议"))
        return encoder.encode(protocol, org.qinyan.rimoat.data.AcSettings().apply(action,
            if (action == org.qinyan.rimoat.data.AcAction.TIMER) 60 else 0), action)
    }

    override suspend fun buildRemoteProfile(
        model: IrRemoteModel,
        customName: String?
    ): RemoteProfile = withContext(Dispatchers.IO) {
        val rawKeys = getRemoteKeys(model.remoteId)
        val protocol = if (model.categoryId == 2) acSignals?.protocolFor(model.remoteId) else null
        val buttons = if (protocol != null) acButtons(model.remoteId, protocol)
            else rawKeys.map { parseButton(model.remoteId, it) }

        val category = mapCategory(model.categoryId)
        val displayName = customName?.takeIf { it.isNotBlank() }
            ?: "${model.brandCn} ${model.categoryName}"

        RemoteProfile(
            id = UUID.randomUUID().toString(),
            name = displayName,
            brand = model.brandCn,
            category = category,
            sourceRemoteId = model.remoteId,
            catalogVersion = CatalogUpgrade.VERSION,
            acProtocolId = protocol,
            buttons = buttons,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun parseButton(remoteId: String, detail: IrKeyDetail, legacy: Boolean = false): RemoteButton {
        val pulses = detail.pulsesCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull()?.let { v -> abs(v) } }

        val iconName = resolveIconName(detail.keyType, detail.keyName, legacy)
        val label = cleanLabel(detail.keyName)

        return RemoteButton(
            id = if (detail.sourceSlot != null) "${remoteId}_slot_${detail.sourceSlot}"
                else "${remoteId}_${detail.keyType}_${abs(detail.keyName.hashCode())}",
            label = label,
            iconName = iconName,
            signalType = SignalType.RAW,
            frequencyHz = if (detail.carrierFreq > 0) detail.carrierFreq else 38000,
            rawPattern = pulses,
            sourceKeyType = detail.keyType,
            sourceSlot = detail.sourceSlot,
            alternateRawPattern = detail.alternatePulses.split(',').mapNotNull { it.toIntOrNull()?.let(::abs) },
            alternateFrequencyHz = detail.alternateFrequency
        )
    }

    private fun resolveIconName(keyType: Int, keyName: String, legacy: Boolean = false): String? {
        if (!legacy) when (keyType) {
            833, 838 -> return "air"
            834, 835, 870 -> return "swap_calls"
            876 -> return "timer"
            875 -> return null
            1010 -> return "home"
        }
        return when {
            keyType == 800 || keyName.contains("电源") || keyName == "关机" -> "power"
            keyType == 801 || keyName.contains("信源") -> "input"
            keyType == 804 || keyName.contains("静音") -> "volume_off"
            keyType == 806 || keyName.contains("返回") -> "arrow_back"
            keyType == 807 || keyName.contains("频道+") -> "arrow_drop_up"
            keyType == 808 || keyName.contains("频道-") -> "arrow_drop_down"
            keyType == 809 || keyName.contains("音量+") -> "volume_up"
            keyType == 810 || keyName.contains("音量-") -> "volume_down"
            keyType == 811 || keyName.contains("温度加") || keyName.contains("温度+") -> "keyboard_arrow_up"
            keyType == 812 || keyName.contains("温度减") || keyName.contains("温度-") -> "keyboard_arrow_down"
            keyType == 817 || keyName == "OK" || keyName.contains("OK键") -> "filter_center_focus"
            keyType == 818 || keyName == "上" || keyName == "上翻" -> "keyboard_arrow_up"
            keyType == 819 || keyName == "下" || keyName == "下翻" -> "keyboard_arrow_down"
            keyType == 820 || keyName == "左" || keyName == "左翻" -> "keyboard_arrow_left"
            keyType == 821 || keyName == "右" || keyName == "右翻" -> "keyboard_arrow_right"
            keyType == 822 || keyName.contains("菜单") -> "menu"
            keyType == 832 || keyName.contains("模式") -> "tune"
            keyType == 836 || keyName.contains("摇头") || keyName.contains("扫风") -> "swap_calls"
            keyType == 838 || keyName.contains("风速") || keyName.contains("风量") -> "air"
            keyType == 875 || keyName.contains("定时") -> "timer"
            keyName.contains("主页") -> "home"
            else -> null
        }
    }

    private fun cleanLabel(keyName: String): String {
        return when {
            keyName.startsWith("数字键 ") -> keyName.removePrefix("数字键 ").trim()
            keyName == "OK键" -> "OK"
            keyName == "菜单键" -> "菜单"
            keyName == "信源键" -> "信源"
            keyName == "上翻" -> "上"
            keyName == "下翻" -> "下"
            keyName == "左翻" -> "左"
            keyName == "右翻" -> "右"
            else -> keyName
        }
    }

    private fun mapCategory(catId: Int): DeviceCategory {
        return when (catId) {
            1, 5, 10, 11 -> DeviceCategory.TV
            2 -> DeviceCategory.AC
            3 -> DeviceCategory.FAN
            4 -> DeviceCategory.PROJECTOR
            6, 9 -> DeviceCategory.AUDIO
            else -> DeviceCategory.CUSTOM
        }
    }

    suspend fun upgradeProfile(profile: RemoteProfile): RemoteProfile = withContext(Dispatchers.IO) {
        if (profile.catalogVersion >= CatalogUpgrade.VERSION || profile.useStaticAcButtons) return@withContext profile
        val source = profile.sourceRemoteId ?: profile.buttons.mapNotNull {
            Regex("^(R_.+)_-?\\d+_-?\\d+$").find(it.id)?.groupValues?.get(1)
        }.distinct().singleOrNull() ?: return@withContext profile
        val protocol = if (profile.category == DeviceCategory.AC) profile.acProtocolId ?: acSignals?.protocolFor(source) else null
        val current = if (protocol != null) acButtons(source, protocol) else getRemoteKeys(source).map { parseButton(source, it) }
        val legacy = if (profile.id == org.qinyan.rimoat.data.LegacyRemoteMigration.greeAc.id) {
            val types = mapOf("ac_power" to 800,"ac_temp_up" to 811,"ac_temp_down" to 812,"ac_mode" to 832,"ac_wind" to 833,"ac_swing" to 870)
            org.qinyan.rimoat.data.LegacyRemoteMigration.greeAc.buttons.map { it.copy(sourceKeyType = types[it.id]) }
        } else getRemoteKeys(source, legacy = true).map { parseButton(source, it, legacy = true) }.map {
            if (protocol != null && it.sourceKeyType == 838) it.copy(sourceKeyType = 833) else it
        }
        CatalogUpgrade.merge(profile, source, legacy, current).copy(acProtocolId = protocol ?: profile.acProtocolId)
    }

    private suspend fun acButtons(source: String, protocol: Int): List<RemoteButton> {
        val encoder = acSignals ?: return emptyList()
        return encoder.availableActions(protocol).map { action ->
            val state = org.qinyan.rimoat.data.AcSettings().apply(action, if (action == org.qinyan.rimoat.data.AcAction.TIMER) 60 else 0)
            val signal = if (encoder.supportsAction(protocol, state, action)) encoder.encode(protocol, state, action).firstOrNull() else null
            RemoteButton(id = "${source}_ac_${action.name.lowercase()}", label = action.label,
                iconName = resolveIconName(action.keyType, action.label), sourceKeyType = action.keyType,
                sourceSlot = "ac_${action.name.lowercase()}", acAction = action,
                rawPattern = signal?.patternMicros?.toList() ?: emptyList(), frequencyHz = signal?.frequencyHz ?: 38000)
        }
    }
}

