package org.qinyan.rimoat.data

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import org.qinyan.rimoat.hardware.IrSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.BitSet
import java.util.zip.InflaterInputStream
import kotlinx.serialization.json.*

/** Portable, offline frames generated from the native encoder for all bundled AC protocols. */
class AcSignalStore(private val context: Context) : AcSignalEncoder {
    private data class ParsedIndex(
        val remotes: Map<String, Int>,
        val modes: Map<Int, List<AcMode>>
    )

    private data class FrameCacheKey(
        val protocol: Int,
        val settings: AcSettings,
        val action: AcAction
    )

    private val mutex = Mutex()
    private var database: SQLiteDatabase? = null

    private val parsedIndex: ParsedIndex by lazy {
        val root = context.assets.open("ac_index.json").bufferedReader().use {
            Json.parseToJsonElement(it.readText()).jsonObject
        }
        val remotesObj = root.getValue("remotes").jsonObject
        val remotes = HashMap<String, Int>(remotesObj.size)
        for ((remoteId, value) in remotesObj) {
            remotes[remoteId] = value.jsonPrimitive.int
        }
        val modesObj = root.getValue("modes").jsonObject
        val modes = HashMap<Int, List<AcMode>>(modesObj.size)
        for ((protocolStr, arr) in modesObj) {
            val protocolId = protocolStr.toIntOrNull() ?: continue
            val available = arr.jsonArray.mapTo(HashSet()) { it.jsonPrimitive.int }
            modes[protocolId] = AcMode.entries.filter { it.nativeValue in available }
        }
        ParsedIndex(remotes, modes)
    }

    private val featureKeysByProtocol: Map<Int, Set<Int>> by lazy {
        val root = context.assets.open("ac_features.json").bufferedReader().use {
            Json.parseToJsonElement(it.readText()).jsonObject
        }
        val map = HashMap<Int, Set<Int>>(root.size)
        for ((protocolStr, obj) in root) {
            val protocolId = protocolStr.toIntOrNull() ?: continue
            val keysArr = obj.jsonObject["keys"]?.jsonArray ?: continue
            val keySet = HashSet<Int>(keysArr.size)
            for (el in keysArr) keySet.add(el.jsonPrimitive.int)
            map[protocolId] = keySet
        }
        map
    }

    private fun <K, V> lruMap(maxEntries: Int): MutableMap<K, V> =
        object : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxEntries
        }

    private val availableActionsCache = lruMap<Int, Set<AcAction>>(32)
    private val protocolStateBits = lruMap<Int, BitSet>(8)
    private val encodedFramesCache = lruMap<FrameCacheKey, List<IrSignal.Raw>>(64)

    private fun cloneFrames(frames: List<IrSignal.Raw>): List<IrSignal.Raw> =
        frames.map { IrSignal.Raw(it.frequencyHz, it.patternMicros.clone()) }

    private fun availableActionsSync(protocol: Int): Set<AcAction> {
        synchronized(availableActionsCache) {
            availableActionsCache[protocol]?.let { return it }
        }
        val keys = featureKeysByProtocol[protocol] ?: emptySet()
        val computed = AcAction.entries.filterTo(LinkedHashSet()) {
            it.keyType in keys &&
                (NativeAcEncoder.available || it.ordinal <= AcAction.MODE.ordinal) &&
                (it != AcAction.TIMER || AcTimerOptions.forProtocol(protocol).isNotEmpty())
        }
        synchronized(availableActionsCache) {
            availableActionsCache[protocol] = computed
        }
        return computed
    }

    override suspend fun availableActions(protocol: Int): Set<AcAction> = availableActionsSync(protocol)

    override suspend fun unavailableReason(protocol: Int, settings: AcSettings, action: AcAction): AcUnavailableReason? {
        val keys = featureKeysByProtocol[protocol] ?: return AcUnavailableReason.CAPABILITY_UNAVAILABLE
        if (action.keyType !in keys) return AcUnavailableReason.MISSING_SIGNAL
        if (action == AcAction.TIMER && AcTimerOptions.forProtocol(protocol, settings.powerOn).isEmpty())
            return AcUnavailableReason.STATE_RESTRICTED
        if (!NativeAcEncoder.available && (action.ordinal > AcAction.MODE.ordinal || !settings.isLegacyState()))
            return AcUnavailableReason.ENCODER_UNAVAILABLE
        return super.unavailableReason(protocol, settings, action)
    }

    private suspend fun db(): SQLiteDatabase = mutex.withLock {
        database?.let { if (it.isOpen) return@withLock it }
        val target = context.getDatabasePath(DB_FILE_NAME)
        val prefs = context.getSharedPreferences(META_PREFS_NAME, Context.MODE_PRIVATE)
        val currentCatalogVersion = CatalogUpgrade.VERSION
        val currentAppVersionCode = currentAppVersionCode()
        val installedCatalogVersion = prefs.getInt(KEY_INSTALLED_AC_DB_VERSION, 0)
        val installedAppVersionCode = prefs.getLong(KEY_INSTALLED_AC_APP_VERSION_CODE, -1L)

        val needsInstall = !target.exists() ||
            target.length() == 0L ||
            installedCatalogVersion != currentCatalogVersion ||
            installedAppVersionCode != currentAppVersionCode

        if (!needsInstall) {
            try {
                return@withLock openAndVerifyDatabase(target).also { database = it }
            } catch (_: SQLiteException) {
                // Fall through to reinstall from assets when the installed database cannot be opened or verified.
            } catch (_: IllegalStateException) {
                // Fall through to reinstall from assets when schema or integrity validation fails.
            }
        }

        installDatabaseFromAssets(target)
        val reopened = openAndVerifyDatabase(target)
        prefs.edit()
            .putInt(KEY_INSTALLED_AC_DB_VERSION, currentCatalogVersion)
            .putLong(KEY_INSTALLED_AC_APP_VERSION_CODE, currentAppVersionCode)
            .apply()
        database = reopened
        reopened
    }

    private fun currentAppVersionCode(): Long = try {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    } catch (_: Exception) {
        -1L
    }

    private fun installDatabaseFromAssets(target: File) {
        val parent = target.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val temp = File(target.path + ".tmp")
        try {
            context.assets.open(ASSET_DB_NAME).use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output); output.fd.sync() }
            }
            openAndVerifyDatabase(temp).close()
            replaceAtomically(temp, target)
            deleteDatabaseSidecars(target)
        } finally {
            temp.delete()
        }
    }

    private fun openAndVerifyDatabase(file: File): SQLiteDatabase {
        val opened = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            opened.rawQuery("PRAGMA quick_check", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                    AppStrings.get(R.string.msg_ac_install_failed, "空调码库安装失败，请重试")
                }
            }
            opened.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('metadata','frames','states')",
                null
            ).use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 3) {
                    AppStrings.get(R.string.msg_ac_install_failed, "空调码库安装失败，请重试")
                }
            }
            opened.rawQuery("SELECT value FROM metadata WHERE key='version'", null).use { cursor ->
                check(cursor.moveToFirst() && !cursor.getString(0).isNullOrBlank()) {
                    AppStrings.get(R.string.msg_ac_install_failed, "空调码库安装失败，请重试")
                }
            }
            return opened
        } catch (t: Throwable) {
            opened.close()
            throw t
        }
    }

    private fun replaceAtomically(temporary: File, target: File) {
        if (Build.VERSION.SDK_INT in 1..25) {
            check(temporary.renameTo(target)) {
                AppStrings.get(R.string.msg_ac_install_failed, "空调码库安装失败，请重试")
            }
        } else {
            // File.renameTo does not overwrite existing files on Windows JVM tests.
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (e: Exception) {
                throw IllegalStateException(
                    AppStrings.get(R.string.msg_ac_install_failed, "空调码库安装失败，请重试"),
                    e
                )
            }
        }
    }

    private fun deleteDatabaseSidecars(target: File) {
        File(target.path + "-journal").delete()
        File(target.path + "-wal").delete()
        File(target.path + "-shm").delete()
    }

    internal suspend fun closeDatabaseForTest() {
        mutex.withLock {
            database?.close()
            database = null
        }
    }

    private suspend fun stateBitsFor(protocol: Int): BitSet {
        synchronized(protocolStateBits) {
            protocolStateBits[protocol]?.let { return it }
        }
        return withContext(Dispatchers.IO) {
            val database = db()
            val bits = BitSet(16384)
            database.rawQuery(
                "SELECT state_key FROM states WHERE protocol_id=?",
                arrayOf("$protocol")
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val key = cursor.getInt(0)
                    if (key >= 0) bits.set(key)
                }
            }
            synchronized(protocolStateBits) {
                protocolStateBits[protocol] ?: bits.also { protocolStateBits[protocol] = it }
            }
        }
    }

    override suspend fun protocolFor(sourceRemoteId: String): Int? =
        parsedIndex.remotes[sourceRemoteId]

    override suspend fun supportedModes(protocol: Int): List<AcMode> =
        checkNotNull(parsedIndex.modes[protocol]) {
            AppStrings.get(R.string.msg_ac_protocol_missing, "未找到空调协议")
        }

    /** ARM encodes directly; fallback frames stay packaged and are extracted only when needed. */
    suspend fun compactExistingCache() = withContext(Dispatchers.IO) {
        if (NativeAcEncoder.available && parsedIndex.modes.isNotEmpty()) {
            mutex.withLock {
                if (database == null) {
                    context.deleteDatabase(DB_FILE_NAME)
                    context.getSharedPreferences(META_PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .remove(KEY_INSTALLED_AC_DB_VERSION)
                        .remove(KEY_INSTALLED_AC_APP_VERSION_CODE)
                        .apply()
                }
            }
        }
    }

    override suspend fun supportsAction(protocol: Int, settings: AcSettings, action: AcAction): Boolean {
        if (action !in availableActionsSync(protocol)) return false
        if (action == AcAction.TIMER && !AcTimerOptions.supports(protocol, settings)) return false
        if (NativeAcEncoder.available) {
            val cacheKey = FrameCacheKey(protocol, settings, action)
            val cached = synchronized(encodedFramesCache) { encodedFramesCache[cacheKey] }
            if (cached != null) return cached.isNotEmpty()
            val frames = withContext(Dispatchers.Default) { nativeFrames(protocol, settings, action) }
            synchronized(encodedFramesCache) { encodedFramesCache[cacheKey] = frames }
            return frames.isNotEmpty()
        }
        if (!settings.isLegacyState() || action.ordinal > AcAction.SWING.ordinal) return false
        val bits = stateBitsFor(protocol)
        if (!bits.get(settings.stateKey(action))) return false
        if (action == AcAction.POWER_ON) {
            val restore = if (settings.mode == AcMode.COOL || settings.mode == AcMode.HEAT)
                AcAction.TEMP_UP else AcAction.MODE
            if (!bits.get(settings.stateKey(restore, settings.counter + 1))) return false
        }
        return true
    }

    override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction): List<IrSignal.Raw> {
        check(action in availableActionsSync(protocol)) { AppStrings.get(R.string.msg_ac_function_missing, "该机型的码库未提供此功能，可学习原遥控器按键") }
        require(action != AcAction.TIMER || AcTimerOptions.supports(protocol, settings)) { AppStrings.get(R.string.msg_timer_unsupported, "该方案不支持此定时时长") }
        val cacheKey = FrameCacheKey(protocol, settings, action)
        synchronized(encodedFramesCache) { encodedFramesCache[cacheKey] }?.let { cached ->
            if (cached.isNotEmpty()) return cloneFrames(cached)
        }
        return withContext(Dispatchers.IO) {
            if (NativeAcEncoder.available) {
                val frames = nativeFrames(protocol, settings, action)
                check(frames.isNotEmpty()) { AppStrings.get(R.string.msg_ac_state_unsupported, "该机型不支持此功能或状态") }
                synchronized(encodedFramesCache) { encodedFramesCache[cacheKey] = frames }
                return@withContext cloneFrames(frames)
            }
            check(settings.isLegacyState() && action.ordinal <= AcAction.SWING.ordinal) { AppStrings.get(R.string.msg_ac_full_encoder_required, "此设备需要完整空调编码器") }
            val database = db()
            fun frame(key: Int): IrSignal.Raw = database.rawQuery(
                "SELECT frequency, pattern FROM states JOIN frames ON frames.id=frame_id WHERE protocol_id=? AND state_key=?",
                arrayOf("$protocol", "$key")
            ).use { cursor ->
                check(cursor.moveToFirst()) { AppStrings.get(R.string.msg_ac_choose_other_state, "该机型不支持此状态，请选择其它温度或模式") }
                val bytes = InflaterInputStream(ByteArrayInputStream(cursor.getBlob(1))).use { it.readBytes() }
                require(bytes.isNotEmpty() && bytes.size % 4 == 0 && bytes.size <= 3200)
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val pulses = IntArray(bytes.size / 4) { buffer.int }
                require(pulses.all { it > 0 } && pulses.sumOf { it.toLong() } < 2_000_000)
                IrSignal.Raw(cursor.getInt(0), pulses)
            }
            val built = buildList {
                add(frame(settings.stateKey(action)))
                // Restore the last full setting after POWER_ON.
                if (action == AcAction.POWER_ON) {
                    val restore = if (settings.mode == AcMode.COOL || settings.mode == AcMode.HEAT)
                        AcAction.TEMP_UP else AcAction.MODE
                    add(frame(settings.stateKey(restore, settings.counter + 1)))
                }
            }
            synchronized(encodedFramesCache) { encodedFramesCache[cacheKey] = built }
            cloneFrames(built)
        }
    }

    private fun nativeFrames(protocol: Int, settings: AcSettings, action: AcAction): List<IrSignal.Raw> {
        val first = NativeAcEncoder.encode(protocol, settings, action) ?: return emptyList()
        if (action != AcAction.POWER_ON) return listOf(first)
        val restore = if (settings.mode == AcMode.COOL || settings.mode == AcMode.HEAT) AcAction.TEMP_UP else AcAction.MODE
        val second = NativeAcEncoder.encode(protocol, settings, restore, settings.counter + 1) ?: return emptyList()
        return listOf(first, second)
    }

    companion object {
        internal const val DB_FILE_NAME = "ac_states_v2.db"
        private const val ASSET_DB_NAME = "ac_states.db"
        internal const val META_PREFS_NAME = "ir_db_meta"
        internal const val KEY_INSTALLED_AC_DB_VERSION = "installed_ac_db_version"
        internal const val KEY_INSTALLED_AC_APP_VERSION_CODE = "installed_ac_app_version_code"
    }
}
