package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.R
import org.qinyan.rimoat.ui.motion.AppMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.hardware.TransmitFailureReason
import org.qinyan.rimoat.hardware.TransmitLog
import org.qinyan.rimoat.hardware.TransmitterType
import kotlinx.coroutines.delay

@Composable
fun HardwareStatusButton(
    transmitterType: TransmitterType,
    isReady: Boolean,
    forceMock: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.control_channel_settings),
    transmitFeedback: TransmitLog? = null,
    onFeedbackFinished: (TransmitLog) -> Unit = {}
) {
    val motion = AppMotion.current
    var feedbackActive by remember { mutableStateOf(false) }
    val latestOnFeedbackFinished by rememberUpdatedState(onFeedbackFinished)
    // Repeated results extend the active interval without restarting the feedback spring.
    LaunchedEffect(transmitFeedback) {
        feedbackActive = transmitFeedback?.isSuccess == true
        if (transmitFeedback?.isSuccess == true) {
            delay(600)
            feedbackActive = false
            latestOnFeedbackFinished(transmitFeedback)
        }
    }
    val label = stringResource(transmitterShortLabelRes(transmitterType, isReady = isReady, forceMock = forceMock))
    val feedbackStateText = stringResource(
        if (forceMock || transmitterType == TransmitterType.MOCK) {
            R.string.control_simulated_send
        } else {
            R.string.control_signal_sent
        }
    )
    val activeFeedbackDescription = stringResource(R.string.control_status_feedback, label, feedbackStateText)
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant

    val successColor = MaterialTheme.colorScheme.tertiary
    val iconColor by animateColorAsState(if (feedbackActive) successColor else contentColor,
        animationSpec = motion.fastEffectsSpec(), label = "TransmitIconColor")
    // The drawing layer sits outside IconButton's round press clip, so waves can
    // extend to 28dp while the touch target stays 48dp and doesn't move neighbours.
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
    // A new send during fade-out reverses the fade without restarting the wave.
    AnimatedVisibility(feedbackActive, enter = fadeIn(motion.fastEffectsSpec()), exit = fadeOut(motion.defaultEffectsSpec())) {
        TransmitWaves(successColor)
    }
    ActionIconButton(
        label = contentDescription,
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                stateDescription = if (feedbackActive) activeFeedbackDescription else label
            }
    ) {
        Icon(
            imageVector = Icons.Rounded.SettingsInputAntenna,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
    }
}

@Composable
private fun TransmitWaves(color: Color) {
    val motion = AppMotion.current
    // This composable is keyed by the active interval, never by a transmission.
    val phase = remember { Animatable(0f) }
    LaunchedEffect(motion) {
        phase.animateTo(1f, motion.slowSpatialSpec())
    }
    Box(Modifier.size(48.dp).drawWithContent {
            // Read animation state only while drawing; the remote layout does
            // not need to recompose or remeasure on every animation frame.
            val progress = phase.value.coerceIn(0f, 1f)
            repeat(2) { index ->
                val start = index * .2f
                val wave = ((progress - start) / (1f - start)).coerceIn(0f, 1f)
                val radius = (4 + 24 * wave).dp.toPx()
                drawCircle(color.copy(alpha = (1 - wave) * 0.5f), radius,
                    style = Stroke(width = 1.5.dp.toPx()))
            }
        })
}

@Composable
fun TransmitFeedbackBanner(
    log: TransmitLog?,
    onDismissed: (TransmitLog) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hostState = remember { SnackbarHostState() }
    val latestOnDismissed by rememberUpdatedState(onDismissed)
    // A new result cancels the previous showSnackbar call instead of queueing
    // every repeated press. SnackbarHost owns timeout and accessibility timing.
    LaunchedEffect(log) {
        if (log != null) {
            hostState.showSnackbar(
                message = when {
                    !log.isSuccess -> context.getString(
                        R.string.control_send_failed,
                        context.getString(
                            log.failureReason?.labelRes ?: R.string.control_unknown_error
                        )
                    )
                    log.transmitterType == TransmitterType.MOCK -> context.getString(R.string.control_simulated_send)
                    else -> context.getString(
                        R.string.control_sent_channel,
                        context.getString(transmitterShortLabelRes(log.transmitterType))
                    )
                },
                duration = if (log.isSuccess) SnackbarDuration.Short else SnackbarDuration.Long
            )
            latestOnDismissed(log)
        }
    }
    AppSnackbarHost(hostState = hostState, modifier = modifier)
}
