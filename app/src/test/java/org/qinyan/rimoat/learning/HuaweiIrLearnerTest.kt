package org.qinyan.rimoat.learning

import org.qinyan.rimoat.testing.AppStringsResourceRule
import org.qinyan.rimoat.hardware.learning.HuaweiIrLearner
import org.qinyan.rimoat.hardware.learning.LearnerType
import org.qinyan.rimoat.hardware.learning.LearningState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class HuaweiIrLearnerTest {
    @get:org.junit.Rule val strings = AppStringsResourceRule()


    class SimulatedHuaweiOemManager {
        var failRead = false
        @Volatile var startLearningResult: Boolean = true
        @Volatile var stopLearningCalled: Boolean = false
        var statusSequence: MutableList<Boolean> = Collections.synchronizedList(mutableListOf(false, true))
        @Volatile var carrierFrequency: Int = 38000
        @Volatile var codePattern: IntArray? = intArrayOf(9000, 4500, 560, 560, 560, 1690)
        val methodCallOrder: MutableList<String> = Collections.synchronizedList(mutableListOf())

        @Synchronized
        fun startLearning(): Boolean {
            methodCallOrder.add("startLearning")
            return startLearningResult
        }

        @Synchronized
        fun stopLearning() {
            methodCallOrder.add("stopLearning")
            stopLearningCalled = true
        }

        @Synchronized
        fun getLearningStatus(): Boolean {
            methodCallOrder.add("getLearningStatus")
            return if (statusSequence.isNotEmpty()) statusSequence.removeAt(0) else true
        }

        @Synchronized
        fun readIRFrequency(): Int {
            methodCallOrder.add("readIRFrequency")
            return carrierFrequency
        }

        @Synchronized
        fun readIRCode(): IntArray? {
            methodCallOrder.add("readIRCode")
            check(!failRead) { "OEM read failed" }
            return codePattern
        }
    }

    class BlockingStopManager {
        val started = java.util.concurrent.CountDownLatch(1)
        val stopEntered = java.util.concurrent.CountDownLatch(1)
        val releaseStop = java.util.concurrent.CountDownLatch(1)
        val stops = java.util.concurrent.atomic.AtomicInteger()
        @Volatile var stopThread: Thread? = null
        fun startLearning(): Boolean { started.countDown(); return true }
        fun stopLearning() {
            stopThread = Thread.currentThread()
            stops.incrementAndGet()
            stopEntered.countDown()
            releaseStop.await(2, java.util.concurrent.TimeUnit.SECONDS)
        }
        fun getLearningStatus() = false
        fun readIRFrequency() = 38000
        fun readIRCode() = intArrayOf(9000, 4500, 560)
    }

    @Test fun cancelNeverRunsBlockingReflectionOnCallerAndStopsExactlyOnce() = runBlocking {
        val oem = BlockingStopManager()
        val learner = HuaweiIrLearner(oem, BlockingStopManager::class.java)
        val caller = Thread.currentThread()
        val job = launch(Dispatchers.Default) { learner.startLearning(10) }
        try {
            assertTrue(oem.started.await(2, java.util.concurrent.TimeUnit.SECONDS))
            learner.cancel()
            learner.cancel()
            job.cancel()
            assertTrue(oem.stopEntered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(oem.stopThread !== caller)
            assertEquals(1, oem.stops.get())
        } finally { oem.releaseStop.countDown(); job.join() }
        assertEquals(1, oem.stops.get())
    }

    @Test
    fun testLearnerTypeAndAvailability() {
        val oem = SimulatedHuaweiOemManager()
        val learner = HuaweiIrLearner(oem, SimulatedHuaweiOemManager::class.java)
        assertEquals(LearnerType.HUAWEI_OEM, learner.type)
        assertTrue(learner.isAvailable.value)
        assertEquals(LearningState.Idle, learner.state.value)
    }

    @Test
    fun testSuccessfulLearningAndStrictMethodCallOrder() = runTest {
        val oem = SimulatedHuaweiOemManager()
        oem.statusSequence = mutableListOf(false, true)
        oem.carrierFrequency = 38000
        oem.codePattern = intArrayOf(9000, 4500, 560, 560, 560, 1690)

        val learner = HuaweiIrLearner(oem, SimulatedHuaweiOemManager::class.java)
        val result = learner.startLearning(timeoutSeconds = 5)

        assertTrue(result.isSuccess)
        val signal = result.getOrThrow()
        assertEquals(38000, signal.frequencyHz)
        assertArrayEquals(intArrayOf(9000, 4500, 560, 560, 560, 1690), signal.patternMicros)

        // Verify state
        assertTrue(learner.state.value is LearningState.Captured)

        // Verify STRICT EMUI ROM requirement: readIRFrequency must be called BEFORE readIRCode
        val freqIndex = oem.methodCallOrder.indexOf("readIRFrequency")
        val codeIndex = oem.methodCallOrder.indexOf("readIRCode")
        assertTrue("readIRFrequency must be called", freqIndex >= 0)
        assertTrue("readIRCode must be called", codeIndex >= 0)
        assertTrue("readIRFrequency must precede readIRCode", freqIndex < codeIndex)

        // Verify stopLearning was called in finally
        assertTrue(oem.stopLearningCalled)
    }

    @Test
    fun testHardwareStartFailureAbortsImmediately() = runTest {
        val oem = SimulatedHuaweiOemManager()
        oem.startLearningResult = false // hardware fails to enter mode

        val learner = HuaweiIrLearner(oem, SimulatedHuaweiOemManager::class.java)
        val result = learner.startLearning(timeoutSeconds = 5)

        assertTrue(result.isFailure)
        assertTrue(learner.state.value is LearningState.Error)
        assertEquals(0, oem.methodCallOrder.count { it == "getLearningStatus" })
    }

    @Test
    fun testFrequencyClamping() = runTest {
        val oem = SimulatedHuaweiOemManager()
        oem.statusSequence = mutableListOf(true)
        oem.carrierFrequency = 80000 // Out of IR bounds (above 60kHz)

        val learner = HuaweiIrLearner(oem, SimulatedHuaweiOemManager::class.java)
        val result = learner.startLearning(timeoutSeconds = 5)

        assertTrue(result.isSuccess)
        val signal = result.getOrThrow()
        // Clamped to 60000 Hz
        assertEquals(60000, signal.frequencyHz)
    }

    @Test
    fun testCancelStopsLearning() = runBlocking {
        val oem = SimulatedHuaweiOemManager()
        oem.statusSequence.clear()
        oem.statusSequence.addAll(listOf(false, false, false, false))

        val learner = HuaweiIrLearner(oem, SimulatedHuaweiOemManager::class.java)
        val job = launch(Dispatchers.Default) {
            learner.startLearning(timeoutSeconds = 10)
        }

        var waited = 0
        while (learner.state.value !is LearningState.Listening && waited < 100) {
            delay(5)
            waited++
        }
        assertTrue("Learner should enter Listening state", learner.state.value is LearningState.Listening)

        learner.cancel()
        job.join()

        assertEquals(LearningState.Idle, learner.state.value)
        assertTrue(oem.stopLearningCalled)
    }
    @Test fun oemReadExceptionBecomesErrorAndAlwaysStopsHardware() = runTest {
        val oem = SimulatedHuaweiOemManager().apply { failRead = true; statusSequence = mutableListOf(true) }
        val learner = HuaweiIrLearner(oem, SimulatedHuaweiOemManager::class.java)
        val result = learner.startLearning(5)
        assertTrue(result.isFailure)
        assertTrue(learner.state.value is LearningState.Error)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("OEM read failed"))
        assertTrue(oem.stopLearningCalled)
    }

}
