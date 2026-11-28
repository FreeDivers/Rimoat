package org.qinyan.rimoat.i18n

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import java.util.Locale
import org.qinyan.rimoat.R

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
