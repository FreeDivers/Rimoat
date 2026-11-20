package org.qinyan.rimoat.hardware.audio

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Synthesizes 16-bit stereo differential PCM audio signals from infrared pulse patterns.
 *
 * Employs ultrasonic frequency halving (e.g. 19 kHz audio tone for 38 kHz optical carrier)
 * to take advantage of anti-parallel infrared diodes plugged into 3.5mm headphone jacks:
 * - Positive audio half-cycle drives Diode 1 (Forward)
 * - Negative audio half-cycle drives Diode 2 (Reverse)
 * - Result: 2 optical IR pulses per audio sine cycle (Frequency Doubling).
 *
 * Left and Right channels are driven in anti-phase (180° offset) to double the differential
 * output voltage across the LED terminals (2x Vpeak).
 */
object AudioPcmBuilder {
    const val SAMPLE_RATE_HZ: Int = 48000
    const val AMPLITUDE: Double = 32000.0 // Near 16-bit max (32767) for maximum LED driving power
    private const val TWO_PI: Double = 2.0 * Math.PI

    /**
     * Converts raw microsecond durations to stereo 16-bit interleaved PCM samples.
     * [L0, R0, L1, R1, L2, R2, ...]
     */
    fun buildStereoPcm(
        carrierHz: Int,
        patternMicros: IntArray,
        sampleRate: Int = SAMPLE_RATE_HZ
    ): ShortArray {
        require(sampleRate in 8000..192000) { AppStrings.get(R.string.msg_sample_rate_invalid, "采样率无效") }
        require(patternMicros.isNotEmpty() && patternMicros.size <= 20000 &&
            patternMicros.all { it != 0 && it != Int.MIN_VALUE } &&
            patternMicros.sumOf { kotlin.math.abs(it.toLong()) } < 2_000_000) { AppStrings.get(R.string.msg_audio_pulses_invalid, "音频红外脉冲无效或过长") }
        // The audio path emits half the optical carrier. At 48 kHz a 24 kHz
        // tone is exactly Nyquist and produces an all-zero sine wave, while
        // higher frequencies alias. Keep a margin below Nyquist.
        require(carrierHz in 20_000..44_000) {
            AppStrings.get(R.string.msg_audio_carrier_range, "音频红外载波必须在 20–44 kHz 范围内: %1\$s", (carrierHz).toString())
        }
        val toneHz = carrierHz / 2.0
        require(toneHz < sampleRate / 2.0) { AppStrings.get(R.string.msg_audio_carrier_nyquist, "音频载波超过采样率可表达范围") }
        val phaseIncrement = (toneHz * TWO_PI) / sampleRate.toDouble()

        // 1. Calculate total audio frames needed
        var totalFrames = 0L
        for (dur in patternMicros) {
            val us = kotlin.math.abs(dur.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val frames = ((us.toDouble() * sampleRate) / 1_000_000.0).roundToInt().coerceAtLeast(0)
            totalFrames += frames
        }

        val totalSamples = (totalFrames * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val pcm = ShortArray(totalSamples)

        var sampleIndex = 0
        var isMark = true

        for (dur in patternMicros) {
            val us = kotlin.math.abs(dur.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val frames = ((us.toDouble() * sampleRate) / 1_000_000.0).roundToInt().coerceAtLeast(0)
            if (frames <= 0) {
                isMark = !isMark
                continue
            }

            if (isMark) {
                var phase = 0.0
                for (f in 0 until frames) {
                    if (sampleIndex + 1 >= pcm.size) break
                    val sample = (sin(phase) * AMPLITUDE).toInt().toShort()
                    val antiPhaseSample = (-sample).toInt().toShort()

                    pcm[sampleIndex++] = sample
                    pcm[sampleIndex++] = antiPhaseSample

                    phase += phaseIncrement
                    if (phase >= TWO_PI) {
                        phase -= TWO_PI
                    }
                }
            } else {
                for (f in 0 until frames) {
                    if (sampleIndex + 1 >= pcm.size) break
                    pcm[sampleIndex++] = 0
                    pcm[sampleIndex++] = 0
                }
            }

            isMark = !isMark
        }

        return pcm
    }

    /**
     * Calculates the playback duration in milliseconds for the given PCM sample array.
     */
    fun calculatePlaybackDurationMs(pcmSize: Int, sampleRate: Int = SAMPLE_RATE_HZ): Long {
        val frames = pcmSize / 2 // Stereo
        return ((frames.toDouble() / sampleRate.toDouble()) * 1000.0).toLong().coerceAtLeast(10L)
    }
}

