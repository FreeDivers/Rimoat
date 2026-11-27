package org.qinyan.rimoat.ui.learning

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoundEffect
import androidx.compose.ui.platform.SoundEffect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ErrorCollector
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.learning.LearnerType
import org.qinyan.rimoat.hardware.learning.LearningState
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.testing.collectScreenshotDifference
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp-port-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class ExpressiveLearningRoborazziTest {
    @get:Rule val screenshotDifferences = ErrorCollector()
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val signal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560, 560))
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    private fun capture(name: String, dialog: Boolean = false) {
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val root = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        screenshotDifferences.collectScreenshotDifference {
            root.captureRoboImage("src/test/screenshots/expressive_learning_$name.webp", roborazziOptions = options)
        }
    }

    @Test fun queueSelectionKeepsCapturedSignalAndSendAndConfirmStaySilent() {
        AppStrings.initialize(app)
        var page by mutableStateOf(CustomRemoteWizardUiState(
            step = WizardStep.LEARNING,
            remoteName = "Living room",
            keyQueue = listOf(
                WizardKeyItem("power", "Power", status = KeyLearnStatus.CAPTURED, capturedSignal = signal),
                WizardKeyItem("volume", "Volume up")
            )
        ))
        var sends = 0
        var confirms = 0
        var sounds = 0
        val sound = object : SoundEffect { override fun playClickSound() { sounds++ } }
        compose.setContent {
            CompositionLocalProvider(LocalSoundEffect provides sound) {
                IrRemoteTheme(dynamicColor = false, darkTheme = false) {
                    Scaffold(bottomBar = {
                        LearningKeyActions(page, true, {}, {}, {}, { confirms++ }, {
                            sends++
                            page = page.copy(isTestingTransmit = true)
                        })
                    }) { padding ->
                        LearningKeyWorkspace(page, LearningState.Captured(signal), LearnerType.MOCK,
                            available = true, receiveConfirmed = false, mock = true,
                            onMock = {}, onSelect = { page = page.copy(currentIndex = it) }, onAdd = {}, onRecord = {},
                            modifier = Modifier.fillMaxSize().padding(padding))
                    }
                }
            }
        }
        compose.onNode(hasText("Power") and hasClickAction()).assertIsSelected()
        capture("captured_before")
        compose.onNodeWithContentDescription(app.getString(R.string.screen_test_transmit)).performTouchInput { click() }
        compose.onNodeWithContentDescription(app.getString(R.string.screen_sending)).assertIsNotEnabled()
        compose.onNodeWithText(app.getString(R.string.screen_confirm_continue)).assertIsNotEnabled()
        assertEquals(1, sends)
        assertEquals(0, sounds)
        compose.runOnIdle { page = page.copy(isTestingTransmit = false) }
        compose.onNodeWithText(app.getString(R.string.screen_confirm_continue)).performTouchInput { click() }
        assertEquals(1, confirms)
        assertEquals(0, sounds)
        compose.onNode(hasText("Volume up") and hasClickAction()).performClick().assertIsSelected()
        compose.onNode(hasText("Power") and hasClickAction()).assertIsNotSelected()
        assertSame(signal, page.keyQueue.first().capturedSignal)
        capture("queue_selected")
    }

    @Test fun countdownAnnouncesActualRemainingTimeAndDisplaysCapturedWaveform() {
        AppStrings.initialize(app)
        var seconds by mutableStateOf(7)
        var captured by mutableStateOf(false)
        compose.setContent {
            IrRemoteTheme(dynamicColor = false, darkTheme = false) {
                Column {
                    if (captured) IrWaveformPreview(signal)
                    else IrListeningRipple(remainingSeconds = seconds, totalSeconds = 30)
                }
            }
        }
        compose.onNodeWithContentDescription(app.getString(R.string.screen_receive_remaining, 7)).assertIsDisplayed()
        capture("listening")
        compose.runOnIdle { seconds = 0 }
        compose.onNodeWithContentDescription(app.getString(R.string.screen_receive_remaining, 0)).assertIsDisplayed()
        compose.runOnIdle { captured = true }
        compose.onNodeWithContentDescription(app.getString(R.string.screen_received_waveform)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.screen_pulse_count, signal.patternMicros.size)).assertIsDisplayed()
        capture("waveform")
    }

    @Test
    @Config(qualifiers = "en-w320dp-h891dp-port-mdpi")
    fun narrowLargeTextDialogKeepsTestAndSaveReachable() {
        RuntimeEnvironment.setFontScale(2f)
        AppStrings.initialize(app)
        assertEquals(2f, app.resources.configuration.fontScale, 0f)
        var testing by mutableStateOf(false)
        var tests = 0
        var saves = 0
        var closes = 0
        compose.setContent {
            IrRemoteTheme(dynamicColor = false, darkTheme = true, pureBlackBackground = true) {
                SingleButtonLearningDialogContent(
                    RemoteButton("power", "Power"), LearningState.Captured(signal), testing,
                    learnerType = LearnerType.MOCK, isHardwareAvailable = true,
                    onStartLearning = {}, onCancelLearning = {}, onTestEmit = {
                        assertSame(signal, it)
                        tests++
                        testing = true
                    }, onSave = { assertSame(signal, it); saves++ }, onDismiss = { closes++ }
                )
            }
        }
        compose.onNodeWithText(app.getString(R.string.screen_close)).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(app.getString(R.string.screen_test_transmit))
            .performScrollTo().assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp)
        capture("dialog_large_before", dialog = true)
        compose.onNodeWithText(app.getString(R.string.screen_test_transmit)).performTouchInput { click() }
        compose.onNodeWithText(app.getString(R.string.screen_save_button))
            .performScrollTo().assertIsDisplayed().assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
        assertEquals(1, tests)
        compose.runOnIdle { testing = false }
        compose.onNodeWithText(app.getString(R.string.screen_save_button))
            .performScrollTo().assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
        assertEquals(1, saves)
        capture("dialog_large_saved", dialog = true)
        compose.onNodeWithText(app.getString(R.string.screen_close)).assertIsDisplayed().performTouchInput { click() }
        assertEquals(1, closes)
    }

    @Test fun leaveLearningDialogCoversLightDarkAmoledAndDynamicColors() {
        AppStrings.initialize(app)
        var dark by mutableStateOf(false)
        var pureBlack by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var confirms = 0
        var dismisses = 0
        compose.setContent {
            IrRemoteTheme(dynamicColor = dynamic, darkTheme = dark, pureBlackBackground = pureBlack) {
                LeaveLearningDraftDialog(
                    onConfirmDiscard = { confirms++ },
                    onDismiss = { dismisses++ }
                )
            }
        }
        compose.onNodeWithText(app.getString(R.string.screen_leave_learning_title)).assertIsDisplayed()
        capture("leave_dialog_light", dialog = true)

        compose.runOnIdle { dark = true; pureBlack = false; dynamic = false }
        capture("leave_dialog_dark", dialog = true)

        compose.runOnIdle { dark = true; pureBlack = true; dynamic = false }
        capture("leave_dialog_amoled", dialog = true)

        compose.runOnIdle { dark = false; pureBlack = false; dynamic = true }
        capture("leave_dialog_dynamic", dialog = true)

        compose.onNodeWithText(app.getString(R.string.screen_continue_learning)).performTouchInput { click() }
        assertEquals(1, dismisses)
        assertEquals(0, confirms)
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h891dp-port-mdpi")
    fun leaveLearningDialogNarrowLargeTextKeepsBothActionsReachable() {
        RuntimeEnvironment.setFontScale(2f)
        AppStrings.initialize(app)
        assertEquals(2f, app.resources.configuration.fontScale, 0f)
        var show by mutableStateOf(true)
        var confirms = 0
        var dismisses = 0
        compose.setContent {
            IrRemoteTheme(dynamicColor = false, darkTheme = false) {
                if (show) {
                    LeaveLearningDraftDialog(
                        onConfirmDiscard = { confirms++; show = false },
                        onDismiss = { dismisses++; show = false }
                    )
                }
            }
        }
        compose.onNodeWithText(app.getString(R.string.screen_leave_learning_title)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.screen_continue_learning))
            .assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(app.getString(R.string.screen_discard_and_switch))
            .assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp)
        capture("leave_dialog_large_before", dialog = true)

        compose.onNodeWithText(app.getString(R.string.screen_continue_learning)).performTouchInput { click() }
        assertEquals(1, dismisses)
        assertEquals(0, confirms)

        compose.runOnIdle { show = true }
        compose.onNodeWithText(app.getString(R.string.screen_discard_and_switch))
            .assertIsDisplayed().performTouchInput { click() }
        assertEquals(1, confirms)
        assertEquals(1, dismisses)
    }
}
