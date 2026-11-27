package org.qinyan.rimoat.ui.learning

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.qinyan.rimoat.clearViewModelsAndAwait
import org.qinyan.rimoat.data.ButtonKey
import org.qinyan.rimoat.data.ApplianceType
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.hardware.IrSignal

@OptIn(ExperimentalCoroutinesApi::class)
class LearningApplianceTypeTest {
    @get:org.junit.Rule val strings = org.qinyan.rimoat.testing.AppStringsResourceRule()
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val signal = IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560))

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun release() {
        clearViewModelsAndAwait(store, dispatcher)
        Dispatchers.resetMain()
    }

    private fun viewModel(state: CustomRemoteWizardUiState? = null): CustomRemoteWizardViewModel {
        val handle = SavedStateHandle(state?.let { mapOf("wizard_state" to Json.encodeToString(it)) }.orEmpty())
        return CustomRemoteWizardViewModel(savedStateHandle = handle).also { store.put("vm${store.keys().size}", it) }
    }

    @Test fun switchingTypesWithinTheSameFilterChangesKeysAndKeepsEnteredMetadata() {
        val vm = viewModel()
        vm.setRemoteName("Living room")
        vm.setBrand("Example")
        vm.setApplianceType(ApplianceType.SET_TOP_BOX)
        assertEquals(DeviceCategory.TV, vm.uiState.value.category)
        assertTrue(vm.uiState.value.keyQueue.any { it.key == ButtonKey.CHANNEL_UP })
        vm.setApplianceType(ApplianceType.STREAMING_BOX)
        assertEquals(DeviceCategory.TV, vm.uiState.value.category)
        assertFalse(vm.uiState.value.keyQueue.any { it.key == ButtonKey.CHANNEL_UP })
        vm.setApplianceType(ApplianceType.CAMERA)
        assertEquals(listOf(ButtonKey.SHUTTER, ButtonKey.SUB_SHUTTER), vm.uiState.value.keyQueue.map { it.key })
        vm.setApplianceType(ApplianceType.LIGHT)
        assertEquals(DeviceCategory.CUSTOM, vm.uiState.value.category)
        assertEquals(listOf(ButtonKey.POWER, ButtonKey.BRIGHTNESS), vm.uiState.value.keyQueue.map { it.key })
        vm.setApplianceType(ApplianceType.DVD)
        assertTrue(vm.uiState.value.keyQueue.any { it.key == ButtonKey.PLAY_PAUSE })
        assertEquals("Living room", vm.uiState.value.remoteName)
        assertEquals("Example", vm.uiState.value.brand)
        assertTrue(vm.proceedToLearning())
        assertEquals(ApplianceType.DVD, vm.uiState.value.selectedApplianceType)
    }

    @Test fun restoredSpecificTypeKeepsLearnedSignalsWhenSelectedAgain() {
        val state = CustomRemoteWizardUiState(
            remoteName = "Camera", category = DeviceCategory.CUSTOM, applianceType = ApplianceType.CAMERA,
            keyQueue = listOf(WizardKeyItem(label = "快门", builtinLabel = "快门",
                status = KeyLearnStatus.LEARNED, capturedSignal = signal))
        )
        val vm = viewModel(state)
        vm.setApplianceType(ApplianceType.CAMERA)
        assertEquals(state.remoteName, vm.uiState.value.remoteName)
        assertEquals(state.keyQueue.single().label, vm.uiState.value.keyQueue.single().label)
        assertEquals(ButtonKey.SHUTTER, vm.uiState.value.keyQueue.single().key)
        assertEquals(ButtonKey.SHUTTER.name, vm.uiState.value.keyQueue.single().builtinLabel)
        assertTrue(vm.proceedToLearning())
        assertEquals(1, vm.uiState.value.learnedCount)
        assertEquals(signal, vm.uiState.value.currentKey?.capturedSignal)
    }

    @Test fun everyTypeSurvivesSavingAndProfileSerialization() = runTest(dispatcher) {
        ApplianceType.entries.forEach { type ->
            val state = CustomRemoteWizardUiState(
                step = WizardStep.REVIEW, remoteName = "Remote", category = type.category, applianceType = type,
                templateApplianceType = type, keySchemaVersion = 1,
                keyQueue = CustomRemoteWizardViewModel.generateDefaultQueue(type).take(1).map {
                    it.copy(status = KeyLearnStatus.LEARNED, capturedSignal = signal)
                }
            )
            val vm = viewModel(state)
            vm.saveRemote()
            val saved = vm.savedRemote.filterNotNull().first()
            val restored = Json.decodeFromString<RemoteProfile>(Json.encodeToString(saved))
            assertEquals(type, restored.applianceType)
            assertEquals(type.category, restored.category)
            assertEquals(type == ApplianceType.AC, restored.useStaticAcButtons)
            assertEquals(signal.patternMicros.toList(), restored.buttons.single().rawPattern)
        }
    }

    @Test fun oldProfilesAndDraftsKeepTheirCategoryWithoutTheNewField() {
        DeviceCategory.entries.forEach { category ->
            val profile = Json.decodeFromString<RemoteProfile>(
                """{"id":"legacy","name":"Remote","category":"${category.name}"}"""
            )
            assertNull(profile.applianceType)
            assertEquals(category, profile.category)
            val state = Json.decodeFromString<CustomRemoteWizardUiState>(
                """{"category":"${category.name}","remoteName":"Remote"}"""
            )
            assertEquals(ApplianceType.fromCategory(category), state.selectedApplianceType)
            val restored = viewModel(state).uiState.value
            assertEquals(state.remoteName, restored.remoteName)
            assertEquals(state.category, restored.category)
            assertEquals(state.keyQueue, restored.keyQueue)
        }
    }

    @Test fun resetClearsTheSpecificTypeWithTheRestOfTheDraft() {
        val vm = viewModel()
        vm.setApplianceType(ApplianceType.IPTV)
        vm.reset(DeviceCategory.FAN)
        assertEquals(ApplianceType.FAN, vm.uiState.value.selectedApplianceType)
        assertTrue(vm.uiState.value.keyQueue.any { it.key == ButtonKey.OSCILLATION })
    }
}
