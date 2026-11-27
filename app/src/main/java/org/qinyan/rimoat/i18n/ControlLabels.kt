package org.qinyan.rimoat.i18n

import android.content.Context
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.ButtonKey

fun localizedTimerDurationLabel(minutes: Int, context: Context? = null): String {
    fun text(id: Int, vararg args: Any) = context?.getString(id, *args) ?: AppStrings.get(id, *args)
    fun quantity(id: Int, value: Int) = context?.resources?.getQuantityString(id, value, value)
        ?: AppStrings.quantity(id, value, value)
    return when {
        minutes == 0 -> text(R.string.control_disable_timer)
        minutes < 60 -> quantity(R.plurals.control_minutes, minutes)
        minutes % 60 == 0 -> quantity(R.plurals.control_hours, minutes / 60)
        else -> text(R.string.control_hours_minutes,
            quantity(R.plurals.control_hours, minutes / 60),
            quantity(R.plurals.control_minutes, minutes % 60))
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
