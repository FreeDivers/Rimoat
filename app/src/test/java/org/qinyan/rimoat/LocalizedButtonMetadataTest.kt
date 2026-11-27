package org.qinyan.rimoat

import android.app.Application
import android.content.res.Configuration
import android.os.LocaleList
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.i18n.labelRes
import org.qinyan.rimoat.i18n.localizedBrand
import org.qinyan.rimoat.ui.components.icon
import org.qinyan.rimoat.i18n.localizedButtonLabel
import org.qinyan.rimoat.i18n.localizedCatalogKeyLabel
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.ui.learning.CustomRemoteWizardViewModel
import org.qinyan.rimoat.widget.WidgetCommands
import org.qinyan.rimoat.widget.displayLabel
import org.qinyan.rimoat.widget.isPower
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN", application = Application::class)
class LocalizedButtonMetadataTest {
    @org.junit.Before fun initializeResources() {
        org.qinyan.rimoat.i18n.AppStrings.initialize(RuntimeEnvironment.getApplication())
    }

    @Test fun widgetPowerMatchesLocalizedLabelsWithoutChangingUserData() {
        val app = RuntimeEnvironment.getApplication()
        val signal = listOf(9000, 4500, 560)
        for (tag in listOf("en", "zh-CN", "zh-Hant", "ja", "en")) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            })
            for (source in listOf("电源", "开关", "开机", "关机")) {
                val name = localizedLabel(source, context)
                for (label in listOf(name, " ${name.uppercase(java.util.Locale.ROOT)} ")) {
                    val button = RemoteButton("custom", label, rawPattern = signal)
                    val profile = RemoteProfile("remote", "My remote", updatedAt = 1L, buttons = listOf(
                        RemoteButton("input", "Input", rawPattern = signal), button))
                    val original = Json.encodeToString(RemoteProfile.serializer(), profile)
                    val command = WidgetCommands.choices(profile).last()
                    assertTrue("$tag: $label", command.isPower(profile, context))
                    assertEquals(button.id, WidgetCommands.default(profile, context = context)?.buttonId)
                    assertEquals(label, command.displayLabel(profile, context))
                    assertEquals(original, Json.encodeToString(RemoteProfile.serializer(), profile))
                    val unrelated = profile.copy(buttons = listOf(button.copy(label = "$name settings")))
                    assertFalse(WidgetCommands.choices(unrelated).single().isPower(unrelated, context))
                }
            }
            val catalog = RemoteButton("catalog", "电源键", sourceKeyType = 800, rawPattern = signal)
            val profile = RemoteProfile("catalog_remote", "My remote", buttons = listOf(catalog))
            val command = WidgetCommands.choices(profile).single()
            assertTrue(command.isPower(profile, context))
            assertEquals(localizedLabel("电源", context), command.displayLabel(profile, context))
        }
    }

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

    @Test fun learnedPowerMetadataSurvivesJsonAndOverridesItsDisplayName() {
        val button = RemoteButton("builtin", "My custom name", builtinLabel = "电源")
            .withLearnedSignal(IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 560)))
        val profile = RemoteProfile("remote", "My remote", buttons = listOf(
            button.copy(id = "input", builtinLabel = "信源"), button))
        val restored = Json.decodeFromString<RemoteProfile>(Json.encodeToString(RemoteProfile.serializer(), profile))
        assertEquals(button.id, WidgetCommands.default(restored)?.buttonId)
        assertEquals(button, restored.buttons.last())
    }

    @Test fun stableKeyAndAcResourcesResolveInEverySupportedLanguageWithoutMutatingProfiles() {
        val app = RuntimeEnvironment.getApplication()
        val button = RemoteButton("renamed", "My volume", builtinLabel = "音量+", key = ButtonKey.VOLUME_UP)
        val profile = RemoteProfile("stable", "Stable", buttons = listOf(button), keySchemaVersion = 1, brandIdx = 42, brand = "品牌")
        val original = Json.encodeToString(RemoteProfile.serializer(), profile)
        for (tag in listOf("en", "zh-CN", "zh-Hant", "ja")) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            })
            for (key in ButtonKey.entries) assertTrue("$tag: $key", context.getString(key.labelRes).isNotBlank())
            for (mode in AcMode.entries) assertEquals(localizedLabel(mode.labelCn, context), context.getString(mode.labelRes))
            for (speed in AcFanSpeed.entries) assertEquals(localizedLabel(speed.labelCn, context), context.getString(speed.labelRes))
            for (direction in AcWindDirection.entries) assertEquals(localizedLabel(direction.labelCn, context), context.getString(direction.labelRes))
            for (display in AcTemperatureDisplay.entries) assertEquals(localizedLabel(display.labelCn, context), context.getString(display.labelRes))
            for (action in AcAction.entries) assertEquals(localizedLabel(action.label, context), context.getString(action.labelRes))
            val brand = org.qinyan.rimoat.data.db.IrBrand(42, "品牌", "Brand", "b", 1)
            assertEquals(if (tag.startsWith("zh")) "品牌" else "Brand", profile.localizedBrand(brand, context))
            assertEquals("User brand", profile.copy(brand = "User brand").localizedBrand(brand, context))
            assertEquals("品牌", profile.copy(brandIdx = null).localizedBrand(brand, context))
        }
        val learned = button.withLearnedSignal(IrSignal.Raw(38000, intArrayOf(9000, 4500, 560)))
        assertEquals(button.key, learned.key)
        assertEquals(button.label, learned.label)
        assertEquals(button.builtinLabel, learned.builtinLabel)
        assertEquals(original, Json.encodeToString(RemoteProfile.serializer(), profile))
        assertNotNull(ButtonKey.VOLUME_UP.icon)
        assertNull(ButtonKey.DIGIT_0.icon)
        assertNull(ButtonKey.OK.icon)
    }

}
