package org.qinyan.rimoat.ui.motion

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shared nonlinear curves for app-owned automatic motion. Gestures remain finger-tracked. */
object AppMotion {
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Enter = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val Exit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    fun <T> tween(duration: Int = 220) = androidx.compose.animation.core.tween<T>(duration, easing = Standard)
}

@Composable
fun FadeVisibility(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180, easing = AppMotion.Enter)),
        exit = fadeOut(tween(100, easing = AppMotion.Exit))
    ) { content() }
}

/** Changes within a screen fade locally; they do not move the entire page or its actions. */
@Composable
fun <T> StateFade(target: T, modifier: Modifier = Modifier, contentKey: (T) -> Any? = { it }, content: @Composable (T) -> Unit) {
    AnimatedContent(target, modifier, transitionSpec = {
        (fadeIn(tween(180, easing = AppMotion.Enter)) togetherWith
            fadeOut(tween(100, easing = AppMotion.Exit))).using(null)
    }, contentKey = contentKey, label = "StateChange") { content(it) }
}
