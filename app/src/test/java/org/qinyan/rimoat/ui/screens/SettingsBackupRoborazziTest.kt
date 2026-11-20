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
import org.qinyan.rimoat.data.RemoteBackupCodec
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.RemoteRepository
import org.qinyan.rimoat.data.ThemePreferences
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

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

        var launchedContract: ActivityResultContract<*, *>? = null
        var launchedInput: Any? = null
        var pendingRequestCode: Int? = null
        val registry = object : ActivityResultRegistry() {
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
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }

        val screenshotOptions = RoborazziOptions(
            recordOptions = RoborazziOptions.RecordOptions(
                imageIoFormat = LosslessWebPImageIoFormat()
            )
        )

        val outputFile = File(app.cacheDir, "settings-backup-test.json").apply { delete() }
        try {
            composeRule.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                        SettingsScreen(viewModel = viewModel, onBack = {})
                    }
                }
            }

            val backupAllLabel = app.getString(R.string.settings_backup_all)
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(backupAllLabel))
            composeRule.onNodeWithText(backupAllLabel).assertIsDisplayed()
            composeRule.onRoot().captureRoboImage(
                filePath = "src/test/screenshots/settings_backup_before.webp",
                roborazziOptions = screenshotOptions
            )

            composeRule.onNodeWithText(backupAllLabel).performClick()
            composeRule.waitForIdle()

            assertTrue(launchedContract is ActivityResultContracts.CreateDocument)
            assertEquals("Rimoat-backup.json", launchedInput)
            composeRule.onNodeWithText(app.getString(R.string.migration_export_options)).assertDoesNotExist()
            composeRule.onRoot().captureRoboImage(
                filePath = "src/test/screenshots/settings_backup_after.webp",
                roborazziOptions = screenshotOptions
            )

            val outputUri = Uri.fromFile(outputFile)
            var dispatched = false
            composeRule.runOnIdle {
                dispatched = registry.dispatchResult(checkNotNull(pendingRequestCode), outputUri)
            }
            assertTrue("ActivityResultRegistry failed to dispatch result", dispatched)
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
}
