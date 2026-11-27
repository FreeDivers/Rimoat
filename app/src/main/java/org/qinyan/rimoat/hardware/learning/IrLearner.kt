package org.qinyan.rimoat.hardware.learning

import org.qinyan.rimoat.hardware.IrSignal
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeoutException

/** Typed exception thrown when an IR learning session times out without capturing a signal. */
class LearningTimeoutException(message: String = "IR learning timed out") : TimeoutException(message)

/**
 * Types of supported IR hardware learning backends.
 */
enum class LearnerType {
    USB_TIQIAA,
    USB_ELKSMART,
    HUAWEI_OEM,
    MOCK,
    NONE
}

/**
 * Lifecycle states of an IR learning session.
 */
sealed interface LearningState {
    /** Hardware is idle and ready for a new learning session */
    object Idle : LearningState

    /** Actively listening for physical remote control signal */
    data class Listening(
        val remainingSeconds: Int,
        val totalSeconds: Int = 30
    ) : LearningState

    /** Signal captured and validated, awaiting verification or persistence */
    data class Captured(
        val signal: IrSignal.Raw,
        val pulseCount: Int = signal.patternMicros.size,
        val durationMicros: Long = signal.patternMicros.fold(0L) { acc, v -> acc + v }
    ) : LearningState

    /** Learning timed out without detecting a valid signal */
    object Timeout : LearningState

    /** Hardware error, missing permissions, or communication failure */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : LearningState
}

/**
 * Common abstraction for IR receivers and learners.
 */
interface IrLearner {
    val type: LearnerType
    val isAvailable: StateFlow<Boolean>
    val state: StateFlow<LearningState>

    /**
     * Starts listening for an incoming IR signal.
     *
     * @param timeoutSeconds Maximum duration to listen before timing out.
     * @return Result containing the captured and sanitized raw IR signal, or failure.
     */
    suspend fun startLearning(timeoutSeconds: Int = 30): Result<IrSignal.Raw>

    /**
     * Cancels an ongoing learning session and resets state to Idle.
     */
    fun cancel()
}
