package org.qinyan.rimoat.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.disabled
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        content = content
    )
}

/** Observes a remote key press without consuming the gesture used by continuous transmission. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldTooltip(label: String, pressed: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberTooltipState()
    LaunchedEffect(pressed) {
        if (pressed) { delay(450); state.show() } else state.dismiss()
    }
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } }, state = state, enableUserInput = false, focusable = false,
        modifier = modifier, content = content)
}

@Composable
fun ActionIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    ActionTooltip(label) {
        IconButton(onClick = onClick, modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clearAndSetSemantics {
            contentDescription = label
            role = Role.Button
            if (enabled) onClick { onClick(); true } else disabled()
        }, enabled = enabled) { Box(Modifier.clearAndSetSemantics {}) { content() } }
    }
}
