package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.data.*

fun panelKey(button: RemoteButton) = "key:${button.id}"
fun panelAction(action: AcAction) = "ac:${action.name}"

/** Spatial combinations retain their geometry and their complete signal set. */
fun fixedPanelKeys(profile: RemoteProfile): Set<String> {
    val power = profile.buttons.firstOrNull { it.iconName == "power" || it.id.contains("power") || it.label.contains("电源") || it.label == "开关" }
    val fixed = mutableSetOf<String>()
    power?.let { fixed += it.id }
    if (profile.category == DeviceCategory.TV) {
        val standard = tvStandardButtons(profile)
        (3..7).plus(11..14).forEach { standard[it]?.let { b -> fixed += b.id } }
        profile.buttons.filter { it.label.toIntOrNull() in 0..9 }.forEach { fixed += it.id }
    } else {
        val directions = profile.buttons.filter { it.label in setOf("上", "下", "左", "右", "OK") }
        if (directions.map { it.label }.containsAll(listOf("上", "下", "左", "右"))) fixed += directions.map { it.id }
        profile.buttons.filter { it.label.toIntOrNull() in 0..9 }.forEach { fixed += it.id }
    }
    return fixed
}

fun RemoteProfile.withPanelPresentation(): RemoteProfile {
    val fixed = fixedPanelKeys(this)
    return copy(buttons = buttons.filter { it.id in fixed || panelKey(it) !in panelLayout.hidden }
        .sortedBy { if (it.id in fixed) -1 else panelLayout.rank(panelKey(it)) })
}

/** Stable metadata takes precedence over old labels, even when a user renames a key. */
private fun acButtonMatchesAction(button: RemoteButton, action: AcAction): Boolean {
    button.acAction?.let { return it == action }
    AcAction.entries.firstOrNull { it.keyType == button.sourceKeyType }?.let { return it == action }
    val aliases = when (action) {
        AcAction.POWER_ON -> setOf("开机", "电源", "开关")
        AcAction.TEMP_UP -> setOf("温度+", "升温", "温度加")
        AcAction.TEMP_DOWN -> setOf("温度-", "降温", "温度减")
        AcAction.FAN -> setOf("风速", "风量")
        else -> setOf(action.label)
    }
    return button.label in aliases
}

/** The state control chooses the most explicit binding; every duplicate remains represented by that control. */
fun acButtonForAction(profile: RemoteProfile, action: AcAction): RemoteButton? =
    profile.buttons.filter { acButtonMatchesAction(it, action) }.minByOrNull { button ->
        when {
            button.acAction == action -> 0
            button.sourceKeyType == action.keyType -> 1
            else -> 2
        }
    }

fun acRawExtensionButtons(profile: RemoteProfile): List<RemoteButton> =
    profile.buttons.filter { button -> AcAction.entries.none { acButtonMatchesAction(button, it) } }
