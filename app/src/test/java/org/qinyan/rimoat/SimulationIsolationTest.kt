package org.qinyan.rimoat

import org.qinyan.rimoat.testing.AppStringsResourceRule
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.*
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationIsolationTest {
    @get:org.junit.Rule val strings = AppStringsResourceRule()

    @Test fun simulatedAcChangesStayTemporaryAndFeedbackBelongsToItsRemote() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val dir = Files.createTempDirectory("rimoat-demo-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = dir
            override fun getSystemService(name: String): Any? = null
        }
        val manager = IrTransmitterManager(context, scope)
        val store = ViewModelStore()
        try {
            manager.mockTransmitter.simulateDelayMs = 0
            val repo = RemoteRepository(context, scope); repo.loadAllRemotes()
            val profile = RemoteProfile("demo-ac", "美的", category = DeviceCategory.AC, acProtocolId = 27)
            repo.saveRemote(profile)
            val original = File(dir, "remotes/demo-ac.json").readText()
            val encoder = object : AcSignalEncoder {
                override suspend fun protocolFor(sourceRemoteId: String) = 27
                override suspend fun supportedModes(protocol: Int) = AcMode.entries.toList()
                override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction) =
                    listOf(IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560)))
            }
            val vm = RemoteControlViewModel(profile.id, repo, manager, initialProfile = profile, acSignalEncoder = encoder)
            val other = RemoteControlViewModel("another", transmitterManager = manager)
            store.put("first", vm); store.put("other", other)
            vm.increaseTemp()
            withTimeout(3000) { vm.acState.first { it.temperature == 27 } }
            assertEquals(26, repo.getRemote(profile.id)!!.acSettings.temperature)
            assertEquals(original, File(dir, "remotes/demo-ac.json").readText())
            assertNotNull(vm.lastTransmitLog.value); assertNull(other.lastTransmitLog.value)
            vm.clearFeedback(); assertNull(vm.lastTransmitLog.value)
            manager.selectTransmitterType(TransmitterType.BUILT_IN)
            withTimeout(3000) { vm.acState.first { it.temperature == 26 } }
            assertEquals(original, File(dir, "remotes/demo-ac.json").readText())
        } finally { store.clear(); manager.release(); scope.cancel(); dir.deleteRecursively(); Dispatchers.resetMain() }
    }

    @Test fun routeChangeFailsBeforeSendingAndReceiptReportsSimulation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
        }
        val manager = IrTransmitterManager(context, scope)
        try {
            manager.mockTransmitter.simulateDelayMs = 0
            val signals = listOf(IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560)))
            assertTrue(manager.transmitWithReceipt(signals, "first", TransmitterType.BUILT_IN).isFailure)
            assertEquals(0, manager.mockTransmitter.transmitCount.value)
            assertTrue(manager.transmitWithReceipt(signals, "first").getOrThrow().simulated)
            assertEquals("first", manager.lastTransmitLog.value!!.ownerId)
        } finally { manager.release(); scope.cancel() }
    }
}
