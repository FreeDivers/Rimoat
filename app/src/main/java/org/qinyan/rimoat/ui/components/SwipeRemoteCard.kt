package org.qinyan.rimoat.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.ui.HapticHelper
import org.qinyan.rimoat.ui.motion.AppMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The moving card uncovers an action at a fixed position beneath it. */
@Composable
fun SwipeRemoteCard(
    favorite: Boolean,
    onFavorite: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val motion = AppMotion.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val threshold = with(density) { 104.dp.toPx() }
    val limit = with(density) { 144.dp.toPx() }
    val revealStart = with(density) { 8.dp.toPx() }
    val revealEnd = with(density) { 80.dp.toPx() }
    val travel = with(density) { 12.dp.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    var actionOpacity by remember { mutableFloatStateOf(1f) }
    var width by remember { mutableIntStateOf(1) }
    var settling by remember { mutableStateOf(false) }
    val favoriteAction by rememberUpdatedState(onFavorite)
    val removeAction by rememberUpdatedState(onRemove)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        snapshotFlow { abs(offset) >= threshold }.collect { ready ->
            if (ready && !settling) HapticHelper.performSelection(context = context)
        }
    }
    fun settle(released: Boolean) {
        if (settling) return
        settling = true
        scope.launch {
            try {
                val completed = released && abs(offset) >= threshold
                val removing = completed && offset < 0
                coroutineScope {
                    if (removing) launch {
                        animate(1f, 0f, animationSpec = motion.fastEffectsSpec()) { value, _ ->
                            actionOpacity = value
                        }
                    }
                    animate(
                        offset, if (removing) -width.toFloat() else 0f,
                        animationSpec = motion.defaultSpatialSpec()
                    ) { value, _ -> offset = value }
                }
                if (removing) {
                    removeAction()
                    // A storage failure must leave a usable card. Successful removal disposes it.
                    delay(700)
                    animate(offset, 0f, animationSpec = motion.defaultSpatialSpec()) { value, _ ->
                        offset = value
                    }
                } else if (completed) {
                    favoriteAction()
                }
            } finally {
                actionOpacity = 1f
                settling = false
            }
        }
    }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxWidth().onSizeChanged { width = it.width }
            .pointerInput(threshold, limit) {
                var accepted = false
                detectHorizontalDragGestures(
                    onDragStart = { accepted = !settling },
                    onDragEnd = { if (accepted) settle(released = true) },
                    onDragCancel = { if (accepted) settle(released = false) }
                ) { _, delta ->
                    if (accepted && !settling) offset = (offset + delta).coerceIn(-limit, limit)
                }
            }
    ) {
        for (removing in listOf(false, true)) {
            val background = if (removing) colors.errorContainer else colors.primaryContainer
            val ink = if (removing) colors.onErrorContainer else colors.onPrimaryContainer
            Box(
                Modifier.align(if (removing) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 28.dp)
                    .size(48.dp)
                    .clearAndSetSemantics { }
                    .graphicsLayer {
                        // Read gesture state in the layer; no layout or path work on each frame.
                        val fraction = ((abs(offset) - revealStart) / (revealEnd - revealStart))
                            .coerceIn(0f, 1f)
                        val reveal = fraction * fraction * (3f - 2f * fraction)
                        alpha = if ((offset < 0) == removing) reveal * actionOpacity else 0f
                        translationX = (if (removing) -travel else travel) * (1f - reveal)
                        scaleX = .85f + .15f * reveal
                        scaleY = scaleX
                    }
                    .background(background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (removing) Icons.Rounded.DeleteOutline
                    else if (favorite) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Box(Modifier.graphicsLayer { translationX = offset }) { content() }
    }
}
