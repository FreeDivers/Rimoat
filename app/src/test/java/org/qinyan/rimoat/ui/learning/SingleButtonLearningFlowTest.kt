package org.qinyan.rimoat.ui.learning

import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.SignalType
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.hardware.learning.LearningState
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SingleButtonLearningFlowTest {

    private val stores = androidx.lifecycle.ViewModelStore()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var learnerManager: IrLearnerManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        learnerManager = IrLearnerManager(context = null)
        learnerManager.mockLearner.setSimulatedDelayMs(0L)
    }

    @After
    fun tearDown() {
        learnerManager.cancel()
        org.qinyan.rimoat.clearViewModelsAndAwait(stores, testDispatcher)
        Dispatchers.resetMain()
    }

    private fun createSampleProfile(): RemoteProfile {
        return RemoteProfile(
            id = "test_remote_1",
            name = "测试电视遥控器",
            category = DeviceCategory.TV,
            buttons = listOf(
                RemoteButton(
                    id = "btn_power",
                    label = "电源",
                    iconName = "power",
                    signalType = SignalType.NEC,
                    necAddress = 0x00FF,
                    necCommand = 0x45
                ),
                RemoteButton(
                    id = "btn_mute",
                    label = "静音",
                    iconName = "volume_off",
                    signalType = SignalType.RAW,
                    rawPattern = listOf(9000, 4500, 560, 560)
                )
            )
        )
    }

    @Test
    fun testLearningModeToggle() {
        val viewModel = RemoteControlViewModel(
            remoteId = "test_remote_1",
            learnerManager = learnerManager,
            initialProfile = createSampleProfile()
        ).also { stores.put("controller", it) }

        assertFalse(viewModel.isLearningMode.value)

        viewModel.toggleLearningMode()
        assertTrue(viewModel.isLearningMode.value)

        viewModel.setLearningMode(false)
        assertFalse(viewModel.isLearningMode.value)
    }

    @Test
    fun testStartAndDismissLearningDialog() {
        val profile = createSampleProfile()
        val viewModel = RemoteControlViewModel(
            remoteId = "test_remote_1",
            learnerManager = learnerManager,
            initialProfile = profile
        ).also { stores.put("controller", it) }

        assertNull(viewModel.learningDialogButton.value)

        val targetButton = profile.buttons[0]
        viewModel.startLearningButton(targetButton)
        assertEquals(targetButton, viewModel.learningDialogButton.value)

        viewModel.dismissLearningDialog()
        assertNull(viewModel.learningDialogButton.value)
        assertEquals(LearningState.Idle, learnerManager.state.value)
    }

    @Test
    fun capturedSignalStaysInViewModelAcrossScreenStopAndClearsOnDismiss() {
        val profile = createSampleProfile()
        val viewModel = RemoteControlViewModel(
            remoteId = profile.id,
            learnerManager = learnerManager,
            initialProfile = profile
        ).also { stores.put("controller", it) }
        val button = profile.buttons.first()
        val signal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560))

        viewModel.startLearningButton(button)
        viewModel.updateLearningCapture(button.id, signal)
        viewModel.onScreenStopped()

        assertEquals(button.id, viewModel.learningDialogButton.value?.id)
        assertEquals(button.id to signal, viewModel.capturedLearningSignal.value)
        viewModel.dismissLearningDialog()
        assertNull(viewModel.capturedLearningSignal.value)
    }

    @Test
    fun testSaveLearnedButtonUpdatesSignalToRaw() {
        val profile = createSampleProfile()
        val viewModel = RemoteControlViewModel(
            remoteId = "test_remote_1",
            learnerManager = learnerManager,
            initialProfile = profile
        ).also { stores.put("controller", it) }

        val targetButton = profile.buttons[0]
        assertEquals(SignalType.NEC, targetButton.signalType)
        viewModel.startLearningButton(targetButton)

        val learnedSignal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560, 560, 1690))
        viewModel.saveLearnedButton(targetButton, learnedSignal)
        testDispatcher.scheduler.advanceUntilIdle()

        // Dialog should be dismissed
        assertNull(viewModel.learningDialogButton.value)

        // Button in profile should now be updated to RAW with learned pulses
        val updatedProfile = viewModel.remoteProfile.value
        assertNotNull(updatedProfile)
        val updatedButton = updatedProfile?.buttons?.find { it.id == targetButton.id }
        assertNotNull(updatedButton)
        assertEquals(SignalType.RAW, updatedButton?.signalType)
        assertEquals(38000, updatedButton?.frequencyHz)
        assertEquals(listOf(9000, 4500, 560, 560, 560, 1690), updatedButton?.rawPattern)
        assertNull(updatedButton?.necAddress)
        assertNull(updatedButton?.necCommand)
    }

    @Test
    fun testTestTransmitSignalTriggersCallback() {
        var transmittedSignal: IrSignal? = null

        val profile = createSampleProfile()
        val viewModel = RemoteControlViewModel(
            remoteId = "test_remote_1",
            learnerManager = learnerManager,
            initialProfile = profile,
            onTransmitSignal = { transmittedSignal = it }
        ).also { stores.put("controller", it) }

        val testSignal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560))
        viewModel.testTransmitSignal(testSignal)
        testDispatcher.scheduler.advanceUntilIdle()

        // sendButton calls onTransmitSignal
        viewModel.sendButton(profile.buttons[0].withLearnedSignal(testSignal))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(transmittedSignal)
        assertTrue(transmittedSignal is IrSignal.Raw)
        val raw = transmittedSignal as IrSignal.Raw
        assertEquals(38000, raw.frequencyHz)
        assertEquals(4, raw.patternMicros.size)
    }
}

