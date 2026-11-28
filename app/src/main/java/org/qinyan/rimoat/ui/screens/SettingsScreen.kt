@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.ui.components.SettingsChoiceAction
import org.qinyan.rimoat.ui.components.UpdateSettingsRow
import org.qinyan.rimoat.ui.components.SettingsValueRow
import org.qinyan.rimoat.ui.components.SettingsAction
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.qinyan.rimoat.ui.components.ExpressivePageTopBar
import org.qinyan.rimoat.ui.components.ExpressiveSegmentedSurface
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import org.qinyan.rimoat.ui.viewmodel.UpdatePhase
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppLanguage
import org.qinyan.rimoat.i18n.ErrorAction
import org.qinyan.rimoat.i18n.asString
import kotlinx.coroutines.launch
import org.qinyan.rimoat.ui.components.ActionIconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import org.qinyan.rimoat.ui.components.AppSnackbarHost
import org.qinyan.rimoat.ui.components.openStorageSettings
import org.qinyan.rimoat.ui.components.showAppError
import org.qinyan.rimoat.ui.components.transmitterShortLabelRes
import org.qinyan.rimoat.ui.screens.settings.BackupSavedDialog
import androidx.compose.material3.Switch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import org.qinyan.rimoat.ui.components.contentWidth
import org.qinyan.rimoat.ui.components.recordItems
import org.qinyan.rimoat.ui.components.replay
import org.qinyan.rimoat.ui.components.TwoPaneLayout
import org.qinyan.rimoat.ui.components.rememberWideLayout
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
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
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import org.qinyan.rimoat.ui.motion.MotionAlertDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(viewModel: RemoteListViewModel, onBack: () -> Unit, onRoomsClick: () -> Unit = {}, onTrashClick: () -> Unit = {}, onAboutClick: () -> Unit = {},
    updateViewModel: org.qinyan.rimoat.ui.viewmodel.AppUpdateViewModel = appUpdateViewModel()) {
    val twoColumn = rememberWideLayout()
    val appearanceScroll = rememberLazyListState()
    val managementScroll = rememberLazyListState()
    val portraitScroll = rememberLazyListState()
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
    val errorAppMessage by viewModel.errorAppMessage.collectAsStateWithLifecycle()
    val transferMessage by viewModel.transferMessage.collectAsStateWithLifecycle()
    val displayedTransfer = transferMessage?.asString()
    val transferBusy by viewModel.transferBusy.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val allRemotes by viewModel.allRemotes.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
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
    val activeError = errorAppMessage ?: errorMessage?.let { org.qinyan.rimoat.i18n.AppError.Resource(it) }
    LaunchedEffect(screenActive, activeError) {
        if (screenActive) activeError?.let { appError ->
            val result = snackbar.showAppError(context, appError)
            if (result == SnackbarResult.ActionPerformed) {
                when (appError.action) {
                    is ErrorAction.Retry -> if (!viewModel.retryLastExport()) {
                        exportLauncher.launch("Rimoat-backup.json")
                    }
                    is ErrorAction.OpenStorageSettings -> openStorageSettings(context)
                    else -> Unit
                }
            }
            viewModel.clearError()
        }
    }
    LaunchedEffect(screenActive, transferMessage, displayedTransfer) {
        if (screenActive) transferMessage?.let {
            snackbar.showSnackbar(checkNotNull(displayedTransfer), duration = SnackbarDuration.Short)
            viewModel.clearTransferMessage()
        }
    }

    val scrollBehavior = if (twoColumn) null else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    ProvideExpressiveCanvas {
    Scaffold(
        modifier = if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier,
        containerColor = ExpressiveSurface.canvas,
        topBar = {
            ExpressivePageTopBar(
                title = stringResource(R.string.settings_title),
                wide = twoColumn,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    ActionIconButton(stringResource(R.string.common_back), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (transferBusy) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val settingsItems: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                item("appearance") { SettingsSection(stringResource(R.string.settings_group_appearance)) {
                    item { index, count ->
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
                            onSelected = { selected -> viewModel.setColorMode(colorModeChoices[selected].first) },
                            index = index,
                            count = count
                        )
                    }
                    item { index, count ->
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
                            optionEnabled = { selected -> selected == 0 || viewModel.isDynamicColorSupported },
                            onSelected = { selected -> viewModel.setUseDynamicColor(selected == 1) },
                            index = index,
                            count = count
                        )
                    }
                    item { index, count ->
                        val blackInteraction = remember { MutableInteractionSource() }
                        ExpressiveSegmentedSurface(
                            index = index,
                            count = count,
                            interactionSource = blackInteraction,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                modifier = Modifier.toggleable(
                                    value = pureBlackBackground,
                                    interactionSource = blackInteraction,
                                    indication = ripple(),
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
                        }
                    }
                    item { index, count ->
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
                            onSelected = { selected ->
                                activity?.let {
                                    AppLanguage.set(it, languageChoices[selected].first)
                                    // The selected option can change even when the effective language stays the same.
                                    selectedLanguage = languageChoices[selected].first
                                    AppLanguage.refreshWidgets(it)
                                }
                            },
                            index = index,
                            count = count
                        )
                    }
                } }
                item("remotes") { SettingsSection(stringResource(R.string.settings_group_remotes)) {
                    item { index, count ->
                        SettingsAction(stringResource(R.string.settings_rooms), Icons.Rounded.MeetingRoom, index = index, count = count, onClick = onRoomsClick)
                    }
                    item { index, count ->
                        SettingsAction(stringResource(R.string.settings_trash), Icons.Rounded.DeleteOutline, index = index, count = count, onClick = onTrashClick)
                    }
                } }
                item("data") { SettingsSection(stringResource(R.string.settings_group_data)) {
                    item { index, count ->
                        SettingsAction(stringResource(R.string.settings_backup_all), Icons.Rounded.FileUpload, enabled = !transferBusy,
                            index = index, count = count,
                            onClick = { exportLauncher.launch("Rimoat-backup.json") })
                    }
                    item { index, count ->
                        SettingsAction(stringResource(R.string.settings_import_backup), Icons.Rounded.FileDownload, enabled = !transferBusy,
                            index = index, count = count,
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
                    }
                    item { index, count ->
                        SettingsAction(stringResource(R.string.migration_ir_import), Icons.Rounded.FileDownload, enabled = !transferBusy,
                            index = index, count = count,
                            onClick = { showIrFormats = true })
                    }
                } }
                item("rotation") { SettingsSection(stringResource(R.string.settings_group_rotation)) {
                    item { index, count ->
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
                            onSelected = { selected -> viewModel.setRotationDirection(directionChoices[selected].first) },
                            index = index,
                            count = count
                        )
                    }
                    item { index, count ->
                        val rotationInteraction = remember { MutableInteractionSource() }
                        ExpressiveSegmentedSurface(
                            index = index,
                            count = count,
                            interactionSource = rotationInteraction,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                ListItem(
                                    modifier = Modifier.toggleable(
                                        value = autoRotateScreen,
                                        interactionSource = rotationInteraction,
                                        indication = ripple(),
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
                                            onSelected = { selected -> viewModel.setRotationCondition(conditionChoices[selected].first) },
                                            segmented = false
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
                                            onSelected = { selected -> viewModel.setRotationScope(scopeChoices[selected].first) },
                                            segmented = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                } }
                item("application") { SettingsSection(stringResource(R.string.settings_group_application)) {
                    item { index, count ->
                        SettingsValueRow(
                            title = stringResource(R.string.control_ir_channel),
                            icon = Icons.Rounded.SettingsInputAntenna,
                            value = currentTransmitterLabel(selectedTransmitterType, isForceMock),
                            trailingIcon = Icons.Rounded.ChevronRight,
                            trailingIconSize = 24.dp,
                            enabled = screenActive,
                            onClick = { showTransmitterSheet = true },
                            index = index,
                            count = count
                        )
                    }
                    item { index, count ->
                        UpdateSettingsRow(
                            updateState = updateState,
                            index = index,
                            count = count,
                            onClick = {
                                if (updateState.minimized && updateState.phase != UpdatePhase.CLOSED) {
                                    updateViewModel.resume()
                                } else {
                                    updateViewModel.check()
                                }
                            },
                        )
                    }
                    item { index, count ->
                        SettingsAction(stringResource(R.string.settings_about), Icons.Rounded.Info, index = index, count = count, onClick = onAboutClick)
                    }
                } }
                }
                if (twoColumn) {
                    val sections = recordItems(settingsItems)
                    val left = sections.filter { it.key == "appearance" || it.key == "data" }
                    val right = sections.filter { it.key != "appearance" && it.key != "data" }
                    TwoPaneLayout(primary = {
                        LazyColumn(Modifier.fillMaxSize(), state = appearanceScroll,
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)) { replay(left) }
                    }, secondary = {
                        LazyColumn(Modifier.fillMaxSize(), state = managementScroll,
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)) { replay(right) }
                    })
                } else {
                    LazyColumn(
                        Modifier.contentWidth(),
                        state = portraitScroll,
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        content = settingsItems
                    )
                }
            }
        }
    }

    if (screenActive) {
        AppUpdateHost(updateViewModel)
        pendingImport?.let { document ->
            MigrationImportDialog(document, allRemotes.map { it.id }.toSet(), transferBusy,
                onDismiss = viewModel::cancelImport, onImport = viewModel::commitImport,
                dynamicColorSupported = viewModel.isDynamicColorSupported)
        }
        if (showIrFormats) MotionAlertDialog(onDismissRequest = { showIrFormats = false },
            title = { Text(stringResource(R.string.migration_ir_import)) },
            text = { Text(stringResource(R.string.migration_ir_formats)) },
            dismissButton = { TextButton(onClick = { showIrFormats = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { TextButton(onClick = { showIrFormats = false; irImportLauncher.launch(arrayOf("*/*")) }, shapes = ButtonDefaults.shapes()) {
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
}

@Composable
private fun currentTransmitterLabel(
    selectedType: TransmitterType?,
    forceMock: Boolean
): String = if (forceMock) {
    stringResource(R.string.control_demo_mode)
} else if (selectedType == null) {
    stringResource(R.string.control_auto_select)
} else {
    stringResource(transmitterShortLabelRes(selectedType))
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
