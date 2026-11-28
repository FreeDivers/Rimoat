@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package org.qinyan.rimoat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.ui.motion.AppMotion
import org.qinyan.rimoat.ui.theme.ExpressiveSurface

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
