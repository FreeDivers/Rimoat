package org.qinyan.rimoat.ui.learning

import android.content.Context
import android.content.ContextWrapper
import android.os.ThrowingVibrator
import android.os.ThrowingVibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.RemoteRepository
import org.qinyan.rimoat.data.SignalType
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.ui.HapticHelper
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AdversarialPersistenceAndHapticTest {
    @get:org.junit.Rule val strings = org.qinyan.rimoat.testing.AppStringsResourceRule()

    private val stores = androidx.lifecycle.ViewModelStore()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var learnerManager: IrLearnerManager
    private lateinit var tempDir: File
    private lateinit var mockContext: Context
    private val activeScopes = mutableListOf<CoroutineScope>()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        learnerManager = IrLearnerManager(context = null)
        learnerManager.mockLearner.setSimulatedDelayMs(0L)

        tempDir = Files.createTempDirectory("adversarial_persistence_test").toFile()
        mockContext = object : ContextWrapper(null) {
            override fun getFilesDir(): File = tempDir
            override fun getApplicationContext(): Context = this
        }
    }

    @After
    fun tearDown() {
        learnerManager.cancel()
        activeScopes.forEach { it.cancel() }
        activeScopes.clear()
        org.qinyan.rimoat.clearViewModelsAndAwait(stores, testDispatcher)
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    private fun createRepository(): RemoteRepository {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        activeScopes.add(scope)
        return RemoteRepository(mockContext, scope)
    }

    private fun awaitCondition(timeoutMs: Long = 2000L, condition: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            testDispatcher.scheduler.advanceUntilIdle()
            if (condition()) return true
            Thread.sleep(10)
        }
        testDispatcher.scheduler.advanceUntilIdle()
        return condition()
    }

    // =========================================================================
    // 1. CUSTOM REMOTE WIZARD PERSISTENCE & DATA FIDELITY
    // =========================================================================

    /**
     * EMPIRICAL STRESS TEST 1:
     * Create custom remote with mixed preset and custom buttons, save to repository,
     * retrieve from repository and disk, assert 100% data fidelity:
     * - Remote names, brands, categories
     * - Raw pulse arrays (all microsecond items intact)
     * - Carrier frequencies (38kHz, 40kHz, 36kHz, 56kHz)
     * - JSON serialization validity and cold restart recovery
     */
    @Test
    fun testCustomRemoteWizard_mixedPresetAndCustomButtons_dataFidelityAndDiskPersistence() = runBlocking {
        val repository = createRepository()
        repository.loadAllRemotes()

        val viewModel = CustomRemoteWizardViewModel(
            repository = repository,
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.TV
        )

        val targetName = "客厅小米电视 (4K HDR 旗舰版)"
        val targetBrand = "Xiaomi"
        viewModel.setRemoteName(targetName)
        viewModel.setBrand(targetBrand)
        assertTrue(viewModel.proceedToLearning())
        assertEquals(WizardStep.LEARNING, viewModel.uiState.value.step)

        // 1. Learn Preset Key 0: "电源" (38 kHz, 8 pulses)
        val pwrPulses = intArrayOf(9000, 4500, 560, 560, 560, 1690, 560, 1690)
        val pwrSignal = IrSignal.Raw(38000, pwrPulses)
        learnerManager.mockLearner.triggerManualSignal(pwrSignal)
        viewModel.startLearningCurrentKey()
        assertTrue(awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED })
        viewModel.confirmCurrentKey()
        testDispatcher.scheduler.advanceUntilIdle()

        // 2. Learn Preset Key 1: "音量+" (40 kHz Sony-like, 10 pulses)
        val volUpPulses = intArrayOf(2400, 600, 1200, 600, 600, 600, 1200, 600, 600, 600)
        val volUpSignal = IrSignal.Raw(40000, volUpPulses)
        learnerManager.mockLearner.triggerManualSignal(volUpSignal)
        viewModel.startLearningCurrentKey()
        assertTrue(awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED })
        viewModel.confirmCurrentKey()
        testDispatcher.scheduler.advanceUntilIdle()

        // 3. Skip Preset Key 2 ("音量-") & Preset Key 3 ("静音")
        viewModel.skipCurrentKey()
        viewModel.skipCurrentKey()

        // 4. Add Custom Key 1: "背光调节" (36 kHz, 16 pulses)
        viewModel.addCustomKey("背光调节")
        // 5. Add Custom Key 2: "画中画切换" (56 kHz, 20 pulses)
        viewModel.addCustomKey("画中画切换")
        // 6. Add Custom Key 3: "超长微秒宏" (38 kHz, 128 alternating pulses)
        viewModel.addCustomKey("超长微秒宏")

        // Advance through remaining preset keys (Preset TV has 12 keys total, 4 already handled)
        val remainingPresetCount = 12 - 4
        for (i in 0 until remainingPresetCount) {
            viewModel.skipCurrentKey()
        }

        // Now we are on Custom Key 1: "背光调节"
        val customKey1 = viewModel.uiState.value.currentKey
        assertNotNull(customKey1)
        assertEquals("背光调节", customKey1?.label)
        val customPulses1 = intArrayOf(8800, 4400, 500, 500, 500, 1500, 500, 500, 500, 1500, 500, 500, 500, 500, 500, 1500)
        val customSignal1 = IrSignal.Raw(36000, customPulses1)
        learnerManager.mockLearner.triggerManualSignal(customSignal1)
        viewModel.startLearningCurrentKey()
        assertTrue(awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED })
        viewModel.confirmCurrentKey()
        testDispatcher.scheduler.advanceUntilIdle()

        // Custom Key 2: "画中画切换"
        val customKey2 = viewModel.uiState.value.currentKey
        assertNotNull(customKey2)
        assertEquals("画中画切换", customKey2?.label)
        val customPulses2 = IntArray(20) { idx -> if (idx % 2 == 0) 420 else 840 }
        val customSignal2 = IrSignal.Raw(56000, customPulses2)
        learnerManager.mockLearner.triggerManualSignal(customSignal2)
        viewModel.startLearningCurrentKey()
        assertTrue(awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED })
        viewModel.confirmCurrentKey()
        testDispatcher.scheduler.advanceUntilIdle()

        // Custom Key 3: "超长微秒宏"
        val customKey3 = viewModel.uiState.value.currentKey
        assertNotNull(customKey3)
        assertEquals("超长微秒宏", customKey3?.label)
        val customPulses3 = IntArray(128) { idx -> if (idx % 2 == 0) 560 + (idx * 5) else 560 + (idx * 10) }
        val customSignal3 = IrSignal.Raw(38000, customPulses3)
        learnerManager.mockLearner.triggerManualSignal(customSignal3)
        viewModel.startLearningCurrentKey()
        assertTrue(awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED })
        viewModel.confirmCurrentKey()
        testDispatcher.scheduler.advanceUntilIdle()

        // All keys processed -> Step must now be REVIEW
        assertEquals(WizardStep.REVIEW, viewModel.uiState.value.step)
        assertEquals(5, viewModel.uiState.value.learnedCount)

        // Save Remote to Repository
        var savedProfileResult: RemoteProfile? = null
        viewModel.saveRemote { profile ->
            savedProfileResult = profile
        }
        val saveFinished = awaitCondition(3000L) { savedProfileResult != null }
        assertTrue("saveRemote callback must complete", saveFinished)

        val saved = savedProfileResult!!

        // --- Step A: Assert in-memory data fidelity ---
        assertEquals(targetName, saved.name)
        assertEquals(targetBrand, saved.brand)
        assertEquals(DeviceCategory.TV, saved.category)
        assertEquals(5, saved.buttons.size)

        // Verify button 0 ("电源")
        val btn0 = saved.buttons[0]
        assertEquals(org.qinyan.rimoat.data.ButtonKey.POWER.name, btn0.label)
        assertEquals(org.qinyan.rimoat.data.ButtonKey.POWER, btn0.key)
        assertEquals(SignalType.RAW, btn0.signalType)
        assertEquals(38000, btn0.frequencyHz)
        assertEquals(pwrPulses.toList(), btn0.rawPattern)
        assertEquals(0, btn0.row)
        assertEquals(0, btn0.col)

        // Verify button 1 ("音量+")
        val btn1 = saved.buttons[1]
        assertEquals(org.qinyan.rimoat.data.ButtonKey.VOLUME_UP.name, btn1.label)
        assertEquals(org.qinyan.rimoat.data.ButtonKey.VOLUME_UP, btn1.key)
        assertEquals(SignalType.RAW, btn1.signalType)
        assertEquals(40000, btn1.frequencyHz)
        assertEquals(volUpPulses.toList(), btn1.rawPattern)
        assertEquals(0, btn1.row)
        assertEquals(1, btn1.col)

        // Verify button 2 ("背光调节")
        val btn2 = saved.buttons[2]
        assertEquals("背光调节", btn2.label)
        assertEquals(SignalType.RAW, btn2.signalType)
        assertEquals(36000, btn2.frequencyHz)
        assertEquals(customPulses1.toList(), btn2.rawPattern)
        assertEquals(0, btn2.row)
        assertEquals(2, btn2.col)

        // Verify button 3 ("画中画切换")
        val btn3 = saved.buttons[3]
        assertEquals("画中画切换", btn3.label)
        assertEquals(SignalType.RAW, btn3.signalType)
        assertEquals(56000, btn3.frequencyHz)
        assertEquals(customPulses2.toList(), btn3.rawPattern)
        assertEquals(1, btn3.row)
        assertEquals(0, btn3.col)

        // Verify button 4 ("超长微秒宏")
        val btn4 = saved.buttons[4]
        assertEquals("超长微秒宏", btn4.label)
        assertEquals(SignalType.RAW, btn4.signalType)
        assertEquals(38000, btn4.frequencyHz)
        assertEquals(customPulses3.toList(), btn4.rawPattern)
        assertEquals(1, btn4.row)
        assertEquals(1, btn4.col)

        // --- Step B: Direct Disk JSON File Verification ---
        val diskFile = File(tempDir, "remotes/${saved.id}.json")
        assertTrue("JSON file must exist on disk: ${diskFile.absolutePath}", diskFile.exists())
        val diskJson = diskFile.readText()
        assertTrue("Disk JSON must not be blank", diskJson.isNotBlank())

        val decodedFromDisk = json.decodeFromString(RemoteProfile.serializer(), diskJson)
        assertEquals(saved.id, decodedFromDisk.id)
        assertEquals(targetName, decodedFromDisk.name)
        assertEquals(targetBrand, decodedFromDisk.brand)
        assertEquals(DeviceCategory.TV, decodedFromDisk.category)
        assertEquals(5, decodedFromDisk.buttons.size)

        for (i in 0 until 5) {
            val originalBtn = saved.buttons[i]
            val diskBtn = decodedFromDisk.buttons[i]
            assertEquals(originalBtn.id, diskBtn.id)
            assertEquals(originalBtn.label, diskBtn.label)
            assertEquals(originalBtn.signalType, diskBtn.signalType)
            assertEquals(originalBtn.frequencyHz, diskBtn.frequencyHz)
            assertEquals(originalBtn.rawPattern, diskBtn.rawPattern)
            assertEquals(originalBtn.row, diskBtn.row)
            assertEquals(originalBtn.col, diskBtn.col)
            assertEquals(originalBtn.spanCol, diskBtn.spanCol)
        }

        // --- Step C: Cold Start Repository Reload ---
        val coldRepo = createRepository()
        coldRepo.loadAllRemotes()
        val reloadedProfile = coldRepo.getRemote(saved.id)

        assertNotNull("Cold loaded profile must exist in repository", reloadedProfile)
        assertEquals(saved.id, reloadedProfile?.id)
        assertEquals(targetName, reloadedProfile?.name)
        assertEquals(5, reloadedProfile?.buttons?.size)
    }

    /**
     * EMPIRICAL STRESS TEST 1B:
     * CustomRemoteWizard for AC (Air Conditioner) category:
     * Mixed AC preset keys ("开关", "温度+") and custom learned keys ("强劲模式", "健康自洁"),
     * verifying category preservation, carrier frequency fidelity, and grid layout.
     */
    @Test
    fun testCustomRemoteWizard_acCategory_customButtonsAndStateFidelity() = runBlocking {
        val repository = createRepository()
        repository.loadAllRemotes()

        val viewModel = CustomRemoteWizardViewModel(
            repository = repository,
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.AC
        )

        val targetName = "格力变频空调 (卧室壁挂机)"
        val targetBrand = "Gree"
        viewModel.setRemoteName(targetName)
        viewModel.setBrand(targetBrand)
        assertTrue(viewModel.proceedToLearning())
        assertEquals(WizardStep.LEARNING, viewModel.uiState.value.step)

        // 1. Learn "开关" (38kHz, 279 pulses typical AC signal)
        val pwrPulses = IntArray(279) { idx -> if (idx % 2 == 0) 600 else 1600 }
        learnerManager.mockLearner.triggerManualSignal(IrSignal.Raw(38000, pwrPulses))
        viewModel.startLearningCurrentKey()
        assertTrue(awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED })
        viewModel.confirmCurrentKey()
        testDispatcher.scheduler.advanceUntilIdle()

        // 2. Learn "温度+" (38kHz, 279 pulses)
        val tempUpPulses = IntArray(279) { idx -> if (idx % 2 == 0) 620 else 1580 }
        learnerManager.mockLearner.triggerManualSignal(IrSignal.Raw(38000, tempUpPulses))
        viewModel.startLearningCurrentKey()
        assertTrue(awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED })
        viewModel.confirmCurrentKey()
        testDispatcher.scheduler.advanceUntilIdle()

        // 3. Add Custom AC key: "强劲模式"
        viewModel.addCustomKey("强劲模式")
        // 4. Add Custom AC key: "健康自洁"
        viewModel.addCustomKey("健康自洁")

        // Skip remaining preset keys in AC queue ("温度-", "模式", "风速", "扫风")
        repeat(4) { viewModel.skipCurrentKey() }

        // Learn "强劲模式" (40kHz, 83 pulses)
        val turboPulses = IntArray(83) { idx -> if (idx % 2 == 0) 540 else 540 }
        learnerManager.mockLearner.triggerManualSignal(IrSignal.Raw(40000, turboPulses))
        viewModel.startLearningCurrentKey()
        assertTrue(awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED })
        viewModel.confirmCurrentKey()
        testDispatcher.scheduler.advanceUntilIdle()

        // Learn "健康自洁" (38kHz, 101 pulses)
        val cleanPulses = IntArray(101) { idx -> if (idx % 2 == 0) 560 else 1690 }
        learnerManager.mockLearner.triggerManualSignal(IrSignal.Raw(38000, cleanPulses))
        viewModel.startLearningCurrentKey()
        assertTrue(awaitCondition { viewModel.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED })
        viewModel.confirmCurrentKey()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(WizardStep.REVIEW, viewModel.uiState.value.step)
        assertEquals(4, viewModel.uiState.value.learnedCount)

        var savedProfile: RemoteProfile? = null
        viewModel.saveRemote { savedProfile = it }
        val saved = awaitCondition(3000L) { savedProfile != null }
        assertTrue("AC remote must be saved", saved)

        val profile = savedProfile!!
        assertEquals(targetName, profile.name)
        assertEquals(targetBrand, profile.brand)
        assertEquals(DeviceCategory.AC, profile.category)
        assertEquals(4, profile.buttons.size)

        // Check on disk
        val diskFile = File(tempDir, "remotes/${profile.id}.json")
        assertTrue(diskFile.exists())
        val decoded = json.decodeFromString(RemoteProfile.serializer(), diskFile.readText())
        assertEquals(DeviceCategory.AC, decoded.category)
        assertEquals(4, decoded.buttons.size)
        assertEquals(279, decoded.buttons[0].rawPattern.size)
        assertEquals(279, decoded.buttons[1].rawPattern.size)
        assertEquals(83, decoded.buttons[2].rawPattern.size)
        assertEquals(101, decoded.buttons[3].rawPattern.size)
        assertEquals(40000, decoded.buttons[2].frequencyHz)
    }


    // =========================================================================
    // 2. ATOMIC SINGLE-BUTTON SAVE FLOW & REPEATED UPDATES STRESS
    // =========================================================================

    /**
     * EMPIRICAL STRESS TEST 2:
     * Repeatedly update buttons via single-button save flow, verify JSON disk
     * persistence is atomic, updates take effect immediately, and no buttons
     * are corrupted or dropped.
     */
    @Test
    fun testSingleButtonSaveFlow_repeatedSequentialUpdates_atomicityAndNoDroppedButtons() = runBlocking {
        val repository = createRepository()
        repository.loadAllRemotes()

        val remoteId = "tv_stress_${UUID.randomUUID()}"
        val initialButtons = listOf(
            RemoteButton(id = "btn_power", label = "电源", iconName = "power", signalType = SignalType.NEC, necAddress = 0x00FF, necCommand = 0x12),
            RemoteButton(id = "btn_vol_up", label = "音量+", iconName = "vol_up", signalType = SignalType.RAW, frequencyHz = 38000, rawPattern = listOf(9000, 4500, 560, 560)),
            RemoteButton(id = "btn_vol_down", label = "音量-", iconName = "vol_down", signalType = SignalType.RAW, frequencyHz = 38000, rawPattern = listOf(9000, 4500, 560, 1690)),
            RemoteButton(id = "btn_mute", label = "静音", iconName = "mute", signalType = SignalType.NEC, necAddress = 0x00FF, necCommand = 0x15)
        )
        val initialProfile = RemoteProfile(
            id = remoteId,
            name = "客厅电视 (原子更新应力测试)",
            category = DeviceCategory.TV,
            buttons = initialButtons
        )
        repository.saveRemote(initialProfile)

        val diskFile = File(tempDir, "remotes/$remoteId.json")
        assertTrue(diskFile.exists())

        // Instantiate RemoteControlViewModel wired to repository
        val viewModel = RemoteControlViewModel(
            remoteId = remoteId,
            repository = repository,
            learnerManager = learnerManager,
            initialProfile = initialProfile
        ).also { stores.put("controller", it) }

        // Perform 30 sequential single-button learning updates across various buttons
        val iterations = 30
        for (i in 1..iterations) {
            val targetButtonId = when (i % 4) {
                0 -> "btn_power"
                1 -> "btn_vol_up"
                2 -> "btn_vol_down"
                else -> "btn_mute"
            }

            val targetButton = viewModel.remoteProfile.value?.buttons?.find { it.id == targetButtonId }
                ?: initialButtons.first { it.id == targetButtonId }

            // Dynamic unique pulse pattern
            val pulseLength = 6 + (i * 2) % 40
            val newPulses = IntArray(pulseLength) { idx -> 500 + i * 10 + idx * 2 }
            val newFreq = 38000 + (i * 250)
            val learnedSignal = IrSignal.Raw(frequencyHz = newFreq, patternMicros = newPulses)

            viewModel.startLearningButton(targetButton)
            assertEquals(targetButton, viewModel.learningDialogButton.value)

            viewModel.saveLearnedButton(targetButton, learnedSignal)

            // Wait for asynchronous persistence and dialog dismissal
            val dismissed = awaitCondition(2000L) { viewModel.learningDialogButton.value == null }
            assertTrue("Learning dialog must be dismissed after save", dismissed)

            // Verify Disk File Atomicity: Read file directly, parse JSON, ensure zero corruption
            assertTrue("Disk file must remain present", diskFile.exists())
            val diskContent = diskFile.readText()
            val parsedProfile = json.decodeFromString(RemoteProfile.serializer(), diskContent)

            // Ensure no buttons are dropped or duplicated
            assertEquals("Button count must stay intact at 4", 4, parsedProfile.buttons.size)
            val uniqueIds = parsedProfile.buttons.map { it.id }.toSet()
            assertEquals("No duplicated button IDs allowed", 4, uniqueIds.size)

            // Verify target button updated
            val updatedDiskBtn = parsedProfile.buttons.find { it.id == targetButtonId }
            assertNotNull("Updated button must exist on disk", updatedDiskBtn)
            assertEquals(SignalType.RAW, updatedDiskBtn?.signalType)
            assertEquals(newFreq, updatedDiskBtn?.frequencyHz)
            assertEquals(newPulses.toList(), updatedDiskBtn?.rawPattern)
        }

        // Test adding a brand-new button via updateButton to existing remote
        val brandNewButton = RemoteButton(
            id = "btn_custom_extra",
            label = "自定义按键",
            signalType = SignalType.RAW,
            frequencyHz = 36000,
            rawPattern = listOf(1000, 2000, 3000, 4000)
        )
        repository.updateButton(remoteId, brandNewButton)

        val updated = awaitCondition(2000L) {
            val content = if (diskFile.exists()) diskFile.readText() else ""
            content.contains("btn_custom_extra")
        }
        assertTrue("Added button must be persisted", updated)

        val finalDiskContent = diskFile.readText()
        val finalProfile = json.decodeFromString(RemoteProfile.serializer(), finalDiskContent)
        assertEquals(5, finalProfile.buttons.size)
        assertTrue(finalProfile.buttons.any { it.id == "btn_custom_extra" })
    }

    /**
     * EMPIRICAL STRESS TEST 3:
     * Concurrency & Mutex Protection:
     * Run 25 concurrent updates on the same remote to verify disk writing is atomic,
     * thread-safe, and never results in truncated/malformed JSON or dropped buttons.
     */
    @Test
    fun testSingleButtonSaveFlow_concurrentRapidUpdates_mutexProtectionAndDataIntegrity() = runBlocking {
        val repository = createRepository()
        repository.loadAllRemotes()

        val remoteId = "concurrent_stress_${UUID.randomUUID()}"
        val initialButtons = (0 until 10).map { i ->
            RemoteButton(
                id = "btn_$i",
                label = "Key $i",
                signalType = SignalType.RAW,
                rawPattern = listOf(1000 + i, 1000 + i)
            )
        }
        val initialProfile = RemoteProfile(
            id = remoteId,
            name = "并发写入压力测试遥控器",
            buttons = initialButtons
        )
        repository.saveRemote(initialProfile)

        // Launch 25 concurrent coroutines updating buttons simultaneously
        val jobs = (0 until 25).map { workerId ->
            launch(Dispatchers.IO) {
                val targetBtnIdx = workerId % 10
                val updatedBtn = RemoteButton(
                    id = "btn_$targetBtnIdx",
                    label = "Key $targetBtnIdx (Updated by Worker $workerId)",
                    signalType = SignalType.RAW,
                    frequencyHz = 38000 + workerId * 100,
                    rawPattern = listOf(workerId * 100 + 10, workerId * 100 + 20, 560, 560)
                )
                repository.updateButton(remoteId, updatedBtn)
            }
        }
        jobs.joinAll()

        // Verify JSON file on disk
        val diskFile = File(tempDir, "remotes/$remoteId.json")
        assertTrue("Disk file must exist", diskFile.exists())
        val diskText = diskFile.readText()
        assertTrue("Disk text must not be empty", diskText.isNotBlank())

        // Must parse cleanly without JsonDecodingException or truncated text
        val profileOnDisk = json.decodeFromString(RemoteProfile.serializer(), diskText)
        assertEquals(10, profileOnDisk.buttons.size)
        val buttonIds = profileOnDisk.buttons.map { it.id }
        assertEquals(10, buttonIds.distinct().size)
    }

    /**
     * EMPIRICAL STRESS TEST 4:
     * Boundary conditions & Special Characters:
     * Extreme strings, Unicode, quotes, line breaks, empty buttons, and 500-pulse payloads.
     */
    @Test
    fun testCustomRemoteWizard_boundaryConditionsAndExtremePayloads() = runBlocking {
        val repository = createRepository()
        repository.loadAllRemotes()

        val complexName = "极米 4K \"'<>&\\/\n\t\u0000 投影仪 🚀✨"
        val complexBrand = "Brand\uD83D\uDD25'\""
        val profile = RemoteProfile(
            id = "boundary_test_${UUID.randomUUID()}",
            name = complexName,
            brand = complexBrand,
            category = DeviceCategory.PROJECTOR,
            buttons = listOf(
                RemoteButton(
                    id = "btn_huge",
                    label = "500脉冲宏",
                    signalType = SignalType.RAW,
                    frequencyHz = 38000,
                    rawPattern = (1..500).toList()
                ),
                RemoteButton(
                    id = "btn_special_label",
                    label = "按键\"'<>&\\/\n\t",
                    signalType = SignalType.RAW,
                    frequencyHz = 40000,
                    rawPattern = listOf(9000, 4500)
                )
            )
        )

        repository.saveRemote(profile)

        val retrieved = repository.getRemote(profile.id)
        assertNotNull(retrieved)
        assertEquals(complexName, retrieved?.name)
        assertEquals(complexBrand, retrieved?.brand)
        assertEquals(500, retrieved?.buttons?.get(0)?.rawPattern?.size)
        assertEquals(listOf(9000, 4500), retrieved?.buttons?.get(1)?.rawPattern)

        // Read directly from disk
        val diskFile = File(tempDir, "remotes/${profile.id}.json")
        val diskContent = diskFile.readText()
        val decoded = json.decodeFromString(RemoteProfile.serializer(), diskContent)
        assertEquals(complexName, decoded.name)
        assertEquals(500, decoded.buttons[0].rawPattern.size)
    }

    // =========================================================================
    // 3. HAPTIC HELPER CRASH IMMUNITY STRESS TESTS
    // =========================================================================

    /**
     * EMPIRICAL STRESS TEST 5:
     * HapticHelper with null Context and null HapticFeedback:
     * Must execute all methods with 100% crash immunity.
     */
    @Test
    fun testHapticHelper_nullContextAndNullFeedback_crashImmunity() {
        // Must not throw any NullPointerException
        HapticHelper.performStartListening(hapticFeedback = null, context = null)
        HapticHelper.performCaptured(hapticFeedback = null, context = null)
        HapticHelper.performSaveSuccess(hapticFeedback = null, context = null)
        HapticHelper.performTestEmit(hapticFeedback = null, context = null)
        HapticHelper.performRemoteClick(hapticFeedback = null, context = null)
    }

    /**
     * EMPIRICAL STRESS TEST 6:
     * HapticHelper with Context returning null Vibrator / null VibratorManager:
     * Must execute all methods without throwing.
     */
    @Test
    fun testHapticHelper_mockContextWithNullVibrator_crashImmunity() {
        val nullVibratorContext = object : ContextWrapper(null) {
            override fun getSystemService(name: String): Any? = null
            override fun getApplicationContext(): Context = this
        }

        val noOpHapticFeedback = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
        }

        HapticHelper.performStartListening(noOpHapticFeedback, nullVibratorContext)
        HapticHelper.performCaptured(noOpHapticFeedback, nullVibratorContext)
        HapticHelper.performSaveSuccess(noOpHapticFeedback, nullVibratorContext)
        HapticHelper.performTestEmit(noOpHapticFeedback, nullVibratorContext)
        HapticHelper.performRemoteClick(noOpHapticFeedback, nullVibratorContext)
    }

    /**
     * EMPIRICAL STRESS TEST 7:
     * HapticHelper with throwing HapticFeedback:
     * Must catch and swallow any RuntimeException / UnsupportedOperationException from Compose haptics.
     */
    @Test
    fun testHapticHelper_throwingHapticFeedback_crashImmunity() {
        val throwingHapticFeedback = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                throw UnsupportedOperationException("Compose haptic feedback unsupported on this device!")
            }
        }

        HapticHelper.performStartListening(throwingHapticFeedback, null)
        HapticHelper.performCaptured(throwingHapticFeedback, null)
        HapticHelper.performSaveSuccess(throwingHapticFeedback, null)
        HapticHelper.performTestEmit(throwingHapticFeedback, null)
        HapticHelper.performRemoteClick(throwingHapticFeedback, null)
    }

    /**
     * EMPIRICAL STRESS TEST 8:
     * HapticHelper with Context throwing on getSystemService:
     * Simulates SecurityException / IllegalStateException when querying system services.
     */
    @Test
    fun testHapticHelper_throwingContextSystemService_crashImmunity() {
        @Suppress("DEPRECATION")
        val throwingContext = object : ContextWrapper(null) {
            override fun getSystemService(name: String): Any {
                throw SecurityException("SecurityException: System service VIBRATOR_SERVICE permission denied")
            }
            override fun getApplicationContext(): Context = this
        }

        HapticHelper.performStartListening(null, throwingContext)
        HapticHelper.performCaptured(null, throwingContext)
        HapticHelper.performSaveSuccess(null, throwingContext)
        HapticHelper.performTestEmit(null, throwingContext)
        HapticHelper.performRemoteClick(null, throwingContext)
    }

    /**
     * EMPIRICAL STRESS TEST 9:
     * HapticHelper with Exception-Throwing Vibrator:
     * Tests two failure modes:
     * Mode A: Vibrator.hasVibrator() throws SecurityException
     * Mode B: Vibrator.vibrate(...) throws SecurityException / IllegalArgumentException
     */
    @Test
    fun testHapticHelper_throwingVibrator_crashImmunity() {
        // Mode A: hasVibrator() throws SecurityException
        val throwingHasVibrator = ThrowingVibrator(throwOnHasVibrator = true, throwOnVibrate = false)
        val contextModeA = object : ContextWrapper(null) {
            @Suppress("DEPRECATION")
            override fun getSystemService(name: String): Any? {
                return if (name == Context.VIBRATOR_SERVICE) throwingHasVibrator else null
            }
            override fun getApplicationContext(): Context = this
        }

        HapticHelper.performStartListening(null, contextModeA)
        HapticHelper.performCaptured(null, contextModeA)
        HapticHelper.performSaveSuccess(null, contextModeA)
        HapticHelper.performTestEmit(null, contextModeA)
        HapticHelper.performRemoteClick(null, contextModeA)

        // Mode B: hasVibrator() returns true, but vibrate() throws SecurityException
        val throwingVibrate = ThrowingVibrator(throwOnHasVibrator = false, throwOnVibrate = true)
        val contextModeB = object : ContextWrapper(null) {
            @Suppress("DEPRECATION")
            override fun getSystemService(name: String): Any? {
                return if (name == Context.VIBRATOR_SERVICE) throwingVibrate else null
            }
            override fun getApplicationContext(): Context = this
        }

        HapticHelper.performStartListening(null, contextModeB)
        HapticHelper.performCaptured(null, contextModeB)
        HapticHelper.performSaveSuccess(null, contextModeB)
        HapticHelper.performTestEmit(null, contextModeB)
        HapticHelper.performRemoteClick(null, contextModeB)

        // Verify calls actually reached the vibrator and were safely handled
        assertTrue("vibrate must have been attempted and caught safely", throwingVibrate.vibrateMsCallCount > 0 || throwingVibrate.vibratePatternCallCount > 0)
    }

    /**
     * EMPIRICAL STRESS TEST 10:
     * HapticHelper with Throwing VibratorManager (Android S+ simulation):
     * Tests manager returning throwing vibrator.
     */
    @Test
    fun testHapticHelper_throwingVibratorManager_crashImmunity() {
        val throwingVibrator = ThrowingVibrator(throwOnHasVibrator = false, throwOnVibrate = true)
        val throwingManager = ThrowingVibratorManager(throwingVibrator)

        val contextWithManager = object : ContextWrapper(null) {
            @Suppress("DEPRECATION")
            override fun getSystemService(name: String): Any? {
                return when (name) {
                    Context.VIBRATOR_MANAGER_SERVICE -> throwingManager
                    Context.VIBRATOR_SERVICE -> throwingVibrator
                    else -> null
                }
            }
            override fun getApplicationContext(): Context = this
        }

        HapticHelper.performStartListening(null, contextWithManager)
        HapticHelper.performCaptured(null, contextWithManager)
        HapticHelper.performSaveSuccess(null, contextWithManager)
        HapticHelper.performTestEmit(null, contextWithManager)
        HapticHelper.performRemoteClick(null, contextWithManager)
        HapticHelper.performTransmissionConfirmed(contextWithManager)
    }

    /**
     * EMPIRICAL STRESS TEST 11:
     * Highly concurrent multi-threaded stress test:
     * 50 coroutines calling all 5 HapticHelper methods concurrently
     * with random combinations of throwing/null feedback and vibrators.
     */
    @Test
    fun testHapticHelper_concurrentMultiThreadStress_noDeadlockOrException() = runBlocking {
        val throwingFeedback = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                throw RuntimeException("Concurrent haptic failure")
            }
        }
        val throwingVibrator = ThrowingVibrator(throwOnHasVibrator = false, throwOnVibrate = true)
        val contextWithVibrator = object : ContextWrapper(null) {
            @Suppress("DEPRECATION")
            override fun getSystemService(name: String): Any? = throwingVibrator
            override fun getApplicationContext(): Context = this
        }

        val jobs = (0 until 50).map { workerId ->
            launch(Dispatchers.Default) {
                val ctx = if (workerId % 2 == 0) contextWithVibrator else null
                val fb = if (workerId % 3 == 0) throwingFeedback else null

                HapticHelper.performStartListening(fb, ctx)
                HapticHelper.performCaptured(fb, ctx)
                HapticHelper.performSaveSuccess(fb, ctx)
                HapticHelper.performTestEmit(fb, ctx)
                HapticHelper.performRemoteClick(fb, ctx)
            }
        }
        jobs.joinAll()
    }
}
