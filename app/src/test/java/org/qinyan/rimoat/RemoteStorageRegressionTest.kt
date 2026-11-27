package org.qinyan.rimoat

import org.qinyan.rimoat.testing.AppStringsResourceRule
import android.content.Context
import android.content.ContextWrapper
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.i18n.UiText
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RemoteStorageRegressionTest {
    @get:org.junit.Rule val strings = AppStringsResourceRule()

    private fun context(dir: File) = object : ContextWrapper(null) {
        override fun getFilesDir(): File = dir
    }

    @Test fun reloadClearsOldErrorsAndRetainsCurrentPartialFailures() = runBlocking {
        val dir = Files.createTempDirectory("reload-remotes-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = RemoteRepository(context(dir), scope)
            repo.awaitLoaded()
            val corrupt = File(dir, "remotes/broken.json").apply { writeText("{") }
            assertTrue(repo.loadAllRemotes().isEmpty())
            assertNotNull(repo.errorMessage.value)
            repo.saveRemote(RemoteProfile("valid", "电视"))
            assertEquals(1, repo.loadAllRemotes().size)
            assertNotNull(repo.errorMessage.value)
            assertTrue(corrupt.delete())
            assertEquals(1, repo.loadAllRemotes().size)
            assertNull(repo.errorMessage.value)
        } finally { scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun corruptRoomMetadataDoesNotBlockProfilesAndIsPreservedForRecovery() = runBlocking {
        val dir = Files.createTempDirectory("corrupt-rooms-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = RemoteRepository(context(dir), scope)
            repo.loadAllRemotes()
            repo.saveRemote(RemoteProfile("living", "电视", room = "客厅"))
            val broken = "[\"空房间\","
            File(dir, "rooms.json").writeText(broken)
            assertEquals(1, repo.loadAllRemotes().size)
            assertEquals(listOf("客厅"), repo.rooms.value)
            assertEquals(UiText.Resource(R.string.msg_rooms_recovered), repo.errorMessage.value)
            repo.toggleFavorite("living")
            assertTrue(repo.getRemote("living")!!.isFavorite)
            assertTrue(dir.listFiles()!!.any { it.name.startsWith("rooms-corrupt-") && it.readText() == broken })
            assertEquals(listOf("客厅"), RemoteRepository(context(dir), scope).also { it.loadAllRemotes() }.rooms.value)
        } finally { scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun unreadableRoomPathDoesNotGetReplacedWithEmptyMetadata() = runBlocking {
        val dir = Files.createTempDirectory("unreadable-rooms-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = RemoteRepository(context(dir), scope)
            repo.loadAllRemotes()
            File(dir, "rooms.json").mkdir()
            val preserved = File(dir, "rooms.json/original").apply { writeText("preserve") }
            assertTrue(runCatching { repo.saveRemote(RemoteProfile("new", "电视")) }.isFailure)
            assertEquals("preserve", preserved.readText())
        } finally { scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun invalidOrMismatchedLocalProfilesCannotProduceDuplicateListKeys() = runBlocking {
        val dir = Files.createTempDirectory("invalid-profile-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = RemoteRepository(context(dir), scope)
            repo.loadAllRemotes()
            repo.saveRemote(RemoteProfile("valid", "电视"))
            val original = File(dir, "remotes/valid.json").readText()
            File(dir, "remotes/duplicate.json").writeText(original)
            File(dir, "remotes/invalid.json").writeText(original.replace("\"id\": \"valid\"", "\"id\": \"../invalid\""))
            assertEquals(listOf("valid"), repo.loadAllRemotes().map { it.id })
            assertEquals(original, File(dir, "remotes/duplicate.json").readText())
            assertTrue(File(dir, "remotes/invalid.json").exists())
            val warning = repo.errorMessage.value as UiText.Resource
            assertEquals(R.string.msg_remote_file_unreadable, warning.id)
            assertEquals(1, warning.arguments.size)
            assertTrue(warning.arguments.single() in listOf("duplicate.json", "invalid.json"))
        } finally { scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun deletedRemotesStayDeletedAcrossRepositoryRecreation() = runBlocking {
        val dir = Files.createTempDirectory("remote-storage-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = RemoteRepository(context(dir), scope)
            repo.loadAllRemotes()
            repo.remotes.value.forEach { repo.deleteRemote(it.id) }
            assertTrue(repo.remotes.value.isEmpty())
            val cold = RemoteRepository(context(dir), scope)
            assertTrue(cold.loadAllRemotes().isEmpty())
        } finally { scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun newInstallStartsEmptyAndExistingProfilesSurviveWithoutPresetSeeding() = runBlocking {
        val dir = Files.createTempDirectory("remote-empty-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = RemoteRepository(context(dir), scope)
            assertTrue(repo.loadAllRemotes().isEmpty())
            val edited = RemoteProfile("preset_tv_xiaomi", "用户修改的遥控器")
            repo.saveRemote(edited)
            repo.saveRemote(RemoteProfile("custom", "学习得到的遥控器"))
            val cold = RemoteRepository(context(dir), scope)
            assertEquals(2, cold.loadAllRemotes().size)
            assertEquals(edited.name, cold.getRemote(edited.id)?.name)
            assertNotNull(cold.getRemote("custom"))
        } finally { scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun roomRenameMoveAndRemovalPersistWithoutDeletingRemotes() = runBlocking {
        val dir = Files.createTempDirectory("room-storage-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = RemoteRepository(context(dir), scope)
            repo.loadAllRemotes()
            repo.editRoom(null, "客厅")
            repo.editRoom(null, "空房间")
            repo.saveRemote(RemoteProfile("learned", "学习遥控器"))
            repo.moveToRoom("learned", "客厅")
            repo.editRoom("客厅", "卧室")
            assertEquals("卧室", repo.getRemote("learned")?.room)
            assertTrue(runCatching { repo.editRoom("卧室", "空房间") }.isFailure)
            val cold = RemoteRepository(context(dir), scope)
            cold.loadAllRemotes()
            assertEquals(setOf("卧室", "空房间"), cold.rooms.value.toSet())
            cold.editRoom("卧室", null)
            assertEquals("", cold.getRemote("learned")?.room)
            assertEquals(1, cold.remotes.value.size)
            assertEquals(listOf("空房间"), cold.rooms.value)
        } finally { scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun failedReplacementKeepsOriginalAndRemovesTemporaryFile() {
        val dir = Files.createTempDirectory("remote-atomic-test").toFile()
        try {
            val target = File(dir, "profile.json").apply { mkdir() }
            val original = File(target, "original").apply { writeText("preserved") }
            assertTrue(runCatching { AtomicLocalFile.write(target, "new content") }.isFailure)
            assertEquals("preserved", original.readText())
            assertEquals(listOf("profile.json"), dir.listFiles()!!.map { it.name })
        } finally { dir.deleteRecursively() }
    }

    @Test fun pendingRoomRenameReplaysBeforeLoadingProfiles() = runBlocking {
        val dir = Files.createTempDirectory("room-recovery-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = RemoteRepository(context(dir), scope)
            repo.loadAllRemotes()
            repo.saveRemote(RemoteProfile("first", "第一个", room = "旧房间"))
            repo.saveRemote(RemoteProfile("second", "第二个", room = "旧房间"))
            val planned = repo.remotes.value.map { it.copy(room = "新房间") }
            val codec = kotlinx.serialization.json.Json
            val profiles = codec.encodeToString(kotlinx.serialization.builtins.ListSerializer(RemoteProfile.serializer()), planned)
            File(dir, "room-mutation.json").writeText("{\"rooms\":[\"新房间\"],\"profiles\":$profiles}")
            // Simulate interruption after only one remote file was replaced.
            File(dir, "remotes/first.json").writeText(codec.encodeToString(RemoteProfile.serializer(), planned.first { it.id == "first" }))
            val cold = RemoteRepository(context(dir), scope)
            assertTrue(cold.loadAllRemotes().all { it.room == "新房间" })
            assertEquals(listOf("新房间"), cold.rooms.value)
            assertFalse(File(dir, "room-mutation.json").exists())
            assertEquals(2, cold.remotes.value.size)
        } finally { scope.cancel(); dir.deleteRecursively() }
    }

    @Test fun loadingLegacyFilesWritesMetadataOnceAndKeepsSignalAndLayoutReferences() = runBlocking {
        val dir = Files.createTempDirectory("key-migration-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = RemoteRepository(context(dir), scope)
            repo.awaitLoaded()
            val old = RemoteProfile("legacy", "TV", sourceRemoteId = "R_compat_stb_42_1", category = DeviceCategory.TV,
                updatedAt = 123, buttons = listOf(RemoteButton("direction_上", "上", builtinLabel = "上",
                    rawPattern = listOf(9000, 4500, 560))), verifiedFunctions = listOf("上"),
                panelLayout = RemotePanelLayout(hidden = setOf("key:direction_上")))
            val codec = kotlinx.serialization.json.Json { encodeDefaults = true }
            val file = File(dir, "remotes/legacy.json")
            file.writeText(codec.encodeToString(RemoteProfile.serializer(), old))
            val first = repo.loadAllRemotes().single()
            assertEquals(LegacyRemoteMigration.normalizeKeys(old), first)
            assertEquals(ApplianceType.SET_TOP_BOX, first.applianceType)
            assertEquals(42, first.brandIdx)
            assertEquals(listOf("UP"), first.verifiedFunctions)
            assertEquals(first, codec.decodeFromString<RemoteProfile>(file.readText()))
            val written = file.readBytes()
            val modified = file.lastModified()
            assertEquals(first, repo.loadAllRemotes().single())
            assertArrayEquals(written, file.readBytes())
            assertEquals(modified, file.lastModified())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); dir.deleteRecursively() }
    }

    @Test fun keyMigrationPreservesSavedTimestampsAndFavoriteOrderingAcrossReloads() = runBlocking {
        val dir = Files.createTempDirectory("key-order-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val profiles = listOf(
                RemoteProfile("tv", "TV", category = DeviceCategory.TV, updatedAt = 1_000L),
                RemoteProfile("fan", "Fan", category = DeviceCategory.FAN, updatedAt = 3_000L),
                RemoteProfile("ac", "AC", category = DeviceCategory.AC, updatedAt = 2_000L),
                RemoteProfile("favorite", "Favorite", isFavorite = true, updatedAt = 500L)
            )
            val codec = kotlinx.serialization.json.Json { encodeDefaults = true }
            val directory = File(dir, "remotes").apply { mkdirs() }
            profiles.forEach { File(directory, "${it.id}.json").writeText(codec.encodeToString(RemoteProfile.serializer(), it)) }
            val repo = RemoteRepository(context(dir), scope)
            repo.awaitLoaded()
            repeat(2) {
                val loaded = repo.remotes.value
                assertEquals(listOf("favorite", "fan", "ac", "tv"), loaded.map { it.id })
                assertEquals(profiles.associate { it.id to it.updatedAt }, loaded.associate { it.id to it.updatedAt })
                assertTrue(loaded.all { it.keySchemaVersion == LegacyRemoteMigration.KEY_SCHEMA_VERSION })
                loaded.forEach { profile ->
                    assertEquals(profile.updatedAt, codec.decodeFromString<RemoteProfile>(File(directory, "${profile.id}.json").readText()).updatedAt)
                }
                repo.loadAllRemotes()
            }
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); dir.deleteRecursively() }
    }

}
