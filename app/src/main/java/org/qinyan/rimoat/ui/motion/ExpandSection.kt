package org.qinyan.rimoat.ui.motion

import org.qinyan.rimoat.ui.motion.AppMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(tween(220, easing = AppMotion.Standard), expandFrom = Alignment.Top) +
            fadeIn(AppMotion.tween(140)),
        exit = shrinkVertically(tween(180, easing = AppMotion.Standard), shrinkTowards = Alignment.Top) +
            fadeOut(AppMotion.tween(100))
    ) { content() }
}
