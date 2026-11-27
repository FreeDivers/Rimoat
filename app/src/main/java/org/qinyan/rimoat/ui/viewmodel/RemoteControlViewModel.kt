package org.qinyan.rimoat.ui.viewmodel

import org.qinyan.rimoat.i18n.AppError
import org.qinyan.rimoat.i18n.ErrorAction
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.UiTextException
import org.qinyan.rimoat.i18n.toAppError
import org.qinyan.rimoat.i18n.wrapped
import org.qinyan.rimoat.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.qinyan.rimoat.data.withoutVerifiedButtons
import org.qinyan.rimoat.i18n.labelRes
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
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
    private val panelSaveLock = Any()
    private var pendingPanelLayout: org.qinyan.rimoat.data.RemotePanelLayout? = null
    private var panelSaveActive = false

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

    private val _panelSaveError = MutableStateFlow<UiText?>(null)
    val panelSaveError: StateFlow<UiText?> = _panelSaveError.asStateFlow()
    private val _panelSaveAppError = MutableStateFlow<AppError?>(null)
    val panelSaveAppError: StateFlow<AppError?> = _panelSaveAppError.asStateFlow()
    private var lastPanelLayout: org.qinyan.rimoat.data.RemotePanelLayout? = null
    fun clearPanelSaveError() {
        _panelSaveError.value = null
        _panelSaveAppError.value = null
    }
    fun retrySavePanelLayout() {
        lastPanelLayout?.let(::savePanelLayout)
    }

    fun savePanelLayout(layout: org.qinyan.rimoat.data.RemotePanelLayout) {
        lastPanelLayout = layout
        val shouldLaunch = synchronized(panelSaveLock) {
            if (!operationLifetime.isActive) return
            pendingPanelLayout = layout
            if (panelSaveActive) false else {
                panelSaveActive = true
                true
            }
        }
        if (!shouldLaunch) return
        operationScope.launch {
            try {
                while (true) {
                    val next = synchronized(panelSaveLock) {
                        pendingPanelLayout.also { pendingPanelLayout = null }
                            ?: run {
                                panelSaveActive = false
                                return@launch
                            }
                    }
                    try {
                        repository?.updatePanelLayout(remoteId, next)
                        _manualProfile.value = _manualProfile.value?.copy(panelLayout = next)
                        clearPanelSaveError()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val appError = e.toAppError(R.string.msg_storage_unavailable, ErrorAction.Retry)
                            .wrapped(R.string.msg_remote_save_failed, ErrorAction.Retry)
                        _panelSaveAppError.value = appError
                        _panelSaveError.value = appError.message
                    }
                }
            } finally {
                synchronized(panelSaveLock) {
                    panelSaveActive = false
                }
            }
        }
    }

    private var learningSession = 0
    private val _learningError = MutableStateFlow<UiText?>(null)
    val learningError = _learningError.asStateFlow()
    private val _learningAppError = MutableStateFlow<AppError?>(null)
    val learningAppError = _learningAppError.asStateFlow()
    private var lastLearnedSave: Pair<RemoteButton, IrSignal.Raw>? = null

    fun startLearningButton(button: RemoteButton) {
        learningSession++
        _learningError.value = null
        _learningAppError.value = null
        clearOperationError()
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
        _learningAppError.value = null
        clearOperationError()
        _capturedLearningSignal.value = null
        _learningDialogButton.value = null
    }

    private val _operationAppError = MutableStateFlow<AppError?>(null)
    val operationAppError: StateFlow<AppError?> = _operationAppError.asStateFlow()
    private val _operationError = MutableStateFlow<UiText?>(null)
    val operationError: StateFlow<UiText?> = _operationError.asStateFlow()
    private fun setOperationError(error: AppError?) {
        _operationAppError.value = error
        _operationError.value = error?.message
    }
    fun clearOperationError(expected: UiText? = null) {
        if (expected == null || _operationError.value == expected) {
            _operationError.value = null
            _operationAppError.value = null
        }
    }
    fun clearOperationAppError(expected: AppError? = null) {
        if (expected == null || _operationAppError.value == expected) {
            _operationAppError.value = null
            _operationError.value = null
        }
    }
    private val _isSavingButton = MutableStateFlow(false)
    val isSavingButton = _isSavingButton.asStateFlow()

    fun retrySaveLearnedButton() {
        lastLearnedSave?.let { (button, signal) -> saveLearnedButton(button, signal) }
    }

    fun saveLearnedButton(button: RemoteButton, signal: IrSignal.Raw) {
        if (_isSavingButton.value || !operationLifetime.isActive) return
        lastLearnedSave = button to signal
        _isSavingButton.value = true
        val session = learningSession
        _learningError.value = null
        _learningAppError.value = null
        clearOperationError()
        operationScope.launch {
            try {
                val updatedButton = button.withLearnedSignal(signal)
                withContext(NonCancellable) {
                    repository?.updateButton(remoteId, updatedButton)
                }
                _manualProfile.value?.let { profile ->
                    val buttons = profile.buttons.toMutableList()
                    val idx = buttons.indexOfFirst { it.id == updatedButton.id }
                    if (idx >= 0) buttons[idx] = updatedButton else buttons.add(updatedButton)
                    val invalidated = profile.withoutVerifiedButtons(listOf(button))
                    _manualProfile.value = invalidated.copy(buttons = buttons.map { current ->
                        if (current.key != null) current else current.copy(key = invalidated.buttons.firstOrNull { it.id == current.id }?.key)
                    })
                }
                if (session == learningSession) dismissLearningDialog()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (session == learningSession) {
                    val appError = e.toAppError(R.string.control_unknown_error, ErrorAction.Retry)
                        .wrapped(R.string.msg_captured_save_failed, ErrorAction.Retry)
                    _learningAppError.value = appError
                    _learningError.value = appError.message
                }
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
    fun clearFeedback() { _feedback.value = null }
    fun onVisible() {
        clearFeedback()
        clearOperationError()
        viewModelScope.launch {
            try { repository?.markUsed(remoteId) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { setOperationError(AppError.Resource(UiText.Resource(R.string.msg_recent_history_save_failed))) }
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
        // Let submitted layout edits, learned button saves, and accepted transmissions finish saving.
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
    private val acReloadTrigger = MutableStateFlow(0)
    private var loadedSettings = initialProfile?.acSettings
    private var lastAttemptedButton: RemoteButton? = null
    private var lastFailedAcSettings: AcSettings? = null

    fun reloadAcCapabilities() {
        acReloadTrigger.value += 1
        viewModelScope.launch {
            try {
                repository?.loadAllRemotes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setOperationError(
                    e.toAppError(R.string.msg_storage_unavailable, ErrorAction.Reload)
                        .wrapped(R.string.msg_ac_functions_load_failed, ErrorAction.Reload)
                )
            }
        }
    }

    fun learnButtonById(buttonId: String) {
        val target = remoteProfile.value?.buttons?.firstOrNull { it.id == buttonId }
            ?: lastAttemptedButton?.takeIf { it.id == buttonId }
            ?: return
        startLearningButton(target)
    }

    fun retrySaveAcSettings() {
        val committed = lastFailedAcSettings ?: return
        operationScope.launch {
            try {
                repository?.updateAcSettings(remoteId, committed)
                lastFailedAcSettings = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setOperationError(
                    e.toAppError(R.string.msg_storage_unavailable, ErrorAction.Retry)
                        .wrapped(R.string.msg_sent_settings_save_failed, ErrorAction.Retry)
                )
            }
        }
    }

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
            kotlinx.coroutines.flow.combine(remoteProfile, acState, isHardwareReady, acReloadTrigger) { profile, state, ready, reload ->
                Triple(profile?.takeUnless { it.useStaticAcButtons }?.acProtocolId, state, ready) to reload
            }.distinctUntilChanged().collect { (triple, _) ->
                val (protocol, state, ready) = triple
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
                        setOperationError(
                            e.toAppError(R.string.control_unknown_error, ErrorAction.Reload)
                                .wrapped(R.string.msg_ac_functions_load_failed, ErrorAction.Reload)
                        )
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
        lastAttemptedButton = button
        if (_isLearningMode.value) { startLearningButton(button); return }
        if (button.acAction != null && !button.isLearned && remoteProfile.value?.useStaticAcButtons == false) {
            performAcAction(button.acAction); return
        }
        if (!button.hasSignal) {
            setOperationError(AppError.Resource(UiText.Resource(R.string.msg_button_learn_first), ErrorAction.Learn(button.id)))
            return
        }
        launchSend { beginTransmission ->
            sendMutex.withLock {
                _lastSentButton.value = button
                _isTransmitting.value = true
                try {
                    if (commandExecutor != null) {
                        val result = commandExecutor.executeButton(remoteId, button.id, feedbackOwner,
                            beforeTransmission = { beginTransmission() })
                        result?.warning?.let { setOperationError(AppError.Resource(it)) }
                        return@withLock
                    }
                    val countKey = "${if (transmitterManager?.currentTransmitterTypeAfterCheck() == TransmitterType.MOCK) "demo" else "real"}:${button.id}"
                    val index = pressCounts[countKey] ?: 0
                    val signal = button.toIrSignal(index)
                    if (transmitterManager != null) {
                        transmitterManager.transmitWithReceipt(listOf(signal), feedbackOwner,
                            beforeTransmission = { beginTransmission() }).getOrThrow()
                    } else { beginTransmission(); onTransmitSignal?.invoke(signal) }
                    pressCounts[countKey] = (index + 1) and 1
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    setOperationError(
                        e.toAppError(R.string.control_unknown_error).wrapped(R.string.msg_send_failed)
                    )
                }
                finally { _isTransmitting.value = false }
            }
        }
    }

    private fun changeAc(transform: suspend (AcControlState, Int) -> Pair<AcControlState, AcAction>?) {
        if (_isLearningMode.value) { setOperationError(AppError.Resource(UiText.Resource(R.string.msg_tap_button_to_learn))); return }
        launchSend { beginTransmission ->
            sendMutex.withLock {
                _isTransmitting.value = true
                try {
                    if (commandExecutor != null) {
                        val result = commandExecutor.changeAc(remoteId, owner = feedbackOwner,
                            beforeTransmission = { beginTransmission() },
                            transform = transform)
                        result?.settings?.let { if (result.simulated) _demoState.value = it else _acState.value = it }
                        result?.warning?.let { setOperationError(AppError.Resource(it)) }
                        return@withLock
                    }
                    val profile = remoteProfile.value ?: throw UiTextException(UiText.Resource(R.string.msg_remote_not_loaded))
                    val encoder = acSignalEncoder ?: throw UiTextException(
                        UiText.Resource(R.string.msg_ac_state_library_unavailable),
                        action = ErrorAction.Reload
                    )
                    if (profile.useStaticAcButtons) throw UiTextException(UiText.Resource(R.string.msg_learned_ac_use_panel))
                    val protocol = profile.acProtocolId ?: throw UiTextException(UiText.Resource(R.string.msg_independent_codes_no_state))
                    val route = transmitterManager?.currentTransmitterTypeAfterCheck()
                    val base = if (route == TransmitterType.MOCK) _demoState.value ?: _acState.value else _acState.value
                    val (transformed, action) = transform(base, protocol) ?: return@withLock
                    val next = if (action == AcAction.TIMER) transformed else transformed.copy(timerMinutes = 0)
                    if (!encoder.supportsAction(protocol, next, action)) {
                        throw UiTextException(
                            if (action == AcAction.TEMP_UP || action == AcAction.TEMP_DOWN) {
                                UiText.resource(R.string.msg_mode_no_temperature, UiText.Resource(next.mode.labelRes))
                            } else {
                                UiText.Resource(R.string.msg_action_unsupported)
                            }
                        )
                    }
                    val signals = encoder.encode(protocol, next, action)
                    if (signals.isEmpty()) throw UiTextException(UiText.Resource(R.string.msg_ac_no_signals))
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
                        catch (e: Exception) {
                            lastFailedAcSettings = committed
                            setOperationError(
                                e.toAppError(R.string.msg_storage_unavailable, ErrorAction.Retry)
                                    .wrapped(R.string.msg_sent_settings_save_failed, ErrorAction.Retry)
                            )
                        }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    setOperationError(
                        e.toAppError(R.string.control_unknown_error).wrapped(R.string.msg_send_incomplete)
                    )
                }
                finally { _isTransmitting.value = false }
            }
        }
    }

    fun resendAcSettings() = changeAc { state, _ ->
        state to if (state.powerOn) AcAction.POWER_ON else AcAction.POWER_OFF
    }

    fun applyAcPreset(settings: AcSettings) = changeAc { state, protocol ->
        if (settings.powerOn && settings.mode !in acSignalEncoder!!.supportedModes(protocol)) {
            throw UiTextException(UiText.Resource(R.string.msg_mode_unsupported))
        }
        settings.copy(counter = state.counter) to if (settings.powerOn) AcAction.POWER_ON else AcAction.POWER_OFF
    }

    fun toggleAcPower() = changeAc { state, _ ->
        val on = !state.powerOn
        state.copy(powerOn = on) to if (on) AcAction.POWER_ON else AcAction.POWER_OFF
    }

    fun increaseTemp() = changeTemperature(AcAction.TEMP_UP)

    fun decreaseTemp() = changeTemperature(AcAction.TEMP_DOWN)

    private fun changeTemperature(action: AcAction) = changeAc { state, _ ->
        state.temperatureBoundaryReason(action)?.let { reason ->
            throw UiTextException(UiText.Resource(when (reason) {
                org.qinyan.rimoat.data.AcUnavailableReason.TEMPERATURE_MINIMUM -> R.string.control_reason_temperature_minimum
                else -> R.string.control_reason_temperature_maximum
            }))
        }
        state.apply(action) to action
    }

    fun cycleAcMode() = changeAc { state, protocol ->
        val modes = acSignalEncoder!!.supportedModes(protocol)
        if (modes.isEmpty()) throw UiTextException(UiText.Resource(R.string.msg_model_no_modes))
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
        catch (e: Exception) {
            setOperationError(
                e.toAppError(R.string.control_unknown_error, ErrorAction.Reload)
                    .wrapped(R.string.msg_states_load_failed, ErrorAction.Reload)
            )
            return emptyList()
        }
    }

    fun selectAcState(choice: AcStateChoice) = changeAc { state, protocol ->
        val target = choice.applyTo(state)
        if (choice.action == AcAction.MODE && target.mode !in acSignalEncoder!!.supportedModes(protocol)) {
            throw UiTextException(UiText.Resource(R.string.msg_model_mode_unsupported))
        }
        target to choice.action
    }

    fun performAcAction(action: AcAction, timerMinutes: Int = 0) = changeAc { state, protocol ->
        val next = state.apply(action, timerMinutes)
        if (action == AcAction.TIMER && !org.qinyan.rimoat.data.AcTimerOptions.supports(protocol, next)) {
            throw UiTextException(UiText.Resource(R.string.msg_timer_unsupported))
        }
        if ((action == AcAction.QUICK_COOL || action == AcAction.QUICK_HEAT) &&
            next.mode !in acSignalEncoder!!.supportedModes(protocol)) {
            throw UiTextException(UiText.Resource(R.string.msg_model_mode_unsupported))
        }
        next to action
    }

    fun toggleForceMock() {
        val current = transmitterManager?.forceMockMode?.value ?: return
        transmitterManager.setForceMockMode(!current)
    }
}

