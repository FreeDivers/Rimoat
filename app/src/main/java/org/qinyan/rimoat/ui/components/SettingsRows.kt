@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.components

import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import org.qinyan.rimoat.ui.components.ExpressiveSegmentedSurface
import android.app.Activity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import java.text.NumberFormat
import org.qinyan.rimoat.ui.viewmodel.AppUpdateState
import org.qinyan.rimoat.ui.viewmodel.UpdatePhase
import org.qinyan.rimoat.R
import org.qinyan.rimoat.ui.components.ExpressiveDropdownMenu
import org.qinyan.rimoat.ui.components.ExpressiveSelectableMenuItem
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import org.qinyan.rimoat.ui.theme.Spacing

@Composable
internal fun SettingsChoiceAction(
    title: String,
    icon: ImageVector,
    value: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    optionEnabled: (Int) -> Boolean = { true },
    modifier: Modifier = Modifier,
    index: Int = 0,
    count: Int = 1,
    segmented: Boolean = true,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menuPresent by remember { mutableStateOf(false) }
    var pendingSelection by remember { mutableStateOf<Int?>(null) }
    val currentOnSelected by rememberUpdatedState(onSelected)
    // Locale changes recreate the Activity. Release the popup window's focus first.
    LaunchedEffect(pendingSelection, menuPresent) {
        if (!menuPresent) pendingSelection?.let { index ->
            pendingSelection = null
            currentOnSelected(index)
        }
    }
    val active = LocalMotionActive.current
    LaunchedEffect(active) { if (!active) expanded = false }

    SettingsValueRow(
        title = title,
        icon = icon,
        value = value,
        trailingIcon = Icons.Rounded.ExpandMore,
        trailingIconSize = 20.dp,
        enabled = active,
        onClick = { expanded = true },
        modifier = modifier,
        index = index,
        count = count,
        segmented = segmented,
    ) {
        Box(Modifier.size(width = 24.dp, height = 48.dp)) {
            ExpressiveDropdownMenu(
                expanded = expanded && active,
                onDismissRequest = { expanded = false },
                selectable = true
            ) {
                DisposableEffect(Unit) {
                    menuPresent = true
                    onDispose { menuPresent = false }
                }
                options.forEachIndexed { index, option ->
                    val isSelected = index == selectedIndex
                    ExpressiveSelectableMenuItem(
                        selected = isSelected,
                        onClick = {
                            expanded = false
                            if (!isSelected) pendingSelection = index
                        },
                        text = { Text(option) },
                        index = index,
                        count = options.size,
                        enabled = optionEnabled(index)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsValueRow(
    title: String,
    icon: ImageVector,
    value: String,
    trailingIcon: ImageVector,
    trailingIconSize: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    count: Int = 1,
    segmented: Boolean = true,
    trailingOverlay: @Composable BoxScope.() -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.bodyLarge
    val valueStyle = MaterialTheme.typography.bodyMedium
    // The narrowest legal wrap preserves complete words (and uses the language's
    // own break opportunities for scripts such as Japanese).
    val titleMinimum = with(density) {
        measurer.measure(title, titleStyle).multiParagraph.minIntrinsicWidth.toDp() + 1.dp
    }
    val valueMinimum = with(density) {
        measurer.measure(value, valueStyle).multiParagraph.minIntrinsicWidth.toDp() + 1.dp
    }
    val trailingWidth = 4.dp + trailingIconSize

    val rowContent: @Composable () -> Unit = {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
            .semantics { stateDescription = value }
            .clickable(interactionSource = interactionSource, indication = ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp),
    ) {
        if (maxWidth < 300.dp || titleMinimum + valueMinimum > maxWidth - 84.dp - trailingWidth) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(24.dp), tint = secondaryColor)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = titleStyle)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(value, Modifier.weight(1f), color = secondaryColor, style = valueStyle)
                            Spacer(Modifier.width(4.dp))
                            Icon(trailingIcon, null, Modifier.size(trailingIconSize), tint = secondaryColor)
                        }
                        trailingOverlay()
                    }
                }
            }
            return@BoxWithConstraints
        }
        // Recover inert space before splitting a word at large font scales. The
        // whole row remains the touch target; neither text column moves below the other.
        val minimumTextWidth = titleMinimum + valueMinimum
        val compact = minimumTextWidth > maxWidth - (84.dp + trailingWidth)
        val horizontalPadding = when {
            !compact -> 16.dp
            minimumTextWidth <= maxWidth - (56.dp + trailingWidth) -> 8.dp
            else -> 4.dp
        }
        val iconGap = if (compact) 8.dp else 16.dp
        val textGap = if (compact) 8.dp else 12.dp
        val textWidth = (maxWidth - horizontalPadding * 2 - 24.dp - iconGap - textGap - trailingWidth)
            .coerceAtLeast(2.dp)
        val preferredTitleWidth = textWidth / 2.15f
        val titleWidth = if (titleMinimum + valueMinimum <= textWidth) {
            preferredTitleWidth.coerceIn(titleMinimum, textWidth - valueMinimum)
        } else {
            // At extreme widths where both words cannot fit, share the available
            // width in proportion to their needs while keeping all text visible.
            textWidth * (titleMinimum.value / (titleMinimum + valueMinimum).value.coerceAtLeast(1f))
        }
        val valueWidth = textWidth - titleWidth + trailingWidth

        Row(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp), tint = secondaryColor)
            Spacer(Modifier.width(iconGap))
            Text(title, Modifier.width(titleWidth), style = titleStyle)
            Spacer(Modifier.width(textGap))
            // This box anchors an optional trailing overlay (such as a dropdown menu).
            Box(Modifier.width(valueWidth), contentAlignment = Alignment.CenterEnd) {
                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(value, Modifier.weight(1f, fill = false), color = secondaryColor,
                        textAlign = TextAlign.End, style = valueStyle)
                    Spacer(Modifier.width(4.dp))
                    Icon(trailingIcon, null, Modifier.size(trailingIconSize), tint = secondaryColor)
                }
                trailingOverlay()
            }
        }
    }
    }
    if (segmented) {
        ExpressiveSegmentedSurface(
            index = index,
            count = count,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth()
        ) {
            rowContent()
        }
    } else {
        rowContent()
    }
}

@Composable
internal fun UpdateSettingsRow(
    updateState: AppUpdateState,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    if (!updateState.minimized || updateState.phase == UpdatePhase.CLOSED) {
        SettingsAction(
            title = stringResource(R.string.update_check),
            icon = Icons.Rounded.SystemUpdate,
            index = index,
            count = count,
            onClick = onClick,
        )
        return
    }
    val interactionSource = remember { MutableInteractionSource() }
    val locale = LocalConfiguration.current.locales[0]
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val title = stringResource(R.string.update_check)
    val titleStyle = MaterialTheme.typography.bodyLarge
    val statusStyle = MaterialTheme.typography.labelLarge
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fraction = if (updateState.totalBytes > 0) {
        (updateState.downloadedBytes.toDouble() / updateState.totalBytes).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }
    val statusText = when (updateState.phase) {
        UpdatePhase.DOWNLOADING -> NumberFormat.getPercentInstance(locale).format(fraction.toDouble())
        UpdatePhase.READY, UpdatePhase.DOWNLOADED, UpdatePhase.PERMISSION -> stringResource(R.string.update_ready_badge)
        UpdatePhase.ERROR -> stringResource(R.string.update_failed_badge)
        else -> ""
    }
    val statusColor = if (updateState.phase == UpdatePhase.ERROR) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val titleMinimum = with(density) {
        measurer.measure(title, titleStyle).multiParagraph.minIntrinsicWidth.toDp() + 1.dp
    }
    val statusMinimum = with(density) {
        measurer.measure(statusText, statusStyle).multiParagraph.minIntrinsicWidth.toDp() + 1.dp
    }
    val progressWidth = if (updateState.phase == UpdatePhase.DOWNLOADING) 56.dp + Spacing.small else 0.dp
    val trailingControlWidth = progressWidth + 24.dp + Spacing.small
    ExpressiveSegmentedSurface(
        index = index,
        count = count,
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics { if (statusText.isNotEmpty()) stateDescription = statusText }
                .heightIn(min = 64.dp),
        ) {
            if (maxWidth < 300.dp || titleMinimum + statusMinimum + trailingControlWidth > maxWidth - 84.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(24.dp), tint = secondaryColor)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                        Text(title, style = titleStyle, color = MaterialTheme.colorScheme.onSurface)
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        ) {
                            if (updateState.phase == UpdatePhase.DOWNLOADING) {
                                LinearWavyProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier.weight(1f).testTag("settings_update_progress"),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            if (statusText.isNotEmpty()) {
                                Text(statusText, style = statusStyle, color = statusColor)
                            }
                            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(24.dp), tint = secondaryColor)
                        }
                    }
                }
            } else {
                ListItem(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    headlineContent = { Text(title) },
                    leadingContent = { Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(24.dp)) },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        ) {
                            if (statusText.isNotEmpty()) {
                                Text(statusText, style = statusStyle, color = statusColor)
                            }
                            if (updateState.phase == UpdatePhase.DOWNLOADING) {
                                LinearWavyProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier.width(56.dp).testTag("settings_update_progress"),
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(24.dp))
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        headlineColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = secondaryColor,
                        trailingIconColor = secondaryColor,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun SettingsAction(
    title: String,
    icon: ImageVector,
    enabled: Boolean = true,
    index: Int = 0,
    count: Int = 1,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = MaterialTheme.colorScheme.onSurface.let { if (enabled) it else it.copy(alpha = 0.38f) }
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant.let { if (enabled) it else it.copy(alpha = 0.38f) }
    ExpressiveSegmentedSurface(
        index = index,
        count = count,
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            modifier = Modifier
                .clickable(interactionSource = interactionSource, indication = ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
                .heightIn(min = 64.dp),
            headlineContent = { Text(title) },
            leadingContent = { Icon(icon, null, Modifier.size(24.dp)) },
            trailingContent = { Icon(Icons.Rounded.ChevronRight, null, Modifier.size(24.dp)) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent, headlineColor = contentColor, leadingIconColor = iconColor, trailingIconColor = iconColor)
        )
    }
}
