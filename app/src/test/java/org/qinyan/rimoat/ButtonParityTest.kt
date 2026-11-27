package org.qinyan.rimoat

import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import androidx.lifecycle.ViewModelStore

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
