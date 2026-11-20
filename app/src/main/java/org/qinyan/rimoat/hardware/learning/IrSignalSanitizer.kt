package org.qinyan.rimoat.hardware.learning

import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.SignalType
import org.qinyan.rimoat.hardware.IrSignal
import java.util.UUID
import kotlin.math.abs

/**
 * Defensive cleaning, validation, and normalization for learned IR pulse streams.
 *
 * Ensures all signals comply with Android ConsumerIrManager transmission constraints:
 * - Positive non-zero alternating on/off durations in microseconds
 * - Odd-length parity padding with trailing space (40,000 µs)
 * - Minimum pulse count threshold (>= 6) to reject spurious optical noise
 * - Minimum total duration threshold (>= 1,000 µs)
 * - Maximum framework duration limit (< 2,000,000 µs)
 * - Carrier frequency clamping within standard infrared range [15 kHz, 60 kHz]
 */
object IrSignalSanitizer {
    const val DEFAULT_FREQUENCY_HZ = 38000
    const val MIN_FREQUENCY_HZ = 15000
    const val MAX_FREQUENCY_HZ = 60000

    const val MIN_PULSE_COUNT = 6
    const val MIN_TOTAL_DURATION_US = 1000L
    const val MAX_TOTAL_DURATION_US = 2_000_000L // 2 seconds Android framework limit
    const val NOISE_GLITCH_THRESHOLD_US = 150
    const val TRAILING_SPACE_PADDING_US = 40000 // 40ms trailing silence for parity

    /**
     * Sanitizes and validates carrier frequency and raw microsecond pulses.
     */
    fun sanitize(frequencyHz: Int, patternMicros: IntArray): Result<IrSignal.Raw> {
        if (patternMicros.isEmpty()) {
            return Result.failure(IllegalArgumentException("Pulse pattern is empty"))
        }

        // Clamp carrier frequency
        val freq = if (frequencyHz <= 0) {
            DEFAULT_FREQUENCY_HZ
        } else {
            frequencyHz.coerceIn(MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ)
        }

        // Removing one duration would invert every subsequent mark/space.
        val positivePulses = ArrayList<Int>(patternMicros.size + 1)
        for (p in patternMicros) {
            val dur = abs(p)
            if (dur <= 0) return Result.failure(IllegalArgumentException("Invalid pulse duration: $p"))
            positivePulses.add(dur)
        }

        // 2. Glitch filtering: if leading pulse is a transient optical spike (< 150 µs),
        // drop leading glitch pair [mark, space] provided enough pulses remain.
        var startIndex = 0
        while (startIndex < positivePulses.size && positivePulses[startIndex] < NOISE_GLITCH_THRESHOLD_US) {
            if (positivePulses.size - startIndex >= 8) {
                startIndex += 2
            } else {
                break
            }
        }

        val filteredPulses = if (startIndex > 0 && startIndex < positivePulses.size) {
            positivePulses.subList(startIndex, positivePulses.size)
        } else {
            positivePulses
        }

        // 3. Minimum pulse count check (>= 6)
        if (filteredPulses.size < MIN_PULSE_COUNT) {
            return Result.failure(
                IllegalArgumentException("Pulse count ${filteredPulses.size} is below minimum threshold of $MIN_PULSE_COUNT")
            )
        }

        // 4. Parity check: Android ConsumerIrManager requires even length (alternating mark/space)
        val finalPulses = ArrayList<Int>(filteredPulses.size + 1)
        finalPulses.addAll(filteredPulses)
        if (finalPulses.size % 2 != 0) {
            finalPulses.add(TRAILING_SPACE_PADDING_US)
        }

        // 5. Total duration checks
        var totalDurationUs = 0L
        for (dur in finalPulses) {
            totalDurationUs += dur
        }

        if (totalDurationUs < MIN_TOTAL_DURATION_US) {
            return Result.failure(
                IllegalArgumentException("Total pulse duration $totalDurationUs µs is below minimum threshold of $MIN_TOTAL_DURATION_US µs")
            )
        }

        if (totalDurationUs >= MAX_TOTAL_DURATION_US) {
            return Result.failure(
                IllegalArgumentException("Total pulse duration $totalDurationUs µs exceeds framework limit of $MAX_TOTAL_DURATION_US µs")
            )
        }

        return Result.success(IrSignal.Raw(freq, finalPulses.toIntArray()))
    }

    /**
     * Converts a sanitized Raw signal into a RemoteButton for local JSON persistence.
     */
    fun toRemoteButton(
        signal: IrSignal.Raw,
        label: String,
        id: String = UUID.randomUUID().toString(),
        row: Int = 0,
        col: Int = 0,
        spanCol: Int = 1,
        iconName: String? = null
    ): RemoteButton {
        return RemoteButton(
            id = id,
            label = label,
            iconName = iconName,
            row = row,
            col = col,
            spanCol = spanCol,
            signalType = SignalType.RAW,
            frequencyHz = signal.frequencyHz,
            rawPattern = signal.patternMicros.toList(),
            necAddress = null,
            necCommand = null
        )
    }

    /**
     * Infer IR protocol type name from pulse pattern timing.
     */
    fun guessProtocol(pattern: IntArray): String {
        if (pattern.size >= 66) {
            val leaderMark = pattern[0]
            val leaderSpace = pattern[1]
            if (leaderMark in 7500..10500 && leaderSpace in 3500..5500) {
                return "NEC (38kHz)"
            }
        }
        if (pattern.size in 24..26) {
            val leaderMark = pattern[0]
            val leaderSpace = pattern[1]
            if (leaderMark in 1800..3000 && leaderSpace in 400..800) {
                return "SONY (40kHz)"
            }
        }
        if (pattern.size >= 80) {
            return "AC/HVAC (Raw)"
        }
        return "RAW"
    }
}
