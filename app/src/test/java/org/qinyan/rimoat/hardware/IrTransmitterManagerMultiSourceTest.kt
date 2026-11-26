package org.qinyan.rimoat.hardware

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.hardware.ConsumerIrManager
import android.media.AudioManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [FakeConsumerIrManager::class])
@OptIn(ExperimentalCoroutinesApi::class)
class IrTransmitterManagerMultiSourceTest {

    @Test
    fun defaultScopeKeepsRoutingStateUpdatesAfterChildFailure() = runBlocking<Unit> {
        val manager = IrTransmitterManager(createMockContext())
        val scope = IrTransmitterManager::class.java.getDeclaredField("scope").run {
            isAccessible = true
            get(manager) as CoroutineScope
        }
        try {
            withTimeout(5_000) {
                manager.setForceMockMode(false)
                manager.selectTransmitterType(TransmitterType.BUILT_IN)
                manager.activeTransmitter.first { it.type == TransmitterType.BUILT_IN }
                manager.transmitterType.first { it == TransmitterType.BUILT_IN }
                manager.isReady.first { !it }

                val failure = IllegalStateException("Background task failed")
                val reportedFailure = CompletableDeferred<Throwable>()
                scope.launch(CoroutineExceptionHandler { _, error -> reportedFailure.complete(error) }) {
                    throw failure
                }.join()

                assertSame(failure, reportedFailure.await())
                assertTrue(scope.isActive)

                manager.selectTransmitterType(null)
                manager.activeTransmitter.first { it.type == TransmitterType.MOCK }
                manager.transmitterType.first { it == TransmitterType.MOCK }
                manager.isReady.first { it }
            }
        } finally {
            manager.release()
            scope.cancel()
        }
    }

    private fun createMockContext(): Context {
        return object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
        }
    }

    @Suppress("DEPRECATION")
    private fun configureWiredAudio(hasEmitter: Boolean): Application {
        val app = RuntimeEnvironment.getApplication()
        val irManager = app.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager
        Shadow.extract<FakeConsumerIrManager>(irManager).hasEmitter = hasEmitter
        val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isWiredHeadsetOn = true
        return app
    }

    @Test
    fun autoPrefersBuiltInIrWhenWiredAudioIsAlsoAvailable() = runTest {
        val app = configureWiredAudio(hasEmitter = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = IrTransmitterManager(app, scope)
        try {
            manager.refreshHardware()
            assertTrue(manager.builtInTransmitter.isReady.value)
            assertTrue(manager.audioTransmitter.isReady.value)
            assertEquals(TransmitterType.BUILT_IN, manager.currentTransmitterType())
            assertEquals(TransmitterType.BUILT_IN, manager.activeTransmitter.value.type)

            val signal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560))
            val receipt = manager.transmitWithReceipt(listOf(signal)).getOrThrow()
            assertFalse(receipt.simulated)
            assertEquals(TransmitterType.BUILT_IN, manager.lastTransmitLog.value?.transmitterType)
            val irManager = app.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager
            val hardware = Shadow.extract<FakeConsumerIrManager>(irManager)
            assertEquals(signal.frequencyHz, hardware.lastFrequency)
            assertArrayEquals(signal.patternMicros, hardware.lastPattern)
        } finally {
            manager.release()
            scope.cancel()
        }
    }

    @Test
    fun manualAudioSelectionOverridesAvailableBuiltInIr() = runTest {
        val app = configureWiredAudio(hasEmitter = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = IrTransmitterManager(app, scope)
        try {
            manager.refreshHardware()
            manager.selectTransmitterType(TransmitterType.AUDIO)
            assertEquals(TransmitterType.AUDIO, manager.currentTransmitterType())
            assertEquals(TransmitterType.AUDIO, manager.activeTransmitter.value.type)

            manager.selectTransmitterType(null)
            assertEquals(TransmitterType.BUILT_IN, manager.currentTransmitterType())
            assertEquals(TransmitterType.BUILT_IN, manager.activeTransmitter.value.type)
        } finally {
            manager.release()
            scope.cancel()
        }
    }

    @Test
    fun autoFallsBackToWiredAudioWhenBuiltInIrIsUnavailable() = runTest {
        val app = configureWiredAudio(hasEmitter = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = IrTransmitterManager(app, scope)
        try {
            manager.refreshHardware()
            assertFalse(manager.builtInTransmitter.isReady.value)
            assertTrue(manager.audioTransmitter.isReady.value)
            assertEquals(TransmitterType.AUDIO, manager.currentTransmitterType())
            assertEquals(TransmitterType.AUDIO, manager.activeTransmitter.value.type)
        } finally {
            manager.release()
            scope.cancel()
        }
    }

    @Test
    fun testDefaultResolutionToMockWhenNoPhysicalHardware() = runTest {
        val manager = IrTransmitterManager(createMockContext(), CoroutineScope(Dispatchers.Unconfined))

        // Without USB hardware or ConsumerIr, manager should default to MockIrTransmitter
        assertEquals(TransmitterType.MOCK, manager.transmitterType.value)
        assertEquals(TransmitterType.MOCK, manager.activeTransmitter.value.type)
        manager.release()
    }

    @Test
    fun testForceMockModeOverridesAllTransmitters() = runTest {
        val manager = IrTransmitterManager(createMockContext(), CoroutineScope(Dispatchers.Unconfined))

        manager.setForceMockMode(true)
        assertTrue(manager.forceMockMode.value)
        assertEquals(TransmitterType.MOCK, manager.transmitterType.value)

        manager.setForceMockMode(false)
        assertEquals(false, manager.forceMockMode.value)
        manager.release()
    }

    @Test
    fun testManualTransmitterSelection() = runTest {
        val manager = IrTransmitterManager(createMockContext(), CoroutineScope(Dispatchers.Unconfined))

        // Select Audio
        manager.selectTransmitterType(TransmitterType.AUDIO)
        assertEquals(TransmitterType.AUDIO, manager.selectedTransmitterType.value)
        assertEquals(TransmitterType.AUDIO, manager.activeTransmitter.value.type)

        // Select the USB transmitter
        manager.selectTransmitterType(TransmitterType.USB_TIQIAA)
        assertEquals(TransmitterType.USB_TIQIAA, manager.activeTransmitter.value.type)

        // Select ElkSmart USB
        manager.selectTransmitterType(TransmitterType.USB_ELKSMART)
        assertEquals(TransmitterType.USB_ELKSMART, manager.activeTransmitter.value.type)

        // Select USB Serial
        manager.selectTransmitterType(TransmitterType.USB_SERIAL)
        assertEquals(TransmitterType.USB_SERIAL, manager.activeTransmitter.value.type)

        // Clear manual selection (return to Auto)
        manager.selectTransmitterType(null)
        assertEquals(null, manager.selectedTransmitterType.value)
        assertEquals(TransmitterType.MOCK, manager.activeTransmitter.value.type)
        manager.release()
    }

    @Test
    fun testTransmitRecordsDetailedLog() = runTest {
        val manager = IrTransmitterManager(createMockContext(), CoroutineScope(Dispatchers.Unconfined))
        manager.mockTransmitter.simulateDelayMs = 0L

        val signal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560))
        val result = manager.transmit(signal)

        assertTrue(result.isSuccess)
        val log = manager.lastTransmitLog.value
        assertNotNull(log)
        assertEquals(TransmitterType.MOCK, log?.transmitterType)
        assertEquals(true, log?.isSuccess)
        assertEquals(signal, log?.signal)
        manager.release()
    }

    @Test
    fun testReleaseUnregistersReceiversSafely() {
        val manager = IrTransmitterManager(createMockContext())
        manager.release()
        // Calling release multiple times should be idempotent and safe
        manager.release()
    }
    @Test fun immediateModeSwitchUsesNewRouteEvenBeforeStateFlowUpdates() = runTest {
        val scheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
        val job = kotlinx.coroutines.SupervisorJob()
        val manager = IrTransmitterManager(createMockContext(), CoroutineScope(job + kotlinx.coroutines.test.StandardTestDispatcher(scheduler)))
        try {
            manager.mockTransmitter.simulateDelayMs = 0
            manager.selectTransmitterType(TransmitterType.BUILT_IN)
            scheduler.runCurrent()
            assertEquals(TransmitterType.BUILT_IN, manager.activeTransmitter.value.type)
            manager.setForceMockMode(true)
            val result = manager.transmitWithReceipt(listOf(IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560))))
            assertTrue(result.getOrThrow().simulated)
            assertEquals(TransmitterType.MOCK, manager.lastTransmitLog.value?.transmitterType)
        } finally { job.cancel(); manager.release() }
    }

    @Test
    fun coldStartCommandExecutorAndViewModelDoNotRouteAsMockBeforeInitialHardwareCheck() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val irManager = app.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager
        Shadow.extract<FakeConsumerIrManager>(irManager).hasEmitter = true

        // Construct on the main thread with a paused dispatcher so deferInitialHardwareCheck=true
        // and the background refreshHardware() coroutine has not yet executed when sends begin.
        // Note: init launches refreshHardware() with Dispatchers.IO, so use a Context wrapper that
        // gates ConsumerIrManager.hasIrEmitter() or verify with a controlled IO race.
        val gate = java.util.concurrent.CountDownLatch(1)
        val entered = java.util.concurrent.CountDownLatch(1)
        val gatedContext = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? {
                if (name == Context.USB_SERVICE && Thread.currentThread() != android.os.Looper.getMainLooper().thread) {
                    entered.countDown()
                    gate.await(5, java.util.concurrent.TimeUnit.SECONDS)
                }
                return super.getSystemService(name)
            }
        }

        val job = SupervisorJob()
        val managerScope = CoroutineScope(job + Dispatchers.Default)
        val manager = IrTransmitterManager(gatedContext, managerScope)
        try {
            assertTrue("Background refreshHardware() should start on IO", entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertFalse(
                "Precondition: deferred initial hardware check has not completed yet",
                manager.builtInTransmitter.isReady.value
            )
            assertEquals(
                "Synchronous currentTransmitterType() reflects pre-check MOCK state",
                TransmitterType.MOCK,
                manager.currentTransmitterType()
            )

            // 1. RemoteCommandExecutor (same wiring as IrRemoteApplication) sending AC state & button
            val power = org.qinyan.rimoat.data.RemoteButton(
                id = "power",
                label = "Power",
                rawPattern = listOf(9000, 4500, 560, 560),
                alternateRawPattern = listOf(8000, 4000, 560, 560)
            )
            var acProfile = org.qinyan.rimoat.data.RemoteProfile(
                id = "ac",
                name = "AC",
                category = org.qinyan.rimoat.data.DeviceCategory.AC,
                acProtocolId = 27
            )
            val tvProfile = org.qinyan.rimoat.data.RemoteProfile(
                id = "tv",
                name = "TV",
                category = org.qinyan.rimoat.data.DeviceCategory.TV,
                buttons = listOf(power)
            )
            val journalFile = java.io.File.createTempFile("rimoat-cold-journal-", ".json")
            journalFile.delete()
            val journal = org.qinyan.rimoat.data.RemoteCommandJournal(journalFile)
            val encoder = object : org.qinyan.rimoat.data.AcSignalEncoder {
                override suspend fun protocolFor(sourceRemoteId: String) = 27
                override suspend fun supportedModes(protocol: Int) = listOf(org.qinyan.rimoat.data.AcMode.COOL)
                override suspend fun encode(
                    protocol: Int,
                    settings: org.qinyan.rimoat.data.AcSettings,
                    action: org.qinyan.rimoat.data.AcAction
                ) = listOf(IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560)))
            }
            val executor = org.qinyan.rimoat.data.RemoteCommandExecutor(
                readProfile = { id ->
                    when (id) {
                        "ac" -> acProfile
                        "tv" -> tvProfile
                        else -> null
                    }
                },
                commitSettings = { _, committed -> acProfile = acProfile.copy(acSettings = committed) },
                encoder = encoder,
                route = manager::currentTransmitterTypeAfterCheck,
                transmit = { signals, owner, route, before ->
                    manager.transmitWithReceipt(signals, owner, route, before).getOrThrow()
                },
                journal = journal
            )

            try {
                // Release the initial hardware check shortly after execute starts so any caller that
                // reads route() without waiting sees MOCK while transmitWithReceipt waits and sees BUILT_IN.
                val releaser = launch(Dispatchers.Default) {
                    kotlinx.coroutines.delay(50)
                    gate.countDown()
                }
                val acResult = executor.changeAc("ac") { state, _ ->
                    state.apply(org.qinyan.rimoat.data.AcAction.TEMP_UP) to org.qinyan.rimoat.data.AcAction.TEMP_UP
                }!!
                releaser.join()
                assertFalse("Cold-start AC send must use BUILT_IN rather than MOCK", acResult.simulated)
                assertEquals(27, acProfile.acSettings.temperature)
                assertEquals(TransmitterType.BUILT_IN, manager.lastTransmitLog.value?.transmitterType)

                val btnResult = executor.executeButton("tv", "power")!!
                assertFalse("Cold-start button send must use BUILT_IN rather than MOCK", btnResult.simulated)
                assertEquals(1, journal.records.value.getValue("tv").toggles["false:power"])
                assertEquals(null, journal.records.value.getValue("tv").toggles["true:power"])
                assertEquals(TransmitterType.BUILT_IN, manager.currentTransmitterTypeAfterCheck())
            } finally {
                gate.countDown()
                journalFile.delete()
            }
        } finally {
            gate.countDown()
            job.cancel()
            manager.release()
        }
    }

    @Test
    fun coldStartViewModelFallbackPathsDoNotFailRouteCheckOrUseDemoToggleKey() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val irManager = app.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager
        Shadow.extract<FakeConsumerIrManager>(irManager).hasEmitter = true

        fun createGatedContext(): Triple<Context, java.util.concurrent.CountDownLatch, java.util.concurrent.CountDownLatch> {
            val entered = java.util.concurrent.CountDownLatch(1)
            val gate = java.util.concurrent.CountDownLatch(1)
            val ctx = object : ContextWrapper(app) {
                override fun getApplicationContext(): Context = this
                override fun getSystemService(name: String): Any? {
                    if (name == Context.USB_SERVICE && Thread.currentThread() != android.os.Looper.getMainLooper().thread) {
                        entered.countDown()
                        gate.await(5, java.util.concurrent.TimeUnit.SECONDS)
                    }
                    return super.getSystemService(name)
                }
            }
            return Triple(ctx, entered, gate)
        }

        // First manager for AC fallback path (no commandExecutor)
        val (acCtx, acEntered, acGate) = createGatedContext()
        val acJob = SupervisorJob()
        val acManager = IrTransmitterManager(acCtx, CoroutineScope(acJob + Dispatchers.Default))
        val store = androidx.lifecycle.ViewModelStore()
        try {
            assertTrue(acEntered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertFalse(acManager.builtInTransmitter.isReady.value)
            val encoder = object : org.qinyan.rimoat.data.AcSignalEncoder {
                override suspend fun protocolFor(sourceRemoteId: String) = 27
                override suspend fun supportedModes(protocol: Int) = listOf(org.qinyan.rimoat.data.AcMode.COOL)
                override suspend fun encode(
                    protocol: Int,
                    settings: org.qinyan.rimoat.data.AcSettings,
                    action: org.qinyan.rimoat.data.AcAction
                ) = listOf(IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560)))
            }
            val acProfile = org.qinyan.rimoat.data.RemoteProfile(
                id = "ac",
                name = "AC",
                category = org.qinyan.rimoat.data.DeviceCategory.AC,
                acProtocolId = 27
            )
            val acVm = org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel(
                remoteId = "ac",
                transmitterManager = acManager,
                initialProfile = acProfile,
                acSignalEncoder = encoder
            ).also { store.put("ac", it) }

            val acReleaser = launch(Dispatchers.Default) {
                kotlinx.coroutines.delay(50)
                acGate.countDown()
            }
            acVm.increaseTemp()
            withTimeout(5_000) {
                while (acVm.isTransmitting.value || acManager.lastTransmitLog.value == null) {
                    org.robolectric.shadows.ShadowLooper.shadowMainLooper().idle()
                    kotlinx.coroutines.delay(10)
                }
            }
            acReleaser.join()
            assertTrue(
                "Cold-start AC send failed: ${acManager.lastTransmitLog.value?.errorMessage}",
                acManager.lastTransmitLog.value!!.isSuccess
            )
            assertEquals(null, acVm.operationError.value)
            assertEquals(27, acVm.acState.value.temperature)
            assertEquals(TransmitterType.BUILT_IN, acManager.lastTransmitLog.value?.transmitterType)

            // Second fresh manager for button fallback path (countKey must use "real:power", not "demo:power")
            val (tvCtx, tvEntered, tvGate) = createGatedContext()
            val tvJob = SupervisorJob()
            val tvManager = IrTransmitterManager(tvCtx, CoroutineScope(tvJob + Dispatchers.Default))
            try {
                assertTrue(tvEntered.await(5, java.util.concurrent.TimeUnit.SECONDS))
                assertFalse(tvManager.builtInTransmitter.isReady.value)
                val power = org.qinyan.rimoat.data.RemoteButton(
                    id = "power",
                    label = "Power",
                    rawPattern = listOf(9000, 4500, 560, 560),
                    alternateRawPattern = listOf(8000, 4000, 560, 560)
                )
                val tvProfile = org.qinyan.rimoat.data.RemoteProfile(
                    id = "tv",
                    name = "TV",
                    category = org.qinyan.rimoat.data.DeviceCategory.TV,
                    buttons = listOf(power)
                )
                val tvVm = org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel(
                    remoteId = "tv",
                    transmitterManager = tvManager,
                    initialProfile = tvProfile
                ).also { store.put("tv", it) }

                val tvReleaser = launch(Dispatchers.Default) {
                    kotlinx.coroutines.delay(50)
                    tvGate.countDown()
                }
                // First send before initial hardware check completes
                tvVm.sendButton(power)
                withTimeout(5_000) {
                    while (tvVm.isTransmitting.value || tvManager.lastTransmitLog.value == null) {
                        org.robolectric.shadows.ShadowLooper.shadowMainLooper().idle()
                        kotlinx.coroutines.delay(10)
                    }
                }
                tvReleaser.join()
                assertEquals(null, tvVm.operationError.value)
                val firstLog = tvManager.lastTransmitLog.value!!
                assertEquals(9000, (firstLog.signal as IrSignal.Raw).patternMicros.first())

                // Ensure distinct timestamp so MutableStateFlow emits even if signal were duplicated
                kotlinx.coroutines.delay(15)
                // Second send after hardware check completed must alternate to 8000 (proving first used "real:power")
                tvVm.sendButton(power)
                withTimeout(5_000) {
                    while (tvVm.isTransmitting.value || tvManager.lastTransmitLog.value == firstLog) {
                        org.robolectric.shadows.ShadowLooper.shadowMainLooper().idle()
                        kotlinx.coroutines.delay(10)
                    }
                }
                val secondSignal = tvManager.lastTransmitLog.value!!.signal as IrSignal.Raw
                assertEquals(8000, secondSignal.patternMicros.first())
            } finally {
                tvGate.countDown()
                tvJob.cancel()
                tvManager.release()
            }
        } finally {
            acGate.countDown()
            store.clear()
            acJob.cancel()
            acManager.release()
        }
    }

}

