package android.os

import android.media.AudioAttributes

/**
 * Test helper in android.os package to allow subclassing android.os.Vibrator
 * whose constructor is package-private in the Android SDK mockable jar.
 */
open class ThrowingVibrator(
    var throwOnHasVibrator: Boolean = false,
    var throwOnVibrate: Boolean = true,
    var customVibratorPresent: Boolean = true
) : Vibrator() {

    var vibrateMsCallCount = 0
    var vibratePatternCallCount = 0
    var vibrateEffectCallCount = 0

    override fun hasVibrator(): Boolean {
        if (throwOnHasVibrator) {
            throw SecurityException("Simulated security permission exception in hasVibrator()")
        }
        return customVibratorPresent
    }

    override fun hasAmplitudeControl(): Boolean = false

    override fun cancel() {}

    override fun vibrate(milliseconds: Long) {
        vibrateMsCallCount++
        if (throwOnVibrate) {
            throw SecurityException("Simulated security exception in vibrate(ms)")
        }
    }

    override fun vibrate(pattern: LongArray?, repeat: Int) {
        vibratePatternCallCount++
        if (throwOnVibrate) {
            throw SecurityException("Simulated security exception in vibrate(pattern)")
        }
    }

    override fun vibrate(effect: VibrationEffect) {
        vibrateEffectCallCount++
        if (throwOnVibrate) {
            throw SecurityException("Simulated security exception in vibrate(effect)")
        }
    }

    override fun vibrate(milliseconds: Long, attributes: AudioAttributes?) {
        vibrate(milliseconds)
    }

    override fun vibrate(pattern: LongArray?, repeat: Int, attributes: AudioAttributes?) {
        vibrate(pattern, repeat)
    }

    override fun vibrate(effect: VibrationEffect, attributes: AudioAttributes?) {
        vibrate(effect)
    }
}

open class ThrowingVibratorManager(
    val vibrator: ThrowingVibrator = ThrowingVibrator()
) : VibratorManager() {
    override fun getVibratorIds(): IntArray = intArrayOf(1)
    override fun getVibrator(vibratorId: Int): Vibrator = vibrator
    override fun getDefaultVibrator(): Vibrator = vibrator
    override fun cancel() {}
}
