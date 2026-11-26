@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SelectableMenuItemColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.SoundEffectOnInteraction
import androidx.compose.ui.unit.DpOffset

private val LocalMenuInteractive = compositionLocalOf { true }

/**
 * MD3 Expressive segmented menu popup host that silences platform click sounds inside
 * the new popup window and freezes item interaction during the exit transition.
 */
@Composable
fun ExpressiveDropdownMenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    anchorPosition: MenuAnchorPosition = MenuAnchorPosition.Below,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(
            dropdownMenuAnchorPosition = anchorPosition,
            offset = offset
        )
    ) {
        CompositionLocalProvider(LocalMenuInteractive provides expanded) {
            SoundEffectOnInteraction(enabled = false) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MenuDefaults.GroupSpacing),
                    content = content
                )
            }
        }
    }
}

/**
 * Convenience single-group MD3 Expressive dropdown menu with vertical scroll support.
 */
@Composable
fun ExpressiveDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    anchorPosition: MenuAnchorPosition = MenuAnchorPosition.Below,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    selectable: Boolean = false,
    containerColor: Color = MenuDefaults.groupStandardContainerColor,
    content: @Composable ColumnScope.() -> Unit
) {
    ExpressiveDropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        anchorPosition = anchorPosition,
        offset = offset
    ) {
        ExpressiveDropdownMenuGroup(
            index = 0,
            count = 1,
            containerColor = containerColor,
            contentModifier = Modifier
                .verticalScroll(scrollState)
                .let { if (selectable) it.selectableGroup() else it },
            content = content
        )
    }
}

/**
 * Segmented container group inside an [ExpressiveDropdownMenuPopup], applying MD3E
 * group corner radii and standard spacing between items.
 */
@Composable
fun ExpressiveDropdownMenuGroup(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    containerColor: Color = MenuDefaults.groupStandardContainerColor,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenuGroup(
        shapes = MenuDefaults.groupShape(index, count),
        modifier = modifier,
        containerColor = containerColor
    ) {
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(MenuDefaults.GroupSpacing),
            content = content
        )
    }
}

/**
 * MD3 Expressive selectable menu item with segmented shape morphing, tertiary container
 * selection highlight, and leading check indicator.
 */
@Composable
fun ExpressiveSelectableMenuItem(
    selected: Boolean,
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    selectedLeadingIcon: (@Composable () -> Unit)? = {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            modifier = Modifier.size(MenuDefaults.LeadingIconSize)
        )
    },
    trailingContent: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SelectableMenuItemColors = MenuDefaults.selectableItemColors(),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuSelectableItemContentPadding,
    interactionSource: MutableInteractionSource? = null
) {
    val interactive = LocalMenuInteractive.current
    // Preserve active colors while ignoring repeated taps during the exit transition.
    val effectiveColors = if (!interactive && enabled) {
        colors.copy(
            disabledTextColor = if (selected) colors.selectedTextColor else colors.textColor,
            disabledContainerColor = if (selected) colors.selectedContainerColor else colors.containerColor,
            disabledLeadingIconColor = if (selected) colors.selectedLeadingIconColor else colors.leadingIconColor,
            disabledTrailingContentColor = if (selected) colors.selectedTrailingContentColor else colors.trailingContentColor
        )
    } else {
        colors
    }
    SelectableDropdownMenuItem(
        selected = selected,
        onClick = onClick,
        text = text,
        shapes = MenuDefaults.itemShape(index, count),
        modifier = modifier,
        leadingIcon = leadingIcon,
        selectedLeadingIcon = selectedLeadingIcon,
        trailingContent = trailingContent,
        supportingText = supportingText,
        enabled = enabled && interactive,
        colors = effectiveColors,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    )
}

/**
 * MD3 Expressive action menu item with segmented group item shape.
 */
@Composable
fun ExpressiveMenuItem(
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource? = null
) {
    val interactive = LocalMenuInteractive.current
    val effectiveColors = if (!interactive && enabled) {
        MenuDefaults.itemColors(
            textColor = colors.textColor,
            leadingIconColor = colors.leadingIconColor,
            trailingIconColor = colors.trailingIconColor,
            disabledTextColor = colors.textColor,
            disabledLeadingIconColor = colors.leadingIconColor,
            disabledTrailingIconColor = colors.trailingIconColor
        )
    } else {
        colors
    }
    DropdownMenuItem(
        onClick = onClick,
        text = text,
        shape = MenuDefaults.itemShape(index, count).shape,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingContent = trailingContent,
        supportingText = supportingText,
        enabled = enabled && interactive,
        colors = effectiveColors,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    )
}
