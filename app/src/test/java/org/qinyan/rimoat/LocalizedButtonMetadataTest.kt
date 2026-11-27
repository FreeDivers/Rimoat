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
    private fun normalizedCatalogButton(button: RemoteButton) = LegacyRemoteMigration.normalizeKeys(
        RemoteProfile("catalog", "Remote", keySchemaVersion = LegacyRemoteMigration.KEY_SCHEMA_VERSION, buttons = listOf(button))
    ).buttons.single()

    private fun localizedCatalogKeyLabel(button: RemoteButton): String? =
        org.qinyan.rimoat.i18n.localizedCatalogKeyLabel(normalizedCatalogButton(button))

    @org.junit.Before fun initializeResources() {
        org.qinyan.rimoat.i18n.AppStrings.initialize(RuntimeEnvironment.getApplication())
    }

    @Test fun migratedCatalogKeysTranslateEvenWhenTheLegacyTypeDiffers() {
        val source = RemoteButton("legacy-timer", "定时", sourceKeyType = 875)
        val migrated = LegacyRemoteMigration.normalizeKeys(RemoteProfile("legacy", "Remote", buttons = listOf(source)))
            .buttons.single()
        assertEquals(source.label, migrated.label)
        assertEquals(ButtonKey.TIMER, migrated.key)
        val app = RuntimeEnvironment.getApplication()
        for (tag in listOf("en", "zh-CN", "zh-Hant", "ja")) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            })
            assertEquals(context.getString(R.string.control_timer), localizedButtonLabel(migrated, context))
            assertEquals("Custom timer", localizedButtonLabel(source.copy(label = "Custom timer"), context))
        }
    }

    @Test fun catalogFallbackMetadataResolvesAcrossLanguagesAndAcSources() {
        val app = RuntimeEnvironment.getApplication()
        val chinese = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("zh-CN"))
        })
        val legacyLabels = mapOf(
            CatalogFallbackLabel.CLEAR to "清除", CatalogFallbackLabel.OFF to "关",
            CatalogFallbackLabel.ON to "开", CatalogFallbackLabel.DISABLED to "关闭",
            CatalogFallbackLabel.CANCEL to "取消", CatalogFallbackLabel.DISPLAY to "显示",
            CatalogFallbackLabel.SOUND to "声音", CatalogFallbackLabel.TELEVISION to "电视",
            CatalogFallbackLabel.CUSTOM to "自定义", CatalogFallbackLabel.TIMER_OFF to "定时关机",
            CatalogFallbackLabel.SAVE to "保存", CatalogFallbackLabel.TEMPERATURE_DOWN to "降温",
            CatalogFallbackLabel.ENABLED to "开启", CatalogFallbackLabel.AUDIO to "音响",
            CatalogFallbackLabel.PICTURE to "画面", CatalogFallbackLabel.TOGGLE to "开关"
        )
        val catalogButtons = CatalogFallbackLabel.entries.map { label ->
            val original = RemoteButton("catalog-${label.name}", legacyLabels.getValue(label), sourceKeyType = 875)
            original to normalizedCatalogButton(original)
        }
        val acButton = normalizedCatalogButton(RemoteButton("ac_function_timer", chinese.getString(R.string.control_timer_off)))
        val actionButton = normalizedCatalogButton(RemoteButton("ac-volume", chinese.getString(R.string.control_volume_up), acAction = AcAction.MODE))
        for (tag in listOf("en", "zh-CN", "zh-Hant", "ja")) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            })
            for ((index, pair) in catalogButtons.withIndex()) {
                val (original, migrated) = pair
                val label = CatalogFallbackLabel.entries[index]
                assertEquals(original.label, migrated.label)
                assertEquals(context.getString(label.labelRes), localizedButtonLabel(migrated, context))
            }
            assertEquals(context.getString(R.string.control_timer_off), localizedButtonLabel(acButton, context))
            assertEquals(context.getString(R.string.control_volume_up), localizedButtonLabel(actionButton, context))
            val learned = normalizedCatalogButton(actionButton.copy(isLearned = true))
            assertEquals(actionButton.label, localizedButtonLabel(learned, context))
        }
    }

    @Test fun builtinDisplayIdentityRemainsIndependentOfTheCommandKey() {
        val app = RuntimeEnvironment.getApplication()
        val button = RemoteButton("explicit", "User name", builtinLabel = ButtonKey.POWER.name, key = ButtonKey.MENU)
        for (tag in listOf("en", "zh-CN", "zh-Hant", "ja")) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            })
            assertEquals(context.getString(R.string.control_power), localizedButtonLabel(button, context))
            assertEquals(ButtonKey.MENU, button.key)
            assertEquals("User name", button.label)
        }
    }

    @Test fun migratedLegacyBuiltinDisplayPreservesItsSourceAndAcWording() {
        val sources = listOf(
            RemoteButton("legacy-power", "User name", builtinLabel = "电源", key = ButtonKey.MENU) to R.string.control_power,
            RemoteButton("legacy-ac-power", "User name", builtinLabel = "电源", key = ButtonKey.MENU, acAction = AcAction.POWER_ON) to R.string.control_power,
            RemoteButton("legacy-confirm", "User name", builtinLabel = "确认", key = ButtonKey.MENU) to R.string.control_confirm,
            RemoteButton("legacy-toggle", "User name", builtinLabel = "开关", key = ButtonKey.MENU) to R.string.control_toggle,
            RemoteButton("stable-ac-power", "User name", builtinLabel = ButtonKey.POWER.name, key = ButtonKey.MENU, acAction = AcAction.POWER_ON) to R.string.control_toggle
        )
        val app = RuntimeEnvironment.getApplication()
        for (tag in listOf("en", "zh-CN", "zh-Hant", "ja")) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            })
            for ((source, resource) in sources) {
                val migrated = normalizedCatalogButton(source)
                assertEquals(source.label, migrated.label)
                assertEquals(source.builtinLabel, migrated.builtinLabel)
                assertEquals(source.key, migrated.key)
                assertEquals(context.getString(resource), localizedButtonLabel(migrated, context))
            }
            val unknown = normalizedCatalogButton(RemoteButton("unknown-builtin", "User name", builtinLabel = "Legacy custom", key = ButtonKey.MENU))
            assertEquals("Legacy custom", localizedButtonLabel(unknown, context))
        }
    }

    @Test fun widgetPowerUsesStableKeysAndPreservesUserLabels() {
        val app = RuntimeEnvironment.getApplication()
        val signal = listOf(9000, 4500, 560)
        for (tag in listOf("en", "zh-CN", "zh-Hant", "ja", "en")) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            })
            for (source in listOf(R.string.control_power, R.string.control_toggle, R.string.control_power_on, R.string.control_power_off)) {
                val name = context.getString(source)
                for (label in listOf(name, " ${name.uppercase(java.util.Locale.ROOT)} ")) {
                    val button = RemoteButton("custom", label, rawPattern = signal)
                    val profile = RemoteProfile("remote", "My remote", updatedAt = 1L, buttons = listOf(
                        RemoteButton("input", "Input", rawPattern = signal), button))
                    val original = Json.encodeToString(RemoteProfile.serializer(), profile)
                    val command = WidgetCommands.choices(profile).last()
                    assertFalse("$tag: $label", command.isPower(profile, context))
                    assertEquals("input", WidgetCommands.default(profile, context = context)?.buttonId)
                    assertEquals(label, command.displayLabel(profile, context))
                    assertEquals(original, Json.encodeToString(RemoteProfile.serializer(), profile))
                    val keyed = profile.copy(buttons = listOf(profile.buttons.first(), button.copy(key = ButtonKey.POWER)))
                    val keyedCommand = WidgetCommands.choices(keyed).last()
                    assertTrue("$tag: $label", keyedCommand.isPower(keyed, context))
                    assertEquals(button.id, WidgetCommands.default(keyed, context = context)?.buttonId)
                    assertEquals(label, keyedCommand.displayLabel(keyed, context))
                    val unrelated = profile.copy(buttons = listOf(button.copy(label = "$name settings")))
                    assertFalse(WidgetCommands.choices(unrelated).single().isPower(unrelated, context))
                }
            }
            val catalog = LegacyRemoteMigration.normalizeKeys(RemoteProfile("catalog", "Remote", buttons = listOf(
                RemoteButton("catalog", "电源键", sourceKeyType = 800, rawPattern = signal)
            ))).buttons.single()
            val profile = RemoteProfile("catalog_remote", "My remote", buttons = listOf(catalog))
            val command = WidgetCommands.choices(profile).single()
            assertTrue(command.isPower(profile, context))
            assertEquals(context.getString(R.string.control_power), command.displayLabel(profile, context))
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
        val restored = LegacyRemoteMigration.normalizeKeys(Json.decodeFromString<RemoteProfile>(Json.encodeToString(RemoteProfile.serializer(), profile)))
        assertEquals(button.id, WidgetCommands.default(restored)?.buttonId)
        assertEquals(button.label, restored.buttons.last().label)
        assertEquals(button.rawPattern, restored.buttons.last().rawPattern)
        assertEquals(ButtonKey.POWER, restored.buttons.last().key)
    }

    @Test fun stableKeyAndAcResourcesResolveInEverySupportedLanguageWithoutMutatingProfiles() {
        val app = RuntimeEnvironment.getApplication()
        val button = normalizedCatalogButton(RemoteButton("renamed", "My volume", builtinLabel = "音量+", key = ButtonKey.VOLUME_UP))
        val profile = RemoteProfile("stable", "Stable", buttons = listOf(button), keySchemaVersion = 1, brandIdx = 42, brand = "品牌")
        val original = Json.encodeToString(RemoteProfile.serializer(), profile)
        for (tag in listOf("en", "zh-CN", "zh-Hant", "ja")) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            })
            for (key in ButtonKey.entries) assertTrue("$tag: $key", context.getString(key.labelRes).isNotBlank())
            for (mode in AcMode.entries) assertEquals(localizedLabel(mode, context), context.getString(mode.labelRes))
            for (speed in AcFanSpeed.entries) assertEquals(localizedLabel(speed, context), context.getString(speed.labelRes))
            for (direction in AcWindDirection.entries) assertEquals(localizedLabel(direction, context), context.getString(direction.labelRes))
            for (display in AcTemperatureDisplay.entries) assertEquals(localizedLabel(display, context), context.getString(display.labelRes))
            for (action in AcAction.entries) assertEquals(localizedLabel(action, context), context.getString(action.labelRes))
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
