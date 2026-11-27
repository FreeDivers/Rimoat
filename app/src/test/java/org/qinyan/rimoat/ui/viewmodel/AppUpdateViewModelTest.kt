package org.qinyan.rimoat.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.qinyan.rimoat.data.update.*

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = ViewModelStore()
    private val release = AppRelease("v1.10.0", "Notes", ReleaseAsset("app.apk", "", 100))
    private val files = mutableListOf<File>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() {
        stores.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
        files.forEach { it.delete() }
    }

    private class FakeRepository(override val installedVersion: String = "1.9.0") : UpdateRepository {
        var latest: suspend () -> AppRelease = { error("Missing fixture") }
        var fetch: suspend ((Long, Long) -> Unit) -> File = { error("Missing fixture") }
        override suspend fun latestRelease() = latest()
        override suspend fun download(release: AppRelease, progress: (Long, Long) -> Unit) = fetch(progress)
    }
    private fun model(repository: UpdateRepository, handle: SavedStateHandle = SavedStateHandle()) =
        AppUpdateViewModel(repository, handle).also { stores.put("update${stores.keys().size}", it) }

    @Test fun checkTimesOutAtThirtySecondsAndCanRetry() {
        val repository = FakeRepository().apply { latest = { awaitCancellation() } }
        val vm = model(repository)
        vm.check()
        dispatcher.scheduler.advanceTimeBy(29_999)
        assertEquals(UpdatePhase.CHECKING, vm.state.value.phase)
        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()
        assertEquals(UpdateFailure.CHECK_TIMEOUT, vm.state.value.failure)
        assertEquals(UpdateErrorStage.CHECK, vm.state.value.errorStage)
        repository.latest = { release }
        vm.check(); dispatcher.scheduler.runCurrent()
        assertEquals(UpdatePhase.AVAILABLE, vm.state.value.phase)
        assertNull(vm.state.value.errorStage)
    }

    @Test fun equalAndOlderReleasesDoNotOfferDowngrades() {
        for (tag in listOf("v1.9.0", "v1.8.9")) {
            val vm = model(FakeRepository().apply { latest = { release.copy(tag = tag) } })
            vm.check(); dispatcher.scheduler.runCurrent()
            assertEquals(UpdatePhase.CURRENT, vm.state.value.phase)
        }
    }

    @Test fun cancelStopsCheckAndDownloadWithoutShowingLateResults() {
        var cancelled = false
        val repository = FakeRepository().apply { latest = { try { awaitCancellation() } finally { cancelled = true } } }
        val vm = model(repository)
        vm.check(); dispatcher.scheduler.runCurrent(); vm.cancel(); dispatcher.scheduler.runCurrent()
        assertTrue(cancelled)
        assertEquals(UpdatePhase.CLOSED, vm.state.value.phase)
        repository.latest = { release }
        var downloadCancelled = false
        repository.fetch = { progress ->
            progress(40, 100)
            try { awaitCancellation() } finally { downloadCancelled = true }
        }
        vm.check(); dispatcher.scheduler.runCurrent(); vm.download(); dispatcher.scheduler.runCurrent()
        assertEquals(40L, vm.state.value.downloadedBytes)
        vm.cancel(); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(downloadCancelled)
        assertEquals(UpdatePhase.CLOSED, vm.state.value.phase)
    }

    @Test fun downloadFailureCanRetryAndPermissionReturnPreservesCompletedApk() {
        val file = File.createTempFile("update-test", ".apk").also { files += it }
        val repository = FakeRepository().apply { latest = { release }; fetch = { throw UpdateException(UpdateFailure.DOWNLOAD) } }
        val handle = SavedStateHandle()
        val vm = model(repository, handle)
        vm.check(); dispatcher.scheduler.runCurrent(); vm.download(); dispatcher.scheduler.runCurrent()
        assertEquals(UpdateFailure.DOWNLOAD, vm.state.value.failure)
        assertEquals(UpdateErrorStage.DOWNLOAD, vm.state.value.errorStage)
        repository.fetch = { progress -> progress(100, 100); file }
        vm.download(); dispatcher.scheduler.runCurrent()
        assertEquals(UpdatePhase.READY, vm.state.value.phase)
        assertNull(vm.state.value.errorStage)
        vm.requirePermission(); vm.awaitingPermission(); vm.permissionResult(false)
        assertEquals(UpdateFailure.PERMISSION, vm.state.value.failure)
        assertEquals(UpdateErrorStage.INSTALL, vm.state.value.errorStage)
        assertTrue(file.exists())
        vm.awaitingPermission()
        val restored = model(repository, handle)
        assertEquals(UpdatePhase.WAITING_PERMISSION, restored.state.value.phase)
        restored.permissionResult(true)
        assertEquals(UpdatePhase.READY, restored.state.value.phase)
        restored.installing(); restored.installerReturned()
        assertEquals(UpdatePhase.DOWNLOADED, restored.state.value.phase)
        restored.installationFailed()
        assertEquals(UpdatePhase.DOWNLOADED, restored.state.value.phase)
        assertEquals(UpdateFailure.INSTALLER, restored.state.value.failure)
        assertEquals(UpdateErrorStage.INSTALL, restored.state.value.errorStage)
        restored.installAgain()
        assertEquals(UpdatePhase.READY, restored.state.value.phase)
        assertNull(restored.state.value.errorStage)
        restored.cancel()
        assertFalse(file.exists())
    }

    @Test fun missingAssetAndApiErrorsRemainActionable() {
        val repository = FakeRepository().apply { latest = { throw UpdateException(UpdateFailure.RATE_LIMIT) } }
        val vm = model(repository)
        vm.check(); dispatcher.scheduler.runCurrent()
        assertEquals(UpdateFailure.RATE_LIMIT, vm.state.value.failure)
        assertEquals(UpdateErrorStage.CHECK, vm.state.value.errorStage)
        repository.latest = { release.copy(asset = null) }
        vm.check(); dispatcher.scheduler.runCurrent(); vm.download()
        assertEquals(UpdatePhase.AVAILABLE, vm.state.value.phase)
        assertEquals(UpdateFailure.NO_APK, vm.state.value.failure)
    }

    @Test fun minimizeKeepsDownloadAndVerifyStageMapsErrors() {
        val file = File.createTempFile("update-bg", ".apk").also { files += it }
        val gate = CompletableDeferred<Unit>()
        val repository = FakeRepository().apply {
            latest = { release }
            fetch = { progress ->
                progress(25, 100)
                gate.await()
                file
            }
        }
        val vm = model(repository)
        vm.check(); dispatcher.scheduler.runCurrent()
        vm.download(); dispatcher.scheduler.runCurrent()
        vm.minimize()
        assertTrue(vm.state.value.minimized)
        assertEquals(UpdatePhase.DOWNLOADING, vm.state.value.phase)
        gate.complete(Unit); dispatcher.scheduler.runCurrent()
        assertEquals(UpdatePhase.READY, vm.state.value.phase)
        assertTrue(vm.state.value.minimized)
        vm.resume()
        assertFalse(vm.state.value.minimized)
        vm.cancel()
        assertFalse(file.exists())

        val badRepo = FakeRepository().apply {
            latest = { release }
            fetch = { throw UpdateException(UpdateFailure.INVALID_APK) }
        }
        val verifyVm = model(badRepo)
        verifyVm.check(); dispatcher.scheduler.runCurrent()
        verifyVm.download(); dispatcher.scheduler.runCurrent()
        assertEquals(UpdatePhase.ERROR, verifyVm.state.value.phase)
        assertEquals(UpdateErrorStage.VERIFY, verifyVm.state.value.errorStage)
    }
}
