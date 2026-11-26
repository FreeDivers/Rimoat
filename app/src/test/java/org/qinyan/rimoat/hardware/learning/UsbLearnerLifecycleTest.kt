package org.qinyan.rimoat.hardware.learning

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Parcelable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.UsbSessionAccess
import org.qinyan.rimoat.i18n.AppStrings
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    application = Application::class,
    shadows = [DetachUsbManagerShadow::class, DetachUsbConnectionShadow::class]
)
class UsbLearnerLifecycleTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val usb get() = app.getSystemService(Context.USB_SERVICE) as UsbManager
    private val usbShadow get() = Shadow.extract<DetachUsbManagerShadow>(usb)

    @Before
    fun setUp() {
        AppStrings.initialize(app)
    }

    private fun endpoint(address: Int, type: Int): UsbEndpoint =
        ReflectionHelpers.callConstructor(
            UsbEndpoint::class.java,
            ClassParameter.from(Int::class.javaPrimitiveType, address),
            ClassParameter.from(Int::class.javaPrimitiveType, type),
            ClassParameter.from(Int::class.javaPrimitiveType, 64),
            ClassParameter.from(Int::class.javaPrimitiveType, 1)
        )

    private fun usbInterface(id: Int = 0): UsbInterface =
        ReflectionHelpers.callConstructor(
            UsbInterface::class.java,
            ClassParameter.from(Int::class.javaPrimitiveType, id),
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(String::class.java, "fixture"),
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(Int::class.javaPrimitiveType, 0),
            ClassParameter.from(Int::class.javaPrimitiveType, 0)
        ).also { intf ->
            val endpoints = listOf(
                endpoint(1, UsbConstants.USB_ENDPOINT_XFER_BULK),
                endpoint(1 or UsbConstants.USB_DIR_IN, UsbConstants.USB_ENDPOINT_XFER_BULK)
            )
            ReflectionHelpers.setField(intf, "mEndpoints", endpoints.toTypedArray<Parcelable>())
        }

    private fun elkSmartDevice(name: String = "usb-elk"): UsbDevice =
        Shadow.newInstanceOf(UsbDevice::class.java).also {
            ReflectionHelpers.setField(it, "mName", name)
            ReflectionHelpers.setField(it, "mVendorId", UsbDeviceFilter.ELKSMART_VID)
            ReflectionHelpers.setField(it, "mProductId", 0x0131)
            ReflectionHelpers.setField(it, "mInterfaces", arrayOf(usbInterface()))
        }

    private fun tiqiaaDevice(name: String = "usb-tiqiaa"): UsbDevice =
        Shadow.newInstanceOf(UsbDevice::class.java).also {
            ReflectionHelpers.setField(it, "mName", name)
            ReflectionHelpers.setField(it, "mVendorId", UsbDeviceFilter.TIQIAA_VID_SILABS)
            ReflectionHelpers.setField(it, "mProductId", UsbDeviceFilter.TIQIAA_PID)
            ReflectionHelpers.setField(it, "mInterfaces", arrayOf(usbInterface()))
        }

    @Test
    fun detachDuringElkSmartBulkTransferClosesConnectionOnlyOnLearningThreadAfterTransferReturns() = runBlocking {
        val device = elkSmartDevice()
        val tracker = BlockingUsbTransferTracker(authorizeReply = ElkSmartProtocol.AUTHORIZE + byteArrayOf(0x70, 1))
        usbShadow.devices[device.deviceName] = device
        usbShadow.tracker = tracker

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = IrLearnerManager(app, scope)
        try {
            manager.scanUsbDevices()
            val resultDeferred = CompletableDeferred<Result<IrSignal.Raw>>()
            val job = launch(Dispatchers.Default) {
                resultDeferred.complete(manager.startLearning(timeoutSeconds = 10))
            }

            assertTrue("Learning should block in bulkTransfer", tracker.inBlockingRead.await(3, TimeUnit.SECONDS))

            // Simulate DETACHED broadcast scanning on a separate IO thread while bulkTransfer is in flight.
            usbShadow.devices.clear()
            val scanDone = CompletableDeferred<Unit>()
            launch(Dispatchers.IO) {
                manager.scanUsbDevices()
                scanDone.complete(Unit)
            }
            withTimeout(2_000) { scanDone.await() }

            // While bulkTransfer is still blocked, the scanning thread must not release or close the connection.
            assertFalse("Detach scan must not releaseInterface during active bulkTransfer", tracker.releasedDuringTransfer.get())
            assertFalse("Detach scan must not close UsbDeviceConnection during active bulkTransfer", tracker.closedDuringTransfer.get())
            assertEquals(0, tracker.releaseCount.get())
            assertEquals(0, tracker.closeCount.get())

            tracker.unblockRead.countDown()
            val result = withTimeout(3_000) { resultDeferred.await() }
            job.join()

            assertTrue("Learning must fail after detach", result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals(1, tracker.releaseCount.get())
            assertEquals(1, tracker.closeCount.get())
            assertEquals(tracker.learningThreadId.get(), tracker.releaseThreadId.get())
            assertEquals(tracker.learningThreadId.get(), tracker.closeThreadId.get())
            assertFalse("USB session lock must be released", UsbSessionAccess.mutex.isLocked)
        } finally {
            tracker.unblockRead.countDown()
            manager.cancel()
            manager.unregisterReceiver()
            scope.cancel()
        }
    }

    @Test
    fun detachDuringTiqiaaBulkTransferClosesConnectionOnlyOnLearningThreadAfterTransferReturns() = runBlocking {
        val device = tiqiaaDevice()
        val tracker = BlockingUsbTransferTracker(authorizeReply = null)
        usbShadow.devices[device.deviceName] = device
        usbShadow.tracker = tracker

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = IrLearnerManager(app, scope)
        try {
            manager.scanUsbDevices()
            val resultDeferred = CompletableDeferred<Result<IrSignal.Raw>>()
            val job = launch(Dispatchers.Default) {
                resultDeferred.complete(manager.startLearning(timeoutSeconds = 10))
            }

            assertTrue("Learning should block in bulkTransfer", tracker.inBlockingRead.await(3, TimeUnit.SECONDS))

            usbShadow.devices.clear()
            val scanDone = CompletableDeferred<Unit>()
            launch(Dispatchers.IO) {
                manager.scanUsbDevices()
                scanDone.complete(Unit)
            }
            withTimeout(2_000) { scanDone.await() }

            assertFalse("Detach scan must not releaseInterface during active bulkTransfer", tracker.releasedDuringTransfer.get())
            assertFalse("Detach scan must not close UsbDeviceConnection during active bulkTransfer", tracker.closedDuringTransfer.get())
            assertEquals(0, tracker.releaseCount.get())
            assertEquals(0, tracker.closeCount.get())

            tracker.unblockRead.countDown()
            val result = withTimeout(3_000) { resultDeferred.await() }
            job.join()

            assertTrue("Learning must fail after detach", result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals(1, tracker.releaseCount.get())
            assertEquals(1, tracker.closeCount.get())
            assertEquals(tracker.learningThreadId.get(), tracker.releaseThreadId.get())
            assertEquals(tracker.learningThreadId.get(), tracker.closeThreadId.get())
            assertFalse("USB session lock must be released", UsbSessionAccess.mutex.isLocked)
        } finally {
            tracker.unblockRead.countDown()
            manager.cancel()
            manager.unregisterReceiver()
            scope.cancel()
        }
    }

    @Test
    fun detachMarksUsbSessionCancellingSoTransmitCanWaitForLearningCleanup() = runBlocking {
        val device = elkSmartDevice()
        val tracker = BlockingUsbTransferTracker(authorizeReply = ElkSmartProtocol.AUTHORIZE + byteArrayOf(0x70, 1))
        usbShadow.devices[device.deviceName] = device
        usbShadow.tracker = tracker

        val learner = LazyUsbLearner(device.deviceName, LearnerType.USB_ELKSMART) {
            ElkSmartUsbLearner.open(usb, device)
        }
        val learningJob = launch(Dispatchers.Default) {
            learner.startLearning(timeoutSeconds = 10)
        }
        try {
            assertTrue(tracker.inBlockingRead.await(3, TimeUnit.SECONDS))

            learner.close()
            assertEquals(0, tracker.closeCount.get())

            val awaitStarted = CompletableDeferred<Unit>()
            val awaitFinished = CompletableDeferred<Unit>()
            val waiter = launch(Dispatchers.Default) {
                awaitStarted.complete(Unit)
                UsbSessionAccess.awaitCancelledLearning()
                awaitFinished.complete(Unit)
            }
            withTimeout(1_000) { awaitStarted.await() }
            // Waiter should still be awaiting learning cleanup until the in-flight transfer completes.
            Thread.sleep(50)
            assertFalse("awaitCancelledLearning must wait for learning thread to release the connection", awaitFinished.isCompleted)

            tracker.unblockRead.countDown()
            withTimeout(2_000) { awaitFinished.await() }
            waiter.join()
            learningJob.join()
            assertEquals(1, tracker.closeCount.get())
            assertFalse(UsbSessionAccess.mutex.isLocked)
        } finally {
            tracker.unblockRead.countDown()
            learningJob.cancel()
        }
    }

    @Test
    fun resolveLearnerDoesNotThrowNpeWhenUsbLearnerIsClearedBetweenCheckAndReturn() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = IrLearnerManager(context = null, scope = scope)
        try {
            @Suppress("UNCHECKED_CAST")
            val usbLearnerField = IrLearnerManager::class.java.getDeclaredField("_usbLearner").apply {
                isAccessible = true
            }
            val usbFlow = usbLearnerField.get(manager) as MutableStateFlow<IrLearner?>
            val racingLearner = object : IrLearner {
                override val type = LearnerType.USB_TIQIAA
                override val isAvailable: StateFlow<Boolean>
                    get() {
                        // Simulate scanUsbDevices() clearing _usbLearner concurrently right after availability check.
                        usbFlow.value = null
                        return MutableStateFlow(true)
                    }
                override val state: StateFlow<LearningState> = MutableStateFlow(LearningState.Idle)
                override suspend fun startLearning(timeoutSeconds: Int) = Result.failure<IrSignal.Raw>(IOException("unused"))
                override fun cancel() {}
            }
            usbFlow.value = racingLearner

            val resolveMethod = IrLearnerManager::class.java.getDeclaredMethod("resolveLearner").apply {
                isAccessible = true
            }
            val resolved = resolveMethod.invoke(manager) as IrLearner
            assertSame(racingLearner, resolved)
        } finally {
            manager.cancel()
            manager.unregisterReceiver()
            scope.cancel()
        }
    }
}

class BlockingUsbTransferTracker(
    val authorizeReply: ByteArray?
) {
    val inBlockingRead = CountDownLatch(1)
    val unblockRead = CountDownLatch(1)
    val learningThreadId = AtomicReference<Long>(null)
    val releaseThreadId = AtomicReference<Long>(null)
    val closeThreadId = AtomicReference<Long>(null)
    val releaseCount = AtomicInteger(0)
    val closeCount = AtomicInteger(0)
    val releasedDuringTransfer = AtomicBoolean(false)
    val closedDuringTransfer = AtomicBoolean(false)
    val activeTransfers = AtomicInteger(0)
    val learnStarted = AtomicBoolean(false)
    val authorizePending = AtomicBoolean(false)
}

@Implements(UsbManager::class)
class DetachUsbManagerShadow {
    val devices = hashMapOf<String, UsbDevice>()
    var tracker: BlockingUsbTransferTracker? = null

    @Implementation
    fun getDeviceList(): HashMap<String, UsbDevice> = HashMap(devices)

    @Implementation
    fun hasPermission(device: UsbDevice): Boolean = devices.containsKey(device.deviceName)

    @Implementation
    fun openDevice(device: UsbDevice): UsbDeviceConnection? {
        val currentTracker = tracker ?: return null
        return ReflectionHelpers.callConstructor(
            UsbDeviceConnection::class.java,
            ClassParameter.from(UsbDevice::class.java, device)
        ).also {
            Shadow.extract<DetachUsbConnectionShadow>(it).tracker = currentTracker
        }
    }
}

@Implements(UsbDeviceConnection::class)
class DetachUsbConnectionShadow {
    lateinit var tracker: BlockingUsbTransferTracker

    @Implementation
    fun claimInterface(intf: UsbInterface, force: Boolean): Boolean = true

    @Implementation
    fun bulkTransfer(endpoint: UsbEndpoint, buffer: ByteArray, length: Int, timeout: Int): Int {
        tracker.activeTransfers.incrementAndGet()
        try {
            if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                val cmd = buffer.copyOf(length)
                if (tracker.authorizeReply != null) {
                    if (cmd.contentEquals(ElkSmartProtocol.AUTHORIZE)) {
                        tracker.authorizePending.set(true)
                    } else if (cmd.contentEquals(ElkSmartProtocol.START_LEARN)) {
                        tracker.learnStarted.set(true)
                    }
                } else {
                    // Tiqiaa sends learn-mode packet before entering the read loop.
                    tracker.learnStarted.set(true)
                }
                return length
            }

            val reply = tracker.authorizeReply
            if (reply != null && tracker.authorizePending.compareAndSet(true, false)) {
                reply.copyInto(buffer)
                return reply.size
            }

            if (tracker.learnStarted.get() && tracker.learningThreadId.compareAndSet(null, Thread.currentThread().id)) {
                tracker.inBlockingRead.countDown()
                tracker.unblockRead.await(5, TimeUnit.SECONDS)
                return -1
            }

            return -1
        } finally {
            tracker.activeTransfers.decrementAndGet()
        }
    }

    @Implementation
    fun releaseInterface(intf: UsbInterface): Boolean {
        if (tracker.activeTransfers.get() > 0) {
            tracker.releasedDuringTransfer.set(true)
        }
        tracker.releaseThreadId.compareAndSet(null, Thread.currentThread().id)
        tracker.releaseCount.incrementAndGet()
        return true
    }

    @Implementation
    fun close() {
        if (tracker.activeTransfers.get() > 0) {
            tracker.closedDuringTransfer.set(true)
        }
        tracker.closeThreadId.compareAndSet(null, Thread.currentThread().id)
        tracker.closeCount.incrementAndGet()
    }
}
