package org.qinyan.rimoat.i18n

import android.content.Context
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.ButtonKey

fun localizedTimerDurationLabel(minutes: Int): String = when {
    minutes == 0 -> AppStrings.get(R.string.control_disable_timer)
    minutes < 60 -> AppStrings.quantity(R.plurals.control_minutes, minutes, minutes)
    minutes % 60 == 0 -> {
        val hours = minutes / 60
        AppStrings.quantity(R.plurals.control_hours, hours, hours)
    }
    else -> {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        val hoursText = AppStrings.quantity(R.plurals.control_hours, hours, hours)
        val minutesText = AppStrings.quantity(R.plurals.control_minutes, remainingMinutes, remainingMinutes)
        AppStrings.get(R.string.control_hours_minutes, hoursText, minutesText)
    }
}

/** Built-in metadata controls translation; editable and unknown source names stay literal. */
fun localizedButtonLabel(button: RemoteButton, context: Context? = null): String {
    fun text(id: Int) = context?.getString(id) ?: AppStrings.get(id)
    val builtinKey = button.builtinLabel?.let { value -> ButtonKey.entries.firstOrNull { it.name == value } }
    if (button.builtinLabel != null) {
        if (builtinKey != null) return text(defaultLearningLabelRes(builtinKey, button.acAction))
        return button.catalogLabel?.fallbackKey?.let { text(it.labelRes) }
            ?: button.catalogLabel?.fallbackLabel?.let { text(it.labelRes) }
            ?: button.builtinLabel
    }
    if (button.isLearned && button.sourceKeyType == null && !button.id.startsWith("ac_function_")) return button.label
    button.acAction?.let { action ->
        if (button.label == action.name || button.label == action.label) return text(action.labelRes)
    }
    if (button.sourceKeyType != null || button.acAction != null || button.id.startsWith("ac_function_")) {
        return localizedCatalogKeyLabel(button, context)
            ?: button.catalogLabel?.fallbackKey?.let { text(it.labelRes) }
            ?: button.catalogLabel?.fallbackLabel?.let { text(it.labelRes) } ?: button.label
    }
    return button.label
}

/** A learned snapshot remains distinct from the protocol's adjustable state controls. */
fun localizedAcRawButtonLabel(button: RemoteButton, context: Context? = null): String {
    val label = localizedButtonLabel(button, context)
    if (!button.isLearned) return label
    return context?.getString(R.string.control_learned_button_name, label)
        ?: AppStrings.get(R.string.control_learned_button_name, label)
}
