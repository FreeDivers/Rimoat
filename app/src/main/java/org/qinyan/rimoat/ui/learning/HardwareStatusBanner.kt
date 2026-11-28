package org.qinyan.rimoat.ui.learning

import org.qinyan.rimoat.R

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import org.qinyan.rimoat.hardware.learning.LearnerType
import org.qinyan.rimoat.ui.HapticHelper
import org.qinyan.rimoat.ui.components.ActionIconButton
import org.qinyan.rimoat.ui.components.ExpressiveDropdownMenu
import org.qinyan.rimoat.ui.components.ExpressiveMenuItem
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import org.qinyan.rimoat.ui.motion.AppMotion

/**
 * Keeps receiver status separate from capture status and recording actions.
 * Simulation controls stay in a local menu; automatic emulator mode has no ineffective exit.
 */
@Composable
fun HardwareStatusBanner(
    learnerType: LearnerType,
    isAvailable: Boolean,
    onEnableMockMode: () -> Unit,
    modifier: Modifier = Modifier,
    receiveConfirmed: Boolean = false,
    onDisableMockMode: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val unavailable = learnerType == LearnerType.NONE || !isAvailable
    val active = LocalMotionActive.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(active, enabled) { if (!active || !enabled) expanded = false }
    val containerColor = if (unavailable) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
    Surface(modifier.fillMaxWidth().animateContentSize(AppMotion.current.defaultSpatialSpec()), shape = MaterialTheme.shapes.large,
        color = containerColor) {
        Row(Modifier.padding(start = if (unavailable) 12.dp else 4.dp, end = 4.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(when {
            unavailable -> Icons.Rounded.SensorsOff
            learnerType == LearnerType.MOCK -> Icons.Rounded.Science
            else -> Icons.Rounded.Sensors
        }, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        val statusText = when {
            unavailable -> stringResource(R.string.screen_no_receiver)
            learnerType == LearnerType.MOCK -> stringResource(R.string.screen_demo_mode)
            learnerType == LearnerType.USB_TIQIAA -> stringResource(R.string.control_tiqiaa)
            learnerType == LearnerType.USB_ELKSMART -> stringResource(R.string.control_elksmart)
            else -> stringResource(R.string.screen_builtin_receiver)
        }
        Text(
            statusText,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (unavailable || (learnerType == LearnerType.MOCK && onDisableMockMode != null)) Box {
            ActionIconButton(stringResource(R.string.screen_receive_settings), { expanded = true }, enabled = enabled) {
                Icon(Icons.Rounded.MoreVert, contentDescription = null)
            }
            ExpressiveDropdownMenu(
                expanded = active && expanded,
                onDismissRequest = { expanded = false }
            ) {
                ExpressiveMenuItem(
                    onClick = {
                        HapticHelper.performSelection(haptic, context)
                        expanded = false
                        if (unavailable) onEnableMockMode() else onDisableMockMode?.invoke()
                    },
                    text = { Text(stringResource(if (unavailable) R.string.screen_demo_mode else R.string.screen_exit_demo)) },
                    index = 0,
                    count = 1
                )
            }
        }
        }
    }
}
