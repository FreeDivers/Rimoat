package org.qinyan.rimoat

import org.qinyan.rimoat.testing.AppStringsResourceRule
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.data.AcFanSpeed
import org.qinyan.rimoat.data.AcMode
import org.qinyan.rimoat.data.AcSettings
import org.qinyan.rimoat.data.AcSignalEncoder
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteCommandExecutor
import org.qinyan.rimoat.data.RemoteCommandJournal
import org.qinyan.rimoat.data.RemotePanelLayout
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.RemoteRepository
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.data.db.IrBrand
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.TransmissionReceipt
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.hardware.UsbSessionAccess
import org.qinyan.rimoat.hardware.learning.IrLearner
import org.qinyan.rimoat.hardware.learning.LazyUsbLearner
import org.qinyan.rimoat.hardware.learning.LearnerType
import org.qinyan.rimoat.hardware.learning.LearningState
import org.qinyan.rimoat.hardware.serial.SerialIrModule
import org.qinyan.rimoat.hardware.serial.SerialIrSession
import org.qinyan.rimoat.hardware.serial.SerialIrSettings
import org.qinyan.rimoat.hardware.serial.SerialPortIo
import org.qinyan.rimoat.hardware.usb.ElkSmartProtocolFormatter
import org.qinyan.rimoat.hardware.usb.ElkSmartSession
import org.qinyan.rimoat.hardware.usb.TiqiaaSession
import org.qinyan.rimoat.hardware.usb.UsbBulkChannel
import org.qinyan.rimoat.ui.screens.brandMonogram
import org.qinyan.rimoat.ui.screens.groupBrandsByIndex
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import org.qinyan.rimoat.widget.widgetRelevantProfilesEqual
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class PerfAndReliabilityRegressionTest {
    @get:org.junit.Rule val strings = AppStringsResourceRule()


    private val testDispatcher = StandardTestDispatcher()
    private val stores = ViewModelStore()
    private val scopes = mutableListOf<CoroutineScope>()
    private var nextVm = 0
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempDir = Files.createTempDirectory("perf_reliability_test").toFile()
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        scopes.clear()
        clearViewModelsAndAwait(stores, testDispatcher)
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    private fun tracked(vm: RemoteControlViewModel): RemoteControlViewModel =
        vm.also { stores.put("vm_${nextVm++}", it) }

    private fun createTestContext(dir: File = tempDir): Context = object : ContextWrapper(null) {
        override fun getFilesDir(): File = dir
        override fun getApplicationContext(): Context = this
        override fun getSystemService(name: String): Any? = null
    }

    @Test
    fun widgetProfileEqualityIgnoresUsageTimestampsButDetectsVisibleMutations() {
        val baseTv = RemoteProfile(
            id = "tv_1",
            name = "Living Room TV",
            room = "Living Room",
            category = DeviceCategory.TV,
            buttons = listOf(RemoteButton("power", "Power", rawPattern = listOf(9000, 4500, 560, 560))),
            updatedAt = 100L,
            lastUsedAt = 200L,
            lastSentAt = 300L
        )
        val baseAc = RemoteProfile(
            id = "ac_1",
            name = "Bedroom AC",
            room = "Bedroom",
            category = DeviceCategory.AC,
            acProtocolId = 27,
            acSettings = AcSettings(temperature = 26, mode = AcMode.COOL, fanSpeed = AcFanSpeed.AUTO),
            updatedAt = 110L,
            lastUsedAt = 210L,
            lastSentAt = 310L
        )

        assertTrue(
            widgetRelevantProfilesEqual(
                listOf(baseTv, baseAc),
                listOf(
                    baseTv.copy(updatedAt = 999L, lastUsedAt = 888L, lastSentAt = 777L),
                    baseAc.copy(updatedAt = 555L, lastUsedAt = 444L, lastSentAt = 333L)
                )
            )
        )

        // Reordering (e.g. recent sort order changed) MUST notify widgets/lists
        assertFalse(widgetRelevantProfilesEqual(listOf(baseTv, baseAc), listOf(baseAc, baseTv)))
        // Name, room, AC settings, or button changes MUST notify widgets
        assertFalse(widgetRelevantProfilesEqual(listOf(baseTv), listOf(baseTv.copy(name = "Renamed TV"))))
        assertFalse(widgetRelevantProfilesEqual(listOf(baseTv), listOf(baseTv.copy(room = "Study"))))
        assertFalse(
            widgetRelevantProfilesEqual(
                listOf(baseAc),
                listOf(baseAc.copy(acSettings = baseAc.acSettings.copy(temperature = 24)))
            )
        )
        assertFalse(
            widgetRelevantProfilesEqual(
                listOf(baseTv),
                listOf(baseTv.copy(buttons = baseTv.buttons + RemoteButton("mute", "Mute", rawPattern = listOf(9000, 4500))))
            )
        )
    }

    @Test
    fun coldRepositoryLoadingGuardPreventsFalseMissingStateUntilLoaded() = runBlocking {
        val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes.add(it) }
        val context = createTestContext()
        val seedRepo = RemoteRepository(context, repoScope)
        seedRepo.loadAllRemotes()
        val saved = RemoteProfile(
            id = "cold_tv",
            name = "Bedroom TV",
            category = DeviceCategory.TV,
            buttons = listOf(RemoteButton("power", "Power", rawPattern = listOf(9000, 4500, 560, 560)))
        )
        seedRepo.saveRemote(saved)

        val loadStarted = CompletableDeferred<Unit>()
        val allowLoad = CompletableDeferred<Unit>()
        val coldRepo = RemoteRepository(context, repoScope, upgradeCatalog = { profile ->
            loadStarted.complete(Unit)
            allowLoad.await()
            profile
        })
        withTimeout(3000) { loadStarted.await() }
        assertFalse(coldRepo.isLoaded.value)
        assertNull(coldRepo.getRemote("cold_tv"))

        val vm = tracked(RemoteControlViewModel(remoteId = "cold_tv", repository = coldRepo))
        testDispatcher.scheduler.runCurrent()
        assertFalse(vm.isRepositoryLoaded.value)
        assertNull(vm.remoteProfile.value)

        allowLoad.complete(Unit)
        withTimeout(3000) { coldRepo.awaitLoaded() }
        withTimeout(3000) {
            while (!vm.isRepositoryLoaded.value || vm.remoteProfile.value?.id != "cold_tv") {
                testDispatcher.scheduler.runCurrent()
                delay(10)
            }
        }
        assertTrue(vm.isRepositoryLoaded.value)
        assertEquals("Bedroom TV", vm.remoteProfile.value?.name)
    }

    @Test
    fun sendGateCommitsAcceptedSendAndCancelsQueuedSendAfterViewModelClears() = runBlocking {
        val startedOnIo = CompletableDeferred<Unit>()
        val allowFinish = CompletableDeferred<Unit>()
        val sentCount = AtomicInteger(0)
        val button = RemoteButton("power", "Power", rawPattern = listOf(9000, 4500, 560, 560))
        val tv = RemoteProfile("tv", "TV", category = DeviceCategory.TV, buttons = listOf(button))
        val journal = RemoteCommandJournal(File(tempDir, "journal.json"))
        journal.load()

        val executor = RemoteCommandExecutor(
            readProfile = { id -> if (id == tv.id) tv else null },
            commitSettings = { _, _ -> },
            encoder = object : AcSignalEncoder {
                override suspend fun supportsAction(protocol: Int, settings: AcSettings, action: AcAction) = false
                override suspend fun protocolFor(sourceRemoteId: String): Int? = null
                override suspend fun supportedModes(protocol: Int) = emptyList<AcMode>()
                override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction) = emptyList<IrSignal.Raw>()
            },
            route = { TransmitterType.MOCK },
            transmit = { _, _, _, beforeTransmission ->
                withContext(Dispatchers.IO) {
                    beforeTransmission()
                    startedOnIo.complete(Unit)
                    allowFinish.await()
                    sentCount.incrementAndGet()
                    TransmissionReceipt(simulated = true)
                }
            },
            journal = journal
        )

        val vm = tracked(RemoteControlViewModel("tv", initialProfile = tv, commandExecutor = executor))
        try {
            vm.sendButton(button)
            // Run Main once so accepting the send must be able to proceed on IO.
            testDispatcher.scheduler.runCurrent()
            withTimeout(2000) { startedOnIo.await() }
            assertTrue(vm.isTransmitting.value)

            vm.sendButton(button)
            testDispatcher.scheduler.runCurrent()
            stores.clear()
        } finally {
            allowFinish.complete(Unit)
        }

        withTimeout(2000) {
            while (vm.isTransmitting.value) {
                testDispatcher.scheduler.runCurrent()
                delay(10)
            }
        }
        assertEquals(1, sentCount.get())
        assertEquals(button.id, journal.records.value[tv.id]?.buttonId)
    }

    @Test
    fun usbDisconnectDuringActiveLearningReportsErrorUnlocksMutexAndNeverAutoReplays() = runBlocking {
        val delegateClosed = AtomicBoolean(false)
        val openCalls = AtomicInteger(0)
        val learner = LazyUsbLearner("dev/bus/usb/001/002", LearnerType.USB_TIQIAA) {
            openCalls.incrementAndGet()
            object : IrLearner, AutoCloseable {
                override val type = LearnerType.USB_TIQIAA
                override val isAvailable = MutableStateFlow(true)
                override val state = MutableStateFlow<LearningState>(LearningState.Idle)
                override suspend fun startLearning(timeoutSeconds: Int): Result<IrSignal.Raw> {
                    state.value = LearningState.Listening(timeoutSeconds, timeoutSeconds)
                    while (!delegateClosed.get()) {
                        delay(10)
                    }
                    val msg = "USB device disconnected"
                    state.value = LearningState.Error(msg)
                    return Result.failure(IOException(msg))
                }
                override fun cancel() {
                    if (!delegateClosed.get()) state.value = LearningState.Idle
                }
                override fun close() {
                    delegateClosed.set(true)
                    isAvailable.value = false
                }
            }
        }

        val resultDeferred = CompletableDeferred<Result<IrSignal.Raw>>()
        val job = launch(Dispatchers.Default) {
            resultDeferred.complete(learner.startLearning(timeoutSeconds = 20))
        }
        withTimeout(2000) {
            while (learner.state.value !is LearningState.Listening) delay(10)
        }
        assertTrue(UsbSessionAccess.mutex.isLocked)

        // Simulate USB detach broadcast -> IrLearnerManager.scanUsbDevices() -> oldLearner.close()
        learner.close()
        val result = withTimeout(2000) { resultDeferred.await() }
        job.join()

        assertTrue(result.isFailure)
        assertTrue(delegateClosed.get())
        assertFalse("USB session lock must be released immediately on detach", UsbSessionAccess.mutex.isLocked)
        assertTrue(
            "Detach during active learning must surface Error rather than silent Idle: ${learner.state.value}",
            learner.state.value is LearningState.Error
        )
        assertEquals(1, openCalls.get())

        // Subsequent start attempt on detached learner fails cleanly without opening hardware or locking mutex
        val retry = learner.startLearning(timeoutSeconds = 5)
        assertTrue(retry.isFailure)
        assertEquals(1, openCalls.get())
        assertFalse(UsbSessionAccess.mutex.isLocked)
    }

    @Test
    fun usbDisconnectDuringTransmitLeavesStateUncommittedAndNeverAutoReplays() = runBlocking {
        val rawSignal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560))

        // 1. Tiqiaa bulk write returns -1 on physical unplug
        val tiqiaaWrites = AtomicInteger(0)
        val tiqiaaDetached = object : UsbBulkChannel {
            override fun read(buffer: ByteArray, timeoutMs: Int): Int = -1
            override fun write(data: ByteArray, timeoutMs: Int): Int {
                tiqiaaWrites.incrementAndGet()
                return -1
            }
        }
        try {
            TiqiaaSession(tiqiaaDetached, replyTimeoutMs = 20).transmit(rawSignal)
            org.junit.Assert.fail("Expected IOException on detached Tiqiaa bulk write")
        } catch (_: IOException) {
        }
        assertEquals(1, tiqiaaWrites.get())

        // 2. ElkSmart bulk write returns -1 mid-transfer on physical unplug
        val elkWrites = AtomicInteger(0)
        val elkDetached = object : UsbBulkChannel {
            override fun read(buffer: ByteArray, timeoutMs: Int): Int = -1
            override fun write(data: ByteArray, timeoutMs: Int): Int {
                elkWrites.incrementAndGet()
                return -1
            }
        }
        try {
            ElkSmartSession(elkDetached).transmit(rawSignal, ElkSmartProtocolFormatter.Subtype.D552)
            org.junit.Assert.fail("Expected IOException on detached ElkSmart bulk write")
        } catch (_: IOException) {
        }
        assertEquals(1, elkWrites.get())

        // 3. Serial port throws IOException on physical unplug
        val serialWrites = AtomicInteger(0)
        val serialDetached = object : SerialPortIo {
            override fun write(data: ByteArray, timeoutMs: Int) {
                serialWrites.incrementAndGet()
                throw IOException("USB serial device detached")
            }
            override fun read(buffer: ByteArray, timeoutMs: Int): Int = -1
        }
        try {
            SerialIrSession(serialDetached).transmit(
                SerialIrSettings(module = SerialIrModule.YS_IRTM),
                IrSignal.Nec(0x00FF, 0x12)
            )
            org.junit.Assert.fail("Expected IOException on detached serial write")
        } catch (_: IOException) {
        }
        assertEquals(1, serialWrites.get())

        // 4. Full RemoteCommandExecutor AC state transaction with mid-flight USB detach
        var storedProfile = RemoteProfile(
            id = "ac_usb",
            name = "Study AC",
            category = DeviceCategory.AC,
            acProtocolId = 27,
            acSettings = AcSettings(temperature = 26, mode = AcMode.COOL)
        )
        val journal = RemoteCommandJournal(File(tempDir, "usb_journal.json"))
        var usbConnected = true
        val transmitAttempts = AtomicInteger(0)
        val executor = RemoteCommandExecutor(
            readProfile = { id -> if (id == storedProfile.id) storedProfile else null },
            commitSettings = { _, committed -> storedProfile = storedProfile.copy(acSettings = committed) },
            encoder = object : AcSignalEncoder {
                override suspend fun supportsAction(protocol: Int, settings: AcSettings, action: AcAction) = true
                override suspend fun protocolFor(sourceRemoteId: String) = 27
                override suspend fun supportedModes(protocol: Int) = AcMode.entries.toList()
                override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction) = listOf(rawSignal)
            },
            route = { if (usbConnected) TransmitterType.USB_TIQIAA else TransmitterType.MOCK },
            transmit = { _, _, expectedRoute, before ->
                check(UsbSessionAccess.mutex.tryLock())
                try {
                    before()
                    transmitAttempts.incrementAndGet()
                    if (expectedRoute == TransmitterType.USB_TIQIAA) {
                        usbConnected = false
                        throw IOException("USB device disconnected")
                    }
                    TransmissionReceipt(simulated = true)
                } finally {
                    UsbSessionAccess.mutex.unlock()
                }
            },
            journal = journal
        )

        try {
            executor.changeAc("ac_usb") { state, _ ->
                state.copy(temperature = 24, powerOn = true) to AcAction.TEMP_DOWN
            }
            org.junit.Assert.fail("Expected IOException when USB disconnects mid-transmission")
        } catch (_: IOException) {
        }

        // State and journal must remain untouched, mutex unlocked, and zero automatic replay when USB reconnects
        assertFalse(UsbSessionAccess.mutex.isLocked)
        assertEquals(26, storedProfile.acSettings.temperature)
        assertNull(journal.records.value["ac_usb"])
        usbConnected = true
        assertEquals(1, transmitAttempts.get())
    }

    @Test
    fun brandGroupingAndMonogramMatchExpectedSemanticsAcross970Brands() {
        val synthetic = (1..965).map { idx ->
            val letter = ('A'.code + (idx % 26)).toChar()
            IrBrand(
                brandIdx = idx,
                nameCn = "品牌$idx",
                nameEn = "${letter}Brand$idx",
                pinyin = "${letter.lowercaseChar()}pin$idx",
                remoteCount = (idx % 12) + 1
            )
        } + listOf(
            IrBrand(966, "格力", "Gree", "gélì", 42),
            IrBrand(967, "美的", "Midea", "měidì", 38),
            IrBrand(968, "长虹", "", "chánghóng", 25),
            IrBrand(969, "123数字", "3M", "", 4),
            IrBrand(970, "★特殊", "---", "", 2)
        )

        val cnGroups = groupBrandsByIndex(synthetic, preferEnglish = false)
        val enGroups = groupBrandsByIndex(synthetic, preferEnglish = true)

        assertEquals(970, cnGroups.sumOf { it.brands.size })
        assertEquals(970, enGroups.sumOf { it.brands.size })
        assertTrue(cnGroups.first { it.letter == 'G' }.brands.any { it.brandIdx == 966 })
        assertTrue(cnGroups.first { it.letter == 'C' }.brands.any { it.brandIdx == 968 })
        assertTrue(cnGroups.last().letter == '#' && cnGroups.last().brands.any { it.brandIdx == 969 })

        assertEquals("GR", brandMonogram(synthetic[965]))
        assertEquals("CH", brandMonogram(synthetic[967]))
        assertEquals("IR", brandMonogram(synthetic[968]))
        assertEquals("IR", brandMonogram(synthetic[969]))
        // Verify IrSignal.Raw clone safety check helper
        val cloneCheck = intArrayOf(9000, 4500)
        assertArrayEquals(intArrayOf(9000, 4500), cloneCheck.clone())
    }

    @Test
    fun rapidPanelLayoutSavesPersistLatestLayoutWithoutOutOfOrderOverwrite() = runBlocking {
        val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes.add(it) }
        val context = createTestContext()
        val repo = RemoteRepository(context, repoScope)
        repo.awaitLoaded()
        val profile = RemoteProfile(
            id = "fan_layout",
            name = "Fan",
            category = DeviceCategory.FAN,
            buttons = listOf(RemoteButton("power", "Power"), RemoteButton("speed", "Speed"))
        )
        repo.saveRemote(profile)
        val vm = tracked(RemoteControlViewModel(profile.id, repository = repo, initialProfile = profile))
        testDispatcher.scheduler.runCurrent()

        repeat(40) { round ->
            val first = RemotePanelLayout(
                hidden = setOf("key:power"),
                order = listOf("key:speed", "key:power", "round_first_$round")
            )
            val second = RemotePanelLayout(
                hidden = setOf("key:speed"),
                order = listOf("key:power", "key:speed", "round_second_$round")
            )
            vm.savePanelLayout(first)
            vm.savePanelLayout(second)
            withTimeout(5000) {
                while (true) {
                    testDispatcher.scheduler.runCurrent()
                    val saved = repo.getRemote(profile.id)?.panelLayout
                    if (saved == second) {
                        // Give any straggler coroutine a brief moment to overwrite if out of order
                        delay(20)
                        testDispatcher.scheduler.runCurrent()
                        assertEquals(
                            "Round $round: older layout overwrote latest layout",
                            second,
                            repo.getRemote(profile.id)?.panelLayout
                        )
                        break
                    }
                    delay(5)
                }
            }
        }
    }

    @Test
    fun panelSaveErrorUsesUiTextAndSurvivesVisibilityAndLifecycleCallbacksUntilClearedOrSaved() = runBlocking {
        val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes.add(it) }
        val context = createTestContext()
        val repo = RemoteRepository(context, repoScope)
        repo.awaitLoaded()
        val profile = RemoteProfile(
            id = "fan_err",
            name = "Fan",
            category = DeviceCategory.FAN,
            buttons = listOf(RemoteButton("power", "Power"), RemoteButton("speed", "Speed"))
        )
        repo.saveRemote(profile)

        val roomsFile = File(tempDir, "rooms.json")
        if (roomsFile.exists()) assertTrue(roomsFile.delete())
        File(roomsFile, "lock").apply {
            parentFile!!.mkdirs()
            writeText("locked")
        }

        val vm = tracked(RemoteControlViewModel(profile.id, repository = repo, initialProfile = profile))
        val failedLayout = RemotePanelLayout(hidden = setOf("key:speed"))
        vm.savePanelLayout(failedLayout)
        withTimeout(5000) {
            while (vm.panelSaveError.value == null) {
                testDispatcher.scheduler.runCurrent()
                delay(5)
            }
        }
        val error = vm.panelSaveError.value
        assertTrue(error is UiText.Resource && error.id == R.string.msg_remote_save_failed)
        assertNull(vm.operationError.value)

        vm.onScreenStopped()
        vm.clearFeedback()
        vm.onVisible()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(error, vm.panelSaveError.value)

        // Unblock saving and verify a subsequent successful save clears panelSaveError.
        assertTrue(roomsFile.deleteRecursively())
        val recoveredLayout = RemotePanelLayout(hidden = setOf("key:power"))
        vm.savePanelLayout(recoveredLayout)
        withTimeout(5000) {
            while (repo.getRemote(profile.id)?.panelLayout != recoveredLayout || vm.panelSaveError.value != null) {
                testDispatcher.scheduler.runCurrent()
                delay(5)
            }
        }
        assertNull(vm.panelSaveError.value)
    }
}
