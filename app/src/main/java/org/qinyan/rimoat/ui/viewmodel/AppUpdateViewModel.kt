package org.qinyan.rimoat.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.qinyan.rimoat.data.update.AppRelease
import org.qinyan.rimoat.data.update.ReleaseVersion
import org.qinyan.rimoat.data.update.UpdateException
import org.qinyan.rimoat.data.update.UpdateFailure
import org.qinyan.rimoat.data.update.UpdateRepository

internal enum class UpdatePhase {
    CLOSED, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, PERMISSION, WAITING_PERMISSION,
    INSTALLING, DOWNLOADED, ERROR,
}

internal data class AppUpdateState(
    val phase: UpdatePhase = UpdatePhase.CLOSED,
    val release: AppRelease? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val apk: File? = null,
    val failure: UpdateFailure? = null,
)

internal class AppUpdateViewModel(
    private val repository: UpdateRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    val installedVersion = repository.installedVersion
    private val mutableState = MutableStateFlow(restore())
    val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0

    private fun restore(): AppUpdateState {
        val path = savedState.get<String>("update_apk") ?: return AppUpdateState()
        val tag = savedState.get<String>("update_tag") ?: return AppUpdateState()
        val file = File(path)
        val latest = ReleaseVersion.parse(tag)
        val installed = ReleaseVersion.parse(installedVersion)
        if (!file.isFile || latest == null || installed == null || latest <= installed) {
            file.delete()
            return AppUpdateState()
        }
        return AppUpdateState(
            phase = if (savedState.get<Boolean>("update_waiting") == true) UpdatePhase.WAITING_PERMISSION else UpdatePhase.DOWNLOADED,
            release = AppRelease(tag, "", null),
            apk = file,
        )
    }

    fun check() {
        cancel()
        val request = generation
        mutableState.value = AppUpdateState(UpdatePhase.CHECKING)
        operation = viewModelScope.launch {
            try {
                val release = withTimeout(30_000) { repository.latestRelease() }
                val installed = ReleaseVersion.parse(installedVersion) ?: throw UpdateException(UpdateFailure.INVALID_RELEASE)
                val latest = ReleaseVersion.parse(release.tag) ?: throw UpdateException(UpdateFailure.INVALID_RELEASE)
                if (request == generation) mutableState.value = AppUpdateState(
                    phase = if (latest > installed) UpdatePhase.AVAILABLE else UpdatePhase.CURRENT,
                    release = release,
                )
            } catch (_: TimeoutCancellationException) {
                fail(UpdateFailure.CHECK_TIMEOUT, request)
            } catch (_: SocketTimeoutException) {
                fail(UpdateFailure.CHECK_TIMEOUT, request)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                fail((error as? UpdateException)?.failure ?: UpdateFailure.NETWORK, request)
            }
        }
    }

    fun download() {
        val current = state.value
        if (current.phase !in setOf(UpdatePhase.AVAILABLE, UpdatePhase.ERROR)) return
        val release = current.release ?: return
        if (release.asset == null) { mutableState.value = current.copy(failure = UpdateFailure.NO_APK); return }
        val request = ++generation
        operation?.cancel()
        mutableState.value = current.copy(phase = UpdatePhase.DOWNLOADING, failure = null, downloadedBytes = 0,
            totalBytes = release.asset.size)
        operation = viewModelScope.launch {
            try {
                var lastPercent = -1
                val file = repository.download(release) { bytes, total ->
                    val percent = if (total > 0) (bytes.toDouble() * 100 / total).toInt() else 0
                    if (percent != lastPercent) {
                        lastPercent = percent
                        viewModelScope.launch {
                            if (request == generation) mutableState.value = mutableState.value.copy(downloadedBytes = bytes, totalBytes = total)
                        }
                    }
                }
                if (request != generation) { file.delete(); return@launch }
                savedState["update_apk"] = file.absolutePath
                savedState["update_tag"] = release.tag
                mutableState.value = state.value.copy(phase = UpdatePhase.READY, apk = file)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                fail((error as? UpdateException)?.failure ?: UpdateFailure.DOWNLOAD, request)
            }
        }
    }

    fun requirePermission() { mutableState.value = state.value.copy(phase = UpdatePhase.PERMISSION) }
    fun awaitingPermission() {
        savedState["update_waiting"] = true
        mutableState.value = state.value.copy(phase = UpdatePhase.WAITING_PERMISSION, failure = null)
    }
    fun permissionResult(granted: Boolean) {
        if (state.value.phase != UpdatePhase.WAITING_PERMISSION) return
        savedState["update_waiting"] = false
        mutableState.value = state.value.copy(phase = if (granted) UpdatePhase.READY else UpdatePhase.PERMISSION,
            failure = if (granted) null else UpdateFailure.PERMISSION)
    }
    fun installing() { mutableState.value = state.value.copy(phase = UpdatePhase.INSTALLING, failure = null) }
    fun installerReturned() {
        if (state.value.phase == UpdatePhase.INSTALLING) mutableState.value = state.value.copy(phase = UpdatePhase.DOWNLOADED)
    }
    fun installAgain() { mutableState.value = state.value.copy(phase = UpdatePhase.READY, failure = null) }
    fun installationFailed() { mutableState.value = state.value.copy(phase = UpdatePhase.DOWNLOADED, failure = UpdateFailure.INSTALLER) }
    fun permissionUnavailable() {
        savedState["update_waiting"] = false
        mutableState.value = state.value.copy(phase = UpdatePhase.PERMISSION, failure = UpdateFailure.PERMISSION)
    }

    fun cancel() {
        generation++
        operation?.cancel()
        operation = null
        val current = state.value
        // The installer may still be reading its content URI while our Activity is stopped.
        if (current.phase != UpdatePhase.INSTALLING) current.apk?.delete()
        savedState.remove<String>("update_apk")
        savedState.remove<String>("update_tag")
        savedState.remove<Boolean>("update_waiting")
        mutableState.value = AppUpdateState()
    }

    private fun fail(failure: UpdateFailure, request: Int) {
        if (generation == request) mutableState.value = state.value.copy(phase = UpdatePhase.ERROR, failure = failure)
    }
}
