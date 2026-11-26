package org.qinyan.rimoat.ui.screens

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.app.ActivityOptionsCompat
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.BackupFileStore
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.FlipperIrImporter
import org.qinyan.rimoat.data.ImportDocument
import org.qinyan.rimoat.data.PortablePreferences
import org.qinyan.rimoat.data.RemoteBackup
import org.qinyan.rimoat.data.RemoteBackupCodec
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.RemoteRepository
import org.qinyan.rimoat.data.ThemePreferences
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.io.IOException
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    qualifiers = "zh-rCN-w411dp-h891dp-mdpi",
    application = Application::class
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class SettingsBackupRoborazziTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val screenshotOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(
            imageIoFormat = LosslessWebPImageIoFormat()
        )
    )

    @Test
    fun backupAndInfraredImportsSucceedAfterImportingALargeRawLibrary() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val jsonFile = File(app.cacheDir, "small-backup.json")
        val irFile = File(app.cacheDir, "风扇遥控.ir")
        try {
            val repository = RemoteRepository(app, scope)
            val libraryText = buildString {
                appendLine("Filetype: IR signals file")
                appendLine("Version: 1")
                val pulses = List(1024) { "560" }.joinToString(" ")
                repeat(384) { index ->
                    appendLine("name: Key $index")
                    appendLine("type: raw")
                    appendLine("frequency: 38000")
                    appendLine("duty_cycle: 0.33")
                    appendLine("data: $pulses")
                }
            }
            val library = FlipperIrImporter.decode(libraryText, "Raw library.ir").backup
            assertTrue(libraryText.toByteArray().size < RemoteBackupCodec.MAX_BYTES)
            assertTrue(RemoteBackupCodec.encode(library).toByteArray().size > RemoteBackupCodec.MAX_BYTES)
            runBlocking {
                repository.awaitLoaded()
                assertEquals(library.remotes.size, repository.importBackup(library, emptyMap()))
            }
            val incoming = RemoteProfile(id = "small_backup", name = "客厅电视", category = DeviceCategory.TV)
            jsonFile.writeText(RemoteBackupCodec.encode(listOf(incoming)))
            irFile.writeText("""
                Filetype: IR signals file
                Version: 1
                name: Power
                type: raw
                frequency: 38000
                duty_cycle: 0.33
                data: 9000 4500 560
            """.trimIndent())
            val viewModel = RemoteListViewModel(
                repository = repository,
                transmitterManager = IrTransmitterManager(app, scope),
                themePreferences = ThemePreferences(app),
                backupFiles = BackupFileStore(app)
            )
            val picker = TestDocumentPicker()
            showSettings(viewModel, picker)

            for ((kind, file) in listOf("backup" to jsonFile, "infrared" to irFile)) {
                val before = repository.remotes.value
                val inputUri = Uri.Builder().scheme("content").authority("org.qinyan.rimoat.test.backups")
                    .appendPath(file.name).build()
                Shadows.shadowOf(app.contentResolver).registerInputStreamSupplier(inputUri) { file.inputStream() }
                val action = app.getString(if (file == irFile) R.string.migration_ir_import else R.string.settings_import_backup)
                composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(action))
                composeRule.onNodeWithText(action).performClick()
                if (file == irFile) {
                    composeRule.onNodeWithText(app.getString(R.string.migration_choose_file)).performClick()
                }
                assertTrue(picker.launchedContract is ActivityResultContracts.OpenDocument)
                composeRule.runOnIdle { picker.select(inputUri) }
                composeRule.waitUntil(timeoutMillis = 30_000) {
                    Shadows.shadowOf(Looper.getMainLooper()).idle()
                    !viewModel.transferBusy.value && viewModel.pendingImport.value != null
                }
                assertEquals(if (file == irFile) "风扇遥控" else incoming.name,
                    viewModel.pendingImport.value!!.backup.remotes.single().name)
                composeRule.onNodeWithText(app.getString(R.string.migration_import_title)).assertIsDisplayed()
                composeRule.onNode(isDialog()).captureRoboImage(
                    "src/test/screenshots/large_library_${kind}_before.webp", roborazziOptions = screenshotOptions
                )
                composeRule.onNodeWithText(app.getString(R.string.migration_import_confirm)).performClick()
                val success = app.resources.getQuantityString(R.plurals.msg_import_finished, 1, 1)
                composeRule.waitUntil(timeoutMillis = 30_000) {
                    Shadows.shadowOf(Looper.getMainLooper()).idle()
                    !viewModel.transferBusy.value &&
                        composeRule.onAllNodesWithText(success).fetchSemanticsNodes().isNotEmpty()
                }
                assertNull(viewModel.errorMessage.value)
                assertNull(viewModel.pendingImport.value)
                assertEquals(before.size + 1, repository.remotes.value.size)
                assertTrue(repository.remotes.value.containsAll(before))
                assertEquals(before, runBlocking { repository.readRecoverySnapshot() }.remotes)
                composeRule.onNodeWithText(success).assertIsDisplayed()
                composeRule.onRoot().captureRoboImage(
                    "src/test/screenshots/large_library_${kind}_after.webp", roborazziOptions = screenshotOptions
                )
            }
        } finally {
            jsonFile.delete()
            irFile.delete()
            runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

    @Test
    fun backupAllRemotesLaunchesDocumentPickerDirectlyWithoutConfirmationDialog() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = RemoteRepository(app, scope)
        val sampleRemote = RemoteProfile(
            id = "backup_test_remote",
            name = "客厅电视",
            category = DeviceCategory.TV,
            buttons = listOf(RemoteButton("power", "电源", rawPattern = listOf(9000, 4500, 560)))
        )
        runBlocking {
            repository.awaitLoaded()
            repository.saveRemote(sampleRemote)
        }
        val transmitterManager = IrTransmitterManager(app, scope)
        val themePreferences = ThemePreferences(app)
        val viewModel = RemoteListViewModel(
            repository = repository,
            transmitterManager = transmitterManager,
            themePreferences = themePreferences,
            backupFiles = BackupFileStore(app)
        )

        val picker = TestDocumentPicker()
        val outputFile = File(app.cacheDir, "settings-backup-test.json").apply { delete() }
        try {
            showSettings(viewModel, picker)

            val backupAllLabel = app.getString(R.string.settings_backup_all)
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(backupAllLabel))
            composeRule.onNodeWithText(backupAllLabel).assertIsDisplayed()
            composeRule.onRoot().captureRoboImage(
                filePath = "src/test/screenshots/settings_backup_before.webp",
                roborazziOptions = screenshotOptions
            )

            composeRule.onNodeWithText(backupAllLabel).performClick()
            composeRule.waitForIdle()

            assertTrue(picker.launchedContract is ActivityResultContracts.CreateDocument)
            assertEquals("Rimoat-backup.json", picker.launchedInput)
            composeRule.onNodeWithText(app.getString(R.string.migration_export_options)).assertDoesNotExist()
            composeRule.onRoot().captureRoboImage(
                filePath = "src/test/screenshots/settings_backup_after.webp",
                roborazziOptions = screenshotOptions
            )

            composeRule.runOnIdle { picker.select(Uri.fromFile(outputFile)) }
            composeRule.waitUntil(timeoutMillis = 10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                !viewModel.transferBusy.value && (outputFile.length() > 0 || viewModel.errorMessage.value != null)
            }
            assertNull("Unexpected export error: ${viewModel.errorMessage.value}", viewModel.errorMessage.value)
            composeRule.onNodeWithText(app.getString(R.string.screen_backup_saved)).assertIsDisplayed()

            val decoded = RemoteBackupCodec.decodeBackup(outputFile.readText(Charsets.UTF_8))
            assertEquals(listOf(sampleRemote.id), decoded.remotes.map { it.id })
        } finally {
            outputFile.delete()
            runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

    @Test
    fun interruptedOverwriteWarnsThatTheSelectedBackupMayBeDamaged() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val outputFile = File.createTempFile("settings-backup-overwrite", ".json", app.cacheDir)
        try {
            val repository = RemoteRepository(app, scope)
            val previousBackup = RemoteBackupCodec.encode(RemoteBackup(remotes = emptyList()))
            outputFile.writeText(previousBackup)
            val bytesBeforeFailure = runBlocking {
                repository.awaitLoaded()
                repository.saveRemote(
                    RemoteProfile(id = "new_remote", name = "客厅电视", category = DeviceCategory.TV)
                )
                repository.exportRemotes().toByteArray().size / 2
            }
            val outputUri = Uri.parse("content://org.qinyan.rimoat.test.backups/overwrite.json")
            Shadows.shadowOf(app.contentResolver).registerOutputStreamSupplier(outputUri) {
                val stream = outputFile.outputStream()
                object : OutputStream() {
                    private var remaining = bytesBeforeFailure

                    override fun write(value: Int) {
                        if (remaining == 0) throw IOException("Write interrupted")
                        stream.write(value)
                        remaining--
                    }

                    override fun close() = stream.close()
                }
            }
            val viewModel = RemoteListViewModel(
                repository = repository,
                transmitterManager = IrTransmitterManager(app, scope),
                themePreferences = ThemePreferences(app),
                backupFiles = BackupFileStore(app)
            )
            val picker = TestDocumentPicker()
            showSettings(viewModel, picker)

            val backupAllLabel = app.getString(R.string.settings_backup_all)
            val warningId = R.string.backup_export_write_failed
            val warning = app.getString(warningId)
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(backupAllLabel))
            composeRule.onNodeWithText(warning).assertDoesNotExist()
            composeRule.onRoot().captureRoboImage(
                filePath = "src/test/screenshots/settings_backup_overwrite_before.webp",
                roborazziOptions = screenshotOptions
            )

            composeRule.onNodeWithText(backupAllLabel).performClick()
            composeRule.runOnIdle { picker.select(outputUri) }
            composeRule.waitUntil(timeoutMillis = 10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithText(warning).fetchSemanticsNodes().isNotEmpty()
            }

            assertEquals(UiText.resource(warningId), viewModel.errorMessage.value)
            assertEquals(bytesBeforeFailure.toLong(), outputFile.length())
            assertTrue(outputFile.readText().startsWith("{"))
            composeRule.onNodeWithText(app.getString(R.string.screen_backup_saved)).assertDoesNotExist()
            composeRule.onNodeWithText(warning).assertIsDisplayed()
            composeRule.onRoot().captureRoboImage(
                filePath = "src/test/screenshots/settings_backup_overwrite_failed.webp",
                roborazziOptions = screenshotOptions
            )
        } finally {
            outputFile.delete()
            runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

    @Test
    @Config(sdk = [30])
    fun importDialogHidesDynamicColorsWhenUnsupportedAndKeepsDefaultTheme() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val backupFile = File(app.cacheDir, "dynamic-prefs-backup.json")
        try {
            val repository = RemoteRepository(app, scope)
            runBlocking { repository.awaitLoaded() }
            val incoming = RemoteProfile(id = "pref_test_remote", name = "卧室空调", category = DeviceCategory.AC)
            val backup = RemoteBackup(
                remotes = listOf(incoming),
                preferences = PortablePreferences(dynamicColors = true, pureBlack = true)
            )
            backupFile.writeText(RemoteBackupCodec.encode(backup))

            val themePreferences = ThemePreferences(app)
            assertFalse(themePreferences.isDynamicColorSupported)
            assertFalse(themePreferences.useDynamicColor.value)

            val viewModel = RemoteListViewModel(
                repository = repository,
                transmitterManager = IrTransmitterManager(app, scope),
                themePreferences = themePreferences,
                backupFiles = BackupFileStore(app)
            )
            val picker = TestDocumentPicker()
            showSettings(viewModel, picker)

            val inputUri = Uri.Builder().scheme("content").authority("org.qinyan.rimoat.test.backups")
                .appendPath(backupFile.name).build()
            Shadows.shadowOf(app.contentResolver).registerInputStreamSupplier(inputUri) { backupFile.inputStream() }

            val importAction = app.getString(R.string.settings_import_backup)
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(importAction))
            composeRule.onNodeWithText(importAction).performClick()
            composeRule.runOnIdle { picker.select(inputUri) }
            composeRule.waitUntil(timeoutMillis = 15_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                !viewModel.transferBusy.value && viewModel.pendingImport.value != null
            }

            composeRule.onNodeWithText(app.getString(R.string.migration_import_title)).assertIsDisplayed()
            composeRule.onNodeWithText(app.getString(R.string.migration_dynamic_colors)).assertDoesNotExist()
            composeRule.onNodeWithText(app.getString(R.string.migration_pure_black)).assertIsDisplayed()
            composeRule.onNode(isDialog()).captureRoboImage(
                filePath = "src/test/screenshots/import_dialog_no_dynamic_color.webp",
                roborazziOptions = screenshotOptions
            )

            composeRule.onNodeWithText(app.getString(R.string.migration_pure_black)).performClick()
            composeRule.onNodeWithText(app.getString(R.string.migration_import_confirm)).performClick()
            val success = app.resources.getQuantityString(R.plurals.msg_import_finished, 1, 1)
            composeRule.waitUntil(timeoutMillis = 15_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                !viewModel.transferBusy.value &&
                    composeRule.onAllNodesWithText(success).fetchSemanticsNodes().isNotEmpty()
            }

            assertFalse(themePreferences.useDynamicColor.value)
            assertTrue(themePreferences.pureBlackBackground.value)
            assertEquals(listOf(incoming.id), repository.remotes.value.map { it.id })
        } finally {
            backupFile.delete()
            runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

    @Test
    @Config(sdk = [30])
    fun importDialogHidesPreferencesSectionWhenOnlyUnsupportedDynamicColorsPresent() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val document = ImportDocument(
            RemoteBackup(
                remotes = emptyList(),
                preferences = PortablePreferences(dynamicColors = true, pureBlack = null)
            )
        )
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                MigrationImportDialog(
                    document = document,
                    existingIds = emptySet(),
                    busy = false,
                    onDismiss = {},
                    onImport = { _, _ -> }
                )
            }
        }
        composeRule.onNodeWithText(app.getString(R.string.migration_preferences_title)).assertDoesNotExist()
        composeRule.onNodeWithText(app.getString(R.string.migration_dynamic_colors)).assertDoesNotExist()
        composeRule.onNodeWithText(app.getString(R.string.migration_import_confirm)).assertIsNotEnabled()
    }

    @Test
    fun importDialogShowsDynamicColorsWhenSupportedOnAndroid12Plus() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        var importedPreferences: PortablePreferences? = null
        val document = ImportDocument(
            RemoteBackup(
                remotes = emptyList(),
                preferences = PortablePreferences(dynamicColors = true, pureBlack = false)
            )
        )
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                MigrationImportDialog(
                    document = document,
                    existingIds = emptySet(),
                    busy = false,
                    onDismiss = {},
                    onImport = { _, prefs -> importedPreferences = prefs }
                )
            }
        }
        composeRule.onNodeWithText(app.getString(R.string.migration_preferences_title)).assertIsDisplayed()
        composeRule.onNodeWithText(app.getString(R.string.migration_dynamic_colors)).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(app.getString(R.string.migration_import_confirm)).performClick()
        composeRule.runOnIdle {
            assertEquals(PortablePreferences(dynamicColors = true, pureBlack = null), importedPreferences)
        }
    }

    private fun showSettings(viewModel: RemoteListViewModel, picker: TestDocumentPicker) {
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                    SettingsScreen(viewModel = viewModel, onBack = {})
                }
            }
        }
    }

    private class TestDocumentPicker : ActivityResultRegistryOwner {
        var launchedContract: ActivityResultContract<*, *>? = null
            private set
        var launchedInput: Any? = null
            private set
        private var pendingRequestCode: Int? = null

        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) {
                launchedContract = contract
                launchedInput = input
                pendingRequestCode = requestCode
            }
        }

        fun select(uri: Uri) {
            assertTrue(
                "ActivityResultRegistry failed to dispatch result",
                activityResultRegistry.dispatchResult(checkNotNull(pendingRequestCode), uri)
            )
        }
    }
}
