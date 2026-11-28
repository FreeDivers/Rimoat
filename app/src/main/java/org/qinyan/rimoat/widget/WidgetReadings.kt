package org.qinyan.rimoat.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.i18n.localizedTimerDurationLabel
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.ui.components.controlAppearance
import org.qinyan.rimoat.ui.components.getCategoryIcon

@Composable
internal fun Identity(profile: RemoteProfile, modifier: GlanceModifier, compact: Boolean = false,
                     showRoom: Boolean = true) {
    val largeFont = LocalWidgetFontScale.current >= WidgetLayoutSpec.LargeFontScale
    val context = LocalContext.current
    Column(modifier.clickable(actionStartActivity(remoteWidgetIntent(LocalContext.current, profile.id)))
        .semantics { contentDescription = if (profile.room.isBlank()) context.getString(R.string.widget_open_remote, profile.name) else context.getString(R.string.widget_open_remote_room, profile.name, profile.room) },
        verticalAlignment = Alignment.CenterVertically) {
        Text(profile.name,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = if (compact) 14.sp else 18.sp,
            fontWeight = FontWeight.Bold), maxLines = 1)
        if (showRoom && !(compact && largeFont)) {
            if (profile.room.isNotBlank()) Text(profile.room, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
        }
    }
}

@Composable
internal fun IndependentReading(profile: RemoteProfile, snapshot: WidgetSnapshot, compact: Boolean = false) {
    val context = LocalContext.current
    val status = widgetDeviceStatus(profile, snapshot.records[profile.id], snapshot.simulated, context)
    val command = status.command
    val description = LocalContext.current.getString(R.string.widget_device_command, profile.category.widgetLabel(context), command?.displayLabel(profile, context).orEmpty()).trim()
    val iconSize = if (compact) 24.dp else 32.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Category and command icons describe local commands, never a guessed power/volume/speed value.
        if (!compact || command == null) {
            Image(widgetImage(getCategoryIcon(profile)), profile.category.widgetLabel(context),
                GlanceModifier.size(iconSize), colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant))
            if (command != null) Spacer(GlanceModifier.width(12.dp))
        }
        if (command != null) {
            val icon = widgetCommandVector(profile, command, snapshot, context)
            Image(widgetImage(icon), description, GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant))
        }
    }
}

@Composable
internal fun AcReading(profile: RemoteProfile, snapshot: WidgetSnapshot, compact: Boolean = false,
                      expanded: Boolean = false, showMode: Boolean = true) {
    val context = LocalContext.current
    val settings = widgetAcSettings(profile, snapshot.records[profile.id], snapshot.simulated)
    val label = if (settings.powerOn) context.getString(R.string.control_temperature_value, settings.temperature)
        else context.getString(R.string.widget_off)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold,
                fontSize = if (compact && LocalWidgetFontScale.current >= WidgetLayoutSpec.LargeFontScale) 16.sp else if (compact) 20.sp else if (LocalWidgetFontScale.current >= WidgetLayoutSpec.LargeFontScale) 24.sp else 36.sp),
                maxLines = 1, modifier = GlanceModifier.semantics {
                    contentDescription = if (settings.powerOn) context.getString(R.string.widget_set_temperature, settings.temperature) else context.getString(R.string.widget_power_off)
                })
            if (showMode) {
                Spacer(GlanceModifier.width(8.dp))
                Image(widgetImage(settings.controlAppearance(AcAction.MODE, context).icon), localizedLabel(settings.mode, context),
                    GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant))
            }
        }
        if (expanded) Row(GlanceModifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(AcAction.FAN, AcAction.SWING).forEachIndexed { index, action ->
                if (index > 0) Spacer(GlanceModifier.width(12.dp))
                val appearance = settings.controlAppearance(action, context)
                Image(widgetImage(appearance.icon), context.getString(R.string.widget_control_state_description,
                    localizedLabel(action, context), appearance.state.orEmpty()),
                    GlanceModifier.size(24.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant))
            }
            if (settings.timerMinutes > 0) {
                Spacer(GlanceModifier.width(12.dp))
                Image(widgetImage(Icons.Rounded.Timer), LocalContext.current.getString(R.string.widget_timer), GlanceModifier.size(20.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant))
                Text(localizedTimerDurationLabel(settings.timerMinutes, context), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
            }
        }
    }
}
