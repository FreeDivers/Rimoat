package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.i18n.localizedTimerDurationLabel
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
    val icon = controlIcon(action)
    val label = when (action) {
        AcAction.MODE -> localizedLabel(mode)
        AcAction.FAN -> AppStrings.get(R.string.control_fan_label, "%1\$s风", localizedLabel(fanSpeed))
        AcAction.WIND_DIRECTION -> when (windDirection) {
            AcWindDirection.AUTO -> AppStrings.get(R.string.control_auto_direction, "自动风向")
            AcWindDirection.UP -> AppStrings.get(R.string.control_air_up, "向上送风")
            AcWindDirection.MIDDLE -> AppStrings.get(R.string.control_air_horizontal, "水平送风")
            AcWindDirection.DOWN -> AppStrings.get(R.string.control_air_down, "向下送风")
        }
        AcAction.TIMER -> if (timerMinutes > 0) localizedTimerDurationLabel(timerMinutes) else localizedLabel(action)
        AcAction.TEMPERATURE_DISPLAY -> localizedLabel(temperatureDisplay)
        else -> localizedLabel(action)
    }
    val state = when (action) {
        AcAction.MODE -> localizedLabel(mode)
        AcAction.FAN -> localizedLabel(fanSpeed)
        AcAction.WIND_DIRECTION -> localizedLabel(windDirection)
        AcAction.TEMPERATURE_DISPLAY -> localizedLabel(temperatureDisplay)
        AcAction.TIMER -> if (timerMinutes > 0) localizedTimerDurationLabel(timerMinutes) else AppStrings.get(R.string.control_disabled, "关闭")
        else -> active?.let { if (it) AppStrings.get(R.string.control_enabled, "开启") else AppStrings.get(R.string.control_disabled, "关闭") }
    }
    return AcControlAppearance(label, icon, state, active)
}
