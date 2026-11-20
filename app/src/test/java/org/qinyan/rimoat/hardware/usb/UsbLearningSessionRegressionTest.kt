package org.qinyan.rimoat.hardware.usb

import android.app.Application
import android.hardware.usb.*
import android.os.Parcelable
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.hardware.UsbSessionAccess
import org.qinyan.rimoat.hardware.learning.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [PollingConnectionShadow::class])
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
