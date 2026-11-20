package org.qinyan.rimoat.ui.learning

import org.qinyan.rimoat.R

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import org.qinyan.rimoat.ui.motion.AppMotion
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

/** Shows actual remaining listening time without decorative looping waves. */
@Composable
fun IrListeningRipple(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    remainingSeconds: Int = 30,
    totalSeconds: Int = 30
) {
    val safeRemaining = remainingSeconds.coerceAtLeast(0)
    val remainingAnnouncement = stringResource(R.string.screen_receive_remaining, safeRemaining)
    val secondsLabel = stringResource(R.string.screen_seconds, safeRemaining)
    val progress = animateFloatAsState((remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f),
        AppMotion.tween(600), label = "ListeningCountdown")
    Box(
        modifier = modifier.size(96.dp).clearAndSetSemantics {
            contentDescription = remainingAnnouncement
        },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress.value },
            modifier = Modifier.size(88.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeWidth = 4.dp
        )
        Text(secondsLabel, style = MaterialTheme.typography.labelLarge)
    }
}
