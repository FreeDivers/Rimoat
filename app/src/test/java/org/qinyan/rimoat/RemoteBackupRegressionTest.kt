package org.qinyan.rimoat

import android.content.ContextWrapper
import android.content.SharedPreferences
import org.qinyan.rimoat.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.lang.reflect.Proxy

class RemoteBackupRegressionTest {
    private val profile = RemoteProfile("personal", "卧室美的空调", brand = "美的", category = DeviceCategory.AC,
        room = "卧室", sourceRemoteId = "R_air_40_1", acProtocolId = 27,
        verifiedFunctions = listOf("开机", "升温"), lastUsedAt = 123,
        buttons = listOf(RemoteButton("learned", "自学按键", frequencyHz = 39500, rawPattern = listOf(9000, -4500, 560))))

    @Test fun backupKeepsLearningMetadataAndOriginalCarrier() {
        assertEquals(profile, RemoteBackupCodec.decode(RemoteBackupCodec.encode(listOf(profile))).single())
        val copy = RemoteBackupCodec.plan(listOf(profile), setOf(profile.id), ImportMode.COPIES).single()
        assertNotEquals(profile.id, copy.id)
        assertEquals(profile.buttons, copy.buttons)
        assertEquals(profile.verifiedFunctions, copy.verifiedFunctions)
        assertEquals(profile.room, copy.room)
        assertTrue(RemoteBackupCodec.plan(listOf(profile), setOf(profile.id), ImportMode.MERGE).isEmpty())
    }

    @Test fun rejectsTraversalDuplicateIdsInvalidSignalsAndUnsupportedVersions() {
        val invalid = listOf(profile.copy(id = "../outside"), profile.copy(name = ""),
            profile.copy(buttons = listOf(RemoteButton("bad", "错误", rawPattern = listOf(Int.MIN_VALUE)))))
        invalid.forEach { assertTrue(runCatching { RemoteBackupCodec.decode(RemoteBackupCodec.encode(listOf(it))) }.isFailure) }
        assertTrue(runCatching { RemoteBackupCodec.decode(RemoteBackupCodec.encode(listOf(profile, profile))) }.isFailure)
        val future = RemoteBackupCodec.encode(listOf(profile)).replace("\"version\": 2", "\"version\": 999")
        assertTrue(runCatching { RemoteBackupCodec.decode(future) }.isFailure)
        assertTrue(runCatching { RemoteBackupCodec.decode("x".repeat(RemoteBackupCodec.MAX_BYTES + 1)) }.isFailure)
    }

    private fun storage(block: suspend (RemoteRepository, File, CoroutineScope) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("rimoat-backup-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val preferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getBoolean" -> args!![1]
                else -> error("Unexpected preference mutation or access: ${method.name}")
            }
        } as SharedPreferences
        val context = object : ContextWrapper(null) {
            override fun getFilesDir() = dir
            override fun getSharedPreferences(name: String, mode: Int) = preferences
        }
        try {
            val repo = RemoteRepository(context, scope); repo.loadAllRemotes()
            block(repo, dir, scope)
        } finally { scope.coroutineContext.job.cancelAndJoin(); dir.deleteRecursively() }
    }

    @Test fun mergeCannotOverwriteExistingDataAndCopiesRemainIndependent() = storage { repo, dir, _ ->
        repo.saveRemote(profile)
        val original = File(dir, "remotes/personal.json").readText()
        assertEquals(0, repo.importRemotes(listOf(profile.copy(name = "不应覆盖")), ImportMode.MERGE))
        assertEquals(original, File(dir, "remotes/personal.json").readText())
        assertEquals(1, repo.importRemotes(listOf(profile), ImportMode.COPIES))
        assertEquals(2, repo.remotes.value.count { it.sourceRemoteId == profile.sourceRemoteId })
    }

    @Test fun deletedDataCanBeRestoredAfterRestartWithoutLosingSignals() = storage { repo, dir, scope ->
        repo.saveRemote(profile); repo.deleteRemote(profile.id)
        assertNull(repo.getRemote(profile.id))
        val context = object : ContextWrapper(null) { override fun getFilesDir() = dir }
        val restarted = RemoteRepository(context, scope); restarted.loadAllRemotes()
        assertEquals(profile.buttons, restarted.deletedRemotes().single().buttons)
        restarted.restoreDeleted(profile.id)
        assertEquals(profile.buttons, restarted.getRemote(profile.id)!!.buttons)
        assertTrue(restarted.deletedRemotes().isEmpty())
    }

    @Test fun interruptedImportRollsBackOnlyNewFilesOnRestart() = storage { repo, dir, _ ->
        repo.saveRemote(profile)
        val original = File(dir, "remotes/personal.json").readText()
        File(dir, "remotes/partial.json").writeText(original.replace("personal", "partial"))
        File(dir, "remotes/.import-pending.json").writeText("[\"partial\"]")
        repo.loadAllRemotes()
        assertFalse(File(dir, "remotes/partial.json").exists())
        assertEquals(original, File(dir, "remotes/personal.json").readText())
        assertNotNull(repo.getRemote(profile.id))
    }

    @Test fun relearningInvalidatesOnlyTheChangedFunctionsConfirmation() = storage { repo, _, _ ->
        repo.saveRemote(profile.copy(verifiedFunctions = listOf("自学按键", "开机")))
        repo.updateButton(profile.id, profile.buttons.single().copy(rawPattern = listOf(1200, 600, 600)))
        val saved = repo.getRemote(profile.id)!!
        assertEquals(listOf("开机"), saved.verifiedFunctions)
        assertTrue(saved.useStaticAcButtons)
    }

    @Test fun renameAndRecentUsePersistWithoutRewritingRemoteSignals() = storage { repo, _, _ ->
        repo.saveRemote(profile)
        repo.updateDetails(profile.id, "客厅空调", "客厅")
        repo.markUsed(profile.id)
        repo.loadAllRemotes()
        val saved = repo.getRemote(profile.id)!!
        assertEquals("客厅空调", saved.name); assertEquals("客厅", saved.room)
        assertTrue(saved.lastUsedAt > 123); assertEquals(profile.buttons, saved.buttons)
        assertEquals(profile.verifiedFunctions, saved.verifiedFunctions)
    }
    @Test fun repeatedDeletionPreservesEachVersionAndRestoresTheSelectedOne() = storage { repo, _, _ ->
        repo.saveRemote(profile)
        repo.deleteRemote(profile.id)
        repo.saveRemote(profile.copy(name = "新版本"))
        repo.deleteRemote(profile.id)
        val entries = repo.deletedEntries()
        assertEquals(2, entries.size)
        assertEquals(2, entries.map { it.entryId }.distinct().size)
        val old = entries.first { it.profile.name == profile.name }
        repo.restoreDeleted(profile.id, old.entryId)
        assertEquals(profile.name, repo.getRemote(profile.id)?.name)
        assertEquals("新版本", repo.deletedEntries().single().profile.name)
        assertTrue(runCatching { repo.restoreDeleted(profile.id) }.isFailure)
    }

    @Test fun saveAndImportRejectTheSameInvalidLabels() = storage { repo, _, _ ->
        val invalid = profile.copy(buttons = listOf(profile.buttons.single().copy(label = " ")))
        assertTrue(runCatching { repo.saveRemote(invalid) }.isFailure)
        assertTrue(runCatching { RemoteBackupCodec.decode(RemoteBackupCodec.encode(listOf(invalid))) }.isFailure)
        assertNull(repo.getRemote(profile.id))
    }

    @Test fun permanentDeletionRemovesOnlySelectedVersionAndSurvivesRestart() = storage { repo, dir, scope ->
        repo.saveRemote(profile); repo.deleteRemote(profile.id)
        repo.saveRemote(profile.copy(name = "待删除版本")); repo.deleteRemote(profile.id)
        repo.saveRemote(profile.copy(name = "正在使用的版本"))
        val original = File(dir, "remotes/personal.json").readBytes()
        val selected = repo.deletedEntries().first { it.profile.name == "待删除版本" }
        repo.permanentlyDelete(selected.entryId)
        assertArrayEquals(original, File(dir, "remotes/personal.json").readBytes())
        assertEquals(profile.name, repo.deletedEntries().single().profile.name)
        assertFalse(File(dir, "remotes/.trash/${selected.entryId}").exists())
        val restarted = RemoteRepository(object : ContextWrapper(null) { override fun getFilesDir() = dir }, scope)
        restarted.loadAllRemotes()
        assertEquals(profile.name, restarted.deletedEntries().single().profile.name)
        assertEquals("正在使用的版本", restarted.getRemote(profile.id)?.name)
    }

    @Test fun permanentDeletionRejectsUnknownPathsAndLeavesTrashUnchanged() = storage { repo, dir, _ ->
        repo.saveRemote(profile); repo.deleteRemote(profile.id)
        val original = repo.deletedEntries().single()
        val outside = File(dir, "keep.json").apply { writeText("keep") }
        for (invalid in listOf("../../keep.json", outside.absolutePath, "missing.json")) {
            assertTrue(runCatching { repo.permanentlyDelete(invalid) }.isFailure)
            assertEquals(original, repo.deletedEntries().single())
            assertEquals("keep", outside.readText())
        }
        repo.permanentlyDelete(original.entryId)
        assertTrue(repo.deletedEntries().isEmpty())
        assertTrue(runCatching { repo.permanentlyDelete(original.entryId) }.isFailure)
    }

}
