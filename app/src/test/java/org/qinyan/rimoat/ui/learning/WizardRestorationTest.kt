package org.qinyan.rimoat.ui.learning

import androidx.lifecycle.SavedStateHandle
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.data.ApplianceType
import org.qinyan.rimoat.data.ButtonKey
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.LegacyRemoteMigration
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.i18n.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
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

    @Test fun switchingTypeAfterReturningToSetupPreservesDraftOnDisk() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val dir = java.nio.file.Files.createTempDirectory("wizard-switch-type-").toFile()
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val draft = WizardDrafts.file(dir, UUID.randomUUID().toString())
            val signal = IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560, 1690))
            val original = CustomRemoteWizardUiState(
                step = WizardStep.LEARNING,
                remoteName = "客厅电视",
                category = org.qinyan.rimoat.data.DeviceCategory.TV,
                applianceType = org.qinyan.rimoat.data.ApplianceType.TV,
                currentIndex = 1,
                keyQueue = listOf(
                    WizardKeyItem(id = "power", label = "电源", builtinLabel = "电源", status = KeyLearnStatus.LEARNED, capturedSignal = signal),
                    WizardKeyItem(id = "vol_up", label = "音量+", builtinLabel = "音量+"),
                    WizardKeyItem(id = "custom_1", label = "睡眠")
                )
            )
            assertTrue(draft.parentFile!!.mkdirs())
            draft.writeText(Json.encodeToString(CustomRemoteWizardUiState.serializer(), original))

            val vm = CustomRemoteWizardViewModel(draftFile = draft).also { store.put("first", it) }
            await(dispatcher) { !vm.uiState.value.isRestoring }

            val normalizedQueue = vm.uiState.value.keyQueue
            assertTrue(vm.handleBack())
            assertEquals(WizardStep.SETUP, vm.uiState.value.step)

            vm.setApplianceType(org.qinyan.rimoat.data.ApplianceType.FAN)
            assertEquals(org.qinyan.rimoat.data.ApplianceType.FAN, vm.uiState.value.selectedApplianceType)
            assertEquals(normalizedQueue, vm.uiState.value.keyQueue)
            assertEquals(1, vm.uiState.value.currentIndex)

            await(dispatcher) {
                val disk = WizardDrafts.read(draft) ?: return@await false
                Json.decodeFromString<CustomRemoteWizardUiState>(disk).applianceType == org.qinyan.rimoat.data.ApplianceType.FAN
            }
            org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher)

            val restored = CustomRemoteWizardViewModel(draftFile = draft).also { store.put("restored", it) }
            await(dispatcher) { !restored.uiState.value.isRestoring }
            assertEquals(org.qinyan.rimoat.data.ApplianceType.FAN, restored.uiState.value.selectedApplianceType)
            assertEquals(1, restored.uiState.value.learnedCount)
            assertEquals(normalizedQueue, restored.uiState.value.keyQueue)
            assertEquals(1, restored.uiState.value.currentIndex)
            assertTrue(restored.proceedToLearning())
            assertEquals(signal, restored.uiState.value.keyQueue.first().capturedSignal)
        } finally {
            org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher)
            Dispatchers.resetMain()
            dir.deleteRecursively()
        }
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

    @Test fun leavingSetupDeletesDraftEvenWhenItsViewModelIsClearedImmediately() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val dir = java.nio.file.Files.createTempDirectory("wizard-discard-").toFile()
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val draft = WizardDrafts.file(dir, UUID.randomUUID().toString())
            val original = CustomRemoteWizardUiState(
                step = WizardStep.LEARNING, remoteName = "客厅",
                keyQueue = listOf(WizardKeyItem(label = "电源", status = KeyLearnStatus.LEARNED,
                    capturedSignal = IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560))))
            )
            assertTrue(draft.parentFile!!.mkdirs())
            draft.writeText(Json.encodeToString(CustomRemoteWizardUiState.serializer(), original))
            val vm = CustomRemoteWizardViewModel(draftFile = draft).also { store.put("wizard", it) }
            await(dispatcher) { !vm.uiState.value.isRestoring }

            assertTrue(vm.handleBack())
            assertEquals(WizardStep.SETUP, vm.uiState.value.step)
            assertTrue(draft.exists())
            assertFalse(vm.handleBack())
            vm.setRemoteName("新名称")
            dispatcher.scheduler.runCurrent()
            vm.discardDraft()
            vm.setRemoteName("不再写入")
            org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher)
            assertFalse(draft.exists())
        } finally {
            org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher)
            Dispatchers.resetMain()
            dir.deleteRecursively()
        }
    }

    @Test fun pruningDeletesOnlyExpiredSessionDraftsAndTemporaryWrites() {
        val dir = java.nio.file.Files.createTempDirectory("wizard-prune-").toFile()
        try {
            val now = System.currentTimeMillis()
            val cutoff = now - TimeUnit.DAYS.toMillis(7)
            fun draftFile() = WizardDrafts.file(dir, UUID.randomUUID().toString())
            fun write(file: File, modified: Long): File = file.apply {
                writeText("draft")
                assertTrue(setLastModified(modified))
            }
            val expired = draftFile()
            val draftsDir = checkNotNull(expired.parentFile)
            assertTrue(draftsDir.mkdirs())
            write(expired, cutoff - TimeUnit.DAYS.toMillis(1))
            val temporary = write(File(draftsDir, ".${expired.name}.123.tmp"), expired.lastModified())
            val recent = write(draftFile(), cutoff + TimeUnit.DAYS.toMillis(1))
            val unrelated = write(File(draftsDir, "other.json"), expired.lastModified())
            val outside = write(File(dir, expired.name), expired.lastModified())
            val nestedDraft = draftFile().apply { assertTrue(mkdirs()) }
            val nested = write(File(nestedDraft, expired.name), expired.lastModified())

            assertNull(WizardDrafts.read(expired))
            assertEquals("draft", WizardDrafts.read(recent))
            WizardDrafts.pruneExpired(dir, now)

            assertFalse(expired.exists())
            assertFalse(temporary.exists())
            listOf(recent, unrelated, outside, nested).forEach { assertTrue(it.exists()) }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun newSessionWritesOnlyAfterEditingAndRestoresAfterViewModelClears() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val dir = java.nio.file.Files.createTempDirectory("wizard-new-").toFile()
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val draft = WizardDrafts.file(dir, UUID.randomUUID().toString())
            val first = CustomRemoteWizardViewModel(draftFile = draft).also { store.put("first", it) }
            await(dispatcher) { !first.uiState.value.isRestoring }
            assertFalse(draft.exists())

            first.setRemoteName("客厅电视")
            await(dispatcher) { draft.exists() }
            org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher)

            val restored = CustomRemoteWizardViewModel(draftFile = draft).also { store.put("restored", it) }
            await(dispatcher) { !restored.uiState.value.isRestoring }
            assertEquals("客厅电视", restored.uiState.value.remoteName)
        } finally {
            org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher)
            Dispatchers.resetMain()
            dir.deleteRecursively()
        }
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

    @Test fun savedStateAndDiskRestoreBothPreserveCapturedSignalOnInterruptedReRecording() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val dir = java.nio.file.Files.createTempDirectory("wizard-rerecord-").toFile()
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val signal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 1690))
            val original = CustomRemoteWizardUiState(
                step = WizardStep.LEARNING,
                remoteName = "客厅电视",
                currentIndex = 0,
                keyQueue = listOf(
                    WizardKeyItem(id = "power", label = "电源", status = KeyLearnStatus.LEARNING, capturedSignal = signal),
                    WizardKeyItem(id = "vol_up", label = "音量+", status = KeyLearnStatus.LEARNING, capturedSignal = null)
                )
            )
            val encoded = Json.encodeToString(CustomRemoteWizardUiState.serializer(), original)

            val fromHandle = CustomRemoteWizardViewModel(
                savedStateHandle = SavedStateHandle(mapOf("wizard_state" to encoded))
            ).also { store.put("handle", it) }
            assertEquals(KeyLearnStatus.CAPTURED, fromHandle.uiState.value.keyQueue[0].status)
            assertEquals(signal, fromHandle.uiState.value.keyQueue[0].capturedSignal)
            assertEquals(KeyLearnStatus.PENDING, fromHandle.uiState.value.keyQueue[1].status)
            fromHandle.confirmCurrentKey()
            assertEquals(KeyLearnStatus.LEARNED, fromHandle.uiState.value.keyQueue[0].status)

            val draft = File(dir, "rerecord.json").apply { writeText(encoded) }
            val fromDisk = CustomRemoteWizardViewModel(draftFile = draft).also { store.put("disk", it) }
            await(dispatcher) { !fromDisk.uiState.value.isRestoring }
            assertEquals(KeyLearnStatus.CAPTURED, fromDisk.uiState.value.keyQueue[0].status)
            assertEquals(signal, fromDisk.uiState.value.keyQueue[0].capturedSignal)
            assertEquals(KeyLearnStatus.PENDING, fromDisk.uiState.value.keyQueue[1].status)
        } finally {
            org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher)
            Dispatchers.resetMain()
            dir.deleteRecursively()
        }
    }

    @Test fun fallbackDraftWritesToDiskWithoutStoringRawPulsesInSavedStateHandle() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val dir = java.nio.file.Files.createTempDirectory("wizard-fallback-").toFile()
        val store = androidx.lifecycle.ViewModelStore()
        val context = object : android.content.ContextWrapper(null) {
            override fun getApplicationContext() = this
            override fun getFilesDir() = dir
            override fun getSystemService(name: String): Any? = null
        }
        val repoScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
        try {
            val repo = org.qinyan.rimoat.data.RemoteRepository(context, repoScope)
            val handle = SavedStateHandle()
            val signal = IrSignal.Raw(patternMicros = IntArray(600) { 1500 })
            val vm = CustomRemoteWizardViewModel(
                repository = repo,
                savedStateHandle = handle,
                draftFile = null
            ).also { store.put("first", it) }

            vm.setRemoteName("大脉冲草稿")
            repeat(64) { index -> vm.addCustomKey("自定义$index") }
            val firstCustom = vm.uiState.value.keyQueue.last()
            val largeQueue = vm.uiState.value.keyQueue.mapIndexed { idx, item ->
                if (idx == 0 || item.id == firstCustom.id) {
                    item.copy(status = KeyLearnStatus.LEARNED, capturedSignal = signal)
                } else item
            }
            // Trigger state change with large waveforms via handleBack/proceedToLearning + encoded state
            assertTrue(vm.proceedToLearning())
            await(dispatcher) {
                handle.get<String>("wizard_state") == "local-draft" &&
                    !handle.get<String>("wizard_draft_id").isNullOrBlank()
            }
            val sessionId = checkNotNull(handle.get<String>("wizard_draft_id"))
            val fallbackFile = WizardDrafts.file(dir, sessionId)
            assertTrue(fallbackFile.exists())
            assertFalse(checkNotNull(handle.get<String>("wizard_state")).contains("patternMicros"))

            // Overwrite fallback draft with large 256-key payload, restore via SavedStateHandle only (no draftFile arg)
            val hugeState = CustomRemoteWizardUiState(
                step = WizardStep.REVIEW,
                remoteName = "256键遥控器",
                keyQueue = List(256) {
                    WizardKeyItem(id = "k$it", label = "键$it", status = KeyLearnStatus.LEARNED, capturedSignal = signal)
                } + largeQueue.take(0)
            )
            WizardDrafts.write(fallbackFile, Json.encodeToString(CustomRemoteWizardUiState.serializer(), hugeState))

            val restoredHandle = SavedStateHandle(
                mapOf(
                    "wizard_state" to handle.get<String>("wizard_state"),
                    "wizard_draft_id" to sessionId
                )
            )
            val restored = CustomRemoteWizardViewModel(
                repository = repo,
                savedStateHandle = restoredHandle,
                draftFile = null
            ).also { store.put("restored", it) }
            await(dispatcher) { !restored.uiState.value.isRestoring }
            assertEquals(256, restored.uiState.value.learnedCount)
            assertEquals(signal, restored.uiState.value.keyQueue.last().capturedSignal)
            assertEquals("local-draft", restoredHandle.get<String>("wizard_state"))

            restored.discardDraft()
            await(dispatcher) { !fallbackFile.exists() }
            assertNull(restoredHandle.get<String>("wizard_state"))
            assertNull(restoredHandle.get<String>("wizard_draft_id"))
        } finally {
            repoScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher)
            Dispatchers.resetMain()
            dir.deleteRecursively()
        }
    }

    @Test fun transientFeedbackDoesNotTriggerDiskDraftRewrite() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val dir = java.nio.file.Files.createTempDirectory("wizard-debounce-").toFile()
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val draft = WizardDrafts.file(dir, UUID.randomUUID().toString())
            val signal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560))
            val initial = CustomRemoteWizardUiState(
                step = WizardStep.LEARNING,
                remoteName = "卧室",
                keyQueue = listOf(WizardKeyItem(id = "power", label = "电源", status = KeyLearnStatus.CAPTURED, capturedSignal = signal))
            )
            draft.parentFile!!.mkdirs()
            draft.writeText(Json.encodeToString(CustomRemoteWizardUiState.serializer(), initial))

            val vm = CustomRemoteWizardViewModel(draftFile = draft).also { store.put("wizard", it) }
            await(dispatcher) { !vm.uiState.value.isRestoring }

            // Rapid name edits should debounce instead of writing every keystroke, and transient test feedback should not write at all.
            vm.testTransmitCurrentKey()
            dispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(400)
            dispatcher.scheduler.advanceUntilIdle()
            assertNotNull(vm.uiState.value.testFeedbackMessage)
            val savedAfterTest = Json.decodeFromString<CustomRemoteWizardUiState>(draft.readText())
            assertEquals("卧室", savedAfterTest.remoteName)
            assertNull(savedAfterTest.testFeedbackMessage)

            // Deleting the file after restore lets us verify testTransmitCurrentKey alone never triggers a write.
            assertTrue(draft.delete())
            vm.testTransmitCurrentKey()
            dispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(400)
            dispatcher.scheduler.advanceUntilIdle()
            assertFalse(draft.exists())

            vm.setRemoteName("主")
            vm.setRemoteName("主卧")
            vm.setRemoteName("主卧电视")
            await(dispatcher) { draft.exists() }
            assertEquals("主卧电视", Json.decodeFromString<CustomRemoteWizardUiState>(draft.readText()).remoteName)
        } finally {
            org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher)
            Dispatchers.resetMain()
            dir.deleteRecursively()
        }
    }

    @Test fun legacyBuiltinLabelsMigrateWithoutChangingUserNamesOrSignals() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val signal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 1690))
            val legacyPowerLabel = LegacyRemoteMigration.greeAc.buttons.first().label
            val legacyTemperatureLabel = LegacyRemoteMigration.greeAc.buttons[1].label
            val original = CustomRemoteWizardUiState(
                category = DeviceCategory.TV,
                remoteName = "Living room",
                keyQueue = listOf(
                    WizardKeyItem(id = "default", label = legacyPowerLabel, builtinLabel = legacyPowerLabel,
                        status = KeyLearnStatus.LEARNED, capturedSignal = signal),
                    WizardKeyItem(id = "custom_1", label = legacyPowerLabel, capturedSignal = signal),
                    WizardKeyItem(id = "renamed", label = "My temperature button", builtinLabel = legacyTemperatureLabel)
                )
            )
            val vm = CustomRemoteWizardViewModel(savedStateHandle = SavedStateHandle(mapOf(
                "wizard_state" to Json.encodeToString(CustomRemoteWizardUiState.serializer(), original)
            ))).also { store.put("wizard", it) }
            val queue = vm.uiState.value.keyQueue
            assertEquals(ButtonKey.POWER, queue[0].key)
            assertEquals(ButtonKey.POWER.name, queue[0].builtinLabel)
            assertEquals(original.keyQueue[0].label, queue[0].label)
            assertEquals(original.keyQueue[1], queue[1])
            assertEquals("My temperature button", queue[2].label)
            assertEquals(ButtonKey.TEMPERATURE_UP.name, queue[2].builtinLabel)
            assertEquals(signal, queue[0].capturedSignal)
            assertEquals(null, vm.uiState.value.templateApplianceType)
        } finally {
            org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher)
            Dispatchers.resetMain()
        }
    }


    @Test fun legacyAcTemplateRestoresActionsOnlyWhenTheWholeTemplateMatches() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val signal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 1690))
            val original = CustomRemoteWizardUiState(
                category = DeviceCategory.AC,
                remoteName = "Bedroom",
                keyQueue = LegacyRemoteMigration.greeAc.buttons.map { button ->
                    WizardKeyItem(id = button.id, label = button.label, builtinLabel = button.label)
                }
            )
            val learned = original.copy(keyQueue = original.keyQueue.mapIndexed { index, item ->
                if (index == 1) item.copy(status = KeyLearnStatus.LEARNED, capturedSignal = signal) else item
            })
            val vm = CustomRemoteWizardViewModel(savedStateHandle = SavedStateHandle(mapOf(
                "wizard_state" to Json.encodeToString(CustomRemoteWizardUiState.serializer(), learned)
            ))).also { store.put("wizard", it) }
            assertEquals(ApplianceType.AC, vm.uiState.value.templateApplianceType)
            assertEquals(listOf(AcAction.POWER_ON, AcAction.TEMP_UP, AcAction.TEMP_DOWN, AcAction.MODE, AcAction.FAN, AcAction.SWING),
                vm.uiState.value.keyQueue.map { it.acAction })
            assertEquals(original.keyQueue.map { it.label }, vm.uiState.value.keyQueue.map { it.label })
            assertEquals(signal, vm.uiState.value.keyQueue[1].capturedSignal)
            var saved: org.qinyan.rimoat.data.RemoteProfile? = null
            vm.saveRemote { saved = it }
            await(dispatcher) { saved != null }
            assertTrue(saved!!.useStaticAcButtons)
            assertEquals(AcAction.TEMP_UP, saved!!.buttons.single().acAction)
            assertEquals(ButtonKey.TEMPERATURE_UP.name, saved!!.buttons.single().builtinLabel)
        } finally {
            org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher)
            Dispatchers.resetMain()
        }
    }

    @Test fun mixedQueueNeverBecomesAnAcTemplateOnRestore() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val signal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 1690))
            val state = CustomRemoteWizardUiState(
                category = DeviceCategory.AC,
                applianceType = ApplianceType.AC,
                remoteName = "Mixed remote",
                keySchemaVersion = 1,
                templateApplianceType = null,
                keyQueue = CustomRemoteWizardViewModel.generateDefaultQueue(ApplianceType.AC).mapIndexed { index, item ->
                    if (index == 0) item.copy(status = KeyLearnStatus.LEARNED, capturedSignal = signal) else item
                }
            )
            val vm = CustomRemoteWizardViewModel(savedStateHandle = SavedStateHandle(mapOf(
                "wizard_state" to Json.encodeToString(CustomRemoteWizardUiState.serializer(), state)
            ))).also { store.put("wizard", it) }
            assertEquals(null, vm.uiState.value.templateApplianceType)
        } finally {
            org.qinyan.rimoat.clearViewModelsAndAwait(store, dispatcher)
            Dispatchers.resetMain()
        }
    }

    private fun await(dispatcher: TestDispatcher, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            dispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(5)
        }
        assertTrue(condition())
    }
}
