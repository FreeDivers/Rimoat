package org.qinyan.rimoat.ui.learning

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.ui.motion.AppMotion

/** Keep both actions readable when translations or accessibility text need more space. */
@Composable
internal fun LearningActionBar(
    primaryLabel: String,
    secondaryLabel: String? = null,
    modifier: Modifier = Modifier,
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
    BoxWithConstraints(modifier.animateContentSize(motion.defaultSpatialSpec())) {
        if (maxWidth >= minimumWidth) {
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(8.dp)
            ) {
                secondary()
                Spacer(Modifier.width(8.dp))
                primary(Modifier.weight(1f))
            }
        } else {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) { secondary() }
                    primary(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
