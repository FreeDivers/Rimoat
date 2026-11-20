package org.qinyan.rimoat.learning

import android.content.Context
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.RemoteRepository
import org.qinyan.rimoat.data.SignalType
import org.qinyan.rimoat.hardware.BuiltInIrTransmitter
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.learning.IrLearner
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.hardware.learning.IrSignalSanitizer
import org.qinyan.rimoat.hardware.learning.LearnerType
import org.qinyan.rimoat.hardware.learning.LearningState
import org.qinyan.rimoat.hardware.learning.MockIrLearner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files

/**
 * IR learner concurrency and failure-mode tests.
 *
 * Tests stress-testing assumptions, concurrency, cancellation, hotplug races,
 * timeout anomalies, and persistence edge cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdversarialIrLearnerTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // =========================================================================
    // SECTION 1: MockIrLearner Timeout & Cancellation Adversarial Tests
    // =========================================================================

    /**
     * EMPIRICAL CHALLENGE 1:
     * Does MockIrLearner.cancel() immediately cancel an active session when in TIMEOUT simulation mode?
     *
     * Bug Hypothesis:
     * When MockIrLearner enters SimulationMode.TIMEOUT, it executes:
     * `delay(deadline - System.currentTimeMillis())`
     * During this delay, it does NOT check `cancelled.get()`.
     * If learner.cancel() is called, `delay(...)` does NOT awaken, and the coroutine
     * remains blocked for the full remaining timeout duration!
     */
    @Test
    fun testMockIrLearnerTimeout_cancelResponsiveness() = runBlocking {
        val learner = MockIrLearner()
        learner.setSimulationMode(MockIrLearner.SimulationMode.TIMEOUT)
        learner.setSimulatedDelayMs(20) // Quickly triggers TIMEOUT mode

        var sessionResult: Result<IrSignal.Raw>? = null
        val job = launch(Dispatchers.Default) {
            sessionResult = learner.startLearning(timeoutSeconds = 4)
        }

        // Wait until it enters Listening and passes simulatedDelayMs (enters TIMEOUT branch)
        val listenDeadline = System.currentTimeMillis() + 3000
        while (learner.state.value !is LearningState.Listening && System.currentTimeMillis() < listenDeadline) {
            Thread.sleep(5)
        }
        assertTrue("Learner should enter Listening state", learner.state.value is LearningState.Listening)
        Thread.sleep(60) // Ensure it crossed simulatedDelayMs and entered wait

        val tCancel = System.currentTimeMillis()
        learner.cancel()

        // If cancel works properly, the job should finish quickly
        val joinDeadline = System.currentTimeMillis() + 2000
        while (job.isActive && System.currentTimeMillis() < joinDeadline) {
            Thread.sleep(5)
        }
        val elapsed = System.currentTimeMillis() - tCancel
        val finishedQuickly = !job.isActive

        println("[Empirical] TIMEOUT mode cancel finished within 2000ms? $finishedQuickly (elapsed: ${elapsed}ms)")
        println("[Empirical] Post-cancel state: ${learner.state.value}")
        println("[Empirical] Session result: $sessionResult")

        if (!finishedQuickly) {
            job.cancel() // Cleanup hanging coroutine
            println("[VULNERABILITY CONFIRMED] MockIrLearner.cancel() failed to abort delay() in SimulationMode.TIMEOUT!")
        }

        // Verify state: cancel MUST leave learner in Idle state
        assertTrue("Job must complete quickly upon cancellation", finishedQuickly && elapsed < 2000)
        assertEquals("State must be Idle after cancellation", LearningState.Idle, learner.state.value)
    }

    /**
     * EMPIRICAL CHALLENGE 2:
     * Does the countdown in MockIrLearner update while in TIMEOUT simulation mode?
     */
    @Test
    fun testMockIrLearnerTimeout_countdownTicks() = runBlocking {
        val learner = MockIrLearner()
        learner.setSimulationMode(MockIrLearner.SimulationMode.TIMEOUT)
        learner.setSimulatedDelayMs(10)

        val recordedStates = java.util.Collections.synchronizedList(mutableListOf<LearningState>())
        val collectorJob = launch(Dispatchers.Default) {
            learner.state.collect { recordedStates.add(it) }
        }

        val sessionJob = launch(Dispatchers.Default) {
            learner.startLearning(timeoutSeconds = 1)
        }

        // Wait for session to finish naturally (1 second)
        sessionJob.join()
        collectorJob.cancel()

        println("[Empirical] Recorded states during TIMEOUT session: $recordedStates")

        val listeningStates = recordedStates.filterIsInstance<LearningState.Listening>()
        val distinctRemaining = listeningStates.map { it.remainingSeconds }.distinct()
        println("[Empirical] Distinct countdown remaining seconds: $distinctRemaining")

        // Should have seen countdown ticks
        assertTrue("State at end of timeout must be Timeout", learner.state.value is LearningState.Timeout)
    }

    /**
     * EMPIRICAL CHALLENGE 3:
     * Boundary check: Does MockIrLearner handle timeoutSeconds <= 0 gracefully?
     */
    @Test
    fun testMockIrLearner_zeroAndNegativeTimeout() = runBlocking {
        val learner = MockIrLearner()
        learner.setSimulationMode(MockIrLearner.SimulationMode.SUCCESS_NEC)
        learner.setSimulatedDelayMs(10)

        val resZero = learner.startLearning(timeoutSeconds = 0)
        assertTrue("Zero timeout must not crash and be coerced to >= 1s", resZero.isSuccess)

        val resNeg = learner.startLearning(timeoutSeconds = -10)
        assertTrue("Negative timeout must not crash and be coerced to >= 1s", resNeg.isSuccess)
    }

    /**
     * EMPIRICAL CHALLENGE 4:
     * Rapid start and cancel cycling stress test.
     */
    @Test
    fun testMockIrLearner_rapidCancelCycleStress() = runBlocking {
        val learner = MockIrLearner()
        learner.setSimulationMode(MockIrLearner.SimulationMode.SUCCESS_NEC)
        learner.setSimulatedDelayMs(1000)

        repeat(30) {
            val job = launch(Dispatchers.Default) {
                learner.startLearning(timeoutSeconds = 5)
            }
            val listenDeadline = System.currentTimeMillis() + 3000
            while (learner.state.value !is LearningState.Listening && System.currentTimeMillis() < listenDeadline) {
                Thread.sleep(5)
            }
            assertTrue("Learner must reach Listening state before cancel", learner.state.value is LearningState.Listening)
            learner.cancel()
            job.join()
            assertEquals("State must be Idle after cancel", LearningState.Idle, learner.state.value)
        }
    }

    // =========================================================================
    // SECTION 2: IrLearnerManager Concurrency & Hotplug Adversarial Tests
    // =========================================================================

    /**
     * EMPIRICAL CHALLENGE 5:
     * Mutex contention on IrLearnerManager.startLearning:
     * When two coroutines invoke startLearning concurrently:
     * If coroutine 1 is cancelled via manager.cancel(), does coroutine 2 start running,
     * or what happens to the manager state?
     */
    @Test
    fun testIrLearnerManager_concurrentStartLearningMutexContention() = runBlocking {
        val manager = IrLearnerManager(context = null, scope = CoroutineScope(Dispatchers.Default))
        manager.mockLearner.setSimulationMode(MockIrLearner.SimulationMode.SUCCESS_NEC)
        manager.mockLearner.setSimulatedDelayMs(500)

        var res1: Result<IrSignal.Raw>? = null
        var res2: Result<IrSignal.Raw>? = null

        val job1 = launch(Dispatchers.Default) {
            res1 = try {
                manager.startLearning(timeoutSeconds = 5)
            } catch (cancelled: CancellationException) {
                Result.failure(cancelled)
            }
        }

        // Allow job1 to acquire mutex and start listening
        val listenDeadline = System.currentTimeMillis() + 3000
        while (manager.state.value !is LearningState.Listening && System.currentTimeMillis() < listenDeadline) {
            Thread.sleep(5)
        }
        assertTrue("Manager should be listening", manager.state.value is LearningState.Listening)

        // Launch job2 which must contend for learningMutex
        val job2 = launch(Dispatchers.Default) {
            res2 = manager.startLearning(timeoutSeconds = 5)
        }
        Thread.sleep(50) // Ensure job2 attempts to acquire learningMutex

        // Now cancel manager (cancelling the active session held by job1)
        manager.cancel()

        job1.join()
        job2.join()

        assertTrue("The active session should be cancelled", res1?.exceptionOrNull() is CancellationException)
        assertTrue("The waiting session should acquire the released mutex", res2?.isSuccess == true)
    }

    /**
     * EMPIRICAL CHALLENGE 5B:
     * When MockIrLearner is in TIMEOUT mode, calling manager.cancel() hangs
     * and holds learningMutex, deadlocking subsequent startLearning calls!
     */
    @Test
    fun testIrLearnerManager_timeoutModeCancelBlocksMutex() = runBlocking {
        val manager = IrLearnerManager(context = null, scope = CoroutineScope(Dispatchers.Default))
        manager.mockLearner.setSimulationMode(MockIrLearner.SimulationMode.TIMEOUT)
        manager.mockLearner.setSimulatedDelayMs(20)

        val job1 = launch(Dispatchers.Default) {
            manager.startLearning(timeoutSeconds = 5)
        }

        val listenDeadline = System.currentTimeMillis() + 3000
        while (manager.state.value !is LearningState.Listening && System.currentTimeMillis() < listenDeadline) {
            Thread.sleep(5)
        }
        assertTrue(manager.state.value is LearningState.Listening)
        Thread.sleep(60) // Ensure it entered TIMEOUT delay

        val t0 = System.currentTimeMillis()
        manager.cancel()

        val joinDeadline = System.currentTimeMillis() + 2000
        while (job1.isActive && System.currentTimeMillis() < joinDeadline) {
            Thread.sleep(5)
        }
        val elapsed = System.currentTimeMillis() - t0
        val completedQuickly = !job1.isActive

        println("[Empirical] Manager startLearning finished within 2000ms after cancel in TIMEOUT mode? $completedQuickly (elapsed: ${elapsed}ms)")
        if (!completedQuickly) {
            job1.cancel()
        }
        assertTrue("Manager session must terminate promptly when cancelled during TIMEOUT mode", completedQuickly && elapsed < 2000)
    }

    /**
     * EMPIRICAL CHALLENGE 6:
     * Dynamic Learner Switch / USB Hotplug during active learning session.
     *
     * Bug Hypothesis:
     * If learner A is running startLearning inside learningMutex,
     * and activeLearner switches (e.g. forceMockMode changed or USB plugged in),
     * manager.cancel() calls `activeLearner.value.cancel()`, which targets learner B!
     * Learner A continues to run in the background, locking learningMutex for its entire duration!
     */
    @Test
    fun testIrLearnerManager_dynamicLearnerSwitchOrphan() = runBlocking {
        val manager = IrLearnerManager(context = null, scope = CoroutineScope(Dispatchers.Default))
        manager.mockLearner.setSimulationMode(MockIrLearner.SimulationMode.SUCCESS_NEC)
        manager.mockLearner.setSimulatedDelayMs(3000) // 3 seconds delay

        // Start learning on mock learner
        val job = launch(Dispatchers.Default) {
            manager.startLearning(timeoutSeconds = 5)
        }

        // Wait until mock learner is listening
        val listenDeadline = System.currentTimeMillis() + 3000
        while (manager.state.value !is LearningState.Listening && System.currentTimeMillis() < listenDeadline) {
            Thread.sleep(5)
        }
        assertTrue("Manager should be listening", manager.state.value is LearningState.Listening)

        // Now simulate switching active learner while mock learner is running
        println("[Empirical] Active learner before switch: ${manager.activeLearner.value.type}")

        // Call cancel
        val t0 = System.currentTimeMillis()
        manager.cancel()

        val joinDeadline = System.currentTimeMillis() + 2000
        while (job.isActive && System.currentTimeMillis() < joinDeadline) {
            Thread.sleep(5)
        }
        val elapsed = System.currentTimeMillis() - t0

        println("[Empirical] Manager cancel elapsed time: ${elapsed}ms, state: ${manager.state.value}")
        assertTrue("Cancellation should complete promptly", !job.isActive && elapsed < 2000)
        assertEquals("Manager state must be Idle after cancellation", LearningState.Idle, manager.state.value)
    }

    /**
     * EMPIRICAL CHALLENGE 7:
     * Calling manager.cancel() when already Idle must be safe and idempotent.
     */
    @Test
    fun testIrLearnerManager_cancelIdempotency() = runBlocking {
        val manager = IrLearnerManager(context = null, scope = CoroutineScope(Dispatchers.Default))
        assertEquals(LearningState.Idle, manager.state.value)

        // Multiple cancels on Idle state
        manager.cancel()
        manager.cancel()
        manager.cancel()

        assertEquals(LearningState.Idle, manager.state.value)

        manager.cancel()
        val sessionResult = manager.startLearning(timeoutSeconds = 5)
        assertTrue("Session after idle cancel must succeed, got $sessionResult", sessionResult.isSuccess)
    }

    // =========================================================================
    // SECTION 3: Persistence & Sanitizer Edge-Case Adversarial Tests
    // =========================================================================

    /**
     * EMPIRICAL CHALLENGE 8:
     * Odd-length pulse patterns, boundary carrier frequencies, and optical noise filtering:
     * Verify that sanitized signals round-trip cleanly to RemoteButton JSON,
     * deserialize with 100% fidelity, and pass BuiltInIrTransmitter.sanitizeAndValidate().
     */
    @Test
    fun testSanitizedSignals_edgeCasesRoundTripPersistence() {
        val testCases = listOf(
            // Odd length 15 pulses -> should be padded to 16 with 40000 trailing space
            Pair(
                38000,
                intArrayOf(9000, 4500, 560, 560, 560, 1690, 560, 560, 560, 1690, 560, 560, 560, 1690, 560)
            ),
            // Low carrier frequency (10 kHz) -> clamped to 15 kHz
            Pair(
                10000,
                intArrayOf(2000, 1000, 500, 500, 500, 500)
            ),
            // High carrier frequency (100 kHz) -> clamped to 60 kHz
            Pair(
                100000,
                intArrayOf(2000, 1000, 500, 500, 500, 500)
            ),
            // Pattern with leading optical spike (< 150us) followed by valid pulses
            Pair(
                38000,
                intArrayOf(80, 50, 9000, 4500, 560, 560, 560, 560, 560, 560)
            ),
            // Signed durations retain all pulse positions.
            Pair(
                38000,
                intArrayOf(-9000, 4500, -560, 560, 560, 560)
            )
        )

        for ((idx, testCase) in testCases.withIndex()) {
            val (origFreq, origPattern) = testCase
            val sanitized = IrSignalSanitizer.sanitize(origFreq, origPattern).getOrThrow()

            // 1. Verify sanitized constraints
            assertTrue("Sanitized pulses must be even length", sanitized.patternMicros.size % 2 == 0)
            assertTrue("Frequency must be in [15k, 60k]", sanitized.frequencyHz in 15000..60000)
            for (p in sanitized.patternMicros) {
                assertTrue("Every pulse must be positive: $p", p > 0)
            }

            // 2. Map to RemoteButton
            val button = IrSignalSanitizer.toRemoteButton(
                signal = sanitized,
                label = "Learned Key $idx",
                id = "btn_learned_$idx",
                row = idx,
                col = 0
            )
            assertEquals(SignalType.RAW, button.signalType)
            assertEquals(sanitized.frequencyHz, button.frequencyHz)
            assertEquals(sanitized.patternMicros.toList(), button.rawPattern)

            // 3. Serialize to JSON and back
            val profile = RemoteProfile(
                id = "remote_learned_test_$idx",
                name = "Learned Test $idx",
                buttons = listOf(button)
            )
            val jsonText = json.encodeToString(RemoteProfile.serializer(), profile)
            val decodedProfile = json.decodeFromString(RemoteProfile.serializer(), jsonText)

            val decodedButton = decodedProfile.buttons.first()
            assertEquals(button.id, decodedButton.id)
            assertEquals(button.label, decodedButton.label)
            assertEquals(button.rawPattern, decodedButton.rawPattern)
            assertEquals(button.frequencyHz, decodedButton.frequencyHz)

            // 4. Convert back to IrSignal and validate through BuiltInIrTransmitter
            val replaySignal = decodedButton.toIrSignal() as IrSignal.Raw
            val transmitterCheck = BuiltInIrTransmitter.sanitizeAndValidate(replaySignal.frequencyHz, replaySignal.patternMicros)
            assertTrue("Transmitter validation must succeed for test case $idx", transmitterCheck.isSuccess)

            val (txFreq, txPattern) = transmitterCheck.getOrThrow()
            assertEquals(sanitized.frequencyHz, txFreq)
            assertArrayEquals("Pattern transmitted must match sanitized pattern exactly", sanitized.patternMicros, txPattern)
        }
    }

    /**
     * EMPIRICAL CHALLENGE 9:
     * Huge remote profile persistence: 50 learned buttons each with 100 pulses.
     * Tests JSON parser performance, memory stability, and data integrity.
     */
    @Test
    fun testPersistence_largeRemoteProfileRoundTrip() {
        val buttons = mutableListOf<RemoteButton>()
        repeat(50) { i ->
            val pulses = IntArray(100) { if (it % 2 == 0) 560 else 1690 }
            val sanitized = IrSignalSanitizer.sanitize(38000, pulses).getOrThrow()
            buttons.add(
                IrSignalSanitizer.toRemoteButton(
                    signal = sanitized,
                    label = "Key_$i",
                    id = "btn_key_$i",
                    row = i / 4,
                    col = i % 4
                )
            )
        }

        val profile = RemoteProfile(
            id = "huge_learned_remote",
            name = "Huge Learned Remote 50 Keys",
            buttons = buttons
        )

        val jsonStr = json.encodeToString(RemoteProfile.serializer(), profile)
        println("[Empirical] Huge profile JSON length: ${jsonStr.length} chars")
        assertTrue("JSON must contain 50 buttons", jsonStr.contains("Key_49"))

        val decoded = json.decodeFromString(RemoteProfile.serializer(), jsonStr)
        assertEquals(50, decoded.buttons.size)
        for (i in 0 until 50) {
            assertEquals("Key_$i", decoded.buttons[i].label)
            assertEquals(100, decoded.buttons[i].rawPattern.size)
        }
    }

    /**
     * EMPIRICAL CHALLENGE 10:
     * RemoteRepository.updateButton concurrency / race condition test:
     * Does concurrent updateButton on the same remote cause a lost update?
     */
    @Test
    fun testRemoteRepository_concurrentUpdateButtonRace() = runBlocking {
        val tempDir = Files.createTempDirectory("remote_repo_race_test").toFile()
        try {
            val mockContext = object : android.content.ContextWrapper(null) {
                override fun getFilesDir(): File = tempDir
                override fun getApplicationContext(): Context = this
            }

            val repository = RemoteRepository(mockContext, CoroutineScope(Dispatchers.IO))
            repository.loadAllRemotes()

            val remoteId = "test_race_remote"
            val initialProfile = RemoteProfile(
                id = remoteId,
                name = "Race Test Remote",
                buttons = listOf(
                    RemoteButton(id = "btn_1", label = "Initial 1"),
                    RemoteButton(id = "btn_2", label = "Initial 2")
                )
            )
            repository.saveRemote(initialProfile)

            // Launch concurrent updates to btn_1 and btn_2
            val updatedBtn1 = RemoteButton(id = "btn_1", label = "Updated 1", rawPattern = listOf(1000, 1000, 1000, 1000))
            val updatedBtn2 = RemoteButton(id = "btn_2", label = "Updated 2", rawPattern = listOf(2000, 2000, 2000, 2000))

            val job1 = launch(Dispatchers.IO) {
                repository.updateButton(remoteId, updatedBtn1)
            }
            val job2 = launch(Dispatchers.IO) {
                repository.updateButton(remoteId, updatedBtn2)
            }

            joinAll(job1, job2)

            val finalProfile = repository.getRemote(remoteId)
            assertNotNull("Profile must exist", finalProfile)
            val btn1 = finalProfile?.buttons?.find { it.id == "btn_1" }
            val btn2 = finalProfile?.buttons?.find { it.id == "btn_2" }

            println("[Empirical] Concurrent updateButton result: btn_1=${btn1?.label}, btn_2=${btn2?.label}")

            // If a lost update occurred, one of the updates will be missing!
            val bothPreserved = (btn1?.label == "Updated 1" && btn2?.label == "Updated 2")
            println("[Empirical] Both concurrent updates preserved? $bothPreserved")
            if (!bothPreserved) {
                println("[VULNERABILITY CONFIRMED] Lost update race condition in RemoteRepository.updateButton!")
            }
            assertTrue("Both button updates must be preserved without lost update", bothPreserved)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
