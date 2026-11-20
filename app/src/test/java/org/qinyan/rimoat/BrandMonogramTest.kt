package org.qinyan.rimoat

import org.qinyan.rimoat.data.db.IrBrand
import org.qinyan.rimoat.ui.screens.brandMonogram
import org.junit.Assert.assertEquals
import org.junit.Test

class BrandMonogramTest {
    private fun mark(en: String, cn: String = "", pinyin: String = "") = brandMonogram(IrBrand(1, cn, en, pinyin, 1))
    @Test fun usesSameTwoLetterRuleForFullNamesAndMultipleWords() {
        assertEquals("SO", mark("SONY")); assertEquals("PH", mark("Philips"))
        assertEquals("AC", mark("Acoustic Research")); assertEquals("BO", mark("B&O"))
        assertEquals("LG", mark("L.G.")); assertEquals("MI", mark("mi小米"))
    }
    @Test fun missingOrShortLatinNameFallsBackToPinyinThenNeutralMark() {
        assertEquals("XI", mark("X", "小米", "xiaomi"))
        assertEquals("IR", mark("&", "未知")); assertEquals("IR", mark("A"))
    }
}
