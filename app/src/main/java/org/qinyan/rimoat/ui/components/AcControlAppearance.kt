package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedLabel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.data.AcFanSpeed
import org.qinyan.rimoat.data.AcMode
import org.qinyan.rimoat.data.AcSettings
import org.qinyan.rimoat.data.AcTemperatureDisplay
import org.qinyan.rimoat.data.AcWindDirection

/** Presentation of the existing committed/demo setting, never an appliance acknowledgement. */
data class AcControlAppearance(
    val label: String,
    val icon: ImageVector,
    val state: String?,
    val active: Boolean? = null
)

fun AcSettings.controlAppearance(action: AcAction): AcControlAppearance {
    val active = when (action) {
        AcAction.SWING -> isSwingOn
        AcAction.HORIZONTAL_SWING -> horizontalSwingOn
        AcAction.LIGHT -> lightOn
        AcAction.TURBO -> turboOn
        AcAction.SLEEP -> sleepOn
        AcAction.VENTILATION -> ventilationOn
        AcAction.AUXILIARY_HEAT -> auxiliaryHeatOn
        AcAction.TIMER -> timerMinutes > 0
        AcAction.DRY -> dryOn
        AcAction.ION -> ionOn
        AcAction.ECO -> ecoOn
        AcAction.COMFORT -> comfortOn
        AcAction.QUIET -> quietOn
        else -> null
    }
    val icon = when (action) {
        AcAction.MODE -> when (mode) {
            AcMode.COOL -> Icons.Rounded.AcUnit
            AcMode.HEAT -> Icons.Rounded.WbSunny
            AcMode.DRY -> Icons.Rounded.WaterDrop
            AcMode.FAN -> Icons.Rounded.Air
            AcMode.AUTO -> Icons.Rounded.AutoMode
        }
        AcAction.FAN -> when (fanSpeed) {
            AcFanSpeed.AUTO -> Icons.Rounded.AutoMode
            AcFanSpeed.LOW -> Icons.Rounded.SignalCellularAlt1Bar
            AcFanSpeed.MEDIUM -> Icons.Rounded.SignalCellularAlt2Bar
            AcFanSpeed.HIGH -> Icons.Rounded.SignalCellularAlt
        }
        AcAction.SWING -> if (isSwingOn) Icons.Rounded.SwapVert else Icons.Rounded.VerticalAlignCenter
        AcAction.HORIZONTAL_SWING -> if (horizontalSwingOn) Icons.Rounded.SwapHoriz else Icons.Rounded.HorizontalRule
        AcAction.WIND_DIRECTION -> when (windDirection) {
            AcWindDirection.AUTO -> Icons.Rounded.UnfoldMore
            AcWindDirection.UP -> Icons.Rounded.KeyboardDoubleArrowUp
            AcWindDirection.MIDDLE -> Icons.Rounded.DragHandle
            AcWindDirection.DOWN -> Icons.Rounded.KeyboardDoubleArrowDown
        }
        AcAction.LIGHT -> Icons.Rounded.Lightbulb
        AcAction.TURBO -> Icons.Rounded.Bolt
        AcAction.SLEEP -> if (sleepOn) Icons.Rounded.Bedtime else Icons.Rounded.BedtimeOff
        AcAction.VENTILATION -> Icons.Rounded.Air
        AcAction.AUXILIARY_HEAT -> Icons.Rounded.WbSunny
        AcAction.TIMER -> if (timerMinutes > 0) Icons.Rounded.Timer else Icons.Rounded.TimerOff
        AcAction.DRY -> Icons.Rounded.WaterDrop
        AcAction.ION -> Icons.Rounded.Grain
        AcAction.ECO -> Icons.Rounded.Eco
        AcAction.COMFORT -> Icons.Rounded.Spa
        AcAction.TEMPERATURE_DISPLAY -> when (temperatureDisplay) {
            AcTemperatureDisplay.OUTDOOR -> Icons.Rounded.Landscape
            AcTemperatureDisplay.INDOOR -> Icons.Rounded.Home
            AcTemperatureDisplay.TARGET -> Icons.Rounded.Thermostat
            AcTemperatureDisplay.OFF -> Icons.Rounded.VisibilityOff
        }
        AcAction.QUICK_COOL -> Icons.Rounded.AcUnit
        AcAction.QUICK_HEAT -> Icons.Rounded.WbSunny
        AcAction.QUIET -> if (quietOn) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp
        AcAction.CLEAN -> Icons.Rounded.CleaningServices
        AcAction.POWER_ON, AcAction.POWER_OFF -> Icons.Rounded.PowerSettingsNew
        AcAction.TEMP_UP -> Icons.Rounded.Add
        AcAction.TEMP_DOWN -> Icons.Rounded.Remove
    }
    val label = when (action) {
        AcAction.MODE -> localizedLabel(mode.labelCn)
        AcAction.FAN -> AppStrings.get(R.string.control_fan_label, "%1\$s风", localizedLabel(fanSpeed.labelCn))
        AcAction.WIND_DIRECTION -> when (windDirection) {
            AcWindDirection.AUTO -> AppStrings.get(R.string.control_auto_direction, "自动风向")
            AcWindDirection.UP -> AppStrings.get(R.string.control_air_up, "向上送风")
            AcWindDirection.MIDDLE -> AppStrings.get(R.string.control_air_horizontal, "水平送风")
            AcWindDirection.DOWN -> AppStrings.get(R.string.control_air_down, "向下送风")
        }
        AcAction.TIMER -> if (timerMinutes > 0) AppStrings.get(R.string.control_minutes, "%1\$d 分钟", timerMinutes) else localizedLabel(action.label)
        AcAction.TEMPERATURE_DISPLAY -> localizedLabel(temperatureDisplay.labelCn)
        else -> localizedLabel(action.label)
    }
    val state = when (action) {
        AcAction.MODE -> localizedLabel(mode.labelCn)
        AcAction.FAN -> localizedLabel(fanSpeed.labelCn)
        AcAction.WIND_DIRECTION -> localizedLabel(windDirection.labelCn)
        AcAction.TEMPERATURE_DISPLAY -> localizedLabel(temperatureDisplay.labelCn)
        AcAction.TIMER -> if (timerMinutes > 0) AppStrings.get(R.string.control_minutes, "%1\$d 分钟", timerMinutes) else AppStrings.get(R.string.control_disabled, "关闭")
        else -> active?.let { if (it) AppStrings.get(R.string.control_enabled, "开启") else AppStrings.get(R.string.control_disabled, "关闭") }
    }
    return AcControlAppearance(label, icon, state, active)
}
