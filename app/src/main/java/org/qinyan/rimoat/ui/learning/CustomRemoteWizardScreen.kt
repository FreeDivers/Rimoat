package org.qinyan.rimoat.ui.learning

import org.qinyan.rimoat.ui.components.contentWidth
import org.qinyan.rimoat.ui.screens.displayName

import org.qinyan.rimoat.R

import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.asString
import org.qinyan.rimoat.i18n.defaultLearningLabelRes

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
import org.qinyan.rimoat.data.ApplianceType
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.db.IrBrand
import org.qinyan.rimoat.hardware.learning.LearnerType
import org.qinyan.rimoat.hardware.learning.LearningState
import org.qinyan.rimoat.ui.HapticHelper
import org.qinyan.rimoat.ui.components.*
import org.qinyan.rimoat.ui.motion.*
import org.qinyan.rimoat.ui.screens.BrandSelectionView
import org.qinyan.rimoat.ui.components.getCategoryIcon
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
import org.qinyan.rimoat.ui.motion.MotionAlertDialog

@Composable
private fun ApplianceType.label(): String = stringResource(when (this) {
    ApplianceType.TV -> R.string.screen_category_tv
    ApplianceType.AC -> R.string.screen_category_ac
    ApplianceType.FAN -> R.string.screen_category_fan
    ApplianceType.AUDIO -> R.string.screen_category_audio
    ApplianceType.PROJECTOR -> R.string.screen_category_projector
    ApplianceType.SET_TOP_BOX -> R.string.screen_category_set_top_box
    ApplianceType.DVD -> R.string.screen_category_dvd
    ApplianceType.CAMERA -> R.string.screen_category_camera
    ApplianceType.LIGHT -> R.string.screen_category_light
    ApplianceType.STREAMING_BOX -> R.string.screen_category_streaming_box
    ApplianceType.IPTV -> R.string.screen_category_iptv
    ApplianceType.CUSTOM -> R.string.screen_category_custom
})

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val wide = rememberWideLayout()
    val active = LocalMotionActive.current
    val savedRemote by viewModel.savedRemote.collectAsStateWithLifecycle()
    val onCreated by rememberUpdatedState(onRemoteCreated)
    LaunchedEffect(active, savedRemote) {
        if (active) savedRemote?.let(onCreated)
    }
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
        var brands by remember(state.selectedApplianceType) { mutableStateOf<List<IrBrand>>(emptyList()) }
        var query by rememberSaveable { mutableStateOf("") }
        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<UiText?>(null) }
        LaunchedEffect(state.selectedApplianceType) {
            loading = true
            error = null
            try { brands = viewModel.brandsFor(state.selectedApplianceType) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = UiText.Resource(R.string.screen_brand_load_failed) }
            finally { loading = false }
        }
        val brandScrollBehavior = if (!wide) TopAppBarDefaults.exitUntilCollapsedScrollBehavior() else null
        ProvideExpressiveCanvas {
        Scaffold(
            modifier = Modifier.let { if (brandScrollBehavior != null) it.nestedScroll(brandScrollBehavior.nestedScrollConnection) else it },
            containerColor = ExpressiveSurface.canvas,
            topBar = {
                ExpressivePageTopBar(
                    title = stringResource(R.string.screen_choose_brand),
                    wide = wide,
                    scrollBehavior = brandScrollBehavior,
                    navigationIcon = {
                        ActionIconButton(stringResource(R.string.screen_back_to_brand), { pickBrand = false }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        }
                    }
                )
            }
        ) { brandPadding ->
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
                    org.qinyan.rimoat.data.db.IrCategory(0, state.selectedApplianceType.label(), "",
                        state.selectedApplianceType.catalogCode), loading, { query = it }, {
                        HapticHelper.performRemoteClick(haptic, context)
                        viewModel.setBrand(it.displayName(brandContext))
                        pickBrand = false
                    })
            }
        }
        }
    } else {
    MotionContent(targetState = state, contentKey = { it.motionKey },
        isBack = { from, to -> to.step.ordinal < from.step.ordinal },
        backTarget = state.backPreview(), onBack = { viewModel.handleBack() }) { page ->
        val scrollBehavior = if (!wide && page.step != WizardStep.LEARNING) {
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        } else null
        ProvideExpressiveCanvas {
        Scaffold(
            modifier = Modifier.let { if (scrollBehavior != null) it.nestedScroll(scrollBehavior.nestedScrollConnection) else it },
            containerColor = ExpressiveSurface.canvas,
            topBar = {
                val title = when (page.step) {
                    WizardStep.SETUP -> stringResource(R.string.screen_learn_remote)
                    WizardStep.LEARNING -> stringResource(R.string.screen_learn_buttons)
                    WizardStep.REVIEW -> stringResource(R.string.screen_save_remote)
                }
                val navIcon: @Composable () -> Unit = {
                    ActionIconButton(stringResource(R.string.screen_back), { if (!viewModel.handleBack()) onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                }
                val barActions: @Composable RowScope.() -> Unit = {
                    ScreenRotationIconButton()
                    if (page.step == WizardStep.LEARNING) {
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = {
                                HapticHelper.performRemoteClick(haptic, context)
                                viewModel.finishEarly()
                            },
                            enabled = page.canFinishEarly,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text(stringResource(R.string.screen_done)) }
                    }
                }
                if (page.step == WizardStep.LEARNING) {
                    ExpressiveCompactTopBar(
                        title = title,
                        navigationIcon = navIcon,
                        actions = barActions
                    )
                } else {
                    ExpressivePageTopBar(
                        title = title,
                        wide = wide,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = navIcon,
                        actions = barActions
                    )
                }
            }, bottomBar = {
                if (page.step == WizardStep.LEARNING && !wide) LearningKeyActions(page, available,
                    onRecord = { HapticHelper.performStartListening(haptic, context); viewModel.startLearningCurrentKey() },
                    onCancel = viewModel::cancelCurrentKeyLearning,
                    onSkip = viewModel::skipCurrentKey,
                    onConfirm = viewModel::confirmCurrentKey,
                    onTest = { HapticHelper.performTestEmit(haptic, context); viewModel.testTransmitCurrentKey() })
            }
        ) { padding ->
            if (page.isRestoring) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { ContainedLoadingIndicator() }
            else when (page.step) {
                WizardStep.SETUP -> LearningSetupContent(
                    page, viewModel::setApplianceType, viewModel::setRemoteName, viewModel::setBrand,
                    onPickBrand = { pickBrand = true }, onStart = { viewModel.proceedToLearning() },
                    modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                )
                WizardStep.LEARNING -> LearningKeyWorkspace(
                    page = page, learner = learner, learnerType = learnerType,
                    available = available, receiveConfirmed = confirmed, mock = mock,
                    onMock = { viewModel.setForceMockMode(it) },
                    onSelect = viewModel::selectKey,
                    onAdd = { keyName = ""; addKey = true },
                    onRecord = { HapticHelper.performStartListening(haptic, context); viewModel.startLearningCurrentKey() },
                    modifier = Modifier.fillMaxSize().padding(padding),
                    actions = {
                        LearningKeyActions(page, available,
                            onRecord = { HapticHelper.performStartListening(haptic, context); viewModel.startLearningCurrentKey() },
                            onCancel = viewModel::cancelCurrentKeyLearning, onSkip = viewModel::skipCurrentKey,
                            onConfirm = viewModel::confirmCurrentKey,
                            onTest = { HapticHelper.performTestEmit(haptic, context); viewModel.testTransmitCurrentKey() },
                            inPane = true)
                    }
                )
                WizardStep.REVIEW -> LearningReviewContent(page, viewModel::returnToLearning,
                    { viewModel.saveRemote() }, Modifier.fillMaxSize().padding(padding))
            }
        }
        }
    }
    if (active && addKey) {
        val dialogKeyboard = LocalSoftwareKeyboardController.current
        MotionAlertDialog(
            onDismissRequest = { addKey = false },
            title = { Text(stringResource(R.string.screen_add_button), modifier = Modifier.semantics { heading() }) },
            text = {
                ExpressiveTextField(
                    value = keyName,
                    onValueChange = { keyName = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
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
                    shapes = ButtonDefaults.shapes(),
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
                    shapes = ButtonDefaults.shapes(),
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

/** Setup categories and the editable draft share one state across window sizes. */
@Composable
internal fun LearningSetupContent(
    page: CustomRemoteWizardUiState,
    onCategory: (ApplianceType) -> Unit,
    onNameChange: (String) -> Unit,
    onBrandChange: (String) -> Unit,
    onPickBrand: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val wide = rememberWideLayout()
    val primaryScroll = rememberScrollState()
    val secondaryScroll = rememberScrollState()
    val textMeasurer = rememberTextMeasurer()
    val categoryLabelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val categoryLabels = ApplianceType.entries.map { it to it.label() }
    val labelWidth = categoryLabels.maxOfOrNull { (_, label) ->
        textMeasurer.measure(label, categoryLabelStyle, softWrap = false).size.width
    } ?: 0
    val minimumCardWidth = with(LocalDensity.current) { labelWidth.toDp() } + 24.dp
    val brandFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val nameError = if (page.errorMessage != null && page.remoteName.isBlank()) page.errorMessage.asString() else null


    val types: @Composable () -> Unit = {
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
                            val selected = page.selectedApplianceType == category
                            val interactionSource = remember { MutableInteractionSource() }
                            val shape = rememberExpressiveShape(interactionSource, RoundedCornerShape(20.dp), RoundedCornerShape(26.dp))
                            Surface(
                                shape = shape,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else ExpressiveSurface.card,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 96.dp)
                                    .clip(shape)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = ripple(),
                                        role = Role.RadioButton
                                    ) {
                                        HapticHelper.performSelection(haptic, context)
                                        onCategory(category)
                                    }
                                    .semantics(mergeDescendants = true) {
                                        role = Role.RadioButton
                                        this.selected = selected
                                    }
                            ) {
                                Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(10.dp)) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.align(Alignment.TopEnd).size(18.dp)
                                        )
                                    }
                                    Column(
                                        Modifier.fillMaxWidth().align(Alignment.Center),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(getCategoryIcon(category.catalogCode), null, Modifier.size(24.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

    }
    val form: @Composable () -> Unit = {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ExpressiveSurface.card,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExpressiveTextField(
                    value = page.remoteName,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth().semantics { nameError?.let { error(it) } },
                    label = { Text(stringResource(R.string.screen_remote_name)) },
                    singleLine = true,
                    isError = nameError != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { brandFocusRequester.requestFocus() })
                )
                ExpressiveTextField(
                    value = page.brand,
                    onValueChange = onBrandChange,
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
                            onPickBrand()
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
            }
        }
        Button(
            onClick = {
                keyboard?.hide()
                HapticHelper.performRemoteClick(haptic, context)
                onStart()
            },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text(stringResource(R.string.screen_start_learning)) }
    }
    if (wide) {
        TwoPaneLayout(modifier.fillMaxSize().imePadding(),
            primary = {
                Column(Modifier.fillMaxSize().verticalScroll(primaryScroll).padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)) { types() }
            },
            secondary = {
                Column(Modifier.fillMaxSize().verticalScroll(secondaryScroll).padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)) { form() }
            })
    } else {
        Column(modifier.fillMaxSize().imePadding().verticalScroll(secondaryScroll).contentWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            types(); form()
        }
    }
}

/** Review keeps metadata and saving beside the complete learned-key list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LearningReviewContent(page: CustomRemoteWizardUiState, onContinue: () -> Unit,
    onSave: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val wide = rememberWideLayout()
    val summary: @Composable () -> Unit = {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ExpressiveSurface.card,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(page.remoteName, style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(page.selectedApplianceType.label(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (page.brand.isNotBlank()) Text(page.brand, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.screen_learned_count, page.learnedCount),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
            }
        }
    }
    val actions: @Composable () -> Unit = {
        page.errorMessage?.let { error ->
            Text(error.asString(), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        OutlinedButton(
            onClick = { HapticHelper.performSelection(haptic, context); onContinue() },
            shapes = ButtonDefaults.shapes(),
            enabled = !page.isSaving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text(stringResource(R.string.screen_continue_learning)) }
        Button(
            onClick = { HapticHelper.performSaveSuccess(haptic, context); onSave() },
            shapes = ButtonDefaults.shapes(),
            enabled = page.learnedCount > 0 && !page.isSaving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text(if (page.isSaving) stringResource(R.string.screen_saving) else stringResource(R.string.screen_save)) }
    }
    val learnedItems = remember(page.keyQueue) { page.keyQueue.filter { it.status == KeyLearnStatus.LEARNED } }
    val learned: LazyListScope.() -> Unit = {
        itemsIndexed(learnedItems, key = { _, key -> key.id }) { index, key ->
            val icon = resolveButtonIcon(key.key, key.iconName) ?: Icons.Rounded.Tune
            ExpressiveSegmentedSurface(index = index, count = learnedItems.size) {
                ListItem(
                    headlineContent = { Text(key.displayLabel()) },
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    trailingContent = { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
    if (wide) {
        TwoPaneLayout(modifier.fillMaxSize(), primaryFraction = .4f,
            primary = {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) { summary(); actions() }
            },
            secondary = {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(ExpressiveShapeTokens.SegmentedGap), content = learned)
            })
    } else {
        LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(ExpressiveShapeTokens.SegmentedGap)) {
            item { Box(Modifier.padding(bottom = 12.dp)) { summary() } }
            learned()
            item { Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { actions() } }
        }
    }
}

/** Default queue labels are translated for display; custom key names remain untouched. */
@Composable
internal fun WizardKeyItem.displayLabel(): String {
    LocalConfiguration.current
    val context = LocalContext.current
    if (builtinLabel != null && key != null) return context.getString(defaultLearningLabelRes(key, acAction))
    return builtinLabel?.let { org.qinyan.rimoat.i18n.localizedLabel(it, context) } ?: label
}

@Composable
internal fun LeaveLearningDraftDialog(
    onConfirmDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    MotionAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.screen_leave_learning_title),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.screen_leave_learning_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    HapticHelper.performRemoteClick(haptic, context)
                    onConfirmDiscard()
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.screen_discard_and_switch))
            }
        },
        dismissButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    HapticHelper.performSelection(haptic, context)
                    onDismiss()
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.screen_continue_learning))
            }
        }
    )
}
