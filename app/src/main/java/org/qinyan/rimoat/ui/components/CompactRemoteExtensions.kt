package org.qinyan.rimoat.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Keep complete words readable without shrinking the circular touch targets. */
@Composable
internal fun extensionColumns(labels: List<String>, width: Dp): Int {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val minimum = labels.flatMap { it.split(Regex("\\s+")) }.maxOfOrNull {
        with(density) { measurer.measure(it, style).size.width.toDp().value }
    }?.coerceAtLeast(64f) ?: 64f
    return (3 downTo 2).firstOrNull { (width.value - (it - 1) * 12f) / it >= minimum + 8f } ?: 1
}

/** Conservative space budget: at most two rows, with full labels and the existing 64dp circles. */
internal fun canInlineExtensions(count: Int, width: Float, height: Float, mainHeight: Float, extraHeight: Float): Boolean =
    count in 1..6 && width >= 280f && mainHeight + extraHeight + 32f <= height &&
        extraHeight <= minOf(240f, height * .3f)

@Composable
internal fun compactExtensionsFit(
    rowGroups: List<List<String>>,
    width: Dp,
    height: Dp,
    extensionCount: Int = rowGroups.sumOf { it.size }
): Boolean {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val cellWidth = with(density) { ((width - 64.dp) / 3).roundToPx().coerceAtLeast(1) }
    fun labelHeight(text: String): Float = with(density) {
        measurer.measure(text, style, constraints = Constraints(maxWidth = cellWidth)).size.height.toDp().value
    }
    val nonEmptyGroups = rowGroups.filter { it.isNotEmpty() }
    val allLabels = nonEmptyGroups.flatten()
    val columns = if (allLabels.isEmpty()) 3 else extensionColumns(allLabels, width - 40.dp)
    val rows = nonEmptyGroups.flatMap { it.chunked(columns) }
    val extraHeight = if (rows.isEmpty()) 0f else {
        rows.sumOf { row ->
            (64f + 8f + (row.maxOfOrNull(::labelHeight) ?: 20f)).toDouble()
        }.toFloat() + (rows.size - 1) * 12f + (nonEmptyGroups.size - 1).coerceAtLeast(0) * 8f * density.fontScale
    }
    // RemoteControlScreen hosts panels inside a scrollable LazyColumn, so inlining depends on
    // the extension count and extra height rather than forcing all regular controls into one viewport.
    return canInlineExtensions(extensionCount, width.value, height.value, 0f, extraHeight)
}
