package org.qinyan.rimoat.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation

/** One effect per feedback event; never stack Compose and vibrator APIs for the same event. */
object HapticHelper {
    private var cachedContext: Context? = null
    private var cachedVibrator: Vibrator? = null
    private var cachedHasVibrator: Boolean? = null
    private val cachedSupportedEffects = HashMap<Int, Boolean>(4)
    private val touchVibrationAttrs: VibrationAttributes? by lazy {
        if (Build.VERSION.SDK_INT >= 33) {
            try { VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build() } catch (_: Throwable) { null }
        } else null
    }
    private val fallbackAudioAttrs: AudioAttributes? by lazy {
        if (Build.VERSION.SDK_INT in 26..32) {
            try { AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build() } catch (_: Throwable) { null }
        } else null
    }
    private var lastTick = 0L
    fun performRemoteClick(hapticFeedback: HapticFeedback? = null, context: Context? = null) =
        emit(VibrationEffect.EFFECT_CLICK, 10, hapticFeedback, context)
    /** Separate completion tick; caller must have completed a real hardware transaction. */
    fun performTransmissionConfirmed(context: Context) =
        emit(VibrationEffect.EFFECT_TICK, 6, null, context)
    fun performTestEmit(hapticFeedback: HapticFeedback? = null, context: Context? = null) =
        performRemoteClick(hapticFeedback, context)
    fun performStartListening(hapticFeedback: HapticFeedback? = null, context: Context? = null) =
        performSelection(hapticFeedback, context)
    fun performCaptured(hapticFeedback: HapticFeedback? = null, context: Context? = null) =
        emit(VibrationEffect.EFFECT_CLICK, 12, hapticFeedback, context)
    fun performSaveSuccess(hapticFeedback: HapticFeedback? = null, context: Context? = null) =
        emit(VibrationEffect.EFFECT_CLICK, 12, hapticFeedback, context)
    @Synchronized
    fun performSelection(hapticFeedback: HapticFeedback? = null, context: Context? = null) {
        val now = System.nanoTime() / 1_000_000
        if (now - lastTick < 60) return
        lastTick = now
        emit(VibrationEffect.EFFECT_TICK, 6, hapticFeedback, context)
    }
    @Synchronized
    private fun emit(effect: Int, fallbackMs: Long, haptic: HapticFeedback?, context: Context?) {
        if (context == null) {
            try { haptic?.performHapticFeedback(if (effect == VibrationEffect.EFFECT_CLICK) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove) } catch (_: RuntimeException) { }
            return
        }
        try {
            if (context.contentResolver?.let { Settings.System.getInt(it, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) } == 0) return
            val app = context.applicationContext
            if (cachedContext !== app) {
                cachedContext = app
                cachedHasVibrator = null
                cachedSupportedEffects.clear()
                cachedVibrator = if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
                    else @Suppress("DEPRECATION") (app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            }
            val vibrator = cachedVibrator ?: return
            val hasVibrator = cachedHasVibrator ?: vibrator.hasVibrator().also { cachedHasVibrator = it }
            if (!hasVibrator) return
            if (Build.VERSION.SDK_INT >= 26) {
                val supported = Build.VERSION.SDK_INT >= 30 && (
                    cachedSupportedEffects[effect] ?: (
                        vibrator.areEffectsSupported(effect).firstOrNull() == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
                    ).also { cachedSupportedEffects[effect] = it }
                )
                val vibration = if (supported) VibrationEffect.createPredefined(effect)
                    else VibrationEffect.createOneShot(fallbackMs, 100)
                if (Build.VERSION.SDK_INT >= 33) {
                    val attrs = touchVibrationAttrs
                    if (attrs != null) vibrator.vibrate(vibration, attrs) else vibrator.vibrate(vibration)
                } else {
                    @Suppress("DEPRECATION")
                    val attrs = fallbackAudioAttrs
                    @Suppress("DEPRECATION")
                    if (attrs != null) vibrator.vibrate(vibration, attrs) else vibrator.vibrate(vibration)
                }
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(fallbackMs)
            }
        } catch (_: RuntimeException) { /* Unsupported/denied vibration must not block the action. */ }
    }
}

class PressHapticFeedback(val modifier: Modifier, private val click: () -> Unit) {
    operator fun invoke() = click()
}

/** Observe DOWN before scroll/tap disambiguation without consuming the gesture. */
@Composable
fun rememberPressHaptic(source: MutableInteractionSource, enabled: Boolean, learning: Boolean = false): PressHapticFeedback {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val active by rememberUpdatedState(enabled)
    val isLearning by rememberUpdatedState(learning)
    var delivered by remember(source) { mutableStateOf(false) }
    fun emit() {
        if (isLearning) HapticHelper.performSelection(haptic, context)
        else HapticHelper.performRemoteClick(haptic, context)
    }
    val modifier = Modifier.pointerInput(source) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            delivered = active
            if (active) emit()
            val up = waitForUpOrCancellation(PointerEventPass.Initial)
            if (up != null) awaitPointerEvent(PointerEventPass.Final)
            // Click handlers run in Main. Clear a held/repeated press afterwards,
            // so a later keyboard/accessibility click still gets its own feedback.
            delivered = false
        }
    }
    return PressHapticFeedback(modifier) { if (!delivered && enabled) emit(); delivered = false }
}
