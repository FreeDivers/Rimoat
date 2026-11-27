package org.qinyan.rimoat.ui.learning

import android.util.Log
import org.qinyan.rimoat.R

import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.toAppError
import org.qinyan.rimoat.i18n.wrapped

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.ApplianceType
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.data.ButtonKey
import org.qinyan.rimoat.data.LegacyLabelMap
import org.qinyan.rimoat.data.LegacyRemoteMigration
import org.qinyan.rimoat.data.buttonKey
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
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
    val builtinLabel: String? = null,
    val key: ButtonKey? = null,
    val acAction: AcAction? = null
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
    val isRestoring: Boolean = false,
    val applianceType: ApplianceType? = null,
    val templateApplianceType: ApplianceType? = null,
    val keySchemaVersion: Int = 0,
    @kotlinx.serialization.Transient val pendingApplianceType: ApplianceType? = null
) {
    val selectedApplianceType: ApplianceType
        get() = applianceType ?: ApplianceType.fromCategory(category)

    val currentKey: WizardKeyItem?
        get() = keyQueue.getOrNull(currentIndex)

    val learnedCount: Int
        get() = keyQueue.count { it.status == KeyLearnStatus.LEARNED && it.capturedSignal != null }

    val canFinishEarly: Boolean
        get() = learnedCount >= 1

    val hasLearningProgress: Boolean
        get() = keyQueue.any {
            it.capturedSignal != null ||
                it.status != KeyLearnStatus.PENDING ||
                it.builtinLabel == null ||
                it.id.startsWith("custom_")
        }

    val hasDraftProgress: Boolean
        get() = step != WizardStep.SETUP ||
            remoteName.isNotBlank() ||
            brand.isNotBlank() ||
            hasLearningProgress
}

@Serializable
private data class WizardDraftSnapshot(
    val step: WizardStep,
    val remoteName: String,
    val brand: String,
    val category: DeviceCategory,
    val keyQueue: List<WizardKeyItem>,
    val currentIndex: Int,
    val applianceType: ApplianceType?,
    val templateApplianceType: ApplianceType?,
    val keySchemaVersion: Int
)

private fun CustomRemoteWizardUiState.toDraftSnapshot(): WizardDraftSnapshot = WizardDraftSnapshot(
    step = step,
    remoteName = remoteName,
    brand = brand,
    category = category,
    keyQueue = keyQueue,
    currentIndex = currentIndex,
    applianceType = applianceType,
    templateApplianceType = templateApplianceType,
    keySchemaVersion = keySchemaVersion
)

private fun normalizeRestored(state: CustomRemoteWizardUiState): CustomRemoteWizardUiState {
    val normalizedQueue = state.keyQueue.map { item ->
        val key = item.key ?: item.builtinLabel?.let(LegacyLabelMap::key)
        item.copy(
            key = key,
            builtinLabel = if (item.builtinLabel != null && key != null) key.name else item.builtinLabel,
            status = if (item.status == KeyLearnStatus.LEARNING) {
                if (item.capturedSignal == null) KeyLearnStatus.PENDING else KeyLearnStatus.CAPTURED
            } else item.status
        )
    }
    val defaults = CustomRemoteWizardViewModel.generateDefaultQueue(state.selectedApplianceType)
    val template = state.templateApplianceType ?: state.selectedApplianceType.takeIf {
        state.keySchemaVersion == 0 &&
            normalizedQueue.filter { it.builtinLabel != null }.map { it.key } == defaults.map { it.key }
    }
    val actionsByKey = defaults.associate { it.key to it.acAction }
    return state.copy(
        isSaving = false,
        isTestingTransmit = false,
        isRestoring = false,
        pendingApplianceType = null,
        templateApplianceType = template,
        keySchemaVersion = 1,
        keyQueue = normalizedQueue.map { item ->
            if (state.keySchemaVersion == 0 && template == ApplianceType.AC && item.builtinLabel != null) {
                item.copy(acAction = item.acAction ?: actionsByKey[item.key])
            } else item
        }
    )
}

@OptIn(FlowPreview::class)
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

    private val initialSavedState = savedStateHandle?.get<String>(KEY_WIZARD_STATE)
    private val initialInlineState = initialSavedState
        ?.takeIf { it.trimStart().startsWith("{") }
        ?.let { encoded ->
            runCatching { draftJson.decodeFromString<CustomRemoteWizardUiState>(encoded) }
                .getOrNull()
                ?.let(::normalizeRestored)
        }
    private val resolvedDraftFile: File? = draftFile ?: if (savedStateHandle != null) {
        val sessionId = savedStateHandle.get<String>(KEY_DRAFT_SESSION_ID)
            ?.takeIf { WizardDrafts.isValidSessionId(it) }
            ?: UUID.randomUUID().toString()
        val baseDir = repository?.appFilesDir
            ?: File(System.getProperty("java.io.tmpdir") ?: ".", "rimoat-drafts")
        WizardDrafts.file(baseDir, sessionId)
    } else null
    private val draftSessionId: String? = if (draftFile == null && savedStateHandle != null) {
        resolvedDraftFile?.nameWithoutExtension
    } else null
    private val shouldRestoreFromDisk: Boolean = initialInlineState == null &&
        (draftFile != null || (initialSavedState == LOCAL_DRAFT_MARKER && resolvedDraftFile != null))

    private val _uiState = MutableStateFlow(
        initialInlineState ?: CustomRemoteWizardUiState(
            category = initialCategory,
            templateApplianceType = ApplianceType.fromCategory(initialCategory),
            keySchemaVersion = 1,
            keyQueue = generateDefaultQueue(initialCategory)
        ).copy(isRestoring = shouldRestoreFromDisk)
    )
    val uiState: StateFlow<CustomRemoteWizardUiState> = _uiState.asStateFlow()
    private val _savedRemote = MutableStateFlow<RemoteProfile?>(null)
    val savedRemote: StateFlow<RemoteProfile?> = _savedRemote.asStateFlow()

    suspend fun brandsFor(type: ApplianceType): List<org.qinyan.rimoat.data.db.IrBrand> {
        val library = brandLibrary ?: return emptyList()
        val code = type.catalogCode.takeUnless { type == ApplianceType.CUSTOM }
        val categories = library.getCategories().filter { code == null || it.code == code }
        return categories.flatMap { library.getBrandsForCategory(it.id) }.distinctBy { it.nameCn to it.nameEn }
    }

    private var completed = false
    private var draftJob: Job? = null
    private var draftFlushJob: Job? = null
    @Volatile private var initialSettledSnapshot: WizardDraftSnapshot? =
        if (shouldRestoreFromDisk) null else _uiState.value.toDraftSnapshot()
    @Volatile private var lastWrittenSnapshot: WizardDraftSnapshot? = null

    init {
        val targetFile = resolvedDraftFile
        if (targetFile != null) draftJob = viewModelScope.launch {
            if (_uiState.value.isRestoring) {
                val initial = _uiState.value.copy(isRestoring = false)
                try {
                    val restored = withContext(Dispatchers.IO) {
                        WizardDrafts.read(targetFile)?.let {
                            draftJson.decodeFromString<CustomRemoteWizardUiState>(it)
                        }
                    }
                    val resumable = restored?.let(::normalizeRestored)
                    val settled = (resumable ?: initial).toDraftSnapshot()
                    initialSettledSnapshot = settled
                    if (resumable != null) lastWrittenSnapshot = settled
                    _uiState.update { current -> resumable ?: current.copy(isRestoring = false) }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            errorMessage = e.toAppError(R.string.control_unknown_error)
                                .wrapped(R.string.screen_restore_draft_failed)
                                .message
                        )
                    }
                    // Keep an unreadable draft intact for recovery.
                    return@launch
                }
            }
            var skipInitialIfUnchanged = true
            withContext(Dispatchers.IO) {
                uiState
                    .filter { !it.isRestoring }
                    .map { it.toDraftSnapshot() }
                    .distinctUntilChanged()
                    .debounce(DRAFT_WRITE_DEBOUNCE_MS)
                    .collect { snapshot ->
                        if (skipInitialIfUnchanged) {
                            skipInitialIfUnchanged = false
                            if (snapshot == initialSettledSnapshot) return@collect
                        }
                        writeDraftSnapshot(targetFile, snapshot)
                    }
            }
        }
    }

    private suspend fun writeDraftSnapshot(file: File, snapshot: WizardDraftSnapshot) {
        try {
            withContext(Dispatchers.IO) {
                WizardDrafts.write(file, draftJson.encodeToString(WizardDraftSnapshot.serializer(), snapshot))
            }
            lastWrittenSnapshot = snapshot
            if (savedStateHandle != null) {
                withContext(Dispatchers.Main.immediate) {
                    draftSessionId?.let { savedStateHandle.set(KEY_DRAFT_SESSION_ID, it) }
                    savedStateHandle.set(KEY_WIZARD_STATE, LOCAL_DRAFT_MARKER)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    errorMessage = e.toAppError(R.string.control_unknown_error)
                        .wrapped(R.string.screen_save_draft_failed)
                        .message
                )
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
        setApplianceType(ApplianceType.fromCategory(category))
    }

    fun setApplianceType(type: ApplianceType) {
        val state = _uiState.value
        if (state.selectedApplianceType == type || state.isSaving || state.isRestoring) return
        if (state.hasLearningProgress) {
            _uiState.update { it.copy(pendingApplianceType = type) }
        } else {
            applyApplianceType(type, keepLearnedKeys = false)
        }
    }

    fun cancelApplianceTypeChange() {
        _uiState.update { it.copy(pendingApplianceType = null) }
    }

    fun confirmApplianceTypeChange(keepLearnedKeys: Boolean) {
        val state = _uiState.value
        val type = state.pendingApplianceType ?: return
        if (state.isSaving || state.isRestoring) return
        applyApplianceType(type, keepLearnedKeys)
    }

    private fun applyApplianceType(type: ApplianceType, keepLearnedKeys: Boolean) {
        cancelTest()
        cancelCurrentKeyLearning()
        _uiState.update {
            val retained = if (keepLearnedKeys) it.keyQueue.filter { item ->
                item.capturedSignal != null || item.builtinLabel == null || item.id.startsWith("custom_")
            } else emptyList()
            val defaults = generateDefaultQueue(type)
            val queue = retained + defaults.filter { item ->
                retained.none { existing ->
                    if (item.acAction != null) existing.acAction == item.acAction
                    else existing.key == item.key && existing.acAction == null
                }
            }
            if (queue.size > 256) return@update it.copy(errorMessage = UiText.Resource(R.string.screen_key_limits))
            it.copy(
                category = type.category,
                applianceType = type,
                templateApplianceType = if (retained.isEmpty()) type else it.templateApplianceType.takeIf { origin -> origin == type },
                keySchemaVersion = 1,
                pendingApplianceType = null,
                currentIndex = queue.indexOfFirst { item -> item.id == it.currentKey?.id }.coerceAtLeast(0),
                keyQueue = queue,
                errorMessage = null
            )
        }
    }

    fun proceedToLearning(): Boolean {
        if (_uiState.value.pendingApplianceType != null) return false
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
                                it is kotlinx.coroutines.TimeoutCancellationException ||
                                    isLearningTimeout(it, lm.state.value, lm.activeLearner.value.state.value) ->
                                    UiText.Resource(R.string.screen_learn_timeout)
                                it is CancellationException -> null
                                else -> it.toAppError(R.string.screen_learn_failed)
                                    .wrapped(R.string.screen_learning_error_e)
                                    .message
                            }
                        })
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                if (epoch == learningEpoch) _uiState.update { current ->
                    current.copy(
                        keyQueue = current.keyQueue.map { if (it.id == currentKey.id) currentKey else it },
                        errorMessage = UiText.Resource(R.string.screen_learn_timeout)
                    )
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (epoch == learningEpoch) _uiState.update { current ->
                    val mapped = if (isLearningTimeout(
                            e,
                            learnerManager?.state?.value ?: LearningState.Idle,
                            learnerManager?.activeLearner?.value?.state?.value ?: LearningState.Idle
                        )
                    ) {
                        UiText.Resource(R.string.screen_learn_timeout)
                    } else {
                        e.toAppError(R.string.screen_learn_failed)
                            .wrapped(R.string.screen_learning_error_e)
                            .message
                    }
                    current.copy(
                        keyQueue = current.keyQueue.map { if (it.id == currentKey.id) currentKey else it },
                        errorMessage = mapped
                    )
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
        val targetFile = resolvedDraftFile ?: return
        if (completed || _uiState.value.isRestoring) return
        val snapshot = _uiState.value.toDraftSnapshot()
        val hasPendingChange = snapshot != lastWrittenSnapshot &&
            !(lastWrittenSnapshot == null && snapshot == initialSettledSnapshot)
        if (!hasPendingChange) return
        val previousFlush = draftFlushJob
        draftFlushJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                previousFlush?.join()
                if (!completed) writeDraftSnapshot(targetFile, snapshot)
            }
        }
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
                if (epoch == testEpoch) _uiState.update {
                    it.copy(
                        testFeedbackMessage = e.toAppError(R.string.screen_hardware_error)
                            .wrapped(R.string.screen_send_error_t)
                            .message
                    )
                }
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

    fun addCustomKey(label: String, acAction: AcAction? = null) {
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
        if (acAction != null) bindCustomKeyToAcAction(newKey.id, acAction)
    }

    fun bindCustomKeyToAcAction(keyId: String, action: AcAction): Boolean {
        val state = _uiState.value
        val target = state.keyQueue.firstOrNull { it.id == keyId } ?: return false
        if (state.category != DeviceCategory.AC || state.isSaving || state.isRestoring || target.builtinLabel != null) return false
        if (state.keyQueue.any {
            it.id != keyId && it.acAction == action && (it.capturedSignal != null || it.builtinLabel == null)
        }) return false
        cancelTest()
        cancelCurrentKeyLearning()
        _uiState.update { current ->
            val queue = current.keyQueue.mapNotNull { item ->
                when {
                    item.id == keyId -> item.copy(
                        key = if (action == AcAction.POWER_ON) ButtonKey.POWER else action.buttonKey,
                        acAction = action
                    )
                    item.acAction == action && item.capturedSignal == null && item.builtinLabel != null -> null
                    else -> item
                }
            }
            current.copy(
                keyQueue = queue,
                currentIndex = queue.indexOfFirst { it.id == current.currentKey?.id }.takeIf { it >= 0 }
                    ?: queue.indexOfFirst { it.id == keyId }
            )
        }
        return true
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

    fun saveRemote(onSaved: (RemoteProfile) -> Unit = {}) {
        val state = _uiState.value
        if (state.isSaving || state.isRestoring || completed) return
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
                    key = item.key,
                    acAction = item.acAction,
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

        val profile = RemoteProfile(
            id = UUID.randomUUID().toString(),
            name = state.remoteName.trim(),
            brand = state.brand.trim(),
            category = state.category,
            applianceType = state.selectedApplianceType,
            useStaticAcButtons = state.category == DeviceCategory.AC && state.templateApplianceType == ApplianceType.AC,
            keySchemaVersion = LegacyRemoteMigration.KEY_SCHEMA_VERSION,
            buttons = learnedButtons,
            updatedAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            try {
                repository?.saveRemote(profile)
                if (completed) return@launch
                closeDraft().join()
                _savedRemote.value = profile
                onSaved(profile)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = e.toAppError(R.string.control_unknown_error)
                            .wrapped(R.string.screen_save_learning_failed).message
                    )
                }
            } finally { _uiState.update { it.copy(isSaving = false) } }
        }
    }

    fun discardDraft() {
        if (completed) return
        closeDraft()
        onScreenStopped()
    }

    private fun closeDraft(): Job {
        completed = true
        val writer = draftJob
        draftJob = null
        writer?.cancel()
        val flush = draftFlushJob
        draftFlushJob = null
        savedStateHandle?.remove<String>(KEY_WIZARD_STATE)
        savedStateHandle?.remove<String>(KEY_DRAFT_SESSION_ID)
        // Exiting the route may clear this ViewModel immediately. Finish the file cleanup first.
        return viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                writer?.cancelAndJoin()
                flush?.cancelAndJoin()
                resolvedDraftFile?.let { file ->
                    withContext(Dispatchers.IO) {
                        try {
                            if (!WizardDrafts.delete(file)) Log.w("WizardDrafts", "Could not delete wizard draft")
                        } catch (e: SecurityException) {
                            Log.w("WizardDrafts", "Could not delete wizard draft", e)
                        }
                    }
                }
            }
        }
    }

    fun reset(category: DeviceCategory = initialCategory) {
        cancelTest()
        cancelCurrentKeyLearning()
        _uiState.value = CustomRemoteWizardUiState(
            category = category,
            templateApplianceType = ApplianceType.fromCategory(category),
            keySchemaVersion = 1,
            keyQueue = generateDefaultQueue(category)
        )
    }

    fun handleBack(): Boolean {
        if (completed || _uiState.value.isSaving || _uiState.value.isRestoring) return true
        if (_uiState.value.pendingApplianceType != null) {
            cancelApplianceTypeChange()
            return true
        }
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
        private const val KEY_WIZARD_STATE = "wizard_state"
        private const val KEY_DRAFT_SESSION_ID = "wizard_draft_id"
        private const val LOCAL_DRAFT_MARKER = "local-draft"
        private const val DRAFT_WRITE_DEBOUNCE_MS = 300L

        private fun defaultKey(
            key: ButtonKey,
            iconName: String? = null,
            acAction: AcAction? = null
        ) = WizardKeyItem(
            label = key.name,
            builtinLabel = key.name,
            key = key,
            iconName = iconName,
            acAction = acAction
        )

        fun generateDefaultQueue(type: ApplianceType): List<WizardKeyItem> {
            val keys = when (type) {
                ApplianceType.SET_TOP_BOX, ApplianceType.IPTV -> generateDefaultQueue(DeviceCategory.TV) + listOf(
                    defaultKey(ButtonKey.CHANNEL_UP, iconName = "arrow_drop_up"),
                    defaultKey(ButtonKey.CHANNEL_DOWN, iconName = "arrow_drop_down")
                )
                ApplianceType.DVD -> generateDefaultQueue(DeviceCategory.AUDIO) + listOf(
                    defaultKey(ButtonKey.PLAY_PAUSE),
                    defaultKey(ButtonKey.STOP),
                    defaultKey(ButtonKey.PREVIOUS),
                    defaultKey(ButtonKey.NEXT),
                    defaultKey(ButtonKey.EJECT)
                )
                ApplianceType.CAMERA -> listOf(
                    defaultKey(ButtonKey.SHUTTER),
                    defaultKey(ButtonKey.SUB_SHUTTER)
                )
                ApplianceType.LIGHT -> listOf(
                    defaultKey(ButtonKey.POWER, iconName = "power"),
                    defaultKey(ButtonKey.BRIGHTNESS)
                )
                else -> generateDefaultQueue(type.category)
            }
            return keys
        }

        fun generateDefaultQueue(category: DeviceCategory): List<WizardKeyItem> {
            return when (category) {
                DeviceCategory.TV -> listOf(
                    defaultKey(ButtonKey.POWER, iconName = "power"),
                    defaultKey(ButtonKey.VOLUME_UP, iconName = "volume_up"),
                    defaultKey(ButtonKey.VOLUME_DOWN, iconName = "volume_down"),
                    defaultKey(ButtonKey.MUTE, iconName = "volume_off"),
                    defaultKey(ButtonKey.UP, iconName = "keyboard_arrow_up"),
                    defaultKey(ButtonKey.DOWN, iconName = "keyboard_arrow_down"),
                    defaultKey(ButtonKey.LEFT, iconName = "keyboard_arrow_left"),
                    defaultKey(ButtonKey.RIGHT, iconName = "keyboard_arrow_right"),
                    defaultKey(ButtonKey.OK, iconName = null),
                    defaultKey(ButtonKey.BACK, iconName = "arrow_back"),
                    defaultKey(ButtonKey.HOME, iconName = "home"),
                    defaultKey(ButtonKey.MENU, iconName = "menu")
                )
                DeviceCategory.AC -> listOf(
                    defaultKey(ButtonKey.POWER, acAction = AcAction.POWER_ON, iconName = "power"),
                    defaultKey(ButtonKey.TEMPERATURE_UP, acAction = AcAction.TEMP_UP, iconName = "arrow_drop_up"),
                    defaultKey(ButtonKey.TEMPERATURE_DOWN, acAction = AcAction.TEMP_DOWN, iconName = "arrow_drop_down"),
                    defaultKey(ButtonKey.MODE, acAction = AcAction.MODE, iconName = "tune"),
                    defaultKey(ButtonKey.FAN_SPEED, acAction = AcAction.FAN, iconName = "air"),
                    defaultKey(ButtonKey.SWING, acAction = AcAction.SWING, iconName = "swap_calls")
                )
                DeviceCategory.FAN -> listOf(
                    defaultKey(ButtonKey.POWER, iconName = "power"),
                    defaultKey(ButtonKey.FAN_SPEED, iconName = "speed"),
                    defaultKey(ButtonKey.OSCILLATION, iconName = "sync_alt"),
                    defaultKey(ButtonKey.TIMER, iconName = "timer"),
                    defaultKey(ButtonKey.MODE, iconName = "tune")
                )
                DeviceCategory.PROJECTOR -> listOf(
                    defaultKey(ButtonKey.POWER, iconName = "power"),
                    defaultKey(ButtonKey.UP, iconName = "keyboard_arrow_up"),
                    defaultKey(ButtonKey.DOWN, iconName = "keyboard_arrow_down"),
                    defaultKey(ButtonKey.LEFT, iconName = "keyboard_arrow_left"),
                    defaultKey(ButtonKey.RIGHT, iconName = "keyboard_arrow_right"),
                    defaultKey(ButtonKey.OK, iconName = null),
                    defaultKey(ButtonKey.BACK, iconName = "arrow_back"),
                    defaultKey(ButtonKey.MENU, iconName = "menu"),
                    defaultKey(ButtonKey.FOCUS_PLUS, iconName = "center_focus_strong"),
                    defaultKey(ButtonKey.FOCUS_MINUS, iconName = "filter_center_focus")
                )
                DeviceCategory.CUSTOM,
                DeviceCategory.AUDIO -> listOf(
                    defaultKey(ButtonKey.POWER, iconName = "power"),
                    defaultKey(ButtonKey.VOLUME_UP, iconName = "volume_up"),
                    defaultKey(ButtonKey.VOLUME_DOWN, iconName = "volume_down"),
                    defaultKey(ButtonKey.UP, iconName = "keyboard_arrow_up"),
                    defaultKey(ButtonKey.DOWN, iconName = "keyboard_arrow_down"),
                    defaultKey(ButtonKey.LEFT, iconName = "keyboard_arrow_left"),
                    defaultKey(ButtonKey.RIGHT, iconName = "keyboard_arrow_right"),
                    defaultKey(ButtonKey.OK, iconName = null)
                )
            }
        }
    }
}

