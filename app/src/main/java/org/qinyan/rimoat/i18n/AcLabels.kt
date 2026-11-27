package org.qinyan.rimoat.i18n

import android.content.Context
import androidx.annotation.StringRes
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.*

val AcMode.labelRes: Int
    @StringRes get() = when (this) {
        AcMode.COOL -> R.string.control_cool
        AcMode.HEAT -> R.string.control_heat
        AcMode.DRY -> R.string.control_dry
        AcMode.FAN -> R.string.control_fan
        AcMode.AUTO -> R.string.control_auto
    }

val AcFanSpeed.labelRes: Int
    @StringRes get() = when (this) {
        AcFanSpeed.AUTO -> R.string.control_auto
        AcFanSpeed.LOW -> R.string.control_low
        AcFanSpeed.MEDIUM -> R.string.control_medium
        AcFanSpeed.HIGH -> R.string.control_high
    }

val AcWindDirection.labelRes: Int
    @StringRes get() = when (this) {
        AcWindDirection.AUTO -> R.string.control_auto
        AcWindDirection.UP -> R.string.control_upward
        AcWindDirection.MIDDLE -> R.string.control_center
        AcWindDirection.DOWN -> R.string.control_downward
    }

val AcTemperatureDisplay.labelRes: Int
    @StringRes get() = when (this) {
        AcTemperatureDisplay.OUTDOOR -> R.string.control_outdoor_temp
        AcTemperatureDisplay.INDOOR -> R.string.control_indoor_temp
        AcTemperatureDisplay.TARGET -> R.string.control_target_temp
        AcTemperatureDisplay.OFF -> R.string.control_display_off
    }

val AcAction.labelRes: Int
    @StringRes get() = when (this) {
        AcAction.POWER_ON -> R.string.control_power_on
        AcAction.POWER_OFF -> R.string.control_power_off
        AcAction.TEMP_UP -> R.string.control_temp_up
        AcAction.TEMP_DOWN -> R.string.control_temp_down
        AcAction.MODE -> R.string.control_mode
        AcAction.FAN -> R.string.control_fan_speed
        AcAction.SWING -> R.string.control_vertical_swing
        AcAction.HORIZONTAL_SWING -> R.string.control_horizontal_swing
        AcAction.WIND_DIRECTION -> R.string.control_wind_direction
        AcAction.LIGHT -> R.string.control_light
        AcAction.TURBO -> R.string.control_turbo
        AcAction.SLEEP -> R.string.control_sleep
        AcAction.VENTILATION -> R.string.control_ventilation
        AcAction.AUXILIARY_HEAT -> R.string.control_aux_heat
        AcAction.TIMER -> R.string.control_timer
        AcAction.DRY -> R.string.control_drying
        AcAction.ION -> R.string.control_ion
        AcAction.ECO -> R.string.control_eco
        AcAction.COMFORT -> R.string.control_comfort
        AcAction.TEMPERATURE_DISPLAY -> R.string.control_temp_display
        AcAction.QUICK_COOL -> R.string.control_quick_cool
        AcAction.QUICK_HEAT -> R.string.control_quick_heat
        AcAction.QUIET -> R.string.control_mute
        AcAction.CLEAN -> R.string.control_clean
    }

/** Shared JVM fallback; Android presentation always resolves the enum resource. */
fun localizedLabel(value: AcMode, context: Context? = null): String =
    context?.getString(value.labelRes) ?: AppStrings.get(value.labelRes, value.labelCn)

fun localizedLabel(value: AcFanSpeed, context: Context? = null): String =
    context?.getString(value.labelRes) ?: AppStrings.get(value.labelRes, value.labelCn)

fun localizedLabel(value: AcWindDirection, context: Context? = null): String =
    context?.getString(value.labelRes) ?: AppStrings.get(value.labelRes, value.labelCn)

fun localizedLabel(value: AcTemperatureDisplay, context: Context? = null): String =
    context?.getString(value.labelRes) ?: AppStrings.get(value.labelRes, value.labelCn)

fun localizedLabel(value: AcAction, context: Context? = null): String =
    context?.getString(value.labelRes) ?: AppStrings.get(value.labelRes, value.label)
