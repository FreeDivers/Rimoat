package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.ui.components.ActionIconButton
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.semantics.*
import org.qinyan.rimoat.ui.motion.AppMotion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalAccessibilityManager
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Nonlinear local fades, live-region semantics and accessibility-adjusted timeouts. */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val motion = AppMotion.current
    val current = hostState.currentSnackbarData
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(current, accessibility) {
        if (current != null && current.visuals.duration != SnackbarDuration.Indefinite) {
            val timeout = accessibility?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = if (current.visuals.duration == SnackbarDuration.Long) 6_000L else 4_000L,
                containsIcons = true, containsText = true,
                containsControls = current.visuals.actionLabel != null || current.visuals.withDismissAction
            ) ?: if (current.visuals.duration == SnackbarDuration.Long) 6_000L else 4_000L
            delay(timeout)
            current.dismiss()
        }
    }
    AnimatedContent(current, modifier, transitionSpec = {
        (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())).using(null)
    }, label = "Snackbar") { data ->
        if (data == null) return@AnimatedContent
        val label = data.visuals.actionLabel
        val action: (@Composable () -> Unit)? = label?.let {
            {
                TextButton(
                    onClick = data::performAction,
                    
                    colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor)
                , shapes = ButtonDefaults.shapes()) { Text(it) }
            }
        }
        val dismiss: (@Composable () -> Unit)? = if (data.visuals.withDismissAction) {
            { ActionIconButton(AppStrings.get(R.string.control_dismiss_message, "关闭提示"), onClick = data::dismiss) { Icon(Icons.Rounded.Close, AppStrings.get(R.string.control_dismiss_message, "关闭提示")) } }
        } else null
        BoxWithConstraints {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val messageStyle = MaterialTheme.typography.bodyMedium
        val actionStyle = MaterialTheme.typography.labelLarge
        val longestWord = data.visuals.message.split(Regex("\\s+"))
            .maxOfOrNull { measurer.measure(AnnotatedString(it), messageStyle).size.width } ?: 0
        val actionWidth = label?.let { measurer.measure(AnnotatedString(it), actionStyle).size.width } ?: 0
        val stackAction = label != null && with(density) {
            (longestWord + actionWidth).toDp() + 88.dp + (if (dismiss != null) 48.dp else 0.dp) > maxWidth
        }
        Snackbar(
            modifier = Modifier.padding(12.dp).semantics {
                liveRegion = LiveRegionMode.Polite
                dismiss { data.dismiss(); true }
            },
            
            action = action,
            actionOnNewLine = stackAction,
            dismissAction = dismiss
        ) { Text(data.visuals.message) }
        }
    }
}
