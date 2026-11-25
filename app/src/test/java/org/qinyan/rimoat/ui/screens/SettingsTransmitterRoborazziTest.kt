package org.qinyan.rimoat.ui.screens

import android.app.Application
import android.content.Context
import android.hardware.ConsumerIrManager
import android.media.AudioManager
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
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
import org.qinyan.rimoat.data.RemoteRepository
import org.qinyan.rimoat.data.ThemePreferences
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.FakeConsumerIrManager
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.components.HardwareStatusButton
import org.qinyan.rimoat.ui.components.TransmitterSelectionBottomSheet
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    qualifiers = "zh-rCN-w411dp-h891dp-mdpi",
    application = Application::class,
    shadows = [FakeConsumerIrManager::class]
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class SettingsTransmitterRoborazziTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val screenshotOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(
            imageIoFormat = LosslessWebPImageIoFormat()
        )
    )

    @Test
    @OptIn(ExperimentalMaterial3Api::class)
    @Suppress("DEPRECATION")
    fun automaticChannelPrefersBuiltInWithHeadsetAndPreservesManualAudioSelection() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val consumerIr = app.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager
        Shadow.extract<FakeConsumerIrManager>(consumerIr).hasEmitter = true
        val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isWiredHeadsetOn = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val manager = IrTransmitterManager(app, scope)
        manager.setForceMockMode(false)
        manager.selectTransmitterType(null)

        try {
            runBlocking { manager.refreshHardware() }
            composeRule.waitUntil(10_000) {
                manager.transmitterType.value == TransmitterType.BUILT_IN &&
                    manager.availableTransmitters.value.any { it.type == TransmitterType.AUDIO }
            }
            composeRule.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    TransmitterSelectionBottomSheet(
                        transmitterManager = manager,
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        onDismissRequest = {}
                    )
                }
            }

            val autoLabel = app.getString(R.string.control_auto_select)
            val builtInLabel = app.getString(R.string.control_builtin_accessory)
            val audioLabel = app.getString(R.string.control_audio_accessory)
            composeRule.onNode(hasText(autoLabel) and hasClickAction()).assertIsSelected()
            composeRule.onNode(hasText(builtInLabel) and !hasClickAction()).assertIsDisplayed()
            assertEquals(TransmitterType.BUILT_IN, manager.currentTransmitterType())
            composeRule.onNode(isDialog()).captureRoboImage(
                filePath = "src/test/screenshots/transmitter_auto_builtin_with_headset.webp",
                roborazziOptions = screenshotOptions
            )

            composeRule.onNode(hasText(audioLabel) and hasClickAction()).performClick()
            composeRule.waitUntil(10_000) { manager.transmitterType.value == TransmitterType.AUDIO }
            composeRule.onNode(hasText(audioLabel) and hasClickAction()).assertIsSelected()
            composeRule.onNode(hasText(audioLabel) and !hasClickAction()).assertIsDisplayed()
            assertEquals(TransmitterType.AUDIO, manager.currentTransmitterType())
            composeRule.onNode(isDialog()).captureRoboImage(
                filePath = "src/test/screenshots/transmitter_manual_audio_with_builtin.webp",
                roborazziOptions = screenshotOptions
            )

            composeRule.onNode(hasText(autoLabel) and hasClickAction()).performClick()
            composeRule.waitUntil(10_000) { manager.transmitterType.value == TransmitterType.BUILT_IN }
            composeRule.onNode(hasText(autoLabel) and hasClickAction()).assertIsSelected()
            composeRule.onNode(hasText(builtInLabel) and !hasClickAction()).assertIsDisplayed()
            assertNull(manager.selectedTransmitterType.value)
            assertEquals(TransmitterType.BUILT_IN, manager.currentTransmitterType())
        } finally {
            manager.release()
            runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun settingsShowsTransmitterChannelPickerInsteadOfMockModeSwitch() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val wasWiredHeadsetOn = audioManager.isWiredHeadsetOn
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = RemoteRepository(app, scope)
        runBlocking { repository.awaitLoaded() }
        val transmitterManager = IrTransmitterManager(app, scope)
        transmitterManager.setForceMockMode(false)
        transmitterManager.selectTransmitterType(null)
        val themePreferences = ThemePreferences(app)
        val viewModel = RemoteListViewModel(
            repository = repository,
            transmitterManager = transmitterManager,
            themePreferences = themePreferences,
            backupFiles = BackupFileStore(app)
        )

        try {
            composeRule.waitUntil(10_000) { transmitterManager.serialSettingsLoaded.value }
            composeRule.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    SettingsScreen(viewModel = viewModel, onBack = {})
                }
            }

            val aboutLabel = app.getString(R.string.settings_about)
            val channelTitle = app.getString(R.string.control_ir_channel)
            val autoLabel = app.getString(R.string.control_auto_select)
            val demoLabel = app.getString(R.string.control_demo_mode)
            val doneLabel = app.getString(R.string.control_done)

            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(aboutLabel))
            composeRule.waitForIdle()

            composeRule.onNodeWithText("模拟模式").assertDoesNotExist()
            composeRule.onNodeWithText(channelTitle).assertIsDisplayed()
            composeRule.onNodeWithText(autoLabel).assertIsDisplayed()
            composeRule.onNodeWithText(aboutLabel).assertIsDisplayed()

            composeRule.onRoot().captureRoboImage(
                filePath = "src/test/screenshots/settings_transmitter_before.webp",
                roborazziOptions = screenshotOptions
            )

            composeRule.onNodeWithText(channelTitle).performClick()
            composeRule.waitForIdle()

            composeRule.onNode(isDialog()).assertIsDisplayed()
            composeRule.onNodeWithText("无可用硬件", substring = true).assertDoesNotExist()
            composeRule.onNodeWithText("不发射红外").assertDoesNotExist()
            val demoOption = composeRule.onNode(
                hasText(demoLabel) and hasClickAction() and hasAnyAncestor(isDialog())
            )
            demoOption.assertIsDisplayed()
            composeRule.onAllNodesWithText(demoLabel).fetchSemanticsNodes().let { nodes ->
                assertEquals(2, nodes.size)
            }
            composeRule.onNode(isDialog()).captureRoboImage(
                filePath = "src/test/screenshots/settings_transmitter_sheet.webp",
                roborazziOptions = screenshotOptions
            )

            demoOption.performClick()
            composeRule.waitForIdle()
            assertTrue(transmitterManager.forceMockMode.value)
            assertNull(transmitterManager.selectedTransmitterType.value)

            composeRule.onNode(hasText(doneLabel) and hasAnyAncestor(isDialog())).performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithText(demoLabel).assertIsDisplayed()
            composeRule.onRoot().captureRoboImage(
                filePath = "src/test/screenshots/settings_transmitter_after.webp",
                roborazziOptions = screenshotOptions
            )

            // Switch to an available physical channel and verify Settings updates its displayed value.
            audioManager.isWiredHeadsetOn = true
            runBlocking { transmitterManager.refreshHardware() }
            composeRule.waitUntil(10_000) {
                transmitterManager.availableTransmitters.value.any { it.type == TransmitterType.AUDIO }
            }
            composeRule.onNodeWithText(channelTitle).performClick()
            composeRule.waitForIdle()

            val audioChannelTitle = app.getString(R.string.control_audio_accessory)
            val audioOptionMatcher = hasText(audioChannelTitle) and hasClickAction() and
                hasAnyAncestor(isDialog())
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodes(audioOptionMatcher).fetchSemanticsNodes().size == 1
            }
            composeRule.onNode(audioOptionMatcher)
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            composeRule.waitUntil(10_000) {
                transmitterManager.transmitterType.value == TransmitterType.AUDIO
            }
            composeRule.waitForIdle()
            composeRule.onNode(audioOptionMatcher).assertIsSelected()
            assertFalse(transmitterManager.forceMockMode.value)
            assertEquals(TransmitterType.AUDIO, transmitterManager.selectedTransmitterType.value)

            composeRule.onNode(hasText(doneLabel) and hasAnyAncestor(isDialog())).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(app.getString(R.string.control_audio_ir)).assertIsDisplayed()
        } finally {
            audioManager.isWiredHeadsetOn = wasWiredHeadsetOn
            transmitterManager.release()
            runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

    @Test
    fun hardwareStatusButtonUsesDemoModeTextInsteadOfSimulationMode() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val demoLabel = app.getString(R.string.control_demo_mode)
        val contentDesc = app.getString(R.string.control_channel_settings)

        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                HardwareStatusButton(
                    transmitterType = TransmitterType.MOCK,
                    isReady = true,
                    forceMock = false,
                    onClick = {}
                )
            }
        }

        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, demoLabel)
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(contentDesc).assertIsDisplayed()
    }
}
