package org.qinyan.rimoat.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.hardware.TransmitFailureReason
import org.qinyan.rimoat.hardware.TransmitLog
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.i18n.AppError
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.ErrorAction
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.ui.components.AppSnackbarHost
import org.qinyan.rimoat.ui.components.TransmitFeedbackBanner
import org.qinyan.rimoat.ui.components.showAppError
import org.qinyan.rimoat.ui.screens.settings.BackupSavedDialog
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class ErrorActionUiRoborazziTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app get() = RuntimeEnvironment.getApplication()
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    @Test
    fun appSnackbarHostShowsLearnActionAndInvokesCallback() {
        AppStrings.initialize(app)
        var invokedAction: ErrorAction? = null
        val error = AppError.Resource(
            UiText.Resource(R.string.msg_button_learn_first),
            ErrorAction.Learn("power")
        )
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                ProvideExpressiveCanvas {
                    val snackbar = remember { SnackbarHostState() }
                    LaunchedEffect(Unit) {
                        snackbar.showAppError(app, error)
                    }
                    Scaffold(
                        containerColor = ExpressiveSurface.canvas,
                        snackbarHost = {
                            AppSnackbarHost(snackbar, onErrorAction = { invokedAction = it })
                        }
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding))
                    }
                }
            }
        }
        composeRule.onNodeWithText(app.getString(R.string.msg_button_learn_first)).assertIsDisplayed()
        composeRule.onNodeWithText(app.getString(R.string.error_action_learn)).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(
            "src/test/screenshots/error_snackbar_learn.webp",
            roborazziOptions = options
        )
        composeRule.onNodeWithText(app.getString(R.string.error_action_learn)).performClick()
        composeRule.runOnIdle {
            assertEquals(ErrorAction.Learn("power"), invokedAction)
        }
    }

    @Test
    fun appSnackbarHostShowsRetryActionOnExportFailure() {
        AppStrings.initialize(app)
        var retried = false
        val error = AppError.Resource(
            UiText.Resource(R.string.backup_export_write_failed),
            ErrorAction.Retry
        )
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                ProvideExpressiveCanvas {
                    val snackbar = remember { SnackbarHostState() }
                    LaunchedEffect(Unit) {
                        snackbar.showAppError(app, error)
                    }
                    Scaffold(
                        containerColor = ExpressiveSurface.canvas,
                        snackbarHost = {
                            AppSnackbarHost(snackbar, onErrorAction = { if (it == ErrorAction.Retry) retried = true })
                        }
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding))
                    }
                }
            }
        }
        composeRule.onNodeWithText(app.getString(R.string.backup_export_write_failed)).assertIsDisplayed()
        composeRule.onNodeWithText(app.getString(R.string.screen_retry)).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(
            "src/test/screenshots/error_snackbar_retry.webp",
            roborazziOptions = options
        )
        composeRule.onNodeWithText(app.getString(R.string.screen_retry)).performClick()
        composeRule.runOnIdle { assertEquals(true, retried) }
    }

    @Test
    fun backupSavedDialogDisplaysBackupFileNameAndActions() {
        AppStrings.initialize(app)
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                BackupSavedDialog(
                    savedUri = "file:///storage/emulated/0/Documents/rimoat_test_backup.json",
                    onDismiss = {},
                    onError = {}
                )
            }
        }
        composeRule.onNodeWithText(app.getString(R.string.screen_backup_saved)).assertIsDisplayed()
        composeRule.onNodeWithText(
            app.getString(R.string.screen_backup_saved_file, "rimoat_test_backup.json")
        ).assertIsDisplayed()
        composeRule.onNodeWithText(app.getString(R.string.screen_share)).assertIsDisplayed()
        composeRule.onNode(isDialog()).captureRoboImage(
            "src/test/screenshots/backup_saved_dialog.webp",
            roborazziOptions = options
        )
    }

    @Test
    fun transmitFeedbackBannerUsesFailureReasonResourceInsteadOfRawErrorString() {
        AppStrings.initialize(app)
        var log by mutableStateOf<TransmitLog?>(
            TransmitLog(
                transmitterType = TransmitterType.BUILT_IN,
                signal = org.qinyan.rimoat.hardware.IrSignal.Raw(38000, intArrayOf(9000, 4500)),
                isSuccess = false,
                errorMessage = "RAW_JAVA_STACK_MESSAGE",
                failureReason = TransmitFailureReason.NO_SIGNALS
            )
        )
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                ProvideExpressiveCanvas {
                    Scaffold(
                        containerColor = ExpressiveSurface.canvas,
                        snackbarHost = {
                            TransmitFeedbackBanner(log = log, onDismissed = { log = null })
                        }
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding))
                    }
                }
            }
        }
        val expected = app.getString(
            R.string.control_send_failed,
            app.getString(R.string.msg_no_signals)
        )
        composeRule.onNodeWithText(expected).assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(
            "src/test/screenshots/transmit_failure_banner.webp",
            roborazziOptions = options
        )
    }
}
