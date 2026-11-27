package org.qinyan.rimoat.ui.learning

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.ContextWrapper
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.data.ApplianceType
import org.qinyan.rimoat.data.ButtonKey
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.SignalType
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.ui.components.DpadDirection
import org.qinyan.rimoat.ui.components.genericDirectionButtons
import org.qinyan.rimoat.ui.components.tvStandardButtons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomRemoteWizardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val stores = ViewModelStore()
    private val trackedModels = mutableListOf<ViewModel>()
    private fun <T : ViewModel> tracked(vm: T): T = vm.also { stores.put("vm${trackedModels.size}", it); trackedModels.add(it) }
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

    private fun awaitCondition(timeoutMs: Long = 2000L, condition: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            testDispatcher.scheduler.advanceUntilIdle()
            if (condition()) return true
            Thread.sleep(10)
        }
        testDispatcher.scheduler.advanceUntilIdle()
        return condition()
    }

    @Test
    fun testInitialState() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.CUSTOM
        ))

        val state = viewModel.uiState.value
        assertEquals(WizardStep.SETUP, state.step)
        assertEquals(DeviceCategory.CUSTOM, state.category)
        assertTrue(state.remoteName.isEmpty())
        assertTrue(state.keyQueue.isNotEmpty())
        assertEquals(0, state.currentIndex)
        assertEquals(0, state.learnedCount)
        assertFalse(state.canFinishEarly)
    }

    @Test
    fun testCategoryChangeUpdatesKeyQueueOnSetupStep() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.CUSTOM
        ))

        viewModel.setCategory(DeviceCategory.TV)
        assertEquals(DeviceCategory.TV, viewModel.uiState.value.category)
        assertTrue(viewModel.uiState.value.keyQueue.any { it.key == ButtonKey.MUTE })

        viewModel.setCategory(DeviceCategory.AC)
        assertEquals(DeviceCategory.AC, viewModel.uiState.value.category)
        assertTrue(viewModel.uiState.value.keyQueue.any { it.key == ButtonKey.SWING })

        viewModel.setCategory(DeviceCategory.FAN)
        assertEquals(DeviceCategory.FAN, viewModel.uiState.value.category)
        assertTrue(viewModel.uiState.value.keyQueue.any { it.key == ButtonKey.OSCILLATION })

        viewModel.setCategory(DeviceCategory.PROJECTOR)
        assertEquals(DeviceCategory.PROJECTOR, viewModel.uiState.value.category)
        assertTrue(viewModel.uiState.value.keyQueue.any { it.key == ButtonKey.FOCUS_PLUS })
    }

    @Test
    fun testProceedToLearningValidation() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.TV
        ))

        // Blank name should fail
        viewModel.setRemoteName("   ")
        val proceedingFailed = viewModel.proceedToLearning()
        assertFalse(proceedingFailed)
        assertEquals(WizardStep.SETUP, viewModel.uiState.value.step)
        assertEquals(UiText.Resource(R.string.screen_enter_name), viewModel.uiState.value.errorMessage)

        // Valid name should succeed
        viewModel.setRemoteName("客厅索尼电视")
        val proceedingSucceeded = viewModel.proceedToLearning()
        assertTrue(proceedingSucceeded)
        assertEquals(WizardStep.LEARNING, viewModel.uiState.value.step)
        assertEquals(0, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun testStartAndCancelLearning() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.TV
        ))
        viewModel.setRemoteName("电视测试")
        viewModel.proceedToLearning()

        viewModel.startLearningCurrentKey()
        assertEquals(KeyLearnStatus.LEARNING, viewModel.uiState.value.currentKey?.status)

        viewModel.cancelCurrentKeyLearning()
        assertEquals(KeyLearnStatus.PENDING, viewModel.uiState.value.currentKey?.status)
    }

    @Test
    fun testCapturedStateUpdatesCurrentKey() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.TV
        ))
        viewModel.setRemoteName("电视测试")
        viewModel.proceedToLearning()

        val sampleSignal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560, 560, 1690))
        learnerManager.mockLearner.triggerManualSignal(sampleSignal)

        viewModel.startLearningCurrentKey()
        val captured = awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED }
        assertTrue(captured)

        val currentKey = viewModel.uiState.value.currentKey
        assertNotNull(currentKey)
        assertEquals(KeyLearnStatus.CAPTURED, currentKey?.status)
        assertNotNull(currentKey?.capturedSignal)
        assertEquals(6, currentKey?.pulseCount)
        assertTrue(currentKey!!.durationMs > 0)
    }

    @Test
    fun testConfirmKeyAdvancesQueueAndIncrementsLearnedCount() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.TV
        ))
        viewModel.setRemoteName("电视测试")
        viewModel.proceedToLearning()

        val sampleSignal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560, 560, 1690))
        learnerManager.mockLearner.triggerManualSignal(sampleSignal)

        viewModel.startLearningCurrentKey()
        val captured = awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED }
        assertTrue(captured)

        viewModel.confirmCurrentKey()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(1, viewModel.uiState.value.learnedCount)
        assertTrue(viewModel.uiState.value.canFinishEarly)
        assertEquals(KeyLearnStatus.LEARNED, viewModel.uiState.value.keyQueue[0].status)
    }

    @Test
    fun testSkipKeyAdvancesQueueWithoutLearning() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.TV
        ))
        viewModel.setRemoteName("电视测试")
        viewModel.proceedToLearning()

        viewModel.skipCurrentKey()

        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(0, viewModel.uiState.value.learnedCount)
        assertEquals(KeyLearnStatus.SKIPPED, viewModel.uiState.value.keyQueue[0].status)
    }

    @Test
    fun testAddCustomKey() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.CUSTOM
        ))
        val originalSize = viewModel.uiState.value.keyQueue.size

        viewModel.addCustomKey("  低音增强  ")
        val updatedQueue = viewModel.uiState.value.keyQueue
        assertEquals(originalSize + 1, updatedQueue.size)
        assertEquals("低音增强", updatedQueue.last().label)
    }

    @Test
    fun testFinishEarlyAndSaveRemote() {
        var createdProfile: RemoteProfile? = null

        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.TV
        ))
        viewModel.setRemoteName("主卧电视")
        viewModel.setBrand("TCL")
        viewModel.proceedToLearning()

        val sampleSignal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560, 560, 1690))
        learnerManager.mockLearner.triggerManualSignal(sampleSignal)

        viewModel.startLearningCurrentKey()
        val captured = awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED }
        assertTrue(captured)

        viewModel.confirmCurrentKey()
        testDispatcher.scheduler.advanceUntilIdle()

        // Finish early into REVIEW
        assertTrue(viewModel.uiState.value.canFinishEarly)
        viewModel.finishEarly()
        assertEquals(WizardStep.REVIEW, viewModel.uiState.value.step)

        viewModel.saveRemote { profile ->
            createdProfile = profile
        }
        assertTrue(awaitCondition { createdProfile != null })

        assertNotNull(createdProfile)
        assertEquals("主卧电视", createdProfile?.name)
        assertEquals("TCL", createdProfile?.brand)
        assertEquals(DeviceCategory.TV, createdProfile?.category)
        assertEquals(1, createdProfile?.buttons?.size)

        val btn = createdProfile?.buttons?.firstOrNull()
        assertNotNull(btn)
        assertEquals(ButtonKey.POWER.name, btn?.label)
        assertEquals(ButtonKey.POWER.name, btn?.builtinLabel)
        assertEquals(ButtonKey.POWER, btn?.key)
        assertEquals(SignalType.RAW, btn?.signalType)
        assertEquals(38000, btn?.frequencyHz)
        assertEquals(listOf(9000, 4500, 560, 560, 560, 1690), btn?.rawPattern)
    }

    @Test
    fun testHandleBackStateTransitions() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.TV
        ))
        viewModel.setRemoteName("返回测试")

        // In SETUP -> back should return false
        assertFalse(viewModel.handleBack())

        // Proceed to LEARNING
        viewModel.proceedToLearning()
        assertEquals(WizardStep.LEARNING, viewModel.uiState.value.step)
        assertEquals(0, viewModel.uiState.value.currentIndex)

        // At index 0 in LEARNING -> back should return true and go to SETUP
        assertTrue(viewModel.handleBack())
        assertEquals(WizardStep.SETUP, viewModel.uiState.value.step)

        // Proceed to LEARNING and advance index
        viewModel.proceedToLearning()
        viewModel.skipCurrentKey()
        assertEquals(1, viewModel.uiState.value.currentIndex)

        // Returning to setup preserves progress in this session.
        assertTrue(viewModel.handleBack())
        assertEquals(WizardStep.SETUP, viewModel.uiState.value.step)
        viewModel.proceedToLearning()
        assertEquals(1, viewModel.uiState.value.currentIndex)
        assertEquals(KeyLearnStatus.SKIPPED, viewModel.uiState.value.keyQueue.first().status)

        // Add 1 learned key and finish early to REVIEW
        val sampleSignal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560, 560, 1690))
        learnerManager.mockLearner.triggerManualSignal(sampleSignal)
        viewModel.startLearningCurrentKey()
        awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED }
        viewModel.confirmCurrentKey()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.finishEarly()
        assertEquals(WizardStep.REVIEW, viewModel.uiState.value.step)

        // In REVIEW -> back should return true and go to LEARNING
        assertTrue(viewModel.handleBack())
        assertEquals(WizardStep.LEARNING, viewModel.uiState.value.step)
    }

    private fun createMockContext(): Context {
        return object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
        }
    }

    @Test
    fun testTransmitCurrentKey_success() {
        val transmitterManager = IrTransmitterManager(context = createMockContext())
        transmitterManager.mockTransmitter.simulateDelayMs = 0L

        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            transmitterManager = transmitterManager,
            initialCategory = DeviceCategory.TV
        ))
        viewModel.setRemoteName("测试遥控")
        viewModel.proceedToLearning()

        val sampleSignal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560))
        learnerManager.mockLearner.triggerManualSignal(sampleSignal)
        viewModel.startLearningCurrentKey()
        awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED }

        viewModel.testTransmitCurrentKey()
        val finished = awaitCondition { !viewModel.uiState.value.isTestingTransmit && viewModel.uiState.value.testFeedbackMessage != null }
        assertTrue(finished)

        val state = viewModel.uiState.value
        assertFalse(state.isTestingTransmit)
        assertEquals(UiText.Resource(R.string.screen_simulated_send), state.testFeedbackMessage)
    }

    @Test
    fun testTransmitCurrentKey_hardwareFailure() {
        val transmitterManager = IrTransmitterManager(context = createMockContext())
        transmitterManager.mockTransmitter.simulateDelayMs = 0L
        transmitterManager.mockTransmitter.shouldFail = true
        transmitterManager.mockTransmitter.failureReason = "Carrier frequency unsupported"

        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            transmitterManager = transmitterManager,
            initialCategory = DeviceCategory.TV
        ))
        viewModel.setRemoteName("测试失败遥控")
        viewModel.proceedToLearning()

        val sampleSignal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560))
        learnerManager.mockLearner.triggerManualSignal(sampleSignal)
        viewModel.startLearningCurrentKey()
        awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED }

        viewModel.testTransmitCurrentKey()
        val finished = awaitCondition { !viewModel.uiState.value.isTestingTransmit && viewModel.uiState.value.testFeedbackMessage != null }
        assertTrue(finished)

        val state = viewModel.uiState.value
        assertFalse(state.isTestingTransmit)
        assertEquals(
            UiText.resource(R.string.screen_send_error_t, UiText.Resource(R.string.screen_hardware_error)),
            state.testFeedbackMessage
        )
    }

    @Test
    fun testTransmitCurrentKey_nullTransmitterManager() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            transmitterManager = null,
            initialCategory = DeviceCategory.TV
        ))
        viewModel.setRemoteName("无发射器遥控")
        viewModel.proceedToLearning()

        val sampleSignal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560))
        learnerManager.mockLearner.triggerManualSignal(sampleSignal)
        viewModel.startLearningCurrentKey()
        awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED }

        viewModel.testTransmitCurrentKey()
        val finished = awaitCondition { !viewModel.uiState.value.isTestingTransmit && viewModel.uiState.value.testFeedbackMessage != null }
        assertTrue(finished)

        val state = viewModel.uiState.value
        assertFalse(state.isTestingTransmit)
        assertEquals(
            UiText.resource(R.string.screen_send_error_t, UiText.Resource(R.string.screen_transmitter_unavailable)),
            state.testFeedbackMessage
        )
    }

    @Test
    fun testSaveRemote_finishesSessionAndRejectsSecondSave() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.TV
        ))
        viewModel.setRemoteName("客厅旧电视")
        viewModel.proceedToLearning()

        val sampleSignal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560, 560, 560))
        learnerManager.mockLearner.triggerManualSignal(sampleSignal)
        viewModel.startLearningCurrentKey()
        awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED }
        viewModel.confirmCurrentKey()

        viewModel.finishEarly()
        assertEquals(WizardStep.REVIEW, viewModel.uiState.value.step)

        var savedProfile: RemoteProfile? = null
        viewModel.saveRemote { profile ->
            savedProfile = profile
        }

        val finished = awaitCondition { savedProfile != null }
        assertTrue("Remote should be saved", finished)
        assertNotNull(savedProfile)

        var savedAgain = false
        viewModel.saveRemote { savedAgain = true }
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(savedAgain)
    }

    @Test
    fun testExplicitReset() {
        val viewModel = tracked(CustomRemoteWizardViewModel(
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.TV
        ))
        viewModel.setRemoteName("测试重置")
        viewModel.proceedToLearning()
        assertEquals(WizardStep.LEARNING, viewModel.uiState.value.step)

        viewModel.reset(DeviceCategory.FAN)
        val state = viewModel.uiState.value
        assertEquals(WizardStep.SETUP, state.step)
        assertEquals(DeviceCategory.FAN, state.category)
        assertEquals("", state.remoteName)
        assertEquals(0, state.currentIndex)
    }
    @Test fun cancellationAndBackKeepPreviouslyLearnedKeys() {
        val vm = tracked(CustomRemoteWizardViewModel(learnerManager = learnerManager))
        vm.setRemoteName("学习结果")
        vm.proceedToLearning()
        vm.startLearningCurrentKey()
        assertTrue(awaitCondition { vm.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED })
        val signal = vm.uiState.value.currentKey!!.capturedSignal
        vm.onScreenStopped()
        assertEquals(KeyLearnStatus.CAPTURED, vm.uiState.value.currentKey?.status)
        vm.confirmCurrentKey()
        vm.handleBack()
        vm.cancelCurrentKeyLearning()
        assertEquals(1, vm.uiState.value.learnedCount)
        assertEquals(signal, vm.uiState.value.keyQueue.first().capturedSignal)
        vm.handleBack()
        assertEquals(1, vm.uiState.value.learnedCount)
    }

    @Test fun skippedAndPendingKeysCannotBecomeLearnedWithoutSignal() {
        val vm = tracked(CustomRemoteWizardViewModel(learnerManager = learnerManager))
        vm.setRemoteName("跳过")
        vm.proceedToLearning()
        vm.confirmCurrentKey()
        assertEquals(0, vm.uiState.value.currentIndex)
        vm.skipCurrentKey()
        vm.selectKey(0)
        vm.confirmCurrentKey()
        assertEquals(1, vm.uiState.value.currentIndex)
        assertEquals(KeyLearnStatus.SKIPPED, vm.uiState.value.keyQueue.first().status)
        assertEquals(0, vm.uiState.value.learnedCount)
        var saved = false
        vm.saveRemote { saved = true }
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(saved)
    }

    @Test fun revisitingLearningKeepsSignalsUntilExplicitReset() {
        val vm = tracked(CustomRemoteWizardViewModel(learnerManager = learnerManager))
        vm.setRemoteName("重新学习")
        vm.proceedToLearning()
        vm.startLearningCurrentKey()
        assertTrue(awaitCondition { vm.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED })
        vm.confirmCurrentKey()
        vm.finishEarly()
        vm.returnToLearning()
        assertEquals(1, vm.uiState.value.learnedCount)
        vm.handleBack()
        vm.proceedToLearning()
        assertEquals(1, vm.uiState.value.learnedCount)
        vm.reset()
        assertEquals(0, vm.uiState.value.learnedCount)
        assertEquals(0, vm.uiState.value.currentIndex)
        assertTrue(vm.uiState.value.keyQueue.all { it.status == KeyLearnStatus.PENDING && it.capturedSignal == null })
    }

    @Test fun oldCaptureCannotPopulateANewWizardOrNewKey() {
        val first = tracked(CustomRemoteWizardViewModel(learnerManager = learnerManager))
        first.setRemoteName("第一个")
        first.proceedToLearning()
        first.startLearningCurrentKey()
        assertTrue(awaitCondition { first.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED })
        val second = tracked(CustomRemoteWizardViewModel(learnerManager = learnerManager))
        second.setRemoteName("第二个")
        second.proceedToLearning()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(KeyLearnStatus.PENDING, second.uiState.value.currentKey?.status)
        second.skipCurrentKey()
        assertEquals(KeyLearnStatus.PENDING, second.uiState.value.currentKey?.status)
        assertEquals(null, second.uiState.value.currentKey?.capturedSignal)
    }

    @Test fun leavingWhileListeningAllowsRetryAndDoesNotStayLearning() {
        learnerManager.mockLearner.setSimulatedDelayMs(10000)
        val vm = tracked(CustomRemoteWizardViewModel(learnerManager = learnerManager))
        vm.setRemoteName("旋转")
        vm.proceedToLearning()
        vm.startLearningCurrentKey()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onScreenStopped()
        assertEquals(KeyLearnStatus.PENDING, vm.uiState.value.currentKey?.status)
        learnerManager.mockLearner.setSimulatedDelayMs(0)
        vm.startLearningCurrentKey()
        assertTrue(awaitCondition { vm.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED })
    }

    @Test fun limitsMatchBackupAndPersistence() {
        val vm = tracked(CustomRemoteWizardViewModel())
        vm.setRemoteName("长".repeat(120))
        assertEquals(100, vm.uiState.value.remoteName.length)
        val initial = vm.uiState.value.keyQueue.size
        vm.addCustomKey("长".repeat(101))
        assertEquals(initial, vm.uiState.value.keyQueue.size)
        repeat(260) { vm.addCustomKey("按键$it") }
        assertEquals(256, vm.uiState.value.keyQueue.size)
        assertEquals(UiText.Resource(R.string.screen_key_limits), vm.uiState.value.errorMessage)
    }

    @Test fun startLearning_nullLearnerManager_setsLocalizedReceiverUnavailableError() {
        val vm = tracked(CustomRemoteWizardViewModel(learnerManager = null, initialCategory = DeviceCategory.TV))
        vm.setRemoteName("无接收器测试")
        vm.proceedToLearning()
        vm.startLearningCurrentKey()
        val finished = awaitCondition { vm.uiState.value.errorMessage != null }
        assertTrue(finished)
        assertEquals(KeyLearnStatus.PENDING, vm.uiState.value.currentKey?.status)
        assertEquals(
            UiText.resource(R.string.screen_learning_error_e, UiText.Resource(R.string.screen_receiver_unavailable)),
            vm.uiState.value.errorMessage
        )
    }

    @Test fun saveRemote_withoutLearnedKeys_setsNeedLearnedKeyUiText() {
        val vm = tracked(CustomRemoteWizardViewModel(learnerManager = learnerManager, initialCategory = DeviceCategory.TV))
        vm.setRemoteName("未学习按键测试")
        vm.proceedToLearning()
        var saved = false
        vm.saveRemote { saved = true }
        assertFalse(saved)
        assertEquals(UiText.Resource(R.string.screen_need_learned_key), vm.uiState.value.errorMessage)
    }

    @Test fun switchingTypeRequiresConfirmationAndKeepsSignalsWithNewDefaults() {
        val vm = tracked(CustomRemoteWizardViewModel(learnerManager = learnerManager, initialCategory = DeviceCategory.TV))
        vm.setRemoteName("Living room")
        assertTrue(vm.proceedToLearning())
        val powerSignal = learnCurrentKey(vm)
        val volumeSignal = learnCurrentKey(vm)
        vm.addCustomKey("Bedtime")
        val original = vm.uiState.value.keyQueue
        assertTrue(vm.handleBack())

        vm.setApplianceType(ApplianceType.AC)
        assertEquals(ApplianceType.TV, vm.uiState.value.selectedApplianceType)
        assertEquals(ApplianceType.AC, vm.uiState.value.pendingApplianceType)
        assertEquals(original, vm.uiState.value.keyQueue)
        assertFalse(vm.proceedToLearning())
        vm.cancelApplianceTypeChange()
        assertEquals(null, vm.uiState.value.pendingApplianceType)
        assertEquals(original, vm.uiState.value.keyQueue)

        vm.setApplianceType(ApplianceType.AC)
        vm.confirmApplianceTypeChange(keepLearnedKeys = true)
        val state = vm.uiState.value
        assertEquals(ApplianceType.AC, state.selectedApplianceType)
        assertEquals(null, state.pendingApplianceType)
        assertEquals(null, state.templateApplianceType)
        assertEquals(2, state.learnedCount)
        assertEquals(powerSignal, state.keyQueue[0].capturedSignal)
        assertEquals(volumeSignal, state.keyQueue[1].capturedSignal)
        assertEquals("Bedtime", state.keyQueue[2].label)
        assertEquals(9, state.keyQueue.size)
        assertEquals(null, state.keyQueue[0].acAction)
        assertEquals(6, state.keyQueue.count { it.acAction != null })
        assertTrue(state.keyQueue.none { it.key == ButtonKey.MUTE })

        var saved: RemoteProfile? = null
        vm.saveRemote { saved = it }
        assertTrue(awaitCondition { saved != null })
        assertFalse(saved!!.useStaticAcButtons)
        assertEquals(listOf(powerSignal.patternMicros.toList(), volumeSignal.patternMicros.toList()), saved!!.buttons.map { it.rawPattern })
    }

    @Test fun switchingTypeKeepsCapturedAndCustomKeysWithoutDuplicatingSharedDefaults() {
        val vm = tracked(CustomRemoteWizardViewModel(learnerManager = learnerManager, initialCategory = DeviceCategory.TV))
        vm.setRemoteName("Living room")
        assertTrue(vm.proceedToLearning())
        val signal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 1120))
        learnerManager.mockLearner.triggerManualSignal(signal)
        vm.startLearningCurrentKey()
        assertTrue(awaitCondition { vm.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED })
        vm.addCustomKey("Custom function")
        val captured = vm.uiState.value.currentKey
        assertTrue(vm.handleBack())
        vm.setApplianceType(ApplianceType.FAN)
        vm.confirmApplianceTypeChange(keepLearnedKeys = true)
        val state = vm.uiState.value
        assertEquals(ApplianceType.FAN, state.selectedApplianceType)
        assertEquals(captured, state.currentKey)
        assertEquals(1, state.keyQueue.count { it.key == ButtonKey.POWER })
        assertEquals(1, state.keyQueue.count { it.label == "Custom function" })
        assertEquals(6, state.keyQueue.size)
        assertEquals(signal, state.currentKey?.capturedSignal)
        assertEquals(KeyLearnStatus.CAPTURED, state.currentKey?.status)
    }

    @Test fun switchingTypeCanExplicitlyClearProgressAndUseTheNewTemplate() {
        val vm = tracked(CustomRemoteWizardViewModel(learnerManager = learnerManager, initialCategory = DeviceCategory.TV))
        vm.setRemoteName("Bedroom")
        assertTrue(vm.proceedToLearning())
        learnCurrentKey(vm)
        vm.addCustomKey("Custom function")
        assertTrue(vm.handleBack())
        vm.setApplianceType(ApplianceType.AC)
        vm.confirmApplianceTypeChange(keepLearnedKeys = false)
        val state = vm.uiState.value
        assertEquals(ApplianceType.AC, state.templateApplianceType)
        assertEquals(0, state.learnedCount)
        assertEquals(0, state.currentIndex)
        assertEquals(6, state.keyQueue.size)
        assertTrue(state.keyQueue.all { it.status == KeyLearnStatus.PENDING && it.capturedSignal == null && it.acAction != null })
        assertEquals("Bedroom", state.remoteName)
    }

    @Test fun switchingTypeWithoutProgressStillUpdatesDefaultQueueAfterReturningFromLearning() {
        val vm = tracked(CustomRemoteWizardViewModel(learnerManager = learnerManager, initialCategory = DeviceCategory.TV))
        vm.setRemoteName("No learned keys")
        assertTrue(vm.proceedToLearning())
        assertTrue(vm.handleBack())
        vm.setApplianceType(ApplianceType.CAMERA)
        assertEquals(ApplianceType.CAMERA, vm.uiState.value.selectedApplianceType)
        assertEquals(listOf(ButtonKey.SHUTTER, ButtonKey.SUB_SHUTTER), vm.uiState.value.keyQueue.map { it.key })
        assertEquals(null, vm.uiState.value.pendingApplianceType)
        assertEquals(0, vm.uiState.value.currentIndex)
    }

    @Test fun defaultQueuesPersistStableKeysForEveryApplianceType() {
        ApplianceType.entries.forEach { type ->
            val queue = CustomRemoteWizardViewModel.generateDefaultQueue(type)
            assertTrue(queue.isNotEmpty())
            assertEquals(queue.size, queue.map { it.key to it.acAction }.distinct().size)
            queue.forEach { item ->
                val key = requireNotNull(item.key)
                assertEquals(key.name, item.label)
                assertEquals(key.name, item.builtinLabel)
            }
        }
    }

    @Test fun acTemplateSavesLearnedSignalsWithTheirStandardActions() {
        val vm = tracked(CustomRemoteWizardViewModel(learnerManager = learnerManager, initialCategory = DeviceCategory.AC))
        vm.setRemoteName("Bedroom")
        assertTrue(vm.proceedToLearning())
        val actions = listOf(AcAction.POWER_ON, AcAction.TEMP_UP, AcAction.TEMP_DOWN, AcAction.MODE, AcAction.FAN, AcAction.SWING)
        assertEquals(actions, vm.uiState.value.keyQueue.map { it.acAction })
        val signals = actions.map { learnCurrentKey(vm) }
        var saved: RemoteProfile? = null
        vm.saveRemote { saved = it }
        assertTrue(awaitCondition { saved != null })
        assertTrue(saved!!.useStaticAcButtons)
        assertEquals(actions, saved!!.buttons.map { it.acAction })
        assertEquals(signals.map { it.patternMicros.toList() }, saved!!.buttons.map { it.rawPattern })
        assertTrue(saved!!.buttons.all { it.isLearned && it.key != null && it.builtinLabel == it.key?.name })
    }

    @Test fun defaultDirectionKeysKeepACompleteDpadAfterSaving() {
        listOf(DeviceCategory.PROJECTOR, DeviceCategory.AUDIO, DeviceCategory.CUSTOM, DeviceCategory.TV).forEach { category ->
            val buttons = CustomRemoteWizardViewModel.generateDefaultQueue(category).map { item ->
                RemoteButton(id = item.id, label = item.label, builtinLabel = item.builtinLabel, key = item.key)
            }
            val directions = genericDirectionButtons(buttons).values
            assertEquals(category.name, DpadDirection.entries.size, directions.size)
            assertEquals(category.name, DpadDirection.entries.toSet(), directions.mapNotNull { DpadDirection.from(it.key) }.toSet())
            assertEquals(
                category.name,
                buttons.filter { DpadDirection.from(it.key) != null }.map { it.id }.toSet(),
                directions.map { it.id }.toSet()
            )
        }
    }

    @Test fun savedTvDefaultsUseStableKeysForTheirStandardSlots() {
        val buttons = CustomRemoteWizardViewModel.generateDefaultQueue(ApplianceType.SET_TOP_BOX).map { item ->
            RemoteButton(id = item.id, label = item.label, builtinLabel = item.builtinLabel, key = item.key)
        }
        val profile = RemoteProfile(id = "learned_tv", name = "Living room", category = DeviceCategory.TV, buttons = buttons)
        val standard = tvStandardButtons(profile)
        assertEquals(
            listOf(ButtonKey.POWER, null, ButtonKey.MUTE, ButtonKey.UP, ButtonKey.DOWN, ButtonKey.LEFT,
                ButtonKey.RIGHT, ButtonKey.OK, ButtonKey.BACK, ButtonKey.HOME, ButtonKey.MENU,
                ButtonKey.VOLUME_UP, ButtonKey.VOLUME_DOWN, ButtonKey.CHANNEL_UP, ButtonKey.CHANNEL_DOWN),
            listOf(standard.power, standard.source, standard.mute, standard.up, standard.down, standard.left,
                standard.right, standard.ok, standard.back, standard.home, standard.menu,
                standard.volUp, standard.volDown, standard.chUp, standard.chDown).map { it?.key }
        )
        val misleading = RemoteButton(id = "tv_up", label = "UP", builtinLabel = ButtonKey.UP.name, key = ButtonKey.VOLUME_UP)
        val classified = tvStandardButtons(profile.copy(buttons = listOf(misleading)))
        assertEquals(1, classified.all.size)
        assertEquals(misleading, classified.volUp)
        assertEquals(null, classified.up)
    }

    @Test fun customStandardActionBindingIsExplicitAndDoesNotDuplicateTheSlot() {
        val vm = tracked(CustomRemoteWizardViewModel(learnerManager = learnerManager, initialCategory = DeviceCategory.AC))
        vm.addCustomKey("+")
        val custom = vm.uiState.value.keyQueue.last()
        assertEquals(null, custom.acAction)
        assertEquals(null, custom.key)
        assertTrue(vm.bindCustomKeyToAcAction(custom.id, AcAction.TEMP_UP))
        assertEquals(6, vm.uiState.value.keyQueue.size)
        val bound = vm.uiState.value.keyQueue.single { it.acAction == AcAction.TEMP_UP }
        assertEquals(custom.id, bound.id)
        assertEquals("+", bound.label)
        assertEquals(null, bound.builtinLabel)
        assertEquals(ButtonKey.TEMPERATURE_UP, bound.key)
        vm.setRemoteName("Custom AC")
        assertTrue(vm.proceedToLearning())
        vm.selectKey(vm.uiState.value.keyQueue.indexOf(bound))
        val signal = learnCurrentKey(vm)
        vm.addCustomKey("Another +")
        val other = vm.uiState.value.keyQueue.last()
        assertFalse(vm.bindCustomKeyToAcAction(other.id, AcAction.TEMP_UP))
        assertEquals(1, vm.uiState.value.keyQueue.count { it.acAction == AcAction.TEMP_UP })
        assertEquals(null, vm.uiState.value.keyQueue.last().acAction)
        assertEquals(signal, vm.uiState.value.keyQueue.single { it.acAction == AcAction.TEMP_UP }.capturedSignal)
    }

    @Test fun customActionBindingThroughAddKeepsTheUserNameWhenSaved() {
        val vm = tracked(CustomRemoteWizardViewModel(learnerManager = learnerManager, initialCategory = DeviceCategory.AC))
        vm.addCustomKey("My temperature button", AcAction.TEMP_UP)
        val target = vm.uiState.value.keyQueue.single { it.acAction == AcAction.TEMP_UP }
        vm.setRemoteName("Custom AC")
        assertTrue(vm.proceedToLearning())
        vm.selectKey(vm.uiState.value.keyQueue.indexOf(target))
        learnCurrentKey(vm)
        var saved: RemoteProfile? = null
        vm.saveRemote { saved = it }
        assertTrue(awaitCondition { saved != null })
        val button = saved!!.buttons.single()
        assertEquals("My temperature button", button.label)
        assertEquals(null, button.builtinLabel)
        assertEquals(AcAction.TEMP_UP, button.acAction)
        assertEquals(ButtonKey.TEMPERATURE_UP, button.key)
    }

    @Test fun learningFailureDetectsTimeoutByTypeInsteadOfMessageString() {
        val vm = tracked(CustomRemoteWizardViewModel(learnerManager = learnerManager, initialCategory = DeviceCategory.TV))
        vm.setRemoteName("Timeout Test")
        assertTrue(vm.proceedToLearning())
        learnerManager.mockLearner.setSimulationMode(org.qinyan.rimoat.hardware.learning.MockIrLearner.SimulationMode.TIMEOUT)
        learnerManager.mockLearner.setSimulatedDelayMs(10L)
        vm.startLearningCurrentKey()
        assertTrue(awaitCondition {
            val state = vm.uiState.value
            state.currentKey?.status != KeyLearnStatus.LEARNING && state.errorMessage != null
        })
        assertEquals(UiText.Resource(R.string.screen_learn_timeout), vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.errorMessage !is UiText.Dynamic)
    }

    private fun learnCurrentKey(vm: CustomRemoteWizardViewModel): IrSignal.Raw {
        val signal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560 + vm.uiState.value.currentIndex * 10))
        learnerManager.mockLearner.triggerManualSignal(signal)
        vm.startLearningCurrentKey()
        assertTrue(awaitCondition { vm.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED })
        vm.confirmCurrentKey()
        return signal
    }
}
