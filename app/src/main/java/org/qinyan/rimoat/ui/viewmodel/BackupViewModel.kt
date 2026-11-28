package org.qinyan.rimoat.ui.viewmodel

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppError
import org.qinyan.rimoat.i18n.ErrorAction
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.toAppError
import org.qinyan.rimoat.i18n.wrapped
import androidx.lifecycle.viewModelScope
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.data.RemoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

import kotlinx.coroutines.CoroutineScope

internal class BackupViewModel(
    private val repository: RemoteRepository,
    private val backupFiles: BackupFileStore?,
    private val isDynamicColorSupported: Boolean,
    private val viewModelScope: CoroutineScope,
    private val setOperationError: (AppError?) -> Unit
) {
    private val _pendingImport = MutableStateFlow<ImportDocument?>(null)
    val pendingImport = _pendingImport.asStateFlow()
    private val _transferBusy = MutableStateFlow(false)
    val transferBusy = _transferBusy.asStateFlow()
    private val _transferMessage = MutableStateFlow<UiText?>(null)
    val transferMessage = _transferMessage.asStateFlow()
    fun reportTransferError(error: AppError) { setOperationError(error) }
    fun reportTransferError(message: UiText) { reportTransferError(AppError.Resource(message)) }
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

}
