package org.qinyan.rimoat.ui.learning

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.hardware.learning.LearningState
import org.qinyan.rimoat.hardware.learning.MockIrLearner
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-port-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class SingleButtonLearningDialogRegressionTest {

    @get:Rule
    val compose = createComposeRule()

    private val app get() = RuntimeEnvironment.getApplication()
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )
    private val button = RemoteButton("power", "电源")
    private val originalSignal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560, 560, 1690))

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNode(isDialog()).captureRoboImage(
            "src/test/screenshots/single_button_learning_$name.webp",
            roborazziOptions = options
        )
    }

    @Test
    fun rerecordCancelRestoresPreviousCapturedSignal() {
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val learner = IrLearnerManager(app, scope).apply {
            setForceMockMode(true)
            mockLearner.setSimulatedDelayMs(5_000)
        }
        val transmitter = IrTransmitterManager(app, scope).apply { setForceMockMode(true) }
        var capturedInHost: IrSignal.Raw? = originalSignal
        var savedSignal: IrSignal.Raw? = null
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    SingleButtonLearningDialog(
                        button = button,
                        learnerManager = learner,
                        transmitterManager = transmitter,
                        onDismissRequest = {},
                        onSaveLearnedButton = { _, signal -> savedSignal = signal },
                        initialCapturedSignal = originalSignal,
                        onCaptureChanged = { capturedInHost = it }
                    )
                }
            }
            compose.onNodeWithText(app.getString(R.string.screen_save_button)).assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithContentDescription(app.getString(R.string.screen_rerecord)).performClick()
            compose.waitUntil(3_000) {
                compose.onAllNodesWithText(app.getString(R.string.screen_cancel_receive)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(app.getString(R.string.screen_cancel_receive)).performClick()
            compose.waitForIdle()

            assertEquals(originalSignal, capturedInHost)
            compose.onNodeWithText(app.getString(R.string.screen_save_button)).assertIsDisplayed().assertIsEnabled().performClick()
            assertEquals(originalSignal, savedSignal)
        } finally {
            learner.cancel()
            learner.unregisterReceiver()
            transmitter.release()
            scope.cancel()
        }
    }

    @Test
    fun rerecordStopRestoresPreviousCapturedSignal() {
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val learner = IrLearnerManager(app, scope).apply {
            setForceMockMode(true)
            mockLearner.setSimulatedDelayMs(5_000)
        }
        val transmitter = IrTransmitterManager(app, scope).apply { setForceMockMode(true) }
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.RESUMED
        var capturedInHost: IrSignal.Raw? = originalSignal
        try {
            compose.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                        SingleButtonLearningDialog(
                            button = button,
                            learnerManager = learner,
                            transmitterManager = transmitter,
                            onDismissRequest = {},
                            onSaveLearnedButton = { _, _ -> },
                            initialCapturedSignal = originalSignal,
                            onCaptureChanged = { capturedInHost = it }
                        )
                    }
                }
            }
            compose.onNodeWithContentDescription(app.getString(R.string.screen_rerecord)).performClick()
            compose.waitUntil(3_000) {
                compose.onAllNodesWithText(app.getString(R.string.screen_cancel_receive)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.runOnUiThread {
                owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
            compose.waitForIdle()

            assertEquals(originalSignal, capturedInHost)
            compose.onNodeWithText(app.getString(R.string.screen_save_button)).assertIsDisplayed().assertIsEnabled()
        } finally {
            learner.cancel()
            learner.unregisterReceiver()
            transmitter.release()
            scope.cancel()
        }
    }

    @Test
    fun rerecordTimeoutRestoresPreviousCapturedSignalAndShowsTimeoutNotice() {
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val learner = IrLearnerManager(app, scope).apply {
            setForceMockMode(true)
            mockLearner.setSimulationMode(MockIrLearner.SimulationMode.TIMEOUT)
            mockLearner.setSimulatedDelayMs(0)
        }
        val transmitter = IrTransmitterManager(app, scope).apply { setForceMockMode(true) }
        var capturedInHost: IrSignal.Raw? = originalSignal
        var savedSignal: IrSignal.Raw? = null
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    SingleButtonLearningDialog(
                        button = button,
                        learnerManager = learner,
                        transmitterManager = transmitter,
                        onDismissRequest = {},
                        onSaveLearnedButton = { _, signal -> savedSignal = signal },
                        initialCapturedSignal = originalSignal,
                        onCaptureChanged = { capturedInHost = it }
                    )
                }
            }
            compose.onNodeWithContentDescription(app.getString(R.string.screen_rerecord)).performClick()
            compose.waitUntil(3_000) {
                compose.onAllNodesWithText(app.getString(R.string.screen_learn_timeout)).fetchSemanticsNodes().isNotEmpty()
            }

            assertEquals(originalSignal, capturedInHost)
            compose.onNodeWithText(app.getString(R.string.screen_learn_timeout)).assertIsDisplayed()
            compose.onNodeWithText(app.getString(R.string.screen_save_button)).assertIsDisplayed().assertIsEnabled()
            capture("rerecord_timeout_restored")
            compose.onNodeWithText(app.getString(R.string.screen_save_button)).performClick()
            assertEquals(originalSignal, savedSignal)
        } finally {
            learner.cancel()
            learner.unregisterReceiver()
            transmitter.release()
            scope.cancel()
        }
    }

    @Test
    fun rerecordFailureRestoresPreviousCapturedSignalAndFormatsLocalizedError() {
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val learner = IrLearnerManager(app, scope).apply {
            setForceMockMode(true)
            mockLearner.setSimulationMode(MockIrLearner.SimulationMode.ERROR)
            mockLearner.setSimulatedDelayMs(0)
        }
        val transmitter = IrTransmitterManager(app, scope).apply { setForceMockMode(true) }
        var capturedInHost: IrSignal.Raw? = originalSignal
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    SingleButtonLearningDialog(
                        button = button,
                        learnerManager = learner,
                        transmitterManager = transmitter,
                        onDismissRequest = {},
                        onSaveLearnedButton = { _, _ -> },
                        initialCapturedSignal = originalSignal,
                        onCaptureChanged = { capturedInHost = it }
                    )
                }
            }
            val expectedError = app.getString(
                R.string.screen_learning_error_e,
                app.getString(R.string.msg_mock_failure)
            )
            compose.onNodeWithContentDescription(app.getString(R.string.screen_rerecord)).performClick()
            compose.waitUntil(3_000) {
                compose.onAllNodesWithText(expectedError).fetchSemanticsNodes().isNotEmpty()
            }

            assertEquals(originalSignal, capturedInHost)
            compose.onNodeWithText(expectedError).assertIsDisplayed()
            compose.onNodeWithText(app.getString(R.string.screen_save_button)).assertIsDisplayed().assertIsEnabled()
            capture("rerecord_error_restored")
        } finally {
            learner.cancel()
            learner.unregisterReceiver()
            transmitter.release()
            scope.cancel()
        }
    }

    @Test
    fun initialFailureDisplaysLocalizedErrorWrapperInsteadOfRawMessage() {
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val learner = IrLearnerManager(app, scope).apply {
            setForceMockMode(true)
            mockLearner.setSimulationMode(MockIrLearner.SimulationMode.ERROR)
            mockLearner.setSimulatedDelayMs(0)
        }
        val transmitter = IrTransmitterManager(app, scope).apply { setForceMockMode(true) }
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    SingleButtonLearningDialog(
                        button = button,
                        learnerManager = learner,
                        transmitterManager = transmitter,
                        onDismissRequest = {},
                        onSaveLearnedButton = { _, _ -> },
                        autoStart = true
                    )
                }
            }
            val expectedError = app.getString(
                R.string.screen_learning_error_e,
                app.getString(R.string.msg_mock_failure)
            )
            compose.waitUntil(3_000) {
                compose.onAllNodesWithText(expectedError).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(expectedError).assertIsDisplayed()
            capture("initial_error")
        } finally {
            learner.cancel()
            learner.unregisterReceiver()
            transmitter.release()
            scope.cancel()
        }
    }

    @Test
    fun timeoutDetectionDoesNotDependOnMessageText() {
        assertTrue(isLearningTimeout(java.util.concurrent.TimeoutException("custom reason"), LearningState.Idle))
        assertTrue(isLearningTimeout(org.qinyan.rimoat.hardware.learning.LearningTimeoutException("timed out"), LearningState.Idle))
        assertTrue(isLearningTimeout(Exception("custom localized text"), LearningState.Timeout))
        assertFalse(
            isLearningTimeout(
                IllegalStateException("Device timed out during handshake"),
                LearningState.Error("Device timed out during handshake")
            )
        )
    }

    @Test
    fun errorFormattingUsesResourceInsteadOfDynamicExceptionText() {
        AppStrings.initialize(app)
        val blankError = formatSingleButtonLearningError(IllegalStateException("   "))
        assertEquals(UiText.Resource(R.string.screen_learn_failed), blankError)
        assertEquals(app.getString(R.string.screen_learn_failed), blankError.resolve(app))

        val detailedError = formatSingleButtonLearningError(IllegalStateException("Device disconnected"))
        assertEquals(UiText.Resource(R.string.screen_learn_failed), detailedError)
        assertFalse(detailedError.resolve(app).contains("Device disconnected"))

        val typedError = formatSingleButtonLearningError(SecurityException("Permission missing"))
        assertEquals(
            UiText.resource(R.string.screen_learning_error_e, UiText.Resource(R.string.error_permission_denied)),
            typedError
        )
    }

    @Test
    fun saveErrorAcceptsUiTextResourceAndRendersWithoutLeakingRawText() {
        AppStrings.initialize(app)
        val saveError = org.qinyan.rimoat.i18n.UiText.resource(
            R.string.msg_captured_save_failed,
            org.qinyan.rimoat.i18n.UiText.Resource(R.string.error_storage_full)
        )
        compose.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                SingleButtonLearningDialogContent(
                    button = button,
                    state = LearningState.Captured(originalSignal),
                    isTesting = false,
                    learnerType = org.qinyan.rimoat.hardware.learning.LearnerType.MOCK,
                    isHardwareAvailable = true,
                    onStartLearning = {},
                    onCancelLearning = {},
                    onTestEmit = {},
                    onSave = {},
                    onDismiss = {},
                    saveError = saveError
                )
            }
        }
        compose.onNodeWithText(saveError.resolve(app)).assertIsDisplayed()
        capture("save_error")
    }
}
