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

}

