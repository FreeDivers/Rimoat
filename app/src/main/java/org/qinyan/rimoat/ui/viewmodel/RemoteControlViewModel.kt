package org.qinyan.rimoat.ui.viewmodel

import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.uiTextDetail
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedLabel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.RemoteRepository
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.TransmitLog
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.hardware.learning.LearningState
import org.qinyan.rimoat.data.AcSettings
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.data.AcSignalEncoder
import org.qinyan.rimoat.data.AcStateChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

typealias AcMode = org.qinyan.rimoat.data.AcMode
typealias AcFanSpeed = org.qinyan.rimoat.data.AcFanSpeed
typealias AcControlState = org.qinyan.rimoat.data.AcSettings

class RemoteControlViewModel(
    val remoteId: String,
    val repository: RemoteRepository? = null,
    val transmitterManager: IrTransmitterManager? = null,
    val learnerManager: IrLearnerManager? = null,
    initialProfile: RemoteProfile? = null,
    private val onTransmitSignal: ((IrSignal) -> Unit)? = null,
    private val acSignalEncoder: AcSignalEncoder? = null,
    private val commandExecutor: org.qinyan.rimoat.data.RemoteCommandExecutor? = null
) : ViewModel() {

    private val operationLifetime = SupervisorJob()
    private val operationScope = CoroutineScope(viewModelScope.coroutineContext + operationLifetime)

    private val _isLearningMode = MutableStateFlow(false)
    val isLearningMode: StateFlow<Boolean> = _isLearningMode.asStateFlow()

    private val _learningDialogButton = MutableStateFlow<RemoteButton?>(null)
    val learningDialogButton: StateFlow<RemoteButton?> = _learningDialogButton.asStateFlow()
    private val _capturedLearningSignal = MutableStateFlow<Pair<String, IrSignal.Raw>?>(null)
    val capturedLearningSignal: StateFlow<Pair<String, IrSignal.Raw>?> = _capturedLearningSignal.asStateFlow()

    fun toggleLearningMode() {
        _isLearningMode.value = !_isLearningMode.value
    }

    fun setLearningMode(enabled: Boolean) {
        _isLearningMode.value = enabled
    }

    fun savePanelLayout(layout: org.qinyan.rimoat.data.RemotePanelLayout) {
        operationScope.launch {
            try {
                repository?.updatePanelLayout(remoteId, layout)
                _manualProfile.value = _manualProfile.value?.copy(panelLayout = layout)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _operationError.value = AppStrings.get(R.string.msg_remote_save_failed, e.message ?: e.javaClass.simpleName) }
        }
    }

    private var learningSession = 0
    private val _learningError = MutableStateFlow<UiText?>(null)
    val learningError = _learningError.asStateFlow()

    fun startLearningButton(button: RemoteButton) {
        learningSession++
        _learningError.value = null
        _operationError.value = null
        _capturedLearningSignal.value = null
        _learningDialogButton.value = button
    }

    fun updateLearningCapture(buttonId: String, signal: IrSignal.Raw?) {
        if (_learningDialogButton.value?.id == buttonId) {
            _capturedLearningSignal.value = signal?.let { buttonId to it }
        }
    }

    fun dismissLearningDialog() {
        learnerManager?.cancel()
        learningSession++
        _learningError.value = null
        _operationError.value = null
        _capturedLearningSignal.value = null
        _learningDialogButton.value = null
    }

    private val _operationError = MutableStateFlow<String?>(null)
    val operationError = _operationError.asStateFlow()
    fun clearOperationError() { _operationError.value = null }
    private val _isSavingButton = MutableStateFlow(false)
    val isSavingButton = _isSavingButton.asStateFlow()

    fun saveLearnedButton(button: RemoteButton, signal: IrSignal.Raw) {
        if (_isSavingButton.value) return
        _isSavingButton.value = true
        val session = learningSession
        _learningError.value = null
        _operationError.value = null
        viewModelScope.launch {
            try {
                val updatedButton = button.withLearnedSignal(signal)
                repository?.updateButton(remoteId, updatedButton)
                _manualProfile.value?.let { profile ->
                    val buttons = profile.buttons.toMutableList()
                    val idx = buttons.indexOfFirst { it.id == updatedButton.id }
                    if (idx >= 0) buttons[idx] = updatedButton else buttons.add(updatedButton)
                    _manualProfile.value = profile.copy(buttons = buttons,
                        verifiedFunctions = profile.verifiedFunctions.filter { it != button.label })
                }
                if (session == learningSession) dismissLearningDialog()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (session == learningSession) _learningError.value = UiText.resource(
                    R.string.msg_captured_save_failed, e.uiTextDetail(R.string.control_unknown_error))
            }
            finally { _isSavingButton.value = false }
        }
    }

    fun testTransmitSignal(signal: IrSignal.Raw) {
        launchSend { beginTransmission ->
            sendMutex.withLock {
                transmitterManager?.transmitWithReceipt(listOf(signal), feedbackOwner,
                    beforeTransmission = { beginTransmission() })
            }
        }
    }

    private val _manualProfile = MutableStateFlow<RemoteProfile?>(initialProfile)

    val isRepositoryLoaded: StateFlow<Boolean> = repository?.isLoaded
        ?: MutableStateFlow(true).asStateFlow()

    val remoteProfile: StateFlow<RemoteProfile?> = if (repository != null) {
        repository.getRemoteFlow(remoteId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialProfile)
    } else {
        _manualProfile.asStateFlow()
    }

    val transmitterType: StateFlow<TransmitterType> = transmitterManager?.transmitterType
        ?: MutableStateFlow(TransmitterType.NONE).asStateFlow()
    val isHardwareReady: StateFlow<Boolean> = transmitterManager?.isReady
        ?: MutableStateFlow(false).asStateFlow()
    val isForceMock: StateFlow<Boolean> = transmitterManager?.forceMockMode
        ?: MutableStateFlow(false).asStateFlow()
    private val feedbackOwner = UUID.randomUUID().toString()
    private val _feedback = MutableStateFlow<TransmitLog?>(null)
    val lastTransmitLog = _feedback.asStateFlow()
    fun dismissFeedback(log: TransmitLog) { _feedback.compareAndSet(log, null) }
    fun clearFeedback() { _feedback.value = null; _operationError.value = null }
    fun onVisible() {
        clearFeedback()
        viewModelScope.launch {
            try { repository?.markUsed(remoteId) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _operationError.value = AppStrings.get(R.string.msg_recent_history_save_failed, "最近使用记录保存失败") }
        }
    }

    private val sendGateLock = Any()
    private var screenActive = true
    private val pendingSends = mutableSetOf<Job>()
    private val acceptedSends = mutableSetOf<Job>()

    fun onScreenStarted() {
        synchronized(sendGateLock) { screenActive = true }
    }

    fun onScreenStopped() {
        val jobsToCancel = synchronized(sendGateLock) {
            screenActive = false
            pendingSends.toList().also { pendingSends.clear() }
        }
        jobsToCancel.forEach { it.cancel() }
        clearFeedback()
    }

    override fun onCleared() {
        onScreenStopped()
        // Let submitted layout edits and accepted transmissions finish saving.
        operationLifetime.complete()
    }

    /** Cancel waiting/encoding jobs on exit; an accepted transmission keeps its state commit. */
    private fun launchSend(block: suspend (beginTransmission: () -> Unit) -> Unit) {
        synchronized(sendGateLock) { if (!screenActive) return }
        val job = operationScope.launch {
            val self = checkNotNull(kotlin.coroutines.coroutineContext[Job])
            synchronized(sendGateLock) {
                if (!screenActive) return@launch
                if (self !in acceptedSends) pendingSends.add(self)
            }
            try {
                block {
                    synchronized(sendGateLock) {
                        if (!screenActive) throw CancellationException("Screen stopped")
                        self.ensureActive()
                        pendingSends.remove(self)
                        acceptedSends.add(self)
                    }
                }
            } finally {
                synchronized(sendGateLock) {
                    pendingSends.remove(self)
                    acceptedSends.remove(self)
                }
            }
        }
        synchronized(sendGateLock) {
            if (screenActive && job.isActive && job !in acceptedSends) pendingSends.add(job)
            else if (!screenActive && job !in acceptedSends) job.cancel()
        }
    }

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _lastSentButton = MutableStateFlow<RemoteButton?>(null)
    val lastSentButton: StateFlow<RemoteButton?> = _lastSentButton.asStateFlow()

    private val _acState = MutableStateFlow(initialProfile?.acSettings ?: AcControlState())
    private val _demoState = MutableStateFlow<AcControlState?>(null)
    val isDemonstrating = transmitterType.combine(isForceMock) { type, forced -> type == TransmitterType.MOCK || forced }
        .stateIn(viewModelScope, SharingStarted.Eagerly, transmitterType.value == TransmitterType.MOCK)
    val acState: StateFlow<AcControlState> = combine(_acState, _demoState, isDemonstrating,
        commandExecutor?.journal?.records ?: MutableStateFlow(emptyMap<String, org.qinyan.rimoat.data.RemoteCommandRecord>())) { actual, demo, simulated, records ->
        val shared = records[remoteId]?.takeIf { it.demoBase == actual }?.demoSettings
        if (simulated) shared ?: demo ?: actual else actual
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _acState.value)

    fun setProfileForTesting(profile: RemoteProfile) {
        _manualProfile.value = profile
    }

    private val sendMutex = Mutex()
    private val pressCounts = mutableMapOf<String, Int>()
    private val _availableAcActions = MutableStateFlow<Set<AcAction>>(emptySet())
    val availableAcActions = _availableAcActions.asStateFlow()
    private val _acUnavailableReasons = MutableStateFlow<Map<AcAction, org.qinyan.rimoat.data.AcUnavailableReason>>(emptyMap())
    val acUnavailableReasons = _acUnavailableReasons.asStateFlow()
    private var loadedSettings = initialProfile?.acSettings

    init {
        viewModelScope.launch {
            transmitterManager?.lastTransmitLog?.collect { log ->
                val active = synchronized(sendGateLock) { screenActive }
                if (active && log?.ownerId == feedbackOwner) _feedback.value = log
            }
        }
        viewModelScope.launch {
            transmitterType.collect { _demoState.value = null; clearFeedback() }
        }
        viewModelScope.launch {
            remoteProfile.map { it?.acSettings }.distinctUntilChanged().collect { settings ->
                if (settings != loadedSettings) {
                    loadedSettings = settings
                    sendMutex.withLock {
                        _demoState.value = null
                        _acState.value = remoteProfile.value?.acSettings ?: AcControlState()
                        pressCounts.clear()
                    }
                }
            }
        }
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(remoteProfile, acState, isHardwareReady) { profile, state, ready ->
                Triple(profile?.takeUnless { it.useStaticAcButtons }?.acProtocolId, state, ready)
            }.distinctUntilChanged().collect { (protocol, state, ready) ->
                if (protocol != null && acSignalEncoder != null) {
                    try {
                        val reasons = AcAction.entries.mapNotNull { action ->
                            val reason = acSignalEncoder.unavailableReason(protocol, state, action)
                                ?: if (!ready && transmitterManager != null) org.qinyan.rimoat.data.AcUnavailableReason.HARDWARE_UNAVAILABLE else null
                            reason?.let { action to it }
                        }.toMap()
                        _acUnavailableReasons.value = reasons
                        _availableAcActions.value = AcAction.entries.filter { it !in reasons }.toSet()
                    }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        _availableAcActions.value = emptySet()
                        _acUnavailableReasons.value = AcAction.entries.associateWith { org.qinyan.rimoat.data.AcUnavailableReason.CAPABILITY_UNAVAILABLE }
                        _operationError.value = AppStrings.get(R.string.msg_ac_functions_load_failed, "读取空调功能失败：%1\$s", (e.localizedMessage ?: e.javaClass.simpleName))
                    }
                } else {
                    _availableAcActions.value = emptySet()
                    _acUnavailableReasons.value = AcAction.entries.associateWith { org.qinyan.rimoat.data.AcUnavailableReason.CAPABILITY_UNAVAILABLE }
                }
            }
        }
    }

    fun repeatButton(button: RemoteButton) {
        if (!_isTransmitting.value && !_isLearningMode.value) sendButton(button)
    }

    fun sendButton(button: RemoteButton) {
        if (_isLearningMode.value) { startLearningButton(button); return }
        if (button.acAction != null && !button.isLearned && remoteProfile.value?.useStaticAcButtons == false) {
            performAcAction(button.acAction); return
        }
        if (!button.hasSignal) { _operationError.value = AppStrings.get(R.string.msg_button_learn_first, "此按键尚未录入信号，请先学习"); return }
        launchSend { beginTransmission ->
            sendMutex.withLock {
                _lastSentButton.value = button
                _isTransmitting.value = true
                try {
                    if (commandExecutor != null) {
                        val result = commandExecutor.executeButton(remoteId, button.id, feedbackOwner,
                            beforeTransmission = { beginTransmission() })
                        result?.warning?.let { _operationError.value = it }
                        return@withLock
                    }
                    val countKey = "${if (transmitterManager?.currentTransmitterType() == TransmitterType.MOCK) "demo" else "real"}:${button.id}"
                    val index = pressCounts[countKey] ?: 0
                    val signal = button.toIrSignal(index)
                    if (transmitterManager != null) {
                        transmitterManager.transmitWithReceipt(listOf(signal), feedbackOwner,
                            beforeTransmission = { beginTransmission() }).getOrThrow()
                    } else { beginTransmission(); onTransmitSignal?.invoke(signal) }
                    pressCounts[countKey] = (index + 1) and 1
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { _operationError.value = AppStrings.get(R.string.msg_send_failed, "发送失败：%1\$s", (e.localizedMessage ?: e.javaClass.simpleName)) }
                finally { _isTransmitting.value = false }
            }
        }
    }

    private fun changeAc(transform: suspend (AcControlState, Int) -> Pair<AcControlState, AcAction>?) {
        if (_isLearningMode.value) { _operationError.value = AppStrings.get(R.string.msg_tap_button_to_learn, "请点击要学习的功能按键"); return }
        launchSend { beginTransmission ->
            sendMutex.withLock {
                _isTransmitting.value = true
                try {
                    if (commandExecutor != null) {
                        val result = commandExecutor.changeAc(remoteId, owner = feedbackOwner,
                            beforeTransmission = { beginTransmission() },
                            transform = transform)
                        result?.settings?.let { if (result.simulated) _demoState.value = it else _acState.value = it }
                        result?.warning?.let { _operationError.value = it }
                        return@withLock
                    }
                    val profile = remoteProfile.value ?: error(AppStrings.get(R.string.msg_remote_not_loaded, "遥控器未加载"))
                    val encoder = acSignalEncoder ?: error(AppStrings.get(R.string.msg_ac_state_library_unavailable, "空调状态码库不可用"))
                    check(!profile.useStaticAcButtons) { AppStrings.get(R.string.msg_learned_ac_use_panel, "学习的空调按键请在按键面板发送") }
                    val protocol = profile.acProtocolId ?: error(AppStrings.get(R.string.msg_independent_codes_no_state, "该遥控器使用独立按键码，不支持完整状态调节"))
                    val route = transmitterManager?.currentTransmitterType()
                    val base = if (route == TransmitterType.MOCK) _demoState.value ?: _acState.value else _acState.value
                    val (transformed, action) = transform(base, protocol) ?: return@withLock
                    val next = if (action == AcAction.TIMER) transformed else transformed.copy(timerMinutes = 0)
                    check(encoder.supportsAction(protocol, next, action)) {
                        if (action == AcAction.TEMP_UP || action == AcAction.TEMP_DOWN)
                            AppStrings.get(R.string.msg_mode_no_temperature, "该方案在%1\$s模式下不支持调温，请切换模式", localizedLabel(next.mode.labelCn))
                        else AppStrings.get(R.string.msg_action_unsupported, "该方案不支持当前模式下的此操作")
                    }
                    val signals = encoder.encode(protocol, next, action)
                    check(signals.isNotEmpty()) { AppStrings.get(R.string.msg_ac_no_signals, "没有可发送的空调信号") }
                    val simulated = if (transmitterManager != null) {
                        transmitterManager.transmitWithReceipt(signals, feedbackOwner, route,
                            beforeTransmission = { beginTransmission() }).getOrThrow().simulated
                    } else {
                        beginTransmission()
                        signals.forEach { onTransmitSignal?.invoke(it) }; false
                    }
                    val committed = next.copy(counter = (next.counter + signals.size) and 1)
                    if (simulated) {
                        _demoState.value = committed
                    } else {
                        _acState.value = committed
                        try { repository?.updateAcSettings(remoteId, committed) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { _operationError.value = AppStrings.get(R.string.msg_sent_settings_save_failed, "信号已发送，但设置保存失败：%1\$s", (e.localizedMessage ?: e.javaClass.simpleName)) }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { _operationError.value = AppStrings.get(R.string.msg_send_incomplete, "未完成发送：%1\$s", (e.localizedMessage ?: e.javaClass.simpleName)) }
                finally { _isTransmitting.value = false }
            }
        }
    }

    fun resendAcSettings() = changeAc { state, _ ->
        state to if (state.powerOn) AcAction.POWER_ON else AcAction.POWER_OFF
    }

    fun applyAcPreset(settings: AcSettings) = changeAc { state, protocol ->
        if (settings.powerOn) check(settings.mode in acSignalEncoder!!.supportedModes(protocol)) { AppStrings.get(R.string.msg_mode_unsupported, "该方案不支持此模式") }
        settings.copy(counter = state.counter) to if (settings.powerOn) AcAction.POWER_ON else AcAction.POWER_OFF
    }

    fun toggleAcPower() = changeAc { state, _ ->
        val on = !state.powerOn
        state.copy(powerOn = on) to if (on) AcAction.POWER_ON else AcAction.POWER_OFF
    }

    fun increaseTemp() = changeAc { state, _ ->
        if (state.temperature >= 30) null
        else state.copy(temperature = state.temperature + 1, powerOn = true) to AcAction.TEMP_UP
    }

    fun decreaseTemp() = changeAc { state, _ ->
        if (state.temperature <= 16) null
        else state.copy(temperature = state.temperature - 1, powerOn = true) to AcAction.TEMP_DOWN
    }

    fun cycleAcMode() = changeAc { state, protocol ->
        val modes = acSignalEncoder!!.supportedModes(protocol)
        check(modes.isNotEmpty()) { AppStrings.get(R.string.msg_model_no_modes, "该机型没有可用模式") }
        state.copy(mode = modes[(modes.indexOf(state.mode) + 1) % modes.size], powerOn = true) to AcAction.MODE
    }

    fun cycleAcFanSpeed() = changeAc { state, _ ->
        state.copy(fanSpeed = AcFanSpeed.entries[(state.fanSpeed.ordinal + 1) % AcFanSpeed.entries.size],
            powerOn = true) to AcAction.FAN
    }

    fun toggleAcSwing() = changeAc { state, _ ->
        state.apply(AcAction.SWING) to AcAction.SWING
    }

    suspend fun availableAcChoices(action: AcAction): List<AcStateChoice> {
        try {
        val profile = remoteProfile.value ?: return emptyList()
        if (profile.useStaticAcButtons || _isLearningMode.value) return emptyList()
        val protocol = profile.acProtocolId ?: return emptyList()
        val encoder = acSignalEncoder ?: return emptyList()
        val modes = if (action == AcAction.MODE) encoder.supportedModes(protocol) else emptyList()
        val state = acState.value
        return AcStateChoice.forAction(action).filter { choice ->
            val target = choice.applyTo(state)
            (action != AcAction.MODE || target.mode in modes) && encoder.supportsAction(protocol, target, action)
        }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _operationError.value = AppStrings.get(R.string.msg_states_load_failed, "读取可选状态失败：%1\$s", (e.localizedMessage ?: e.javaClass.simpleName)); return emptyList() }
    }

    fun selectAcState(choice: AcStateChoice) = changeAc { state, protocol ->
        val target = choice.applyTo(state)
        if (choice.action == AcAction.MODE) check(target.mode in acSignalEncoder!!.supportedModes(protocol)) {
            AppStrings.get(R.string.msg_model_mode_unsupported, "该机型不支持此模式")
        }
        target to choice.action
    }

    fun performAcAction(action: AcAction, timerMinutes: Int = 0) = changeAc { state, protocol ->
        val next = state.apply(action, timerMinutes)
        check(action != AcAction.TIMER || org.qinyan.rimoat.data.AcTimerOptions.supports(protocol, next)) {
            AppStrings.get(R.string.msg_timer_unsupported, "该方案不支持此定时时长")
        }
        if (action == AcAction.QUICK_COOL || action == AcAction.QUICK_HEAT)
            check(next.mode in acSignalEncoder!!.supportedModes(protocol)) { AppStrings.get(R.string.msg_model_mode_unsupported, "该机型不支持此模式") }
        next to action
    }

    fun toggleForceMock() {
        val current = transmitterManager?.forceMockMode?.value ?: return
        transmitterManager.setForceMockMode(!current)
    }
}

