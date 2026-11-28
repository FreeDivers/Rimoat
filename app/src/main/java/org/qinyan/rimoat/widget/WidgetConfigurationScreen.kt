package org.qinyan.rimoat.widget

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.UiTextException
import org.qinyan.rimoat.i18n.UiTextStateSaver
import org.qinyan.rimoat.i18n.asString
import org.qinyan.rimoat.i18n.toAppError
import androidx.compose.ui.res.stringResource
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.IrRemoteApplication
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.compose
import org.qinyan.rimoat.ui.components.TwoPaneLayout
import org.qinyan.rimoat.ui.components.rememberWideLayout
import org.qinyan.rimoat.ui.components.recordItems
import org.qinyan.rimoat.ui.components.replay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WidgetConfigurationScreen(app: IrRemoteApplication, widgetId: Int, kind: WidgetKind,
                                      onCancel: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val snapshot by app.widgets.state.collectAsState()
    // Serialize only for state saving, not on every selection or reordering click.
    var draft by rememberSaveable(stateSaver = Saver<WidgetConfiguration?, String>(
        save = { it?.let { value -> Json.encodeToString(value) }.orEmpty() },
        restore = { it.takeIf(String::isNotBlank)?.let { value -> Json.decodeFromString<WidgetConfiguration>(value) } }
    )) { mutableStateOf<WidgetConfiguration?>(null) }
    fun change(value: WidgetConfiguration) { draft = value }
    var error by rememberSaveable(stateSaver = UiTextStateSaver) { mutableStateOf<UiText?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(widgetId) {
        app.widgets.awaitReady()
        if (draft == null) change(app.widgets.store.configurations.value[widgetId] ?: WidgetConfiguration(kind))
    }
    val currentDraft = draft
    val preview = rememberConfigurationPreview(widgetId, currentDraft, snapshot)
    WidgetConfigurationContent(widgetId, kind, snapshot, currentDraft, preview, saving, error,
        onChange = ::change, onCancel = onCancel,
        onRetry = { scope.launch { app.widgets.retry() } },
        onAddRemote = { app.startActivity(remoteWidgetIntent(app, destination = "add").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        onSave = {
            if (!saving && currentDraft != null) {
                saving = true; error = null
                scope.launch {
                    try {
                        currentDraft.validate()
                        if (currentDraft.shortcuts.isEmpty()) {
                            throw UiTextException(UiText.Resource(R.string.widget_select_shortcut))
                        }
                        currentDraft.shortcuts.forEach { shortcut ->
                            if (app.widgets.state.value.commands[shortcut.remoteId].orEmpty().none { it.id == shortcut.buttonId }) {
                                throw UiTextException(UiText.Resource(R.string.widget_selected_button_changed))
                            }
                        }
                        app.widgets.save(widgetId, currentDraft)
                        onSaved()
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.toAppError(R.string.widget_save_failed).message }
                    finally { saving = false }
                }
            }
        })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WidgetConfigurationContent(
    widgetId: Int,
    kind: WidgetKind,
    snapshot: WidgetSnapshot,
    currentDraft: WidgetConfiguration?,
    preview: WidgetPreview,
    saving: Boolean,
    error: UiText?,
    onChange: (WidgetConfiguration) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onAddRemote: () -> Unit
) {
    val context = LocalContext.current
    var choosing by rememberSaveable { mutableStateOf(false) }
    var choosingRemote by rememberSaveable { mutableStateOf(false) }
    var chooseRemoteId by rememberSaveable { mutableStateOf("") }
    val wide = rememberWideLayout()
    val previewListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val configurationListState = androidx.compose.foundation.lazy.rememberLazyListState()
    fun change(value: WidgetConfiguration) = onChange(value)
    val title = when (kind) { WidgetKind.SINGLE -> stringResource(R.string.widget_single_name); WidgetKind.PANEL -> stringResource(R.string.widget_panel_name); WidgetKind.STATUS -> stringResource(R.string.widget_status_name) }
    val actions: @Composable () -> Unit = {
            Surface {
                if (wide) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes(), enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.widget_cancel)) }
                        Button(onClick = onSave, shapes = ButtonDefaults.shapes(), enabled = !saving && currentDraft?.shortcuts?.isNotEmpty() == true, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(if (saving) stringResource(R.string.widget_saving) else stringResource(R.string.widget_done))
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes(), enabled = !saving) { Text(stringResource(R.string.widget_cancel)) }
                        Button(onClick = onSave, shapes = ButtonDefaults.shapes(), enabled = !saving && currentDraft?.shortcuts?.isNotEmpty() == true, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(if (saving) stringResource(R.string.widget_saving) else stringResource(R.string.widget_done))
                        }
                    }
                }
            }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = onCancel, shapes = IconButtonDefaults.shapes(), enabled = !saving) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.widget_cancel_config)) }
        }) },
        bottomBar = { if (!wide) Box(Modifier.navigationBarsPadding()) { actions() } }
    ) { padding ->
        val content = recordItems {
            if (!snapshot.loaded || currentDraft == null) item(key = "preview_loading") { LinearWavyProgressIndicator(Modifier.fillMaxWidth()) }
            if (snapshot.error != null) item(key = "preview_error") {
                Text(snapshot.error!!.asString(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.widget_reload)) }
            }
            if (currentDraft != null) {
                val draft = currentDraft
                item(key = "preview") { ConfigurationPreview(widgetId, preview) }
                if (error != null) item { Text(error.asString(), color = MaterialTheme.colorScheme.error) }
                if (snapshot.profiles.isEmpty()) item {
                    Text(stringResource(R.string.widget_no_remotes), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onAddRemote, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.widget_add_remote)) }
                }
                if (kind != WidgetKind.PANEL) {
                    item { Text(stringResource(R.string.widget_category_remote), style = MaterialTheme.typography.titleSmall) }
                    item(key = "selected_remote") {
                        val profile = snapshot.profiles.firstOrNull { it.id == draft.remoteId }
                        ListItem(headlineContent = { Text(profile?.name ?: stringResource(R.string.widget_select_remote)) },
                            supportingContent = profile?.room?.takeIf(String::isNotBlank)?.let { { Text(it) } },
                            leadingContent = { Icon(profile?.let { org.qinyan.rimoat.ui.components.getCategoryIcon(it) } ?: Icons.Rounded.SettingsRemote, null) },
                            trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(enabled = !saving) { choosingRemote = true })
                    }
                }
                item { Text(stringResource(R.string.widget_shortcuts), style = MaterialTheme.typography.titleSmall) }
                items(draft.shortcuts, key = { "shortcut:${it.remoteId}:${it.buttonId}" }) { shortcut ->
                    val index = draft.shortcuts.indexOf(shortcut)
                    val profile = snapshot.profiles.firstOrNull { it.id == shortcut.remoteId }
                    val command = snapshot.commands[shortcut.remoteId].orEmpty().firstOrNull { it.id == shortcut.buttonId }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val textMeasurer = rememberTextMeasurer()
                    val label = command?.displayLabel(profile, context) ?: stringResource(R.string.widget_button_removed)
                    val labelWidth = with(LocalDensity.current) {
                        textMeasurer.measure(label, MaterialTheme.typography.bodyLarge).size.width.toDp()
                    }
                    // Keep the label readable when large fonts and a narrow window meet.
                    val stacked = kind != WidgetKind.SINGLE && maxWidth < 240.dp + labelWidth
                    val controls: @Composable () -> Unit = {
                        Row {
                            if (kind != WidgetKind.SINGLE) {
                                IconButton(shapes = IconButtonDefaults.shapes(), enabled = index > 0 && !saving, onClick = {
                                    val next = draft.shortcuts.toMutableList(); java.util.Collections.swap(next, index, index - 1); change(draft.copy(shortcuts = next))
                                }) { Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.widget_move_up, command?.displayLabel(profile, context).orEmpty())) }
                                IconButton(shapes = IconButtonDefaults.shapes(), enabled = index < draft.shortcuts.lastIndex && !saving, onClick = {
                                    val next = draft.shortcuts.toMutableList(); java.util.Collections.swap(next, index, index + 1); change(draft.copy(shortcuts = next))
                                }) { Icon(Icons.Rounded.ArrowDownward, stringResource(R.string.widget_move_down, command?.displayLabel(profile, context).orEmpty())) }
                                IconButton(shapes = IconButtonDefaults.shapes(), enabled = !saving, onClick = { change(draft.copy(shortcuts = draft.shortcuts - shortcut)) }) { Icon(Icons.Rounded.Close, stringResource(R.string.widget_remove_button, command?.displayLabel(profile, context).orEmpty())) }
                            } else IconButton(shapes = IconButtonDefaults.shapes(), enabled = !saving, onClick = { chooseRemoteId = draft.remoteId; choosing = true }) { Icon(Icons.Rounded.Edit, stringResource(R.string.widget_change_button)) }
                        }
                    }
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                    ListItem(
                        headlineContent = { Text(command?.displayLabel(profile, context) ?: stringResource(R.string.widget_button_removed)) },
                        supportingContent = if (kind == WidgetKind.PANEL) ({ Text(profile?.name.orEmpty()) }) else null,
                        leadingContent = { Icon(command?.let { widgetCommandVector(profile, it, snapshot, context) } ?: Icons.Rounded.Edit, null) },
                        trailingContent = if (stacked) null else controls,
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                    if (stacked) Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) { controls() }
                    }
                    }
                    }
                }
                if (kind != WidgetKind.SINGLE || draft.shortcuts.isEmpty()) item {
                    OutlinedButton(shapes = ButtonDefaults.shapes(), enabled = !saving && draft.shortcuts.size < WidgetConfiguration.MAX_SHORTCUTS,
                        onClick = { chooseRemoteId = if (kind == WidgetKind.PANEL) "" else draft.remoteId; choosing = true }) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.widget_add_shortcut))
                    }
                }
                if (kind == WidgetKind.PANEL) item {
                    Text(stringResource(R.string.widget_button_layout), style = MaterialTheme.typography.titleSmall)
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToggleButton(checked = draft.arrangement == WidgetArrangement.GRID,
                            onCheckedChange = { change(draft.copy(arrangement = WidgetArrangement.GRID)) },
                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                selected = draft.arrangement == WidgetArrangement.GRID; role = Role.RadioButton
                            }) { Text(stringResource(R.string.widget_grid)) }
                        ToggleButton(checked = draft.arrangement == WidgetArrangement.LIST,
                            onCheckedChange = { change(draft.copy(arrangement = WidgetArrangement.LIST)) },
                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                selected = draft.arrangement == WidgetArrangement.LIST; role = Role.RadioButton
                            }) { Text(stringResource(R.string.widget_list)) }
                    }
                }
            }
            if (wide) item(key = "configuration_actions") { actions() }
        }
        if (wide) TwoPaneLayout(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), primaryFraction = .45f,
            primary = { LazyColumn(Modifier.fillMaxSize(), state = previewListState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                replay(content.filter { it.key in setOf("preview", "preview_loading", "preview_error") })
            } },
            secondary = { LazyColumn(Modifier.fillMaxSize(), state = configurationListState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                replay(content.filterNot { it.key in setOf("preview", "preview_loading", "preview_error") })
            } })
        else LazyColumn(Modifier.fillMaxSize().padding(padding), state = configurationListState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { replay(content) }
    }
    WidgetShortcutPicker(choosingRemote, choosing, currentDraft, snapshot, kind, wide, chooseRemoteId,
        change = ::change, onChooseRemote = { chooseRemoteId = it },
        onDismissRemote = { choosingRemote = false }, onDismissShortcut = { choosing = false })
}

/**
 * Responsive Glance renders layout buckets, then the Launcher measures that
 * RemoteViews tree using the actual allocation. Keep those two sizes separate so the editor
 * preview uses the app's canonical canvas for multi-row layouts when launcher allocations
 * include space outside the widget's content view.
 */
internal data class WidgetPreview(
    val displaySize: DpSize,
    val views: android.widget.RemoteViews?,
    val failed: Boolean = false,
    val onRetry: () -> Unit = {}
)

private val previewExecutor by lazy { java.util.concurrent.Executors.newSingleThreadExecutor() }

@OptIn(androidx.glance.ExperimentalGlanceApi::class)
@Composable
private fun rememberConfigurationPreview(widgetId: Int, configuration: WidgetConfiguration?, snapshot: WidgetSnapshot): WidgetPreview {
    val sourceContext = LocalContext.current
    val device = LocalConfiguration.current
    val context = remember(sourceContext, android.content.res.Configuration(device)) {
        WidgetPreviewComposer.previewContext(sourceContext)
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { WidgetPreviewComposer.cleanStaleLayouts(context) }
    }
    val availableSize = remember(widgetId, device.orientation, device.densityDpi) {
        widgetPreviewSize(context, widgetId, device.orientation)
    }
    val selected = configuration?.let { value ->
        val ids = (value.shortcuts.map { it.remoteId } + value.remoteId).filter(String::isNotBlank).toSet()
        snapshot.copy(
            profiles = if (ids.isEmpty()) snapshot.profiles.take(1) else snapshot.profiles.filter { it.id in ids },
            configurations = mapOf(-1 to value),
            commands = snapshot.commands.filterKeys { it in ids },
            records = snapshot.records.filterKeys { it in ids }
        )
    }
    val previewKey = remember(configuration, selected, availableSize, device.uiMode, device.fontScale,
        device.densityDpi, device.locales.toLanguageTags()) {
        "$availableSize|${device.uiMode}|${device.fontScale}|${device.densityDpi}|${device.locales.toLanguageTags()}|${configuration?.hashCode()}|${selected?.hashCode()}"
    }
    var views by remember { mutableStateOf(WidgetPreviewComposer.cached(previewKey)) }
    var failed by remember { mutableStateOf(false) }
    var retryToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(previewKey, retryToken) {
        if (configuration == null || selected == null) return@LaunchedEffect
        failed = false
        views = WidgetPreviewComposer.cached(previewKey)
        if (views != null) return@LaunchedEffect
        val started = android.os.SystemClock.elapsedRealtime()
        // The composition itself runs in an application-lifetime scope; leaving this page or
        // changing the draft only stops waiting for it, never interrupts Glance mid-write.
        val deferred = WidgetPreviewComposer.request(previewKey) {
            composeConfigurationPreview(context, configuration, selected, availableSize)
        }
        try {
            views = kotlinx.coroutines.withTimeout(PREVIEW_TIMEOUT_MS) { deferred.await() }
            android.util.Log.d("RimoatWidget", "Preview composed in " +
                "${android.os.SystemClock.elapsedRealtime() - started}ms at $availableSize")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            android.util.Log.e("RimoatWidget", "Configuration preview timed out after ${PREVIEW_TIMEOUT_MS}ms", e)
            failed = true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.e("RimoatWidget", "Configuration preview failed", e)
            failed = true
        }
    }
    val onRetry: () -> Unit = remember(previewKey) {
        {
            WidgetPreviewComposer.clearInFlight(previewKey)
            retryToken += 1
        }
    }
    return WidgetPreview(availableSize, views, failed, onRetry)
}

private const val PREVIEW_TIMEOUT_MS = 10_000L

@Suppress("DEPRECATION")
internal fun widgetPreviewSize(context: android.content.Context, id: Int, orientation: Int): DpSize {
    val manager = AppWidgetManager.getInstance(context)
    val options = manager.getAppWidgetOptions(id)
    val info = manager.getAppWidgetInfo(id)
    val portrait = orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // Provider dimensions are resolved pixels; options are already dp. Before the
    // launcher supplies options, use the provider's requested initial dimensions.
    val density = context.resources.displayMetrics.density
    val minSize = info?.let { DpSize((it.minWidth / density).dp, (it.minHeight / density).dp) }
    val defaultWidth = (minSize?.width?.value ?: 160f).coerceAtLeast(128f)
    val defaultHeight = (minSize?.height?.value ?: 144f).coerceAtLeast(64f)
    val width = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        .takeIf { it > 0 }?.toFloat() ?: defaultWidth
    val height = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        .takeIf { it > 0 }?.toFloat() ?: defaultHeight
    val sizes = if (android.os.Build.VERSION.SDK_INT >= 31)
        options.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty() else emptyList()
    val optionsSize = sizes.filter { it.width > 0 && it.height > 0 }.minByOrNull {
        kotlin.math.abs(it.width - width) + kotlin.math.abs(it.height - height)
    }?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(width.dp, height.dp)
    return previewCanvasSize(optionsSize)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ConfigurationPreview(widgetId: Int, preview: WidgetPreview) {
    val context = LocalContext.current
    val providerInfo = remember(context, widgetId) {
        AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.widget_preview), Modifier.align(Alignment.Start), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val scale = (maxWidth / preview.displaySize.width).coerceAtMost(1f)
            val containerModifier = if (preview.failed) {
                Modifier.fillMaxWidth().heightIn(min = preview.displaySize.height * scale)
            } else {
                Modifier.size(preview.displaySize.width * scale, preview.displaySize.height * scale)
            }
            Box(containerModifier, contentAlignment = Alignment.Center) {
                if (preview.views != null && !preview.failed && providerInfo != null) {
                    AndroidView(factory = { createConfigurationPreviewHost(it, widgetId, providerInfo) },
                        modifier = Modifier.requiredSize(preview.displaySize.width, preview.displaySize.height).graphicsLayer {
                            scaleX = scale; scaleY = scale
                        }, update = { host ->
                            if (host.tag !== preview.views) {
                                host.tag = preview.views
                                host.updateAppWidget(preview.views)
                            }
                        })
                } else if (preview.failed || providerInfo == null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.widget_preview_unavailable),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (preview.failed) {
                            TextButton(
                                onClick = preview.onRetry,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(stringResource(R.string.screen_retry))
                            }
                        }
                    }
                } else LoadingIndicator(Modifier.size(40.dp))
            }
        }
    }
}

internal fun createConfigurationPreviewHost(
    context: android.content.Context,
    widgetId: Int,
    providerInfo: android.appwidget.AppWidgetProviderInfo,
    executor: java.util.concurrent.Executor = previewExecutor
): android.appwidget.AppWidgetHostView = object : android.appwidget.AppWidgetHostView(
    WidgetPreviewComposer.previewContext(context)
) {
    init {
        viewTreeObserver.addOnScrollChangedListener { makePreviewOnly(this) }
        viewTreeObserver.addOnPreDrawListener {
            makePreviewOnly(this)
            true
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        makePreviewOnly(this)
    }

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        // Collection rows can be rebound without laying out the host again.
        makePreviewOnly(this)
        super.dispatchDraw(canvas)
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        makePreviewOnly(this)
        if (event.actionMasked != android.view.MotionEvent.ACTION_UP) return super.dispatchTouchEvent(event)
        val cancel = android.view.MotionEvent.obtain(event).apply { action = android.view.MotionEvent.ACTION_CANCEL }
        return try { super.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
    }

    // Allow preview lists to scroll without launching activities or sending commands.
    private fun makePreviewOnly(view: android.view.View) {
        if (view is android.widget.AdapterView<*>) {
            view.onItemClickListener = null
            view.onItemLongClickListener = null
        } else {
            if (view.hasOnClickListeners()) view.setOnClickListener(null)
            if (view.isClickable) view.isClickable = false
        }
        if (view.isLongClickable) {
            view.setOnLongClickListener(null)
            view.isLongClickable = false
        }
        if (view.isSoundEffectsEnabled) view.isSoundEffectsEnabled = false
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) makePreviewOnly(view.getChildAt(index))
        }
    }
}.apply {
    setAppWidget(widgetId, providerInfo)
    setPadding(0, 0, 0, 0)
    if (android.os.Build.VERSION.SDK_INT >= 26) setExecutor(executor)
    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
}

@OptIn(androidx.glance.ExperimentalGlanceApi::class, androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
internal suspend fun composeConfigurationPreview(
    context: android.content.Context,
    configuration: WidgetConfiguration,
    snapshot: WidgetSnapshot,
    availableSize: DpSize
): android.widget.RemoteViews {
    // Compose the same Glance content without starting a widget session. Sessions run a
    // WorkManager-style worker and write layout bookkeeping to a DataStore; a preview that is
    // closed or recomposed while that write is in flight can leave the store busy, and every
    // later preview then waits forever. GlanceRemoteViews renders the identical composables
    // (LocalSize = the canvas below) into one RemoteViews with no persistent state.
    val content: @androidx.compose.runtime.Composable () -> Unit = {
        WidgetColorTheme(snapshot) {
            RemoteWidgetContent(configuration.kind, -1,
                snapshot.copy(configurations = mapOf(-1 to configuration)), null)
        }
    }
    val options = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, availableSize.width.value.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, availableSize.width.value.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, availableSize.height.value.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, availableSize.height.value.toInt())
    }
    return androidx.glance.appwidget.GlanceRemoteViews().compose(
        context = context,
        size = availableSize,
        appWidgetOptions = options,
        content = content
    ).remoteViews
}

@Preview(widthDp = 640, heightDp = 360, showBackground = true)
@Preview(widthDp = 480, heightDp = 320, fontScale = 2f, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WidgetConfigurationLandscapePreview() {
    val label = stringResource(R.string.widget_power)
    val name = stringResource(R.string.widget_category_remote)
    val profile = remember(label, name) {
        RemoteProfile("preview_remote", name, buttons = listOf(
            org.qinyan.rimoat.data.RemoteButton("power", label, iconName = "power", rawPattern = listOf(9000, 4500, 560))))
    }
    var configuration by remember { mutableStateOf(WidgetConfiguration(WidgetKind.PANEL,
        shortcuts = listOf(WidgetShortcut(profile.id, "power")))) }
    val snapshot = WidgetSnapshot(loaded = true, profiles = listOf(profile),
        commands = mapOf(profile.id to WidgetCommands.choices(profile)))
    IrRemoteTheme {
        WidgetConfigurationContent(-1, WidgetKind.PANEL, snapshot, configuration,
            WidgetPreview(DpSize(240.dp, 160.dp), null), false, null,
            onChange = { configuration = it }, onCancel = {}, onSave = {}, onRetry = {}, onAddRemote = {})
    }
}
