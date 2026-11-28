package org.qinyan.rimoat.i18n

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import java.util.Locale
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.ButtonKey

fun localizedTimerDurationLabel(minutes: Int, context: Context? = null): String {
    val resolved = context ?: AppStrings.resourceContextOrNull()
    if (minutes == 0) return resolved?.getString(R.string.control_disable_timer)
        ?: AppStrings.get(R.string.control_disable_timer)
    val locale = resolved?.resources?.configuration?.locales?.takeIf { !it.isEmpty }?.get(0)
        ?: Locale.getDefault()
    val hours = minutes / 60
    val remainder = minutes % 60
    val measures = buildList {
        if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
        if (remainder > 0) add(Measure(remainder, MeasureUnit.MINUTE))
    }
    return runCatching {
        MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
            .formatMeasures(*measures.toTypedArray())
    }.getOrElse {
        fun quantity(id: Int, value: Int) = resolved?.resources?.getQuantityString(id, value, value)
            ?: AppStrings.quantity(id, value, value)
        when {
            hours == 0 -> quantity(R.plurals.control_minutes, remainder)
            remainder == 0 -> quantity(R.plurals.control_hours, hours)
            else -> "${quantity(R.plurals.control_hours, hours)} ${quantity(R.plurals.control_minutes, remainder)}"
        }
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
