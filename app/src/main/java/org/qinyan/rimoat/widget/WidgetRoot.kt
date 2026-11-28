package org.qinyan.rimoat.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.*
import androidx.glance.*
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppLanguage
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.data.*

internal val LocalWidgetFontScale = staticCompositionLocalOf { 1f }
internal val LocalWidgetFeedback = staticCompositionLocalOf<WidgetFeedback?> { null }

@android.annotation.SuppressLint("LocalContextConfigurationRead")
@Composable
internal fun RemoteWidgetContent(kind: WidgetKind, widgetId: Int, snapshot: WidgetSnapshot, feedback: WidgetFeedback?) {
    val sourceContext = LocalContext.current
    // Glance provides its own LocalContext, but no Compose UI LocalConfiguration.
    // Snapshot localeTags triggers recomposition when the application locale changes.
    val context = if (snapshot.localeTags.isBlank()) AppLanguage.wrap(sourceContext) else
        sourceContext.createConfigurationContext(android.content.res.Configuration(sourceContext.resources.configuration).apply {
            setLocales(android.os.LocaleList.forLanguageTags(snapshot.localeTags))
        })
    val size = LocalSize.current
    val compact = size.height < WidgetLayoutSpec.CompactHeight
    val wide = size.width >= WidgetLayoutSpec.WideWidth
    val tall = size.height >= WidgetLayoutSpec.TallHeight
    val configuration = snapshot.configurations[widgetId]
    val profile = snapshot.profiles.firstOrNull { it.id == configuration?.remoteId }
    val open = actionStartActivity(remoteWidgetIntent(context, profile?.id.orEmpty(),
        if (kind == WidgetKind.PANEL || profile == null) "list" else "remote"))
    // Launcher can merge non-actionable descendants into the clickable widget root.
    // Keep the current reading in that root's name instead of hiding it behind "Open".
    val openDescription = when {
        kind == WidgetKind.PANEL -> context.getString(R.string.widget_open_list)
        profile?.acProtocolId != null && !profile.useStaticAcButtons && !configuration?.shortcuts.isNullOrEmpty() -> {
            val settings = widgetAcSettings(profile, snapshot.records[profile.id], snapshot.simulated)
            val status = context.getString(R.string.control_ac_status,
                context.getString(if (snapshot.simulated) R.string.control_simulation_setting else R.string.control_target_temp),
                context.getString(if (settings.powerOn) R.string.widget_on else R.string.widget_off), settings.temperature,
                localizedLabel(settings.mode, context), localizedLabel(settings.fanSpeed, context))
            context.getString(R.string.widget_open_remote_status, profile.name, status)
        }
        else -> context.getString(R.string.widget_open_remote, profile?.name ?: context.getString(R.string.widget_category_remote))
    }
    var background = GlanceModifier.fillMaxSize().appWidgetBackground()
        .background(ImageProvider(R.drawable.widget_background), colorFilter = ColorFilter.tint(LocalWidgetSurfaces.current.background))
        .clickable(open)
        .semantics { contentDescription = openDescription }
    if (Build.VERSION.SDK_INT >= 31) background = background.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    CompositionLocalProvider(LocalContext provides context, LocalWidgetFontScale provides snapshot.fontScale, LocalWidgetRtl provides snapshot.isRtl,
        LocalWidgetFeedback provides feedback) {
    Box(background) {
    // Keep content insets below the root that Launcher may measure/pad/clip itself.
    Box(GlanceModifier.fillMaxSize().padding(horizontal = if (compact) WidgetLayoutSpec.CompactHorizontalPadding else WidgetLayoutSpec.ContentPadding,
        vertical = if (compact) WidgetLayoutSpec.CompactVerticalPadding else WidgetLayoutSpec.ContentPadding)) {
        when {
            !snapshot.loaded -> StatusMessage(LocalContext.current.getString(R.string.widget_loading), "", open)
            snapshot.error != null -> StatusMessage(LocalContext.current.getString(R.string.widget_read_failed), LocalContext.current.getString(R.string.widget_tap_retry), actionRunCallback<ReloadWidgets>())
            snapshot.profiles.isEmpty() -> StatusMessage(LocalContext.current.getString(R.string.widget_no_remotes), LocalContext.current.getString(R.string.widget_add_remote), actionStartActivity(remoteWidgetIntent(context, destination = "add")))
            configuration == null || configuration.shortcuts.isEmpty() ->
                StatusMessage("Rimoat", LocalContext.current.getString(R.string.widget_select_remote), actionStartActivity(configurationIntent(context, widgetId)))
            kind != WidgetKind.PANEL && profile == null ->
                StatusMessage(LocalContext.current.getString(R.string.widget_remote_removed), LocalContext.current.getString(R.string.widget_select_again), actionStartActivity(configurationIntent(context, widgetId)))
            kind == WidgetKind.SINGLE -> SingleContent(widgetId, profile!!, configuration, snapshot, feedback, compact, wide)
            kind == WidgetKind.PANEL -> PanelContent(widgetId, configuration, snapshot, feedback, compact, wide)
            else -> DeviceContent(widgetId, profile!!, configuration, snapshot, feedback, compact, wide, tall)
        }
    }
    }
    }
}

@Composable
internal fun StatusMessage(title: String, actionLabel: String, action: Action) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize().clickable(action), verticalAlignment = Alignment.CenterVertically) {
        if (LocalSize.current.height < 140.dp && LocalWidgetFontScale.current >= WidgetLayoutSpec.LargeFontScale) {
            Text(actionLabel.ifBlank { title }, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp), maxLines = 1,
                modifier = GlanceModifier.semantics {
                    contentDescription = context.getString(R.string.widget_status_action_description, title, actionLabel)
                })
        } else {
            Text(title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            if (actionLabel.isNotBlank()) Text(actionLabel, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }
}
