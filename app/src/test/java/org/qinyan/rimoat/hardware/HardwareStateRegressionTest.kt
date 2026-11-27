package org.qinyan.rimoat.hardware

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import org.qinyan.rimoat.hardware.learning.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HardwareStateRegressionTest {
    @org.junit.Before
    fun initializeStrings() = org.qinyan.rimoat.i18n.AppStrings.initialize(RuntimeEnvironment.getApplication())

    @Test fun readinessFollowsBackendAndSwitchesSubscriptions() = runBlocking {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
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

    @Test fun usbBroadcastRefreshesHardwareOffMainThread() = runBlocking {
        val context = HardwareContext()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val manager = IrTransmitterManager(context, scope)
        val probe = RefreshProbe().apply { release.countDown() }
        try {
            manager.refreshHardware()
            context.usbProbe = probe
            val registration = context.receivers.single { it.filter.hasAction(UsbManager.ACTION_USB_DEVICE_ATTACHED) }
            assertTrue(registration.flags and ContextCompat.RECEIVER_NOT_EXPORTED != 0)
            registration.receiver.onReceive(context, Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED))
            assertTrue("USB broadcast must refresh hardware", probe.entered.await(3, TimeUnit.SECONDS))
            assertFalse("Hardware discovery must stay off the main thread", probe.onMain.get())
            manager.refreshHardware()
            val audio = context.receivers.single { it.filter.hasAction(Intent.ACTION_HEADSET_PLUG) }
            assertTrue(audio.flags and ContextCompat.RECEIVER_NOT_EXPORTED != 0)
            audio.receiver.onReceive(context, Intent(Intent.ACTION_HEADSET_PLUG).putExtra("state", 1))
            assertTrue(manager.audioTransmitter.isReady.value)
            audio.receiver.onReceive(context, Intent(Intent.ACTION_HEADSET_PLUG).putExtra("state", 0))
            assertFalse(manager.audioTransmitter.isReady.value)
        } finally {
            probe.release.countDown()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            manager.release()
        }
    }

    @Test fun concurrentHardwareRefreshesCannotOverlap() = runBlocking {
        val context = HardwareContext()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val manager = IrTransmitterManager(context, scope)
        val probe = RefreshProbe()
        try {
            manager.refreshHardware()
            context.usbProbe = probe
            val first = scope.async { manager.refreshHardware() }
            assertTrue(probe.entered.await(3, TimeUnit.SECONDS))
            val secondStarted = CompletableDeferred<Unit>()
            val second = scope.async {
                secondStarted.complete(Unit)
                manager.refreshHardware()
            }
            withTimeout(3_000) { secondStarted.await() }
            assertFalse("Only one refresh may enumerate hardware at a time", probe.overlap.await(200, TimeUnit.MILLISECONDS))
            probe.release.countDown()
            withTimeout(3_000) { awaitAll(first, second) }
            assertEquals(2, probe.calls.get())
            assertFalse(probe.onMain.get())
        } finally {
            probe.release.countDown()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            manager.release()
        }
    }

    @Test fun registrationFailuresAreLoggedForBothReceivers() = runBlocking {
        val failure = SecurityException("Receiver registration rejected")
        val context = HardwareContext(failure)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val manager = IrTransmitterManager(context, scope)
        try {
            val warnings = ShadowLog.getLogsForTag("IrTransmitterManager").filter { it.type == Log.WARN }
            assertTrue(warnings.any { it.msg == "Failed to register USB receiver" && it.throwable === failure })
            assertTrue(warnings.any { it.msg == "Failed to register audio receiver" && it.throwable === failure })
            assertEquals(2, context.receivers.size)
            manager.refreshHardware()
            manager.mockTransmitter.simulateDelayMs = 0
            manager.setForceMockMode(true)
            assertTrue(manager.transmit(IrSignal.Nec(0, 1)).isSuccess)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            manager.release()
        }
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

    private data class ReceiverRegistration(val receiver: BroadcastReceiver, val filter: IntentFilter, val flags: Int)

    private class HardwareContext(private val registrationFailure: Exception? = null) :
        ContextWrapper(RuntimeEnvironment.getApplication()) {
        val receivers = mutableListOf<ReceiverRegistration>()
        @Volatile var usbProbe: RefreshProbe? = null

        override fun getApplicationContext(): Context = this

        override fun getSystemService(name: String): Any? = when (name) {
            Context.USB_SERVICE -> { usbProbe?.read(); null }
            else -> super.getSystemService(name)
        }

        override fun registerReceiver(
            receiver: BroadcastReceiver?, filter: IntentFilter, broadcastPermission: String?, scheduler: Handler?, flags: Int
        ): Intent? {
            if (receiver != null) receivers.add(ReceiverRegistration(receiver, filter, flags))
            registrationFailure?.let { throw it }
            return super.registerReceiver(receiver, filter, broadcastPermission, scheduler, flags)
        }
    }

    private class RefreshProbe {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val overlap = CountDownLatch(1)
        val onMain = AtomicBoolean()
        val calls = AtomicInteger()
        private val active = AtomicInteger()

        fun read() {
            calls.incrementAndGet()
            if (Looper.myLooper() == Looper.getMainLooper()) onMain.set(true)
            if (active.incrementAndGet() > 1) overlap.countDown()
            entered.countDown()
            try { check(release.await(3, TimeUnit.SECONDS)) }
            finally { active.decrementAndGet() }
        }
    }
}
