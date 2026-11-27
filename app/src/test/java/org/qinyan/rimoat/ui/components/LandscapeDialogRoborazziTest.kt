package org.qinyan.rimoat.ui.components

import android.app.Application
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ErrorCollector
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.learning.LearnerType
import org.qinyan.rimoat.hardware.learning.LearningState
import org.qinyan.rimoat.hardware.serial.SerialIrModule
import org.qinyan.rimoat.hardware.serial.SerialIrSettings
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.testing.collectScreenshotDifference
import org.qinyan.rimoat.ui.learning.SingleButtonLearningDialogContent
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w640dp-h360dp-land-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class, ExperimentalMaterial3Api::class)
class LandscapeDialogRoborazziTest {
    @get:Rule val screenshotDifferences = ErrorCollector()
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))
    private fun capture(name: String) {
        val root = compose.onNode(isDialog())
        screenshotDifferences.collectScreenshotDifference {
            root.captureRoboImage("src/test/screenshots/landscape_dialog_$name.webp", roborazziOptions = options)
        }
    }
    private fun panes() {
        val left = compose.onNodeWithTag("two_pane_primary").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("two_pane_secondary").fetchSemanticsNode().boundsInRoot
        assertTrue("Both panes must have useful width", left.width >= 160f && right.width >= 200f)
        assertTrue("Dialog panes must sit side by side", left.right < right.left)
        assertEquals(left.top, right.top, 1f)
    }

    @Test fun serialParametersScrollBesideModulesAndSaveOnce() {
        AppStrings.initialize(app)
        var saved: SerialIrSettings? = null
        var saves = 0
        var dismisses = 0
        compose.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                SerialIrSettingsForm(SerialIrSettings(module = SerialIrModule.YS_IRTM), emptyList(), {},
                    onSave = { value, _ -> saved = value; saves++; Result.success(Unit) },
                    onDismiss = { dismisses++ })
            }
        }
        panes()
        val module = compose.onNodeWithText(SerialIrModule.YS_IRTM.label)
        module.assertIsSelected()
        val initialModuleBounds = module.fetchSemanticsNode().boundsInRoot
        capture("serial_before")
        compose.onNode(hasSetTextAction() and hasText(app.getString(R.string.control_port_index)))
            .performScrollTo().performTextReplacement("1")
        compose.onNode(hasSetTextAction() and hasText(app.getString(R.string.control_baud_rate)))
            .performScrollTo().performTextReplacement("19200")
        compose.onNode(hasSetTextAction() and hasText(app.getString(R.string.control_module_address)))
            .performScrollTo().performTextReplacement("A2")
        assertEquals(initialModuleBounds, module.fetchSemanticsNode().boundsInRoot)
        capture("serial_edited")
        compose.onNodeWithText(app.getString(R.string.control_save)).assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(1, saves); assertEquals(1, dismisses)
        assertEquals(1, saved?.portIndex); assertEquals(19200, saved?.baudRate); assertEquals(0xA2, saved?.moduleAddress)
        capture("serial_saved")
    }

    @Test fun capturedLearningKeepsReceiverBesideTestAndSave() {
        AppStrings.initialize(app)
        val signal = IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560, 560, 560, 1690))
        var feedback by mutableStateOf<UiText?>(null)
        var tests = 0
        var saves = 0
        compose.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                SingleButtonLearningDialogContent(RemoteButton("power", "Power"), LearningState.Captured(signal), false,
                    testFeedbackMessage = feedback, learnerType = LearnerType.MOCK, isHardwareAvailable = true,
                    onStartLearning = {}, onCancelLearning = {}, onTestEmit = {
                        assertArrayEquals(signal.patternMicros, it.patternMicros)
                        tests++; feedback = UiText.Resource(R.string.screen_simulated_send)
                    }, onSave = {
                        assertArrayEquals(signal.patternMicros, it.patternMicros); saves++
                    }, onDismiss = {})
            }
        }
        panes()
        val left = compose.onNodeWithTag("two_pane_primary").fetchSemanticsNode().boundsInRoot
        capture("learning_before")
        compose.onNodeWithText(app.getString(R.string.screen_test_transmit)).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(app.getString(R.string.screen_simulated_send)).performScrollTo().assertIsDisplayed()
        capture("learning_tested")
        compose.onNodeWithText(app.getString(R.string.screen_save_button)).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, tests); assertEquals(1, saves)
        assertEquals(left, compose.onNodeWithTag("two_pane_primary").fetchSemanticsNode().boundsInRoot)
        capture("learning_saved")
    }

    @Test
    @Config(qualifiers = "en-w891dp-h411dp-land-mdpi")
    fun channelSelectionScrollsIndependentlyAndSelectsDemoOnce() {
        AppStrings.initialize(app)
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        val manager = IrTransmitterManager(app, scope)
        val changes = AtomicInteger()
        try {
            compose.waitUntil(10_000) { manager.serialSettingsLoaded.value }
            manager.setForceMockMode(false)
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                manager.forceMockMode.drop(1).collect { if (it) changes.incrementAndGet() }
            }
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    TransmitterSelectionBottomSheet(manager, rememberModalBottomSheetState(skipPartiallyExpanded = true), {})
                }
            }
            panes()
            val summary = compose.onNodeWithText(app.getString(R.string.control_current_output))
            val initialSummary = summary.fetchSemanticsNode().boundsInRoot
            capture("channels_before")
            val demo = compose.onNode(hasText(app.getString(R.string.control_demo_mode)) and hasClickAction())
            demo.performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(5_000) { manager.forceMockMode.value && changes.get() == 1 }
            demo.assertIsSelected()
            assertNull(manager.selectedTransmitterType.value)
            assertEquals(initialSummary, summary.fetchSemanticsNode().boundsInRoot)
            assertEquals(1, changes.get())
            capture("channels_selected")
        } finally {
            manager.release()
            runBlocking { job.cancelAndJoin() }
        }
    }
}
