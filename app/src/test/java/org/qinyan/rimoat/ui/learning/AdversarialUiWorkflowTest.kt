package org.qinyan.rimoat.ui.learning

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.SignalType
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.hardware.learning.LearningState
import org.qinyan.rimoat.hardware.learning.MockIrLearner
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Learning workflow stress tests:
 * 1. Wizard State Machine: rapid step transitions, skipping queue, empty/duplicate labels,
 *    0 vs >=1 finish early constraints, rapid category queue re-init, timeout/error recovery.
 * 2. Single-Button Learning: rapid learning mode toggle during open dialog, immediate dismiss
 *    upon listening, rapid test transmit spamming under concurrency, signal overwriting integrity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdversarialUiWorkflowTest {
    @get:org.junit.Rule val strings = org.qinyan.rimoat.testing.AppStringsResourceRule()

    private val testDispatcher = StandardTestDispatcher()
    private val stores = ViewModelStore()
    private val trackedModels = mutableListOf<ViewModel>()
    private fun <T : ViewModel> tracked(vm: T): T = vm.also { stores.put("vm${trackedModels.size}", it); trackedModels.add(it) }
    private var managerScope: CoroutineScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var learnerManager: IrLearnerManager
    private lateinit var transmitterManager: IrTransmitterManager

    private fun createMockContext(): Context {
        return object : android.content.ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
        }
    }

    private fun createSampleProfile(): RemoteProfile {
        return RemoteProfile(
            id = "adversarial_remote_1",
            name = "客厅测试电视",
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
                    id = "btn_vol_up",
                    label = "音量+",
                    iconName = "volume_up",
                    signalType = SignalType.RAW,
                    rawPattern = listOf(9000, 4500, 560, 560)
                ),
                RemoteButton(
                    id = "btn_vol_down",
                    label = "音量-",
                    iconName = "volume_down",
                    signalType = SignalType.RAW,
                    rawPattern = listOf(9000, 4500, 560, 560)
                )
            )
        )
    }

    private fun awaitCondition(timeoutMs: Long = 3000L, condition: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            testDispatcher.scheduler.advanceUntilIdle()
            if (condition()) return true
            Thread.sleep(10)
        }
        testDispatcher.scheduler.advanceUntilIdle()
        return condition()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        managerScope = CoroutineScope(Dispatchers.Default + Job())
        val context = createMockContext()

        learnerManager = IrLearnerManager(context = null, scope = managerScope)
        learnerManager.mockLearner.setSimulatedDelayMs(0L)

        transmitterManager = IrTransmitterManager(context = context, scope = managerScope)
        transmitterManager.setForceMockMode(true)
        transmitterManager.mockTransmitter.simulateDelayMs = 0L
    }

    @After
    fun tearDown() {
        if (::learnerManager.isInitialized) {
            learnerManager.cancel()
        }
        managerScope.cancel()
        Thread.sleep(50)
        testDispatcher.scheduler.advanceUntilIdle()
        stores.clear()
        val deadline = System.nanoTime() + 5_000_000_000L
        while (trackedModels.any { it.viewModelScope.coroutineContext[kotlinx.coroutines.Job]?.isCompleted == false }) {
            testDispatcher.scheduler.advanceUntilIdle()
            check(System.nanoTime() < deadline) { "ViewModel jobs did not finish" }
            Thread.sleep(10)
        }
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. Wizard State Machine Challenges
    // =========================================================================

    @Test
    fun challengeWizard_proceedToLearningValidationAndRapidTransitions() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            transmitterManager = transmitterManager,
            initialCategory = DeviceCategory.TV
        ))

        // 1. Whitespace or empty name should be rejected
        viewModel.setRemoteName("")
        assertFalse(viewModel.proceedToLearning())
        assertEquals(WizardStep.SETUP, viewModel.uiState.value.step)
        assertEquals(UiText.Resource(R.string.screen_enter_name), viewModel.uiState.value.errorMessage)

        viewModel.setRemoteName("   \t\n  ")
        assertFalse(viewModel.proceedToLearning())
        assertEquals(WizardStep.SETUP, viewModel.uiState.value.step)

        // 2. Valid name proceeds to LEARNING
        viewModel.setRemoteName(" 客厅智能电视 ")
        assertTrue(viewModel.proceedToLearning())
        assertEquals(WizardStep.LEARNING, viewModel.uiState.value.step)
        assertEquals(0, viewModel.uiState.value.currentIndex)
        assertNull(viewModel.uiState.value.errorMessage)

        // 3. Rapid redundant proceedToLearning calls should remain in LEARNING
        repeat(10) {
            viewModel.proceedToLearning()
            assertEquals(WizardStep.LEARNING, viewModel.uiState.value.step)
            assertEquals(0, viewModel.uiState.value.currentIndex)
        }

        // 4. Boundary back handling: at index 0, back goes to SETUP
        assertTrue(viewModel.handleBack())
        assertEquals(WizardStep.SETUP, viewModel.uiState.value.step)

        // In SETUP, back returns false (cannot go back further)
        assertFalse(viewModel.handleBack())
        assertEquals(WizardStep.SETUP, viewModel.uiState.value.step)
    }

    @Test
    fun challengeWizard_skippingAllKeysInQueueTransitionsToReviewWithZeroLearned() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            transmitterManager = transmitterManager,
            initialCategory = DeviceCategory.TV
        ))
        viewModel.setRemoteName("全部跳过测试")
        viewModel.proceedToLearning()

        val totalDefaultKeys = viewModel.uiState.value.keyQueue.size
        assertEquals(12, totalDefaultKeys)

        // Skip every single key in the queue
        for (i in 0 until totalDefaultKeys) {
            assertEquals(i, viewModel.uiState.value.currentIndex)
            assertEquals(WizardStep.LEARNING, viewModel.uiState.value.step)
            viewModel.skipCurrentKey()
        }

        // After skipping the last key, should transition automatically to REVIEW
        assertEquals(WizardStep.REVIEW, viewModel.uiState.value.step)
        assertEquals(0, viewModel.uiState.value.learnedCount)
        assertFalse(viewModel.uiState.value.canFinishEarly)

        // All items in the queue should be marked SKIPPED
        assertTrue(viewModel.uiState.value.keyQueue.all { it.status == KeyLearnStatus.SKIPPED })

        // Calling skipCurrentKey or confirmCurrentKey again when already in REVIEW should not throw
        viewModel.skipCurrentKey()
        assertEquals(WizardStep.REVIEW, viewModel.uiState.value.step)

        // handleBack from REVIEW returns to LEARNING
        assertTrue(viewModel.handleBack())
        assertEquals(WizardStep.LEARNING, viewModel.uiState.value.step)
    }

    @Test
    fun challengeWizard_finishEarlyConstraintWithZeroVsNonZeroLearned() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            transmitterManager = transmitterManager,
            initialCategory = DeviceCategory.TV
        ))
        viewModel.setRemoteName("提前完成约束测试")
        viewModel.proceedToLearning()

        // 1. With 0 keys learned, canFinishEarly MUST be false and finishEarly() MUST NOT advance
        assertEquals(0, viewModel.uiState.value.learnedCount)
        assertFalse(viewModel.uiState.value.canFinishEarly)

        viewModel.finishEarly()
        assertEquals(WizardStep.LEARNING, viewModel.uiState.value.step) // Must stay in LEARNING

        // 2. Skip first key -> learnedCount is still 0
        viewModel.skipCurrentKey()
        assertEquals(0, viewModel.uiState.value.learnedCount)
        assertFalse(viewModel.uiState.value.canFinishEarly)
        viewModel.finishEarly()
        assertEquals(WizardStep.LEARNING, viewModel.uiState.value.step)

        // 3. Learn the second key
        val sampleSignal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560))
        learnerManager.mockLearner.triggerManualSignal(sampleSignal)
        viewModel.startLearningCurrentKey()

        val captured = awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED }
        assertTrue(captured)

        viewModel.confirmCurrentKey()
        testDispatcher.scheduler.advanceUntilIdle()

        // 4. Now learnedCount == 1, canFinishEarly MUST be true
        assertEquals(1, viewModel.uiState.value.learnedCount)
        assertTrue(viewModel.uiState.value.canFinishEarly)

        // 5. finishEarly() should now successfully transition to REVIEW
        viewModel.finishEarly()
        assertEquals(WizardStep.REVIEW, viewModel.uiState.value.step)

        // 6. Verify saveRemote produces exactly 1 learned button with the captured signal
        var savedProfile: RemoteProfile? = null
        viewModel.saveRemote { profile ->
            savedProfile = profile
        }
        assertTrue(awaitCondition { savedProfile != null })

        assertNotNull(savedProfile)
        assertEquals(1, savedProfile?.buttons?.size)
        val btn = savedProfile!!.buttons.first()
        assertEquals(SignalType.RAW, btn.signalType)
        assertEquals(38000, btn.frequencyHz)
        assertEquals(listOf(9000, 4500, 560, 560), btn.rawPattern)
    }

    @Test
    fun challengeWizard_addCustomKeysValidationAndDuplicationStress() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.CUSTOM
        ))
        val initialCount = viewModel.uiState.value.keyQueue.size

        // 1. Empty and whitespace labels must be ignored
        viewModel.addCustomKey("")
        assertEquals(initialCount, viewModel.uiState.value.keyQueue.size)

        viewModel.addCustomKey("   ")
        assertEquals(initialCount, viewModel.uiState.value.keyQueue.size)

        viewModel.addCustomKey("\t\n")
        assertEquals(initialCount, viewModel.uiState.value.keyQueue.size)

        // 2. Adding key with whitespace padding should be trimmed
        viewModel.addCustomKey("   低音炮   ")
        assertEquals(initialCount + 1, viewModel.uiState.value.keyQueue.size)
        assertEquals("低音炮", viewModel.uiState.value.keyQueue.last().label)

        // 3. Adding duplicate labels is permitted with distinct unique IDs
        viewModel.addCustomKey("低音炮")
        assertEquals(initialCount + 2, viewModel.uiState.value.keyQueue.size)
        val key1 = viewModel.uiState.value.keyQueue[viewModel.uiState.value.keyQueue.size - 2]
        val key2 = viewModel.uiState.value.keyQueue[viewModel.uiState.value.keyQueue.size - 1]
        assertEquals("低音炮", key1.label)
        assertEquals("低音炮", key2.label)
        assertNotEquals(key1.id, key2.id)

        // 4. Concurrency stress: 30 concurrent additions
        runBlocking {
            val jobs = (1..30).map { i ->
                async(Dispatchers.Default) {
                    viewModel.addCustomKey("并发按键_$i")
                }
            }
            jobs.awaitAll()
        }

        // Verify total size = initialCount + 2 + 30
        assertEquals(initialCount + 32, viewModel.uiState.value.keyQueue.size)
        // Verify all IDs are completely unique
        val allIds = viewModel.uiState.value.keyQueue.map { it.id }
        assertEquals(allIds.toSet().size, allIds.size)
    }

    @Test
    fun challengeWizard_rapidCategoryChangesInSetupStep() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.TV
        ))
        assertEquals(DeviceCategory.TV, viewModel.uiState.value.category)
        assertEquals(12, viewModel.uiState.value.keyQueue.size)

        // Rapid cycling through all categories
        val sequence = listOf(
            DeviceCategory.AC to 6,
            DeviceCategory.FAN to 5,
            DeviceCategory.PROJECTOR to 10,
            DeviceCategory.AUDIO to 8,
            DeviceCategory.CUSTOM to 8,
            DeviceCategory.TV to 12
        )

        for ((cat, expectedCount) in sequence) {
            viewModel.setCategory(cat)
            assertEquals(cat, viewModel.uiState.value.category)
            assertEquals(expectedCount, viewModel.uiState.value.keyQueue.size)
            assertEquals(0, viewModel.uiState.value.currentIndex)
        }

        // Proceed to LEARNING, then change category: category field updates, but queue should NOT be wiped
        viewModel.setRemoteName("电视品类保护测试")
        viewModel.proceedToLearning()
        viewModel.skipCurrentKey() // Advance to index 1

        viewModel.setCategory(DeviceCategory.AC)
        assertEquals(DeviceCategory.AC, viewModel.uiState.value.category)
        // Key queue should NOT have been wiped to AC (6 keys); must remain TV (12 keys)
        assertEquals(12, viewModel.uiState.value.keyQueue.size)
        assertEquals(1, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun challengeWizard_errorAndTimeoutRecoveryInQueue() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.TV
        ))
        viewModel.setRemoteName("容错测试")
        viewModel.proceedToLearning()

        // 1. Timeout simulation: set mode to TIMEOUT before starting
        learnerManager.mockLearner.setSimulationMode(MockIrLearner.SimulationMode.TIMEOUT)
        viewModel.startLearningCurrentKey()
        assertEquals(KeyLearnStatus.LEARNING, viewModel.uiState.value.currentKey?.status)

        // TIMEOUT with zero simulated delay completes itself. Triggering another timeout
        // before the IO coroutine starts can leak a stale terminal event into the retry.
        val timeoutHandled = awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.PENDING }
        assertTrue(timeoutHandled)

        // Key resets to PENDING after timeout, with timeout error message
        assertEquals(KeyLearnStatus.PENDING, viewModel.uiState.value.currentKey?.status)
        assertEquals(UiText.Resource(R.string.screen_learn_timeout), viewModel.uiState.value.errorMessage)

        // 2. Error simulation
        learnerManager.mockLearner.setSimulationMode(MockIrLearner.SimulationMode.ERROR)
        viewModel.startLearningCurrentKey()
        val errorHandled = awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.PENDING }
        assertTrue(errorHandled)

        assertEquals(KeyLearnStatus.PENDING, viewModel.uiState.value.currentKey?.status)
        assertEquals(
            UiText.resource(R.string.screen_learning_error_e, UiText.Dynamic("Mock hardware simulation failure")),
            viewModel.uiState.value.errorMessage
        )

        // 3. Normal recovery after error
        learnerManager.mockLearner.setSimulationMode(MockIrLearner.SimulationMode.SUCCESS_NEC)
        learnerManager.mockLearner.setSimulatedDelayMs(0L)
        viewModel.startLearningCurrentKey()
        val captured = awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED }
        assertTrue(captured)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // =========================================================================
    // 2. Single-Button In-Place Learning Challenges
    // =========================================================================

    @Test
    fun challengeSingleButton_rapidLearningModeToggleWithOpenDialog() {
        val profile = createSampleProfile()
        val viewModel = tracked(RemoteControlViewModel(
            remoteId = "adversarial_remote_1",
            learnerManager = learnerManager,
            transmitterManager = transmitterManager,
            initialProfile = profile
        ))

        val targetButton = profile.buttons[0]
        viewModel.startLearningButton(targetButton)
        assertEquals(targetButton, viewModel.learningDialogButton.value)

        // Rapidly toggle learning mode sequentially
        repeat(50) {
            viewModel.toggleLearningMode()
        }

        // Dialog should remain open regardless of mode toggles
        assertEquals(targetButton, viewModel.learningDialogButton.value)

        // Concurrent toggling stress
        runBlocking {
            val toggleJobs = (1..50).map {
                async(Dispatchers.Default) {
                    viewModel.toggleLearningMode()
                }
            }
            toggleJobs.awaitAll()
        }

        // Dialog must STILL be open
        assertEquals(targetButton, viewModel.learningDialogButton.value)

        // Explicit setLearningMode(false) should also NOT prematurely close the active dialog
        viewModel.setLearningMode(false)
        assertFalse(viewModel.isLearningMode.value)
        assertEquals(targetButton, viewModel.learningDialogButton.value)

        // Explicit dismissLearningDialog closes the dialog
        viewModel.dismissLearningDialog()
        assertNull(viewModel.learningDialogButton.value)
    }

    @Test
    fun challengeSingleButton_immediateDismissCancelsListeningAndResetsToIdle() {
        val profile = createSampleProfile()
        val viewModel = tracked(RemoteControlViewModel(
            remoteId = "adversarial_remote_1",
            learnerManager = learnerManager,
            transmitterManager = transmitterManager,
            initialProfile = profile
        ))

        val targetButton = profile.buttons[0]
        viewModel.startLearningButton(targetButton)

        // Start listening with long delay
        learnerManager.mockLearner.setSimulatedDelayMs(5000L)
        val listenJob = CoroutineScope(Dispatchers.IO).launch {
            learnerManager.startLearning(timeoutSeconds = 30)
        }

        // Wait until listening state is active
        val isListening = awaitCondition { learnerManager.state.value is LearningState.Listening }
        assertTrue(isListening)

        // Immediately dismiss dialog
        viewModel.dismissLearningDialog()

        // Verify dialog is closed
        assertNull(viewModel.learningDialogButton.value)

        // Verify learner state immediately cancels and returns to Idle
        val isIdle = awaitCondition { learnerManager.state.value == LearningState.Idle }
        assertTrue(isIdle)
        assertEquals(LearningState.Idle, learnerManager.state.value)

        listenJob.cancel()
    }

    @Test
    fun challengeSingleButton_rapidTestTransmitSpammingNoDeadlocks() {
        val profile = createSampleProfile()
        val viewModel = tracked(RemoteControlViewModel(
            remoteId = "adversarial_remote_1",
            learnerManager = learnerManager,
            transmitterManager = transmitterManager,
            initialProfile = profile
        ))

        val testSignal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560, 560, 1690))

        // Spam testTransmitSignal 30 times in rapid concurrent succession
        runBlocking {
            val transmitJobs = (1..30).map {
                async(Dispatchers.Default) {
                    viewModel.testTransmitSignal(testSignal)
                }
            }
            transmitJobs.awaitAll()
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Await all transmits to be processed by transmitter on IO thread
        val logRecorded = awaitCondition(timeoutMs = 3000L) {
            transmitterManager.lastTransmitLog.value != null
        }
        assertTrue(logRecorded)

        // Verify no deadlock: VM is not stuck in transmitting state
        assertFalse(viewModel.isTransmitting.value)
        assertNotNull(transmitterManager.lastTransmitLog.value)
        assertTrue(transmitterManager.lastTransmitLog.value!!.isSuccess)
        // Verify transmitter recorded transmissions without crash
        assertTrue(transmitterManager.mockTransmitter.transmitCount.value > 0)
    }

    @Test
    fun challengeWizardAndSingleButton_testTransmitHardwareFailureResilience() {
        // Set mock transmitter to fail
        transmitterManager.mockTransmitter.shouldFail = true
        transmitterManager.mockTransmitter.failureReason = "IR blaster busy or disconnected"

        val profile = createSampleProfile()
        val remoteVm = tracked(RemoteControlViewModel(
            remoteId = "adversarial_remote_1",
            learnerManager = learnerManager,
            transmitterManager = transmitterManager,
            initialProfile = profile
        ))

        val testSignal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560))

        // 1. RemoteControlViewModel testTransmitSignal should not throw unhandled exception
        remoteVm.testTransmitSignal(testSignal)
        testDispatcher.scheduler.advanceUntilIdle()

        val logRecorded = awaitCondition { transmitterManager.lastTransmitLog.value != null }
        assertTrue(logRecorded)

        val log = transmitterManager.lastTransmitLog.value
        assertNotNull(log)
        assertFalse(log!!.isSuccess)
        assertEquals("IR blaster busy or disconnected", log.errorMessage)

        // 2. CustomRemoteWizardViewModel testTransmitCurrentKey failure feedback
        val wizardVm = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            transmitterManager = transmitterManager,
            initialCategory = DeviceCategory.TV
        ))
        wizardVm.setRemoteName("发射失败测试")
        wizardVm.proceedToLearning()

        learnerManager.mockLearner.triggerManualSignal(testSignal)
        wizardVm.startLearningCurrentKey()
        awaitCondition { wizardVm.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED }

        wizardVm.testTransmitCurrentKey()
        testDispatcher.scheduler.advanceUntilIdle()

        val feedbackReceived = awaitCondition { wizardVm.uiState.value.testFeedbackMessage != null }
        assertTrue(feedbackReceived)

        // VERIFY DEFECT REMEDIATION:
        // transmitterManager.transmit(signal) returns Result<Unit> encapsulating the failure.
        // CustomRemoteWizardViewModel properly invokes .getOrThrow(), catches the failure,
        // and updates testFeedbackMessage to indicate failure instead of falsely reporting success.
        assertFalse(wizardVm.uiState.value.isTestingTransmit)
        val feedback = wizardVm.uiState.value.testFeedbackMessage
        assertNotNull(feedback)
        assertNotEquals(
            "CustomRemoteWizardViewModel must NOT swallow Result.failure as success",
            UiText.Resource(R.string.screen_test_sent),
            feedback
        )
        assertEquals(
            "testFeedbackMessage should report transmission failure with reason",
            UiText.resource(R.string.screen_send_error_t, UiText.Dynamic("IR blaster busy or disconnected")),
            feedback
        )
    }

    @Test
    fun challengeSingleButton_overwriteButtonSignalIntegrity() {
        val profile = createSampleProfile()
        val viewModel = tracked(RemoteControlViewModel(
            remoteId = "adversarial_remote_1",
            learnerManager = learnerManager,
            transmitterManager = transmitterManager,
            initialProfile = profile
        ))

        val powerBtn = profile.buttons[0]
        assertEquals(SignalType.NEC, powerBtn.signalType)
        assertEquals(0x00FF, powerBtn.necAddress)
        assertEquals(0x45, powerBtn.necCommand)

        viewModel.startLearningButton(powerBtn)

        val learnedRaw = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560, 560, 1690))
        viewModel.saveLearnedButton(powerBtn, learnedRaw)
        testDispatcher.scheduler.advanceUntilIdle()

        // Dialog should be dismissed
        assertNull(viewModel.learningDialogButton.value)

        // Power button in profile must be updated to RAW with cleared NEC fields
        val updatedPower = viewModel.remoteProfile.value?.buttons?.find { it.id == "btn_power" }
        assertNotNull(updatedPower)
        assertEquals(SignalType.RAW, updatedPower?.signalType)
        assertEquals(38000, updatedPower?.frequencyHz)
        assertEquals(listOf(9000, 4500, 560, 560, 560, 1690), updatedPower?.rawPattern)
        assertNull(updatedPower?.necAddress)
        assertNull(updatedPower?.necCommand)

        // Other buttons in profile must remain untouched
        val volUp = viewModel.remoteProfile.value?.buttons?.find { it.id == "btn_vol_up" }
        assertNotNull(volUp)
        assertEquals(listOf(9000, 4500, 560, 560), volUp?.rawPattern)
    }
}
