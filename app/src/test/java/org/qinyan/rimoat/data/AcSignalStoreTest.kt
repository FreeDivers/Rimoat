package org.qinyan.rimoat.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.DeflaterOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcSignalStoreTest {
    private lateinit var app: Context
    private val storesToClose = mutableListOf<AcSignalStore>()
    private val protocolId = 27
    private val sampleSettings = AcSettings(
        powerOn = true,
        temperature = 26,
        mode = AcMode.COOL,
        fanSpeed = AcFanSpeed.AUTO,
        isSwingOn = false,
        counter = 0
    )

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        assertFalse("JVM test expects fallback offline AC store path", NativeAcEncoder.available)
        app.deleteDatabase(AcSignalStore.DB_FILE_NAME)
        app.getSharedPreferences(AcSignalStore.META_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        runBlocking {
            storesToClose.forEach { it.closeDatabaseForTest() }
            storesToClose.clear()
        }
        app.deleteDatabase(AcSignalStore.DB_FILE_NAME)
        app.getSharedPreferences(AcSignalStore.META_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun newStore(): AcSignalStore = AcSignalStore(app).also { storesToClose += it }

    private fun currentAppVersionCode(): Long = PackageInfoCompat.getLongVersionCode(
        app.packageManager.getPackageInfo(app.packageName, 0)
    )

    private fun writeStaleAcDatabase(target: File, markerPulse: Int) {
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        val raw = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(markerPulse)
            .putInt(markerPulse)
            .array()
        val deflated = ByteArrayOutputStream().apply {
            DeflaterOutputStream(this).use { it.write(raw) }
        }.toByteArray()
        SQLiteDatabase.openOrCreateDatabase(target, null).use { db ->
            db.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("CREATE TABLE frames(id INTEGER PRIMARY KEY, frequency INTEGER NOT NULL, pattern BLOB NOT NULL)")
            db.execSQL(
                "CREATE TABLE states(protocol_id INTEGER, state_key INTEGER, frame_id INTEGER NOT NULL, " +
                    "PRIMARY KEY(protocol_id, state_key)) WITHOUT ROWID"
            )
            db.execSQL("INSERT INTO metadata VALUES ('version', '1')")
            db.execSQL("INSERT INTO frames VALUES (1, 38000, ?)", arrayOf(deflated))
            db.execSQL(
                "INSERT INTO states VALUES (?, ?, 1)",
                arrayOf(protocolId, sampleSettings.stateKey(AcAction.TEMP_UP))
            )
        }
    }

    @Test
    fun reinstallsBundledDatabaseWhenInstalledVersionIsMissingOrOutdated() = runBlocking {
        val target = app.getDatabasePath(AcSignalStore.DB_FILE_NAME)
        val markerPulse = 12345
        writeStaleAcDatabase(target, markerPulse)

        val store = newStore()
        val encoded = store.encode(protocolId, sampleSettings, AcAction.TEMP_UP)

        assertTrue(encoded.isNotEmpty())
        assertFalse(
            "Outdated installed ac_states_v2.db should be replaced with the bundled asset",
            encoded.first().patternMicros.contentEquals(intArrayOf(markerPulse, markerPulse))
        )
        assertFalse(File(target.path + ".tmp").exists())
    }

    @Test
    fun reinstallsBundledDatabaseWhenAppVersionChanges() = runBlocking {
        val target = app.getDatabasePath(AcSignalStore.DB_FILE_NAME)
        val markerPulse = 23456
        writeStaleAcDatabase(target, markerPulse)

        val currentVersionCode = currentAppVersionCode()
        app.getSharedPreferences(AcSignalStore.META_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(AcSignalStore.KEY_INSTALLED_AC_DB_VERSION, CatalogUpgrade.VERSION)
            .putLong(AcSignalStore.KEY_INSTALLED_AC_APP_VERSION_CODE, currentVersionCode - 1L)
            .commit()

        val store = newStore()
        val encoded = store.encode(protocolId, sampleSettings, AcAction.TEMP_UP)

        assertTrue(encoded.isNotEmpty())
        assertFalse(
            "Installed ac_states_v2.db should refresh when app versionCode changes",
            encoded.first().patternMicros.contentEquals(intArrayOf(markerPulse, markerPulse))
        )
        val prefs = app.getSharedPreferences(AcSignalStore.META_PREFS_NAME, Context.MODE_PRIVATE)
        assertEquals(currentVersionCode, prefs.getLong(AcSignalStore.KEY_INSTALLED_AC_APP_VERSION_CODE, -1L))
        assertEquals(CatalogUpgrade.VERSION, prefs.getInt(AcSignalStore.KEY_INSTALLED_AC_DB_VERSION, 0))
    }

    @Test
    fun recoversCorruptedDatabaseByReinstallingFromAssets() = runBlocking {
        val target = app.getDatabasePath(AcSignalStore.DB_FILE_NAME)
        target.parentFile?.mkdirs()
        target.writeBytes("not a valid sqlite database".toByteArray())

        val currentVersionCode = currentAppVersionCode()
        app.getSharedPreferences(AcSignalStore.META_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(AcSignalStore.KEY_INSTALLED_AC_DB_VERSION, CatalogUpgrade.VERSION)
            .putLong(AcSignalStore.KEY_INSTALLED_AC_APP_VERSION_CODE, currentVersionCode)
            .commit()

        val store = newStore()
        assertTrue(store.supportsAction(protocolId, sampleSettings, AcAction.TEMP_UP))
        val encoded = store.encode(protocolId, sampleSettings, AcAction.TEMP_UP)

        assertTrue(encoded.isNotEmpty())
        assertTrue(encoded.first().patternMicros.isNotEmpty())
        assertFalse(File(target.path + ".tmp").exists())
    }

    @Test
    fun recoversDatabaseWithMissingSchemaTablesByReinstallingFromAssets() = runBlocking {
        val target = app.getDatabasePath(AcSignalStore.DB_FILE_NAME)
        target.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(target, null).use { db ->
            db.execSQL("CREATE TABLE unrelated(id INTEGER PRIMARY KEY)")
        }

        val currentVersionCode = currentAppVersionCode()
        app.getSharedPreferences(AcSignalStore.META_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(AcSignalStore.KEY_INSTALLED_AC_DB_VERSION, CatalogUpgrade.VERSION)
            .putLong(AcSignalStore.KEY_INSTALLED_AC_APP_VERSION_CODE, currentVersionCode)
            .commit()

        val store = newStore()
        val encoded = store.encode(protocolId, sampleSettings, AcAction.TEMP_UP)

        assertTrue(encoded.isNotEmpty())
        assertTrue(encoded.first().patternMicros.isNotEmpty())
    }

    @Test
    fun reusesValidDatabaseWhenInstalledVersionsMatch() = runBlocking {
        val target = app.getDatabasePath(AcSignalStore.DB_FILE_NAME)
        val markerPulse = 34567
        writeStaleAcDatabase(target, markerPulse)

        val currentVersionCode = currentAppVersionCode()
        app.getSharedPreferences(AcSignalStore.META_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(AcSignalStore.KEY_INSTALLED_AC_DB_VERSION, CatalogUpgrade.VERSION)
            .putLong(AcSignalStore.KEY_INSTALLED_AC_APP_VERSION_CODE, currentVersionCode)
            .commit()

        val store = newStore()
        val encoded = store.encode(protocolId, sampleSettings, AcAction.TEMP_UP)

        assertEquals(1, encoded.size)
        assertTrue(encoded.first().patternMicros.contentEquals(intArrayOf(markerPulse, markerPulse)))
    }
}
