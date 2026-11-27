package org.qinyan.rimoat

import org.qinyan.rimoat.testing.AppStringsResourceRule
import android.content.ContextWrapper
import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.qinyan.rimoat.data.*
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files

class MigrationRegressionTest {
    @get:org.junit.Rule val strings = AppStringsResourceRule()

    private val remote = LegacyRemoteMigration.normalizeKeys(RemoteProfile("migration_original", "Migration original", room = "Occupied", updatedAt = 1L,
        buttons = listOf(RemoteButton("power", "Power", rawPattern = listOf(9000, 4500, 560)))))

    @Test fun versionOneAndSingleProfileRemainCompatibleAndVersionTwoKeepsEmptyRooms() {
        val legacy = RemoteBackupCodec.decodeBackup("""{"format":"rimoat.backup","version":1,"remotes":[{"id":"old","name":"Old"}]}""")
        assertEquals("old", legacy.remotes.single().id)
        assertEquals(RemotePanelLayout(), legacy.remotes.single().panelLayout)
        assertEquals(remote, RemoteBackupCodec.decodeBackup(Json.encodeToString(remote)).remotes.single())
        val empty = RemoteBackup(remotes = emptyList(), rooms = listOf("Empty room"), preferences = PortablePreferences(true, false))
        assertEquals(empty, RemoteBackupCodec.decodeBackup(RemoteBackupCodec.encode(empty)))
        assertTrue(runCatching { RemoteBackupCodec.decodeBackup(RemoteBackupCodec.encode(empty.copy(version = 3))) }.isFailure)
    }

    @Test fun conflictChoicesPreserveSignalsAndProduceExactSummary() {
        val incoming = remote.copy(name = "Incoming")
        val added = remote.copy(id = "migration_new", name = "New")
        val backup = RemoteBackup(remotes = listOf(incoming, added))
        val existing = setOf(remote.id)
        assertEquals(ImportSummary(1, 1, 1, 0), importSummary(backup, existing, emptyMap()))
        assertEquals(listOf(added), RemoteBackupCodec.plan(backup, existing, emptyMap()))
        assertEquals(incoming, RemoteBackupCodec.plan(backup, existing, mapOf(remote.id to ConflictChoice.OVERWRITE)).first())
        val copy = RemoteBackupCodec.plan(backup, existing, mapOf(remote.id to ConflictChoice.COPY)).first()
        assertNotEquals(remote.id, copy.id)
        assertEquals(remote.buttons, copy.buttons)
        assertEquals(ImportSummary(2, 1, 0, 0), importSummary(backup, existing, mapOf(remote.id to ConflictChoice.COPY)))
    }

    private class MemoryPreferences {
        val values = mutableMapOf<String, Boolean>()
        var failNextCommit = false
        val preferences: SharedPreferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getBoolean" -> values[args!![0] as String] ?: args[1]
                "edit" -> {
                    val writes = mutableMapOf<String, Boolean>()
                    lateinit var editor: SharedPreferences.Editor
                    editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                        arrayOf(SharedPreferences.Editor::class.java)) { _, editMethod, editArgs ->
                        when (editMethod.name) {
                            "putBoolean" -> { writes[editArgs!![0] as String] = editArgs[1] as Boolean; editor }
                            "commit" -> if (failNextCommit) { failNextCommit = false; false } else { values.putAll(writes); true }
                            else -> error(editMethod.name)
                        }
                    } as SharedPreferences.Editor
                    editor
                }
                else -> error(method.name)
            }
        } as SharedPreferences
    }

    private fun storage(block: suspend (RemoteRepository, File, MemoryPreferences) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("rimoat-migration-test-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = MemoryPreferences()
        val context = object : ContextWrapper(null) {
            override fun getFilesDir() = dir
            override fun getSharedPreferences(name: String, mode: Int) = prefs.preferences
        }
        try {
            val repository = RemoteRepository(context, scope)
            repository.awaitLoaded()
            block(repository, dir, prefs)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); dir.deleteRecursively() }
    }

    private fun fileBytes(dir: File): Map<String, ByteArray> = dir.walkTopDown()
        .filter { it.isFile }
        .associate { it.relativeTo(dir).invariantSeparatorsPath to it.readBytes() }

    private suspend fun assertImportWithRecoverySnapshot(repo: RemoteRepository, dir: File, prefs: MemoryPreferences) {
        prefs.values["use_dynamic_color"] = false
        prefs.values["pure_black_background"] = true
        val beforeRemotes = repo.remotes.value
        val beforeRooms = repo.rooms.value
        val beforeFiles = fileBytes(File(dir, "remotes"))
        val incoming = remote.copy(id = "migration_incoming", room = "Imported room")

        assertEquals(1, repo.importBackup(RemoteBackup(remotes = listOf(incoming)),
            emptyMap(), PortablePreferences(true, false)))

        beforeFiles.forEach { (path, bytes) -> assertArrayEquals(path, bytes, File(dir, "remotes/$path").readBytes()) }
        assertEquals(beforeRemotes.size + 1, repo.remotes.value.size)
        assertEquals(incoming, repo.getRemote(incoming.id))
        assertTrue("Imported room" in repo.rooms.value)
        assertEquals(PortablePreferences(true, false), repo.portablePreferences())
        val snapshot = repo.readRecoverySnapshot()
        assertEquals(beforeRemotes, snapshot.remotes)
        assertEquals(beforeRooms, snapshot.rooms)
        assertEquals(PortablePreferences(false, true), snapshot.preferences)
        assertFalse(File(dir, "migration/before-import.json").readText().contains('\n'))
        assertFalse(File(dir, "migration/pending.json").exists())
        repo.loadAllRemotes()
        assertEquals(beforeRemotes.size + 1, repo.remotes.value.size)
        assertEquals(incoming, repo.getRemote(incoming.id))
        assertEquals(snapshot, repo.readRecoverySnapshot())
    }

    @Test fun migrationIncludesEmptyRoomsAndPreservesSnapshotBeforeOverwrite() = storage { repo, _, prefs ->
        repo.saveRemote(remote)
        val saved = checkNotNull(repo.getRemote(remote.id))
        repo.editRoom(null, "Empty original")
        prefs.values["use_dynamic_color"] = true
        prefs.values["pure_black_background"] = false
        repo.importBackup(RemoteBackup(remotes = listOf(remote.copy(name = "Replaced")), rooms = listOf("Empty imported")),
            mapOf(remote.id to ConflictChoice.OVERWRITE))
        assertEquals("Replaced", repo.getRemote(remote.id)?.name)
        assertTrue(repo.rooms.value.containsAll(listOf("Empty original", "Empty imported", "Occupied")))
        val snapshot = repo.readRecoverySnapshot()
        assertEquals(saved, snapshot.remotes.single())
        assertTrue(snapshot.rooms.contains("Empty original"))
        assertFalse(snapshot.rooms.contains("Empty imported"))
        assertEquals(PortablePreferences(true, false), snapshot.preferences)
    }

    @Test fun preferenceFailureRollsBackProfilesRoomsAndSelectedPreferences() = storage { repo, dir, prefs ->
        repo.saveRemote(remote)
        val saved = checkNotNull(repo.getRemote(remote.id))
        repo.editRoom(null, "Empty original")
        val original = File(dir, "remotes/${remote.id}.json").readBytes()
        val oldRooms = File(dir, "rooms.json").readBytes()
        prefs.values["use_dynamic_color"] = false
        prefs.failNextCommit = true
        val incoming = RemoteBackup(remotes = listOf(remote.copy(name = "Changed"), remote.copy(id = "migration_new")),
            rooms = listOf("New room"))
        assertTrue(runCatching { repo.importBackup(incoming, mapOf(remote.id to ConflictChoice.OVERWRITE), PortablePreferences(true, true)) }.isFailure)
        assertArrayEquals(original, File(dir, "remotes/${remote.id}.json").readBytes())
        assertArrayEquals(oldRooms, File(dir, "rooms.json").readBytes())
        assertFalse(File(dir, "remotes/migration_new.json").exists())
        assertFalse(prefs.values["use_dynamic_color"]!!)
        assertEquals(saved, repo.getRemote(remote.id))
        assertFalse(File(dir, "migration/pending.json").exists())
    }

    @Test fun interruptedMigrationRestoresReplacementsAndDeletesOnlyNewFiles() = storage { repo, dir, _ ->
        repo.saveRemote(remote)
        val saved = checkNotNull(repo.getRemote(remote.id))
        val original = File(dir, "remotes/${remote.id}.json").readText()
        val untouched = File(dir, "unrelated-user-data.txt").apply { writeText("Preserve") }
        File(dir, "remotes/${remote.id}.json").writeText(Json.encodeToString(remote.copy(name = "Partial")))
        File(dir, "remotes/migration_new.json").writeText(Json.encodeToString(remote.copy(id = "migration_new")))
        val journal = File(dir, "migration/pending.json").apply { parentFile!!.mkdirs() }
        journal.writeText("""{"originals":{"remotes/${remote.id}.json":${Json.encodeToString(original)},"remotes/migration_new.json":null},"preferences":null}""")
        repo.loadAllRemotes()
        assertEquals(saved, repo.getRemote(remote.id))
        assertFalse(File(dir, "remotes/migration_new.json").exists())
        assertEquals("Preserve", untouched.readText())
        assertFalse(journal.exists())
    }

    @Test fun importAndRecoveryAllowMoreThanFiveHundredExistingProfiles() = storage { repo, dir, prefs ->
        repo.importRemotes(List(500) { index -> remote.copy(id = "migration_profile_$index") }, ImportMode.MERGE)
        repo.saveRemote(remote.copy(id = "migration_profile_500"))

        assertImportWithRecoverySnapshot(repo, dir, prefs)
        assertEquals(501, repo.readRecoverySnapshot().remotes.size)
    }

    @Test fun importAndRecoveryAllowMoreThanFiveHundredIndependentRooms() = storage { repo, dir, prefs ->
        repo.importBackup(RemoteBackup(remotes = emptyList(), rooms = List(500) { "Room $it" }), emptyMap())
        repo.editRoom(null, "Room 500")

        assertImportWithRecoverySnapshot(repo, dir, prefs)
        assertEquals(501, repo.readRecoverySnapshot().rooms.size)
    }

    @Test fun importAndRecoveryAllowCompactSnapshotsOverEightMiB() = storage { repo, dir, prefs ->
        val longPattern = List(20_000) { 99 }
        val oversized = remote.copy(id = "migration_large", name = "Large profile",
            buttons = List(145) { index -> RemoteButton("raw_$index", "Key $index", rawPattern = longPattern) })
        RemoteBackupCodec.validate(oversized)
        assertTrue(RemoteBackupCodec.encodeRecoverySnapshot(RemoteBackup(remotes = listOf(oversized)))
            .toByteArray(Charsets.UTF_8).size > RemoteBackupCodec.MAX_BYTES)
        repo.saveRemote(oversized)
        val savedOversized = checkNotNull(repo.getRemote(oversized.id))

        assertImportWithRecoverySnapshot(repo, dir, prefs)
        val snapshotFile = File(dir, "migration/before-import.json")
        assertTrue(snapshotFile.length() > RemoteBackupCodec.MAX_BYTES)
        assertTrue(runCatching { RemoteBackupCodec.decodeBackup(snapshotFile.readText()) }.isFailure)
        assertEquals(savedOversized, repo.getRemote(oversized.id))
    }

    @Test fun recoveryReadsPrettyPrintedSnapshotsAndStillRejectsInvalidContents() {
        val backup = RemoteBackup(remotes = listOf(remote), rooms = listOf(remote.room),
            preferences = PortablePreferences(false, true))
        assertEquals(backup, RemoteBackupCodec.decodeRecoverySnapshot(RemoteBackupCodec.encode(backup)))
        val invalid = listOf(
            backup.copy(format = "unknown"), backup.copy(version = 3),
            backup.copy(remotes = listOf(remote, remote)),
            backup.copy(remotes = listOf(remote.copy(id = "../outside"))),
            backup.copy(remotes = listOf(remote.copy(buttons = listOf(RemoteButton("bad", "Bad", rawPattern = listOf(0)))))),
            backup.copy(rooms = listOf(" ")), backup.copy(rooms = listOf("Room", "Room")),
            backup.copy(rooms = listOf("x".repeat(61)))
        )
        invalid.forEach {
            assertTrue(runCatching { RemoteBackupCodec.decodeRecoverySnapshot(RemoteBackupCodec.encode(it)) }.isFailure)
        }
        assertTrue(runCatching { RemoteBackupCodec.decodeRecoverySnapshot("{") }.isFailure)
    }

    @Test fun externalImportsStillRejectProfileAndRoomCountsBeforeAnyWrites() = storage { repo, dir, _ ->
        repo.saveRemote(remote)
        repo.importBackup(RemoteBackup(remotes = emptyList()), emptyMap())
        val beforeFiles = fileBytes(dir)
        val invalid = listOf(
            RemoteBackup(remotes = List(501) { remote.copy(id = "external_$it") }),
            RemoteBackup(remotes = emptyList(), rooms = List(501) { "Room $it" })
        )
        invalid.forEach { backup ->
            assertTrue(runCatching { RemoteBackupCodec.decodeBackup(RemoteBackupCodec.encode(backup)) }.isFailure)
            assertTrue(runCatching { repo.importBackup(backup, emptyMap()) }.isFailure)
            val actual = fileBytes(dir)
            assertEquals(beforeFiles.keys, actual.keys)
            beforeFiles.forEach { (path, bytes) -> assertArrayEquals(path, bytes, actual.getValue(path)) }
        }
    }

    @Test fun portablePreferencesNeverExportSystemOrHardwareIdentities() = storage { repo, _, prefs ->
        prefs.values["usb_permission"] = true
        repo.importBackup(RemoteBackup(remotes = emptyList(), rooms = listOf("Empty")), emptyMap(), PortablePreferences(true, null))
        assertEquals(setOf("usb_permission", "use_dynamic_color"), prefs.values.keys)
        val exported = repo.exportRemotes(preferences = repo.portablePreferences())
        assertFalse(exported.contains("usb_permission"))
        assertFalse(exported.contains("widgetId"))
        assertEquals(PortablePreferences(true, false), RemoteBackupCodec.decodeBackup(exported).preferences)
    }

    @Test fun recoverySnapshotKeepsCurrentPortablePreferencesWhenImportDoesNotChangeThem() = storage { repo, _, prefs ->
        prefs.values["use_dynamic_color"] = true
        prefs.values["pure_black_background"] = false
        repo.importBackup(RemoteBackup(remotes = emptyList(), preferences = PortablePreferences(false, true)), emptyMap())

        assertEquals(PortablePreferences(true, false), repo.readRecoverySnapshot().preferences)
        assertEquals(PortablePreferences(true, false), repo.portablePreferences())
    }

    @Test fun directBackupImportNormalizesBeforeWritingAndKeepsVersionTwo() = storage { repo, dir, _ ->
        val button = RemoteButton("direction_上", "上", builtinLabel = "上", rawPattern = listOf(9000, 4500, 560))
        val incoming = RemoteProfile("legacy_keys", "Legacy TV", category = DeviceCategory.TV,
            buttons = listOf(button), verifiedFunctions = listOf("上"),
            panelLayout = RemotePanelLayout(hidden = setOf("key:direction_上")))
        repo.importBackup(RemoteBackup(remotes = listOf(incoming)), emptyMap())
        val saved = checkNotNull(repo.getRemote(incoming.id))
        assertEquals(LegacyRemoteMigration.normalizeKeys(incoming), saved)
        assertEquals(saved, Json.decodeFromString<RemoteProfile>(File(dir, "remotes/legacy_keys.json").readText()))
        val exported = RemoteBackupCodec.decodeBackup(repo.exportRemotes())
        assertEquals(2, exported.version)
        assertEquals(saved, exported.remotes.single())
    }

}
