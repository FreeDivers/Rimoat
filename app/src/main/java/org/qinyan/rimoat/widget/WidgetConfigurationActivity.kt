package org.qinyan.rimoat.widget

import android.app.Activity
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.asString
import androidx.compose.ui.res.stringResource
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.IrRemoteApplication
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.compose
import androidx.glance.material3.ColorProviders
import org.qinyan.rimoat.ui.theme.LightColorScheme
import org.qinyan.rimoat.ui.theme.DarkColorScheme
import org.qinyan.rimoat.ui.motion.MotionAlertDialog
import org.qinyan.rimoat.ui.components.TwoPaneLayout
import org.qinyan.rimoat.ui.components.rememberWideLayout
import org.qinyan.rimoat.ui.components.recordItems
import org.qinyan.rimoat.ui.components.replay

class WidgetConfigurationActivity : AppCompatActivity() {
    private val refreshRateController by lazy { org.qinyan.rimoat.ui.DisplayRefreshRateController(this) }

    override fun onResume() {
        super.onResume()
        refreshRateController.start()
        (application as IrRemoteApplication).let { app ->
            app.applicationScope.launch { app.widgets.configurationChanged() }
        }
    }
    override fun onPause() { refreshRateController.stop(); super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshRateController.update()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(Activity.RESULT_CANCELED, result)
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
        val kind = WidgetPickerPreviews.kind(info?.provider) ?: run { finish(); return }
        enableEdgeToEdge()
        val app = application as IrRemoteApplication
        setContent {
            val composeView = LocalView.current
            DisposableEffect(composeView) {
                refreshRateController.bind(composeView)
                onDispose { refreshRateController.bind(null) }
            }
            val dynamic by app.themePreferences.useDynamicColor.collectAsState()
            val colorMode by app.themePreferences.colorMode.collectAsState()
            val pureBlack by app.themePreferences.pureBlackBackground.collectAsState()
            IrRemoteTheme(colorMode = colorMode, dynamicColor = dynamic, pureBlackBackground = pureBlack) {
                WidgetConfigurationScreen(app, widgetId, kind, onCancel = ::finish,
                    onSaved = { setResult(Activity.RESULT_OK, result); finish() })
            }
        }
    }
}

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
    var error by rememberSaveable { mutableStateOf<String?>(null) }
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
                        check(currentDraft.shortcuts.isNotEmpty()) { AppStrings.get(R.string.widget_select_shortcut, "请选择一个快捷键") }
                        currentDraft.shortcuts.forEach { shortcut ->
                            check(app.widgets.state.value.commands[shortcut.remoteId].orEmpty().any { it.id == shortcut.buttonId }) { AppStrings.get(R.string.widget_selected_button_changed, "所选按键已改变，请重新选择") }
                        }
                        app.widgets.save(widgetId, currentDraft)
                        onSaved()
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: AppStrings.get(R.string.widget_save_failed, "保存失败") }
                    finally { saving = false }
                }
            }
        })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WidgetConfigurationContent(
    widgetId: Int,
    kind: WidgetKind,
    snapshot: WidgetSnapshot,
    currentDraft: WidgetConfiguration?,
    preview: WidgetPreview,
    saving: Boolean,
    error: String?,
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
                        TextButton(onClick = onCancel, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.widget_cancel)) }
                        Button(onClick = onSave, enabled = !saving && currentDraft?.shortcuts?.isNotEmpty() == true, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(if (saving) stringResource(R.string.widget_saving) else stringResource(R.string.widget_done))
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onCancel, enabled = !saving) { Text(stringResource(R.string.widget_cancel)) }
                        Button(onClick = onSave, enabled = !saving && currentDraft?.shortcuts?.isNotEmpty() == true, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(if (saving) stringResource(R.string.widget_saving) else stringResource(R.string.widget_done))
                        }
                    }
                }
            }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = onCancel, enabled = !saving) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.widget_cancel_config)) }
        }) },
        bottomBar = { if (!wide) Box(Modifier.navigationBarsPadding()) { actions() } }
    ) { padding ->
        val content = recordItems {
            if (!snapshot.loaded || currentDraft == null) item(key = "preview_loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (snapshot.error != null) item(key = "preview_error") {
                Text(snapshot.error!!.asString(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.widget_reload)) }
            }
            if (currentDraft != null) {
                val draft = currentDraft
                item(key = "preview") { ConfigurationPreview(widgetId, preview) }
                if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
                if (snapshot.profiles.isEmpty()) item {
                    Text(stringResource(R.string.widget_no_remotes), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onAddRemote) { Text(stringResource(R.string.widget_add_remote)) }
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
                                IconButton(enabled = index > 0 && !saving, onClick = {
                                    val next = draft.shortcuts.toMutableList(); java.util.Collections.swap(next, index, index - 1); change(draft.copy(shortcuts = next))
                                }) { Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.widget_move_up, command?.displayLabel(profile, context).orEmpty())) }
                                IconButton(enabled = index < draft.shortcuts.lastIndex && !saving, onClick = {
                                    val next = draft.shortcuts.toMutableList(); java.util.Collections.swap(next, index, index + 1); change(draft.copy(shortcuts = next))
                                }) { Icon(Icons.Rounded.ArrowDownward, stringResource(R.string.widget_move_down, command?.displayLabel(profile, context).orEmpty())) }
                                IconButton(enabled = !saving, onClick = { change(draft.copy(shortcuts = draft.shortcuts - shortcut)) }) { Icon(Icons.Rounded.Close, stringResource(R.string.widget_remove_button, command?.displayLabel(profile, context).orEmpty())) }
                            } else IconButton(enabled = !saving, onClick = { chooseRemoteId = draft.remoteId; choosing = true }) { Icon(Icons.Rounded.Edit, stringResource(R.string.widget_change_button)) }
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
                    OutlinedButton(enabled = !saving && draft.shortcuts.size < WidgetConfiguration.MAX_SHORTCUTS,
                        onClick = { chooseRemoteId = if (kind == WidgetKind.PANEL) "" else draft.remoteId; choosing = true }) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.widget_add_shortcut))
                    }
                }
                if (kind == WidgetKind.PANEL) item {
                    Text(stringResource(R.string.widget_button_layout), style = MaterialTheme.typography.titleSmall)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(draft.arrangement == WidgetArrangement.GRID, onClick = { change(draft.copy(arrangement = WidgetArrangement.GRID)) }, label = { Text(stringResource(R.string.widget_grid)) })
                        FilterChip(draft.arrangement == WidgetArrangement.LIST, onClick = { change(draft.copy(arrangement = WidgetArrangement.LIST)) }, label = { Text(stringResource(R.string.widget_list)) })
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
    if (choosingRemote && currentDraft != null) {
        MotionAlertDialog(onDismissRequest = { choosingRemote = false }, title = { Text(stringResource(R.string.widget_select_remote)) }, text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(snapshot.profiles, key = { it.id }) { profile ->
                    val selected = profile.id == currentDraft.remoteId
                    ListItem(headlineContent = { Text(profile.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        supportingContent = profile.room.takeIf(String::isNotBlank)?.let { { Text(it) } },
                        trailingContent = { RadioButton(selected, onClick = null) },
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).selectable(selected, role = Role.RadioButton) {
                            val commands = snapshot.commands[profile.id].orEmpty()
                            val primary = WidgetCommands.default(profile, commands, context)
                            val keys = if (kind == WidgetKind.SINGLE) listOfNotNull(primary) else
                                (listOfNotNull(primary) + commands.take(6).map { WidgetShortcut(profile.id, it.id) }).distinct().take(6)
                            change(currentDraft.copy(remoteId = profile.id, shortcuts = keys))
                            choosingRemote = false
                        })
                }
            }
        }, confirmButton = { TextButton(onClick = { choosingRemote = false }) { Text(stringResource(R.string.widget_cancel)) } })
    }
    if (choosing && currentDraft != null) {
        val draft = currentDraft
        val selectedProfile = snapshot.profiles.firstOrNull { it.id == chooseRemoteId } ?: if (wide) snapshot.profiles.firstOrNull() else null
        MotionAlertDialog(onDismissRequest = { choosing = false }, title = { Text(if (selectedProfile == null) stringResource(R.string.widget_select_remote) else selectedProfile.name) },
            modifier = if (wide) Modifier.safeDrawingPadding().fillMaxWidth().widthIn(max = 840.dp) else Modifier,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = !wide),
            text = {
                val remotes: @Composable () -> Unit = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(snapshot.profiles, key = { it.id }) { profile ->
                        ListItem(headlineContent = { Text(profile.name) }, supportingContent = if (profile.room.isNotBlank()) ({ Text(profile.room) }) else null,
                            colors = ListItemDefaults.colors(containerColor = if (wide && selectedProfile?.id == profile.id) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.clickable { chooseRemoteId = profile.id })
                    }
                }
                }
                val buttons: @Composable () -> Unit = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    if (selectedProfile != null) {
                        val commands = snapshot.commands[selectedProfile.id].orEmpty()
                        if (commands.isEmpty()) item { Text(stringResource(R.string.widget_no_buttons)) }
                        items(commands, key = { it.id }) { command ->
                            val shortcut = WidgetShortcut(selectedProfile.id, command.id)
                            ListItem(headlineContent = { Text(command.displayLabel(selectedProfile, context)) }, trailingContent = if (shortcut in draft.shortcuts) ({ Icon(Icons.Rounded.Check, stringResource(R.string.widget_selected)) }) else null,
                                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                                modifier = Modifier.clickable {
                                    val next = if (kind == WidgetKind.SINGLE) listOf(shortcut) else (draft.shortcuts + shortcut).distinct()
                                    change(draft.copy(remoteId = if (kind == WidgetKind.PANEL) draft.remoteId else selectedProfile.id, shortcuts = next))
                                    choosing = false
                                })
                        }
                    }
                }
                }
                if (wide) BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    TwoPaneLayout(Modifier.fillMaxSize(), primaryFraction = .4f, primary = remotes, secondary = buttons)
                } else if (selectedProfile == null) remotes() else buttons()
            }, confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.widget_cancel)) } },
            dismissButton = if (!wide && kind == WidgetKind.PANEL && selectedProfile != null) ({ TextButton(onClick = { chooseRemoteId = "" }) { Text(stringResource(R.string.widget_change_remote)) } }) else null)
    }
}

/**
 * Responsive Glance renders layout buckets, then the Launcher measures that
 * RemoteViews tree using the actual allocation. Keep those two sizes separate so the editor
 * preview uses the app's canonical canvas for multi-row layouts when launcher allocations
 * include space outside the widget's content view.
 */
private data class WidgetPreview(
    val displaySize: DpSize,
    val views: android.widget.RemoteViews?,
    val failed: Boolean = false
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
    val previewKey = remember(configuration, selected, availableSize, device.uiMode, device.locales.toLanguageTags()) {
        "$availableSize|${device.uiMode}|${device.locales.toLanguageTags()}|${configuration?.hashCode()}|${selected?.hashCode()}"
    }
    var views by remember { mutableStateOf(WidgetPreviewComposer.cached(previewKey)) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(previewKey) {
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
    return WidgetPreview(availableSize, views, failed)
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

@Composable
private fun ConfigurationPreview(widgetId: Int, preview: WidgetPreview) {
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
            Box(Modifier.size(preview.displaySize.width * scale, preview.displaySize.height * scale), contentAlignment = Alignment.Center) {
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
                    Box(Modifier.matchParentSize().clickable(interactionSource = remember {
                        androidx.compose.foundation.interaction.MutableInteractionSource()
                    }, indication = null) {})
                } else if (preview.failed || providerInfo == null) Text(stringResource(R.string.widget_preview_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}

internal fun createConfigurationPreviewHost(
    context: android.content.Context,
    widgetId: Int,
    providerInfo: android.appwidget.AppWidgetProviderInfo,
    executor: java.util.concurrent.Executor = previewExecutor
): android.appwidget.AppWidgetHostView = android.appwidget.AppWidgetHostView(
    WidgetPreviewComposer.previewContext(context)
).apply {
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
