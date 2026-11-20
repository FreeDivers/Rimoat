package org.qinyan.rimoat.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import org.qinyan.rimoat.R
import org.qinyan.rimoat.IrRemoteApplication
import org.qinyan.rimoat.i18n.AppLanguage
import org.qinyan.rimoat.i18n.asString
import kotlinx.coroutines.launch
import org.qinyan.rimoat.ui.components.ActionIconButton
import org.qinyan.rimoat.ui.components.SimpleDropdownMenu
import org.qinyan.rimoat.ui.components.SimpleDropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import org.qinyan.rimoat.ui.components.AppSnackbarHost
import androidx.compose.material3.Switch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.qinyan.rimoat.ui.components.contentWidth
import org.qinyan.rimoat.ui.components.recordItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.qinyan.rimoat.data.AppColorMode
import org.qinyan.rimoat.data.ScreenRotationCondition
import org.qinyan.rimoat.data.ScreenRotationDirection
import org.qinyan.rimoat.data.ScreenRotationScope
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.ui.components.TransmitterSelectionBottomSheet
import org.qinyan.rimoat.ui.motion.ExpandSection
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import org.qinyan.rimoat.ui.theme.Spacing
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import org.qinyan.rimoat.ui.motion.MotionAlertDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: RemoteListViewModel, onBack: () -> Unit, onRoomsClick: () -> Unit = {}, onTrashClick: () -> Unit = {}, onAboutClick: () -> Unit = {}) {
    val screenActive = LocalMotionActive.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = remember(context) { context.findActivity() }
    var selectedLanguage by remember(configuration) {
        mutableStateOf(AppLanguage.choiceTag(AppLanguage.selectedTag(context)))
    }
    LifecycleResumeEffect(Unit) {
        selectedLanguage = AppLanguage.choiceTag(AppLanguage.selectedTag(context))
        onPauseOrDispose { }
    }
    val isForceMock by viewModel.isForceMock.collectAsStateWithLifecycle()
    val selectedTransmitterType by viewModel.selectedTransmitterType.collectAsStateWithLifecycle()
    val useDynamicColor by viewModel.useDynamicColor.collectAsStateWithLifecycle()
    val colorMode by viewModel.colorMode.collectAsStateWithLifecycle()
    val pureBlackBackground by viewModel.pureBlackBackground.collectAsStateWithLifecycle()
    val rotationDirection by viewModel.rotationDirection.collectAsStateWithLifecycle()
    val autoRotateScreen by viewModel.autoRotateScreen.collectAsStateWithLifecycle()
    val rotationCondition by viewModel.rotationCondition.collectAsStateWithLifecycle()
    val rotationScope by viewModel.rotationScope.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val transferMessage by viewModel.transferMessage.collectAsStateWithLifecycle()
    val displayedError = errorMessage?.asString()
    val displayedTransfer = transferMessage?.asString()
    val transferBusy by viewModel.transferBusy.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val allRemotes by viewModel.allRemotes.collectAsStateWithLifecycle()
    var showIrFormats by rememberSaveable { mutableStateOf(false) }
    var showTransmitterSheet by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    var exportedUri by rememberSaveable { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri, null) { exportedUri = uri.toString() }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readImport(uri)
    }

    val irImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readInfraredImport(uri)
    }

    // The home page also observes this ViewModel during transitions/back previews.
    // Only the interactive page may display or consume operation feedback.
    LaunchedEffect(screenActive, errorMessage, displayedError) {
        if (screenActive) errorMessage?.let {
            snackbar.showSnackbar(checkNotNull(displayedError), duration = SnackbarDuration.Long)
            viewModel.clearError()
        }
    }
    LaunchedEffect(screenActive, transferMessage, displayedTransfer) {
        if (screenActive) transferMessage?.let {
            snackbar.showSnackbar(checkNotNull(displayedTransfer), duration = SnackbarDuration.Short)
            viewModel.clearTransferMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    ActionIconButton(stringResource(R.string.settings_back), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (transferBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val twoColumn = maxWidth >= 720.dp
                val settingsItems: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                item { SettingsSection(stringResource(R.string.settings_group_appearance)) {
                    val colorModeChoices = listOf(
                        AppColorMode.SYSTEM to R.string.color_mode_system,
                        AppColorMode.LIGHT to R.string.color_mode_light,
                        AppColorMode.DARK to R.string.color_mode_dark
                    )
                    val selectedColorMode = colorModeChoices.indexOfFirst { it.first == colorMode }.coerceAtLeast(0)
                    SettingsChoiceAction(
                        title = stringResource(R.string.settings_color_mode),
                        icon = Icons.Rounded.Brightness6,
                        value = stringResource(colorModeChoices[selectedColorMode].second),
                        options = colorModeChoices.map { (_, label) -> stringResource(label) },
                        selectedIndex = selectedColorMode,
                        onSelected = { index -> viewModel.setColorMode(colorModeChoices[index].first) }
                    )
                    val themeOptions = listOf(
                        stringResource(R.string.theme_default_green),
                        if (viewModel.isDynamicColorSupported) stringResource(R.string.theme_dynamic)
                        else stringResource(R.string.theme_dynamic_android12)
                    )
                    SettingsChoiceAction(
                        title = stringResource(R.string.settings_theme),
                        icon = Icons.Rounded.Palette,
                        value = themeOptions[if (useDynamicColor) 1 else 0],
                        options = themeOptions,
                        selectedIndex = if (useDynamicColor) 1 else 0,
                        optionEnabled = { index -> index == 0 || viewModel.isDynamicColorSupported },
                        onSelected = { index -> viewModel.setUseDynamicColor(index == 1) }
                    )
                    ListItem(
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).toggleable(
                            value = pureBlackBackground,
                            enabled = screenActive,
                            role = Role.Switch,
                            onValueChange = viewModel::setPureBlackBackground
                        ).heightIn(min = 64.dp),
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        headlineContent = { Text(stringResource(R.string.settings_switch_pure_black)) },
                        leadingContent = {
                            Icon(Icons.Rounded.DarkMode, null, Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingContent = { Switch(checked = pureBlackBackground, onCheckedChange = null) }
                    )
                    val languageChoices = listOf(
                        "" to R.string.language_system,
                        "zh-CN" to R.string.language_chinese,
                        "zh-Hant" to R.string.settings_language_traditional_chinese,
                        "en" to R.string.language_english,
                        "ja" to R.string.language_japanese
                    )
                    SettingsChoiceAction(
                        title = stringResource(R.string.settings_language),
                        icon = Icons.Rounded.Language,
                        value = stringResource(languageChoices.firstOrNull { (tag, _) ->
                            selectedLanguage == tag
                        }?.second ?: R.string.language_system),
                        options = languageChoices.map { (_, label) -> stringResource(label) },
                        selectedIndex = languageChoices.indexOfFirst { (tag, _) ->
                            selectedLanguage == tag
                        }.coerceAtLeast(0),
                        onSelected = { index ->
                            activity?.let {
                                AppLanguage.set(it, languageChoices[index].first)
                                // The selected option can change even when the effective language stays the same.
                                selectedLanguage = languageChoices[index].first
                                val app = it.application as IrRemoteApplication
                                app.applicationScope.launch { app.widgets.configurationChanged() }
                            }
                        }
                    )
                } }
                item { SettingsSection(stringResource(R.string.settings_group_remotes)) {
                    SettingsAction(stringResource(R.string.settings_rooms), Icons.Rounded.MeetingRoom, onClick = onRoomsClick)
                    SettingsAction(stringResource(R.string.settings_trash), Icons.Rounded.DeleteOutline, onClick = onTrashClick)
                } }
                item { SettingsSection(stringResource(R.string.settings_group_data)) {
                    SettingsAction(stringResource(R.string.settings_backup_all), Icons.Rounded.FileUpload, enabled = !transferBusy,
                        onClick = { exportLauncher.launch("Rimoat-backup.json") })
                    SettingsAction(stringResource(R.string.settings_import_backup), Icons.Rounded.FileDownload, enabled = !transferBusy,
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
                    SettingsAction(stringResource(R.string.migration_ir_import), Icons.Rounded.FileDownload, enabled = !transferBusy,
                        onClick = { showIrFormats = true })
                } }
                item { SettingsSection(stringResource(R.string.settings_group_rotation)) {
                    val directionChoices = listOf(
                        ScreenRotationDirection.REVERSE_PORTRAIT to R.string.rotation_direction_reverse_portrait,
                        ScreenRotationDirection.SENSOR_PORTRAIT to R.string.rotation_direction_sensor_portrait,
                        ScreenRotationDirection.LANDSCAPE to R.string.rotation_direction_landscape,
                        ScreenRotationDirection.REVERSE_LANDSCAPE to R.string.rotation_direction_reverse_landscape
                    )
                    val selectedDirection = directionChoices.indexOfFirst { it.first == rotationDirection }.coerceAtLeast(0)
                    SettingsChoiceAction(
                        title = stringResource(R.string.settings_rotation_direction),
                        icon = Icons.Rounded.ScreenRotation,
                        value = stringResource(directionChoices[selectedDirection].second),
                        options = directionChoices.map { (_, label) -> stringResource(label) },
                        selectedIndex = selectedDirection,
                        onSelected = { index -> viewModel.setRotationDirection(directionChoices[index].first) }
                    )
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        border = if (autoRotateScreen) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)) else null
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            ListItem(
                                modifier = Modifier.clip(MaterialTheme.shapes.medium).toggleable(
                                    value = autoRotateScreen,
                                    enabled = screenActive,
                                    role = Role.Switch,
                                    onValueChange = viewModel::setAutoRotateScreen
                                ).heightIn(min = 64.dp),
                                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                                headlineContent = { Text(stringResource(R.string.settings_auto_rotate_screen)) },
                                leadingContent = {
                                    Icon(Icons.Rounded.ScreenLockRotation, null, Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                trailingContent = { Switch(checked = autoRotateScreen, onCheckedChange = null) }
                            )
                            ExpandSection(visible = autoRotateScreen) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                    )
                                    val conditionChoices = listOf(
                                        ScreenRotationCondition.EXTERNAL_ONLY to R.string.rotation_condition_external_only,
                                        ScreenRotationCondition.ALWAYS to R.string.rotation_condition_always
                                    )
                                    val selectedCondition = conditionChoices.indexOfFirst { it.first == rotationCondition }.coerceAtLeast(0)
                                    SettingsChoiceAction(
                                        title = stringResource(R.string.settings_rotation_condition),
                                        icon = Icons.Rounded.Usb,
                                        value = stringResource(conditionChoices[selectedCondition].second),
                                        options = conditionChoices.map { (_, label) -> stringResource(label) },
                                        selectedIndex = selectedCondition,
                                        onSelected = { index -> viewModel.setRotationCondition(conditionChoices[index].first) }
                                    )
                                    val scopeChoices = listOf(
                                        ScreenRotationScope.REMOTE_AND_MATCHING to R.string.rotation_scope_remote_and_matching,
                                        ScreenRotationScope.ALL_SCREENS to R.string.rotation_scope_all_screens
                                    )
                                    val selectedScope = scopeChoices.indexOfFirst { it.first == rotationScope }.coerceAtLeast(0)
                                    SettingsChoiceAction(
                                        title = stringResource(R.string.settings_rotation_scope),
                                        icon = Icons.Rounded.Devices,
                                        value = stringResource(scopeChoices[selectedScope].second),
                                        options = scopeChoices.map { (_, label) -> stringResource(label) },
                                        selectedIndex = selectedScope,
                                        onSelected = { index -> viewModel.setRotationScope(scopeChoices[index].first) }
                                    )
                                }
                            }
                        }
                    }
                } }
                item { SettingsSection(stringResource(R.string.settings_group_application)) {
                    SettingsValueRow(
                        title = stringResource(R.string.control_ir_channel),
                        icon = Icons.Rounded.SettingsInputAntenna,
                        value = currentTransmitterLabel(selectedTransmitterType, isForceMock),
                        trailingIcon = Icons.Rounded.ChevronRight,
                        trailingIconSize = 24.dp,
                        enabled = screenActive,
                        onClick = { showTransmitterSheet = true }
                    )
                    SettingsAction(stringResource(R.string.settings_about), Icons.Rounded.Info, onClick = onAboutClick)
                } }
                }
                if (twoColumn) {
                    val sections = recordItems(settingsItems)
                    val left = sections.filterIndexed { index, _ -> index == 0 || index == 2 }
                    val right = sections.filterIndexed { index, _ -> index != 0 && index != 2 }
                    LazyColumn(
                        Modifier.contentWidth(960.dp),
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        item("settings_two_column") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                    left.forEach { it.content.invoke(this@item) }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                    right.forEach { it.content.invoke(this@item) }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.contentWidth(),
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        content = settingsItems
                    )
                }
            }
        }
    }

    if (screenActive) {
        pendingImport?.let { document ->
            MigrationImportDialog(document, allRemotes.map { it.id }.toSet(), transferBusy,
                onDismiss = viewModel::cancelImport, onImport = viewModel::commitImport)
        }
        if (showIrFormats) MotionAlertDialog(onDismissRequest = { showIrFormats = false },
            title = { Text(stringResource(R.string.migration_ir_import)) },
            text = { Text(stringResource(R.string.migration_ir_formats)) },
            dismissButton = { TextButton(onClick = { showIrFormats = false }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { TextButton(onClick = { showIrFormats = false; irImportLauncher.launch(arrayOf("*/*")) }) {
                Text(stringResource(R.string.migration_choose_file))
            } })
        exportedUri?.let { saved ->
            BackupSavedDialog(saved, onDismiss = { exportedUri = null }, onError = viewModel::reportTransferError)
        }
        if (showTransmitterSheet) {
            TransmitterSelectionBottomSheet(
                transmitterManager = viewModel.transmitterManager,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                onDismissRequest = { showTransmitterSheet = false }
            )
        }
    }
}

@Composable
private fun currentTransmitterLabel(
    selectedType: TransmitterType?,
    forceMock: Boolean
): String = if (forceMock) {
    stringResource(R.string.control_demo_mode)
} else {
    when (selectedType) {
        null -> stringResource(R.string.control_auto_select)
        TransmitterType.MOCK, TransmitterType.NONE -> stringResource(R.string.control_demo_mode)
        TransmitterType.USB_TIQIAA -> stringResource(R.string.control_tiqiaa)
        TransmitterType.USB_ELKSMART -> stringResource(R.string.control_elksmart)
        TransmitterType.USB_SERIAL -> stringResource(R.string.control_serial_ir)
        TransmitterType.USB -> stringResource(R.string.control_usb)
        TransmitterType.AUDIO -> stringResource(R.string.control_audio_ir)
        TransmitterType.BUILT_IN -> stringResource(R.string.control_builtin_ir)
    }
}


@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(horizontal = 4.dp).semantics { heading() },
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f))) {
            Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
private fun SettingsChoiceAction(
    title: String,
    icon: ImageVector,
    value: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    optionEnabled: (Int) -> Boolean = { true },
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menuPresent by remember { mutableStateOf(false) }
    var pendingSelection by remember { mutableStateOf<Int?>(null) }
    val currentOnSelected by rememberUpdatedState(onSelected)
    // Locale changes recreate the Activity. Release the popup window's focus first.
    LaunchedEffect(pendingSelection, menuPresent) {
        if (!menuPresent) pendingSelection?.let { index ->
            pendingSelection = null
            currentOnSelected(index)
        }
    }
    val active = LocalMotionActive.current
    LaunchedEffect(active) { if (!active) expanded = false }

    SettingsValueRow(
        title = title,
        icon = icon,
        value = value,
        trailingIcon = Icons.Rounded.ExpandMore,
        trailingIconSize = 20.dp,
        enabled = active,
        onClick = { expanded = true },
        modifier = modifier,
    ) {
        Box(Modifier.size(width = 24.dp, height = 48.dp)) {
            SimpleDropdownMenu(
                expanded = expanded && active,
                onDismissRequest = { expanded = false },
                selectedIndex = selectedIndex,
                itemCount = options.size
            ) {
                DisposableEffect(Unit) {
                    menuPresent = true
                    onDispose { menuPresent = false }
                }
                options.forEachIndexed { index, option ->
                    val isSelected = index == selectedIndex
                    SimpleDropdownMenuItem(
                        text = { Text(option) },
                        selected = isSelected,
                        enabled = optionEnabled(index),
                        onClick = {
                            expanded = false
                            if (!isSelected) pendingSelection = index
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsValueRow(
    title: String,
    icon: ImageVector,
    value: String,
    trailingIcon: ImageVector,
    trailingIconSize: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingOverlay: @Composable BoxScope.() -> Unit = {},
) {
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.bodyLarge
    val valueStyle = MaterialTheme.typography.bodyMedium
    // The narrowest legal wrap preserves complete words (and uses the language's
    // own break opportunities for scripts such as Japanese).
    val titleMinimum = with(density) {
        measurer.measure(title, titleStyle).multiParagraph.minIntrinsicWidth.toDp() + 1.dp
    }
    val valueMinimum = with(density) {
        measurer.measure(value, valueStyle).multiParagraph.minIntrinsicWidth.toDp() + 1.dp
    }
    val trailingWidth = 4.dp + trailingIconSize

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .semantics { stateDescription = value }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp),
    ) {
        // Recover inert space before splitting a word at large font scales. The
        // whole row remains the touch target; neither text column moves below the other.
        val minimumTextWidth = titleMinimum + valueMinimum
        val compact = minimumTextWidth > maxWidth - (84.dp + trailingWidth)
        val horizontalPadding = when {
            !compact -> 16.dp
            minimumTextWidth <= maxWidth - (56.dp + trailingWidth) -> 8.dp
            else -> 0.dp // The enclosing group already supplies its outer inset.
        }
        val iconGap = if (compact) 8.dp else 16.dp
        val textGap = if (compact) 8.dp else 12.dp
        val textWidth = (maxWidth - horizontalPadding * 2 - 24.dp - iconGap - textGap - trailingWidth)
            .coerceAtLeast(2.dp)
        val preferredTitleWidth = textWidth / 2.15f
        val titleWidth = if (titleMinimum + valueMinimum <= textWidth) {
            preferredTitleWidth.coerceIn(titleMinimum, textWidth - valueMinimum)
        } else {
            // At extreme widths where both words cannot fit, share the available
            // width in proportion to their needs while keeping all text visible.
            textWidth * (titleMinimum.value / (titleMinimum + valueMinimum).value.coerceAtLeast(1f))
        }
        val valueWidth = textWidth - titleWidth + trailingWidth

        Row(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp), tint = secondaryColor)
            Spacer(Modifier.width(iconGap))
            Text(title, Modifier.width(titleWidth), style = titleStyle)
            Spacer(Modifier.width(textGap))
            // This box anchors an optional trailing overlay (such as a dropdown menu).
            Box(Modifier.width(valueWidth), contentAlignment = Alignment.CenterEnd) {
                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(value, Modifier.weight(1f, fill = false), color = secondaryColor,
                        textAlign = TextAlign.End, style = valueStyle)
                    Spacer(Modifier.width(4.dp))
                    Icon(trailingIcon, null, Modifier.size(trailingIconSize), tint = secondaryColor)
                }
                trailingOverlay()
            }
        }
    }
}

@Composable
private fun SettingsAction(title: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    val contentColor = MaterialTheme.colorScheme.onSurface.let { if (enabled) it else it.copy(alpha = 0.38f) }
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant.let { if (enabled) it else it.copy(alpha = 0.38f) }
    ListItem(
        modifier = Modifier.clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).heightIn(min = 64.dp),
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, null, Modifier.size(24.dp)) },
        trailingContent = { Icon(Icons.Rounded.ChevronRight, null, Modifier.size(24.dp)) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent, headlineColor = contentColor, leadingIconColor = iconColor, trailingIconColor = iconColor)
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
