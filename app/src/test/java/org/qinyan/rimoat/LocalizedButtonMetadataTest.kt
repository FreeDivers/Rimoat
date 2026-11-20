package org.qinyan.rimoat

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.i18n.localizedButtonLabel
import org.qinyan.rimoat.i18n.localizedCatalogKeyLabel
import org.qinyan.rimoat.ui.learning.CustomRemoteWizardViewModel

class LocalizedButtonMetadataTest {
    @Test fun catalogAliasNormalizationRequiresSourceMetadataAndPreservesUnknownLabels() {
        assertNull(localizedCatalogKeyLabel(RemoteButton("user", "电源键")))
        assertNull(localizedCatalogKeyLabel(RemoteButton("wrong-type", "前进", sourceKeyType = 825)))
        assertNull(localizedCatalogKeyLabel(RemoteButton("unknown", "My 前进_日本語", sourceKeyType = 824)))
        assertEquals("My 前进_日本語", localizedButtonLabel(RemoteButton("unknown", "My 前进_日本語", sourceKeyType = 824)))
        assertEquals("5", localizedCatalogKeyLabel(RemoteButton("numeric", "数字键 5", sourceKeyType = 5)))
        assertNull(localizedCatalogKeyLabel(RemoteButton("wrong-digit", "数字键 6", sourceKeyType = 5)))
        val alias = RemoteButton("catalog", "前进", sourceKeyType = 824, sourceSlot = "fast_forward")
        val learned = alias.withLearnedSignal(IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560)))
        assertEquals("快进", localizedCatalogKeyLabel(learned))
        assertEquals(alias.label, learned.label)
        assertEquals(alias.sourceKeyType, learned.sourceKeyType)
        assertEquals(alias.sourceSlot, learned.sourceSlot)
    }

    @Test fun oldAndUserLabelsStayLiteralWhileDefaultMetadataSurvivesLearningAndJson() {
        val old = Json.decodeFromString<RemoteProfile>("""{"id":"old","name":"My remote","buttons":[{"id":"custom","label":"自定义_日本語"}]}""")
        val user = old.buttons.single()
        assertNull(user.builtinLabel)
        assertEquals(user.label, localizedButtonLabel(user))
        assertEquals("电源", localizedButtonLabel(RemoteButton("user-named-power", "电源", iconName = "power")))
        val defaults = CustomRemoteWizardViewModel.generateDefaultQueue(DeviceCategory.TV)
        assertTrue(defaults.all { it.builtinLabel == it.label })
        val source = RemoteButton("catalog", "音量+", sourceKeyType = 809, sourceSlot = "volume_up", builtinLabel = "音量+")
        val learned = source.withLearnedSignal(IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560)))
        assertTrue(learned.isLearned)
        assertEquals(source.sourceKeyType, learned.sourceKeyType)
        assertEquals(source.sourceSlot, learned.sourceSlot)
        assertEquals(source.builtinLabel, learned.builtinLabel)
        val profile = old.copy(buttons = listOf(user, learned))
        val decoded = Json.decodeFromString<RemoteProfile>(Json.encodeToString(RemoteProfile.serializer(), profile))
        assertEquals(profile, decoded)
        assertNull(decoded.buttons.first().builtinLabel)
        assertEquals("音量+", decoded.buttons.last().builtinLabel)
    }
}
