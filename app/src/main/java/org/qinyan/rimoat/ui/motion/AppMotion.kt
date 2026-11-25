package org.qinyan.rimoat.ui.motion

import androidx.compose.animation.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier

/** Read the local motion scheme in composition, then use its springs in interaction callbacks. */
object AppMotion {
    val current: MotionScheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme
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
