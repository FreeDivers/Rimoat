package org.qinyan.rimoat.ui.learning

import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.ApplianceType
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.data.ButtonKey

internal object WizardKeyTemplates {
    private fun defaultKey(
        key: ButtonKey,
        iconName: String? = null,
        acAction: AcAction? = null
    ) = WizardKeyItem(
        label = key.name,
        builtinLabel = key.name,
        key = key,
        iconName = iconName,
        acAction = acAction
    )

    fun generateDefaultQueue(type: ApplianceType): List<WizardKeyItem> {
        val keys = when (type) {
            ApplianceType.SET_TOP_BOX, ApplianceType.IPTV -> generateDefaultQueue(DeviceCategory.TV) + listOf(
                defaultKey(ButtonKey.CHANNEL_UP, iconName = "arrow_drop_up"),
                defaultKey(ButtonKey.CHANNEL_DOWN, iconName = "arrow_drop_down")
            )
            ApplianceType.DVD -> generateDefaultQueue(DeviceCategory.AUDIO) + listOf(
                defaultKey(ButtonKey.PLAY_PAUSE),
                defaultKey(ButtonKey.STOP),
                defaultKey(ButtonKey.PREVIOUS),
                defaultKey(ButtonKey.NEXT),
                defaultKey(ButtonKey.EJECT)
            )
            ApplianceType.CAMERA -> listOf(
                defaultKey(ButtonKey.SHUTTER),
                defaultKey(ButtonKey.SUB_SHUTTER)
            )
            ApplianceType.LIGHT -> listOf(
                defaultKey(ButtonKey.POWER, iconName = "power"),
                defaultKey(ButtonKey.BRIGHTNESS)
            )
            else -> generateDefaultQueue(type.category)
        }
        return keys
    }

    fun generateDefaultQueue(category: DeviceCategory): List<WizardKeyItem> {
        return when (category) {
            DeviceCategory.TV -> listOf(
                defaultKey(ButtonKey.POWER, iconName = "power"),
                defaultKey(ButtonKey.VOLUME_UP, iconName = "volume_up"),
                defaultKey(ButtonKey.VOLUME_DOWN, iconName = "volume_down"),
                defaultKey(ButtonKey.MUTE, iconName = "volume_off"),
                defaultKey(ButtonKey.UP, iconName = "keyboard_arrow_up"),
                defaultKey(ButtonKey.DOWN, iconName = "keyboard_arrow_down"),
                defaultKey(ButtonKey.LEFT, iconName = "keyboard_arrow_left"),
                defaultKey(ButtonKey.RIGHT, iconName = "keyboard_arrow_right"),
                defaultKey(ButtonKey.OK, iconName = null),
                defaultKey(ButtonKey.BACK, iconName = "arrow_back"),
                defaultKey(ButtonKey.HOME, iconName = "home"),
                defaultKey(ButtonKey.MENU, iconName = "menu")
            )
            DeviceCategory.AC -> listOf(
                defaultKey(ButtonKey.POWER, acAction = AcAction.POWER_ON, iconName = "power"),
                defaultKey(ButtonKey.TEMPERATURE_UP, acAction = AcAction.TEMP_UP, iconName = "arrow_drop_up"),
                defaultKey(ButtonKey.TEMPERATURE_DOWN, acAction = AcAction.TEMP_DOWN, iconName = "arrow_drop_down"),
                defaultKey(ButtonKey.MODE, acAction = AcAction.MODE, iconName = "tune"),
                defaultKey(ButtonKey.FAN_SPEED, acAction = AcAction.FAN, iconName = "air"),
                defaultKey(ButtonKey.SWING, acAction = AcAction.SWING, iconName = "swap_calls")
            )
            DeviceCategory.FAN -> listOf(
                defaultKey(ButtonKey.POWER, iconName = "power"),
                defaultKey(ButtonKey.FAN_SPEED, iconName = "speed"),
                defaultKey(ButtonKey.OSCILLATION, iconName = "sync_alt"),
                defaultKey(ButtonKey.TIMER, iconName = "timer"),
                defaultKey(ButtonKey.MODE, iconName = "tune")
            )
            DeviceCategory.PROJECTOR -> listOf(
                defaultKey(ButtonKey.POWER, iconName = "power"),
                defaultKey(ButtonKey.UP, iconName = "keyboard_arrow_up"),
                defaultKey(ButtonKey.DOWN, iconName = "keyboard_arrow_down"),
                defaultKey(ButtonKey.LEFT, iconName = "keyboard_arrow_left"),
                defaultKey(ButtonKey.RIGHT, iconName = "keyboard_arrow_right"),
                defaultKey(ButtonKey.OK, iconName = null),
                defaultKey(ButtonKey.BACK, iconName = "arrow_back"),
                defaultKey(ButtonKey.MENU, iconName = "menu"),
                defaultKey(ButtonKey.FOCUS_PLUS, iconName = "center_focus_strong"),
                defaultKey(ButtonKey.FOCUS_MINUS, iconName = "filter_center_focus")
            )
            DeviceCategory.CUSTOM,
            DeviceCategory.AUDIO -> listOf(
                defaultKey(ButtonKey.POWER, iconName = "power"),
                defaultKey(ButtonKey.VOLUME_UP, iconName = "volume_up"),
                defaultKey(ButtonKey.VOLUME_DOWN, iconName = "volume_down"),
                defaultKey(ButtonKey.UP, iconName = "keyboard_arrow_up"),
                defaultKey(ButtonKey.DOWN, iconName = "keyboard_arrow_down"),
                defaultKey(ButtonKey.LEFT, iconName = "keyboard_arrow_left"),
                defaultKey(ButtonKey.RIGHT, iconName = "keyboard_arrow_right"),
                defaultKey(ButtonKey.OK, iconName = null)
            )
        }
    }
}
