package org.qinyan.rimoat.ui.learning

import org.qinyan.rimoat.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        AppMotion.current.defaultEffectsSpec(), label = "ListeningCountdown")
    Column(
        modifier = modifier.widthIn(min = 96.dp).heightIn(min = 96.dp).clearAndSetSemantics {
            contentDescription = remainingAnnouncement
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        ContainedLoadingIndicator(
            progress = { progress.value },
            modifier = Modifier.size(56.dp),
            indicatorColor = color,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        Text(secondsLabel, style = MaterialTheme.typography.labelLarge)
    }
}
