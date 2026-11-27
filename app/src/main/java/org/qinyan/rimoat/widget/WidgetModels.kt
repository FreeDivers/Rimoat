package org.qinyan.rimoat.widget

import android.content.Context
import androidx.annotation.StringRes
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.i18n.localizedButtonLabel
import org.qinyan.rimoat.ui.components.isPowerKey

import org.qinyan.rimoat.data.*
import kotlinx.serialization.Serializable

@Serializable enum class WidgetKind { SINGLE, PANEL, STATUS }
@Serializable enum class WidgetArrangement { GRID, LIST }

@Serializable
data class WidgetShortcut(val remoteId: String, val buttonId: String)

@Serializable
data class WidgetConfiguration(
    val kind: WidgetKind,
    val remoteId: String = "",
    val shortcuts: List<WidgetShortcut> = emptyList(),
    val arrangement: WidgetArrangement = WidgetArrangement.GRID
) {
    fun validate() {
        require(shortcuts.size <= MAX_SHORTCUTS && shortcuts.distinct().size == shortcuts.size) { AppStrings.get(R.string.widget_max_shortcuts) }
        require(kind != WidgetKind.SINGLE || shortcuts.size <= 1) { AppStrings.get(R.string.widget_single_shortcut_only) }
        require(kind == WidgetKind.PANEL || shortcuts.all { it.remoteId == remoteId }) { AppStrings.get(R.string.widget_wrong_remote) }
        require(shortcuts.all { it.remoteId.isNotBlank() && it.buttonId.isNotBlank() }) { AppStrings.get(R.string.widget_invalid_shortcut) }
    }
    companion object { const val MAX_SHORTCUTS = 12 }
}

data class WidgetCommand(val id: String, val label: String, val iconName: String?)

internal fun widgetAcAction(profile: RemoteProfile?, command: WidgetCommand): AcAction? =
    if (command.id.startsWith(WidgetCommands.PREFIX)) {
        if (command.id == WidgetCommands.POWER) AcAction.POWER_ON
        else AcAction.entries.firstOrNull { it.name == command.id.removePrefix(WidgetCommands.PREFIX) }
    } else profile?.buttons?.firstOrNull { it.id == command.id }?.acAction

@Suppress("UNUSED_PARAMETER")
internal fun WidgetCommand.isPower(profile: RemoteProfile?, context: Context? = null): Boolean =
    if (id.startsWith(WidgetCommands.PREFIX)) {
        widgetAcAction(profile, this) in setOf(AcAction.POWER_ON, AcAction.POWER_OFF)
    } else profile?.buttons?.firstOrNull { it.id == id }?.isPowerKey() == true

/** Translate built-in labels only at presentation; persisted user names stay untouched. */
fun WidgetCommand.displayLabel(profile: RemoteProfile?, context: Context? = null): String = when {
    id == WidgetCommands.POWER -> widgetString(context, R.string.widget_power, "电源")
    id.startsWith(WidgetCommands.PREFIX) -> AcAction.entries.firstOrNull {
        it.name == id.removePrefix(WidgetCommands.PREFIX)
    }?.let { localizedLabel(it, context) } ?: label
    else -> profile?.buttons?.firstOrNull { it.id == id }?.let {
        if (profile.category == DeviceCategory.AC && profile.acProtocolId != null && !profile.useStaticAcButtons)
            org.qinyan.rimoat.i18n.localizedAcRawButtonLabel(it, context)
        else localizedButtonLabel(it, context)
    } ?: label
}

object WidgetCommands {
    const val POWER = "@ac:power"
    const val PREFIX = "@ac:"

    fun choices(profile: RemoteProfile, actions: Set<AcAction> = AcAction.entries.toSet()): List<WidgetCommand> {
        val dynamic = profile.category == DeviceCategory.AC && profile.acProtocolId != null && !profile.useStaticAcButtons
        val commands = profile.buttons.filter {
            it.hasSignal || (dynamic && it.acAction != null && it.acAction in actions)
        }.map { WidgetCommand(it.id, it.label, it.iconName) }
        if (!dynamic) return commands
        val preferred = buildList {
            if (AcAction.POWER_ON in actions && AcAction.POWER_OFF in actions) add(WidgetCommand(POWER, POWER, "power"))
            for ((action, icon) in listOf(AcAction.TEMP_DOWN to "remove", AcAction.TEMP_UP to "add",
                AcAction.MODE to "mode", AcAction.FAN to "fan", AcAction.SWING to "swing")) {
                if (action in actions) add(WidgetCommand(PREFIX + action.name, action.label, icon))
            }
        }
        return preferred + commands
    }

    fun default(profile: RemoteProfile, choices: List<WidgetCommand> = choices(profile), context: Context? = null): WidgetShortcut? {
        val command = choices.firstOrNull { it.isPower(profile) }
            ?: choices.firstOrNull() ?: return null
        return WidgetShortcut(profile.id, command.id)
    }
}

fun DeviceCategory.widgetLabel(context: Context? = null): String = when (this) {
    DeviceCategory.AC -> widgetString(context, R.string.widget_category_ac, "空调")
    DeviceCategory.TV -> widgetString(context, R.string.widget_category_tv, "电视")
    DeviceCategory.FAN -> widgetString(context, R.string.widget_category_fan, "风扇")
    DeviceCategory.AUDIO -> widgetString(context, R.string.widget_category_audio, "音响")
    DeviceCategory.PROJECTOR -> widgetString(context, R.string.widget_category_projector, "投影仪")
    DeviceCategory.CUSTOM -> widgetString(context, R.string.widget_category_remote, "遥控器")
}

data class WidgetDeviceStatus(val primary: String, val secondary: String, val details: List<String>,
                              val command: WidgetCommand? = null)

fun widgetAcSettings(profile: RemoteProfile, record: RemoteCommandRecord?, simulated: Boolean): AcSettings =
    if (simulated && record?.demoBase == profile.acSettings) record.demoSettings ?: profile.acSettings else profile.acSettings

fun widgetCommandEnabled(shortcut: WidgetShortcut, profile: RemoteProfile?, record: RemoteCommandRecord?, simulated: Boolean): Boolean {
    if (profile == null) return false
    val temperature = widgetAcSettings(profile, record, simulated).temperature
    return when (shortcut.buttonId) {
        WidgetCommands.PREFIX + "TEMP_UP" -> temperature < 30
        WidgetCommands.PREFIX + "TEMP_DOWN" -> temperature > 16
        else -> true
    }
}

fun widgetDeviceStatus(profile: RemoteProfile, record: RemoteCommandRecord?, simulated: Boolean, context: Context? = null): WidgetDeviceStatus {
    if (profile.category == DeviceCategory.AC && profile.acProtocolId != null && !profile.useStaticAcButtons) {
        val state = widgetAcSettings(profile, record, simulated)
        return WidgetDeviceStatus(if (state.powerOn) "${state.temperature}°" else widgetString(context, R.string.widget_turned_off, "已关闭"),
            if (state.powerOn) localizedLabel(state.mode, context) else "${state.temperature}°  ${localizedLabel(state.mode, context)}",
            buildList {
                add(widgetString(context, R.string.widget_fan_value, "风速  %1\$s", localizedLabel(state.fanSpeed, context)))
                add(widgetString(context, R.string.widget_vertical_swing_value, "上下扫风  %1\$s", if (state.isSwingOn) widgetString(context, R.string.widget_on, "开") else widgetString(context, R.string.widget_off, "关")))
                if (state.horizontalSwingOn) add(widgetString(context, R.string.widget_horizontal_swing_on, "左右扫风  开"))
                if (state.timerMinutes > 0) add(widgetString(context, R.string.widget_timer_value, "定时  %1\$d 分钟", state.timerMinutes))
                if (state.sleepOn) add(widgetString(context, R.string.widget_sleep_on, "睡眠  开"))
                if (state.ecoOn) add(widgetString(context, R.string.widget_eco_on, "节能  开"))
                if (state.turboOn) add(widgetString(context, R.string.widget_turbo_on, "超强  开"))
            })
    }
    // Independent IR codes do not report volume, fan speed or appliance power. Show the actual command.
    val receipt = record?.takeIf { it.simulated == simulated }
    // New receipts use stable IDs; older local receipts resolve only an unambiguous label.
    // Deleted commands and records from the other transmitter mode must not imply a device state.
    val button = receipt?.let {
        if (it.buttonId != null) profile.buttons.firstOrNull { button -> button.id == it.buttonId }
        else profile.buttons.singleOrNull { button -> button.label == it.label }
    }?.takeIf { it.hasSignal }
    val command = button?.let { WidgetCommand(it.id, it.label, it.iconName) }
    val count = profile.buttons.count { it.hasSignal }
    val buttonCount = context?.resources?.getQuantityString(R.plurals.widget_available_buttons, count, count)
        ?: AppStrings.quantity(R.plurals.widget_available_buttons, count, count)
    return WidgetDeviceStatus(command?.displayLabel(profile, context) ?: profile.category.widgetLabel(context),
        profile.brand.ifBlank { profile.category.widgetLabel(context) },
        listOfNotNull(profile.room.takeIf { it.isNotBlank() }, buttonCount), command)
}

private fun widgetString(context: Context?, @StringRes id: Int, fallback: String, vararg args: Any): String =
    context?.getString(id, *args) ?: AppStrings.get(id, *args)
