package org.qinyan.rimoat.i18n

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.qinyan.rimoat.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone

/** A per-display formatter: application locale, system clock preference, and explicit local zone. */
fun localizedDateTimeFormatter(
    context: Context,
    includeDate: Boolean,
    use24HourClock: Boolean = DateFormat.is24HourFormat(context),
    timeZone: TimeZone = TimeZone.getDefault()
): java.text.DateFormat {
    val locale = context.resources.configuration.locales[0]
    val skeleton = (if (includeDate) "yMMMd" else "") + if (use24HourClock) "Hm" else "hm"
    return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).apply {
        this.timeZone = timeZone
    }
}

/** Returning from system settings must also refresh the clock preference and time zone. */
@Composable
internal fun rememberLocalizedDateTimeFormatter(includeDate: Boolean): java.text.DateFormat {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumeGeneration by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeGeneration++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return remember(context, configuration, includeDate, resumeGeneration) {
        localizedDateTimeFormatter(context, includeDate)
    }
}

internal fun timerTargetDayResource(now: Long, minutes: Int, timeZone: TimeZone): Int {
    val today = GregorianCalendar(timeZone).apply { timeInMillis = now }
    val end = GregorianCalendar(timeZone).apply { timeInMillis = now + minutes * 60_000L }
    val sameDay = today.get(Calendar.ERA) == end.get(Calendar.ERA) &&
        today.get(Calendar.YEAR) == end.get(Calendar.YEAR) &&
        today.get(Calendar.DAY_OF_YEAR) == end.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) R.string.control_today_time else R.string.control_tomorrow_time
}

fun localizedTimerTargetLabel(
    context: Context,
    now: Long,
    minutes: Int,
    formatter: java.text.DateFormat = localizedDateTimeFormatter(context, includeDate = false)
): String = context.getString(
    timerTargetDayResource(now, minutes, formatter.timeZone),
    formatter.format(Date(now + minutes * 60_000L))
)
