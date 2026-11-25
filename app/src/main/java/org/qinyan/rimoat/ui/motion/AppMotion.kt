package org.qinyan.rimoat.ui.motion

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier

/** Read the local motion scheme in composition, then use its springs in interaction callbacks. */
object AppMotion {
    val current: MotionScheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme

    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Enter = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val Exit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    fun <T> tween(duration: Int = 220) = androidx.compose.animation.core.tween<T>(duration, easing = Standard)
}

@Composable
fun FadeVisibility(visible: Boolean, content: @Composable () -> Unit) {
    val motion = AppMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.defaultEffectsSpec()),
        exit = fadeOut(motion.fastEffectsSpec())
    ) { content() }
}

/** Changes within a screen fade locally; they do not move the entire page or its actions. */
@Composable
fun <T> StateFade(target: T, modifier: Modifier = Modifier, contentKey: (T) -> Any? = { it }, content: @Composable (T) -> Unit) {
    val motion = AppMotion.current
    AnimatedContent(target, modifier, transitionSpec = {
        (fadeIn(motion.defaultEffectsSpec()) togetherWith
            fadeOut(motion.fastEffectsSpec())).using(null)
    }, contentKey = contentKey, label = "StateChange") { content(it) }
}
