package org.qinyan.rimoat.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.glance.*
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import org.qinyan.rimoat.IrRemoteApplication
import org.qinyan.rimoat.MainActivity
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppLanguage
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.ui.components.controlAppearance
import org.qinyan.rimoat.ui.components.getCategoryIcon

class SingleRemoteWidget : RemoteAppWidget(WidgetKind.SINGLE)
class RemotePanelWidget : RemoteAppWidget(WidgetKind.PANEL)
class DeviceStatusWidget : RemoteAppWidget(WidgetKind.STATUS)
private val LocalWidgetFontScale = staticCompositionLocalOf { 1f }
private val LocalWidgetFeedback = staticCompositionLocalOf<WidgetFeedback?> { null }

open class RemoteAppWidget(private val kind: WidgetKind) : GlanceAppWidget(errorUiLayout = R.layout.widget_error) {
    override val sizeMode = SizeMode.Responsive(WidgetLayoutSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as IrRemoteApplication
        app.widgets.awaitReady()
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val snapshot by app.widgets.state.collectAsState()
            val feedback by app.widgets.feedback.collectAsState()
            WidgetColorTheme(snapshot) {
                RemoteWidgetContent(kind, widgetId, snapshot, feedback[widgetId])
            }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        (context.applicationContext as IrRemoteApplication).widgets.store.remove(GlanceAppWidgetManager(context).getAppWidgetId(glanceId))
    }

    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        android.util.Log.e("RimoatWidget", "Composition failed for $appWidgetId", throwable)
        val views = RemoteViews(context.packageName, R.layout.widget_error)
        val localized = AppLanguage.wrap(context)
        views.setTextViewText(R.id.widget_error_title, localized.getString(R.string.widget_error_title))
        views.setTextViewText(R.id.widget_error_action, localized.getString(R.string.widget_error_action))
        views.setOnClickPendingIntent(R.id.widget_recover, PendingIntent.getActivity(context, appWidgetId,
            configurationIntent(context, appWidgetId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }
}

@Composable
internal fun WidgetColorTheme(snapshot: WidgetSnapshot, content: @Composable () -> Unit) {
    WidgetVisualTheme(snapshot, content)
}

fun configurationIntent(context: Context, id: Int) = Intent(context, WidgetConfigurationActivity::class.java)
    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)

fun remoteWidgetIntent(context: Context, remoteId: String = "", destination: String = "remote") =
    Intent(context, MainActivity::class.java).setAction("org.qinyan.rimoat.OPEN_WIDGET")
        .putExtra("widget_remote_id", remoteId).putExtra("widget_destination", destination)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

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
    val compact = size.height < 132.dp
    val wide = size.width >= 280.dp
    val tall = size.height >= 240.dp
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
    Box(GlanceModifier.fillMaxSize().padding(horizontal = if (compact) 8.dp else 12.dp,
        vertical = if (compact) 4.dp else 12.dp)) {
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
private fun StatusMessage(title: String, actionLabel: String, action: Action) {
    Column(GlanceModifier.fillMaxSize().clickable(action), verticalAlignment = Alignment.CenterVertically) {
        if (LocalSize.current.height < 140.dp && LocalWidgetFontScale.current >= 1.5f) {
            Text(actionLabel.ifBlank { title }, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp), maxLines = 1,
                modifier = GlanceModifier.semantics { contentDescription = "$title $actionLabel" })
        } else {
            Text(title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            if (actionLabel.isNotBlank()) Text(actionLabel, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }
}

@Composable
private fun Identity(profile: RemoteProfile, modifier: GlanceModifier, compact: Boolean = false,
                     showRoom: Boolean = true) {
    val largeFont = LocalWidgetFontScale.current >= 1.5f
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
private fun SingleContent(id: Int, profile: RemoteProfile, config: WidgetConfiguration, state: WidgetSnapshot,
                          feedback: WidgetFeedback?, compact: Boolean, wide: Boolean) {
    val ac = profile.acProtocolId != null && !profile.useStaticAcButtons
    val largeIdentity = !compact && wide && LocalWidgetFontScale.current >= 1.5f
    val showRoom = !(largeIdentity && ac && LocalSize.current.height < 208.dp)
    val identityHeight = when {
        largeIdentity && showRoom -> 80.dp
        largeIdentity -> 56.dp
        else -> 48.dp
    }
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            Identity(profile, GlanceModifier.fillMaxWidth().height(identityHeight),
                compact = compact || !wide, showRoom = showRoom)
            if (!compact && ac) {
                Spacer(GlanceModifier.height(8.dp))
                AcReading(profile, state, compact = true, showMode = wide)
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        ShortcutIcon(id, config.shortcuts.first(), state, feedback?.busy == true || state.sending,
            primary = true, size = if (!compact && wide) 56.dp else 48.dp)
    }
}

@Composable
private fun PanelContent(id: Int, config: WidgetConfiguration, state: WidgetSnapshot,
                         feedback: WidgetFeedback?, compact: Boolean, wide: Boolean) {
    val largeFont = LocalWidgetFontScale.current >= 1.5f
    val busy = feedback?.busy == true || state.sending
    val size = LocalSize.current
    val contentWidth = size.width.value - if (compact) 16 else 24
    val contentHeight = size.height.value - if (compact) 8 else 24
    // Sparse panels use complete rows. A single shortcut never sits in a mostly empty grid.
    val list = config.arrangement == WidgetArrangement.LIST || largeFont || config.shortcuts.size <= 2
    if (compact) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            config.shortcuts.take(if (wide && !largeFont) 2 else 1).forEachIndexed { index, shortcut ->
                if (index > 0) Spacer(GlanceModifier.width(12.dp))
                Box(GlanceModifier.defaultWeight()) { PanelKey(id, shortcut, state, busy, list = true, showRoom = !largeFont) }
            }
        }
    } else {
        val columns = if (list) 1 else ((contentWidth + 8) / 64).toInt().coerceIn(1, 4)
        val showRoom = !largeFont && config.shortcuts.any { key -> state.profiles.any { it.id == key.remoteId && it.room.isNotBlank() } }
        val rowHeight = if (list) (if (largeFont && showRoom) 80 else 56) else (if (showRoom) 96 else 80)
        val rows = config.shortcuts.chunked(columns)
        val capacity = (contentHeight / rowHeight).toInt().coerceAtLeast(1)
        if (rows.size <= capacity || size.height < 240.dp) {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                rows.take(capacity).forEach { row ->
                    PanelRow(id, row, state, busy, list, showRoom, columns, rowHeight)
                }
            }
        } else LazyColumn(GlanceModifier.fillMaxSize()) {
            rows.forEachIndexed { index, row -> item(itemId = index.toLong()) {
                PanelRow(id, row, state, busy, list, showRoom, columns, rowHeight)
            } }
        }
    }
}

@Composable
private fun PanelRow(id: Int, shortcuts: List<WidgetShortcut>, state: WidgetSnapshot, busy: Boolean,
                     list: Boolean, showRoom: Boolean, columns: Int, height: Int) {
    Row(GlanceModifier.fillMaxWidth().height(height.dp), verticalAlignment = Alignment.CenterVertically) {
        shortcuts.forEach { shortcut ->
            Box(GlanceModifier.defaultWeight()) { PanelKey(id, shortcut, state, busy, list, showRoom) }
        }
        // Keep columns aligned between complete and incomplete rows, without stretching row height.
        repeat(columns - shortcuts.size) { Spacer(GlanceModifier.defaultWeight()) }
    }
}

@Composable
private fun PanelKey(id: Int, shortcut: WidgetShortcut, state: WidgetSnapshot, busy: Boolean,
                     list: Boolean, showRoom: Boolean) {
    val profile = state.profiles.firstOrNull { it.id == shortcut.remoteId }
    if (list) {
        Row(GlanceModifier.fillMaxWidth().height(48.dp).widgetContainer(LocalWidgetSurfaces.current.container),
            verticalAlignment = Alignment.CenterVertically) {
            if (profile != null) Identity(profile, GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 8.dp), compact = true)
            else Text(LocalContext.current.getString(R.string.widget_removed), modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = GlanceTheme.colors.error, fontSize = 14.sp), maxLines = 1)
            Spacer(GlanceModifier.width(8.dp))
            ShortcutIcon(id, shortcut, state, busy)
        }
    } else Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ShortcutIcon(id, shortcut, state, busy)
        Spacer(GlanceModifier.height(4.dp))
        Text(profile?.name ?: LocalContext.current.getString(R.string.widget_removed), modifier = GlanceModifier.fillMaxWidth(),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.glance.text.TextAlign.Center), maxLines = 1)
        if (showRoom && profile?.room?.isNotBlank() == true) Text(profile.room,
            modifier = GlanceModifier.fillMaxWidth(), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp, textAlign = androidx.glance.text.TextAlign.Center), maxLines = 1)
    }
}

@Composable
private fun DeviceContent(id: Int, profile: RemoteProfile, config: WidgetConfiguration, state: WidgetSnapshot,
                          feedback: WidgetFeedback?, compact: Boolean, wide: Boolean, tall: Boolean) {
    val ac = profile.category == DeviceCategory.AC && profile.acProtocolId != null && !profile.useStaticAcButtons
    val largeFont = LocalWidgetFontScale.current >= 1.5f
    val busy = feedback?.busy == true || state.sending
    val first = config.shortcuts.first()
    val rest = config.shortcuts.drop(1)
    val width = LocalSize.current.width.value - if (compact) 16 else 24
    val height = LocalSize.current.height.value - if (compact) 16 else 24
    val columns = ((width + 8) / 56).toInt().coerceIn(1, 5)
    if (compact) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                if (!largeFont) Text(profile.name, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                if (ac) AcReading(profile, state, compact = true, showMode = wide && !largeFont)
                else if (largeFont) Text(profile.name, style = TextStyle(color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                else IndependentReading(profile, state, compact = true)
            }
            Spacer(GlanceModifier.width(8.dp))
            ShortcutIcon(id, first, state, busy, primary = true)
        }
    } else if (tall && !wide && rest.size <= columns * 2) {
        // Keep the reading and both rows of common controls visible in narrow previews.
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            DeviceHeader(id, profile, first, state, busy, 48)
            Spacer(GlanceModifier.height(8.dp))
            Box(GlanceModifier.fillMaxWidth().height(48.dp).widgetContainer(LocalWidgetSurfaces.current.container),
                contentAlignment = Alignment.Center) {
                if (ac) AcReading(profile, state, compact = true) else IndependentReading(profile, state, compact = true)
            }
            rest.chunked(columns).forEach { row ->
                Spacer(GlanceModifier.height(8.dp))
                KeyRow(id, row, state, busy, GlanceModifier.fillMaxWidth().height(48.dp))
            }
        }
    } else if (!tall && !wide && (!largeFont || LocalSize.current.width < 224.dp)) {
        // At 160dp two 48dp keys plus a temperature cannot share one padded row.
        // Put the reading next to the primary key; keep secondary controls on their own row.
        // Taller two-column buckets (152x208) get a second key row instead of centering
        // one row inside the extra height.
        val headerHeight = if (largeFont) 80 else 56
        val available = height - headerHeight - if (rest.isEmpty()) 0 else 8
        val rowCount = if (rest.isEmpty()) 0 else ((available + 8) / 56).toInt().coerceIn(0, 2)
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(GlanceModifier.fillMaxWidth().height(headerHeight.dp)
                .widgetContainer(LocalWidgetSurfaces.current.container), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp,
                        fontWeight = FontWeight.Medium), maxLines = 1)
                    if (ac) AcReading(profile, state, compact = true, showMode = false)
                    else IndependentReading(profile, state, compact = true)
                }
                Spacer(GlanceModifier.width(8.dp))
                ShortcutIcon(id, first, state, busy, primary = true)
            }
            if (rowCount > 0) {
                Spacer(GlanceModifier.height(8.dp))
                rest.chunked(columns).take(rowCount).forEachIndexed { index, row ->
                    if (index > 0) Spacer(GlanceModifier.height(8.dp))
                    KeyRow(id, row, state, busy, GlanceModifier.fillMaxWidth())
                }
            }
        }
    } else {
        val rows = rest.chunked(columns)
        val headerHeight = if (largeFont && tall) 80 else if (largeFont) 56 else 48
        val readingHeight = if (largeFont) 56 else if (tall && ac) 88 else 40
        val needed = headerHeight + 12 + readingHeight + if (rows.isEmpty()) 0 else 12 + rows.size * 56
        if (tall && needed > height) {
            Column(GlanceModifier.fillMaxSize()) {
                DeviceHeader(id, profile, first, state, busy, headerHeight)
                Spacer(GlanceModifier.height(12.dp))
                LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    item(itemId = 0) {
                        if (ac) AcReading(profile, state, expanded = true) else IndependentReading(profile, state)
                    }
                    rows.forEachIndexed { index, row -> item(itemId = index.toLong() + 1) {
                        KeyRow(id, row, state, busy, GlanceModifier.fillMaxWidth().padding(top = 8.dp))
                    } }
                }
            }
        } else Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            DeviceHeader(id, profile, first, state, busy, headerHeight)
            if (!tall) {
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.defaultWeight().fillMaxHeight().widgetContainer(LocalWidgetSurfaces.current.container),
                        contentAlignment = Alignment.Center) {
                        if (ac) AcReading(profile, state, compact = largeFont, showMode = !largeFont)
                        else IndependentReading(profile, state, compact = true)
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    KeyRow(id, rest.take(if (largeFont) 2 else 3), state, busy)
                }
            } else {
                Spacer(GlanceModifier.height(12.dp))
                Box(GlanceModifier.fillMaxWidth().height(readingHeight.dp).widgetContainer(LocalWidgetSurfaces.current.container),
                    contentAlignment = Alignment.Center) {
                    if (ac) AcReading(profile, state, expanded = !largeFont) else IndependentReading(profile, state)
                }
                if (rows.isNotEmpty()) Spacer(GlanceModifier.height(12.dp))
                rows.forEach { row -> KeyRow(id, row, state, busy, GlanceModifier.fillMaxWidth().height(56.dp)) }
            }
        }
    }
}

@Composable
private fun DeviceHeader(id: Int, profile: RemoteProfile, first: WidgetShortcut, state: WidgetSnapshot,
                         busy: Boolean, height: Int) {
    Row(GlanceModifier.fillMaxWidth().height(height.dp), verticalAlignment = Alignment.CenterVertically) {
        Identity(profile, GlanceModifier.defaultWeight().fillMaxHeight(),
            compact = LocalSize.current.width < 224.dp || (LocalWidgetFontScale.current >= 1.5f && height < 56),
            showRoom = LocalWidgetFontScale.current < 1.5f || height >= 80)
        Spacer(GlanceModifier.width(8.dp))
        ShortcutIcon(id, first, state, busy, primary = true)
    }
}

@Composable
private fun IndependentReading(profile: RemoteProfile, snapshot: WidgetSnapshot, compact: Boolean = false) {
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
private fun AcReading(profile: RemoteProfile, snapshot: WidgetSnapshot, compact: Boolean = false,
                      expanded: Boolean = false, showMode: Boolean = true) {
    val context = LocalContext.current
    val settings = widgetAcSettings(profile, snapshot.records[profile.id], snapshot.simulated)
    val label = if (settings.powerOn) "${settings.temperature}°" else LocalContext.current.getString(R.string.widget_off)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold,
                fontSize = if (compact && LocalWidgetFontScale.current >= 1.5f) 16.sp else if (compact) 20.sp else if (LocalWidgetFontScale.current >= 1.5f) 24.sp else 36.sp),
                maxLines = 1, modifier = GlanceModifier.semantics {
                    contentDescription = if (settings.powerOn) context.getString(R.string.widget_set_temperature, settings.temperature) else context.getString(R.string.widget_power_off)
                })
            if (showMode) {
                Spacer(GlanceModifier.width(8.dp))
                Image(widgetImage(settings.controlAppearance(AcAction.MODE).icon), localizedLabel(settings.mode, context),
                    GlanceModifier.size(20.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant))
            }
        }
        if (expanded) Row(GlanceModifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(AcAction.FAN, AcAction.SWING).forEachIndexed { index, action ->
                if (index > 0) Spacer(GlanceModifier.width(12.dp))
                val appearance = settings.controlAppearance(action)
                Image(widgetImage(appearance.icon), "${localizedLabel(action, context)} ${appearance.state.orEmpty()}",
                    GlanceModifier.size(24.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant))
            }
            if (settings.timerMinutes > 0) {
                Spacer(GlanceModifier.width(12.dp))
                Image(widgetImage(Icons.Rounded.Timer), LocalContext.current.getString(R.string.widget_timer), GlanceModifier.size(20.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant))
                Text(context.getString(R.string.widget_minutes_short, settings.timerMinutes), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun KeyRow(id: Int, shortcuts: List<WidgetShortcut>, state: WidgetSnapshot, busy: Boolean,
                   modifier: GlanceModifier = GlanceModifier) {
    Row(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        shortcuts.forEachIndexed { index, shortcut ->
            if (index > 0) Spacer(GlanceModifier.width(8.dp))
            ShortcutIcon(id, shortcut, state, busy)
        }
    }
}

private fun shortcutAction(shortcut: WidgetShortcut): Action = actionRunCallback<SendWidgetCommand>(
    actionParametersOf(SendWidgetCommand.remoteKey to shortcut.remoteId, SendWidgetCommand.buttonKey to shortcut.buttonId))

@Composable
private fun ShortcutIcon(id: Int, shortcut: WidgetShortcut, state: WidgetSnapshot, busy: Boolean,
                         primary: Boolean = false, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val context = LocalContext.current
    val command = state.commands[shortcut.remoteId].orEmpty().firstOrNull { it.id == shortcut.buttonId }
    val profile = state.profiles.firstOrNull { it.id == shortcut.remoteId }
    val enabled = !busy && widgetCommandEnabled(shortcut, profile, state.records[shortcut.remoteId], state.simulated)
    if (command == null) {
        SquareIconButton(widgetImage(Icons.Rounded.Edit), LocalContext.current.getString(R.string.widget_reselect_button),
            actionStartActivity(configurationIntent(LocalContext.current, id)),
            modifier = GlanceModifier.size(size),
            backgroundColor = GlanceTheme.colors.errorContainer, contentColor = GlanceTheme.colors.onErrorContainer)
    } else {
        val acAction = widgetAcAction(profile, command)
        val appearance = if (profile != null && acAction != null)
            widgetAcSettings(profile, state.records[profile.id], state.simulated).controlAppearance(acAction) else null
        val power = command.isPower(profile)
        val active = appearance?.active == true
        val failure = LocalWidgetFeedback.current?.takeIf { it.error && it.shortcut == shortcut }
        WidgetActionIcon(widgetImage(if (failure != null) Icons.Rounded.ErrorOutline else widgetCommandVector(profile, command, state, context)),
            listOfNotNull(profile?.name, profile?.room?.takeIf { it.isNotBlank() },
                command.displayLabel(profile, context),
                if (acAction == AcAction.TIMER && profile != null) {
                    val minutes = widgetAcSettings(profile, state.records[profile.id], state.simulated).timerMinutes
                    if (minutes > 0) context.getString(R.string.widget_minutes_short, minutes) else context.getString(R.string.widget_off)
                } else appearance?.state,
                failure?.let { context.getString(R.string.widget_failure_retry, it.text.resolve(context)) }).joinToString(" "),
            shortcutAction(shortcut), enabled = enabled,
            primary = primary || power, size = size,
            backgroundColor = when { !enabled -> LocalWidgetSurfaces.current.container; failure != null -> GlanceTheme.colors.errorContainer; primary || power -> GlanceTheme.colors.primary; active -> GlanceTheme.colors.primaryContainer; else -> LocalWidgetSurfaces.current.container },
            contentColor = when { !enabled -> GlanceTheme.colors.outline; failure != null -> GlanceTheme.colors.onErrorContainer; primary || power -> GlanceTheme.colors.onPrimary; active -> GlanceTheme.colors.onPrimaryContainer; else -> GlanceTheme.colors.onSurface })
    }
}

@Composable
private fun WidgetActionIcon(image: ImageProvider, description: String, onClick: Action, enabled: Boolean,
                             primary: Boolean, size: androidx.compose.ui.unit.Dp,
                             backgroundColor: androidx.glance.unit.ColorProvider,
                             contentColor: androidx.glance.unit.ColorProvider) {
    if (primary) SquareIconButton(image, description, onClick, GlanceModifier.size(size), enabled,
        backgroundColor = backgroundColor, contentColor = contentColor)
    else CircleIconButton(image, description, onClick, GlanceModifier.size(size), enabled,
        backgroundColor = backgroundColor, contentColor = contentColor)
}

class SendWidgetCommand : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val remote = parameters[remoteKey] ?: return
        val button = parameters[buttonKey] ?: return
        (context.applicationContext as IrRemoteApplication).widgets.perform(GlanceAppWidgetManager(context).getAppWidgetId(glanceId), WidgetShortcut(remote, button))
    }
    companion object {
        val remoteKey = ActionParameters.Key<String>("remote")
        val buttonKey = ActionParameters.Key<String>("button")
    }
}

class ReloadWidgets : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        (context.applicationContext as IrRemoteApplication).widgets.retry()
    }
}
