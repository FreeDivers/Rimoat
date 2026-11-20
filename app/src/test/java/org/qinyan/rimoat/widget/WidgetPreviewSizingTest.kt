package org.qinyan.rimoat.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that launcher allocations and smaller content boxes select the same
 * canonical preview format while reported dimensions remain available elsewhere.
 */
class WidgetPreviewSizingTest {

    private fun assertSize(expectedWidth: Int, expectedHeight: Int, actual: DpSize) {
        assertEquals(expectedWidth.toFloat(), actual.width.value, 0.001f)
        assertEquals(expectedHeight.toFloat(), actual.height.value, 0.001f)
    }

    @Test
    fun measuredTwoByTwoBoxesUseTheCanonicalFormat() {
        assertSize(152, 168, canonicalPreviewSize(162, 209)) // Allocation with reserved launcher chrome
        assertSize(152, 168, canonicalPreviewSize(163, 210)) // Allocation without reserved launcher chrome
        assertSize(152, 168, canonicalPreviewSize(158, 171)) // measured content box
        assertSize(152, 168, canonicalPreviewSize(179, 210)) // AOSP 2x2
    }

    @Test
    fun twoColumnHeightThresholds() {
        assertSize(128, 64, canonicalPreviewSize(162, 80))   // 2x1
        assertSize(128, 64, canonicalPreviewSize(162, 143))
        assertSize(152, 168, canonicalPreviewSize(162, 144))
        assertSize(152, 168, canonicalPreviewSize(162, 239))
        assertSize(152, 240, canonicalPreviewSize(162, 240))
        assertSize(152, 240, canonicalPreviewSize(162, 400))
    }

    @Test
    fun wideFormats() {
        assertSize(280, 64, canonicalPreviewSize(368, 80))
        assertSize(280, 144, canonicalPreviewSize(368, 210))
        assertSize(280, 240, canonicalPreviewSize(368, 300))
        assertSize(280, 320, canonicalPreviewSize(368, 420))
    }

    @Test
    fun threeColumnFormats() {
        assertSize(128, 64, canonicalPreviewSize(224, 80))
        assertSize(224, 144, canonicalPreviewSize(224, 144))
        assertSize(224, 240, canonicalPreviewSize(224, 250))
    }

    @Test
    fun degenerateOptionsStayUsable() {
        assertSize(128, 64, canonicalPreviewSize(0, 0))
        assertSize(128, 64, canonicalPreviewSize(-10, -10))
    }

    @Test
    fun everyCanonicalSizeIsAResponsiveBucket() {
        val inputs = listOf(
            162 to 209, 179 to 210, 162 to 80, 162 to 144, 162 to 240,
            368 to 80, 368 to 210, 368 to 300, 368 to 420,
            224 to 144, 224 to 250, 0 to 0
        )
        for ((width, height) in inputs) {
            val size = canonicalPreviewSize(width, height)
            assertTrue("$width x $height -> $size is not a bucket", size in WidgetLayoutSizes)
        }
    }

    @Test
    fun multiRowWidgetsUseTheCanonicalFormat() {
        assertSize(152, 168, previewCanvasSize(DpSize(162.85715.dp, 209.71428.dp)))
        assertSize(152, 168, previewCanvasSize(DpSize(179.dp, 210.dp)))
        assertSize(280, 144, previewCanvasSize(DpSize(368.57144.dp, 144.dp)))
    }

    @Test
    fun singleRowWidgetsKeepTheReportedOptions() {
        val twoByOne = DpSize(162.85715.dp, 80.57143.dp)
        assertEquals(twoByOne, previewCanvasSize(twoByOne))
        val fourByOne = DpSize(368.57144.dp, 80.57143.dp)
        assertEquals(fourByOne, previewCanvasSize(fourByOne))
    }
}
