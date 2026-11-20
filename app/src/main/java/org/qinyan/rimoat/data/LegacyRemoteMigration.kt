package org.qinyan.rimoat.data

object LegacyRemoteMigration {

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
