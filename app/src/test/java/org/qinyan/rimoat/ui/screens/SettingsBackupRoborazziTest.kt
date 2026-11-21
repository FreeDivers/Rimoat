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
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.BackupFileStore
import org.qinyan.rimoat.data.DeviceCategory
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
