package org.qinyan.rimoat.ui.learning

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.ui.motion.AppMotion
import org.qinyan.rimoat.ui.theme.ExpressiveSurface

/** Keep both actions readable when translations or accessibility text need more space. */
@Composable
internal fun LearningActionBar(
    primaryLabel: String,
    secondaryLabel: String? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = ExpressiveSurface.card,
    secondary: @Composable () -> Unit,
    primary: @Composable (Modifier) -> Unit
) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val primaryWidth = measurer.measure(primaryLabel, style, softWrap = false).size.width
    val secondaryWidth = secondaryLabel?.let { measurer.measure(it, style, softWrap = false).size.width }
    val minimumWidth = with(LocalDensity.current) {
        primaryWidth.toDp() + (secondaryWidth?.toDp()?.plus(32.dp) ?: 56.dp) + 64.dp
    }
    val motion = AppMotion.current
    val contentPadding = if (containerColor == Color.Transparent) 0.dp else 12.dp
    Surface(
        modifier = modifier.animateContentSize(motion.defaultSpatialSpec()),
        shape = RoundedCornerShape(24.dp),
        color = containerColor
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(contentPadding)) {
            if (maxWidth >= minimumWidth) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    secondary()
                    Spacer(Modifier.width(10.dp))
                    primary(Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) { secondary() }
                    primary(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
