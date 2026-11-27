package org.qinyan.rimoat

import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.*
import org.qinyan.rimoat.data.db.*
import org.qinyan.rimoat.ui.viewmodel.AddRemoteViewModel
import org.qinyan.rimoat.i18n.UiText
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import java.nio.file.Files

class ButtonParityTest {
    private fun stock(id: String = "old", key: Int = 834, label: String = "左右扫风") =
        RemoteButton(id, label, rawPattern = listOf(9000,4500,500,500), sourceKeyType = key)

    @Test fun catalogUpgradePreservesIdentityNamesFavoritesAndLearnedSignals() {
        val old = stock()
        val learned = old.copy(label = "自己命名", sourceKeyType = null, rawPattern = listOf(4000,2000,600))
        val profile = RemoteProfile("remote", "卧室", buttons = listOf(learned), isFavorite = true,
            room = "二楼", verifiedFunctions = listOf("自己命名"))
        val new = stock("new").copy(sourceSlot = "0_1", rawPattern = listOf(1000,500,500))
        val result = CatalogUpgrade.merge(profile, "R_1", listOf(old), listOf(new,stock("vertical",835,"上下扫风")))
        assertEquals(profile.id,result.id); assertEquals(profile.name,result.name)
        assertEquals(profile.isFavorite,result.isFavorite); assertEquals(profile.room,result.room)
        assertEquals(learned.rawPattern,result.buttons.first().rawPattern)
        assertEquals(learned.label,result.buttons.first().label)
        assertEquals(2,result.buttons.size)
        assertEquals(result,CatalogUpgrade.merge(result,"R_1",listOf(old),listOf(new)))
    }

    @Test fun catalogUpgradeCorrectsStockCodesAndClearsOnlyTheirVerification() {
        val old = stock()
        val profile = RemoteProfile("remote","测试",buttons=listOf(old.copy(sourceKeyType=null)),
            verifiedFunctions=listOf(old.label,"自定义"))
        val current = old.copy(id="new",sourceSlot="2_1",rawPattern=listOf(300,200,100),alternateRawPattern=listOf(700,300,100))
        val result = CatalogUpgrade.merge(profile,"R_1",listOf(old),listOf(current))
        assertEquals(old.id,result.buttons.single().id)
        assertEquals(current.rawPattern,result.buttons.single().rawPattern)
        assertEquals(current.alternateRawPattern,result.buttons.single().alternateRawPattern)
        assertEquals(listOf("自定义"),result.verifiedFunctions)
    }

    @Test fun sameNamedKeysSurviveMigrationAndHaveUniqueIds() {
        val old = stock(key=2003,label="功能")
        val source = RemoteProfile("r","r",buttons=listOf(old.copy(sourceKeyType=null)))
        val result = CatalogUpgrade.merge(source,"R",listOf(old),listOf(old.copy(id="slot0"),old.copy(id="slot1",rawPattern=listOf(700,800))))
        assertEquals(2,result.buttons.size)
        assertEquals(2,result.buttons.map { it.id }.toSet().size)
    }

    @Test fun learnedCaptureClearsAlternatingFrames() {
        val button = stock().copy(alternateRawPattern=listOf(700,300),alternateFrequencyHz=40000)
        assertEquals(IrSignal.Raw(40000,intArrayOf(700,300)),button.toIrSignal(1))
        assertEquals(button.toIrSignal(0),button.toIrSignal(2))
        val learned = button.withLearnedSignal(IrSignal.Raw(38000,intArrayOf(7000,3000)))
        assertTrue(learned.isLearned); assertTrue(learned.alternateRawPattern.isEmpty())
        assertEquals(learned.toIrSignal(0),learned.toIrSignal(1))
    }

    @Test fun swingAxesAreIndependentAndPreservedInSubsequentTemperatureFrame() {
        val state = AcSettings().apply(AcAction.SWING).apply(AcAction.HORIZONTAL_SWING).apply(AcAction.TEMP_UP)
        assertTrue(state.isSwingOn); assertTrue(state.horizontalSwingOn)
        assertEquals(27,state.temperature)
        assertEquals(1,state.nativeState()[5].toInt()); assertEquals(1,state.nativeState()[6].toInt())
        assertEquals(0,state.nativeState()[4].toInt())
        assertEquals(835,AcAction.SWING.keyType); assertEquals(834,AcAction.HORIZONTAL_SWING.keyType)
    }

    @Test fun extendedStateAndOfficialMutualExclusionAreEncoded() {
        val state=AcSettings().apply(AcAction.TURBO).apply(AcAction.ECO).apply(AcAction.SLEEP).apply(AcAction.QUIET)
        assertFalse(state.turboOn); assertTrue(state.ecoOn); assertTrue(state.sleepOn); assertTrue(state.quietOn)
        assertEquals(1,state.nativeState()[12].toInt()); assertEquals(1,state.nativeState()[18].toInt())
        assertFalse(state.apply(AcAction.TURBO).ecoOn)
    }

    @Test fun timerHasBoundsAndOtherActionsClearItAsOfficialReducerDoes() {
        val timed=AcSettings().apply(AcAction.TIMER,90)
        assertEquals(90,timed.timerMinutes); assertEquals(1,timed.nativeState()[10].toInt())
        assertEquals(0,timed.apply(AcAction.LIGHT).timerMinutes)
        assertTrue(runCatching { timed.apply(AcAction.TIMER,-1) }.isFailure)
        assertTrue(runCatching { timed.apply(AcAction.TIMER,1441) }.isFailure)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun sequentialPressesAlternateAndFailedPressDoesNotAdvance() {
        val dispatcher=StandardTestDispatcher();Dispatchers.setMain(dispatcher)
        val stores = androidx.lifecycle.ViewModelStore()
        try {
            val sent=mutableListOf<IrSignal>(); var fail=true
            val vm=RemoteControlViewModel("test",onTransmitSignal={ if(fail) { fail=false;error("detach") };sent+=it })
            stores.put("controller", vm)
            val button=stock().copy(alternateRawPattern=listOf(700,300))
            repeat(4) { vm.sendButton(button) };dispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf(button.toIrSignal(0),button.toIrSignal(1),button.toIrSignal(0)),sent)
        } finally { org.qinyan.rimoat.clearViewModelsAndAwait(stores, dispatcher); Dispatchers.resetMain() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun sameNamedPairingButtonsKeepIndependentObservationsAndPersistStableFunctions() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val dir = Files.createTempDirectory("pairing-key-test").toFile()
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
            override fun getFilesDir() = dir
        }
        val stores = ViewModelStore()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val keys = listOf(
                RemoteButton("first", "Power", key = ButtonKey.POWER, rawPattern = listOf(9000, 4500, 560)),
                RemoteButton("second", "Power", key = ButtonKey.POWER, rawPattern = listOf(9000, 4500, 600)))
            val category = IrCategory(1, "电视", "TV", "tv")
            val brand = IrBrand(42, "品牌", "Brand", "b", 1)
            val model = IrRemoteModel("R_tv_42_1", 1, "电视", 42, "品牌", "Brand", 1, "Model", 2)
            val catalog = object : IrCatalog {
                override suspend fun getCategories() = listOf(category)
                override suspend fun getBrandsForCategory(categoryId: Int, searchQuery: String) = listOf(brand)
                override suspend fun getRemotesForBrand(categoryId: Int, brandIdx: Int) = listOf(model)
                override suspend fun getTestKeysForRemote(remoteId: String) = keys[0] to keys[1]
                override suspend fun buildRemoteProfile(model: IrRemoteModel, customName: String?) =
                    RemoteProfile("saved", "TV", category = DeviceCategory.TV, buttons = keys,
                        sourceRemoteId = model.remoteId, brand = model.brandCn, brandIdx = model.brandIdx)
            }
            val repo = RemoteRepository(context, scope)
            repo.awaitLoaded()
            val manager = org.qinyan.rimoat.hardware.IrTransmitterManager(context, scope)
            val vm = AddRemoteViewModel(catalog, repo, manager,
                transmitSignals = { _, _ -> Result.success(org.qinyan.rimoat.hardware.TransmissionReceipt(false)) })
            stores.put("pairing", vm)
            runCurrent()
            vm.selectCategory(category); runCurrent()
            vm.selectBrand(brand); runCurrent()
            for (key in keys) {
                vm.testSignal(key); runCurrent()
                assertTrue(vm.uiState.value.canConfirmTest)
                vm.confirmTest(true); runCurrent()
            }
            assertEquals(setOf("first", "second"), vm.uiState.value.verifiedFunctions)
            assertEquals(2, vm.uiState.value.verifiedCount)
            vm.testSignal(keys[1]); runCurrent(); vm.confirmTest(false); runCurrent()
            assertEquals(setOf("first"), vm.uiState.value.verifiedFunctions)
            assertEquals(1, vm.uiState.value.verifiedCount)
            var saved: RemoteProfile? = null
            vm.saveRemote { saved = it }
            withTimeout(5000) { while (saved == null) { runCurrent(); delay(10) } }
            assertEquals(listOf("POWER"), saved!!.verifiedFunctions)
            assertEquals(42, saved!!.brandIdx)
            assertEquals("品牌", saved!!.brand)
            assertEquals(keys.map { it.id }, saved!!.buttons.map { it.id })
            assertEquals(listOf("POWER"), repo.getRemote("saved")!!.verifiedFunctions)
        } finally {
            stores.clear(); runCurrent(); scope.coroutineContext[Job]!!.cancelAndJoin()
            Dispatchers.resetMain(); dir.deleteRecursively()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun temperatureCommandsShareStateBoundariesAndReportLimitsWithoutSending() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val stores = ViewModelStore()
        try {
            val sent = mutableListOf<AcSettings>()
            val encoder = object : AcSignalEncoder {
                override suspend fun protocolFor(sourceRemoteId: String) = 1
                override suspend fun supportedModes(protocol: Int) = AcMode.entries.toList()
                override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction): List<IrSignal.Raw> {
                    sent += settings
                    return listOf(IrSignal.Raw(38000, intArrayOf(9000, 4500, 560)))
                }
            }
            for ((limit, action, resource) in listOf(
                Triple(16, AcAction.TEMP_DOWN, R.string.control_reason_temperature_minimum),
                Triple(30, AcAction.TEMP_UP, R.string.control_reason_temperature_maximum))) {
                val profile = RemoteProfile("ac", "AC", category = DeviceCategory.AC, acProtocolId = 1,
                    acSettings = AcSettings(temperature = limit))
                val vm = RemoteControlViewModel("ac", initialProfile = profile, acSignalEncoder = encoder)
                stores.put("ac_$limit", vm)
                dispatcher.scheduler.advanceUntilIdle()
                assertNotNull(profile.acSettings.temperatureBoundaryReason(action))
                if (action == AcAction.TEMP_DOWN) vm.decreaseTemp() else vm.increaseTemp()
                dispatcher.scheduler.advanceUntilIdle()
                assertEquals(UiText.resource(R.string.msg_send_incomplete, UiText.Resource(resource)), vm.operationError.value)
                assertEquals(limit, vm.acState.value.temperature)
                assertTrue(sent.isEmpty())
                vm.clearOperationError()
                if (action == AcAction.TEMP_DOWN) vm.increaseTemp() else vm.decreaseTemp()
                dispatcher.scheduler.advanceUntilIdle()
                assertEquals(if (limit == 16) 17 else 29, sent.single().temperature)
                assertNull(vm.operationError.value)
                sent.clear()
            }
        } finally { clearViewModelsAndAwait(stores, dispatcher); Dispatchers.resetMain() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun relearningRenamedButtonClearsItsStableVerificationInManualController() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val stores = ViewModelStore()
        try {
            val power = RemoteButton("power", "Custom power", key = ButtonKey.POWER, builtinLabel = "电源")
            val profile = RemoteProfile("remote", "Remote", buttons = listOf(power), keySchemaVersion = 1,
                verifiedFunctions = listOf("POWER", "VOLUME_UP"))
            val vm = RemoteControlViewModel(profile.id, initialProfile = profile)
            stores.put("manual", vm)
            vm.saveLearnedButton(power.copy(label = "New name"), IrSignal.Raw(38000, intArrayOf(9000, 4500, 560)))
            dispatcher.scheduler.advanceUntilIdle()
            val saved = checkNotNull(vm.remoteProfile.value)
            assertEquals(listOf("VOLUME_UP"), saved.verifiedFunctions)
            assertEquals(ButtonKey.POWER, saved.buttons.single().key)
            assertEquals("New name", saved.buttons.single().label)
        } finally { clearViewModelsAndAwait(stores, dispatcher); Dispatchers.resetMain() }
    }
}
