package org.qinyan.rimoat.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Unfold below the controlling row, then fold back towards that same row. */
@Composable
fun ExpandSection(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val motion = AppMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Top) +
            fadeIn(motion.fastEffectsSpec()),
        exit = shrinkVertically(motion.fastSpatialSpec(), shrinkTowards = Alignment.Top) +
            fadeOut(motion.fastEffectsSpec())
    ) { content() }
}
