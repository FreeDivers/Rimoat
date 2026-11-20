package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.ui.motion.AppMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import org.qinyan.rimoat.ui.components.AppSnackbarHost
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
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
    contentDescription: String = AppStrings.get(R.string.control_channel_settings, "红外发射器通道设置"),
    transmitFeedback: TransmitLog? = null,
    onFeedbackFinished: (TransmitLog) -> Unit = {}
) {
    var feedbackActive by remember { mutableStateOf(false) }
    val latestOnFeedbackFinished by rememberUpdatedState(onFeedbackFinished)
    // Repeated results extend the active interval without recreating the wave
    // animation. Only the timeout restarts; the drawing phase keeps advancing.
    LaunchedEffect(transmitFeedback) {
        feedbackActive = transmitFeedback?.isSuccess == true
        if (transmitFeedback?.isSuccess == true) {
            delay(600)
            feedbackActive = false
            latestOnFeedbackFinished(transmitFeedback)
        }
    }
    val label = when {
        forceMock || transmitterType == TransmitterType.MOCK -> AppStrings.get(R.string.control_demo_mode, "演示模式")
        transmitterType == TransmitterType.USB_TIQIAA -> AppStrings.get(R.string.control_tiqiaa, "遥控精灵 USB")
        transmitterType == TransmitterType.USB_ELKSMART -> AppStrings.get(R.string.control_elksmart, "小蛮腰 USB")
        transmitterType == TransmitterType.USB_SERIAL -> AppStrings.get(R.string.control_serial_ir, "串口红外")
        transmitterType == TransmitterType.USB -> AppStrings.get(R.string.control_usb, "USB 外设")
        transmitterType == TransmitterType.AUDIO -> AppStrings.get(R.string.control_audio_ir, "音频口红外")
        transmitterType == TransmitterType.BUILT_IN && isReady -> AppStrings.get(R.string.control_builtin_ir, "内置红外")
        transmitterType == TransmitterType.BUILT_IN -> AppStrings.get(R.string.control_no_ir, "无红外硬件")
        else -> AppStrings.get(R.string.control_no_hardware, "未连接硬件")
    }
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant

    val successColor = if (isSystemInDarkTheme()) Color(0xFFA8C7FA) else Color(0xFF0B57D0)
    val iconColor by animateColorAsState(if (feedbackActive) successColor else contentColor,
        animationSpec = AppMotion.tween(if (feedbackActive) 80 else 360), label = "TransmitIconColor")
    // The drawing layer sits outside IconButton's round press clip, so waves can
    // extend to 28dp while the touch target stays 48dp and doesn't move neighbours.
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
    // Keep the same wave phase through the tail. A new send during fade-out
    // reverses the fade instead of replacing the animation with its first frame.
    AnimatedVisibility(feedbackActive, enter = fadeIn(AppMotion.tween(80)), exit = fadeOut(AppMotion.tween(360))) {
        TransmitWaves(successColor)
    }
    ActionIconButton(
        label = contentDescription,
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                stateDescription = if (feedbackActive) AppStrings.get(R.string.control_status_feedback, "%1\$s，%2\$s", label, localizedLabel(if (forceMock || transmitterType == TransmitterType.MOCK) "模拟发送" else "信号已发送")) else label
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
    // This composable is keyed by the active interval, never by a transmission.
    val transition = rememberInfiniteTransition(label = "TransmitWaves")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = AppMotion.Standard), RepeatMode.Restart),
        label = "TransmitWavePhase"
    )
    Box(Modifier.size(48.dp).drawWithContent {
            // Read animation state only while drawing; the remote layout does
            // not need to recompose or remeasure on every animation frame.
            val progress = phase.value
            repeat(2) { index ->
                val wave = (progress + index * .5f) % 1f
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
    val hostState = remember { SnackbarHostState() }
    val latestOnDismissed by rememberUpdatedState(onDismissed)
    // A new result cancels the previous showSnackbar call instead of queueing
    // every repeated press. SnackbarHost owns timeout and accessibility timing.
    LaunchedEffect(log) {
        if (log != null) {
            hostState.showSnackbar(
                message = when {
                    !log.isSuccess -> AppStrings.get(R.string.control_send_failed, "发送失败: %1\$s", log.errorMessage ?: localizedLabel("未知错误"))
                    log.transmitterType == TransmitterType.MOCK -> AppStrings.get(R.string.control_simulated_send, "模拟发送")
                    else -> AppStrings.get(R.string.control_sent_channel, "已发送 [%1\$s]", log.transmitterType.displayName)
                },
                duration = if (log.isSuccess) SnackbarDuration.Short else SnackbarDuration.Long
            )
            latestOnDismissed(log)
        }
    }
    AppSnackbarHost(hostState = hostState, modifier = modifier)
}
