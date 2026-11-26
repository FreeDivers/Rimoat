package org.qinyan.rimoat.ui.viewmodel

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.i18n.uiTextDetail
import androidx.lifecycle.SavedStateHandle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.RemoteRepository
import org.qinyan.rimoat.data.db.IrBrand
import org.qinyan.rimoat.data.db.IrCategory
import org.qinyan.rimoat.data.db.IrCatalog
import org.qinyan.rimoat.data.db.IrRemoteModel
import org.qinyan.rimoat.ui.screens.BrandIndexGroup
import org.qinyan.rimoat.ui.screens.groupBrandsByIndex
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.TransmissionReceipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@kotlinx.serialization.Serializable
enum class AddRemoteStep {
    CATEGORY,
    BRAND,
    WIZARD,
    CONFIRM
}

data class AddRemoteUiState(
    val step: AddRemoteStep = AddRemoteStep.CATEGORY,
    val categories: List<IrCategory> = emptyList(),
    val selectedCategory: IrCategory? = null,
    val brands: List<IrBrand> = emptyList(),
    val brandGroupsCn: List<BrandIndexGroup>? = null,
    val brandGroupsEn: List<BrandIndexGroup>? = null,
    val selectedBrand: IrBrand? = null,
    val brandSearchQuery: String = "",
    val remotes: List<IrRemoteModel> = emptyList(),
    val currentRemoteIndex: Int = 0,
    val primaryTestKey: RemoteButton? = null,
    val secondaryTestKey: RemoteButton? = null,
    val allPreviewButtons: List<RemoteButton> = emptyList(),
    val isSendingTest: Boolean = false,
    val customRemoteName: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: UiText? = null,
    val testedButton: RemoteButton? = null,
    val canConfirmTest: Boolean = false,
    val verifiedFunctions: Set<String> = emptySet(),
    val testMessage: UiText? = null,
    val observations: Map<String, PairingObservation> = emptyMap(),
    val distinguishingKeys: Set<String> = emptySet(),
    val sessionRevision: Int = 0
) {
    val currentRemote: IrRemoteModel?
        get() = remotes.getOrNull(currentRemoteIndex)
}

class AddRemoteViewModel(
    private val irDbRepository: IrCatalog,
    private val remoteRepository: RemoteRepository,
    val transmitterManager: IrTransmitterManager,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val transmitSignals: suspend (List<IrSignal>, String) -> Result<TransmissionReceipt> = { signals, owner ->
        transmitterManager.transmitWithReceipt(signals, owner)
    }
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddRemoteUiState(isLoading = true))
    val uiState: StateFlow<AddRemoteUiState> = _uiState.asStateFlow()

    private val progressJson = Json { ignoreUnknownKeys = true }
    private val savedProgressJson: String? = savedStateHandle["pairing_progress"]
    private var progress = decodeProgress(savedProgressJson)
    private fun decodeProgress(json: String?): PairingProgress = runCatching {
        json?.let { progressJson.decodeFromString<PairingProgress>(it) }?.takeIf { it.version in 1..2 }
    }.getOrNull() ?: PairingProgress()
    private var restoring = true
    private var flowCompleted = false
    private fun brandKey(category: Int, brand: Int) = "$category:$brand"
    private fun checkpoint() {
        if (restoring || flowCompleted) return
        val state = _uiState.value
        progress = progress.copy(categoryId = state.selectedCategory?.id, brandId = state.selectedBrand?.brandIdx,
            step = state.step, query = state.brandSearchQuery)
        val category = state.selectedCategory
        val brand = state.selectedBrand
        val model = state.currentRemote
        if (category != null && brand != null && model != null && state.step in listOf(AddRemoteStep.WIZARD, AddRemoteStep.CONFIRM)) {
            val key = brandKey(category.id, brand.brandIdx)
            val previous = progress.brands[key] ?: BrandProgress()
            progress = progress.copy(brands = progress.brands + (key to previous.copy(remoteId = model.remoteId, name = state.customRemoteName)))
        }
        persistProgress()
    }
    private fun persistProgress() {
        if (progress == PairingProgress()) {
            savedStateHandle.remove<String>("pairing_progress")
            return
        }
        val serialized = Json.encodeToString(progress)
        if (savedStateHandle.get<String>("pairing_progress") == serialized) return
        savedStateHandle["pairing_progress"] = serialized
    }
    private fun observationsFor(remoteId: String, buttons: List<RemoteButton>): Map<String, PairingObservation> {
        val state = _uiState.value
        val key = brandKey(state.selectedCategory?.id ?: return emptyMap(), state.selectedBrand?.brandIdx ?: return emptyMap())
        val brand = progress.brands[key] ?: return emptyMap()
        val legacy = brand.verified[remoteId].orEmpty().mapValues { PairingObservation(it.value, PairingResult.VALID, sent = true) }
        val records = legacy + brand.observations[remoteId].orEmpty()
        return buttons.distinctBy { it.id }.mapNotNull { button ->
            records[button.id]?.takeIf { it.fingerprint == pairingFingerprint(button) }?.let { button.id to it }
        }.toMap()
    }
    private fun verifiedFor(remoteId: String, buttons: List<RemoteButton>): Set<String> {
        val records = observationsFor(remoteId, buttons)
        return buttons.filter { records[it.id]?.result == PairingResult.VALID }.map { it.label }.toSet()
    }
    private fun recordObservation(button: RemoteButton, observation: PairingObservation) {
        val state = _uiState.value
        val key = brandKey(state.selectedCategory?.id ?: return, state.selectedBrand?.brandIdx ?: return)
        val model = state.currentRemote?.remoteId ?: return
        val brand = progress.brands[key] ?: BrandProgress()
        progress = progress.copy(brands = progress.brands + (key to brand.copy(
            observations = brand.observations + (model to (brand.observations[model].orEmpty() + (button.id to observation))),
            verified = brand.verified + (model to (brand.verified[model].orEmpty() - button.id)))))
        persistProgress()
    }
    private suspend fun differences(model: IrRemoteModel, remotes: List<IrRemoteModel>, keys: List<RemoteButton>): Set<String> {
        if (keys.none { it.acAction == null && it.hasSignal }) return emptySet()
        val neighbour = remotes.firstOrNull { it.remoteId != model.remoteId } ?: return emptySet()
        return runCatching { distinguishingPairingKeys(keys, irDbRepository.buildRemoteProfile(neighbour).buttons) }
            .getOrElse { if (it is CancellationException) throw it else emptySet() }
    }

    private var loadJob: Job? = null
    private var loadEpoch = 0
    private var saveJob: Job? = null
    private var saveEpoch = 0
    private val saving: Boolean get() = _uiState.value.isSaving
    private fun cancelLoad(): Int {
        loadJob?.cancel()
        loadJob = null
        return ++loadEpoch
    }
    private var testJob: Job? = null
    private var testEpoch = 0
    private val feedbackOwner = java.util.UUID.randomUUID().toString()
    private val _feedback = MutableStateFlow<org.qinyan.rimoat.hardware.TransmitLog?>(null)
    val lastTransmitLog = _feedback.asStateFlow()
    fun dismissFeedback(log: org.qinyan.rimoat.hardware.TransmitLog) { _feedback.compareAndSet(log, null) }
    fun clearFeedback() { _feedback.value = null }
    fun dismissError(message: UiText) { _uiState.update { if (it.error == message) it.copy(error = null) else it } }


    init {
        viewModelScope.launch {
            transmitterManager.lastTransmitLog.collect { log -> if (log?.ownerId == feedbackOwner) _feedback.value = log }
        }
        loadCategories()
        viewModelScope.launch {
            uiState.collect { if (!it.isLoading) checkpoint() }
        }
    }

    private fun loadCategories() {
        val epoch = cancelLoad()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val categories = irDbRepository.getCategories()
                if (epoch != loadEpoch) return@launch
                _uiState.update { it.copy(categories = categories, isLoading = false) }
                val draft = progress
                val category = categories.firstOrNull { it.id == draft.categoryId }
                if (restoring && category != null && draft.step != AddRemoteStep.CATEGORY) {
                    _uiState.update { it.copy(selectedCategory = category, step = AddRemoteStep.BRAND,
                        brandSearchQuery = draft.query, isLoading = true) }
                    val brands = irDbRepository.getBrandsForCategory(category.id, draft.query)
                    val (groupsCn, groupsEn) = precomputeBrandGroups(brands)
                    if (epoch != loadEpoch) return@launch
                    _uiState.update { it.copy(brands = brands, brandGroupsCn = groupsCn, brandGroupsEn = groupsEn, isLoading = false) }
                    val brand = brands.firstOrNull { it.brandIdx == draft.brandId }
                        ?: if (draft.brandId != null && draft.query.isNotBlank() && draft.step in listOf(AddRemoteStep.WIZARD, AddRemoteStep.CONFIRM)) {
                            irDbRepository.getBrandsForCategory(category.id, "").firstOrNull { it.brandIdx == draft.brandId }
                        } else null
                    if (epoch != loadEpoch) return@launch
                    restoring = false
                    if (brand != null && draft.step in listOf(AddRemoteStep.WIZARD, AddRemoteStep.CONFIRM)) {
                        selectBrand(brand, draft.step)
                    }
                } else restoring = false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (epoch == loadEpoch) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = UiText.resource(R.string.msg_categories_load_failed, e.message ?: e.javaClass.simpleName)
                        )
                    }
                }
            } finally {
                restoring = false
            }
        }
    }

    fun selectCategory(category: IrCategory) {
        if (saving) return
        flowCompleted = false
        checkpoint()
        restoring = false
        val epoch = cancelLoad()
        _uiState.update { AddRemoteUiState(categories = it.categories, selectedCategory = category,
            step = AddRemoteStep.BRAND, isLoading = true, sessionRevision = it.sessionRevision) }
        loadJob = viewModelScope.launch { loadBrands(category.id, "", epoch) }
    }

    fun searchBrand(query: String) {
        val state = _uiState.value
        if (state.step != AddRemoteStep.BRAND || saving) return
        val categoryId = state.selectedCategory?.id ?: return
        restoring = false
        val epoch = cancelLoad()
        _uiState.update { it.copy(brandSearchQuery = query, isLoading = true, error = null) }
        loadJob = viewModelScope.launch { loadBrands(categoryId, query, epoch) }
    }

    private suspend fun precomputeBrandGroups(brands: List<IrBrand>): Pair<List<BrandIndexGroup>, List<BrandIndexGroup>> {
        if (android.os.Looper.getMainLooper() == null) {
            return groupBrandsByIndex(brands, false) to groupBrandsByIndex(brands, true)
        }
        return withContext(Dispatchers.Default) {
            groupBrandsByIndex(brands, false) to groupBrandsByIndex(brands, true)
        }
    }

    private suspend fun loadBrands(categoryId: Int, query: String, epoch: Int) {
        try {
            val brands = irDbRepository.getBrandsForCategory(categoryId, query)
            val (groupsCn, groupsEn) = precomputeBrandGroups(brands)
            if (epoch == loadEpoch) _uiState.update {
                it.copy(brands = brands, brandGroupsCn = groupsCn, brandGroupsEn = groupsEn, isLoading = false)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (epoch == loadEpoch) _uiState.update { it.copy(isLoading = false, error = UiText.resource(R.string.msg_brands_load_failed, e.message ?: e.javaClass.simpleName)) }
        }
    }

    fun selectBrand(brand: IrBrand, restoredStep: AddRemoteStep = AddRemoteStep.WIZARD) {
        val category = _uiState.value.selectedCategory ?: return
        if (saving) return
        restoring = false
        checkpoint()
        onScreenStopped()
        val saved = progress.brands[brandKey(category.id, brand.brandIdx)]
        val epoch = cancelLoad()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedBrand = brand, remotes = emptyList(),
                    verifiedFunctions = emptySet(), observations = emptyMap(), distinguishingKeys = emptySet(), testedButton = null, canConfirmTest = false, testMessage = null,
                    isLoading = true,
                    error = null
                )
            }
            try {
                val remotes = irDbRepository.getRemotesForBrand(category.id, brand.brandIdx)
                if (epoch != loadEpoch) return@launch
                if (remotes.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = UiText.resource(R.string.msg_brand_no_codes, brand.displayText())
                        )
                    }
                    return@launch
                }

                val restoredIndex = remotes.indexOfFirst { it.remoteId == saved?.remoteId }.coerceAtLeast(0)
                val firstRemote = remotes[restoredIndex]
                val (primaryKey, secondaryKey) = irDbRepository.getTestKeysForRemote(firstRemote.remoteId)
                val previewKeys = irDbRepository.buildRemoteProfile(firstRemote).buttons
                val distinctions = differences(firstRemote, remotes, previewKeys)

                val defaultName = "${brand.displayName()} ${localizedLabel(category.nameCn)}".take(100)
                if (epoch != loadEpoch) return@launch
                _uiState.update {
                    it.copy(
                        step = restoredStep,
                        remotes = remotes,
                        currentRemoteIndex = restoredIndex,
                        primaryTestKey = primaryKey,
                        secondaryTestKey = secondaryKey,
                        allPreviewButtons = previewKeys,
                        customRemoteName = saved?.name?.takeIf { it.isNotBlank() } ?: defaultName,
                        verifiedFunctions = verifiedFor(firstRemote.remoteId, previewKeys + listOfNotNull(primaryKey, secondaryKey)),
                        observations = observationsFor(firstRemote.remoteId, previewKeys + listOfNotNull(primaryKey, secondaryKey)),
                        distinguishingKeys = distinctions,
                        isLoading = false
                    )
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (epoch == loadEpoch) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = UiText.resource(R.string.msg_codes_load_failed, e.message ?: e.javaClass.simpleName)
                        )
                    }
                }
            }
        }
    }

    private val testPressCounts = mutableMapOf<String, Int>()
    fun testSignal(button: RemoteButton) {
        if (_uiState.value.isSendingTest || _uiState.value.isLoading || _uiState.value.step != AddRemoteStep.WIZARD) return
        val modelId = _uiState.value.currentRemote?.remoteId ?: return
        val epoch = ++testEpoch
        _uiState.update { it.copy(isSendingTest = true, testedButton = button, canConfirmTest = false, testMessage = null) }
        testJob = viewModelScope.launch {
            try {
                val countKey = "${transmitterManager.currentTransmitterType()}:${button.id}"
                val index = testPressCounts[countKey] ?: 0
                val model = _uiState.value.currentRemote ?: run {
                    _uiState.update { it.copy(isSendingTest = false, testMessage = UiText.Resource(R.string.msg_code_set_closed)) }
                    return@launch
                }
                val signals = if (button.acAction != null) irDbRepository.testSignalsFor(model, button)
                    else listOf(button.toIrSignal(index))
                val receipt = transmitSignals(signals, feedbackOwner).getOrThrow()
                testPressCounts[countKey] = (index + 1) and 1
                if (epoch == testEpoch && _uiState.value.currentRemote?.remoteId == modelId) {
                    val prior = _uiState.value.observations[button.id]?.takeIf { it.fingerprint == pairingFingerprint(button) }
                    val observation = (prior ?: PairingObservation(pairingFingerprint(button))).copy(sent = true, simulated = receipt.simulated)
                    recordObservation(button, observation)
                    _uiState.update {
                        it.copy(
                            canConfirmTest = !receipt.simulated,
                            observations = it.observations + (button.id to observation),
                            testMessage = if (receipt.simulated) {
                                UiText.Resource(R.string.msg_simulated_send)
                            } else {
                                UiText.resource(R.string.msg_button_sent, UiText.ButtonLabel(button))
                            }
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (epoch == testEpoch) {
                    _uiState.update {
                        it.copy(testMessage = UiText.resource(R.string.msg_test_failed, e.uiTextDetail(R.string.control_unknown_error)))
                    }
                }
            }
            finally { if (epoch == testEpoch) _uiState.update { it.copy(isSendingTest = false) } }
        }
    }

    fun confirmTest(responded: Boolean) {
        val state = _uiState.value
        val button = state.testedButton ?: return
        if (!state.canConfirmTest) return
        val observation = PairingObservation(pairingFingerprint(button),
            if (responded) PairingResult.VALID else PairingResult.INVALID, sent = true)
        recordObservation(button, observation)
        _uiState.update {
            it.copy(
                canConfirmTest = false,
                observations = it.observations + (button.id to observation),
                verifiedFunctions = if (responded) it.verifiedFunctions + button.label else it.verifiedFunctions - button.label,
                testMessage = if (responded) {
                    UiText.resource(R.string.msg_response_recorded, UiText.ButtonLabel(button))
                } else {
                    UiText.resource(R.string.msg_no_response_recorded, UiText.ButtonLabel(button))
                }
            )
        }
    }

    fun nextModel() {
        val state = _uiState.value
        if (state.currentRemoteIndex < state.remotes.size - 1) {
            updateModelIndex(state.currentRemoteIndex + 1)
        }
    }

    fun prevModel() {
        val state = _uiState.value
        if (state.currentRemoteIndex > 0) {
            updateModelIndex(state.currentRemoteIndex - 1)
        }
    }

    private fun updateModelIndex(newIndex: Int) {
        if (_uiState.value.isSendingTest || _uiState.value.isLoading) return
        checkpoint()
        clearFeedback()
        val remotes = _uiState.value.remotes
        val model = remotes.getOrNull(newIndex) ?: return

        val epoch = cancelLoad()
        _uiState.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val (primaryKey, secondaryKey) = irDbRepository.getTestKeysForRemote(model.remoteId)
                val previewKeys = irDbRepository.buildRemoteProfile(model).buttons
                val distinctions = differences(model, remotes, previewKeys)
                if (epoch != loadEpoch) return@launch
                _uiState.update { it.copy(currentRemoteIndex = newIndex,
                    verifiedFunctions = verifiedFor(model.remoteId, previewKeys + listOfNotNull(primaryKey, secondaryKey)),
                    observations = observationsFor(model.remoteId, previewKeys + listOfNotNull(primaryKey, secondaryKey)),
                    distinguishingKeys = distinctions, testedButton = null, canConfirmTest = false, testMessage = null,
                    primaryTestKey = primaryKey, secondaryTestKey = secondaryKey,
                    allPreviewButtons = previewKeys, isLoading = false) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (epoch == loadEpoch) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = UiText.resource(R.string.msg_codes_load_failed_localized, e.localizedMessage ?: e.javaClass.simpleName)
                        )
                    }
                }
            }
        }
    }

    fun proceedToConfirm() {
        if (_uiState.value.isSendingTest || _uiState.value.isLoading) return
        _uiState.update { it.copy(step = AddRemoteStep.CONFIRM, error = null) }
    }

    fun updateCustomName(name: String) {
        _uiState.update { it.copy(customRemoteName = name.take(100), error = null) }
    }

    fun saveRemote(onSaved: (RemoteProfile) -> Unit) {
        val state = _uiState.value
        val model = state.currentRemote ?: return
        if (state.isSaving || state.isLoading) return
        val epoch = ++saveEpoch
        _uiState.update { it.copy(isLoading = true, isSaving = true, error = null) }
        saveJob = viewModelScope.launch {
            try {
                val profile = irDbRepository.buildRemoteProfile(model, state.customRemoteName)
                    .copy(verifiedFunctions = state.verifiedFunctions.toList())
                if (epoch != saveEpoch) return@launch
                remoteRepository.saveRemote(profile)
                if (epoch != saveEpoch) return@launch
                flowCompleted = true
                progress = PairingProgress()
                savedStateHandle.remove<String>("pairing_progress")
                testPressCounts.clear()
                clearFeedback()
                _uiState.update { it.copy(isLoading = false, isSaving = false) }
                onSaved(profile)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (epoch != saveEpoch) return@launch
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = UiText.resource(R.string.msg_remote_save_failed, e.message ?: e.javaClass.simpleName)
                    )
                }
            } finally {
                if (epoch == saveEpoch) {
                    _uiState.update { it.copy(isSaving = false) }
                }
            }
        }
    }

    fun restartMatching() {
        val state = _uiState.value
        val category = state.selectedCategory ?: return
        val brand = state.selectedBrand ?: return
        if (saving || state.isSendingTest) return
        progress = progress.copy(brands = progress.brands - brandKey(category.id, brand.brandIdx))
        _uiState.update { it.copy(remotes = emptyList(), verifiedFunctions = emptySet(), customRemoteName = "") }
        testPressCounts.clear()
        selectBrand(brand)
    }

    fun reset() {
        flowCompleted = false
        restoring = false
        progress = PairingProgress()
        savedStateHandle.remove<String>("pairing_progress")
        testPressCounts.clear()
        saveEpoch++
        saveJob?.cancel()
        saveJob = null
        cancelLoad()
        testEpoch++
        testJob?.cancel()
        clearFeedback()
        _uiState.update {
            AddRemoteUiState(
                step = AddRemoteStep.CATEGORY,
                categories = it.categories,
                isLoading = false,
                sessionRevision = it.sessionRevision + 1
            )
        }
        if (_uiState.value.categories.isEmpty()) loadCategories()
    }

    fun onScreenStopped() {
        checkpoint()
        testEpoch++
        testJob?.cancel()
        testJob = null
        clearFeedback()
        _uiState.update { it.copy(isSendingTest = false, canConfirmTest = false, testMessage = null) }
    }

    fun handleBack(): Boolean {
        if (saving || _uiState.value.isSendingTest) return true
        restoring = false
        cancelLoad()
        _uiState.update { it.copy(isLoading = false, error = null) }
        clearFeedback()
        checkpoint()
        val current = _uiState.value.step
        return when (current) {
            AddRemoteStep.CONFIRM -> {
                _uiState.update { it.copy(step = AddRemoteStep.WIZARD) }
                true
            }
            AddRemoteStep.WIZARD -> {
                onScreenStopped()
                _uiState.update { it.copy(step = AddRemoteStep.BRAND, testedButton = null) }
                true
            }
            AddRemoteStep.BRAND -> {
                _uiState.update { it.copy(step = AddRemoteStep.CATEGORY) }
                true
            }
            AddRemoteStep.CATEGORY -> false
        }
    }
}

private fun IrBrand.displayName(): String = if (AppStrings.currentLanguage != "zh") nameEn.ifBlank { nameCn } else nameCn

private fun IrBrand.displayText(): UiText = if (nameCn == "其他" || nameCn == "其它") {
    UiText.Resource(R.string.screen_other_brand)
} else UiText.CatalogBrand(nameCn, nameEn)
