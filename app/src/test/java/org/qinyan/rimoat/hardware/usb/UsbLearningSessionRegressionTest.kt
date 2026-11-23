package org.qinyan.rimoat.hardware.usb

import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.usb.*
import android.os.Looper
import android.os.Parcelable
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.hardware.UsbSessionAccess
import org.qinyan.rimoat.hardware.learning.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import org.robolectric.shadow.api.Shadow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [PollingConnectionShadow::class, UsbDiscoveryShadow::class])
class UsbLearningSessionRegressionTest {
    private fun endpoint(address: Int, type: Int = UsbConstants.USB_ENDPOINT_XFER_BULK): UsbEndpoint =
        ReflectionHelpers.callConstructor(UsbEndpoint::class.java,
            ClassParameter.from(Int::class.javaPrimitiveType, address),
            ClassParameter.from(Int::class.javaPrimitiveType, type),
            ClassParameter.from(Int::class.javaPrimitiveType, 64),
            ClassParameter.from(Int::class.javaPrimitiveType, 0))

    private fun intf(vararg endpoints: UsbEndpoint): UsbInterface =
        ReflectionHelpers.callConstructor(UsbInterface::class.java,
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(String::class.java, "fixture"),
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(Int::class.javaPrimitiveType, 0)).also {
            ReflectionHelpers.setField(it, "mEndpoints", arrayOf<Parcelable>(*endpoints))
        }

    @Test fun pairingFindsMatchingNumbersInsteadOfFirstOppositeDirections() {
        val pairs = usbEndpointPairs(intf(endpoint(1), endpoint(0x82), endpoint(2), endpoint(0x81)))
        assertEquals(listOf(1, 2), pairs.map { it.outEp.endpointNumber })
        assertTrue(pairs.all { it.outEp.endpointNumber == it.inEp.endpointNumber })
    }

    @Test fun onlyUniqueAsymmetricPairCanFallBackAndInterruptEndpointsStaySeparate() {
        assertEquals(1, usbEndpointPairs(intf(endpoint(1), endpoint(0x82))).size)
        assertTrue(usbEndpointPairs(intf(endpoint(1), endpoint(2), endpoint(0x83))).isEmpty())
        assertTrue(usbEndpointPairs(intf(endpoint(1), endpoint(0x81, UsbConstants.USB_ENDPOINT_XFER_INT))).isEmpty())
    }

    private suspend fun cancelPolling(type: Class<out IrLearner>, elk: Boolean) {
        val out = endpoint(1)
        val input = endpoint(0x81)
        val device = org.robolectric.shadow.api.Shadow.newInstanceOf(UsbDevice::class.java)
        val connection = ReflectionHelpers.callConstructor(UsbDeviceConnection::class.java,
            ClassParameter.from(UsbDevice::class.java, device))
        val io = PollingConnectionShadow.reset(elk)
        val learner = type.declaredConstructors.single { it.parameterCount == 5 }.apply { isAccessible = true }
            .newInstance(device, connection, intf(out, input), out, input) as IrLearner
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job = scope.launch { learner.startLearning(30) }
        try {
            assertTrue("Learning must reach a stream of positive reads", io.polling.await(3, TimeUnit.SECONDS))
            assertTrue("Input must be drained before the first command", io.drainedBeforeWrite)
            job.cancel()
            withTimeout(1_500) { job.join() }
            assertTrue(job.isCancelled)
            assertTrue("Hardware cleanup must run on cancellation", io.cleanup.get() > 0)
        } finally { job.cancelAndJoin(); scope.cancel(); (learner as AutoCloseable).close() }
    }

    @Test fun tiqiaaDrainsStaleInputAndCancelsUnderContinuousPositiveReads() = runBlocking {
        cancelPolling(TiqiaaUsbLearner::class.java, false)
    }

    @Test fun elkSmartCancelsUnderContinuousPositiveReads() = runBlocking {
        cancelPolling(ElkSmartUsbLearner::class.java, true)
    }

    @Test fun sendWaitsOnlyForCancelledLearningAndResumesAfterRelease() = runBlocking {
        UsbSessionAccess.mutex.lock()
        val lease = UsbSessionAccess.beginLearning()
        try {
            withTimeout(200) { UsbSessionAccess.awaitCancelledLearning() }
            UsbSessionAccess.cancelLearning(lease)
            val waiter = async { UsbSessionAccess.awaitCancelledLearning() }
            yield()
            assertFalse(waiter.isCompleted)
            UsbSessionAccess.mutex.unlock()
            UsbSessionAccess.finishLearning(lease)
            withTimeout(500) { waiter.await() }
            assertTrue(UsbSessionAccess.mutex.tryLock())
            UsbSessionAccess.mutex.unlock()
        } finally {
            if (UsbSessionAccess.mutex.isLocked) UsbSessionAccess.mutex.unlock()
            UsbSessionAccess.finishLearning(lease)
        }
    }

    @Test fun concurrentScansPreserveLearnerAndDisconnectItOnce() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val usb = app.getSystemService(Context.USB_SERVICE) as UsbManager
        val discovery = Shadow.extract<UsbDiscoveryShadow>(usb)
        val device = Shadow.newInstanceOf(UsbDevice::class.java).also {
            ReflectionHelpers.setField(it, "mName", "fixture-usb")
            ReflectionHelpers.setField(it, "mVendorId", UsbDeviceFilter.TIQIAA_VID_SILABS)
            ReflectionHelpers.setField(it, "mProductId", UsbDeviceFilter.TIQIAA_PID)
        }
        discovery.devices[device.deviceName] = device
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val manager = IrLearnerManager(app, scope)
        val probe = UsbDiscoveryShadow.Probe()
        try {
            withContext(Dispatchers.IO) { manager.scanUsbDevices() }
            val learners = ReflectionHelpers.getField<MutableStateFlow<IrLearner?>>(manager, "_usbLearner")
            val original = learners.value!!
            assertEquals(LearnerType.USB_TIQIAA, original.type)
            discovery.probe = probe
            val first = scope.async { manager.scanUsbDevices() }
            assertTrue(probe.entered.await(3, TimeUnit.SECONDS))
            val secondStarted = CompletableDeferred<Unit>()
            val second = scope.async {
                secondStarted.complete(Unit)
                manager.scanUsbDevices()
            }
            withTimeout(3_000) { secondStarted.await() }
            assertFalse("USB scans must not enumerate or replace learners concurrently", probe.overlap.await(200, TimeUnit.MILLISECONDS))
            probe.release.countDown()
            withTimeout(3_000) { awaitAll(first, second) }
            assertSame(original, learners.value)
            assertTrue(original.isAvailable.value)
            assertEquals(2, probe.calls.get())

            discovery.devices.clear()
            withContext(Dispatchers.IO) { manager.scanUsbDevices() }
            assertNull(learners.value)
            assertFalse(original.isAvailable.value)
            assertTrue(original.state.value is LearningState.Error)
            withContext(Dispatchers.IO) { manager.scanUsbDevices() }
            assertNull(learners.value)
        } finally {
            probe.release.countDown()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            manager.unregisterReceiver()
            UsbDeviceReceiver.onDeviceEvent = null
        }
    }

    @Test fun dynamicAndManifestUsbBroadcastsScanOffMainThread() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val usb = app.getSystemService(Context.USB_SERVICE) as UsbManager
        val discovery = Shadow.extract<UsbDiscoveryShadow>(usb)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val manager = IrLearnerManager(app, scope)
        try {
            withContext(Dispatchers.IO) { manager.scanUsbDevices() }
            val receiver = ReflectionHelpers.getField<android.content.BroadcastReceiver>(manager, "usbReceiver")
            val dynamicProbe = UsbDiscoveryShadow.Probe().apply { release.countDown() }
            discovery.probe = dynamicProbe
            receiver.onReceive(app, Intent(IrLearnerManager.ACTION_USB_PERMISSION))
            assertTrue(dynamicProbe.entered.await(3, TimeUnit.SECONDS))
            assertFalse("Dynamic USB receiver must scan on IO", dynamicProbe.onMain.get())
            withContext(Dispatchers.IO) { manager.scanUsbDevices() }

            val manifestProbe = UsbDiscoveryShadow.Probe().apply { release.countDown() }
            discovery.probe = manifestProbe
            UsbDeviceReceiver().onReceive(app, Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED))
            assertTrue(manifestProbe.entered.await(3, TimeUnit.SECONDS))
            assertFalse("Manifest USB receiver must scan on IO", manifestProbe.onMain.get())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            manager.unregisterReceiver()
            UsbDeviceReceiver.onDeviceEvent = null
        }
    }
}

@Implements(UsbManager::class)
class UsbDiscoveryShadow {
    val devices = hashMapOf<String, UsbDevice>()
    @Volatile var probe: Probe? = null

    @Implementation fun getDeviceList(): HashMap<String, UsbDevice> {
        probe?.read()
        return HashMap(devices)
    }

    @Implementation fun hasPermission(device: UsbDevice): Boolean = devices.containsKey(device.deviceName)

    class Probe {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val overlap = CountDownLatch(1)
        val calls = AtomicInteger()
        val onMain = AtomicBoolean()
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

@Implements(UsbDeviceConnection::class)
class PollingConnectionShadow {
    class Io(val elk: Boolean) {
        val polling = CountDownLatch(1)
        val cleanup = AtomicInteger()
        var drainReads = 0
        var writes = 0
        var reads = 0
        var command = 0
        var drainedBeforeWrite = false
    }
    companion object {
        lateinit var io: Io
        fun reset(elk: Boolean): Io = Io(elk).also { io = it }
    }

    @Implementation
    fun bulkTransfer(endpoint: UsbEndpoint, buffer: ByteArray, length: Int, timeout: Int): Int {
        if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
            if (io.writes++ == 0) io.drainedBeforeWrite = io.drainReads >= 2
            io.command = buffer[0].toInt() and 255
            if (io.elk && io.command == 0xFD || !io.elk && io.writes > 1) io.cleanup.incrementAndGet()
            return length
        }
        if (io.writes == 0) return if (io.drainReads++ == 0) 1 else -1
        if (io.elk && io.command == 0xFC) {
            byteArrayOf(-4, -4, -4, -4, 0x70, 1).copyInto(buffer)
            return 6
        }
        buffer[0] = 0x33
        if (++io.reads >= 50) io.polling.countDown()
        return 1
    }

    @Implementation fun releaseInterface(intf: UsbInterface) = true
    @Implementation fun close() = Unit
}
