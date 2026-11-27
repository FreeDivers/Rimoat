package org.qinyan.rimoat

import org.qinyan.rimoat.testing.AppStringsResourceRule
import android.content.ContextWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.widget.WidgetConfiguration
import org.qinyan.rimoat.widget.WidgetKind
import org.qinyan.rimoat.widget.WidgetShortcut
import org.qinyan.rimoat.widget.WidgetStore
import java.io.File

class DirectionIdMigrationTest {
    @get:org.junit.Rule val strings = AppStringsResourceRule()

    @get:Rule val temp = TemporaryFolder()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    @Test
    fun legacyDirectionIdsInButtonsOrderAndHiddenMigrateToAsciiKeepingSignalsAndLabels() {
        val pattern = listOf(9000, 4500, 560, 1690)
        val legacy = RemoteProfile(
            id = "tv_legacy",
            name = "Legacy TV",
            category = DeviceCategory.TV,
            keySchemaVersion = 0,
            buttons = listOf(
                RemoteButton("direction_上", "上", builtinLabel = "上", isLearned = true, rawPattern = pattern),
                RemoteButton("direction_下", "下", builtinLabel = "下", isLearned = true, rawPattern = pattern),
                RemoteButton("direction_左", "左", builtinLabel = "左", isLearned = true, rawPattern = pattern),
                RemoteButton("direction_右", "右", builtinLabel = "右", isLearned = true, rawPattern = pattern),
                RemoteButton("direction_OK", "OK", builtinLabel = "OK", isLearned = true, rawPattern = pattern),
                RemoteButton("custom_1", "Custom")
            ),
            panelLayout = RemotePanelLayout(
                order = listOf("key:direction_上", "key:custom_1", "key:direction_OK"),
                hidden = setOf("key:direction_下", "key:direction_左")
            )
        )

        val migrated = LegacyRemoteMigration.normalizeKeys(legacy)
        assertEquals(2, migrated.keySchemaVersion)
        assertEquals(
            listOf("direction_up", "direction_down", "direction_left", "direction_right", "direction_ok", "custom_1"),
            migrated.buttons.map { it.id }
        )
        assertEquals(
            listOf(ButtonKey.UP, ButtonKey.DOWN, ButtonKey.LEFT, ButtonKey.RIGHT, ButtonKey.OK, null),
            migrated.buttons.map { it.key }
        )
        assertEquals(listOf("上", "下", "左", "右", "OK", "Custom"), migrated.buttons.map { it.label })
        assertTrue(migrated.buttons.take(5).all { it.rawPattern == pattern })
        assertEquals(listOf("key:direction_up", "key:custom_1", "key:direction_ok"), migrated.panelLayout.order)
        assertEquals(setOf("key:direction_down", "key:direction_left"), migrated.panelLayout.hidden)
    }

    @Test
    fun versionOneProfileMigratesToVersionTwoAndIsIdempotent() {
        val v1 = RemoteProfile(
            id = "v1_tv",
            name = "TV",
            category = DeviceCategory.TV,
            keySchemaVersion = 1,
            buttons = listOf(
                RemoteButton("direction_上", "Up", builtinLabel = "上", key = ButtonKey.UP, isLearned = true,
                    rawPattern = listOf(9000, 4500, 560)),
                RemoteButton("direction_下", "Down", isLearned = true, rawPattern = listOf(9000, 4500, 560))
            ),
            panelLayout = RemotePanelLayout(
                order = listOf("key:direction_上"),
                hidden = setOf("key:direction_下")
            )
        )

        val v2 = LegacyRemoteMigration.normalizeKeys(v1)
        assertEquals(2, v2.keySchemaVersion)
        assertEquals(listOf("direction_up", "direction_down"), v2.buttons.map { it.id })
        assertEquals(listOf(ButtonKey.UP, ButtonKey.DOWN), v2.buttons.map { it.key })
        assertEquals(listOf("key:direction_up"), v2.panelLayout.order)
        assertEquals(setOf("key:direction_down"), v2.panelLayout.hidden)
        assertEquals(v2, LegacyRemoteMigration.normalizeKeys(v2))
    }

    @Test
    fun collidingLegacyAndNewDirectionIdsKeepSignalAndMergeLayoutReferences() {
        val learnedPattern = listOf(9000, 4500, 560)
        val newerPattern = listOf(8000, 4000, 560)
        val profile = RemoteProfile(
            id = "conflict_tv",
            name = "Conflict TV",
            category = DeviceCategory.TV,
            keySchemaVersion = 1,
            buttons = listOf(
                RemoteButton("direction_上", "上", isLearned = false, rawPattern = emptyList()),
                RemoteButton("direction_up", "Up", isLearned = true, rawPattern = learnedPattern, key = ButtonKey.UP),
                RemoteButton("direction_下", "下", isLearned = true, rawPattern = learnedPattern, key = ButtonKey.DOWN),
                RemoteButton("direction_down", "Down", isLearned = false, rawPattern = learnedPattern, key = ButtonKey.DOWN),
                RemoteButton("direction_左", "Left old", isLearned = true, rawPattern = learnedPattern, key = ButtonKey.LEFT),
                RemoteButton("direction_left", "Left new", isLearned = true, rawPattern = newerPattern, key = ButtonKey.LEFT)
            ),
            panelLayout = RemotePanelLayout(
                order = listOf("key:direction_上", "key:direction_down", "key:direction_up", "key:direction_left"),
                hidden = setOf("key:direction_上", "key:direction_left")
            )
        )

        val migrated = LegacyRemoteMigration.normalizeKeys(profile)
        assertEquals(listOf("direction_up", "direction_down", "direction_left"), migrated.buttons.map { it.id })
        assertEquals(learnedPattern, migrated.buttons[0].rawPattern)
        assertTrue(migrated.buttons[1].isLearned)
        assertEquals("下", migrated.buttons[1].label)
        assertEquals(newerPattern, migrated.buttons[2].rawPattern)
        assertEquals(listOf("key:direction_up", "key:direction_down", "key:direction_left"), migrated.panelLayout.order)
        assertEquals(setOf("key:direction_up", "key:direction_left"), migrated.panelLayout.hidden)
    }

    @Test
    fun repositoryAndBackupImportMigrateLegacyDirectionIdsAndWriteBackOnce() = runBlocking {
        val dir = temp.newFolder()
        val remotesDir = File(dir, "remotes").apply { mkdirs() }
        val legacy = RemoteProfile(
            id = "repo_tv",
            name = "TV",
            category = DeviceCategory.TV,
            keySchemaVersion = 1,
            updatedAt = 123L,
            buttons = listOf(RemoteButton("direction_上", "上", builtinLabel = "上", key = ButtonKey.UP,
                isLearned = true, rawPattern = listOf(9000, 4500, 560))),
            panelLayout = RemotePanelLayout(order = listOf("key:direction_上"), hidden = setOf("key:direction_下"))
        )
        val file = File(remotesDir, "repo_tv.json").apply { writeText(json.encodeToString(legacy)) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = RemoteRepository(object : ContextWrapper(null) { override fun getFilesDir() = dir }, scope)
            val loaded = repo.loadAllRemotes().single()
            assertEquals("direction_up", loaded.buttons.single().id)
            assertEquals(listOf("key:direction_up"), loaded.panelLayout.order)
            assertEquals(setOf("key:direction_下".let(LegacyDirectionIds::migratePanelKey)), loaded.panelLayout.hidden)
            val bytesAfterFirstLoad = file.readBytes()
            repo.loadAllRemotes()
            assertArrayEquals(bytesAfterFirstLoad, file.readBytes())

            val backupText = RemoteBackupCodec.encode(RemoteBackup(version = 2, remotes = listOf(legacy)))
            val decoded = RemoteBackupCodec.decodeBackup(backupText)
            assertEquals(2, decoded.version)
            assertEquals("direction_up", decoded.remotes.single().buttons.single().id)
            assertEquals(setOf("key:direction_down"), decoded.remotes.single().panelLayout.hidden)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }

    @Test
    fun widgetStoreMigratesLegacyDirectionShortcutsToMatchMigratedButtonId() = runBlocking {
        val dir = temp.newFolder()
        val widgetFile = File(dir, "widgets.json")
        val legacyConfig = WidgetConfiguration(
            kind = WidgetKind.PANEL,
            shortcuts = listOf(WidgetShortcut("repo_tv", "direction_上"), WidgetShortcut("repo_tv", "direction_OK"))
        )
        widgetFile.writeText("""{"configurations":{"1":${json.encodeToString(legacyConfig)}},"completedRestores":[]}""")

        val store = WidgetStore(widgetFile)
        store.load()
        val migratedConfig = store.configurations.value.getValue(1)
        assertEquals(listOf("direction_up", "direction_ok"), migratedConfig.shortcuts.map { it.buttonId })

        val persistedText = widgetFile.readText()
        assertTrue("direction_up" in persistedText)
        assertTrue("direction_上" !in persistedText)

        val reloaded = WidgetStore(widgetFile)
        reloaded.load()
        assertEquals(migratedConfig, reloaded.configurations.value.getValue(1))
    }
}
