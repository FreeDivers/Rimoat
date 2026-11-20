package org.qinyan.rimoat

import org.junit.Assert.assertEquals
import org.junit.Test
import org.qinyan.rimoat.i18n.AppLanguage

class AppLanguageTest {
    @Test fun chineseScriptsAndRegionsSelectDistinctChoices() {
        listOf("zh-Hant", "zh-TW", "zh-HK", "zh-MO", "zh-Hant-CN").forEach {
            assertEquals(it, "zh-Hant", AppLanguage.choiceTag(it))
        }
        listOf("zh", "zh-CN", "zh-SG", "zh-Hans", "zh-Hans-HK").forEach {
            assertEquals(it, "zh-CN", AppLanguage.choiceTag(it))
        }
    }

    @Test fun systemAndOtherSupportedLanguagesKeepTheirChoices() {
        assertEquals("", AppLanguage.choiceTag(""))
        assertEquals("en", AppLanguage.choiceTag("en-US"))
        assertEquals("ja", AppLanguage.choiceTag("ja-JP"))
        assertEquals("", AppLanguage.choiceTag("fr"))
    }
}
