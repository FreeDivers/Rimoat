package org.qinyan.rimoat.ui.learning

import org.qinyan.rimoat.ui.components.contentWidth
import org.qinyan.rimoat.ui.screens.displayName

import org.qinyan.rimoat.R

import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.asString

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.db.IrBrand
import org.qinyan.rimoat.hardware.learning.LearnerType
import org.qinyan.rimoat.hardware.learning.LearningState
import org.qinyan.rimoat.ui.HapticHelper
import org.qinyan.rimoat.ui.components.*
import org.qinyan.rimoat.ui.motion.*
import org.qinyan.rimoat.ui.screens.BrandSelectionView
import org.qinyan.rimoat.ui.screens.getCategoryIcon
import kotlinx.coroutines.CancellationException
import org.qinyan.rimoat.ui.motion.MotionAlertDialog

@Composable
private fun DeviceCategory.label(): String = stringResource(when (this) {
    DeviceCategory.TV -> R.string.screen_category_tv
    DeviceCategory.AC -> R.string.screen_category_ac
    DeviceCategory.FAN -> R.string.screen_category_fan
    DeviceCategory.AUDIO -> R.string.screen_category_audio
    DeviceCategory.PROJECTOR -> R.string.screen_category_projector
    DeviceCategory.CUSTOM -> R.string.screen_category_custom
})

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRemoteWizardScreen(viewModel: CustomRemoteWizardViewModel, onBack: () -> Unit, onRemoteCreated: (RemoteProfile) -> Unit) {
    StandaloneScreenRotationScope(
        isTargetRotationScreen = true,
        targetScreenKey = "wizard",
        transmitterManager = viewModel.transmitterManager,
        learnerManager = viewModel.learnerManager
    ) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val learner by viewModel.learnerState.collectAsStateWithLifecycle()
    val learnerType by viewModel.learnerType.collectAsStateWithLifecycle()
    val available by viewModel.isHardwareAvailable.collectAsStateWithLifecycle()
    val mock by viewModel.forceMockMode.collectAsStateWithLifecycle()
    val confirmed by viewModel.receiveConfirmed.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var addKey by rememberSaveable { mutableStateOf(false) }
    var keyName by rememberSaveable { mutableStateOf("") }
    var pickBrand by rememberSaveable { mutableStateOf(false) }
    val brandContext = androidx.compose.ui.platform.LocalContext.current
    val active = LocalMotionActive.current
    DisposableEffect(viewModel) { onDispose { viewModel.onScreenStopped() } }
    org.qinyan.rimoat.ui.CancelOperationsOnStop(viewModel::onScreenStopped)
    LaunchedEffect(state.currentKey?.id, state.currentKey?.status) {
        if (state.currentKey?.status == KeyLearnStatus.CAPTURED) HapticHelper.performCaptured(haptic, context)
    }
    BackHandler(enabled = active && (state.isSaving || state.isRestoring)) { }
    MotionContent(targetState = pickBrand, contentKey = { if (it) "brand" else "wizard" },
        isBack = { from, to -> from && !to }, backTarget = if (pickBrand) false else null,
        onBack = { pickBrand = false }) { brandPage ->
    if (brandPage) {
        var brands by remember(state.category) { mutableStateOf<List<IrBrand>>(emptyList()) }
        var query by rememberSaveable { mutableStateOf("") }
        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<UiText?>(null) }
        LaunchedEffect(state.category) {
            loading = true
            error = null
            try { brands = viewModel.brandsFor(state.category) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = UiText.Resource(R.string.screen_brand_load_failed) }
            finally { loading = false }
        }
        Scaffold(topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.screen_choose_brand),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    ActionIconButton(stringResource(R.string.screen_back_to_brand), { pickBrand = false }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }) { brandPadding ->
            Column(Modifier.fillMaxSize().padding(brandPadding)) {
                error?.let {
                    Text(
                        it.asString(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                BrandSelectionView(brands.filter { query.isBlank() || it.nameCn.contains(query, true) || it.nameEn.contains(query, true) || it.pinyin.contains(query, true) },
                    rememberLazyListState(), query,
                    org.qinyan.rimoat.data.db.IrCategory(0, state.category.label(), "", when (state.category) {
                        DeviceCategory.TV -> "tv"; DeviceCategory.AC -> "air"; DeviceCategory.FAN -> "fan"
                        DeviceCategory.AUDIO -> "amp"; DeviceCategory.PROJECTOR -> "pjt"; DeviceCategory.CUSTOM -> "custom"
                    }), loading, { query = it }, {
                        HapticHelper.performRemoteClick(haptic, context)
                        viewModel.setBrand(it.displayName(brandContext))
                        pickBrand = false
                    })
            }
        }
    } else {
    MotionContent(targetState = state, contentKey = { it.motionKey },
        isBack = { from, to -> to.step.ordinal < from.step.ordinal },
        backTarget = state.backPreview(), onBack = { viewModel.handleBack() }) { page ->
        Scaffold(topBar = {
            TopAppBar(
                title = {
                    val title = when (page.step) {
                        WizardStep.SETUP -> stringResource(R.string.screen_learn_remote)
                        WizardStep.LEARNING -> stringResource(R.string.screen_learn_buttons)
                        WizardStep.REVIEW -> stringResource(R.string.screen_save_remote)
                    }
                    Text(
                        title,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics {
                            heading()
                            contentDescription = title
                        }
                    )
                },
                navigationIcon = {
                    ActionIconButton(stringResource(R.string.screen_back), { if (!viewModel.handleBack()) onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    ScreenRotationIconButton()
                    if (page.step == WizardStep.LEARNING) {
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                HapticHelper.performRemoteClick(haptic, context)
                                viewModel.finishEarly()
                            },
                            enabled = page.canFinishEarly,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text(stringResource(R.string.screen_done)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }, bottomBar = {
            if (page.step == WizardStep.LEARNING) LearningKeyActions(page, available,
                onRecord = { HapticHelper.performStartListening(haptic, context); viewModel.startLearningCurrentKey() },
                onCancel = viewModel::cancelCurrentKeyLearning,
                onSkip = viewModel::skipCurrentKey,
                onConfirm = viewModel::confirmCurrentKey,
                onTest = { HapticHelper.performTestEmit(haptic, context); viewModel.testTransmitCurrentKey() })
        }) { padding ->
            if (page.isRestoring) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else when (page.step) {
                WizardStep.SETUP -> Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).contentWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val textMeasurer = rememberTextMeasurer()
                    val categoryLabelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    val categoryLabels = DeviceCategory.entries.map { it to it.label() }
                    val labelWidth = categoryLabels.maxOfOrNull { (_, label) ->
                        textMeasurer.measure(label, categoryLabelStyle, softWrap = false).size.width
                    } ?: 0
                    val minimumCardWidth = with(LocalDensity.current) { labelWidth.toDp() } + 24.dp
                    val brandFocusRequester = remember { FocusRequester() }
                    val keyboard = LocalSoftwareKeyboardController.current
                    val focusManager = LocalFocusManager.current
                    val nameError = if (page.errorMessage != null && page.remoteName.isBlank()) page.errorMessage.asString() else null

                    Text(
                        stringResource(R.string.screen_appliance_type),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val columns = ((maxWidth + 8.dp) / (minimumCardWidth + 8.dp)).toInt().coerceIn(1, 3)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            categoryLabels.chunked(columns).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { (category, label) ->
                                        val selected = page.category == category
                                        Card(
                                            onClick = {
                                                HapticHelper.performSelection(haptic, context)
                                                viewModel.setCategory(category)
                                            },

                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 88.dp)
                                                .semantics(mergeDescendants = true) {
                                                    role = Role.Button
                                                    this.selected = selected
                                                },
                                            colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                                        ) {
                                            Column(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                Icon(getCategoryIcon(category), null, Modifier.size(28.dp))
                                                Spacer(Modifier.height(8.dp))
                                                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = page.remoteName,
                        onValueChange = viewModel::setRemoteName,
                        modifier = Modifier.fillMaxWidth().semantics { nameError?.let { error(it) } },
                        label = { Text(stringResource(R.string.screen_remote_name)) },
                        singleLine = true,
                        isError = nameError != null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { brandFocusRequester.requestFocus() })
                    )
                    OutlinedTextField(
                        value = page.brand,
                        onValueChange = viewModel::setBrand,
                        modifier = Modifier.fillMaxWidth().focusRequester(brandFocusRequester),
                        label = { Text(stringResource(R.string.screen_brand_optional)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                        }),
                        trailingIcon = {
                            ActionIconButton(stringResource(R.string.screen_choose_catalog_brand), {
                                keyboard?.hide()
                                pickBrand = true
                            }) { Icon(Icons.Rounded.ListAlt, contentDescription = null) }
                        }
                    )
                    page.errorMessage?.let {
                        Text(
                            it.asString(),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                    Button(
                        onClick = {
                            keyboard?.hide()
                            HapticHelper.performRemoteClick(haptic, context)
                            viewModel.proceedToLearning()
                        },

                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.screen_start_learning)) }
                }
                WizardStep.LEARNING -> LearningKeyWorkspace(
                    page = page, learner = learner, learnerType = learnerType,
                    available = available, receiveConfirmed = confirmed, mock = mock,
                    onMock = { viewModel.setForceMockMode(it) },
                    onSelect = viewModel::selectKey,
                    onAdd = { keyName = ""; addKey = true },
                    onRecord = { HapticHelper.performStartListening(haptic, context); viewModel.startLearningCurrentKey() },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
                WizardStep.REVIEW -> LearningReviewContent(page, viewModel::returnToLearning,
                    { viewModel.saveRemote(onRemoteCreated) }, Modifier.fillMaxSize().padding(padding))
            }
        }
    }
    if (active && addKey) {
        val dialogKeyboard = LocalSoftwareKeyboardController.current
        MotionAlertDialog(
            onDismissRequest = { addKey = false },
            title = { Text(stringResource(R.string.screen_add_button), modifier = Modifier.semantics { heading() }) },
            text = {
                OutlinedTextField(
                    value = keyName,
                    onValueChange = { keyName = it.take(100) },
                    label = { Text(stringResource(R.string.screen_button_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        dialogKeyboard?.hide()
                        if (keyName.isNotBlank()) {
                            HapticHelper.performSelection(haptic, context)
                            viewModel.addCustomKey(keyName)
                            addKey = false
                        }
                    })
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialogKeyboard?.hide()
                        HapticHelper.performSelection(haptic, context)
                        viewModel.addCustomKey(keyName)
                        addKey = false
                    },
                    enabled = keyName.isNotBlank(),

                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.screen_add)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dialogKeyboard?.hide()
                        HapticHelper.performSelection(haptic, context)
                        addKey = false
                    },

                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(stringResource(R.string.screen_cancel)) }
            }
        )
    }
    }
    }
    }
}

/** Metadata and learned keys share a scroll area, leaving actions reachable on short screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LearningReviewContent(page: CustomRemoteWizardUiState, onContinue: () -> Unit,
    onSave: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    page.remoteName,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(page.category.label(), style = MaterialTheme.typography.bodyMedium)
                    if (page.brand.isNotBlank()) Text(page.brand, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Text(
                    stringResource(R.string.screen_learned_count, page.learnedCount),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )
            }
            items(page.keyQueue.filter { it.status == KeyLearnStatus.LEARNED }, key = { it.id }) { key ->
                ListItem(headlineContent = { Text(key.displayLabel()) }, leadingContent = { Icon(getIconVector(key.iconName) ?: Icons.Rounded.Tune, null) },
                    trailingContent = { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) })
            }
            page.errorMessage?.let { error ->
                item {
                    Text(
                        error.asString(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    HapticHelper.performSelection(haptic, context)
                    onContinue()
                },
                enabled = !page.isSaving,

                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.screen_continue_learning)) }
            Button(
                onClick = {
                    HapticHelper.performSaveSuccess(haptic, context)
                    onSave()
                },
                enabled = page.learnedCount > 0 && !page.isSaving,

                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Text(if (page.isSaving) stringResource(R.string.screen_saving) else stringResource(R.string.screen_save))
            }
        }
    }
}

/** Default queue labels are translated for display; custom key names remain untouched. */
@Composable
internal fun WizardKeyItem.displayLabel(): String {
    LocalConfiguration.current
    val context = LocalContext.current
    return builtinLabel?.let { org.qinyan.rimoat.i18n.localizedLabel(it, context) } ?: label
}
