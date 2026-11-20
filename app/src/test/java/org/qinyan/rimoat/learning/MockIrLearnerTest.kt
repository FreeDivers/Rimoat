package org.qinyan.rimoat.learning

import org.qinyan.rimoat.hardware.learning.LearningState
import org.qinyan.rimoat.hardware.learning.MockIrLearner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockIrLearnerTest {

    @Test
    fun testInitialStateAndType() {
        val learner = MockIrLearner()
        assertEquals(org.qinyan.rimoat.hardware.learning.LearnerType.MOCK, learner.type)
        assertTrue(learner.isAvailable.value)
        assertEquals(LearningState.Idle, learner.state.value)
    }

    @Test
    fun testSimulateSuccessNec() = runTest {
        val learner = MockIrLearner()
        learner.setSimulationMode(MockIrLearner.SimulationMode.SUCCESS_NEC)
        learner.setSimulatedDelayMs(50)

        val result = learner.startLearning(timeoutSeconds = 5)
        assertTrue(result.isSuccess)
        val signal = result.getOrThrow()
        assertEquals(38000, signal.frequencyHz)
        assertTrue(signal.patternMicros.size >= 66) // NEC 67/68 pulses

        val state = learner.state.value
        assertTrue(state is LearningState.Captured)
        val captured = state as LearningState.Captured
        assertEquals(signal, captured.signal)
    }

    @Test
    fun testSimulateSuccessSony() = runTest {
        val learner = MockIrLearner()
        learner.setSimulationMode(MockIrLearner.SimulationMode.SUCCESS_SONY)
        learner.setSimulatedDelayMs(50)

        val result = learner.startLearning(timeoutSeconds = 5)
        assertTrue(result.isSuccess)
        val signal = result.getOrThrow()
        assertEquals(40000, signal.frequencyHz)
        assertEquals(26, signal.patternMicros.size) // 25 pulses + 1 parity padding space
        assertTrue(learner.state.value is LearningState.Captured)
    }

    @Test
    fun testSimulateError() = runTest {
        val learner = MockIrLearner()
        learner.setSimulationMode(MockIrLearner.SimulationMode.ERROR)
        learner.setSimulatedDelayMs(50)

        val result = learner.startLearning(timeoutSeconds = 5)
        assertTrue(result.isFailure)
        val state = learner.state.value
        assertTrue(state is LearningState.Error)
    }

    @Test
    fun testCancelResetsToIdle() = runBlocking {
        val learner = MockIrLearner()
        learner.setSimulationMode(MockIrLearner.SimulationMode.SUCCESS_NEC)
        learner.setSimulatedDelayMs(5000)

        val job = launch(Dispatchers.Default) {
            learner.startLearning(timeoutSeconds = 10)
        }

        val deadline1 = System.currentTimeMillis() + 3000
        while (learner.state.value !is LearningState.Listening && System.currentTimeMillis() < deadline1) {
            Thread.sleep(5)
        }
        assertTrue("Learner should enter Listening state", learner.state.value is LearningState.Listening)

        // Cancel
        learner.cancel()
        job.join()

        assertEquals(LearningState.Idle, learner.state.value)
    }

    @Test
    fun testManualTriggerSignal() = runBlocking {
        val learner = MockIrLearner()
        learner.setSimulatedDelayMs(5000) // long delay

        val customSignal = org.qinyan.rimoat.hardware.IrSignal.Raw(
            frequencyHz = 36000,
            patternMicros = intArrayOf(2000, 1000, 500, 500, 500, 500)
        )

        val job = launch(Dispatchers.Default) {
            learner.startLearning(timeoutSeconds = 10)
        }

        val deadline2 = System.currentTimeMillis() + 3000
        while (learner.state.value !is LearningState.Listening && System.currentTimeMillis() < deadline2) {
            Thread.sleep(5)
        }
        assertTrue("Learner should enter Listening state", learner.state.value is LearningState.Listening)

        // Trigger manual signal
        learner.triggerManualSignal(customSignal)
        job.join()

        val state = learner.state.value
        assertTrue(state is LearningState.Captured)
        val captured = state as LearningState.Captured
        assertEquals(customSignal, captured.signal)
    }
}
