package org.qinyan.rimoat.ui.screens

import androidx.compose.foundation.layout.fillMaxHeight
import org.qinyan.rimoat.ui.components.contentWidth
import org.qinyan.rimoat.ui.components.TwoPaneLayout
import org.qinyan.rimoat.ui.components.rememberWideLayout
import org.qinyan.rimoat.R

import org.qinyan.rimoat.i18n.asString

import org.qinyan.rimoat.ui.theme.Spacing
import androidx.activity.compose.BackHandler
import org.qinyan.rimoat.ui.motion.MotionContent
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import org.qinyan.rimoat.ui.motion.backPreview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import org.qinyan.rimoat.ui.components.ActionIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.db.IrCategory
import org.qinyan.rimoat.ui.HapticHelper
import org.qinyan.rimoat.ui.components.HardwareStatusButton
import org.qinyan.rimoat.ui.components.TransmitFeedbackBanner
import org.qinyan.rimoat.ui.viewmodel.AddRemoteStep
import org.qinyan.rimoat.ui.viewmodel.AddRemoteUiState
import org.qinyan.rimoat.ui.viewmodel.AddRemoteViewModel
import org.qinyan.rimoat.ui.motion.MotionAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRemoteScreen(
    viewModel: AddRemoteViewModel,
    onBack: () -> Unit,
    onRemoteCreated: (RemoteProfile) -> Unit,
    onCustomWizardClick: () -> Unit = {}
) {
    val liveUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val transmitterType by viewModel.transmitterManager.transmitterType.collectAsStateWithLifecycle()
    val isHardwareReady by viewModel.transmitterManager.isReady.collectAsStateWithLifecycle()
    val isForceMock by viewModel.transmitterManager.forceMockMode.collectAsStateWithLifecycle()
    val lastTransmitLog by viewModel.lastTransmitLog.collectAsStateWithLifecycle()

    val screenActive = LocalMotionActive.current
    var settledUiState by remember { mutableStateOf(liveUiState) }
    androidx.compose.runtime.SideEffect {
        if (screenActive || (settledUiState.step == liveUiState.step && settledUiState.isLoading && !liveUiState.isLoading)) {
            settledUiState = liveUiState
        }
    }
    val uiState = if (screenActive) liveUiState else settledUiState

    org.qinyan.rimoat.ui.components.StandaloneScreenRotationScope(
        isTargetRotationScreen = uiState.step == AddRemoteStep.WIZARD,
        targetScreenKey = if (uiState.step == AddRemoteStep.WIZARD) "add_matching:${uiState.selectedBrand?.brandIdx ?: 0}" else "",
        transmitterManager = viewModel.transmitterManager
    ) {

    val errorSnackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val displayedError = uiState.error?.asString()
    org.qinyan.rimoat.ui.CancelOperationsOnStop(viewModel::onScreenStopped)
    androidx.compose.runtime.LaunchedEffect(screenActive, uiState.error, displayedError) {
        if (screenActive) uiState.error?.let {
            errorSnackbar.showSnackbar(checkNotNull(displayedError), duration = androidx.compose.material3.SnackbarDuration.Long)
            viewModel.dismissError(it)
        }
    }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var showTransmitterSheet by rememberSaveable { mutableStateOf(false) }

    var showRestart by rememberSaveable { mutableStateOf(false) }
    if (showRestart && screenActive) MotionAlertDialog(
        onDismissRequest = { showRestart = false },
        title = { Text(stringResource(R.string.pairing_restart)) },
        text = { Text(stringResource(R.string.pairing_restart_message)) },
        confirmButton = {
            androidx.compose.material3.TextButton(

                onClick = {
                    HapticHelper.performRemoteClick(haptic, context)
                    showRestart = false
                    viewModel.restartMatching()
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.pairing_restart)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(

                onClick = { showRestart = false },
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.screen_cancel)) }
        }
    )
    val wide = rememberWideLayout()
    val blocked = uiState.isSaving || uiState.isSendingTest
    // Keep the brand position outside the animated step so returning from matching restores it.
    val brandListState = rememberSaveable(
        uiState.sessionRevision,
        uiState.selectedCategory?.id,
        uiState.brandSearchQuery,
        saver = LazyListState.Saver
    ) { LazyListState() }
    BackHandler(enabled = screenActive && blocked) { }
    MotionContent(
        targetState = uiState,
        contentKey = { it.step.name },
        isBack = { from, to -> to.step.ordinal < from.step.ordinal },
        backTarget = if (blocked) null else uiState.backPreview(),
        onBack = { viewModel.handleBack() }
    ) { stepState ->
        val title = when (stepState.step) {
            AddRemoteStep.CATEGORY -> stringResource(R.string.screen_choose_category)
            AddRemoteStep.BRAND -> stringResource(
                R.string.screen_choose_brand_category,
                stepState.selectedCategory?.displayName(context).orEmpty()
            )
            AddRemoteStep.WIZARD -> stringResource(
                R.string.screen_match_brand,
                stepState.selectedBrand?.displayName(context).orEmpty()
            )
            AddRemoteStep.CONFIRM -> stringResource(R.string.screen_save_remote)
        }

        Scaffold(
            snackbarHost = {
                Column {
                    if (stepState.step == AddRemoteStep.WIZARD) TransmitFeedbackBanner(
                        log = lastTransmitLog?.takeUnless { it.isSuccess }, onDismissed = viewModel::dismissFeedback)
                    org.qinyan.rimoat.ui.components.AppSnackbarHost(errorSnackbar)
                }
            },
            bottomBar = {
                if (stepState.step == AddRemoteStep.WIZARD && !wide) Column(Modifier.navigationBarsPadding()) {
                    CodeMatchingActions(stepState, onMatched = {
                        HapticHelper.performRemoteClick(haptic, context)
                        viewModel.proceedToConfirm()
                    }, onConfirmTest = viewModel::confirmTest)
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.semantics {
                                heading()
                                contentDescription = title
                            }
                        )
                    },
                    navigationIcon = {
                        ActionIconButton(stringResource(R.string.screen_back), onClick = {
                            if (!viewModel.handleBack()) {
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        if (stepState.step == AddRemoteStep.WIZARD) {
                            org.qinyan.rimoat.ui.components.ScreenRotationIconButton()
                            Spacer(Modifier.width(4.dp))
                            ActionIconButton(
                                stringResource(R.string.pairing_restart),
                                onClick = {
                                    HapticHelper.performSelection(haptic, context)
                                    showRestart = true
                                },
                                enabled = !blocked && !stepState.isLoading
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        HardwareStatusButton(
                            transmitterType = transmitterType,
                            isReady = isHardwareReady,
                            forceMock = isForceMock,
                            onClick = { showTransmitterSheet = true },
                            transmitFeedback = lastTransmitLog,
                            onFeedbackFinished = viewModel::dismissFeedback
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (stepState.step) {
                    AddRemoteStep.CATEGORY -> {
                        CategorySelectionView(
                            categories = stepState.categories,
                            isLoading = stepState.isLoading,
                            onCategoryClick = { cat ->
                                HapticHelper.performRemoteClick(haptic, context)
                                viewModel.selectCategory(cat)
                            },
                            onCustomWizardClick = {
                                HapticHelper.performRemoteClick(haptic, context)
                                onCustomWizardClick()
                            }
                        )
                    }
                    AddRemoteStep.BRAND -> {
                        BrandSelectionView(
                            brands = stepState.brands,
                            listState = brandListState,
                            searchQuery = stepState.brandSearchQuery,
                            category = stepState.selectedCategory,
                            isLoading = stepState.isLoading,
                            onSearchChange = { viewModel.searchBrand(it) },
                            onBrandClick = { brand ->
                                HapticHelper.performRemoteClick(haptic, context)
                                viewModel.selectBrand(brand)
                            },
                            precomputedGroupsCn = stepState.brandGroupsCn,
                            precomputedGroupsEn = stepState.brandGroupsEn
                        )
                    }
                    AddRemoteStep.WIZARD -> {
                        CodeMatchingWizardView(
                            state = stepState,
                            onTestKey = { btn ->
                                viewModel.testSignal(btn)
                            },
                            onNextModel = {
                                HapticHelper.performRemoteClick(haptic, context)
                                viewModel.nextModel()
                            },
                            onPrevModel = {
                                HapticHelper.performRemoteClick(haptic, context)
                                viewModel.prevModel()
                            },
                            onConfirmTest = viewModel::confirmTest,
                            showActions = wide,
                            onMatched = {
                                HapticHelper.performRemoteClick(haptic, context)
                                viewModel.proceedToConfirm()
                            }
                        )
                    }
                    AddRemoteStep.CONFIRM -> {
                        ConfirmRemoteView(
                            modifier = Modifier.consumeWindowInsets(padding),
                            state = stepState,
                            onNameChange = { viewModel.updateCustomName(it) },
                            onSave = {
                                HapticHelper.performRemoteClick(haptic, context)
                                viewModel.saveRemote(onRemoteCreated)
                            }
                        )
                    }
                }


            }
        }
    }
    if (showTransmitterSheet && LocalMotionActive.current) {
        org.qinyan.rimoat.ui.components.TransmitterSelectionBottomSheet(
            transmitterManager = viewModel.transmitterManager,
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismissRequest = { showTransmitterSheet = false }
        )
    }
    }
}

// ---------------------------------------------------------------------------
// Step 1: Category Selection Grid
// ---------------------------------------------------------------------------

@Composable
fun CategorySelectionView(
    categories: List<IrCategory>,
    isLoading: Boolean,
    onCategoryClick: (IrCategory) -> Unit,
    onCustomWizardClick: () -> Unit = {}
) {
    if (isLoading && categories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    val categoryGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val categoryLabels = categories.map { it to it.displayName(context) }
    val labelWidth = categoryLabels.maxOfOrNull { (_, label) ->
        textMeasurer.measure(label, labelStyle, softWrap = false).size.width
    } ?: 0
    val minimumCardWidth = with(LocalDensity.current) { labelWidth.toDp() } + 24.dp
    val wide = rememberWideLayout()
    val learnEntry: @Composable () -> Unit = {
                Card(
                    onClick = onCustomWizardClick,

                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.compact)
                        .semantics(mergeDescendants = true) { role = Role.Button }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Sensors,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(Spacing.large))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.screen_learn_physical),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(Spacing.small))

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
    }
    val categoriesContent: @Composable () -> Unit = {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availableWidth = (maxWidth - 32.dp).coerceAtLeast(0.dp)
            val columns = ((availableWidth + Spacing.medium) / (minimumCardWidth + Spacing.medium)).toInt().coerceIn(1, 3)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = categoryGridState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                modifier = Modifier.fillMaxSize()
            ) {
                if (!wide) item(key = "learn_physical_hero", span = { GridItemSpan(maxLineSpan) }) { learnEntry() }
                items(categoryLabels, key = { (cat, _) -> cat.id }) { (cat, label) ->
                    CategoryGridCard(category = cat, label = label, onClick = { onCategoryClick(cat) })
                }
            }
        }
    }
    if (wide) {
        TwoPaneLayout(
            modifier = Modifier.fillMaxSize(), primaryFraction = .4f, primaryMaxWidth = 320.dp,
            primary = {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { learnEntry() }
            },
            secondary = categoriesContent
        )
    } else {
        Box(Modifier.contentWidth(720.dp).fillMaxHeight()) { categoriesContent() }
    }
}

@Composable
fun CategoryGridCard(
    category: IrCategory,
    onClick: () -> Unit,
    label: String = category.displayName(LocalContext.current)
) {
    val icon = getCategoryIcon(category.code)

    Card(
        onClick = onClick,

        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .semantics(mergeDescendants = true) { role = Role.Button }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth().heightIn(min = 88.dp)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.small))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun getCategoryIcon(code: String): ImageVector {
    return when (code.lowercase()) {
        "tv" -> Icons.Rounded.Tv
        "air" -> Icons.Rounded.AcUnit
        "fan" -> Icons.Rounded.Air
        "pjt" -> Icons.Rounded.Videocam
        "stb" -> Icons.Rounded.SettingsInputHdmi
        "dvd" -> Icons.Rounded.Album
        "amp" -> Icons.Rounded.Speaker
        "ipt" -> Icons.Rounded.Router
        "box" -> Icons.Rounded.DeveloperBoard
        "light" -> Icons.Rounded.Lightbulb
        "cam" -> Icons.Rounded.CameraAlt
        else -> Icons.Rounded.Tv
    }
}

// ---------------------------------------------------------------------------
// Step 3: Code Matching Wizard
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Step 4: Name Customization & Save Confirmation
// ---------------------------------------------------------------------------

@Composable
fun ConfirmRemoteView(
    state: AddRemoteUiState,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val model = state.currentRemote
    val icon = getCategoryIcon(state.selectedCategory?.code ?: "tv")
    val canSave = state.customRemoteName.isNotBlank() && !state.isLoading && !state.isSaving

    val wide = rememberWideLayout()
    val primaryScroll = rememberScrollState()
    val secondaryScroll = rememberScrollState()
    val summary: @Composable () -> Unit = {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.page))

        Text(
            text = listOfNotNull(
                model?.brandDisplayName(context),
                state.selectedCategory?.displayName(context),
                stringResource(R.string.screen_code_number, state.currentRemoteIndex + 1)
            ).filter { it.isNotBlank() }.joinToString("  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.section))


    }
    val form: @Composable () -> Unit = {
        OutlinedTextField(
            value = state.customRemoteName,
            onValueChange = { if (it.length <= 100) onNameChange(it) },
            enabled = !state.isSaving,
            label = { Text(stringResource(R.string.screen_remote_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboard?.hide()
                if (canSave) onSave()
            }),
            modifier = Modifier.fillMaxWidth()
        )

        state.error?.let {
            Text(it.asString(), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))

        Button(

            onClick = onSave,
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text(
                    text = stringResource(R.string.screen_saving),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(Spacing.small))
                Text(
                    text = stringResource(R.string.screen_save),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    if (wide) {
        TwoPaneLayout(
            modifier = modifier.fillMaxSize().imePadding(), primaryFraction = .4f,
            primary = {
                Column(Modifier.fillMaxSize().verticalScroll(primaryScroll).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) { summary() }
            },
            secondary = {
                Column(Modifier.fillMaxSize().verticalScroll(secondaryScroll).padding(8.dp)) { form() }
            }
        )
    } else {
        Column(modifier.fillMaxSize().imePadding().verticalScroll(secondaryScroll).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) { summary(); form() }
    }
}
