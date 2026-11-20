package org.qinyan.rimoat.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.zIndex
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.i18n.localizedButtonLabel
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.ui.components.*
import org.qinyan.rimoat.ui.motion.AppMotion
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import org.qinyan.rimoat.ui.motion.MotionAlertDialog

private data class PanelEntry(val id: String, val label: String, val icon: ImageVector?)

private data class PanelGroup(@StringRes val title: Int, val entries: List<PanelEntry>)

private fun buttonEntry(button: RemoteButton, label: String) = PanelEntry(panelKey(button), label, getIconVector(button.iconName))

/** Layout keys share the same 3-column grid as the remote so a drop position matches the panel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemotePanelEditor(profile: RemoteProfile, available: Set<AcAction>, onSave: (RemotePanelLayout) -> Unit,
    onBack: () -> Unit) {
    var serialized by rememberSaveable(profile.id) { mutableStateOf(Json.encodeToString(profile.panelLayout)) }
    val layout = remember(serialized) { Json.decodeFromString<RemotePanelLayout>(serialized) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    fun update(value: RemotePanelLayout, persist: Boolean = true) {
        serialized = Json.encodeToString(value)
        if (persist) onSave(value)
    }

    val groups = buildList {
        val dynamic = profile.category == DeviceCategory.AC && !profile.useStaticAcButtons && profile.acProtocolId != null
        if (dynamic) {
            val main = setOf(AcAction.MODE, AcAction.FAN, AcAction.SWING, AcAction.HORIZONTAL_SWING, AcAction.WIND_DIRECTION)
            val actions = acPanelActions(profile.copy(panelLayout = RemotePanelLayout()), available, false)
            val defaults = AcSettings()
            fun actionEntry(action: AcAction) = PanelEntry(panelAction(action), localizedLabel(action.label), defaults.controlAppearance(action).icon)
            add(PanelGroup(R.string.panel_group_controls, actions.filter { it in main }.map(::actionEntry)))
            add(PanelGroup(R.string.panel_group_ac_actions, actions.filter { it !in main }.map(::actionEntry)))
            add(PanelGroup(R.string.panel_group_raw_keys, acRawExtensionButtons(profile).map { buttonEntry(it, localizedButtonLabel(it)) }))
            add(PanelGroup(R.string.panel_group_presets, listOf(
                PanelEntry("preset:0", stringResource(R.string.screen_resend), Icons.Rounded.Refresh),
                PanelEntry("preset:1", stringResource(R.string.screen_cool_preset), Icons.Rounded.AcUnit),
                PanelEntry("preset:2", stringResource(R.string.screen_heat_preset), Icons.Rounded.WbSunny))))
        } else {
            val fixed = fixedPanelKeys(profile)
            val editable = profile.buttons.filter { it.id !in fixed }
            if (profile.category == DeviceCategory.TV) {
                val standard = tvStandardButtons(profile)
                val top = listOfNotNull(standard[1], standard[2])
                val nav = (8..10).mapNotNull { standard[it] }
                add(PanelGroup(R.string.panel_group_controls, top.map { buttonEntry(it, localizedButtonLabel(it)) }))
                add(PanelGroup(R.string.panel_group_navigation, nav.map { buttonEntry(it, localizedButtonLabel(it)) }))
                add(PanelGroup(R.string.panel_group_extensions, (editable - top.toSet() - nav.toSet()).map { buttonEntry(it, localizedButtonLabel(it)) }))
            } else add(PanelGroup(R.string.panel_group_controls, editable.map { buttonEntry(it, localizedButtonLabel(it)) }))
        }
    }.filter { it.entries.isNotEmpty() }.map { group -> group.copy(entries = group.entries.sortedBy { layout.rank(it.id) }) }

    val haptic = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()
    val currentGroups by rememberUpdatedState(groups)
    val currentLayout by rememberUpdatedState(layout)
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var dragMoved by remember { mutableStateOf(false) }

    fun reorder(ids: List<String>, from: Int, to: Int, persist: Boolean) {
        if (from !in ids.indices || to !in ids.indices || from == to) return
        val order = ids.toMutableList().apply { add(to, removeAt(from)) }
        update(currentLayout.copy(order = currentLayout.order.filter { it !in order } + order), persist)
    }
    fun itemInfo(id: String): LazyGridItemInfo? = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
    fun groupIds(id: String) = currentGroups.firstOrNull { group -> group.entries.any { it.id == id } }?.entries?.map { it.id }.orEmpty()
    fun followPointer() {
        val id = draggingId ?: return
        val self = itemInfo(id) ?: return
        val ids = groupIds(id)
        val center = dragOrigin + dragDelta + Offset(self.size.width / 2f, self.size.height / 2f)
        val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            info.key != id && info.key in ids &&
                center.x >= info.offset.x && center.x < info.offset.x + info.size.width &&
                center.y >= info.offset.y && center.y < info.offset.y + info.size.height
        } ?: return
        reorder(ids, ids.indexOf(id), ids.indexOf(target.key), persist = false)
        dragMoved = true
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    fun finishDrag() {
        if (draggingId == null) return
        draggingId = null
        dragDelta = Offset.Zero
        if (dragMoved) onSave(currentLayout)
        dragMoved = false
    }

    // Edge auto-scroll keeps long groups reachable while the dragged slot stays on screen.
    LaunchedEffect(draggingId) {
        val id = draggingId ?: return@LaunchedEffect
        while (draggingId == id) {
            withFrameNanos { }
            val self = itemInfo(id) ?: continue
            val height = gridState.layoutInfo.viewportSize.height.toFloat()
            val zone = self.size.height / 2f
            val y = dragOrigin.y + dragDelta.y + self.size.height / 2f
            val step = when {
                y < zone && self.offset.y + self.size.height < height -> -(zone - y).coerceAtMost(zone) / 4f
                y > height - zone && self.offset.y > 0 -> (y - (height - zone)).coerceAtMost(zone) / 4f
                else -> 0f
            }
            if (step != 0f) {
                val consumed = gridState.scrollBy(step)
                dragOrigin -= Offset(0f, consumed)
                dragDelta += Offset(0f, consumed)
                followPointer()
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.panel_edit_title)) },
        navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.screen_back))
        } }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            groups.forEach { group ->
                item(key = "title_${group.title}", span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
                    Text(stringResource(group.title), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() })
                }
                items(group.entries, key = { it.id }, contentType = { "key" }) { entry ->
                    val ids = group.entries.map { it.id }
                    val index = ids.indexOf(entry.id)
                    val dragged = draggingId == entry.id
                    val visible = entry.id !in layout.hidden
                    val moveBefore = stringResource(R.string.panel_move_before, entry.label)
                    val moveAfter = stringResource(R.string.panel_move_after, entry.label)
                    DisposableEffect(entry.id) { onDispose { if (draggingId == entry.id) finishDrag() } }
                    PanelTile(
                        entry = entry, visible = visible, dragged = dragged,
                        onToggle = { show ->
                            update(layout.copy(hidden = if (show) layout.hidden - entry.id else layout.hidden + entry.id))
                        },
                        modifier = Modifier
                            .animateItem(fadeInSpec = null, fadeOutSpec = null,
                                placementSpec = if (dragged) null else AppMotion.tween())
                            .zIndex(if (dragged) 1f else 0f)
                            .graphicsLayer {
                                if (dragged) itemInfo(entry.id)?.let { info ->
                                    val shift = dragOrigin + dragDelta - info.offset.toOffset()
                                    translationX = shift.x
                                    translationY = shift.y
                                }
                            }
                            .semantics {
                                customActions = buildList {
                                    if (index > 0) add(CustomAccessibilityAction(moveBefore) { reorder(ids, index, index - 1, true); true })
                                    if (index < ids.lastIndex) add(CustomAccessibilityAction(moveAfter) { reorder(ids, index, index + 1, true); true })
                                }
                            },
                        // Inside the toggle so a finished drag consumes its release before the tap handler sees it.
                        gestures = Modifier.pointerInput(entry.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        itemInfo(entry.id)?.let { info ->
                                            dragOrigin = info.offset.toOffset()
                                            dragDelta = Offset.Zero
                                            dragMoved = false
                                            draggingId = entry.id
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragDelta += amount
                                        followPointer()
                                    },
                                    onDragEnd = ::finishDrag,
                                    onDragCancel = ::finishDrag
                                )
                            }
                    )
                }
            }
            item(key = "restore", span = { GridItemSpan(maxLineSpan) }, contentType = "restore") {
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.padding(top = 24.dp).fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.panel_restore_default))
                }
            }
        }
    }
    if (LocalMotionActive.current && confirmReset) MotionAlertDialog(onDismissRequest = { confirmReset = false }, title = { Text(stringResource(R.string.panel_restore_default)) },
        text = { Text(stringResource(R.string.panel_restore_confirm)) },
        confirmButton = { TextButton(onClick = { update(RemotePanelLayout()); confirmReset = false }) { Text(stringResource(R.string.panel_restore_default)) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.screen_close)) } })
}

/** A key preview with the same circle and label as the panel; the corner mark shows whether it is on the panel. */
@Composable
private fun PanelTile(entry: PanelEntry, visible: Boolean, dragged: Boolean, onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier, gestures: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (dragged) 1.08f else 1f, AppMotion.tween(), label = "PanelTileScale")
    val elevation by animateDpAsState(if (dragged) 6.dp else 0.dp, AppMotion.tween(), label = "PanelTileElevation")
    val container by animateColorAsState(if (visible) colors.secondaryContainer else Color.Transparent, AppMotion.tween(), label = "PanelTileColor")
    val content = if (visible) colors.onSecondaryContainer else colors.onSurface.copy(alpha = .38f)
    val description = stringResource(R.string.panel_show_control, entry.label)
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = visible, role = Role.Checkbox, onValueChange = onToggle)
            .semantics { contentDescription = description }
            .then(gestures)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
            Surface(
                shape = CircleShape, color = container, contentColor = content,
                border = if (visible) null else BorderStroke(1.dp, colors.outline.copy(alpha = .6f)),
                shadowElevation = elevation, modifier = Modifier.padding(6.dp).size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val textKey = entry.icon == null && (entry.label.length == 1 || entry.label == "OK")
                    if (textKey) Text(entry.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    else Icon(entry.icon ?: Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
            Surface(
                shape = CircleShape,
                color = if (visible) colors.primary else colors.surfaceContainerHighest,
                contentColor = if (visible) colors.onPrimary else colors.onSurfaceVariant,
                border = BorderStroke(2.dp, colors.surface),
                modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (visible) Icons.Rounded.Check else Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(entry.label, style = MaterialTheme.typography.labelLarge, color = if (visible) colors.onSurface else colors.onSurface.copy(alpha = .38f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp).clearAndSetSemantics {})
    }
}
