package org.qinyan.rimoat.ui.viewmodel

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppError
import org.qinyan.rimoat.i18n.toAppError
import org.qinyan.rimoat.i18n.wrapped
import androidx.lifecycle.viewModelScope
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.data.RemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update

import kotlinx.coroutines.CoroutineScope

internal class TrashViewModel(
    private val repository: RemoteRepository,
    private val viewModelScope: CoroutineScope,
    private val _deletedIds: MutableStateFlow<List<String>>,
    private val performStorageOperation: (suspend () -> Unit) -> Unit,
    private val setOperationError: (AppError?) -> Unit
) {
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
}
