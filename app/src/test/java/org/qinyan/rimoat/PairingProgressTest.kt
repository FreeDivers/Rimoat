package org.qinyan.rimoat

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.data.db.*
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.ui.viewmodel.*
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class PairingProgressTest {
    private class Catalog : IrCatalog {
        val category = IrCategory(1, "电视", "TV", "tv")
        val brands = listOf(IrBrand(1, "A", "A", "a", 3), IrBrand(2, "B", "B", "b", 3))
        var changed = false
        override suspend fun getCategories() = listOf(category)
        override suspend fun getBrandsForCategory(categoryId: Int, searchQuery: String) = brands
        override suspend fun getRemotesForBrand(categoryId: Int, brandIdx: Int) = (1..3).map {
            IrRemoteModel("$brandIdx:$it", 1, "电视", brandIdx, "A", "A", it, "model", 1)
        }
        fun button(id: String) = RemoteButton("$id:power", "电源", rawPattern = listOf(9000,4500,if(changed) 570 else 560))
        override suspend fun getTestKeysForRemote(remoteId: String) = button(remoteId) to null
        override suspend fun buildRemoteProfile(model: IrRemoteModel, customName: String?) =
            RemoteProfile(model.remoteId, customName ?: "test", buttons = listOf(button(model.remoteId)))
    }

    @Test fun brandPositionsResetAndProcessRestorationNeverTransmit() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dir = Files.createTempDirectory("experience_pairing_").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
            override fun getFilesDir() = dir
        }
        val store = ViewModelStore()
        try {
            val repo = RemoteRepository(context, backgroundScope)
            val manager = IrTransmitterManager(context, backgroundScope)
            val catalog = Catalog()
            var handle = SavedStateHandle()
            fun vm(key: String) = AddRemoteViewModel(catalog, repo, manager, handle).also { store.put(key, it) }
            var current = vm("first"); runCurrent()
            current.selectCategory(catalog.category); runCurrent()
            current.selectBrand(catalog.brands[0]); runCurrent()
            current.nextModel(); runCurrent()
            assertEquals("1:2", current.uiState.value.currentRemote?.remoteId)
            current.handleBack(); runCurrent()
            current.selectBrand(catalog.brands[1]); runCurrent()
            assertEquals(0, current.uiState.value.currentRemoteIndex)
            current.nextModel(); current.nextModel(); runCurrent()
            current.handleBack(); runCurrent()
            current.selectBrand(catalog.brands[0]); runCurrent()
            assertEquals("1:2", current.uiState.value.currentRemote?.remoteId)
            current.updateCustomName("experience_ユーザー"); runCurrent()
            val serialized = checkNotNull(handle.get<String>("pairing_progress"))
            handle = SavedStateHandle(mapOf("pairing_progress" to serialized))
            current = vm("restored"); runCurrent()
            assertEquals(AddRemoteStep.WIZARD, current.uiState.value.step)
            assertEquals("1:2", current.uiState.value.currentRemote?.remoteId)
            assertEquals("experience_ユーザー", current.uiState.value.customRemoteName)
            assertFalse(current.uiState.value.canConfirmTest)
            assertNull(current.uiState.value.testedButton)
            assertEquals(0, manager.mockTransmitter.transmitCount.value)
            current.restartMatching(); runCurrent()
            assertEquals(0, current.uiState.value.currentRemoteIndex)
            current.reset(); runCurrent()
            assertEquals(AddRemoteStep.CATEGORY, current.uiState.value.step)
            current.selectCategory(catalog.category); runCurrent()
            current.selectBrand(catalog.brands[0]); runCurrent()
            assertEquals(0, current.uiState.value.currentRemoteIndex)
        } finally { store.clear(); runCurrent(); backgroundScope.coroutineContext.cancelChildren(); Dispatchers.resetMain(); dir.deleteRecursively() }
    }

    @Test fun confirmedRecordRestoresOnlyForUnchangedSignal() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dir = Files.createTempDirectory("experience_records_").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
            override fun getFilesDir() = dir
        }
        val store = ViewModelStore()
        try {
            val catalog = Catalog()
            val button = catalog.button("1:2")
            val draft = PairingProgress(1,1,AddRemoteStep.WIZARD, brands = mapOf("1:1" to
                BrandProgress("1:2", "experience_record", mapOf("1:2" to mapOf(button.id to pairingFingerprint(button))))))
            val manager = IrTransmitterManager(context, backgroundScope)
            val repo = RemoteRepository(context, backgroundScope)
            fun restore(key: String) = AddRemoteViewModel(catalog, repo, manager,
                SavedStateHandle(mapOf("pairing_progress" to Json.encodeToString(draft)))).also { store.put(key,it) }
            val good = restore("good"); runCurrent()
            assertEquals(setOf("电源"), good.uiState.value.verifiedFunctions)
            good.nextModel(); runCurrent(); assertTrue(good.uiState.value.verifiedFunctions.isEmpty())
            good.prevModel(); runCurrent(); assertEquals(setOf("电源"), good.uiState.value.verifiedFunctions)
            catalog.changed = true
            val changed = restore("changed"); runCurrent()
            assertTrue(changed.uiState.value.verifiedFunctions.isEmpty())
            assertEquals(0, manager.mockTransmitter.transmitCount.value)
        } finally { store.clear(); runCurrent(); backgroundScope.coroutineContext.cancelChildren(); Dispatchers.resetMain(); dir.deleteRecursively() }
    }
    @Test fun observationsSurviveActiveSessionProcessRestorationAndMockCannotConfirm() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dir = Files.createTempDirectory("experience_pairing_observations_").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
            override fun getFilesDir() = dir
        }
        val store = ViewModelStore()
        try {
            val catalog = Catalog()
            val button = catalog.button("1:2")
            val draft = PairingProgress(1, 1, AddRemoteStep.WIZARD, brands = mapOf("1:1" to
                BrandProgress(remoteId = "1:2", observations = mapOf("1:2" to mapOf(button.id to
                    PairingObservation(pairingFingerprint(button), PairingResult.INVALID, sent = true))))))
            val manager = IrTransmitterManager(context, backgroundScope)
            val repo = RemoteRepository(context, backgroundScope)
            var handle = SavedStateHandle(mapOf("pairing_progress" to Json.encodeToString(draft)))
            fun restored(name: String) = AddRemoteViewModel(catalog, repo, manager, handle).also { store.put(name, it) }
            val first = restored("first"); runCurrent()
            assertEquals(PairingResult.INVALID, first.uiState.value.observations[button.id]?.result)
            handle = SavedStateHandle(mapOf("pairing_progress" to checkNotNull(handle.get<String>("pairing_progress"))))
            val second = restored("second"); runCurrent()
            assertEquals(AddRemoteStep.WIZARD, second.uiState.value.step)
            assertEquals("1:2", second.uiState.value.currentRemote?.remoteId)
            assertEquals(PairingResult.INVALID, second.uiState.value.observations[button.id]?.result)
            second.restartMatching(); runCurrent()
            assertTrue(second.uiState.value.observations.isEmpty())
            assertEquals(0, manager.mockTransmitter.transmitCount.value)
            manager.setForceMockMode(true)
            second.testSignal(checkNotNull(second.uiState.value.primaryTestKey))
            // The hardware manager deliberately uses real Dispatchers.IO. Virtual test time
            // reaching idle does not mean that transaction has completed.
            withContext(Dispatchers.Default) {
                withTimeout(10000) { second.uiState.first { !it.isSendingTest } }
            }
            runCurrent()
            assertEquals(1, manager.mockTransmitter.transmitCount.value)
            assertFalse(second.uiState.value.canConfirmTest)
            second.confirmTest(true); runCurrent()
            assertTrue(second.uiState.value.verifiedFunctions.isEmpty())
            val simulated = second.uiState.value.observations.values.single()
            assertTrue(simulated.sent && simulated.simulated)
            assertEquals(PairingResult.UNTESTED, simulated.result)
            second.reset(); runCurrent()
            assertEquals(AddRemoteStep.CATEGORY, second.uiState.value.step)
            assertNull(handle.get<String>("pairing_progress"))
        } finally { store.clear(); runCurrent(); backgroundScope.coroutineContext.cancelChildren(); Dispatchers.resetMain(); dir.deleteRecursively() }
    }

    @Test fun distinguishingKeysUseSignalsAndUnambiguousFunctionsOnly() {
        val first = RemoteButton("a", "电源", rawPattern = listOf(9000, 4500, 560), sourceKeyType = 1)
        val same = first.copy(id = "b")
        assertTrue(distinguishingPairingKeys(listOf(first), listOf(same)).isEmpty())
        val different = same.copy(rawPattern = listOf(9000, 4500, 570))
        assertEquals(setOf("a"), distinguishingPairingKeys(listOf(first), listOf(different)))
        assertTrue(distinguishingPairingKeys(listOf(first), listOf(same, different)).isEmpty())
        assertTrue(distinguishingPairingKeys(listOf(first.copy(acAction = AcAction.POWER_ON)), listOf(different)).isEmpty())
        assertTrue(distinguishingPairingKeys(listOf(first), listOf(different.copy(sourceKeyType = 2))).isEmpty())
        assertTrue(distinguishingPairingKeys(listOf(first.copy(sourceKeyType = -100)),
            listOf(different.copy(sourceKeyType = -100, label = "菜单"))).isEmpty())
    }

    @Test fun coldEntryAlwaysStartsAtCategoryEvenIfStaleDiskDraftExists() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dir = Files.createTempDirectory("experience_pairing_cold_").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
            override fun getFilesDir() = dir
        }
        val owner = ViewModelStore()
        try {
            val catalog = Catalog()
            val draft = PairingProgress(1, 1, AddRemoteStep.WIZARD, brands = mapOf("1:1" to BrandProgress("1:3")))
            dir.resolve("pairing-progress.json").writeText(Json.encodeToString(draft))
            val manager = IrTransmitterManager(context, backgroundScope)
            val handle = SavedStateHandle()
            val vm = AddRemoteViewModel(catalog, RemoteRepository(context, backgroundScope), manager, handle)
                .also { owner.put("cold", it) }
            vm.uiState.first { !it.isLoading }
            assertEquals(AddRemoteStep.CATEGORY, vm.uiState.value.step)
            assertNull(vm.uiState.value.selectedCategory)
            assertNull(vm.uiState.value.selectedBrand)
            assertNull(handle.get<String>("pairing_progress"))
            assertFalse(vm.uiState.value.canConfirmTest)
            assertEquals(0, manager.mockTransmitter.transmitCount.value)
        } finally { owner.clear(); runCurrent(); backgroundScope.coroutineContext.cancelChildren(); Dispatchers.resetMain(); dir.deleteRecursively() }
    }

}
