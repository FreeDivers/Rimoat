package org.qinyan.rimoat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.i18n.localizedCatalogKeyLabel

class CatalogKeyLabelsTest {
    private fun button(type: Int, label: String) = RemoteButton("catalog-$type", label, sourceKeyType = type)

    @Test fun legacyDigitsAndDirectionAliasesKeepMeaning() {
        assertEquals("3", localizedCatalogKeyLabel(button(3, "数字键 3")))
        assertEquals("上", localizedCatalogKeyLabel(button(818, "上方")))
        assertEquals("快进", localizedCatalogKeyLabel(button(824, "前进")))
        assertEquals("菜单", localizedCatalogKeyLabel(button(822, "菜单键")))
    }

    @Test fun conflictingTypeIdsDoNotOverrideTheSourceMeaning() {
        // Catalog legacy IDs differ from official names for these entries.
        assertNull(localizedCatalogKeyLabel(button(875, "定时")))
        assertNull(localizedCatalogKeyLabel(button(902, "未知灯光")))
        assertNull(localizedCatalogKeyLabel(button(-97, "电视关机")))
        assertNull(localizedCatalogKeyLabel(button(-100, "卧室窗帘")))
    }

    @Test fun knownLegacyLabelsOverrideShiftedTypeMeanings() {
        // These are Home and Humidify despite current type 902/876 meaning Brightness/Timer.
        assertEquals("主页", localizedCatalogKeyLabel(button(902, "首页")))
        assertEquals("加湿", localizedCatalogKeyLabel(button(876, "加湿")))
    }

    @Test fun presetsKeepTheirModeAndTemperature() {
        assertEquals("开机 制冷 26°C", localizedCatalogKeyLabel(button(800, "开机_制冷_26度")))
        assertEquals("开机 制热 20°C", localizedCatalogKeyLabel(button(800, "开机_制热_20度")))
        assertNull(localizedCatalogKeyLabel(button(800, "开机_未知_20度")))
    }

    @Test fun userLabelsAreNotNormalizedWithoutCatalogProvenance() {
        assertNull(localizedCatalogKeyLabel(RemoteButton("custom", "菜单键")))
    }
}
