package org.qinyan.rimoat

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.ui.learning.CustomRemoteWizardViewModel
import org.qinyan.rimoat.widget.WidgetConfiguration
import org.qinyan.rimoat.widget.WidgetKind
import org.qinyan.rimoat.widget.WidgetShortcut

class ButtonKeyMigrationTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun migrate(vararg buttons: RemoteButton) = LegacyRemoteMigration.normalizeKeys(
        RemoteProfile("remote", "Remote", updatedAt = 1L, buttons = buttons.toList()))

    @Test fun allChineseDefaultQueuesReceiveStableKeysWithoutChangingLegacyFields() {
        for (type in ApplianceType.entries) {
            val buttons = CustomRemoteWizardViewModel.generateDefaultQueue(type).mapIndexed { index, item ->
                RemoteButton("key_$index", item.label, iconName = item.iconName, builtinLabel = item.builtinLabel,
                    rawPattern = listOf(9000, 4500, 560), isLearned = true)
            }
            val result = migrate(*buttons.toTypedArray())
            assertEquals(LegacyRemoteMigration.KEY_SCHEMA_VERSION, result.keySchemaVersion)
            result.buttons.zip(buttons).forEach { (actual, before) ->
                assertNotNull("$type: ${before.label}", actual.key)
                assertEquals(before, actual.copy(key = null))
            }
        }
    }

    @Test fun englishCustomNamesRequireExactMatchesAndNeverUseMisleadingIdsOrIcons() {
        val result = migrate(
            RemoteButton("volume", "Volume up"),
            RemoteButton("mute", "Mute"),
            RemoteButton("tv_power", "My Power", iconName = "power"),
            RemoteButton("tv_up", "Move upstairs", iconName = "keyboard_arrow_up"),
            RemoteButton("spaces", " Volume up "),
            RemoteButton("named", "音量+ setting"),
            RemoteButton("literal", "用户自定义_日本語"))
        assertEquals(listOf(ButtonKey.VOLUME_UP, ButtonKey.MUTE, null, null, null, null, null), result.buttons.map { it.key })
        assertEquals("My Power", result.buttons[2].label)
    }

    @Test fun builtinsPreferTheirSourceLabelThenKnownIdThenIcon() {
        val result = migrate(
            RemoteButton("tv_up", "Renamed", iconName = "power", builtinLabel = "音量+"),
            RemoteButton("tv_up", "Renamed", iconName = "power", builtinLabel = "unknown"),
            RemoteButton("unrecognized", "Renamed", iconName = "power", builtinLabel = "unknown"),
            RemoteButton("explicit", "电源", builtinLabel = "电源", key = ButtonKey.MENU))
        assertEquals(listOf(ButtonKey.VOLUME_UP, ButtonKey.UP, ButtonKey.POWER, ButtonKey.MENU), result.buttons.map { it.key })
    }

    @Test fun mixedLabelsAndAcActionsBecomeStableVerificationNames() {
        val profile = RemoteProfile("mixed", "Mixed", category = DeviceCategory.AC, updatedAt = 1L,
            buttons = listOf(
                RemoteButton("power", "开机", acAction = AcAction.POWER_ON),
                RemoteButton("volume", "Volume up"),
                RemoteButton("custom", "My Scene"),
                RemoteButton("fan", "User fan", acAction = AcAction.FAN)),
            verifiedFunctions = listOf("开机", "Volume up", "My Scene", "User fan", "升温"))
        val migrated = LegacyRemoteMigration.normalizeKeys(profile)
        assertEquals(listOf("POWER_ON", "VOLUME_UP", "custom", "FAN", "TEMP_UP"), migrated.verifiedFunctions)
        assertEquals(profile.buttons.map { it.label }, migrated.buttons.map { it.label })
        assertEquals(migrated, LegacyRemoteMigration.normalizeKeys(migrated))
        assertEquals(json.encodeToString(migrated), json.encodeToString(LegacyRemoteMigration.normalizeKeys(migrated)))
    }

    @Test fun schemaMarkerPreventsReinterpretingLaterUserEdits() {
        val profile = migrate(RemoteButton("custom", "A scene"))
        val renamed = profile.copy(buttons = listOf(profile.buttons.single().copy(label = "Power")))
        assertSame(renamed, LegacyRemoteMigration.normalizeKeys(renamed))
        assertNull(LegacyRemoteMigration.normalizeKeys(renamed).buttons.single().key)
        val future = profile.copy(keySchemaVersion = LegacyRemoteMigration.KEY_SCHEMA_VERSION)
        assertSame(future, LegacyRemoteMigration.normalizeKeys(future))
    }

    @Test fun directionIdsHiddenLayoutAndWidgetShortcutSurviveBackupMigration() {
        val profile = RemoteProfile("tv", "Learned TV", category = DeviceCategory.TV, updatedAt = 1L,
            buttons = listOf(RemoteButton("direction_上", "上", builtinLabel = "上", isLearned = true,
                rawPattern = listOf(9000, 4500, 560))),
            panelLayout = RemotePanelLayout(order = listOf("key:direction_上"), hidden = setOf("key:direction_上")))
        val widget = WidgetConfiguration(WidgetKind.SINGLE, profile.id, listOf(WidgetShortcut(profile.id, "direction_上")))
        val widgetJson = json.encodeToString(widget)
        val migrated = RemoteBackupCodec.decode(RemoteBackupCodec.encode(listOf(profile))).single()
        assertEquals("direction_up", migrated.buttons.single().id)
        assertEquals(ButtonKey.UP, migrated.buttons.single().key)
        assertEquals(profile.buttons.single().copy(id = "direction_up", builtinLabel = "UP"), migrated.buttons.single().copy(key = null))
        assertEquals(RemotePanelLayout(order = listOf("key:direction_up"), hidden = setOf("key:direction_up")), migrated.panelLayout)
        assertEquals(widgetJson, json.encodeToString(widget))
        val file = java.nio.file.Files.createTempFile("widget-migration-", ".json").toFile()
        try {
            file.writeText("""{"configurations":{"1":$widgetJson},"completedRestores":[]}""")
            val store = org.qinyan.rimoat.widget.WidgetStore(file)
            kotlinx.coroutines.runBlocking { store.load() }
            val restoredWidget = store.configurations.value.getValue(1)
            assertEquals("direction_up", restoredWidget.shortcuts.single().buttonId)
            assertEquals(migrated.buttons.single().id, restoredWidget.shortcuts.single().buttonId)
        } finally {
            file.delete()
        }
        assertEquals(migrated, RemoteBackupCodec.decode(RemoteBackupCodec.encode(listOf(migrated))).single())
    }

    @Serializable private data class OldButton(val id: String, val label: String, val builtinLabel: String? = null, val iconName: String? = null)
    @Serializable private data class OldProfile(val id: String, val name: String, val buttons: List<OldButton>)
    @Serializable private data class OldBackup(val version: Int, val remotes: List<OldProfile>)

    @Test fun exportedVersionTwoRetainsLegacyLabelsAndCanBeReadWithOldSchema() {
        val profile = migrate(RemoteButton("volume", "My volume", builtinLabel = "音量+", iconName = "volume_up"))
        val encoded = json.encodeToString(profile)
        val buttonJson = json.parseToJsonElement(encoded).jsonObject.getValue("buttons").jsonArray.single().jsonObject
        assertEquals("My volume", buttonJson.getValue("label").jsonPrimitive.content)
        assertEquals("音量+", buttonJson.getValue("builtinLabel").jsonPrimitive.content)
        assertEquals("volume_up", buttonJson.getValue("iconName").jsonPrimitive.content)
        val exported = RemoteBackupCodec.encode(listOf(profile))
        val old = json.decodeFromString<OldBackup>(exported)
        assertEquals(2, old.version)
        assertEquals(OldButton("volume", "My volume", "音量+", "volume_up"), old.remotes.single().buttons.single())
        assertEquals(profile, RemoteBackupCodec.decode(exported).single())
        val oldReexport = json.encodeToString(old)
        assertEquals(ButtonKey.VOLUME_UP, RemoteBackupCodec.decode(oldReexport).single().buttons.single().key)
    }

    @Test fun catalogAliasesRespectTheirTypeAndNeverGuessFromSubstrings() {
        val migrated = migrate(
            RemoteButton("forward", "前进", sourceKeyType = 824),
            RemoteButton("wrong", "前进", sourceKeyType = 825),
            RemoteButton("humidify", "加湿", sourceKeyType = 876),
            RemoteButton("custom", "My 前进", sourceKeyType = 824),
            RemoteButton("unknown", "Custom circle", sourceKeyType = -100))
        assertEquals(listOf(ButtonKey.FAST_FORWARD, null, ButtonKey.HUMIDIFY, null, null), migrated.buttons.map { it.key })
    }

    @Test fun applianceMigrationHandlesCatalogPrefixesAndPreservesExplicitTypesAndCustomBrands() {
        for ((code, type) in mapOf("stb" to ApplianceType.SET_TOP_BOX, "dvd" to ApplianceType.DVD,
            "cam" to ApplianceType.CAMERA, "light" to ApplianceType.LIGHT, "box" to ApplianceType.STREAMING_BOX, "ipt" to ApplianceType.IPTV)) {
            for (prefix in listOf("R_", "R_compat_", "R_spec_")) {
                val profile = RemoteProfile("legacy", "Legacy", brand = "Stored brand", sourceRemoteId = "${prefix}${code}_42_1")
                val migrated = LegacyRemoteMigration.normalizeKeys(profile)
                assertEquals(type, migrated.applianceType)
                assertEquals(42, migrated.brandIdx)
                assertEquals(profile.brand, migrated.brand)
                assertEquals(profile.sourceRemoteId, migrated.sourceRemoteId)
            }
        }
        val custom = RemoteProfile("custom", "Custom", brand = "User brand", category = DeviceCategory.AUDIO)
        val normalized = LegacyRemoteMigration.normalizeKeys(custom)
        assertNull(normalized.brandIdx)
        assertEquals("User brand", normalized.brand)
        assertEquals(ApplianceType.AUDIO, normalized.applianceType)
        val explicit = custom.copy(applianceType = ApplianceType.DVD, brandIdx = 7, sourceRemoteId = "R_stb_42_1")
        assertEquals(ApplianceType.DVD, LegacyRemoteMigration.normalizeKeys(explicit).applianceType)
        assertEquals(7, LegacyRemoteMigration.normalizeKeys(explicit).brandIdx)
    }

    @Test fun relabelledButtonsInvalidateStableVerificationWithoutTouchingOtherFunctions() {
        val power = RemoteButton("power", "User power", key = ButtonKey.POWER, builtinLabel = "电源")
        val volume = RemoteButton("volume", "User volume", key = ButtonKey.VOLUME_UP)
        val profile = RemoteProfile("remote", "Remote", buttons = listOf(power, volume), keySchemaVersion = 1,
            verifiedFunctions = listOf("POWER", "VOLUME_UP"))
        val result = profile.withoutVerifiedButtons(listOf(power.copy(label = "New name")))
        assertEquals(listOf("VOLUME_UP"), result.verifiedFunctions)
        assertEquals(profile.buttons, result.buttons)
    }
}
