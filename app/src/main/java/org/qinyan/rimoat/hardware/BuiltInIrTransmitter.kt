package org.qinyan.rimoat.hardware

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Android built-in Consumer IR transmitter wrapper.
 */
class BuiltInIrTransmitter(
    private val context: Context,
    refreshImmediately: Boolean = true
) : IrTransmitter {

    companion object {
        private const val TAG = "BuiltInIrTransmitter"
        const val DEFAULT_FREQUENCY_HZ = 38000
        const val MAX_TRANSMISSION_DURATION_US = 2_000_000L // 2 seconds Android framework limit
        const val DEFAULT_TRAILING_SPACE_US = 40000 // 40ms trailing silence for mark/space parity

        /**
         * Validates and sanitizes carrier frequency and raw pulse pattern.
         *
         * 1. Check for empty pattern -> failure.
         * 2. Convert each duration to positive integer abs(dur). Reject / return failure if dur <= 0.
         * 3. Carrier frequency check: validate freq > 0; default to 38000 Hz if <= 0.
         * 4. Parity check (odd-length padding): If pattern.size % 2 != 0, append trailing space 40000 us.
         * 5. Check total duration: sum of durations must be < 2,000,000 us.
         */
        fun sanitizeAndValidate(
            frequencyHz: Int,
            patternMicros: IntArray
        ): Result<Pair<Int, IntArray>> {
            if (patternMicros.isEmpty()) {
                return Result.failure(IllegalArgumentException("Empty pulse pattern"))
            }

            val freq = if (frequencyHz > 0) frequencyHz else DEFAULT_FREQUENCY_HZ

            val sanitizedList = ArrayList<Int>(patternMicros.size + 1)
            var totalDurationUs = 0L

            for (p in patternMicros) {
                val dur = abs(p)
                if (dur <= 0) {
                    return Result.failure(IllegalArgumentException("Invalid non-positive pulse duration: $p"))
                }
                sanitizedList.add(dur)
                totalDurationUs += dur
            }

            val wasPadded = sanitizedList.size % 2 != 0
            if (wasPadded) {
                sanitizedList.add(DEFAULT_TRAILING_SPACE_US)
                totalDurationUs += DEFAULT_TRAILING_SPACE_US
            }

            if (totalDurationUs >= MAX_TRANSMISSION_DURATION_US) {
                return Result.failure(
                    IllegalArgumentException("Total pulse duration $totalDurationUs µs exceeds framework limit of $MAX_TRANSMISSION_DURATION_US µs")
                )
            }

            try {
                Log.d(TAG, "Sanitized pulse pattern: ${patternMicros.size} pulses -> ${sanitizedList.size} pulses (parity padding: $wasPadded), carrier: $freq Hz, totalDuration: $totalDurationUs µs")
            } catch (_: Throwable) {
                // Ignore logger issues in non-android test environments
            }

            return Result.success(Pair(freq, sanitizedList.toIntArray()))
        }
    }

    override val type: TransmitterType = TransmitterType.BUILT_IN

    private val irManager: ConsumerIrManager? by lazy {
        try {
            context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        } catch (e: Exception) {
            Log.w(TAG, "ConsumerIrManager service acquisition failed", e)
            null
        }
    }

    private fun queryHardwareAvailability(): Boolean {
        return try {
            irManager?.hasIrEmitter() == true
        } catch (_: Exception) {
            false
        }
    }

    private val _isReady = MutableStateFlow(if (refreshImmediately) queryHardwareAvailability() else false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    fun checkHardwareAvailability(): Boolean {
        val ready = queryHardwareAvailability()
        _isReady.value = ready
        return ready
    }

    override suspend fun transmit(signal: IrSignal): Result<Unit> = withContext(Dispatchers.IO) {
        val manager = irManager
            ?: return@withContext Result.failure(IllegalStateException(AppStrings.get(R.string.msg_builtin_manager_missing, "ConsumerIrManager not available")))

        if (!manager.hasIrEmitter()) {
            return@withContext Result.failure(IllegalStateException(AppStrings.get(R.string.msg_builtin_emitter_missing, "Device does not possess an IR emitter")))
        }

        try {
            val rawSignal = when (signal) {
                is IrSignal.Raw -> signal
                is IrSignal.Nec -> IrProtocolEncoder.encodeNec(signal)
            }

            val (freq, pattern) = sanitizeAndValidate(rawSignal.frequencyHz, rawSignal.patternMicros).getOrElse {
                return@withContext Result.failure(it)
            }

            try {
                Log.d(TAG, "Transmitting frequency: $freq Hz, pulses: ${pattern.size}")
            } catch (_: Throwable) {
                // Ignore logger issues in non-android test environments
            }
            manager.transmit(freq, pattern)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
