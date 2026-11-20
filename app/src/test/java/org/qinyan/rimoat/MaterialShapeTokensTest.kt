package org.qinyan.rimoat

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.qinyan.rimoat.ui.theme.Shapes

class MaterialShapeTokensTest {
    @Test
    fun themeUsesOfficialMaterial3ShapeTokens() {
        val defaults = androidx.compose.material3.Shapes()
        assertEquals(RoundedCornerShape(4.dp), defaults.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), defaults.small)
        assertEquals(RoundedCornerShape(12.dp), defaults.medium)
        assertEquals(RoundedCornerShape(16.dp), defaults.large)
        assertEquals(RoundedCornerShape(28.dp), defaults.extraLarge)

        assertEquals(defaults.extraSmall, Shapes.extraSmall)
        assertEquals(defaults.small, Shapes.small)
        assertEquals(defaults.medium, Shapes.medium)
        assertEquals(defaults.large, Shapes.large)
        assertEquals(defaults.extraLarge, Shapes.extraLarge)
    }
}
