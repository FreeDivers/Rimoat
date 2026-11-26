@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package org.qinyan.rimoat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateDpAsState
import org.qinyan.rimoat.ui.motion.AppMotion
import org.qinyan.rimoat.ui.theme.ExpressiveSurface

/**
 * Role-aware MD3 Expressive top bar:
 * - Browsing & management screens in portrait use a collapsible [MediumFlexibleTopAppBar].
 * - Landscape two-pane layouts automatically stay compact to preserve vertical workspace height.
 */
@Composable
fun ExpressivePageTopBar(
    title: String,
    wide: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = ExpressiveSurface.canvas,
        scrolledContainerColor = ExpressiveSurface.scrolledTopBar
    )
    if (wide) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics {
                        heading()
                        contentDescription = title
                    }
                )
            },
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior
        )
    } else {
        MediumFlexibleTopAppBar(
            title = {
                Text(
                    text = title,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics {
                        heading()
                        contentDescription = title
                    }
                )
            },
            subtitle = subtitle?.let { sub ->
                {
                    Text(
                        text = sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior
        )
    }
}

/** Compact top bar for active remote control and direct manipulation workspaces. */
@Composable
fun ExpressiveCompactTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics {
                    heading()
                    contentDescription = title
                }
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ExpressiveSurface.canvas,
            scrolledContainerColor = ExpressiveSurface.scrolledTopBar
        )
    )
}

/** Consistent segmented card wrapper used across list, settings, room, and trash rows. */
@Composable
fun ExpressiveSegmentedSurface(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    containerColor: Color = ExpressiveSurface.card,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    outerRadius: Dp = ExpressiveShapeTokens.SegmentedOuterRadius,
    innerRadius: Dp = ExpressiveShapeTokens.SegmentedInnerRadius,
    content: @Composable () -> Unit
) {
    val shape: Shape = if (interactionSource != null) {
        rememberSegmentedExpressiveShape(interactionSource, index, count, outerRadius, innerRadius)
    } else {
        segmentedItemShape(index, count, outerRadius, innerRadius)
    }
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
        content = content
    )
}

/**
 * Cohesive segmented single-choice row without ToggleButton's pill-to-square flip.
 * Preserves [Role.RadioButton] and [selected] semantics for accessibility and tests.
 */
@Composable
fun ExpressiveSingleChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    count: Int = 1,
    description: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val motion = AppMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberSegmentedExpressiveShape(interactionSource, index, count)
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else ExpressiveSurface.card,
        animationSpec = motion.fastEffectsSpec(),
        label = "ChoiceContainer"
    )
    val contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface
    val supportingColor = if (selected) colors.onPrimaryContainer.copy(alpha = 0.85f) else colors.onSurfaceVariant
    val iconContainer by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.surfaceContainerHigh,
        animationSpec = motion.fastEffectsSpec(),
        label = "ChoiceIconBadge"
    )
    val iconTint = if (selected) colors.onPrimary else colors.onSurfaceVariant

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
        shape = shape,
        color = containerColor,
        contentColor = contentColor
    ) {
        ListItem(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = ripple(),
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick
                ),
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            },
            supportingContent = description?.let {
                { Text(it, style = MaterialTheme.typography.bodyMedium, color = supportingColor) }
            },
            leadingContent = icon?.let {
                {
                    Surface(
                        shape = CircleShape,
                        color = iconContainer,
                        contentColor = iconTint,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(it, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            },
            trailingContent = if (selected) {
                {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else null,
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = contentColor,
                supportingColor = supportingColor
            )
        )
    }
}

/**
 * Centered pill track switcher for dual-page views (Controls / More, Test / All buttons).
 * Animated indicator moves smoothly with [AppMotion] springs while preserving [Role.Tab] semantics.
 */
@Composable
fun ExpressivePageSwitcher(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (labels.size != 2) return
    val motion = AppMotion.current
    val colors = MaterialTheme.colorScheme
    val trackColor = colors.surfaceContainerHighest

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = trackColor,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().padding(4.dp)
            ) {
                val segmentWidth = maxWidth / 2
                val indicatorOffset by animateDpAsState(
                    targetValue = if (selectedIndex == 1) segmentWidth else 0.dp,
                    animationSpec = motion.defaultSpatialSpec(),
                    label = "PageSwitcherOffset"
                )
                Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Surface(
                        shape = CircleShape,
                        color = colors.primary,
                        modifier = Modifier
                            .offset(x = indicatorOffset)
                            .width(segmentWidth)
                            .fillMaxHeight()
                    ) {}
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        labels.forEachIndexed { index, label ->
                            val isSelected = selectedIndex == index
                            val textColor by animateColorAsState(
                                targetValue = if (isSelected) colors.onPrimary else colors.onSurfaceVariant,
                                animationSpec = motion.fastEffectsSpec(),
                                label = "PageSwitcherText"
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .heightIn(min = 48.dp)
                                    .clip(CircleShape)
                                    .semantics {
                                        selected = isSelected
                                        role = Role.Tab
                                    }
                                    .clickable(role = Role.Tab) { onSelect(index) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = textColor,
                                    softWrap = true,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
