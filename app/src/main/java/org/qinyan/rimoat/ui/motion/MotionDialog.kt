package org.qinyan.rimoat.ui.motion

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoundEffectOnInteraction
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import org.qinyan.rimoat.R
import org.qinyan.rimoat.ui.components.LocalExpressiveDialogSurface

/** Let Android animate the modal window so closing it can release Compose content immediately. */
@Composable
private fun DialogWindowMotion() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        if (window != null && window.attributes.windowAnimations != R.style.RimoatDialogAnimation) {
            window.setWindowAnimations(R.style.RimoatDialogAnimation)
        }
    }
}

@Composable
internal fun MotionAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            DialogWindowMotion()
            SoundEffectOnInteraction(enabled = false, content = confirmButton)
        },
        modifier = modifier,
        dismissButton = dismissButton?.let { content ->
            { SoundEffectOnInteraction(enabled = false, content = content) }
        },
        title = title?.let { content ->
            { SoundEffectOnInteraction(enabled = false, content = content) }
        },
        text = text?.let { content ->
            {
                CompositionLocalProvider(LocalExpressiveDialogSurface provides true) {
                    SoundEffectOnInteraction(enabled = false, content = content)
                }
            }
        },
        properties = properties
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MotionBasicAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    BasicAlertDialog(onDismissRequest, modifier, properties) {
        DialogWindowMotion()
        CompositionLocalProvider(LocalExpressiveDialogSurface provides true) {
            SoundEffectOnInteraction(enabled = false, content = content)
        }
    }
}
