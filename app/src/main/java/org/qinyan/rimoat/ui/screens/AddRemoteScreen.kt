@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import androidx.compose.material3.ButtonDefaults
import org.qinyan.rimoat.ui.components.rememberWideLayout
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppError
import org.qinyan.rimoat.i18n.ErrorAction
import org.qinyan.rimoat.ui.components.openStorageSettings
import org.qinyan.rimoat.ui.components.showAppError
import androidx.activity.compose.BackHandler
import org.qinyan.rimoat.ui.motion.MotionContent
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import org.qinyan.rimoat.ui.motion.backPreview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import org.qinyan.rimoat.ui.components.ActionIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.ui.HapticHelper
import org.qinyan.rimoat.ui.components.HardwareStatusButton
import org.qinyan.rimoat.ui.components.TransmitFeedbackBanner
import org.qinyan.rimoat.ui.viewmodel.AddRemoteStep
import org.qinyan.rimoat.ui.viewmodel.AddRemoteViewModel
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.qinyan.rimoat.ui.components.ExpressiveCompactTopBar
import org.qinyan.rimoat.ui.components.ExpressivePageTopBar
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
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
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val showErrorInSnackbar = uiState.step != AddRemoteStep.CONFIRM
    org.qinyan.rimoat.ui.CancelOperationsOnStop(viewModel::onScreenStopped)
    androidx.compose.runtime.LaunchedEffect(screenActive, showErrorInSnackbar, uiState.error, uiState.errorAction) {
        if (screenActive && showErrorInSnackbar) uiState.error?.let { error ->
            val result = errorSnackbar.showAppError(context, AppError.Resource(error, uiState.errorAction))
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                when (uiState.errorAction) {
                    is ErrorAction.Retry, is ErrorAction.Reload -> viewModel.retryLastAction()
                    is ErrorAction.OpenStorageSettings -> openStorageSettings(context)
                    else -> Unit
                }
            }
            viewModel.dismissError(error)
        }
    }
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
                modifier = Modifier.heightIn(min = 48.dp),
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.pairing_restart)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(

                onClick = { showRestart = false },
                modifier = Modifier.heightIn(min = 48.dp),
                shapes = ButtonDefaults.shapes()
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

        val scrollBehavior = if (!wide && stepState.step != AddRemoteStep.WIZARD) {
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        } else null
        ProvideExpressiveCanvas {
        Scaffold(
            modifier = Modifier.let { if (scrollBehavior != null) it.nestedScroll(scrollBehavior.nestedScrollConnection) else it },
            containerColor = ExpressiveSurface.canvas,
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
                val navIcon: @Composable () -> Unit = {
                    ActionIconButton(stringResource(R.string.screen_back), {
                        if (!viewModel.handleBack()) {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                }
                val barActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
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
                }
                if (stepState.step == AddRemoteStep.WIZARD) {
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
                                viewModel.saveRemote(stepState.displayRemoteName(context), onRemoteCreated)
                            }
                        )
                    }
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
