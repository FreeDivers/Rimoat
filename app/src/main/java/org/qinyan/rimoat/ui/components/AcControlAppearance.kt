package org.qinyan.rimoat.ui.components

import android.content.Context
import androidx.annotation.StringRes
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

fun AcSettings.controlAppearance(action: AcAction, context: Context? = null): AcControlAppearance {
    fun text(@StringRes id: Int, vararg args: Any): String =
        context?.getString(id, *args) ?: AppStrings.get(id, *args)
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
        AcAction.MODE -> localizedLabel(mode, context)
        AcAction.FAN -> text(R.string.control_fan_label, localizedLabel(fanSpeed, context))
        AcAction.WIND_DIRECTION -> when (windDirection) {
            AcWindDirection.AUTO -> text(R.string.control_auto_direction)
            AcWindDirection.UP -> text(R.string.control_air_up)
            AcWindDirection.MIDDLE -> text(R.string.control_air_horizontal)
            AcWindDirection.DOWN -> text(R.string.control_air_down)
        }
        AcAction.TIMER -> if (timerMinutes > 0) localizedTimerDurationLabel(timerMinutes, context) else localizedLabel(action, context)
        AcAction.TEMPERATURE_DISPLAY -> localizedLabel(temperatureDisplay, context)
        else -> localizedLabel(action, context)
    }
    val state = when (action) {
        AcAction.MODE -> localizedLabel(mode, context)
        AcAction.FAN -> localizedLabel(fanSpeed, context)
        AcAction.WIND_DIRECTION -> localizedLabel(windDirection, context)
        AcAction.TEMPERATURE_DISPLAY -> localizedLabel(temperatureDisplay, context)
        AcAction.TIMER -> if (timerMinutes > 0) localizedTimerDurationLabel(timerMinutes, context) else text(R.string.control_disabled)
        else -> active?.let { if (it) text(R.string.control_enabled) else text(R.string.control_disabled) }
    }
    return AcControlAppearance(label, icon, state, active)
}
