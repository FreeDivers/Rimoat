package org.qinyan.rimoat.learning

import org.qinyan.rimoat.testing.AppStringsResourceRule
import android.content.Context
import org.qinyan.rimoat.hardware.HardwareEnvironment
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.hardware.learning.LearnerType
import org.qinyan.rimoat.hardware.learning.LearningState
import org.qinyan.rimoat.hardware.learning.MockIrLearner
import org.qinyan.rimoat.hardware.learning.NoHardwareLearner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class IrLearnerManagerTest {
    @get:org.junit.Rule val strings = AppStringsResourceRule()


    @Test
    fun defaultScopeKeepsLearningStateUpdatesAfterChildFailure() = runBlocking<Unit> {
        val manager = IrLearnerManager()
        val scope = IrLearnerManager::class.java.getDeclaredField("scope").run {
            isAccessible = true
            get(manager) as CoroutineScope
        }
        try {
            withTimeout(5_000) {
                manager.mockLearner.setSimulatedDelayMs(0)
                assertTrue(manager.startLearning(timeoutSeconds = 5).isSuccess)
                manager.state.first { it is LearningState.Captured }

                val failure = IllegalStateException("Background task failed")
                val reportedFailure = CompletableDeferred<Throwable>()
                scope.launch(CoroutineExceptionHandler { _, error -> reportedFailure.complete(error) }) {
                    throw failure
                }.join()

                assertSame(failure, reportedFailure.await())
                assertTrue(scope.isActive)

                manager.cancel()
                manager.state.first { it == LearningState.Idle }

                val result = manager.startLearning(timeoutSeconds = 5)
                assertTrue(result.isSuccess)
                val captured = manager.state.first { it is LearningState.Captured } as LearningState.Captured
                assertEquals(result.getOrThrow(), captured.signal)

                manager.cancel()
                manager.state.first { it == LearningState.Idle }
            }
        } finally {
            manager.cancel()
            manager.unregisterReceiver()
            scope.cancel()
        }
    }

    @Test
    fun testDefaultActiveLearnerIsMock() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val manager = IrLearnerManager(context = null, scope = testScope)
        assertEquals(LearnerType.MOCK, manager.learnerType.value)
        assertTrue(manager.isAvailable.value)
        assertEquals(LearningState.Idle, manager.state.value)

        manager.setForceMockMode(true)
        assertTrue(manager.forceMockMode.value)
        assertEquals(LearnerType.MOCK, manager.activeLearner.value.type)

        manager.mockLearner.setSimulationMode(MockIrLearner.SimulationMode.SUCCESS_NEC)
        manager.mockLearner.setSimulatedDelayMs(50)

        val result = manager.startLearning(timeoutSeconds = 5)
        assertTrue(result.isSuccess)
        assertEquals(38000, result.getOrThrow().frequencyHz)
        assertTrue(manager.state.value is LearningState.Captured)

        manager.cancel()
        assertEquals(LearningState.Idle, manager.state.value)
    }

    @Test
    fun testNoHardwareLearnerDirectBehavior() = runTest {
        val noHw = NoHardwareLearner()
        assertEquals(LearnerType.NONE, noHw.type)
        assertFalse(noHw.isAvailable.value)
        assertTrue(noHw.state.value is LearningState.Error)
        assertTrue((noHw.state.value as LearningState.Error).message.contains("未检测到红外接收硬件"))

        val result = noHw.startLearning()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(result.exceptionOrNull()?.message?.contains("未检测到红外接收硬件") == true)
    }

    @Test
    fun testHardwareEnvironmentDetectionSafe() {
        val isEmu = HardwareEnvironment.isEmulator()
        // Must complete without throwing exceptions
        println("[Empirical] HardwareEnvironment.isEmulator() in test: $isEmu")
    }
}
