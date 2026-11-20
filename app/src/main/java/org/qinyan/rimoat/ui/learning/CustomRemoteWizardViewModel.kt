package org.qinyan.rimoat.ui.learning

import org.qinyan.rimoat.R

import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.uiTextDetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.RemoteRepository
import org.qinyan.rimoat.data.SignalType
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.hardware.learning.LearnerType
import org.qinyan.rimoat.hardware.learning.LearningState
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class WizardStep {
    SETUP,
    LEARNING,
    REVIEW
}

@Serializable
enum class KeyLearnStatus {
    PENDING,
    LEARNING,
    CAPTURED,
    LEARNED,
    SKIPPED
}

@Serializable
data class WizardKeyItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val iconName: String? = null,
    val status: KeyLearnStatus = KeyLearnStatus.PENDING,
    val capturedSignal: IrSignal.Raw? = null,
    val pulseCount: Int = 0,
    val durationMs: Double = 0.0,
    val builtinLabel: String? = null
)

@Serializable
data class CustomRemoteWizardUiState(
    val step: WizardStep = WizardStep.SETUP,
    val remoteName: String = "",
    val brand: String = "",
    val category: DeviceCategory = DeviceCategory.CUSTOM,
    val keyQueue: List<WizardKeyItem> = emptyList(),
    val currentIndex: Int = 0,
    val isTestingTransmit: Boolean = false,
    @kotlinx.serialization.Transient val testFeedbackMessage: UiText? = null,
    @kotlinx.serialization.Transient val errorMessage: UiText? = null,
    val isSaving: Boolean = false,
    val isRestoring: Boolean = false
) {
    val currentKey: WizardKeyItem?
        get() = keyQueue.getOrNull(currentIndex)

    val learnedCount: Int
        get() = keyQueue.count { it.status == KeyLearnStatus.LEARNED && it.capturedSignal != null }

    val canFinishEarly: Boolean
        get() = learnedCount >= 1
}

class CustomRemoteWizardViewModel(
    val repository: RemoteRepository? = null,
    val transmitterManager: IrTransmitterManager? = null,
    val learnerManager: IrLearnerManager? = null,
    val initialCategory: DeviceCategory = DeviceCategory.CUSTOM,
    private val savedStateHandle: SavedStateHandle? = null,
    private val draftFile: File? = null,
    private val brandLibrary: org.qinyan.rimoat.data.db.IrCatalog? = null
) : ViewModel() {

    private val draftJson = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(
        savedStateHandle?.get<String>("wizard_state")?.let { encoded ->
            runCatching { draftJson.decodeFromString<CustomRemoteWizardUiState>(encoded) }.getOrNull()?.let { state ->
                state.copy(isSaving = false, isTestingTransmit = false, keyQueue = state.keyQueue.map { item ->
                    if (item.status == KeyLearnStatus.LEARNING) item.copy(status = KeyLearnStatus.PENDING) else item
                })
            }
        } ?: CustomRemoteWizardUiState(
            category = initialCategory,
            keyQueue = generateDefaultQueue(initialCategory)
        ).copy(isRestoring = draftFile != null && savedStateHandle?.get<String>("wizard_state") == "local-draft")
    )
    val uiState: StateFlow<CustomRemoteWizardUiState> = _uiState.asStateFlow()

    suspend fun brandsFor(category: DeviceCategory): List<org.qinyan.rimoat.data.db.IrBrand> {
        val library = brandLibrary ?: return emptyList()
        val code = when (category) {
            DeviceCategory.TV -> "tv"; DeviceCategory.AC -> "air"; DeviceCategory.FAN -> "fan"
            DeviceCategory.AUDIO -> "amp"; DeviceCategory.PROJECTOR -> "pjt"; DeviceCategory.CUSTOM -> null
        }
        val categories = library.getCategories().filter { code == null || it.code == code }
        return categories.flatMap { library.getBrandsForCategory(it.id) }.distinctBy { it.nameCn to it.nameEn }
    }

    private var savedProfile: RemoteProfile? = null
    private var completed = false
    private var draftJob: Job? = null

    init {
        if (savedStateHandle != null || draftFile != null) draftJob = viewModelScope.launch {
            if (_uiState.value.isRestoring) {
                try {
                    val restored = withContext(Dispatchers.IO) {
                        draftJson.decodeFromString<CustomRemoteWizardUiState>(checkNotNull(draftFile).readText())
                    }
                    _uiState.value = restored.copy(isSaving = false, isTestingTransmit = false, isRestoring = false,
                        keyQueue = restored.keyQueue.map { item ->
                            if (item.status == KeyLearnStatus.LEARNING) item.copy(status =
                                if (item.capturedSignal == null) KeyLearnStatus.PENDING else KeyLearnStatus.CAPTURED)
                            else item
                        })
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    _uiState.update { it.copy(isRestoring = false, errorMessage = UiText.resource(R.string.screen_restore_draft_failed, e.uiTextDetail(R.string.control_unknown_error))) }
                    // Keep an unreadable draft intact for recovery.
                    return@launch
                }
            }
            uiState.collect { state ->
                if (completed || savedProfile != null) return@collect
                try {
                    if (draftFile != null) {
                        withContext(Dispatchers.IO) {
                            org.qinyan.rimoat.data.AtomicLocalFile.write(draftFile,
                                draftJson.encodeToString(CustomRemoteWizardUiState.serializer(), state))
                        }
                        savedStateHandle?.set("wizard_state", "local-draft")
                    } else {
                        savedStateHandle?.set("wizard_state", draftJson.encodeToString(CustomRemoteWizardUiState.serializer(), state))
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    _uiState.update { it.copy(errorMessage = UiText.resource(R.string.screen_save_draft_failed, e.uiTextDetail(R.string.control_unknown_error))) }
                }
            }
        }
    }

    val learnerState: StateFlow<LearningState> = learnerManager?.state
        ?: MutableStateFlow<LearningState>(LearningState.Idle).asStateFlow()

    val learnerType: StateFlow<LearnerType> = learnerManager?.learnerType
        ?: MutableStateFlow(LearnerType.NONE).asStateFlow()

    val forceMockMode = learnerManager?.forceMockMode ?: MutableStateFlow(false).asStateFlow()

    val receiveConfirmed = learnerManager?.receiveConfirmed ?: MutableStateFlow(false).asStateFlow()

    val isHardwareAvailable: StateFlow<Boolean> = learnerManager?.isAvailable
        ?: MutableStateFlow(false).asStateFlow()

    fun setForceMockMode(forced: Boolean) {
        cancelCurrentKeyLearning()
        learnerManager?.setForceMockMode(forced)
    }

    private var activeLearningJob: Job? = null
    private var learningEpoch = 0
    private var keyBeforeLearning: WizardKeyItem? = null
    private var testJob: Job? = null
    private var testEpoch = 0

    fun setRemoteName(name: String) {
        _uiState.update { it.copy(remoteName = name.take(100), errorMessage = null) }
    }

    fun setBrand(brand: String) {
        _uiState.update { it.copy(brand = brand.take(100)) }
    }

    fun setCategory(category: DeviceCategory) {
        if (_uiState.value.category == category) return
        _uiState.update {
            if (it.step == WizardStep.SETUP) {
                it.copy(
                    category = category,
                    keyQueue = generateDefaultQueue(category)
                )
            } else {
                it.copy(category = category)
            }
        }
    }

    fun proceedToLearning(): Boolean {
        val name = _uiState.value.remoteName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = UiText.Resource(R.string.screen_enter_name)) }
            return false
        }
        _uiState.update {
            it.copy(
                step = WizardStep.LEARNING,
                currentIndex = it.currentIndex.coerceIn(0, it.keyQueue.lastIndex.coerceAtLeast(0)),
                errorMessage = null
            )
        }
        return true
    }

    fun startLearningCurrentKey() {
        if (_uiState.value.step != WizardStep.LEARNING || _uiState.value.isTestingTransmit) return
        if (_uiState.value.currentKey?.status == KeyLearnStatus.LEARNING) return
        val currentKey = _uiState.value.currentKey ?: return
        cancelCurrentKeyLearning()
        keyBeforeLearning = currentKey
        val epoch = ++learningEpoch
        _uiState.update { current ->
            val updatedKey = currentKey.copy(status = KeyLearnStatus.LEARNING)
            val newQueue = current.keyQueue.toMutableList()
            newQueue[current.currentIndex] = updatedKey
            current.copy(keyQueue = newQueue, testFeedbackMessage = null, errorMessage = null)
        }

        activeLearningJob = viewModelScope.launch {
            try {
                val lm = learnerManager
                if (lm == null) {
                    if (epoch == learningEpoch) _uiState.update { current ->
                        if (current.step != WizardStep.LEARNING || current.currentKey?.id != currentKey.id) return@update current
                        current.copy(
                            keyQueue = current.keyQueue.map { if (it.id == currentKey.id) currentKey else it },
                            errorMessage = UiText.resource(R.string.screen_learning_error_e, UiText.Resource(R.string.screen_receiver_unavailable))
                        )
                    }
                    return@launch
                }
                val result = lm.startLearning(timeoutSeconds = 30)
                if (epoch != learningEpoch) return@launch
                _uiState.update { current ->
                    if (current.step != WizardStep.LEARNING || current.currentKey?.id != currentKey.id) return@update current
                    val signal = result.getOrNull()
                    val item = if (signal != null) currentKey.copy(status = KeyLearnStatus.CAPTURED,
                        capturedSignal = signal, pulseCount = signal.patternMicros.size,
                        durationMs = signal.patternMicros.sumOf { kotlin.math.abs(it.toLong()) } / 1000.0)
                    else currentKey
                    current.copy(keyQueue = current.keyQueue.map { if (it.id == item.id) item else it },
                        errorMessage = result.exceptionOrNull()?.let {
                            when {
                                it is CancellationException -> null
                                it.message?.contains("timed out", ignoreCase = true) == true -> UiText.Resource(R.string.screen_learn_timeout)
                                else -> UiText.resource(R.string.screen_learning_error_e, it.uiTextDetail(R.string.screen_learn_failed))
                            }
                        })
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (epoch == learningEpoch) _uiState.update { current ->
                    current.copy(keyQueue = current.keyQueue.map { if (it.id == currentKey.id) currentKey else it },
                        errorMessage = UiText.resource(R.string.screen_learning_error_e, e.uiTextDetail(R.string.screen_learn_failed)))
                }
            } finally {
                if (epoch == learningEpoch) { activeLearningJob = null; keyBeforeLearning = null }
            }
        }
    }

    fun cancelCurrentKeyLearning() {
        learningEpoch++
        activeLearningJob?.cancel()
        activeLearningJob = null
        learnerManager?.cancel()
        _uiState.update { current ->
            val key = current.currentKey ?: return@update current
            if (key.status != KeyLearnStatus.LEARNING) return@update current
            val updatedKey = keyBeforeLearning?.takeIf { it.id == key.id }
                ?: key.copy(status = if (key.capturedSignal == null) KeyLearnStatus.PENDING else KeyLearnStatus.CAPTURED)
            val newQueue = current.keyQueue.toMutableList()
            if (current.currentIndex in newQueue.indices) {
                newQueue[current.currentIndex] = updatedKey
            }
            current.copy(keyQueue = newQueue, errorMessage = null)
        }
        keyBeforeLearning = null
    }

    fun onScreenStopped() {
        cancelCurrentKeyLearning()
        cancelTest()
    }

    private fun cancelTest() {
        testEpoch++
        testJob?.cancel()
        testJob = null
        _uiState.update { it.copy(isTestingTransmit = false, testFeedbackMessage = null) }
    }

    fun testTransmitCurrentKey() {
        if (_uiState.value.isTestingTransmit) return
        val signal = _uiState.value.currentKey?.capturedSignal ?: return
        val epoch = ++testEpoch
        _uiState.update { it.copy(isTestingTransmit = true, testFeedbackMessage = null) }
        testJob = viewModelScope.launch {
            try {
                val tm = transmitterManager
                if (tm == null) {
                    if (epoch == testEpoch) _uiState.update {
                        it.copy(testFeedbackMessage = UiText.resource(R.string.screen_send_error_t, UiText.Resource(R.string.screen_transmitter_unavailable)))
                    }
                    return@launch
                }
                val receipt = tm.transmitWithReceipt(listOf(signal)).getOrThrow()
                if (epoch == testEpoch) _uiState.update { it.copy(testFeedbackMessage =
                    if (receipt.simulated) UiText.Resource(R.string.screen_simulated_send) else UiText.Resource(R.string.screen_test_sent)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (epoch == testEpoch) _uiState.update { it.copy(testFeedbackMessage = UiText.resource(R.string.screen_send_error_t, e.uiTextDetail(R.string.screen_hardware_error))) }
            } finally {
                if (epoch == testEpoch) _uiState.update { it.copy(isTestingTransmit = false) }
            }
        }
    }

    fun confirmCurrentKey() {
        val key = _uiState.value.currentKey ?: return
        if (key.status == KeyLearnStatus.SKIPPED) { skipCurrentKey(); return }
        if (key.capturedSignal == null || key.status !in setOf(KeyLearnStatus.CAPTURED, KeyLearnStatus.LEARNED)) return
        cancelTest()
        cancelCurrentKeyLearning()
        _uiState.update { current ->
            val key = current.currentKey ?: return@update current
            val updatedKey = key.copy(status = KeyLearnStatus.LEARNED)
            val newQueue = current.keyQueue.toMutableList()
            if (current.currentIndex in newQueue.indices) {
                newQueue[current.currentIndex] = updatedKey
            }

            val nextIndex = current.currentIndex + 1
            if (nextIndex >= newQueue.size) {
                current.copy(keyQueue = newQueue, step = WizardStep.REVIEW, testFeedbackMessage = null)
            } else {
                current.copy(keyQueue = newQueue, currentIndex = nextIndex, testFeedbackMessage = null)
            }
        }
    }

    fun skipCurrentKey() {
        cancelTest()
        cancelCurrentKeyLearning()
        _uiState.update { current ->
            val key = current.currentKey ?: return@update current
            val updatedKey = key.copy(status = KeyLearnStatus.SKIPPED)
            val newQueue = current.keyQueue.toMutableList()
            if (current.currentIndex in newQueue.indices) {
                newQueue[current.currentIndex] = updatedKey
            }

            val nextIndex = current.currentIndex + 1
            if (nextIndex >= newQueue.size) {
                current.copy(keyQueue = newQueue, step = WizardStep.REVIEW, testFeedbackMessage = null)
            } else {
                current.copy(keyQueue = newQueue, currentIndex = nextIndex, testFeedbackMessage = null)
            }
        }
    }

    fun addCustomKey(label: String) {
        val trimmed = label.trim()
        if (trimmed.isBlank()) return
        if (trimmed.length > 100 || _uiState.value.keyQueue.size >= 256) {
            _uiState.update { it.copy(errorMessage = UiText.Resource(R.string.screen_key_limits)) }
            return
        }
        val newKey = WizardKeyItem(
            id = "custom_${UUID.randomUUID()}",
            label = trimmed
        )
        _uiState.update { current ->
            val newQueue = current.keyQueue + newKey
            current.copy(keyQueue = newQueue)
        }
    }

    fun selectKey(index: Int) {
        if (_uiState.value.isSaving || index !in _uiState.value.keyQueue.indices) return
        cancelTest()
        cancelCurrentKeyLearning()
        _uiState.update { it.copy(currentIndex = index, errorMessage = null) }
    }

    fun finishEarly() {
        if (_uiState.value.canFinishEarly) {
            cancelTest()
            cancelCurrentKeyLearning()
            _uiState.update { it.copy(step = WizardStep.REVIEW, testFeedbackMessage = null) }
        }
    }

    fun returnToLearning() {
        _uiState.update { it.copy(step = WizardStep.LEARNING) }
    }

    fun saveRemote(onSaved: (RemoteProfile) -> Unit) {
        val state = _uiState.value
        if (state.isSaving || completed) return
        if (state.learnedCount == 0 || state.remoteName.isBlank()) {
            _uiState.update { it.copy(errorMessage = UiText.Resource(R.string.screen_need_learned_key)) }
            return
        }
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        val learnedButtons = state.keyQueue
            .filter { it.status == KeyLearnStatus.LEARNED && it.capturedSignal != null }
            .mapIndexed { idx, item ->
                val sig = item.capturedSignal!!
                RemoteButton(
                    id = item.id,
                    label = item.label,
                    builtinLabel = item.builtinLabel,
                    iconName = item.iconName,
                    row = idx / 3,
                    col = idx % 3,
                    spanCol = 1,
                    signalType = SignalType.RAW,
                    isLearned = true,
                    frequencyHz = sig.frequencyHz,
                    rawPattern = sig.patternMicros.toList()
                )
            }

        val profile = savedProfile ?: RemoteProfile(
            id = UUID.randomUUID().toString(),
            name = state.remoteName.trim(),
            brand = state.brand.trim(),
            category = state.category,
            useStaticAcButtons = state.category == DeviceCategory.AC,
            buttons = learnedButtons,
            updatedAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            try {
                if (savedProfile == null) {
                    repository?.saveRemote(profile)
                    savedProfile = profile
                }
                draftJob?.cancelAndJoin()
                draftJob = null
                withContext(Dispatchers.IO) {
                    draftFile?.let { file ->
                        check(!file.exists() || file.delete()) { org.qinyan.rimoat.i18n.AppStrings.get(R.string.msg_storage_unavailable) }
                    }
                }
                savedStateHandle?.remove<String>("wizard_state")
                completed = true
                reset(state.category)
                onSaved(profile)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = UiText.resource(R.string.screen_save_learning_failed, e.uiTextDetail(R.string.control_unknown_error))) }
            } finally { _uiState.update { it.copy(isSaving = false) } }
        }
    }

    fun reset(category: DeviceCategory = initialCategory) {
        cancelTest()
        cancelCurrentKeyLearning()
        _uiState.value = CustomRemoteWizardUiState(
            category = category,
            keyQueue = generateDefaultQueue(category)
        )
    }

    fun handleBack(): Boolean {
        if (_uiState.value.isSaving || _uiState.value.isRestoring) return true
        cancelTest()
        val current = _uiState.value
        return when (current.step) {
            WizardStep.REVIEW -> {
                _uiState.update { it.copy(step = WizardStep.LEARNING) }
                true
            }
            WizardStep.LEARNING -> {
                cancelCurrentKeyLearning()
                _uiState.update { it.copy(step = WizardStep.SETUP) }
                true
            }
            WizardStep.SETUP -> false
        }
    }

    override fun onCleared() {
        super.onCleared()
        learnerManager?.cancel()
    }

    companion object {
        fun generateDefaultQueue(category: DeviceCategory): List<WizardKeyItem> {
            return when (category) {
                DeviceCategory.TV -> listOf(
                    WizardKeyItem(label = "电源", iconName = "power"),
                    WizardKeyItem(label = "音量+", iconName = "volume_up"),
                    WizardKeyItem(label = "音量-", iconName = "volume_down"),
                    WizardKeyItem(label = "静音", iconName = "volume_off"),
                    WizardKeyItem(label = "上", iconName = "keyboard_arrow_up"),
                    WizardKeyItem(label = "下", iconName = "keyboard_arrow_down"),
                    WizardKeyItem(label = "左", iconName = "keyboard_arrow_left"),
                    WizardKeyItem(label = "右", iconName = "keyboard_arrow_right"),
                    WizardKeyItem(label = "OK", iconName = null),
                    WizardKeyItem(label = "返回", iconName = "arrow_back"),
                    WizardKeyItem(label = "主页", iconName = "home"),
                    WizardKeyItem(label = "菜单", iconName = "menu")
                )
                DeviceCategory.AC -> listOf(
                    WizardKeyItem(label = "开关", iconName = "power"),
                    WizardKeyItem(label = "温度+", iconName = "arrow_drop_up"),
                    WizardKeyItem(label = "温度-", iconName = "arrow_drop_down"),
                    WizardKeyItem(label = "模式", iconName = "tune"),
                    WizardKeyItem(label = "风速", iconName = "air"),
                    WizardKeyItem(label = "扫风", iconName = "swap_calls")
                )
                DeviceCategory.FAN -> listOf(
                    WizardKeyItem(label = "电源", iconName = "power"),
                    WizardKeyItem(label = "风速", iconName = "speed"),
                    WizardKeyItem(label = "摇头", iconName = "sync_alt"),
                    WizardKeyItem(label = "定时", iconName = "timer"),
                    WizardKeyItem(label = "模式", iconName = "tune")
                )
                DeviceCategory.PROJECTOR -> listOf(
                    WizardKeyItem(label = "电源", iconName = "power"),
                    WizardKeyItem(label = "上", iconName = "keyboard_arrow_up"),
                    WizardKeyItem(label = "下", iconName = "keyboard_arrow_down"),
                    WizardKeyItem(label = "左", iconName = "keyboard_arrow_left"),
                    WizardKeyItem(label = "右", iconName = "keyboard_arrow_right"),
                    WizardKeyItem(label = "OK", iconName = null),
                    WizardKeyItem(label = "返回", iconName = "arrow_back"),
                    WizardKeyItem(label = "菜单", iconName = "menu"),
                    WizardKeyItem(label = "对焦+", iconName = "center_focus_strong"),
                    WizardKeyItem(label = "对焦-", iconName = "filter_center_focus")
                )
                DeviceCategory.CUSTOM,
                DeviceCategory.AUDIO -> listOf(
                    WizardKeyItem(label = "电源", iconName = "power"),
                    WizardKeyItem(label = "音量+", iconName = "volume_up"),
                    WizardKeyItem(label = "音量-", iconName = "volume_down"),
                    WizardKeyItem(label = "上", iconName = "keyboard_arrow_up"),
                    WizardKeyItem(label = "下", iconName = "keyboard_arrow_down"),
                    WizardKeyItem(label = "左", iconName = "keyboard_arrow_left"),
                    WizardKeyItem(label = "右", iconName = "keyboard_arrow_right"),
                    WizardKeyItem(label = "OK", iconName = null)
                )
            }.map { it.copy(builtinLabel = it.label) }
        }
    }
}

