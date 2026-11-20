package org.qinyan.rimoat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppColorModeTest {
    @Test
    fun systemModeTracksSystemAppearance() {
        assertTrue(AppColorMode.SYSTEM.isDark(systemIsDark = true))
        assertFalse(AppColorMode.SYSTEM.isDark(systemIsDark = false))
    }

    @Test
    fun explicitModesIgnoreSystemAppearance() {
        assertFalse(AppColorMode.LIGHT.isDark(systemIsDark = true))
        assertFalse(AppColorMode.LIGHT.isDark(systemIsDark = false))
        assertTrue(AppColorMode.DARK.isDark(systemIsDark = true))
        assertTrue(AppColorMode.DARK.isDark(systemIsDark = false))
    }

    @Test
    fun invalidPreferenceFallsBackToSystem() {
        assertEquals(AppColorMode.SYSTEM, AppColorMode.fromPreference(null))
        assertEquals(AppColorMode.SYSTEM, AppColorMode.fromPreference("unknown"))
        assertEquals(AppColorMode.DARK, AppColorMode.fromPreference("DARK"))
    }
}
