package org.qinyan.rimoat.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

// Keep viable layouts below the Host's allocation. Some launchers report a larger
// options rectangle than the content view they actually draw (including their own title).
internal val WidgetLayoutSizes = setOf(
    DpSize(128.dp, 64.dp), DpSize(280.dp, 64.dp),
    // Two 48dp controls + 8dp gap + 24dp content padding fit at 128dp.
    // Keep a vertical layout below 160dp so a slightly narrower 2x2 host
    // does not fall all the way back to the single-row 2x1 layout.
    DpSize(128.dp, 144.dp), DpSize(128.dp, 240.dp),
    // Canonical two-column formats. 152x168 is what the configuration preview
    // renders and what a 2x2 content box of roughly 158-180 x 168-210dp selects.
    DpSize(152.dp, 168.dp), DpSize(152.dp, 208.dp), DpSize(152.dp, 240.dp),
    DpSize(160.dp, 144.dp), DpSize(224.dp, 144.dp), DpSize(280.dp, 144.dp),
    DpSize(160.dp, 240.dp), DpSize(224.dp, 240.dp), DpSize(280.dp, 240.dp),
    DpSize(280.dp, 320.dp)
)

/**
 * Canvas size the configuration preview renders, in dp.
 *
 * Launcher allocations can exceed the content area available to RemoteViews.
 * Select a canonical [WidgetLayoutSizes] entry so the preview uses an existing
 * responsive layout bucket.
 */
internal fun canonicalPreviewSize(widthDp: Int, heightDp: Int): DpSize {
    val width = widthDp.coerceAtLeast(1)
    val height = heightDp.coerceAtLeast(1)
    return when {
        width >= 280 && height < 144 -> DpSize(280.dp, 64.dp)   // 4x1
        width >= 280 && height < 240 -> DpSize(280.dp, 144.dp)  // 4x2
        width >= 280 && height < 360 -> DpSize(280.dp, 240.dp)  // 4x3
        width >= 280 -> DpSize(280.dp, 320.dp)                  // 4x4
        width >= 224 && height < 144 -> DpSize(128.dp, 64.dp)   // 3x1 uses compact
        width >= 224 && height < 240 -> DpSize(224.dp, 144.dp)  // 3x2
        width >= 224 -> DpSize(224.dp, 240.dp)                  // 3x3+
        height < 144 -> DpSize(128.dp, 64.dp)                   // 2x1
        height < 240 -> DpSize(152.dp, 168.dp)                  // canonical 2x2
        else -> DpSize(152.dp, 240.dp)                          // 2x3+
    }
}
