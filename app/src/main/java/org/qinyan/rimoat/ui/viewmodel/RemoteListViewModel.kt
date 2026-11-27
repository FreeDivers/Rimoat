package org.qinyan.rimoat.ui.viewmodel

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppError
import org.qinyan.rimoat.i18n.ErrorAction
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.i18n.toAppError
import org.qinyan.rimoat.i18n.wrapped
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.RemoteRepository
import org.qinyan.rimoat.data.ThemePreferences
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.TransmitterType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update

class RemoteListViewModel(
    private val repository: RemoteRepository,
    val transmitterManager: IrTransmitterManager,
    private val themePreferences: ThemePreferences,
    private val backupFiles: org.qinyan.rimoat.data.BackupFileStore? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<DeviceCategory?>(null)
    val selectedCategory: StateFlow<DeviceCategory?> = _selectedCategory.asStateFlow()

    val transmitterType: StateFlow<TransmitterType> = transmitterManager.transmitterType
    val selectedTransmitterType: StateFlow<TransmitterType?> = transmitterManager.selectedTransmitterType
    val isHardwareReady: StateFlow<Boolean> = transmitterManager.isReady
    val isForceMock: StateFlow<Boolean> = transmitterManager.forceMockMode

    val useDynamicColor: StateFlow<Boolean> = themePreferences.useDynamicColor
    val colorMode: StateFlow<AppColorMode> = themePreferences.colorMode
    val pureBlackBackground: StateFlow<Boolean> = themePreferences.pureBlackBackground
    val isDynamicColorSupported: Boolean = themePreferences.isDynamicColorSupported
    val rotationDirection: StateFlow<ScreenRotationDirection> = themePreferences.rotationDirection
    val autoRotateScreen: StateFlow<Boolean> = themePreferences.autoRotateScreen
    val rotationCondition: StateFlow<ScreenRotationCondition> = themePreferences.rotationCondition
    val rotationScope: StateFlow<ScreenRotationScope> = themePreferences.rotationScope
    val suppressRotationPrompt: StateFlow<Boolean> = themePreferences.suppressRotationPrompt

    private val _room = MutableStateFlow<String?>(null)
    val selectedRoom = _room.asStateFlow()
    val rooms = repository.rooms
    val allRemotes = repository.remotes
    fun editRoom(oldName: String?, newName: String?, onSaved: () -> Unit) = performStorageOperation {
        repository.editRoom(oldName, newName)
        if (_room.value == oldName && oldName != null) _room.value = newName?.trim()
        onSaved()
    }
    fun moveToRoom(id: String, room: String) = performStorageOperation { repository.moveToRoom(id, room) }
    private val _recentFirst = MutableStateFlow(false)
    val recentFirst = _recentFirst.asStateFlow()
    fun selectRoom(room: String?) { _room.value = room }
    fun setRecentFirst(value: Boolean) { _recentFirst.value = value }

    val filteredRemotes: StateFlow<List<RemoteProfile>> = combine(
        repository.remotes,
        _searchQuery,
        _selectedCategory, _room, _recentFirst
    ) { remotes, query, category, room, recent ->
        remotes.filter { remote ->
            val matchesCategory = category == null || remote.category == category
            val matchesQuery = query.isBlank() ||
                remote.name.contains(query, ignoreCase = true) ||
                remote.brand.contains(query, ignoreCase = true) ||
                remote.room.contains(query, ignoreCase = true) ||
                when (remote.category) {
                    DeviceCategory.TV -> "电视"; DeviceCategory.AC -> "空调"; DeviceCategory.FAN -> "风扇"
                    DeviceCategory.AUDIO -> "音响"; DeviceCategory.PROJECTOR -> "投影仪"; DeviceCategory.CUSTOM -> "自定义"
                }.let { it.contains(query, ignoreCase = true) || localizedLabel(it).contains(query, ignoreCase = true) }
            matchesCategory && matchesQuery && (room == null || remote.room == room)
        }.let { if (recent) it.sortedByDescending { remote -> remote.lastUsedAt } else it }
    }.distinctUntilChanged { old, new ->
        old.size == new.size && old.indices.all { i ->
            old[i].copy(lastUsedAt = 0L, lastSentAt = 0L) == new[i].copy(lastUsedAt = 0L, lastSentAt = 0L)
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: DeviceCategory?) {
        _selectedCategory.value = category
    }

    private val _operationAppError = MutableStateFlow<AppError?>(null)
    private val _operationError = MutableStateFlow<UiText?>(null)
    val errorMessage = combine(_operationError, repository.errorMessage) { operation, storage ->
        operation ?: storage
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val errorAppMessage: StateFlow<AppError?> = combine(_operationAppError, repository.errorMessage) { operation, storage ->
        operation ?: storage?.let { AppError.Resource(it) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun setOperationError(error: AppError?) {
        _operationAppError.value = error
        _operationError.value = error?.message
    }

    fun clearError() {
        _operationAppError.value = null
        _operationError.value = null
        repository.clearError()
    }

    private fun performStorageOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                setOperationError(
                    e.toAppError(R.string.msg_storage_unavailable).wrapped(R.string.msg_operation_failed)
                )
            }
        }
    }

    fun toggleFavorite(remoteId: String) {
        performStorageOperation {
            repository.toggleFavorite(remoteId)
        }
    }

    private val _deletedIds = MutableStateFlow<List<String>>(emptyList())
    val deletedIds: StateFlow<List<String>> = _deletedIds.asStateFlow()
    fun consumeDeleted(ids: List<String>) { _deletedIds.compareAndSet(ids, emptyList()) }
    private val _trash = MutableStateFlow<List<org.qinyan.rimoat.data.DeletedRemoteEntry>?>(null)
    val trash = _trash.asStateFlow()
    fun loadTrash() = performStorageOperation { _trash.value = repository.deletedEntries() }
    private val _trashBusy = MutableStateFlow(false)
    val trashBusy = _trashBusy.asStateFlow()
    fun restoreDeleted(id: String, entryId: String? = null) {
        if (!_trashBusy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                repository.restoreDeleted(id, entryId)
                _deletedIds.update { current -> current.filterNot { it == id } }
                if (_trash.value != null) _trash.value = repository.deletedEntries()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                setOperationError(
                    e.toAppError(R.string.msg_storage_unavailable).wrapped(R.string.msg_operation_failed)
                )
            }
            finally { _trashBusy.value = false }
        }
    }
    fun restoreDeleted(ids: List<String>) {
        if (ids.isEmpty() || !_trashBusy.compareAndSet(false, true)) return
        viewModelScope.launch {
            val restored = mutableSetOf<String>()
            try {
                ids.asReversed().forEach { id ->
                    repository.restoreDeleted(id)
                    restored += id
                }
                if (_trash.value != null) _trash.value = repository.deletedEntries()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                setOperationError(
                    e.toAppError(R.string.msg_storage_unavailable).wrapped(R.string.msg_operation_failed)
                )
            }
            finally {
                if (restored.isNotEmpty()) {
                    _deletedIds.update { current -> current.filterNot(restored::contains) }
                }
                _trashBusy.value = false
            }
        }
    }
    fun permanentlyDelete(entryId: String, onDeleted: () -> Unit) {
        if (!_trashBusy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                repository.permanentlyDelete(entryId)
                _trash.value = repository.deletedEntries()
                onDeleted()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                setOperationError(
                    e.toAppError(R.string.msg_storage_unavailable).wrapped(R.string.msg_permanent_delete_failed)
                )
            }
            finally { _trashBusy.value = false }
        }
    }
    fun updateDetails(id: String, name: String, room: String, onSaved: () -> Unit) = performStorageOperation {
        repository.updateDetails(id, name, room); onSaved()
    }
    fun deleteRemote(remoteId: String): Deferred<Boolean> = viewModelScope.async {
        try {
            val removed = repository.deleteRemote(remoteId)
            if (removed) {
                _deletedIds.update { current -> if (remoteId in current) current else current + remoteId }
            }
            removed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setOperationError(
                e.toAppError(R.string.msg_storage_unavailable).wrapped(R.string.msg_operation_failed)
            )
            false
        }
    }

    private val _pendingImport = MutableStateFlow<ImportDocument?>(null)
    val pendingImport = _pendingImport.asStateFlow()
    private val _transferBusy = MutableStateFlow(false)
    val transferBusy = _transferBusy.asStateFlow()
    private val _transferMessage = MutableStateFlow<UiText?>(null)
    val transferMessage = _transferMessage.asStateFlow()
    fun reportTransferError(error: AppError) { setOperationError(error) }
    fun reportTransferError(message: UiText) { reportTransferError(AppError.Resource(message)) }
    fun reportTransferError(message: String) { reportTransferError(UiText.Dynamic(message)) }
    fun clearTransferMessage() { _transferMessage.value = null }
    fun cancelImport() { if (!_transferBusy.value) _pendingImport.value = null }
    private fun transfer(defaultAction: ErrorAction? = null, block: suspend () -> Unit) {
        if (_transferBusy.value) return
        _transferBusy.value = true
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: BackupWriteException) {
                setOperationError(AppError.Resource(UiText.Resource(R.string.backup_export_write_failed), defaultAction))
            }
            catch (e: Exception) {
                setOperationError(
                    e.toAppError(R.string.msg_file_unavailable, defaultAction)
                        .wrapped(R.string.msg_file_operation_failed, defaultAction)
                )
            }
            finally { _transferBusy.value = false }
        }
    }
    fun readImport(uri: android.net.Uri) = transfer {
        _pendingImport.value = checkNotNull(backupFiles).readDocument(uri)
    }
    fun readInfraredImport(uri: android.net.Uri) = transfer {
        _pendingImport.value = checkNotNull(backupFiles).readDocument(uri, infrared = true)
    }
    fun commitImport(mode: ImportMode) = transfer {
        val document = _pendingImport.value ?: return@transfer
        val count = repository.importBackup(document.backup, emptyMap(), copyAll = mode == ImportMode.COPIES)
        _pendingImport.value = null
        _transferMessage.value = UiText.plural(R.plurals.msg_import_finished, count, count)
    }
    fun commitImport(choices: Map<String, ConflictChoice>, preferences: PortablePreferences?) = transfer {
        val document = _pendingImport.value ?: return@transfer
        val applicablePreferences = preferences?.let {
            if (isDynamicColorSupported) it else it.copy(dynamicColors = null)
        }?.takeIf { it.dynamicColors != null || it.pureBlack != null }
        val count = repository.importBackup(document.backup, choices, applicablePreferences)
        _pendingImport.value = null
        _transferMessage.value = UiText.plural(R.plurals.msg_import_finished, count, count)
    }
    private var lastExportAction: (() -> Unit)? = null
    fun retryLastExport(): Boolean {
        val action = lastExportAction ?: return false
        action()
        return true
    }

    fun export(uri: android.net.Uri, id: String?, preferences: PortablePreferences? = null, onSaved: () -> Unit) {
        lastExportAction = { export(uri, id, preferences, onSaved) }
        transfer(defaultAction = ErrorAction.Retry) {
            val text = repository.exportRemotes(id, preferences)
            checkNotNull(backupFiles).write(uri, text)
            lastExportAction = null
            onSaved()
        }
    }

    fun setUseDynamicColor(useDynamic: Boolean) {
        themePreferences.setUseDynamicColor(useDynamic)
    }

    fun setColorMode(mode: AppColorMode) {
        themePreferences.setColorMode(mode)
    }

    fun setPureBlackBackground(enabled: Boolean) {
        themePreferences.setPureBlackBackground(enabled)
    }

    fun setRotationDirection(direction: ScreenRotationDirection) {
        themePreferences.setRotationDirection(direction)
    }

    fun setAutoRotateScreen(enabled: Boolean) {
        themePreferences.setAutoRotateScreen(enabled)
    }

    fun setRotationCondition(condition: ScreenRotationCondition) {
        themePreferences.setRotationCondition(condition)
    }

    fun setRotationScope(scope: ScreenRotationScope) {
        themePreferences.setRotationScope(scope)
    }

    fun setSuppressRotationPrompt(suppressed: Boolean) {
        themePreferences.setSuppressRotationPrompt(suppressed)
    }
}
