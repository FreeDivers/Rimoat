package org.qinyan.rimoat

import org.qinyan.rimoat.ui.DisplayModeRate
import org.qinyan.rimoat.ui.preferredRefreshRate
import org.qinyan.rimoat.ui.preferredDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayRefreshRateTest {
    @Test fun modeRequestPreservesResolutionAndResetsForPowerSaving() {
        val modes = listOf(
            DisplayModeRate(1368, 3192, 60f, 3),
            DisplayModeRate(1368, 3192, 120.00001f, 1),
            DisplayModeRate(912, 2128, 144f, 4)
        )
        assertEquals(modes[1], preferredDisplayMode(1368, 3192, modes, false))
        assertEquals(null, preferredDisplayMode(1368, 3192, modes, true))
        assertEquals(null, preferredDisplayMode(100, 100, modes, false))
    }

    @Test fun selectsHighRateAtCurrentResolution() {
        val modes = listOf(60f, 90f, 120f).map { DisplayModeRate(1440, 3200, it) } +
            DisplayModeRate(1080, 2400, 144f)
        assertEquals(120f, preferredRefreshRate(1440, 3200, modes, false), 0f)
    }

    @Test fun respectsPowerSavingAndMissingModes() {
        val modes = listOf(DisplayModeRate(1440, 3200, 120f))
        assertEquals(0f, preferredRefreshRate(1440, 3200, modes, true), 0f)
        assertEquals(0f, preferredRefreshRate(1080, 2400, modes, false), 0f)
    }

    @Test fun supportsSixtyHertzAndFractionalRatesWithoutRounding() {
        val modes = listOf(59.94f, Float.NaN, Float.POSITIVE_INFINITY, -1f)
            .map { DisplayModeRate(1080, 2400, it) }
        assertEquals(59.94f, preferredRefreshRate(1080, 2400, modes, false), 0f)
    }
}
