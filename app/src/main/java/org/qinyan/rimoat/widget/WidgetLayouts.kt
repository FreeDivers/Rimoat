package org.qinyan.rimoat.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.*
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.*

@Composable
internal fun SingleContent(id: Int, profile: RemoteProfile, config: WidgetConfiguration, state: WidgetSnapshot,
                          feedback: WidgetFeedback?, compact: Boolean, wide: Boolean) {
    val ac = profile.acProtocolId != null && !profile.useStaticAcButtons
    val largeIdentity = !compact && wide && LocalWidgetFontScale.current >= WidgetLayoutSpec.LargeFontScale
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
internal fun PanelContent(id: Int, config: WidgetConfiguration, state: WidgetSnapshot,
                         feedback: WidgetFeedback?, compact: Boolean, wide: Boolean) {
    val largeFont = LocalWidgetFontScale.current >= WidgetLayoutSpec.LargeFontScale
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
internal fun DeviceContent(id: Int, profile: RemoteProfile, config: WidgetConfiguration, state: WidgetSnapshot,
                          feedback: WidgetFeedback?, compact: Boolean, wide: Boolean, tall: Boolean) {
    val ac = profile.category == DeviceCategory.AC && profile.acProtocolId != null && !profile.useStaticAcButtons
    val largeFont = LocalWidgetFontScale.current >= WidgetLayoutSpec.LargeFontScale
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
            compact = LocalSize.current.width < 224.dp || (LocalWidgetFontScale.current >= WidgetLayoutSpec.LargeFontScale && height < 56),
            showRoom = LocalWidgetFontScale.current < 1.5f || height >= 80)
        Spacer(GlanceModifier.width(8.dp))
        ShortcutIcon(id, first, state, busy, primary = true)
    }
}
