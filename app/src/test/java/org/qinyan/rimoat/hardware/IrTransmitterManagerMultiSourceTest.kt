package org.qinyan.rimoat.hardware

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IrTransmitterManagerMultiSourceTest {

    private fun createMockContext(): Context {
        return object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
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

