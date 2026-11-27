package org.qinyan.rimoat

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.data.db.IrDatabaseRepository
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.ui.learning.*
import org.qinyan.rimoat.ui.screens.genericDirectionButtons
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import org.qinyan.rimoat.widget.WidgetSnapshot
import org.qinyan.rimoat.widget.withRepositoryError
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StateRecoveryRegressionTest {
    @Test fun widgetErrorsApplyOnlyWhenNoUsableProfileExists() {
        val error = UiText.Resource(R.string.widget_config_read_failed)
        val empty = WidgetSnapshot(loaded = true).withRepositoryError(error)
        assertEquals(error, empty.error)
        assertNull(empty.withRepositoryError(null).error)
        val recovered = empty.copy(profiles = listOf(RemoteProfile("remote", "电视")))
            .withRepositoryError(error)
        assertNull(recovered.error)
    }

    @Test fun directionMatchingRejectsSubstringsAndVolumeKeysAndPrefersExactIds() {
        val unrelated = listOf("backup", "lookup", "book", "volume_up", "ch_down", "page_left")
            .map { RemoteButton(it, it) }
        assertTrue(genericDirectionButtons(unrelated).isEmpty())
        val buttons = unrelated + listOf(RemoteButton("remote_up", "North"), RemoteButton("up", "Preferred"),
            RemoteButton("remote-down", "South"), RemoteButton("remote:left", "West"),
            RemoteButton("remote.right", "East"), RemoteButton("remote_enter", "Confirm"))
        val result = genericDirectionButtons(buttons)
        assertEquals("up", result["上"]?.id)
        assertEquals("remote_enter", result["OK"]?.id)
        assertEquals(5, result.size)
        assertFalse(result.values.any { it in unrelated })
    }

    private fun pump(dispatcher: TestDispatcher, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            dispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(5)
        }
        assertTrue(condition())
    }

    @Test fun roomSelectionUsesCommittedTrimmedNameAndStaysUnchangedOnFailure() = runBlocking {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val app = RuntimeEnvironment.getApplication()
        val dir = Files.createTempDirectory("room-selection-").toFile()
        val context = object : ContextWrapper(app) { override fun getFilesDir() = dir }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        val transmitter = IrTransmitterManager(context, scope)
        try {
            val repo = RemoteRepository(context, scope)
            repo.awaitLoaded()
            repo.saveRemote(RemoteProfile("remote", "电视", room = "客厅"))
            val vm = RemoteListViewModel(repo, transmitter, ThemePreferences(app)).also { store.put("rooms", it) }
            vm.selectRoom("客厅")
            var saved = false
            vm.editRoom("客厅", "  卧室  ") { saved = true }
            pump(dispatcher) { saved }
            assertEquals("卧室", vm.selectedRoom.value)
            assertEquals("卧室", repo.remotes.value.single().room)
            val errors = scope.launch { vm.errorMessage.collect {} }
            vm.editRoom("卧室", "   ") { fail("Invalid room must not commit") }
            pump(dispatcher) { vm.errorMessage.value != null }
            errors.cancel()
            assertEquals("卧室", vm.selectedRoom.value)
            assertEquals("卧室", repo.remotes.value.single().room)
        } finally {
            clearViewModelsAndAwait(store, dispatcher)
            transmitter.release(); scope.cancel(); Dispatchers.resetMain(); dir.deleteRecursively()
        }
    }

    @Test fun dismissedLearningDiscardsSaveErrorsIncludingLateFailures() = runBlocking {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val app = RuntimeEnvironment.getApplication()
        val dir = Files.createTempDirectory("learning-error-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        try {
            val repo = RemoteRepository(object : ContextWrapper(app) { override fun getFilesDir() = dir }, scope)
            repo.awaitLoaded()
            val button = RemoteButton("power", "电源")
            val vm = RemoteControlViewModel("missing", repository = repo).also { store.put("remote", it) }
            val signal = IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560))
            vm.startLearningButton(button)
            vm.saveLearnedButton(button, signal)
            pump(dispatcher) { !vm.isSavingButton.value }
            assertNotNull(vm.learningError.value)
            vm.dismissLearningDialog()
            assertNull(vm.learningError.value)
            assertNull(vm.operationError.value)
            vm.startLearningButton(button)
            vm.saveLearnedButton(button, signal)
            vm.dismissLearningDialog()
            pump(dispatcher) { !vm.isSavingButton.value }
            assertNull(vm.learningError.value)
            assertNull(vm.operationError.value)
        } finally { clearViewModelsAndAwait(store, dispatcher); scope.cancel(); Dispatchers.resetMain(); dir.deleteRecursively() }
    }

    @Test fun immediateMockSelectionLearnsBeforeDerivedAvailabilityUpdates() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)
        val manager = IrLearnerManager(RuntimeEnvironment.getApplication(), managerScope)
        manager.mockLearner.setSimulatedDelayMs(0)
        try {
            manager.setForceMockMode(true)
            val vm = CustomRemoteWizardViewModel(learnerManager = manager).also { store.put("wizard", it) }
            vm.setRemoteName("电视")
            vm.proceedToLearning()
            vm.startLearningCurrentKey()
            pump(dispatcher) { vm.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED }
            assertNull(vm.uiState.value.errorMessage)
        } finally {
            manager.cancel(); clearViewModelsAndAwait(store, dispatcher)
            manager.unregisterReceiver(); managerScope.cancel(); Dispatchers.resetMain()
        }
    }

    @Test fun catalogSearchTreatsPercentUnderscoreAndBackslashAsLiteralText() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val file = app.getDatabasePath("catalog_v1.db")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE remotes (remote_id TEXT, category_id INTEGER, brand_idx INTEGER, brand_cn TEXT, brand_en TEXT)")
            db.execSQL("CREATE TABLE brands (brand_idx INTEGER, pinyin TEXT)")
            listOf("100%", "A_B", "A\\B", "ordinary").forEachIndexed { index, text ->
                db.execSQL("INSERT INTO remotes VALUES (?, 1, ?, ?, ?)", arrayOf("r$index", index, text, text))
                db.execSQL("INSERT INTO brands VALUES (?, ?)", arrayOf(index, text))
            }
        }
        app.getSharedPreferences("ir_db_meta", Context.MODE_PRIVATE).edit()
            .putInt("installed_db_version", CatalogUpgrade.VERSION).commit()
        val repo = IrDatabaseRepository(app)
        assertEquals(listOf("100%"), repo.getBrandsForCategory(1, "%").map { it.nameEn })
        assertEquals(listOf("A_B"), repo.getBrandsForCategory(1, "_").map { it.nameEn })
        assertEquals(listOf("A\\B"), repo.getBrandsForCategory(1, "\\").map { it.nameEn })
        assertEquals(4, repo.getBrandsForCategory(1, "").size)
    }

    @Test fun consecutiveDeletionsAccumulateDeletedIdsAndUndoRestoresAll() = runBlocking {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val app = RuntimeEnvironment.getApplication()
        val dir = Files.createTempDirectory("batch-undo-").toFile()
        val context = object : ContextWrapper(app) { override fun getFilesDir() = dir }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        val transmitter = IrTransmitterManager(context, scope)
        try {
            val repo = RemoteRepository(context, scope)
            repo.awaitLoaded()
            repo.saveRemote(RemoteProfile("first", "客厅电视"))
            repo.saveRemote(RemoteProfile("second", "卧室空调"))
            repo.saveRemote(RemoteProfile("third", "书房风扇"))
            val vm = RemoteListViewModel(repo, transmitter, ThemePreferences(app)).also { store.put("home", it) }
            val d1 = vm.deleteRemote("first")
            val d2 = vm.deleteRemote("second")
            pump(dispatcher) { d1.isCompleted && d2.isCompleted }
            assertTrue(d1.await())
            assertTrue(d2.await())
            assertEquals(listOf("first", "second"), vm.deletedIds.value)
            assertEquals(listOf("third"), repo.remotes.value.map { it.id })

            // Stale snapshot consumption must not drop newer deletions.
            vm.consumeDeleted(listOf("first"))
            assertEquals(listOf("first", "second"), vm.deletedIds.value)

            vm.restoreDeleted(vm.deletedIds.value)
            pump(dispatcher) { !vm.trashBusy.value && vm.deletedIds.value.isEmpty() }
            assertEquals(setOf("first", "second", "third"), repo.remotes.value.map { it.id }.toSet())
            assertTrue(repo.deletedEntries().isEmpty())
        } finally {
            clearViewModelsAndAwait(store, dispatcher)
            transmitter.release(); scope.cancel(); Dispatchers.resetMain(); dir.deleteRecursively()
        }
    }

    @Test fun failedDeleteReturnsFalseAndLeavesDeletedQueueUnchanged() = runBlocking {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val app = RuntimeEnvironment.getApplication()
        val dir = Files.createTempDirectory("failed-delete-").toFile()
        val context = object : ContextWrapper(app) { override fun getFilesDir() = dir }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        val transmitter = IrTransmitterManager(context, scope)
        try {
            val repo = RemoteRepository(context, scope)
            repo.awaitLoaded()
            repo.saveRemote(RemoteProfile("valid_remote", "客厅电视"))
            val vm = RemoteListViewModel(repo, transmitter, ThemePreferences(app)).also { store.put("home", it) }
            val errors = scope.launch { vm.errorMessage.collect {} }
            val invalidResult = vm.deleteRemote("bad/id")
            pump(dispatcher) { invalidResult.isCompleted && vm.errorMessage.value != null }
            assertFalse(invalidResult.await())
            assertTrue(vm.deletedIds.value.isEmpty())
            assertEquals(listOf("valid_remote"), repo.remotes.value.map { it.id })
            errors.cancel()
        } finally {
            clearViewModelsAndAwait(store, dispatcher)
            transmitter.release(); scope.cancel(); Dispatchers.resetMain(); dir.deleteRecursively()
        }
    }
}
