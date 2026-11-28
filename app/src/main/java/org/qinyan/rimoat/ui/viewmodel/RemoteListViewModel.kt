package org.qinyan.rimoat.ui.viewmodel

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppError
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

    private val appearance = AppearanceSettingsViewModel(themePreferences)
    val useDynamicColor: StateFlow<Boolean> = appearance.useDynamicColor
    val colorMode: StateFlow<AppColorMode> = appearance.colorMode
    val pureBlackBackground: StateFlow<Boolean> = appearance.pureBlackBackground
    val isDynamicColorSupported: Boolean = appearance.isDynamicColorSupported
    val rotationDirection: StateFlow<ScreenRotationDirection> = appearance.rotationDirection
    val autoRotateScreen: StateFlow<Boolean> = appearance.autoRotateScreen
    val rotationCondition: StateFlow<ScreenRotationCondition> = appearance.rotationCondition
    val rotationScope: StateFlow<ScreenRotationScope> = appearance.rotationScope
    val suppressRotationPrompt: StateFlow<Boolean> = appearance.suppressRotationPrompt

    private val roomModel = RoomViewModel(repository, ::performStorageOperation)
    val selectedRoom = roomModel.selectedRoom
    val rooms = roomModel.rooms
    val allRemotes = roomModel.allRemotes
    fun editRoom(oldName: String?, newName: String?, onSaved: () -> Unit) = roomModel.editRoom(oldName, newName, onSaved)
    fun moveToRoom(id: String, room: String) = roomModel.moveToRoom(id, room)
    private val _recentFirst = MutableStateFlow(false)
    val recentFirst = _recentFirst.asStateFlow()
    fun selectRoom(room: String?) = roomModel.selectRoom(room)
    fun setRecentFirst(value: Boolean) { _recentFirst.value = value }

    val filteredRemotes: StateFlow<List<RemoteProfile>> = combine(
        repository.remotes,
        _searchQuery,
        _selectedCategory, selectedRoom, _recentFirst
    ) { remotes, query, category, room, recent ->
        remotes.filter { remote ->
            val matchesCategory = category == null || remote.category == category
            val matchesQuery = query.isBlank() ||
                remote.name.contains(query, ignoreCase = true) ||
                remote.brand.contains(query, ignoreCase = true) ||
                remote.room.contains(query, ignoreCase = true) ||
                remote.category.catalogSearchAlias.contains(query, ignoreCase = true) ||
                localizedLabel(remote.category).contains(query, ignoreCase = true)
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
    val errorAppMessage: StateFlow<AppError?> = combine(
        _operationAppError,
        repository.errorAppMessage,
        repository.errorMessage
    ) { operation, storageApp, storageText ->
        operation ?: storageApp ?: storageText?.let { AppError.Resource(it) }
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
    private val trashModel = TrashViewModel(repository, viewModelScope, _deletedIds, ::performStorageOperation, ::setOperationError)
    val trash = trashModel.trash
    val trashBusy = trashModel.trashBusy
    fun loadTrash() = trashModel.loadTrash()
    fun restoreDeleted(id: String, entryId: String? = null) = trashModel.restoreDeleted(id, entryId)
    fun restoreDeleted(ids: List<String>) = trashModel.restoreDeleted(ids)
    fun permanentlyDelete(entryId: String, onDeleted: () -> Unit) = trashModel.permanentlyDelete(entryId, onDeleted)
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

    private val backup = BackupViewModel(repository, backupFiles, isDynamicColorSupported, viewModelScope, ::setOperationError)
    val pendingImport = backup.pendingImport
    val transferBusy = backup.transferBusy
    val transferMessage = backup.transferMessage
    fun reportTransferError(error: AppError) = backup.reportTransferError(error)
    fun reportTransferError(message: UiText) = backup.reportTransferError(message)
    fun clearTransferMessage() = backup.clearTransferMessage()
    fun cancelImport() = backup.cancelImport()
    fun readImport(uri: android.net.Uri) = backup.readImport(uri)
    fun readInfraredImport(uri: android.net.Uri) = backup.readInfraredImport(uri)
    fun commitImport(mode: ImportMode) = backup.commitImport(mode)
    fun commitImport(choices: Map<String, ConflictChoice>, preferences: PortablePreferences?) = backup.commitImport(choices, preferences)
    fun retryLastExport(): Boolean = backup.retryLastExport()
    fun export(uri: android.net.Uri, id: String?, preferences: PortablePreferences? = null, onSaved: () -> Unit) =
        backup.export(uri, id, preferences, onSaved)

    fun setUseDynamicColor(useDynamic: Boolean) {
        appearance.setUseDynamicColor(useDynamic)
    }

    fun setColorMode(mode: AppColorMode) {
        appearance.setColorMode(mode)
    }

    fun setPureBlackBackground(enabled: Boolean) {
        appearance.setPureBlackBackground(enabled)
    }

    fun setRotationDirection(direction: ScreenRotationDirection) {
        appearance.setRotationDirection(direction)
    }

    fun setAutoRotateScreen(enabled: Boolean) {
        appearance.setAutoRotateScreen(enabled)
    }

    fun setRotationCondition(condition: ScreenRotationCondition) {
        appearance.setRotationCondition(condition)
    }

    fun setRotationScope(scope: ScreenRotationScope) {
        appearance.setRotationScope(scope)
    }

    fun setSuppressRotationPrompt(suppressed: Boolean) {
        appearance.setSuppressRotationPrompt(suppressed)
    }
}
