package org.qinyan.rimoat

import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import androidx.lifecycle.ViewModelStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteControlViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = ViewModelStore()
    private var nextVm = 0
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { org.qinyan.rimoat.clearViewModelsAndAwait(stores, dispatcher); Dispatchers.resetMain() }

    private fun tracked(vm: RemoteControlViewModel) = vm.also { stores.put("vm${nextVm++}", it) }

    private class RecordingEncoder : AcSignalEncoder {
        val requests = mutableListOf<Pair<AcSettings, AcAction>>()
        var modes = AcMode.entries.toList()
        var unsupported = emptySet<AcAction>()
        override suspend fun supportsAction(protocol: Int, settings: AcSettings, action: AcAction) = action !in unsupported
        override suspend fun protocolFor(sourceRemoteId: String) = 27
        override suspend fun supportedModes(protocol: Int) = modes
        override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction): List<IrSignal.Raw> {
            assertEquals(27, protocol)
            delay(10) // Catch rapid clicks that read stale state before a previous send finishes.
            requests.add(settings to action)
            return listOf(IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560, 560)))
        }
    }
    private fun profile(settings: AcSettings = AcSettings()) = RemoteProfile("ac", "美的", category = DeviceCategory.AC,
        acProtocolId = 27, acSettings = settings)

    @Test fun stopDiscardsPendingSendsAndResumeAcceptsNewInput() {
        val encoder = RecordingEncoder()
        val sent = mutableListOf<IrSignal>()
        val vm = tracked(RemoteControlViewModel("ac", initialProfile = profile(), acSignalEncoder = encoder,
            onTransmitSignal = { sent.add(it) }))
        vm.increaseTemp(); vm.increaseTemp()
        dispatcher.scheduler.runCurrent() // First request is encoding, second is waiting for its lock.
        vm.onScreenStopped()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(sent.isEmpty())
        assertFalse(vm.isTransmitting.value)
        vm.increaseTemp()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(sent.isEmpty())
        vm.onScreenStarted(); vm.increaseTemp()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, sent.size)
        assertEquals(27, vm.acState.value.temperature)
    }

    @Test fun stoppingAfterAcceptedSendKeepsCommittedState() {
        val encoder = RecordingEncoder()
        lateinit var vm: RemoteControlViewModel
        vm = tracked(RemoteControlViewModel("ac", initialProfile = profile(), acSignalEncoder = encoder,
            onTransmitSignal = { vm.onScreenStopped() }))
        vm.increaseTemp(); vm.increaseTemp()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, encoder.requests.size)
        assertEquals(27, vm.acState.value.temperature)
        assertFalse(vm.isTransmitting.value)
    }

    @Test fun retainedControllerReloadsReplacedRemoteSettings() {
        val encoder = RecordingEncoder()
        val vm = tracked(RemoteControlViewModel("ac", initialProfile = profile(), acSignalEncoder = encoder))
        dispatcher.scheduler.advanceUntilIdle()
        // Same Activity ViewModel survives removal and restoring/importing this remote ID.
        vm.setProfileForTesting(profile(AcSettings(temperature = 19, mode = AcMode.HEAT)))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(19, vm.acState.value.temperature)
        vm.increaseTemp()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(20, encoder.requests.single().first.temperature)
        assertEquals(AcMode.HEAT, encoder.requests.single().first.mode)
    }

    @Test fun rapidTemperatureChangesCarryDifferentCompleteTargets() {
        val encoder = RecordingEncoder()
        val vm = tracked(RemoteControlViewModel("ac", initialProfile = profile(), acSignalEncoder = encoder))
        vm.increaseTemp(); vm.increaseTemp(); vm.decreaseTemp()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(27, 28, 27), encoder.requests.map { it.first.temperature })
        assertEquals(27, vm.acState.value.temperature)
    }

    @Test fun modeFanAndSwingRemainInputsWhenTemperatureChanges() {
        val encoder = RecordingEncoder()
        val vm = tracked(RemoteControlViewModel("ac", initialProfile = profile(), acSignalEncoder = encoder))
        vm.cycleAcMode(); vm.cycleAcFanSpeed(); vm.toggleAcSwing(); vm.increaseTemp()
        dispatcher.scheduler.advanceUntilIdle()
        val final = encoder.requests.last().first
        assertEquals(AcMode.HEAT, final.mode)
        assertEquals(AcFanSpeed.LOW, final.fanSpeed)
        assertTrue(final.isSwingOn)
        assertEquals(27, final.temperature)
    }

    @Test fun failedTransmissionDoesNotAdvanceDisplayedState() {
        val encoder = RecordingEncoder()
        val vm = tracked(RemoteControlViewModel("ac", initialProfile = profile(), acSignalEncoder = encoder,
            onTransmitSignal = { error("USB detached") }))
        vm.increaseTemp()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(26, vm.acState.value.temperature)
        assertEquals(
            UiText.resource(R.string.msg_send_incomplete, UiText.Resource(R.string.control_unknown_error)),
            vm.operationError.value
        )
        assertTrue(vm.operationError.value !is UiText.Dynamic)
        assertFalse(vm.isTransmitting.value)
    }

    @Test fun restoredSettingsAndSupportedModesAreRespected() {
        val encoder = RecordingEncoder().apply { modes = listOf(AcMode.COOL, AcMode.FAN) }
        val vm = tracked(RemoteControlViewModel("ac", initialProfile = profile(AcSettings(temperature = 30)), acSignalEncoder = encoder))
        vm.increaseTemp(); vm.cycleAcMode()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, encoder.requests.size)
        assertEquals(AcMode.FAN, vm.acState.value.mode)
        assertEquals(30, vm.acState.value.temperature)
    }

    @Test fun powerUsesExplicitOnOffActionsAndKeepsPreviousSettings() {
        val encoder = RecordingEncoder()
        val vm = tracked(RemoteControlViewModel("ac", initialProfile = profile(AcSettings(mode = AcMode.HEAT, temperature = 22)), acSignalEncoder = encoder))
        vm.toggleAcPower(); vm.toggleAcPower()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(AcAction.POWER_OFF, AcAction.POWER_ON), encoder.requests.map { it.second })
        assertTrue(vm.acState.value.powerOn)
        assertEquals(22, vm.acState.value.temperature)
        assertEquals(AcMode.HEAT, vm.acState.value.mode)
    }

    @Test fun staticLearnedAcCannotPretendToChangeTemperature() {
        val encoder = RecordingEncoder()
        val vm = tracked(RemoteControlViewModel("ac", initialProfile = profile().copy(useStaticAcButtons = true), acSignalEncoder = encoder))
        vm.increaseTemp()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(encoder.requests.isEmpty())
        assertEquals(26, vm.acState.value.temperature)
    }

    @Test fun learningTemperatureKeyKeepsDynamicControlsAndReplaysOnlyItsRecordedSignal() {
        val encoder = RecordingEncoder()
        val sent = mutableListOf<IrSignal>()
        val button = RemoteButton("temperature", AcAction.TEMP_UP.label, acAction = AcAction.TEMP_UP)
        val vm = tracked(RemoteControlViewModel("ac", initialProfile = profile().copy(buttons = listOf(button)),
            acSignalEncoder = encoder, onTransmitSignal = { sent += it }))
        val recorded = IrSignal.Raw(39500, intArrayOf(2400, 1200, 600))
        vm.saveLearnedButton(button, recorded)
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.remoteProfile.value!!.useStaticAcButtons)
        val learned = vm.remoteProfile.value!!.buttons.single()
        assertTrue(learned.isLearned)
        vm.sendButton(learned)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(recorded), sent)
        assertEquals(26, vm.acState.value.temperature)
        assertTrue(encoder.requests.isEmpty())
        vm.increaseTemp(); vm.cycleAcMode()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(27, vm.acState.value.temperature)
        assertEquals(AcMode.HEAT, vm.acState.value.mode)
        assertEquals(listOf(AcAction.TEMP_UP, AcAction.MODE), encoder.requests.map { it.second })
    }

    @Test fun learningAndMissingKeysDoNotEmitSignals() {
        val sent = mutableListOf<IrSignal>()
        val vm = tracked(RemoteControlViewModel("tv", onTransmitSignal = { sent.add(it) }))
        vm.sendButton(RemoteButton("num_1", "1"))
        val up = RemoteButton("up", "上", signalType = SignalType.NEC, necAddress = 1, necCommand = 2)
        vm.setLearningMode(true); vm.sendButton(up)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(sent.isEmpty())
        assertEquals(up, vm.learningDialogButton.value)
    }

    @Test fun greeFactoryFunctionsContainDistinctRealFrames() {
        val buttons = PresetRemotes.greeAc.buttons
        assertEquals(6, buttons.map { it.toIrSignal() }.toSet().size)
        assertTrue(buttons.all { it.rawPattern.size > 100 })
        assertEquals(184, PresetRemotes.greeAc.acProtocolId)
    }
    @Test fun unsupportedTemperatureDoesNotSendOrChangeStateAndModeStillWorks() {
        val encoder = RecordingEncoder().apply { unsupported = setOf(AcAction.TEMP_UP, AcAction.TEMP_DOWN) }
        val vm = tracked(RemoteControlViewModel("ac", initialProfile = profile(AcSettings(mode = AcMode.FAN)), acSignalEncoder = encoder))
        vm.increaseTemp()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(encoder.requests.isEmpty())
        assertEquals(26, vm.acState.value.temperature)
        assertEquals(
            UiText.resource(
                R.string.msg_send_incomplete,
                UiText.resource(R.string.msg_mode_no_temperature, UiText.Resource(R.string.control_fan))
            ),
            vm.operationError.value
        )
        vm.cycleAcMode()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AcMode.AUTO, vm.acState.value.mode)
    }

    @Test fun directChoiceSendsOnlyTheChosenStateAndKeepsUnrelatedFields() {
        val encoder = RecordingEncoder()
        val original = AcSettings(temperature = 22, fanSpeed = AcFanSpeed.HIGH, isSwingOn = true, timerMinutes = 60)
        val vm = tracked(RemoteControlViewModel("ac", initialProfile = profile(original), acSignalEncoder = encoder))
        vm.selectAcState(AcStateChoice.forAction(AcAction.MODE).single { it.index == AcMode.AUTO.ordinal })
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, encoder.requests.size)
        val sent = encoder.requests.single()
        assertEquals(AcAction.MODE, sent.second)
        assertEquals(AcMode.AUTO, sent.first.mode)
        assertEquals(22, sent.first.temperature)
        assertEquals(AcFanSpeed.HIGH, sent.first.fanSpeed)
        assertTrue(sent.first.isSwingOn)
        assertEquals(0, sent.first.timerMinutes)
    }

    @Test fun openingChoicesDoesNotSendAndUnsupportedModesAreExcluded() = runTest(dispatcher) {
        val encoder = RecordingEncoder().apply { modes = listOf(AcMode.COOL, AcMode.FAN) }
        val vm = tracked(RemoteControlViewModel("ac", initialProfile = profile(), acSignalEncoder = encoder))
        assertEquals(listOf("制冷", "送风"), vm.availableAcChoices(AcAction.MODE).map { it.label })
        assertTrue(encoder.requests.isEmpty())
        vm.selectAcState(AcStateChoice.forAction(AcAction.MODE).single { it.label == "制热" })
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(encoder.requests.isEmpty())
        assertEquals(AcMode.COOL, vm.acState.value.mode)
    }

    @Test fun directFanWindAndDisplayPreserveOtherStateAndFailedSendDoesNotCommit() {
        val encoder = RecordingEncoder()
        val vm = tracked(RemoteControlViewModel("ac", initialProfile = profile(), acSignalEncoder = encoder))
        for (action in listOf(AcAction.FAN, AcAction.WIND_DIRECTION, AcAction.TEMPERATURE_DISPLAY)) {
            vm.selectAcState(AcStateChoice.forAction(action)[2])
        }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, encoder.requests.size)
        assertEquals(AcFanSpeed.MEDIUM, vm.acState.value.fanSpeed)
        assertEquals(AcWindDirection.MIDDLE, vm.acState.value.windDirection)
        assertEquals(AcTemperatureDisplay.TARGET, vm.acState.value.temperatureDisplay)
        encoder.unsupported = setOf(AcAction.FAN)
        vm.selectAcState(AcStateChoice.forAction(AcAction.FAN)[3])
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, encoder.requests.size)
        assertEquals(AcFanSpeed.MEDIUM, vm.acState.value.fanSpeed)
    }

    @Test fun learnedAndLearningKeysHaveNoExplicitChoiceMenu() = runTest(dispatcher) {
        val encoder = RecordingEncoder()
        val vm = tracked(RemoteControlViewModel("ac", initialProfile = profile().copy(useStaticAcButtons = true), acSignalEncoder = encoder))
        assertTrue(vm.availableAcChoices(AcAction.MODE).isEmpty())
        vm.setProfileForTesting(profile())
        vm.setLearningMode(true)
        assertTrue(vm.availableAcChoices(AcAction.MODE).isEmpty())
        assertTrue(encoder.requests.isEmpty())
    }

    @Test fun operationErrorsKeepUiTextResourceIdentity() {
        val vm = tracked(RemoteControlViewModel("tv"))
        vm.sendButton(RemoteButton("num_1", "1"))
        assertEquals(
            org.qinyan.rimoat.i18n.UiText.Resource(R.string.msg_button_learn_first),
            vm.operationError.value
        )

        val acVm = tracked(RemoteControlViewModel("ac", initialProfile = profile(AcSettings(mode = AcMode.FAN)),
            acSignalEncoder = RecordingEncoder().apply { unsupported = setOf(AcAction.TEMP_UP) }))
        acVm.increaseTemp()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            org.qinyan.rimoat.i18n.UiText.resource(
                R.string.msg_send_incomplete,
                org.qinyan.rimoat.i18n.UiText.resource(
                    R.string.msg_mode_no_temperature,
                    org.qinyan.rimoat.i18n.UiText.Resource(R.string.control_fan)
                )
            ),
            acVm.operationError.value
        )

        val failingVm = tracked(RemoteControlViewModel("ac", initialProfile = profile(),
            acSignalEncoder = RecordingEncoder(), onTransmitSignal = { throw IllegalStateException() }))
        failingVm.increaseTemp()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            org.qinyan.rimoat.i18n.UiText.resource(
                R.string.msg_send_incomplete,
                org.qinyan.rimoat.i18n.UiText.Resource(R.string.control_unknown_error)
            ),
            failingVm.operationError.value
        )
    }

    @Test fun saveLearnedButtonCompletesPersistenceAfterViewModelCleared() = runTest(dispatcher) {
        val dir = java.nio.file.Files.createTempDirectory("remote-save-learned-").toFile()
        val context = object : android.content.ContextWrapper(null) {
            override fun getApplicationContext() = this
            override fun getFilesDir() = dir
            override fun getSystemService(name: String): Any? = null
        }
        val repoScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
        try {
            val repo = RemoteRepository(context, repoScope)
            repo.awaitLoaded()
            val button = RemoteButton("power", "电源", signalType = SignalType.NEC, necAddress = 0x10, necCommand = 0x20)
            val savedProfile = RemoteProfile("tv_save_clear", "客厅电视", category = DeviceCategory.TV, buttons = listOf(button))
            repo.saveRemote(savedProfile)

            val vm = tracked(RemoteControlViewModel(savedProfile.id, repository = repo, initialProfile = savedProfile))
            val learned = IrSignal.Raw(39500, intArrayOf(9000, 4500, 560, 1690))
            vm.startLearningButton(button)
            vm.saveLearnedButton(button, learned)
            // Leave screen immediately so ViewModel.onCleared() cancels viewModelScope while IO save is pending/running.
            stores.clear()

            withContext(Dispatchers.Default) {
                withTimeout(5_000) {
                    while (true) {
                        dispatcher.scheduler.advanceUntilIdle()
                        val updated = repo.getRemote(savedProfile.id)?.buttons?.single()
                        if (updated?.isLearned == true && updated.rawPattern == learned.patternMicros.toList()) break
                        delay(10)
                    }
                }
            }

            val persisted = repo.getRemote(savedProfile.id)!!.buttons.single()
            assertTrue(persisted.isLearned)
            assertEquals(SignalType.RAW, persisted.signalType)
            assertEquals(39500, persisted.frequencyHz)
            assertEquals(learned.patternMicros.toList(), persisted.rawPattern)
        } finally {
            repoScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            dir.deleteRecursively()
        }
    }

    @Test fun missingSignalEmitsLearnActionAndMissingAcEncoderEmitsReloadAction() {
        val emptyButton = RemoteButton("power", "电源")
        val remoteWithEmpty = RemoteProfile("tv_empty", "电视", category = DeviceCategory.TV, buttons = listOf(emptyButton))
        val tvVm = tracked(RemoteControlViewModel(remoteWithEmpty.id, initialProfile = remoteWithEmpty))
        tvVm.sendButton(emptyButton)
        val learnErr = tvVm.operationAppError.value
        assertNotNull(learnErr)
        assertEquals(UiText.Resource(R.string.msg_button_learn_first), learnErr!!.message)
        assertEquals(org.qinyan.rimoat.i18n.ErrorAction.Learn("power"), learnErr.action)
        assertTrue(tvVm.operationError.value !is UiText.Dynamic)
        tvVm.learnButtonById("power")
        assertEquals(emptyButton, tvVm.learningDialogButton.value)

        val acVm = tracked(RemoteControlViewModel("ac", initialProfile = profile(), acSignalEncoder = null))
        acVm.toggleAcPower()
        dispatcher.scheduler.advanceUntilIdle()
        val acErr = acVm.operationAppError.value
        assertNotNull(acErr)
        assertEquals(
            UiText.resource(R.string.msg_send_incomplete, UiText.Resource(R.string.msg_ac_state_library_unavailable)),
            acErr!!.message
        )
        assertEquals(org.qinyan.rimoat.i18n.ErrorAction.Reload, acErr.action)
        assertTrue(acVm.operationError.value !is UiText.Dynamic)
    }

}
