package org.qinyan.rimoat.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.qinyan.rimoat.ui.motion.StateFade

/** A missing semantic icon is a text key, including multi-word custom names. */
@Composable
fun RemoteKeyGlyph(label: String, icon: ImageVector?, diameter: Dp = 64.dp, modifier: Modifier = Modifier) {
    if (icon != null) {
        StateFade(icon, modifier.size(28.dp)) {
            Icon(it, contentDescription = null, modifier = Modifier.size(28.dp))
        }
    } else if (label.length == 1 || label == "OK") {
        Text(label, style = MaterialTheme.typography.titleMedium,
            maxLines = 2, modifier = modifier.clearAndSetSemantics {})
    } else {
        Text(
            text = label,
            modifier = modifier.padding(horizontal = 4.dp).clearAndSetSemantics {},
            style = MaterialTheme.typography.titleMedium.copy(lineHeight = TextUnit.Unspecified),
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp,
                maxFontSize = (diameter.value / 4f).coerceIn(16f, 18f).sp, stepSize = 1.sp),
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}
