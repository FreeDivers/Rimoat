package org.qinyan.rimoat.data

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
/** Explicit full-state choices; raw/learned toggle keys cannot promise a particular appliance state. */
data class AcStateChoice(val action: AcAction, val index: Int, val label: String) {
    fun applyTo(state: AcSettings): AcSettings {
        require(this in forAction(action))
        val next = state.apply(action)
        return when (action) {
            AcAction.MODE -> next.copy(mode = AcMode.entries[index])
            AcAction.FAN -> next.copy(fanSpeed = AcFanSpeed.entries[index])
            AcAction.WIND_DIRECTION -> next.copy(windDirection = AcWindDirection.entries[index])
            AcAction.TEMPERATURE_DISPLAY -> next.copy(temperatureDisplay = AcTemperatureDisplay.entries[index])
            else -> error(AppStrings.get(R.string.msg_button_state_unavailable, "此按键没有可直选状态"))
        }
    }

    fun isSelected(state: AcSettings): Boolean = when (action) {
        AcAction.MODE -> state.mode.ordinal == index
        AcAction.FAN -> state.fanSpeed.ordinal == index
        AcAction.WIND_DIRECTION -> state.windDirection.ordinal == index
        AcAction.TEMPERATURE_DISPLAY -> state.temperatureDisplay.ordinal == index
        else -> false
    }

    companion object {
        fun forAction(action: AcAction): List<AcStateChoice> = when (action) {
            AcAction.MODE -> AcMode.entries.map { it.labelCn }
            AcAction.FAN -> AcFanSpeed.entries.map { it.labelCn }
            AcAction.WIND_DIRECTION -> AcWindDirection.entries.map { it.labelCn }
            AcAction.TEMPERATURE_DISPLAY -> AcTemperatureDisplay.entries.map { it.labelCn }
            else -> emptyList()
        }.mapIndexed { index, label -> AcStateChoice(action, index, label) }
    }
}
