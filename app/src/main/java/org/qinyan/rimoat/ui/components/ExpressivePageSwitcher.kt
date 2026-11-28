@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package org.qinyan.rimoat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateDpAsState
import org.qinyan.rimoat.ui.motion.AppMotion

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
