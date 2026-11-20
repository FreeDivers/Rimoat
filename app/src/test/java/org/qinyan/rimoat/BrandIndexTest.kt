package org.qinyan.rimoat

import org.qinyan.rimoat.data.db.IrBrand
import org.qinyan.rimoat.ui.screens.brandHeaderPositions
import org.qinyan.rimoat.ui.screens.brandIndexAt
import org.qinyan.rimoat.ui.screens.groupBrandsByIndex
import org.junit.Assert.*
import org.junit.Test

class BrandIndexTest {
    private fun brand(id: Int, name: String, pinyin: String, english: String = "") =
        IrBrand(id, name, english, pinyin, 1)

    @Test fun englishAndJapaneseIndexUsesDisplayedLatinNameWithPinyinFallback() {
        val brands = listOf(brand(1, "松下", "songxia", "Panasonic"),
            brand(2, "海信", "haixin", "Hisense"), brand(3, "创维", "chuangwei", "Skyworth"),
            brand(4, "未命名品牌", "weimingming", ""))
        val localized = groupBrandsByIndex(brands, preferEnglish = true)
        assertEquals(listOf('H', 'P', 'S', 'W'), localized.map { it.letter })
        assertEquals(listOf(2, 1, 3, 4), localized.flatMap { it.brands }.map { it.brandIdx })
        assertEquals(listOf('C', 'H', 'S', 'W'), groupBrandsByIndex(brands).map { it.letter })
    }

    @Test fun chinesePinyinWinsOverEnglishAndFallbacksSortConsistently() {
        val brands = listOf(
            brand(1, "索尼", "suoni", "Sony"), brand(2, "长虹", "changhong", "Changhong"),
            brand(3, "格力", "géli", "Gree"), brand(4, "TCL", ""),
            brand(5, "未知", " ", "Acme"), brand(6, "3M", "3M"), brand(7, "???", ""),
            brand(8, "创维", "chuangwei"), brand(9, "松下", "songxia", "Panasonic"))
        val groups = groupBrandsByIndex(brands)
        assertEquals(listOf('A', 'C', 'G', 'S', 'T', '#'), groups.map { it.letter })
        assertEquals(listOf("长虹", "创维"), groups[1].brands.map { it.nameCn })
        assertEquals(listOf("松下", "索尼"), groups[3].brands.map { it.nameCn })
        assertEquals(brands.map { it.brandIdx }.toSet(), groups.flatMap { it.brands }.map { it.brandIdx }.toSet())
    }

    @Test fun headerTargetsIncludePopularEntryAndPreviousHeaders() {
        val groups = groupBrandsByIndex(listOf(brand(1, "A1", "a1"), brand(2, "A2", "a2"), brand(3, "Z", "z")))
        assertEquals(mapOf('A' to 1, 'Z' to 4), brandHeaderPositions(groups, 1))
        assertEquals(mapOf('A' to 0, 'Z' to 3), brandHeaderPositions(groups, 0))
        assertTrue(brandHeaderPositions(emptyList(), 1).isEmpty())
    }

    @Test fun dragPositionUsesUniformSlotsAndClampsOutsideTheRail() {
        assertEquals(0, brandIndexAt(-10f, 520, 26))
        assertEquals(12, brandIndexAt(250f, 520, 26))
        assertEquals(25, brandIndexAt(520f, 520, 26))
        assertEquals(25, brandIndexAt(1000f, 520, 26))
        assertEquals(0, brandIndexAt(0f, 0, 0))
        assertEquals(0, brandIndexAt(Float.NaN, 520, 26))
    }
}
