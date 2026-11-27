package org.qinyan.rimoat.data

object LegacyRemoteMigration {

    const val KEY_SCHEMA_VERSION = 1

    /** Add semantic metadata without changing signal IDs, labels, or panel references. */
    fun normalizeKeys(profile: RemoteProfile): RemoteProfile {
        val learnedProfile = normalizeLearnedDefaults(profile)
        if (profile.keySchemaVersion >= KEY_SCHEMA_VERSION) return learnedProfile
        val buttons = learnedProfile.buttons.map { button ->
            val key = button.key ?: button.acAction?.buttonKey ?: if (button.builtinLabel != null) {
                LegacyLabelMap.key(button.builtinLabel) ?: LegacyLabelMap.id(button.id)
                    ?: LegacyLabelMap.icon(button.iconName)
            } else {
                LegacyLabelMap.key(button.label) ?: LegacyLabelMap.catalogAlias(button)
            }
            button.copy(key = key)
        }
        val verified = profile.verifiedFunctions.flatMap { value ->
            val matches = profile.buttons.mapIndexedNotNull { index, button ->
                buttons[index].takeIf { button.id == value || button.label == value || button.builtinLabel == value }
            }
            when {
                matches.isNotEmpty() -> matches.map { it.verifiedFunctionId }
                profile.category == DeviceCategory.AC -> listOf(
                    AcAction.entries.firstOrNull { it.label == value }?.name
                        ?: LegacyLabelMap.key(value)?.name ?: value)
                else -> listOf(LegacyLabelMap.key(value)?.name ?: value)
            }
        }.distinct()
        val source = legacyCatalogIdentity(profile.sourceRemoteId)
        return learnedProfile.copy(
            buttons = buttons,
            verifiedFunctions = verified,
            applianceType = profile.applianceType ?: source?.first ?: ApplianceType.fromCategory(profile.category),
            brandIdx = profile.brandIdx ?: source?.second,
            keySchemaVersion = KEY_SCHEMA_VERSION
        )
    }

    private fun normalizeLearnedDefaults(profile: RemoteProfile): RemoteProfile {
        val buttons = profile.buttons.map { button ->
            val builtin = button.builtinLabel
            if (!button.isLearned || builtin == null) return@map button
            val key = button.key ?: LegacyLabelMap.key(builtin) ?: return@map button
            button.copy(
                key = key,
                builtinLabel = key.name,
                acAction = button.acAction ?: if (profile.category == DeviceCategory.AC && profile.useStaticAcButtons) {
                    LegacyLabelMap.wizardAcAction(builtin)
                } else null
            )
        }
        if (buttons == profile.buttons) return profile
        val verified = profile.verifiedFunctions.flatMap { value ->
            val matches = profile.buttons.mapIndexedNotNull { index, button ->
                buttons[index].verifiedFunctionId.takeIf { button.verifiedFunctionId == value }
            }
            matches.ifEmpty { listOf(value) }
        }.distinct()
        return profile.copy(buttons = buttons, verifiedFunctions = verified)
    }

    /** The catalog ID syntax is a legacy storage concern, never a UI fallback. */
    private fun legacyCatalogIdentity(source: String?): Pair<ApplianceType, Int>? {
        val match = Regex("^R_(?:(?:compat|spec)_)?([a-z]+)_(\\d+)_.+$")
            .matchEntire(source ?: return null) ?: return null
        val type = ApplianceType.entries.firstOrNull { it.catalogCode == match.groupValues[1] } ?: return null
        val brand = match.groupValues[2].toIntOrNull() ?: return null
        return type to brand
    }

    // Common standard NEC raw pulses sample (Power signal)
    private val standardNecPowerPattern = listOf(
        9000, 4500,
        560, 560, 560, 560, 560, 1690, 560, 560,
        560, 560, 560, 560, 560, 560, 560, 560,
        560, 1690, 560, 1690, 560, 560, 560, 1690,
        560, 1690, 560, 1690, 560, 1690, 560, 1690,
        560, 560, 560, 560, 560, 560, 560, 1690,
        560, 560, 560, 560, 560, 560, 560, 560,
        560, 1690, 560, 1690, 560, 1690, 560, 560,
        560, 1690, 560, 1690, 560, 1690, 560, 1690,
        560
    )

    val greeAc = RemoteProfile(
        id = "preset_ac_gree",
        name = "格力变频空调",
        brand = "格力 (Gree)",
        category = DeviceCategory.AC,
        isFavorite = true,
        sourceRemoteId = "R_air_25_1",
        acProtocolId = 184,
        buttons = listOf(
            RemoteButton(
                id = "ac_power",
                label = "开关",
                iconName = "power_settings_new",
                row = 0, col = 0, spanCol = 1,
                signalType = SignalType.RAW,
                frequencyHz = 38000,
                rawPattern = GreeAcSignals.power
            ),
            RemoteButton(
                id = "ac_temp_up",
                label = "温度+",
                iconName = "keyboard_arrow_up",
                row = 1, col = 0, spanCol = 1,
                signalType = SignalType.RAW,
                frequencyHz = 38000,
                rawPattern = GreeAcSignals.tempUp
            ),
            RemoteButton(
                id = "ac_temp_down",
                label = "温度-",
                iconName = "keyboard_arrow_down",
                row = 1, col = 1, spanCol = 1,
                signalType = SignalType.RAW,
                frequencyHz = 38000,
                rawPattern = GreeAcSignals.tempDown
            ),
            RemoteButton(
                id = "ac_mode",
                label = "模式",
                iconName = "tune",
                row = 2, col = 0, spanCol = 1,
                signalType = SignalType.RAW,
                frequencyHz = 38000,
                rawPattern = GreeAcSignals.mode
            ),
            RemoteButton(
                id = "ac_wind",
                label = "风速",
                iconName = "air",
                row = 2, col = 1, spanCol = 1,
                signalType = SignalType.RAW,
                frequencyHz = 38000,
                rawPattern = GreeAcSignals.fan
            ),
            RemoteButton(
                id = "ac_swing",
                label = "扫风",
                iconName = "swap_calls",
                row = 2, col = 2, spanCol = 1,
                signalType = SignalType.RAW,
                frequencyHz = 38000,
                rawPattern = GreeAcSignals.swing
            )
        )
    )

    fun upgradeLegacyAc(profile: RemoteProfile): RemoteProfile {
        if (profile.id != greeAc.id || profile.acProtocolId != null || profile.useStaticAcButtons) return profile
        var replacements = 0
        val buttons = profile.buttons.map { button ->
            val original = greeAc.buttons.find { it.id == button.id }
            if (original != null && button.signalType == SignalType.RAW && button.rawPattern == standardNecPowerPattern) {
                replacements++
                button.copy(rawPattern = original.rawPattern, frequencyHz = original.frequencyHz)
            } else button
        }
        if (replacements == 0) return profile
        val untouched = replacements == 6 && buttons.size == 6
        return profile.copy(buttons = buttons, sourceRemoteId = greeAc.sourceRemoteId,
            acProtocolId = if (untouched) greeAc.acProtocolId else null, useStaticAcButtons = !untouched)
    }

}
