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
        require(shortcuts.size <= MAX_SHORTCUTS && shortcuts.distinct().size == shortcuts.size) { AppStrings.get(R.string.widget_max_shortcuts, MAX_SHORTCUTS) }
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
    id == WidgetCommands.POWER -> widgetString(context, R.string.widget_power)
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
    DeviceCategory.AC -> widgetString(context, R.string.widget_category_ac)
    DeviceCategory.TV -> widgetString(context, R.string.widget_category_tv)
    DeviceCategory.FAN -> widgetString(context, R.string.widget_category_fan)
    DeviceCategory.AUDIO -> widgetString(context, R.string.widget_category_audio)
    DeviceCategory.PROJECTOR -> widgetString(context, R.string.widget_category_projector)
    DeviceCategory.CUSTOM -> widgetString(context, R.string.widget_category_remote)
}

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

fun lastCommand(profile: RemoteProfile, record: RemoteCommandRecord?, simulated: Boolean): WidgetCommand? {
    // Independent IR codes do not report volume, fan speed or appliance power. Show the actual command.
    val receipt = record?.takeIf { it.simulated == simulated }
    // New receipts use stable IDs; older local receipts resolve only an unambiguous label.
    // Deleted commands and records from the other transmitter mode must not imply a device state.
    val button = receipt?.let {
        if (it.buttonId != null) profile.buttons.firstOrNull { button -> button.id == it.buttonId }
        else profile.buttons.singleOrNull { button -> button.label == it.label }
    }?.takeIf { it.hasSignal }
    return button?.let { WidgetCommand(it.id, it.label, it.iconName) }
}

private fun widgetString(context: Context?, @StringRes id: Int, vararg args: Any): String =
    context?.getString(id, *args) ?: AppStrings.get(id, *args)

internal fun widgetShortcutDescription(
    context: Context,
    profile: RemoteProfile?,
    command: String,
    state: String?,
    failure: String?
): String {
    val room = profile?.room?.takeIf { it.isNotBlank() }
    val description = when {
        profile == null -> if (state == null) command
            else context.getString(R.string.widget_control_state_description, command, state)
        room != null && state != null -> context.getString(
            R.string.widget_shortcut_room_state_description, profile.name, room, command, state)
        room != null -> context.getString(R.string.widget_shortcut_room_description, profile.name, room, command)
        state != null -> context.getString(R.string.widget_shortcut_state_description, profile.name, command, state)
        else -> context.getString(R.string.widget_shortcut_description, profile.name, command)
    }
    return if (failure == null) description
        else context.getString(R.string.widget_shortcut_failure_description, description, failure)
}
