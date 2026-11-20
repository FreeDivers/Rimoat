package org.qinyan.rimoat.hardware

import android.content.Context
import android.content.ContextWrapper
import org.qinyan.rimoat.hardware.learning.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.Assert.*

class HardwareStateRegressionTest {
    @Test fun readinessFollowsBackendAndSwitchesSubscriptions() = runBlocking {
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = null
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = IrTransmitterManager(context, scope)
        try {
            assertTrue(manager.isReady.value)
            manager.mockTransmitter.setReady(false); yield()
            assertFalse(manager.isReady.value)
            manager.mockTransmitter.setReady(true); yield()
            assertTrue(manager.isReady.value)
            manager.selectTransmitterType(TransmitterType.BUILT_IN); yield()
            assertFalse(manager.isReady.value)
            manager.mockTransmitter.setReady(false); manager.mockTransmitter.setReady(true); yield()
            assertFalse(manager.isReady.value)
        } finally { manager.release(); scope.cancel() }
    }

    @Test fun usbLearnerOpensLazilyAndReleasesBeforeTestTransmit() = runBlocking {
        var opened = 0; var closed = 0
        val signal = IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560))
        val learner = LazyUsbLearner("usb", LearnerType.USB_TIQIAA) {
            opened++
            object : IrLearner, AutoCloseable {
                override val type = LearnerType.USB_TIQIAA
                override val isAvailable = MutableStateFlow(true)
                override val state = MutableStateFlow<LearningState>(LearningState.Idle)
                override suspend fun startLearning(timeoutSeconds: Int): Result<IrSignal.Raw> {
                    assertTrue(UsbSessionAccess.mutex.isLocked)
                    state.value = LearningState.Captured(signal)
                    return Result.success(signal)
                }
                override fun cancel() {}
                override fun close() { closed++ }
            }
        }
        assertEquals(0, opened)
        assertTrue(learner.startLearning().isSuccess)
        assertEquals(1, opened); assertEquals(1, closed)
        assertFalse(UsbSessionAccess.mutex.isLocked)
        assertTrue(learner.state.value is LearningState.Captured)
    }
}
