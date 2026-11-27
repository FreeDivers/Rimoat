package org.qinyan.rimoat

import android.content.Context
import android.content.ContextWrapper
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.data.db.*
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.ui.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class AddRemoteLifecycleTest {
    private class Catalog : IrCatalog {
        val category = IrCategory(1, "电视", "TV", "tv")
        var categoriesAction: suspend () -> List<IrCategory> = { listOf(category) }
        val brands = mutableMapOf<String, CompletableDeferred<List<IrBrand>>>()
        val models = CompletableDeferred<List<IrRemoteModel>>()
        var remotesErrorGate: CompletableDeferred<Throwable>? = null
        var buildProfileGate: CompletableDeferred<Unit>? = null
        var buildProfileErrorGate: CompletableDeferred<Throwable>? = null
        var testGate: CompletableDeferred<Unit>? = null
        override suspend fun getCategories() = categoriesAction()
        override suspend fun getBrandsForCategory(categoryId: Int, searchQuery: String) =
            brands.getOrPut(searchQuery) { CompletableDeferred() }.await()
        override suspend fun getRemotesForBrand(categoryId: Int, brandIdx: Int): List<IrRemoteModel> {
            remotesErrorGate?.let { gate ->
                val error = withContext(NonCancellable) { gate.await() }
                throw error
            }
            return models.await()
        }
        override suspend fun getTestKeysForRemote(remoteId: String) = null to null
        override suspend fun buildRemoteProfile(model: IrRemoteModel, customName: String?): RemoteProfile {
            buildProfileErrorGate?.let { gate ->
                val error = withContext(NonCancellable) { gate.await() }
                throw error
            }
            buildProfileGate?.let { gate -> withContext(NonCancellable) { gate.await() } }
            return RemoteProfile("model", "电视")
        }
        override suspend fun testSignalsFor(model: IrRemoteModel, button: RemoteButton): List<org.qinyan.rimoat.hardware.IrSignal> {
            testGate?.await()
            return listOf(org.qinyan.rimoat.hardware.IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560)))
        }
    }

    private fun scenario(block: suspend TestScope.(AddRemoteViewModel, Catalog) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dir = Files.createTempDirectory("add-remote-").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
            override fun getFilesDir() = dir
        }
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val catalog = Catalog()
            val repo = RemoteRepository(context, backgroundScope)
            val manager = IrTransmitterManager(context, backgroundScope)
            val vm = AddRemoteViewModel(catalog, repo, manager)
            store.put("add", vm)
            runCurrent()
            block(vm, catalog)
            backgroundScope.coroutineContext.cancelChildren()
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain(); dir.deleteRecursively() }
    }

    @Test fun backgroundCancelsEncodingTestAndKeepsSelectedModel() = scenario { vm, catalog ->
        vm.selectCategory(catalog.category)
        runCurrent()
        catalog.brands.getValue("").complete(listOf(IrBrand(1, "测试", "Test", "test", 1)))
        catalog.models.complete(listOf(IrRemoteModel("R_tv_1", 1, "电视", 1, "测试", "Test", 1, "方案", 1)))
        vm.selectBrand(IrBrand(1, "测试", "Test", "test", 1))
        runCurrent()
        catalog.testGate = CompletableDeferred()
        vm.testSignal(RemoteButton("power", "电源", acAction = AcAction.POWER_ON))
        runCurrent()
        assertTrue(vm.uiState.value.isSendingTest)
        vm.onScreenStopped()
        catalog.testGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSendingTest)
        assertFalse(vm.uiState.value.canConfirmTest)
        assertNull(vm.uiState.value.testMessage)
        assertEquals("R_tv_1", vm.uiState.value.currentRemote?.remoteId)
        assertEquals(0, vm.transmitterManager.mockTransmitter.transmitCount.value)
    }

    @Test fun returningDuringBrandOrModelLoadDoesNotNavigateForwardAgain() = scenario { vm, catalog ->
        vm.selectCategory(catalog.category)
        runCurrent()
        vm.handleBack()
        catalog.brands.getValue("").complete(emptyList())
        runCurrent()
        assertEquals(AddRemoteStep.CATEGORY, vm.uiState.value.step)
        assertNull(vm.uiState.value.error)

        vm.selectCategory(catalog.category)
        runCurrent()
        vm.selectBrand(IrBrand(1, "测试", "Test", "test", 1))
        runCurrent()
        vm.reset()
        catalog.models.complete(listOf(IrRemoteModel("R_tv_1", 1, "电视", 1, "测试", "Test", 1, "方案", 1)))
        runCurrent()
        assertEquals(AddRemoteStep.CATEGORY, vm.uiState.value.step)
        assertTrue(vm.uiState.value.remotes.isEmpty())
        assertNull(vm.uiState.value.error)
    }

    @Test fun olderSearchCannotReplaceTheNewQueryResults() = scenario { vm, catalog ->
        vm.selectCategory(catalog.category)
        runCurrent()
        vm.searchBrand("new")
        runCurrent()
        val newer = IrBrand(2, "新品牌", "New", "new", 1)
        catalog.brands.getValue("new").complete(listOf(newer))
        runCurrent()
        catalog.brands.getValue("").complete(listOf(IrBrand(1, "旧品牌", "Old", "old", 1)))
        runCurrent()
        assertEquals(listOf(newer), vm.uiState.value.brands)
        assertEquals("new", vm.uiState.value.brandSearchQuery)
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
    }

    @Test fun unreadErrorKeepsResourceIdentityOnStopAndOldConsumerCannotClearNewError() = scenario { vm, catalog ->
        vm.selectCategory(catalog.category)
        runCurrent()
        catalog.brands.getValue("").completeExceptionally(java.io.IOException("first external detail"))
        runCurrent()
        val first = checkNotNull(vm.uiState.value.error)
        assertEquals(
            UiText.resource(R.string.msg_brands_load_failed, UiText.Resource(R.string.control_unknown_error)),
            first
        )
        vm.onScreenStopped()
        assertSame(first, vm.uiState.value.error)

        vm.searchBrand("retry")
        runCurrent()
        catalog.brands.getValue("retry").completeExceptionally(SecurityException("second external detail"))
        runCurrent()
        val second = checkNotNull(vm.uiState.value.error)
        assertEquals(
            UiText.resource(R.string.msg_brands_load_failed, UiText.Resource(R.string.error_permission_denied)),
            second
        )
        vm.dismissError(first)
        assertSame(second, vm.uiState.value.error)
        vm.dismissError(second)
        assertNull(vm.uiState.value.error)
    }

    @Test fun saveRemoteKeepsConfirmStepWhileClearingSavedStateHandle() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dir = Files.createTempDirectory("add-remote-save-").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
            override fun getFilesDir() = dir
        }
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val catalog = Catalog()
            val repo = RemoteRepository(context, backgroundScope)
            val manager = IrTransmitterManager(context, backgroundScope)
            val handle = androidx.lifecycle.SavedStateHandle()
            val vm = AddRemoteViewModel(catalog, repo, manager, handle)
            store.put("add", vm)
            runCurrent()

            vm.selectCategory(catalog.category)
            runCurrent()
            catalog.brands.getValue("").complete(listOf(IrBrand(1, "测试", "Test", "test", 1)))
            catalog.models.complete(listOf(IrRemoteModel("R_tv_1", 1, "电视", 1, "测试", "Test", 1, "方案", 1)))
            vm.selectBrand(IrBrand(1, "测试", "Test", "test", 1))
            runCurrent()
            vm.proceedToConfirm()
            runCurrent()
            assertEquals(AddRemoteStep.CONFIRM, vm.uiState.value.step)
            assertNotNull(handle.get<String>("pairing_progress"))

            var savedProfile: RemoteProfile? = null
            vm.saveRemote { savedProfile = it }
            withContext(Dispatchers.Default) {
                withTimeout(10000) {
                    while (savedProfile == null) delay(10)
                }
            }
            runCurrent()

            assertNotNull(savedProfile)
            assertEquals(AddRemoteStep.CONFIRM, vm.uiState.value.step)
            assertFalse(vm.uiState.value.isSaving)
            assertFalse(vm.uiState.value.isLoading)
            assertNull(handle.get<String>("pairing_progress"))

            vm.reset()
            runCurrent()
            assertEquals(AddRemoteStep.CATEGORY, vm.uiState.value.step)
            assertEquals(1, vm.uiState.value.sessionRevision)
            assertNull(handle.get<String>("pairing_progress"))
            backgroundScope.coroutineContext.cancelChildren()
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain(); dir.deleteRecursively() }
    }

    @Test fun testMessageUsesUiTextResourceIdentity() = scenario { vm, catalog ->
        vm.selectCategory(catalog.category)
        runCurrent()
        catalog.brands.getValue("").complete(listOf(IrBrand(1, "测试", "Test", "test", 1)))
        catalog.models.complete(listOf(IrRemoteModel("R_tv_1", 1, "电视", 1, "测试", "Test", 1, "方案", 1)))
        vm.selectBrand(IrBrand(1, "测试", "Test", "test", 1))
        runCurrent()
        vm.transmitterManager.setForceMockMode(true)
        vm.testSignal(RemoteButton("power", "电源", acAction = AcAction.POWER_ON))
        withContext(Dispatchers.Default) {
            withTimeout(10000) {
                while (vm.uiState.value.isSendingTest) delay(10)
            }
        }
        runCurrent()
        assertEquals(UiText.Resource(R.string.msg_simulated_send), vm.uiState.value.testMessage)
    }

    @Test fun resetDuringInFlightSaveCancelsStaleSaveAndKeepsFreshSessionSaveable() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dir = Files.createTempDirectory("add-remote-save-race-").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
            override fun getFilesDir() = dir
        }
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val catalog = Catalog()
            val repo = RemoteRepository(context, backgroundScope)
            val manager = IrTransmitterManager(context, backgroundScope)
            val handle = androidx.lifecycle.SavedStateHandle()
            val vm = AddRemoteViewModel(catalog, repo, manager, handle)
            store.put("add", vm)
            runCurrent()

            vm.selectCategory(catalog.category)
            runCurrent()
            catalog.brands.getValue("").complete(listOf(IrBrand(1, "测试", "Test", "test", 1)))
            catalog.models.complete(listOf(IrRemoteModel("R_tv_1", 1, "电视", 1, "测试", "Test", 1, "方案", 1)))
            vm.selectBrand(IrBrand(1, "测试", "Test", "test", 1))
            runCurrent()
            vm.proceedToConfirm()
            runCurrent()

            val saveGate = CompletableDeferred<Unit>()
            catalog.buildProfileGate = saveGate
            var staleSavedCount = 0
            vm.saveRemote { staleSavedCount++ }
            runCurrent()
            assertTrue(vm.uiState.value.isSaving)

            vm.reset()
            runCurrent()
            saveGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(0, staleSavedCount)
            assertEquals(AddRemoteStep.CATEGORY, vm.uiState.value.step)
            assertNull(vm.uiState.value.error)
            assertFalse(vm.uiState.value.isSaving)
            assertFalse(vm.uiState.value.isLoading)

            // Verify flowCompleted remained false so a fresh session can still checkpoint and save.
            catalog.buildProfileGate = null
            vm.selectCategory(catalog.category)
            runCurrent()
            vm.selectBrand(IrBrand(1, "测试", "Test", "test", 1))
            runCurrent()
            vm.proceedToConfirm()
            runCurrent()
            assertNotNull(handle.get<String>("pairing_progress"))

            var freshSaved: RemoteProfile? = null
            vm.saveRemote { freshSaved = it }
            withContext(Dispatchers.Default) {
                withTimeout(10000) {
                    while (freshSaved == null) delay(10)
                }
            }
            runCurrent()
            assertNotNull(freshSaved)
            assertNull(handle.get<String>("pairing_progress"))
            backgroundScope.coroutineContext.cancelChildren()
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain(); dir.deleteRecursively() }
    }

    @Test fun lateSaveExceptionAfterResetDoesNotLeakError() = scenario { vm, catalog ->
        vm.selectCategory(catalog.category)
        runCurrent()
        catalog.brands.getValue("").complete(listOf(IrBrand(1, "测试", "Test", "test", 1)))
        catalog.models.complete(listOf(IrRemoteModel("R_tv_1", 1, "电视", 1, "测试", "Test", 1, "方案", 1)))
        vm.selectBrand(IrBrand(1, "测试", "Test", "test", 1))
        runCurrent()
        vm.proceedToConfirm()
        runCurrent()

        val errorGate = CompletableDeferred<Throwable>()
        catalog.buildProfileErrorGate = errorGate
        var saved = false
        vm.saveRemote { saved = true }
        runCurrent()

        vm.reset()
        runCurrent()
        errorGate.complete(IllegalStateException("late save failure"))
        advanceUntilIdle()

        assertFalse(saved)
        assertEquals(AddRemoteStep.CATEGORY, vm.uiState.value.step)
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSaving)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun lateNonCancellationLoadErrorsAfterResetDoNotLeakIntoFreshSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dir = Files.createTempDirectory("add-remote-load-err-").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
            override fun getFilesDir() = dir
        }
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val catalog = Catalog()
            val firstCategoriesError = CompletableDeferred<Throwable>()
            var categoryCalls = 0
            catalog.categoriesAction = {
                if (categoryCalls++ == 0) {
                    val error = withContext(NonCancellable) { firstCategoriesError.await() }
                    throw error
                }
                listOf(catalog.category)
            }
            val repo = RemoteRepository(context, backgroundScope)
            val manager = IrTransmitterManager(context, backgroundScope)
            val vm = AddRemoteViewModel(catalog, repo, manager)
            store.put("add", vm)
            runCurrent()

            // Late loadCategories() failure after reset() must not overwrite fresh session.
            vm.reset()
            runCurrent()
            firstCategoriesError.complete(IllegalStateException("late categories failure"))
            runCurrent()
            assertEquals(AddRemoteStep.CATEGORY, vm.uiState.value.step)
            assertNull(vm.uiState.value.error)
            assertEquals(listOf(catalog.category), vm.uiState.value.categories)

            // Late selectBrand() failure after reset() must not leak error into CATEGORY.
            vm.selectCategory(catalog.category)
            runCurrent()
            catalog.brands.getValue("").complete(listOf(IrBrand(1, "测试", "Test", "test", 1)))
            runCurrent()
            val remotesErrorGate = CompletableDeferred<Throwable>()
            catalog.remotesErrorGate = remotesErrorGate
            vm.selectBrand(IrBrand(1, "测试", "Test", "test", 1))
            runCurrent()
            vm.reset()
            runCurrent()
            remotesErrorGate.complete(IllegalStateException("late brand codes failure"))
            advanceUntilIdle()
            assertEquals(AddRemoteStep.CATEGORY, vm.uiState.value.step)
            assertNull(vm.uiState.value.error)
            assertFalse(vm.uiState.value.isLoading)

            // Late updateModelIndex() failure after reset() must not leak error into CATEGORY.
            catalog.remotesErrorGate = null
            catalog.models.complete(
                listOf(
                    IrRemoteModel("R_tv_1", 1, "电视", 1, "测试", "Test", 1, "方案1", 1),
                    IrRemoteModel("R_tv_2", 1, "电视", 1, "测试", "Test", 1, "方案2", 2)
                )
            )
            vm.selectCategory(catalog.category)
            runCurrent()
            vm.selectBrand(IrBrand(1, "测试", "Test", "test", 1))
            runCurrent()
            val modelErrorGate = CompletableDeferred<Throwable>()
            catalog.buildProfileErrorGate = modelErrorGate
            vm.nextModel()
            runCurrent()
            vm.reset()
            runCurrent()
            modelErrorGate.complete(IllegalStateException("late model switch failure"))
            advanceUntilIdle()
            assertEquals(AddRemoteStep.CATEGORY, vm.uiState.value.step)
            assertNull(vm.uiState.value.error)
            assertFalse(vm.uiState.value.isLoading)

            backgroundScope.coroutineContext.cancelChildren()
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain(); dir.deleteRecursively() }
    }

    @Test fun interruptedRestoreBySearchResumesCheckpointingSubsequentProgress() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dir = Files.createTempDirectory("add-remote-restore-search-").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
            override fun getFilesDir() = dir
        }
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val sony = IrBrand(2, "索尼", "Sony", "suoni", 2)
            val models = listOf(
                IrRemoteModel("R_tv_1", 1, "电视", 2, "索尼", "Sony", 1, "方案1", 1),
                IrRemoteModel("R_tv_2", 1, "电视", 2, "索尼", "Sony", 2, "方案2", 2)
            )
            val catalog = Catalog()
            val repo = RemoteRepository(context, backgroundScope)
            val manager = IrTransmitterManager(context, backgroundScope)
            val initialDraft = PairingProgress(categoryId = 1, step = AddRemoteStep.BRAND, query = "")
            val handle = androidx.lifecycle.SavedStateHandle(
                mapOf("pairing_progress" to Json.encodeToString(initialDraft))
            )
            val vm = AddRemoteViewModel(catalog, repo, manager, handle).also { store.put("first", it) }
            runCurrent()
            assertEquals(AddRemoteStep.BRAND, vm.uiState.value.step)
            assertTrue(vm.uiState.value.isLoading)

            // User starts typing while restored brand list is still loading.
            vm.searchBrand("索")
            runCurrent()
            catalog.brands.getValue("索").complete(listOf(sony))
            runCurrent()
            assertFalse(vm.uiState.value.isLoading)
            assertEquals(listOf(sony), vm.uiState.value.brands)

            catalog.models.complete(models)
            vm.selectBrand(sony)
            runCurrent()
            vm.nextModel()
            runCurrent()
            vm.updateCustomName("客厅索尼电视")
            vm.proceedToConfirm()
            runCurrent()

            val persisted = checkNotNull(handle.get<String>("pairing_progress"))
            val restoredCatalog = Catalog().apply {
                brands["索"] = CompletableDeferred(listOf(sony))
                this.models.complete(models)
            }
            val restoredHandle = androidx.lifecycle.SavedStateHandle(mapOf("pairing_progress" to persisted))
            val restored = AddRemoteViewModel(restoredCatalog, repo, manager, restoredHandle)
                .also { store.put("restored", it) }
            runCurrent()

            assertEquals(AddRemoteStep.CONFIRM, restored.uiState.value.step)
            assertEquals(sony, restored.uiState.value.selectedBrand)
            assertEquals("R_tv_2", restored.uiState.value.currentRemote?.remoteId)
            assertEquals("客厅索尼电视", restored.uiState.value.customRemoteName)
            backgroundScope.coroutineContext.cancelChildren()
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain(); dir.deleteRecursively() }
    }

    @Test fun failedBrandRestoreResumesCheckpointingSubsequentProgress() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dir = Files.createTempDirectory("add-remote-restore-fail-").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
            override fun getFilesDir() = dir
        }
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val sony = IrBrand(2, "索尼", "Sony", "suoni", 2)
            val models = listOf(
                IrRemoteModel("R_tv_1", 1, "电视", 2, "索尼", "Sony", 1, "方案1", 1),
                IrRemoteModel("R_tv_2", 1, "电视", 2, "索尼", "Sony", 2, "方案2", 2)
            )
            val catalog = Catalog()
            val repo = RemoteRepository(context, backgroundScope)
            val manager = IrTransmitterManager(context, backgroundScope)
            val initialDraft = PairingProgress(categoryId = 1, step = AddRemoteStep.BRAND)
            val handle = androidx.lifecycle.SavedStateHandle(
                mapOf("pairing_progress" to Json.encodeToString(initialDraft))
            )
            val vm = AddRemoteViewModel(catalog, repo, manager, handle).also { store.put("first", it) }
            runCurrent()
            catalog.brands.getValue("").completeExceptionally(java.io.IOException("db busy"))
            runCurrent()
            assertFalse(vm.uiState.value.isLoading)
            assertNotNull(vm.uiState.value.error)

            vm.searchBrand("索")
            runCurrent()
            catalog.brands.getValue("索").complete(listOf(sony))
            runCurrent()
            catalog.models.complete(models)
            vm.selectBrand(sony)
            runCurrent()
            vm.nextModel()
            runCurrent()
            vm.updateCustomName("卧室索尼电视")
            runCurrent()

            val persisted = checkNotNull(handle.get<String>("pairing_progress"))
            val restoredCatalog = Catalog().apply {
                brands["索"] = CompletableDeferred(listOf(sony))
                this.models.complete(models)
            }
            val restoredHandle = androidx.lifecycle.SavedStateHandle(mapOf("pairing_progress" to persisted))
            val restored = AddRemoteViewModel(restoredCatalog, repo, manager, restoredHandle)
                .also { store.put("restored", it) }
            runCurrent()

            assertEquals(AddRemoteStep.WIZARD, restored.uiState.value.step)
            assertEquals(sony, restored.uiState.value.selectedBrand)
            assertEquals("R_tv_2", restored.uiState.value.currentRemote?.remoteId)
            assertEquals("卧室索尼电视", restored.uiState.value.customRemoteName)
            backgroundScope.coroutineContext.cancelChildren()
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain(); dir.deleteRecursively() }
    }

    @Test fun restoringWizardWithSearchQueryKeepsBrandListConsistentOnBack() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dir = Files.createTempDirectory("add-remote-restore-query-").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
            override fun getFilesDir() = dir
        }
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val xiaomi = IrBrand(1, "小米", "Xiaomi", "xiaomi", 1)
            val sony = IrBrand(2, "索尼", "Sony", "suoni", 1)
            val tcl = IrBrand(3, "TCL", "TCL", "tcl", 1)
            val catalog = Catalog().apply {
                brands[""] = CompletableDeferred(listOf(xiaomi, sony, tcl))
                brands["索"] = CompletableDeferred(listOf(sony))
                models.complete(listOf(IrRemoteModel("R_tv_1", 1, "电视", 2, "索尼", "Sony", 1, "方案1", 1)))
            }
            val repo = RemoteRepository(context, backgroundScope)
            val manager = IrTransmitterManager(context, backgroundScope)
            val draft = PairingProgress(
                categoryId = 1,
                brandId = sony.brandIdx,
                step = AddRemoteStep.WIZARD,
                query = "索",
                brands = mapOf("1:2" to BrandProgress(remoteId = "R_tv_1"))
            )
            val handle = androidx.lifecycle.SavedStateHandle(mapOf("pairing_progress" to Json.encodeToString(draft)))
            val vm = AddRemoteViewModel(catalog, repo, manager, handle).also { store.put("restored", it) }
            runCurrent()

            assertEquals(AddRemoteStep.WIZARD, vm.uiState.value.step)
            assertEquals(sony, vm.uiState.value.selectedBrand)

            assertTrue(vm.handleBack())
            runCurrent()
            assertEquals(AddRemoteStep.BRAND, vm.uiState.value.step)
            assertEquals("索", vm.uiState.value.brandSearchQuery)
            assertEquals(listOf(sony), vm.uiState.value.brands)
            assertEquals(listOf(sony), vm.uiState.value.brandGroupsCn?.flatMap { it.brands })
            assertEquals(listOf(sony), vm.uiState.value.brandGroupsEn?.flatMap { it.brands })
            backgroundScope.coroutineContext.cancelChildren()
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain(); dir.deleteRecursively() }
    }

    @Test fun restoringConfirmFallsBackToFullCatalogWhenSelectedBrandNotInQueryResults() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dir = Files.createTempDirectory("add-remote-restore-fallback-").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
            override fun getFilesDir() = dir
        }
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val sony = IrBrand(2, "索尼", "Sony", "suoni", 1)
            val catalog = Catalog().apply {
                brands["不存在"] = CompletableDeferred(emptyList())
                brands[""] = CompletableDeferred(listOf(sony))
                models.complete(listOf(IrRemoteModel("R_tv_1", 1, "电视", 2, "索尼", "Sony", 1, "方案1", 1)))
            }
            val repo = RemoteRepository(context, backgroundScope)
            val manager = IrTransmitterManager(context, backgroundScope)
            val draft = PairingProgress(
                categoryId = 1,
                brandId = sony.brandIdx,
                step = AddRemoteStep.CONFIRM,
                query = "不存在",
                brands = mapOf("1:2" to BrandProgress(remoteId = "R_tv_1", name = "我的电视"))
            )
            val handle = androidx.lifecycle.SavedStateHandle(mapOf("pairing_progress" to Json.encodeToString(draft)))
            val vm = AddRemoteViewModel(catalog, repo, manager, handle).also { store.put("restored", it) }
            runCurrent()

            assertEquals(AddRemoteStep.CONFIRM, vm.uiState.value.step)
            assertEquals(sony, vm.uiState.value.selectedBrand)
            assertEquals("我的电视", vm.uiState.value.customRemoteName)
            assertTrue(vm.handleBack())
            assertTrue(vm.handleBack())
            runCurrent()
            assertEquals(AddRemoteStep.BRAND, vm.uiState.value.step)
            assertEquals("不存在", vm.uiState.value.brandSearchQuery)
            assertTrue(vm.uiState.value.brands.isEmpty())
            backgroundScope.coroutineContext.cancelChildren()
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain(); dir.deleteRecursively() }
    }
}
