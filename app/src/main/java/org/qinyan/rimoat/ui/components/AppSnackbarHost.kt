package org.qinyan.rimoat.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppError
import org.qinyan.rimoat.i18n.ErrorAction
import org.qinyan.rimoat.i18n.labelRes
import org.qinyan.rimoat.ui.motion.AppMotion

class AppErrorSnackbarVisuals(
    val error: AppError,
    override val message: String,
    override val actionLabel: String?,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Long
) : SnackbarVisuals

suspend fun SnackbarHostState.showAppError(
    context: Context,
    error: AppError,
    duration: SnackbarDuration = SnackbarDuration.Long,
    withDismissAction: Boolean = false
): SnackbarResult = showSnackbar(
    AppErrorSnackbarVisuals(
        error = error,
        message = error.message.resolve(context),
        actionLabel = error.action?.let { context.getString(it.labelRes) },
        withDismissAction = withDismissAction,
        duration = duration
    )
)

fun openStorageSettings(context: Context): Boolean {
    val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}

/** Nonlinear local fades, live-region semantics and accessibility-adjusted timeouts. */
@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onErrorAction: ((ErrorAction) -> Unit)? = null
) {
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
        val visuals = data.visuals
        val label = visuals.actionLabel
        val action: (@Composable () -> Unit)? = label?.let {
            {
                TextButton(
                    onClick = {
                        val errorAction = (visuals as? AppErrorSnackbarVisuals)?.error?.action
                        data.performAction()
                        if (errorAction != null) {
                            onErrorAction?.invoke(errorAction)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor),
                    shapes = ButtonDefaults.shapes()
                ) { Text(it) }
            }
        }
        val dismissLabel = stringResource(R.string.control_dismiss_message)
        val dismiss: (@Composable () -> Unit)? = if (visuals.withDismissAction) {
            {
                ActionIconButton(dismissLabel, onClick = data::dismiss) {
                    Icon(Icons.Rounded.Close, dismissLabel)
                }
            }
        } else null
        BoxWithConstraints {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val messageStyle = MaterialTheme.typography.bodyMedium
            val actionStyle = MaterialTheme.typography.labelLarge
            val longestWord = visuals.message.split(Regex("\\s+"))
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
            ) { Text(visuals.message) }
        }
    }
}
