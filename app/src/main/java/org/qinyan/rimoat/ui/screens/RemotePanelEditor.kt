@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.i18n.AppStrings
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
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
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.localizedButtonLabel
import org.qinyan.rimoat.i18n.labelRes
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.ui.components.*
import org.qinyan.rimoat.ui.motion.AppMotion
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import org.qinyan.rimoat.ui.motion.MotionAlertDialog
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas

private data class PanelEntry(val id: String, val label: String, val icon: ImageVector?)

private data class PanelGroup(@StringRes val title: Int, val entries: List<PanelEntry>)

private val PanelLayoutSaver = Saver<RemotePanelLayout, String>(
    save = { Json.encodeToString(it) },
    restore = { Json.decodeFromString(it) }
)

private fun buttonEntry(button: RemoteButton, label: String) = PanelEntry(panelKey(button), label, resolveButtonIcon(button))

/** The preview and editable groups share the same saved visibility and ordering. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemotePanelEditor(
    profile: RemoteProfile,
    available: Set<AcAction>,
    onSave: (RemotePanelLayout) -> Unit,
    onBack: () -> Unit,
    saveError: UiText? = null,
    onDismissSaveError: () -> Unit = {}
) {
    val motion = AppMotion.current
    var layout by rememberSaveable(profile.id, stateSaver = PanelLayoutSaver) {
        mutableStateOf(profile.panelLayout)
    }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val wide = rememberWideLayout()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val screenActive = LocalMotionActive.current
    LaunchedEffect(screenActive, saveError) {
        if (screenActive) saveError?.let {
            snackbarHostState.showSnackbar(it.resolve(context), duration = SnackbarDuration.Long)
            onDismissSaveError()
        }
    }
    fun update(value: RemotePanelLayout, persist: Boolean = true) {
        layout = value
        if (persist) onSave(value)
    }

    val baseGroups = buildList {
        val dynamic = profile.category == DeviceCategory.AC && !profile.useStaticAcButtons && profile.acProtocolId != null
        if (dynamic) {
            val main = setOf(AcAction.MODE, AcAction.FAN, AcAction.SWING, AcAction.HORIZONTAL_SWING, AcAction.WIND_DIRECTION)
            val actions = acPanelActions(profile.copy(panelLayout = RemotePanelLayout()), available, false)
            val defaults = AcSettings()
            fun actionEntry(action: AcAction) = PanelEntry(panelAction(action), context.getString(action.labelRes), defaults.controlAppearance(action).icon)
            add(PanelGroup(R.string.panel_group_controls, actions.filter { it in main }.map(::actionEntry)))
            add(PanelGroup(R.string.panel_group_ac_actions, actions.filter { it !in main }.map(::actionEntry)))
            add(PanelGroup(R.string.panel_group_raw_keys, acRawExtensionButtons(profile).map { buttonEntry(it, org.qinyan.rimoat.i18n.localizedAcRawButtonLabel(it)) }))
            add(PanelGroup(R.string.panel_group_presets, listOf(
                PanelEntry("preset:0", stringResource(R.string.screen_resend), Icons.Rounded.Refresh),
                PanelEntry("preset:1", stringResource(R.string.screen_cool_preset), Icons.Rounded.AcUnit),
                PanelEntry("preset:2", stringResource(R.string.screen_heat_preset), Icons.Rounded.WbSunny))))
        } else {
            val fixed = fixedPanelKeys(profile)
            val editable = profile.buttons.filter { it.id !in fixed }
            if (profile.category == DeviceCategory.TV) {
                val standard = tvStandardButtons(profile)
                val top = listOfNotNull(standard.source, standard.mute)
                val nav = standard.navigation
                add(PanelGroup(R.string.panel_group_controls, top.map { buttonEntry(it, localizedButtonLabel(it)) }))
                add(PanelGroup(R.string.panel_group_navigation, nav.map { buttonEntry(it, localizedButtonLabel(it)) }))
                add(PanelGroup(R.string.panel_group_extensions, (editable - top.toSet() - nav.toSet()).map { buttonEntry(it, localizedButtonLabel(it)) }))
            } else add(PanelGroup(R.string.panel_group_controls, editable.map { buttonEntry(it, localizedButtonLabel(it)) }))
        }
    }.filter { it.entries.isNotEmpty() }
    val groups = baseGroups.map { group -> group.copy(entries = group.entries.sortedBy { layout.rank(it.id) }) }

    val haptic = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()
    val previewState = rememberLazyListState()
    val currentBaseGroups by rememberUpdatedState(baseGroups)
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var dragMoved by remember { mutableStateOf(false) }
    var lastSwapLayoutInfo by remember { mutableStateOf<Any?>(null) }
    var lastSwapTarget by remember { mutableStateOf<String?>(null) }

    fun reorder(ids: List<String>, from: Int, to: Int, persist: Boolean) {
        if (from !in ids.indices || to !in ids.indices || from == to) return
        val order = ids.toMutableList().apply { add(to, removeAt(from)) }
        update(layout.copy(order = layout.order.filter { it !in order } + order), persist)
    }
    fun itemInfo(id: String): LazyGridItemInfo? = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
    fun groupIds(id: String): List<String> =
        currentBaseGroups.firstOrNull { group -> group.entries.any { it.id == id } }
            ?.entries
            ?.sortedBy { layout.rank(it.id) }
            ?.map { it.id }
            .orEmpty()
    fun followPointer() {
        val id = draggingId ?: return
        val layoutInfo = gridState.layoutInfo
        val self = layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        val ids = groupIds(id)
        val center = dragOrigin + dragDelta + Offset(self.size.width / 2f, self.size.height / 2f)
        val target = layoutInfo.visibleItemsInfo.firstOrNull { info ->
            info.key != id && info.key in ids &&
                center.x >= info.offset.x && center.x < info.offset.x + info.size.width &&
                center.y >= info.offset.y && center.y < info.offset.y + info.size.height
        } ?: return
        val targetId = target.key as? String ?: return
        // Avoid ping-ponging on repeated pointer moves before the grid lays out the swapped slots.
        if (lastSwapLayoutInfo === layoutInfo && lastSwapTarget == targetId) return
        lastSwapLayoutInfo = layoutInfo
        lastSwapTarget = targetId
        reorder(ids, ids.indexOf(id), ids.indexOf(targetId), persist = false)
        dragMoved = true
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    fun finishDrag() {
        if (draggingId == null) return
        draggingId = null
        dragDelta = Offset.Zero
        lastSwapLayoutInfo = null
        lastSwapTarget = null
        if (dragMoved) onSave(layout)
        dragMoved = false
    }
    LaunchedEffect(wide) { finishDrag() }

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

    ProvideExpressiveCanvas {
    Scaffold(
        containerColor = ExpressiveSurface.canvas,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            ExpressiveCompactTopBar(
                title = stringResource(R.string.panel_edit_title),
                navigationIcon = {
                    ActionIconButton(stringResource(R.string.screen_back), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.screen_back))
                    }
                }
            )
        }
    ) { padding ->
        val editor: @Composable () -> Unit = {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (wide) extensionColumns(groups.flatMap { it.entries }.map { it.label }, maxWidth)
            .coerceAtMost((maxWidth.value / 88).toInt().coerceAtLeast(1)) else 3
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize().testTag("panel_editor_controls"),
            contentPadding = PaddingValues(horizontal = if (wide) 0.dp else 20.dp, vertical = 12.dp),
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
                                placementSpec = if (dragged) null else motion.defaultSpatialSpec())
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
                                    if (index > 0) add(CustomAccessibilityAction(moveBefore) {
                                        val currentIds = groupIds(entry.id)
                                        val from = currentIds.indexOf(entry.id)
                                        reorder(currentIds, from, from - 1, true)
                                        true
                                    })
                                    if (index < ids.lastIndex) add(CustomAccessibilityAction(moveAfter) {
                                        val currentIds = groupIds(entry.id)
                                        val from = currentIds.indexOf(entry.id)
                                        reorder(currentIds, from, from + 1, true)
                                        true
                                    })
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
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.padding(top = 24.dp).fillMaxWidth().heightIn(min = 48.dp), shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.panel_restore_default))
                }
            }
        }
        }
        }
        if (wide) {
            TwoPaneLayout(Modifier.padding(padding), primary = {
                val presented = profile.copy(panelLayout = layout).withPanelPresentation()
                val hideFromPreview: (RemoteButton) -> Unit = { button ->
                    if (groups.any { group -> group.entries.any { it.id == panelKey(button) } })
                        update(layout.copy(hidden = layout.hidden + panelKey(button)))
                }
                LazyColumn(Modifier.fillMaxSize().testTag("panel_editor_preview"), state = previewState,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                    item { Text(stringResource(R.string.widget_preview), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().semantics { heading() }) }
                    when {
                        profile.category == DeviceCategory.AC && !profile.useStaticAcButtons && profile.acProtocolId != null -> item {
                            AcRemoteLayout(presented, profile.acSettings, false, {}, {}, {}, {}, {}, available,
                                onAction = { _, _ -> }, onSendButton = hideFromPreview, inlineExtensions = true)
                        }
                        profile.category == DeviceCategory.TV -> tvRemoteItems(presented, hideFromPreview, inlineExtensions = true)
                        profile.category == DeviceCategory.FAN -> fanRemoteItems(presented, hideFromPreview, inlineExtensions = true)
                        else -> genericRemoteItems(presented, hideFromPreview, inlineExtensions = true)
                    }
                }
            }, secondary = editor)
        } else Box(Modifier.fillMaxSize().padding(padding)) { editor() }
    }
    }
    if (LocalMotionActive.current && confirmReset) MotionAlertDialog(onDismissRequest = { confirmReset = false }, title = { Text(stringResource(R.string.panel_restore_default)) },
        text = { Text(stringResource(R.string.panel_restore_confirm)) },
        confirmButton = { TextButton(onClick = { update(RemotePanelLayout()); confirmReset = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.panel_restore_default)) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.screen_close)) } })
}

/** A key preview with the same circle and label as the panel; the corner mark shows whether it is on the panel. */
@Composable
private fun PanelTile(entry: PanelEntry, visible: Boolean, dragged: Boolean, onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier, gestures: Modifier = Modifier) {
    val motion = AppMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val tileShape = rememberExpressiveShape(interactionSource, RoundedCornerShape(18.dp), RoundedCornerShape(24.dp))
    val keyShape = rememberExpressiveShape(interactionSource, RoundedCornerShape(50))
    val colors = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (dragged) 1.04f else 1f, motion.fastSpatialSpec(), label = "PanelTileScale")
    val elevation by animateDpAsState(if (dragged) 6.dp else 0.dp, motion.fastSpatialSpec(), label = "PanelTileElevation")
    val tileContainer by animateColorAsState(if (visible) ExpressiveSurface.card else ExpressiveSurface.card.copy(alpha = 0.45f), motion.defaultEffectsSpec(), label = "PanelTileBg")
    val container by animateColorAsState(if (visible) colors.secondaryContainer else Color.Transparent, motion.defaultEffectsSpec(), label = "PanelTileColor")
    val content = if (visible) colors.onSecondaryContainer else colors.onSurface.copy(alpha = .38f)
    val description = stringResource(R.string.panel_show_control, entry.label)
    Surface(
        shape = tileShape,
        color = tileContainer,
        modifier = modifier
            .clip(tileShape)
            .toggleable(value = visible, interactionSource = interactionSource, indication = ripple(), role = Role.Checkbox, onValueChange = onToggle)
            .semantics { contentDescription = description }
            .then(gestures)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
                Surface(
                    shape = keyShape, color = container, contentColor = content,
                    border = if (visible) null else BorderStroke(1.dp, colors.outline.copy(alpha = .6f)),
                    shadowElevation = elevation, modifier = Modifier.padding(6.dp).size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RemoteKeyGlyph(entry.label, entry.icon,
                            modifier = if (entry.icon == null) Modifier.padding(end = 12.dp) else Modifier)
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = if (visible) colors.primary else colors.surfaceContainerHighest,
                    contentColor = if (visible) colors.onPrimary else colors.onSurfaceVariant,
                    border = BorderStroke(2.dp, ExpressiveSurface.card),
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
}
