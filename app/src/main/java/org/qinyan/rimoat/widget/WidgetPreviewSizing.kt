package org.qinyan.rimoat.widget

import androidx.compose.ui.unit.DpSize

/**
 * Canvas size for the configuration preview.
 *
 * Launcher allocations can include title strips outside the widget content view.
 * Multi-row previews use the app-owned canonical layout bucket; single-row
 * previews retain the reported allocation.
 */
internal fun previewCanvasSize(options: DpSize): DpSize =
    if (options.height.value < SINGLE_ROW_HEIGHT_DP) options
    else canonicalPreviewSize(options.width.value.toInt(), options.height.value.toInt())

private const val SINGLE_ROW_HEIGHT_DP = 120f
