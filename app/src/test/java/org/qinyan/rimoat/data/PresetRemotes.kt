package org.qinyan.rimoat.data

object PresetRemotes {

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

    val xiaomiTv = RemoteProfile(
        id = "preset_tv_xiaomi",
        name = "小米智能电视",
        brand = "小米 (Xiaomi)",
        category = DeviceCategory.TV,
        isFavorite = true,
        buttons = listOf(
            RemoteButton(
                id = "tv_power",
                label = "电源",
                iconName = "power_settings_new",
                row = 0, col = 0, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x04FB,
                necCommand = 0x08
            ),
            RemoteButton(
                id = "tv_source",
                label = "信源",
                iconName = "input",
                row = 0, col = 1, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x04FB,
                necCommand = 0x0B
            ),
            RemoteButton(
                id = "tv_mute",
                label = "静音",
                iconName = "volume_off",
                row = 0, col = 2, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x04FB,
                necCommand = 0x09
            ),
            RemoteButton(
                id = "tv_up",
                label = "上",
                iconName = "keyboard_arrow_up",
                row = 1, col = 1, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x04FB,
                necCommand = 0x11
            ),
            RemoteButton(
                id = "tv_left",
                label = "左",
                iconName = "keyboard_arrow_left",
                row = 2, col = 0, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x04FB,
                necCommand = 0x14
            ),
            RemoteButton(
                id = "tv_ok",
                label = "OK",
                iconName = null,
                row = 2, col = 1, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x04FB,
                necCommand = 0x15
            ),
            RemoteButton(
                id = "tv_right",
                label = "右",
                iconName = "keyboard_arrow_right",
                row = 2, col = 2, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x04FB,
                necCommand = 0x13
            ),
            RemoteButton(
                id = "tv_down",
                label = "下",
                iconName = "keyboard_arrow_down",
                row = 3, col = 1, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x04FB,
                necCommand = 0x12
            ),
            RemoteButton(
                id = "tv_back",
                label = "返回",
                iconName = "arrow_back",
                row = 4, col = 0, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x04FB,
                necCommand = 0x19
            ),
            RemoteButton(
                id = "tv_home",
                label = "主页",
                iconName = "home",
                row = 4, col = 1, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x04FB,
                necCommand = 0x1A
            ),
            RemoteButton(
                id = "tv_menu",
                label = "菜单",
                iconName = "menu",
                row = 4, col = 2, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x04FB,
                necCommand = 0x1B
            ),
            RemoteButton(
                id = "tv_vol_up",
                label = "音量+",
                iconName = "volume_up",
                row = 5, col = 0, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x04FB,
                necCommand = 0x16
            ),
            RemoteButton(
                id = "tv_vol_down",
                label = "音量-",
                iconName = "volume_down",
                row = 5, col = 1, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x04FB,
                necCommand = 0x17
            ),
            RemoteButton(
                id = "tv_ch_up",
                label = "频道+",
                iconName = "arrow_drop_up",
                row = 5, col = 2, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x04FB,
                necCommand = 0x20
            ),
            RemoteButton(
                id = "tv_ch_down",
                label = "频道-",
                iconName = "arrow_drop_down",
                row = 6, col = 2, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x04FB,
                necCommand = 0x21
            )
        )
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

    val mideaFan = RemoteProfile(
        id = "preset_fan_midea",
        name = "美的落地风扇",
        brand = "美的 (Midea)",
        category = DeviceCategory.FAN,
        isFavorite = false,
        buttons = listOf(
            RemoteButton(
                id = "fan_power",
                label = "电源",
                iconName = "power_settings_new",
                row = 0, col = 0, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x02FD,
                necCommand = 0x01
            ),
            RemoteButton(
                id = "fan_speed",
                label = "风速",
                iconName = "speed",
                row = 0, col = 1, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x02FD,
                necCommand = 0x02
            ),
            RemoteButton(
                id = "fan_swing",
                label = "摇头",
                iconName = "sync_alt",
                row = 1, col = 0, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x02FD,
                necCommand = 0x03
            ),
            RemoteButton(
                id = "fan_timer",
                label = "定时",
                iconName = "timer",
                row = 1, col = 1, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x02FD,
                necCommand = 0x04
            ),
            RemoteButton(
                id = "fan_mode",
                label = "自然风",
                iconName = "nature",
                row = 2, col = 0, spanCol = 2,
                signalType = SignalType.NEC,
                necAddress = 0x02FD,
                necCommand = 0x05
            )
        )
    )

    val xgimiProjector = RemoteProfile(
        id = "preset_pjt_xgimi",
        name = "极米智能投影仪",
        brand = "极米 (XGIMI)",
        category = DeviceCategory.PROJECTOR,
        isFavorite = false,
        buttons = listOf(
            RemoteButton(
                id = "pjt_power",
                label = "电源",
                iconName = "power_settings_new",
                row = 0, col = 0, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x01FE,
                necCommand = 0x48
            ),
            RemoteButton(
                id = "pjt_source",
                label = "信源",
                iconName = "input",
                row = 0, col = 1, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x01FE,
                necCommand = 0x49
            ),
            RemoteButton(
                id = "pjt_menu",
                label = "菜单",
                iconName = "menu",
                row = 0, col = 2, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x01FE,
                necCommand = 0x4A
            ),
            RemoteButton(
                id = "pjt_up",
                label = "上",
                iconName = "keyboard_arrow_up",
                row = 1, col = 1, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x01FE,
                necCommand = 0x50
            ),
            RemoteButton(
                id = "pjt_left",
                label = "左",
                iconName = "keyboard_arrow_left",
                row = 2, col = 0, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x01FE,
                necCommand = 0x51
            ),
            RemoteButton(
                id = "pjt_ok",
                label = "OK",
                iconName = null,
                row = 2, col = 1, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x01FE,
                necCommand = 0x52
            ),
            RemoteButton(
                id = "pjt_right",
                label = "右",
                iconName = "keyboard_arrow_right",
                row = 2, col = 2, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x01FE,
                necCommand = 0x53
            ),
            RemoteButton(
                id = "pjt_down",
                label = "下",
                iconName = "keyboard_arrow_down",
                row = 3, col = 1, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x01FE,
                necCommand = 0x54
            ),
            RemoteButton(
                id = "pjt_back",
                label = "返回",
                iconName = "arrow_back",
                row = 4, col = 0, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x01FE,
                necCommand = 0x55
            ),
            RemoteButton(
                id = "pjt_focus_up",
                label = "对焦+",
                iconName = "center_focus_strong",
                row = 4, col = 1, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x01FE,
                necCommand = 0x56
            ),
            RemoteButton(
                id = "pjt_focus_down",
                label = "对焦-",
                iconName = "filter_center_focus",
                row = 4, col = 2, spanCol = 1,
                signalType = SignalType.NEC,
                necAddress = 0x01FE,
                necCommand = 0x57
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

    val allPresets = listOf(xiaomiTv, greeAc, mideaFan, xgimiProjector)
}

