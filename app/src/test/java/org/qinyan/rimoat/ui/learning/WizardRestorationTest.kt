package org.qinyan.rimoat.ui.learning

import androidx.lifecycle.SavedStateHandle
import org.qinyan.rimoat.R
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.i18n.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class WizardRestorationTest {
    @Test fun editingNameAndTappingSelectedCategoryKeepDraftUntilExplicitRestart() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val signal = IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560))
            val original = CustomRemoteWizardUiState(step = WizardStep.LEARNING, remoteName = "电视", currentIndex = 1,
                keyQueue = listOf(WizardKeyItem(label = "电源", status = KeyLearnStatus.LEARNED, capturedSignal = signal),
                    WizardKeyItem(label = "音量+")))
            val vm = CustomRemoteWizardViewModel(savedStateHandle = SavedStateHandle(mapOf("wizard_state" to
                Json.encodeToString(CustomRemoteWizardUiState.serializer(), original))))
            store.put("wizard", vm)
            assertTrue(vm.handleBack())
            vm.setRemoteName("卧室电视")
            vm.setCategory(original.category) // Tapping the already selected category is not a reset.
            assertEquals(1, vm.uiState.value.learnedCount)
            assertEquals(signal, vm.uiState.value.keyQueue.first().capturedSignal)
            assertEquals(1, vm.uiState.value.currentIndex)
            assertTrue(vm.proceedToLearning())
            assertEquals(1, vm.uiState.value.learnedCount)
            assertEquals(signal, vm.uiState.value.keyQueue.first().capturedSignal)
            assertEquals(1, vm.uiState.value.currentIndex)
            vm.addCustomKey("灯光")
            vm.handleBack()
            vm.proceedToLearning()
            assertEquals(1, vm.uiState.value.learnedCount)
            assertEquals(KeyLearnStatus.PENDING, vm.uiState.value.keyQueue.last().status)
        } finally { org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher); Dispatchers.resetMain() }
    }

    @Test fun completedWizardDeletesDiskAndSavedStateWithoutRecreatingDraft() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val dir = java.nio.file.Files.createTempDirectory("wizard-complete-").toFile()
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val draft = java.io.File(dir, "draft.json")
            val original = CustomRemoteWizardUiState(step = WizardStep.REVIEW, remoteName = "客厅",
                keyQueue = listOf(WizardKeyItem(id = "power", label = "电源", status = KeyLearnStatus.LEARNED,
                    capturedSignal = IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560)))))
            val handle = SavedStateHandle(mapOf("wizard_state" to Json.encodeToString(CustomRemoteWizardUiState.serializer(), original)))
            val vm = CustomRemoteWizardViewModel(savedStateHandle = handle, draftFile = draft).also { store.put("wizard", it) }
            dispatcher.scheduler.runCurrent()
            var saved = false
            vm.saveRemote {
                assertFalse(draft.exists())
                assertNull(handle.get<String>("wizard_state"))
                saved = true
            }
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!saved && System.nanoTime() < deadline) {
                dispatcher.scheduler.advanceUntilIdle()
                Thread.sleep(5)
            }
            assertTrue(saved)
            vm.setRemoteName("ignored")
            dispatcher.scheduler.advanceUntilIdle()
            assertFalse(draft.exists())
            assertNull(handle.get<String>("wizard_state"))
        } finally { org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher); Dispatchers.resetMain(); dir.deleteRecursively() }
    }

    @Test fun largeWaveformsRestoreFromDiskWhileSavedStateStaysSmall() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val dir = java.nio.file.Files.createTempDirectory("wizard-draft-").toFile()
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val draft = java.io.File(dir, "draft.json")
            val signal = IrSignal.Raw(patternMicros = IntArray(1000) { 1500 })
            val original = CustomRemoteWizardUiState(step = WizardStep.REVIEW, remoteName = "大量学习结果",
                keyQueue = List(256) { WizardKeyItem(id = "key$it", label = "按键$it",
                    status = KeyLearnStatus.LEARNED, capturedSignal = signal) })
            draft.writeText(Json.encodeToString(CustomRemoteWizardUiState.serializer(), original))
            assertTrue(draft.length() > 1_000_000)
            val handle = SavedStateHandle(mapOf("wizard_state" to "local-draft"))
            val vm = CustomRemoteWizardViewModel(savedStateHandle = handle, draftFile = draft)
            store.put("draft", vm)
            assertTrue(vm.uiState.value.isRestoring)
            val deadline = System.nanoTime() + 5_000_000_000L
            while (vm.uiState.value.isRestoring && System.nanoTime() < deadline) {
                dispatcher.scheduler.advanceUntilIdle()
                Thread.sleep(10)
            }
            assertFalse(vm.uiState.value.isRestoring)
            assertEquals(256, vm.uiState.value.learnedCount)
            assertEquals(signal, vm.uiState.value.keyQueue.last().capturedSignal)
            assertEquals("local-draft", handle.get<String>("wizard_state"))
        } finally { org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher); Dispatchers.resetMain(); dir.deleteRecursively() }
    }

    @Test fun restorationKeepsLearnedKeysAndClearsInFlightOperations() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val signal = IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560))
            val original = CustomRemoteWizardUiState(step = WizardStep.LEARNING, remoteName = "客厅遥控器",
                isSaving = true, isTestingTransmit = true, keyQueue = listOf(
                    WizardKeyItem(label = "上", status = KeyLearnStatus.LEARNED, capturedSignal = signal),
                    WizardKeyItem(label = "下", status = KeyLearnStatus.LEARNING)))
            val handle = SavedStateHandle(mapOf("wizard_state" to Json.encodeToString(CustomRemoteWizardUiState.serializer(), original)))
            val restored = CustomRemoteWizardViewModel(savedStateHandle = handle).also { store.put("wizard", it) }.uiState.value
            assertEquals("客厅遥控器", restored.remoteName)
            assertEquals(signal, restored.keyQueue.first().capturedSignal)
            assertEquals(KeyLearnStatus.LEARNED, restored.keyQueue.first().status)
            assertEquals(KeyLearnStatus.PENDING, restored.keyQueue[1].status)
            assertFalse(restored.isSaving)
            assertFalse(restored.isTestingTransmit)
        } finally { org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher); Dispatchers.resetMain() }
    }

    @Test fun activeUiTextFieldsAndLegacyUnknownKeysRestoreSafely() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val dir = java.nio.file.Files.createTempDirectory("wizard-legacy-").toFile()
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val signal = IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560))
            val activeState = CustomRemoteWizardUiState(
                step = WizardStep.LEARNING,
                remoteName = "餐厅风扇",
                testFeedbackMessage = UiText.Resource(R.string.screen_simulated_send),
                errorMessage = UiText.Resource(R.string.screen_enter_name),
                keyQueue = listOf(WizardKeyItem(label = "电源", status = KeyLearnStatus.LEARNED, capturedSignal = signal))
            )
            val encoded = Json.encodeToString(CustomRemoteWizardUiState.serializer(), activeState)
            assertFalse(encoded.contains("errorMessage"))
            assertFalse(encoded.contains("testFeedbackMessage"))

            val legacyJson = encoded.removeSuffix("}") +
                ""","errorMessage":"旧错误","testFeedbackMessage":"旧反馈","legacyUnknownField":42}"""
            val handle = SavedStateHandle(mapOf("wizard_state" to legacyJson))
            val fromHandle = CustomRemoteWizardViewModel(savedStateHandle = handle).also { store.put("handle", it) }
            assertEquals("餐厅风扇", fromHandle.uiState.value.remoteName)
            assertEquals(WizardStep.LEARNING, fromHandle.uiState.value.step)
            assertEquals(1, fromHandle.uiState.value.learnedCount)
            assertNull(fromHandle.uiState.value.errorMessage)
            assertNull(fromHandle.uiState.value.testFeedbackMessage)

            val draft = java.io.File(dir, "legacy-draft.json").apply { writeText(legacyJson) }
            val draftHandle = SavedStateHandle(mapOf("wizard_state" to "local-draft"))
            val fromDraft = CustomRemoteWizardViewModel(savedStateHandle = draftHandle, draftFile = draft)
                .also { store.put("draft", it) }
            val deadline = System.nanoTime() + 5_000_000_000L
            while (fromDraft.uiState.value.isRestoring && System.nanoTime() < deadline) {
                dispatcher.scheduler.advanceUntilIdle()
                Thread.sleep(10)
            }
            assertFalse(fromDraft.uiState.value.isRestoring)
            assertEquals("餐厅风扇", fromDraft.uiState.value.remoteName)
            assertEquals(1, fromDraft.uiState.value.learnedCount)
            assertNull(fromDraft.uiState.value.errorMessage)
            assertNull(fromDraft.uiState.value.testFeedbackMessage)
        } finally { org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher); Dispatchers.resetMain(); dir.deleteRecursively() }
    }
}
