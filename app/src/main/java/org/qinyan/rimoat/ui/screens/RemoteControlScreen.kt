@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.ui.components.isPowerKey

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import org.qinyan.rimoat.i18n.labelRes
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.i18n.localizedButtonLabel
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppError
import org.qinyan.rimoat.i18n.ErrorAction
import org.qinyan.rimoat.i18n.asString
import org.qinyan.rimoat.ui.components.openStorageSettings
import org.qinyan.rimoat.ui.components.showAppError
import org.qinyan.rimoat.ui.components.withPanelPresentation
import org.qinyan.rimoat.ui.components.panelAction
import org.qinyan.rimoat.ui.components.acButtonForAction
import org.qinyan.rimoat.ui.components.acRawExtensionButtons
import org.qinyan.rimoat.ui.components.hasAcControlPanel
import org.qinyan.rimoat.i18n.localizedAcRawButtonLabel
import org.qinyan.rimoat.ui.components.panelKey
import org.qinyan.rimoat.ui.components.remoteButtonNumber
import org.qinyan.rimoat.ui.components.RemoteControlPages
import org.qinyan.rimoat.ui.components.remoteExtraButtons
import org.qinyan.rimoat.ui.components.tvStandardButtons
import org.qinyan.rimoat.ui.components.RemoteFunctionGroups
import org.qinyan.rimoat.ui.components.RemoteFunctionGrid
import org.qinyan.rimoat.ui.components.ActionTooltip
import org.qinyan.rimoat.ui.components.CenteredContent
import org.qinyan.rimoat.ui.components.LocalCompactRemoteButtons
import org.qinyan.rimoat.ui.components.rememberWideLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import org.qinyan.rimoat.ui.theme.Spacing
import org.qinyan.rimoat.ui.motion.ExpandSection
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import org.qinyan.rimoat.ui.motion.MotionContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import org.qinyan.rimoat.data.AcUnavailableReason
import org.qinyan.rimoat.ui.components.compactExtensionsFit
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import org.qinyan.rimoat.ui.components.ActionIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import org.qinyan.rimoat.ui.learning.SingleButtonLearningDialog
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import org.qinyan.rimoat.ui.components.ExpressiveDropdownMenu
import org.qinyan.rimoat.ui.components.ExpressiveSelectableMenuItem
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.ui.motion.MotionAlertDialog
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.ui.components.DpadController
import org.qinyan.rimoat.ui.components.HardwareStatusButton
import org.qinyan.rimoat.ui.components.TransmitterSelectionBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import org.qinyan.rimoat.ui.components.RemoteButtonVariant
import org.qinyan.rimoat.ui.components.controlAppearance
import org.qinyan.rimoat.ui.components.RemoteButtonView
import org.qinyan.rimoat.ui.components.TransmitFeedbackBanner
import org.qinyan.rimoat.ui.theme.AccentCool
import org.qinyan.rimoat.ui.theme.AccentHeat
import org.qinyan.rimoat.ui.theme.AccentPower
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Check
import org.qinyan.rimoat.ui.components.ExpressiveCompactTopBar
import org.qinyan.rimoat.ui.components.ExpressiveSingleChoiceRow
import org.qinyan.rimoat.ui.components.ExpressiveShapeTokens
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
import org.qinyan.rimoat.ui.viewmodel.AcControlState
import org.qinyan.rimoat.ui.viewmodel.AcMode
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel

@Composable
fun RemoteControlScreen(
    viewModel: RemoteControlViewModel,
    onBack: () -> Unit
) {
    org.qinyan.rimoat.ui.components.StandaloneScreenRotationScope(
        isTargetRotationScreen = true,
        targetScreenKey = "control:${viewModel.remoteId}",
        transmitterManager = viewModel.transmitterManager,
        learnerManager = viewModel.learnerManager
    ) {
        val remote by viewModel.remoteProfile.collectAsStateWithLifecycle()
        val available by viewModel.availableAcActions.collectAsStateWithLifecycle()
        val panelSaveError by viewModel.panelSaveError.collectAsStateWithLifecycle()
        var showEditor by androidx.compose.runtime.saveable.rememberSaveable(viewModel.remoteId) { mutableStateOf(false) }
        var visitRecorded by androidx.compose.runtime.saveable.rememberSaveable(viewModel.remoteId) { mutableStateOf(false) }
        val editing = showEditor && remote != null
        MotionContent(
            targetState = editing,
            contentKey = { if (it) "editor" else "controls" },
            isBack = { _, to -> !to },
            backTarget = if (editing) false else null,
            onBack = { showEditor = false },
            discardSavedStateOnExit = { it }
        ) { editor ->
            if (editor) {
                remote?.let { profile ->
                    RemotePanelEditor(
                        profile = profile,
                        available = available,
                        onSave = viewModel::savePanelLayout,
                        onBack = { showEditor = false },
                        saveError = panelSaveError,
                        saveAppError = viewModel.panelSaveAppError.collectAsStateWithLifecycle().value,
                        onRetrySave = viewModel::retrySavePanelLayout,
                        onDismissSaveError = viewModel::clearPanelSaveError
                    )
                }
            } else {
                RemoteControlContent(
                    viewModel = viewModel,
                    onBack = onBack,
                    onEdit = {
                        viewModel.setLearningMode(false)
                        showEditor = true
                    },
                    visitRecorded = visitRecorded,
                    onVisitRecorded = { visitRecorded = true }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RemoteControlContent(
    viewModel: RemoteControlViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    visitRecorded: Boolean,
    onVisitRecorded: () -> Unit
) {
    val wide = rememberWideLayout()
    var selectedControlPage by androidx.compose.runtime.saveable.rememberSaveable(viewModel.remoteId) { mutableStateOf(0) }
    var showNumberPad by androidx.compose.runtime.saveable.rememberSaveable(viewModel.remoteId) { mutableStateOf(false) }
    val primaryScroll = rememberScrollState()
    val secondaryScroll = rememberScrollState()
    val regularListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val moreListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val remote by viewModel.remoteProfile.collectAsStateWithLifecycle()
    val isRepositoryLoaded by viewModel.isRepositoryLoaded.collectAsStateWithLifecycle()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val transmitterType by viewModel.transmitterType.collectAsStateWithLifecycle()
    val isHardwareReady by viewModel.isHardwareReady.collectAsStateWithLifecycle()
    val isForceMock by viewModel.isForceMock.collectAsStateWithLifecycle()
    val acState by viewModel.acState.collectAsStateWithLifecycle()
    val availableAcActions by viewModel.availableAcActions.collectAsStateWithLifecycle()
    val unavailableReasons by viewModel.acUnavailableReasons.collectAsStateWithLifecycle()
    val isDemonstrating by viewModel.isDemonstrating.collectAsStateWithLifecycle()
    val isTransmitting by viewModel.isTransmitting.collectAsStateWithLifecycle()
    val screenActive = LocalMotionActive.current
    val hostActivity = androidx.compose.ui.platform.LocalContext.current.findActivity()
    androidx.compose.runtime.LaunchedEffect(screenActive, viewModel) {
        if (screenActive && !visitRecorded) {
            onVisitRecorded()
            viewModel.onVisible()
        }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(viewModel) {
        onDispose {
            if (hostActivity?.isChangingConfigurations != true) {
                viewModel.setLearningMode(false)
                if (viewModel.learningDialogButton.value != null) viewModel.dismissLearningDialog()
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(viewModel, lifecycleOwner, screenActive) {
        fun updateActivity() {
            if (screenActive && lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
                viewModel.onScreenStarted()
            else viewModel.onScreenStopped()
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ -> updateActivity() }
        lifecycleOwner.lifecycle.addObserver(observer)
        updateActivity()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); viewModel.onScreenStopped() }
    }
    val isLearningMode by viewModel.isLearningMode.collectAsStateWithLifecycle()
    val learningDialogButton by viewModel.learningDialogButton.collectAsStateWithLifecycle()
    val capturedLearningSignal by viewModel.capturedLearningSignal.collectAsStateWithLifecycle()
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()
    val operationAppError by viewModel.operationAppError.collectAsStateWithLifecycle()
    val panelSaveError by viewModel.panelSaveError.collectAsStateWithLifecycle()
    val panelSaveAppError by viewModel.panelSaveAppError.collectAsStateWithLifecycle()
    val learningError by viewModel.learningError.collectAsStateWithLifecycle()
    val isSavingButton by viewModel.isSavingButton.collectAsStateWithLifecycle()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val feedbackScope = rememberCoroutineScope()
    var showTransmitterSheet by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var unavailableDetail by remember { mutableStateOf<UnavailableControl?>(null) }
    val explainUnavailable: (RemoteButton, AcUnavailableReason) -> Unit = { button, reason ->
        feedbackScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.control_unavailable_tapped, localizedButtonLabel(button)),
                actionLabel = context.getString(R.string.control_show_reason),
                withDismissAction = true,
                // Keep the explicit Reason/Close actions available while TalkBack traverses the panel.
                duration = androidx.compose.material3.SnackbarDuration.Indefinite)
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed)
                unavailableDetail = UnavailableControl(button, reason)
        }
    }
    if (screenActive) unavailableDetail?.let { detail ->
        val missingSignal = detail.reason == AcUnavailableReason.MISSING_SIGNAL
        val temperatureLimit = detail.reason == AcUnavailableReason.TEMPERATURE_MINIMUM ||
            detail.reason == AcUnavailableReason.TEMPERATURE_MAXIMUM
        MotionAlertDialog(onDismissRequest = { unavailableDetail = null },
            title = { Text(localizedButtonLabel(detail.button)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (missingSignal) {
                    Text(stringResource(R.string.control_reason_missing_signal_details))
                } else if (temperatureLimit) {
                    Text(stringResource(detail.reason.messageResource()))
                } else {
                    Text(stringResource(detail.reason.messageResource()))
                    Text(stringResource(R.string.control_learning_help))
                }
            } },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (!temperatureLimit) {
                        TextButton(onClick = {
                            unavailableDetail = null
                            viewModel.setLearningMode(true)
                            viewModel.startLearningButton(detail.button)
                        }, modifier = Modifier.weight(1f, fill = false), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text(stringResource(R.string.control_enter_learning), textAlign = TextAlign.Center)
                        }
                    }
                    TextButton(onClick = { unavailableDetail = null },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(stringResource(R.string.screen_close))
                    }
                }
            })
    }
    val activeOperationError = operationAppError ?: operationError?.let { AppError.Resource(it) }
    androidx.compose.runtime.LaunchedEffect(screenActive, activeOperationError, learningDialogButton) {
        if (screenActive && learningDialogButton == null) activeOperationError?.let { appError ->
            val result = snackbarHostState.showAppError(context, appError)
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                when (val action = appError.action) {
                    is ErrorAction.Learn -> viewModel.learnButtonById(action.buttonId)
                    is ErrorAction.Reload -> viewModel.reloadAcCapabilities()
                    is ErrorAction.Retry -> viewModel.retrySaveAcSettings()
                    is ErrorAction.OpenTransmitter -> showTransmitterSheet = true
                    is ErrorAction.OpenStorageSettings -> openStorageSettings(context)
                    null -> Unit
                }
            }
            viewModel.clearOperationAppError(operationAppError)
            viewModel.clearOperationError(operationError)
        }
    }
    val activePanelSaveError = panelSaveAppError ?: panelSaveError?.let { AppError.Resource(it) }
    androidx.compose.runtime.LaunchedEffect(screenActive, activePanelSaveError, learningDialogButton) {
        if (screenActive && learningDialogButton == null) activePanelSaveError?.let { appError ->
            val result = snackbarHostState.showAppError(context, appError)
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                when (appError.action) {
                    is ErrorAction.Retry -> viewModel.retrySavePanelLayout()
                    is ErrorAction.OpenStorageSettings -> openStorageSettings(context)
                    else -> Unit
                }
            }
            viewModel.clearPanelSaveError()
        }
    }

    ProvideExpressiveCanvas {
    Scaffold(
        containerColor = ExpressiveSurface.canvas,
        snackbarHost = {
            val lastTransmitLog by viewModel.lastTransmitLog.collectAsStateWithLifecycle()
            Column {
                TransmitFeedbackBanner(log = lastTransmitLog?.takeUnless { it.isSuccess }, onDismissed = viewModel::dismissFeedback)
                org.qinyan.rimoat.ui.components.AppSnackbarHost(snackbarHostState)
            }
        },
        topBar = {
            ExpressiveCompactTopBar(
                title = remote?.name ?: stringResource(R.string.screen_remote),
                navigationIcon = {
                    ActionIconButton(stringResource(R.string.screen_back), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.screen_back))
                    }
                },
                actions = {
                    org.qinyan.rimoat.ui.components.ScreenRotationIconButton()
                    Spacer(Modifier.width(4.dp))
                    ActionIconButton(stringResource(R.string.panel_edit_title), onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    val learningLabel = stringResource(
                        if (isLearningMode) R.string.screen_leave_learning else R.string.control_enter_learning
                    )
                    ActionIconButton(learningLabel, onClick = viewModel::toggleLearningMode) {
                        Icon(
                            imageVector = if (isLearningMode) Icons.Rounded.Done else Icons.Rounded.School,
                            contentDescription = null,
                            tint = if (isLearningMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    val transmitFeedback by viewModel.lastTransmitLog.collectAsStateWithLifecycle()
                    HardwareStatusButton(
                        transmitterType = transmitterType,
                        isReady = isHardwareReady,
                        forceMock = isForceMock,
                        onClick = { showTransmitterSheet = true },
                        transmitFeedback = transmitFeedback?.takeIf { it.isSuccess && screenActive },
                        onFeedbackFinished = viewModel::dismissFeedback
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Learning Mode Persistent Indicator Banner
            ExpandSection(visible = isLearningMode) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text(
                            text = stringResource(R.string.control_enter_learning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.toggleLearningMode() }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.screen_exit), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // In-place Single Button Learning Dialog
            if (screenActive) learningDialogButton?.let { buttonToLearn ->
                val learnerMgr = viewModel.learnerManager
                val transmitterMgr = viewModel.transmitterManager
                if (learnerMgr != null && transmitterMgr != null) {
                    SingleButtonLearningDialog(
                        button = buttonToLearn,
                        learnerManager = learnerMgr,
                        transmitterManager = transmitterMgr,
                        isSaving = isSavingButton,
                        saveError = learningError,
                        initialCapturedSignal = capturedLearningSignal?.takeIf { it.first == buttonToLearn.id }?.second,
                        onCaptureChanged = { signal -> viewModel.updateLearningCapture(buttonToLearn.id, signal) },
                        onDismissRequest = { viewModel.dismissLearningDialog() },
                        onSaveLearnedButton = { btn, signal ->
                            viewModel.saveLearnedButton(btn, signal)
                        }
                    )
                }
            }

            val presentationKey = remember(remote) {
                remote?.copy(updatedAt = 0L, lastUsedAt = 0L, lastSentAt = 0L)
            }
            val profile = remember(presentationKey, isLearningMode) {
                remote?.let { if (isLearningMode) it else it.withPanelPresentation() }
            }
            if (profile == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRepositoryLoaded) {
                        Text(stringResource(R.string.screen_remote_missing), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                androidx.compose.runtime.key(profile.id) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                val acPanel = profile.hasAcControlPanel()
                val dynamicAc = acPanel && !profile.useStaticAcButtons && profile.acProtocolId != null
                val mainAc = setOf(AcAction.POWER_ON, AcAction.POWER_OFF, AcAction.TEMP_UP, AcAction.TEMP_DOWN,
                    AcAction.MODE, AcAction.FAN, AcAction.SWING, AcAction.HORIZONTAL_SWING, AcAction.WIND_DIRECTION)
                val extras = if (acPanel) profile.buttons.filter { it.acAction !in mainAc } else remoteExtraButtons(profile)
                val acMainActions = if (acPanel) acPanelActions(profile, availableAcActions, isLearningMode).filter { it in mainAc } else emptyList()
                val acExtraLabels = if (acPanel) acPanelActions(profile, availableAcActions, isLearningMode)
                    .filter { it !in mainAc }.map { stringResource(it.labelRes) } +
                    acRawExtensionButtons(profile).map { localizedAcRawButtonLabel(it) } else emptyList()
                val acPresetLabels = if (dynamicAc && !isLearningMode) listOf(stringResource(R.string.screen_resend), stringResource(R.string.screen_cool_preset), stringResource(R.string.screen_heat_preset)).filterIndexed { index, _ -> "preset:$index" !in profile.panelLayout.hidden } else emptyList()
                val extraLabels = if (acPanel) acExtraLabels + acPresetLabels else extras.map { localizedButtonLabel(it) }
                val inlineGroups = if (acPanel) {
                    val trailingSlots = (3 - acMainActions.size % 3) % 3
                    listOf(acExtraLabels.drop(trailingSlots), acPresetLabels).filter { it.isNotEmpty() }
                } else {
                    listOf(extraLabels).filter { it.isNotEmpty() }
                }
                val inlineCount = if (acPanel) {
                    if (acExtraLabels.isNotEmpty()) acExtraLabels.size else acPresetLabels.size
                } else {
                    extraLabels.size
                }
                val inline = !isLearningMode && compactExtensionsFit(
                    rowGroups = inlineGroups,
                    width = if (wide) maxWidth / 2 else maxWidth,
                    height = maxHeight,
                    extensionCount = inlineCount
                )
                val hasMore = if (acPanel) extraLabels.isNotEmpty() || dynamicAc && isLearningMode else extras.isNotEmpty()
                if (wide) {
                    LandscapeRemotePanel(
                        profile = profile, acState = acState, isDemonstrating = isDemonstrating,
                        availableAcActions = availableAcActions, unavailableReasons = unavailableReasons,
                        isLearningMode = isLearningMode, isTransmitting = isTransmitting,
                        selectedPage = selectedControlPage, onPageSelected = { selectedControlPage = it },
                        showNumberPad = showNumberPad, onNumberPadChange = { showNumberPad = it },
                        onSendButton = viewModel::sendButton, onLearnButton = viewModel::startLearningButton,
                        onRepeatButton = viewModel::repeatButton, onUnavailable = explainUnavailable,
                        onTogglePower = viewModel::toggleAcPower, onTempUp = viewModel::increaseTemp,
                        onTempDown = viewModel::decreaseTemp, onCycleMode = viewModel::cycleAcMode,
                        onCycleWind = viewModel::cycleAcFanSpeed, onAction = viewModel::performAcAction,
                        onLoadChoices = viewModel::availableAcChoices, onSelectChoice = viewModel::selectAcState,
                        onResend = viewModel::resendAcSettings, onPreset = viewModel::applyAcPreset,
                        transmitterType = transmitterType,
                        singleConfiguredKey = profile.buttons.size == 1,
                        primaryScroll = primaryScroll, secondaryScroll = secondaryScroll
                    )
                } else {
                RemoteControlPages(hasMore = hasMore && !inline, selectedPage = selectedControlPage,
                    onPageSelected = { selectedControlPage = it }) { morePage ->
                CompositionLocalProvider(LocalCompactRemoteButtons provides false) {
                val panelItems: LazyListScope.() -> Unit = {
                    when (profile.category) {
                        DeviceCategory.TV -> {
                            tvRemoteItems(
                                profile = profile,
                                onSendButton = { viewModel.sendButton(it) },
                                onLearnButton = { viewModel.startLearningButton(it) },
                                isLearningMode = isLearningMode,
                                onRepeatButton = { viewModel.repeatButton(it) }, morePage = morePage,
                                inlineExtensions = inline,
                                showNumberPad = showNumberPad, onNumberPadChange = { showNumberPad = it },
                                onUnavailable = { explainUnavailable(it, AcUnavailableReason.MISSING_SIGNAL) }
                            )
                        }
                        DeviceCategory.AC -> {
                            if (!acPanel) {
                                genericRemoteItems(profile, viewModel::sendButton, viewModel::startLearningButton, isLearningMode, viewModel::repeatButton, morePage, inline, { explainUnavailable(it, AcUnavailableReason.MISSING_SIGNAL) })
                            } else remoteItem("ac_controls") {
                            AcRemoteLayout(
                                profile = profile,
                                acState = acState,
                                isDemonstrating = isDemonstrating,
                                onTogglePower = { viewModel.toggleAcPower() },
                                onTempUp = { viewModel.increaseTemp() },
                                onTempDown = { viewModel.decreaseTemp() },
                                onCycleMode = { viewModel.cycleAcMode() },
                                onCycleWind = { viewModel.cycleAcFanSpeed() },
                                availableActions = availableAcActions,
                                unavailableReasons = unavailableReasons,
                                onUnavailable = explainUnavailable,
                                inlineExtensions = inline,
                                onAction = { action, minutes -> viewModel.performAcAction(action, minutes) },
                                onLoadChoices = viewModel::availableAcChoices,
                                onSelectChoice = viewModel::selectAcState,
                                onSendButton = { viewModel.sendButton(it) },
                                onLearnButton = { viewModel.startLearningButton(it) },
                                isLearningMode = isLearningMode,
                                isTransmitting = isTransmitting,
                                onRepeatButton = viewModel::repeatButton, morePage = morePage
                            )
                            if (dynamicAc && (morePage || inline) && !isLearningMode) {
                                Spacer(Modifier.height(Spacing.large))
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    val labels = listOf(stringResource(R.string.screen_resend), stringResource(R.string.screen_cool_preset), stringResource(R.string.screen_heat_preset))
                                    val visiblePresets = (0..2).filter { "preset:$it" !in profile.panelLayout.hidden }.sortedBy { profile.panelLayout.rank("preset:$it") }
                                    val columns = org.qinyan.rimoat.ui.components.extensionColumns(labels, maxWidth)
                                    if (visiblePresets.isNotEmpty()) Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = ExpressiveSurface.card,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            visiblePresets.chunked(columns).forEach { row ->
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    row.forEach { index ->
                                                        RemoteButtonView(labels[index],
                                                            icon = when(index) { 0 -> Icons.Rounded.Refresh; 1 -> Icons.Rounded.AcUnit; else -> Icons.Rounded.WbSunny },
                                                            accessibilityLabel = if (index == 0) stringResource(R.string.screen_resend_settings) else labels[index],
                                                            modifier = Modifier.weight(1f), enabled = !isTransmitting,
                                                            onClick = { when(index) {
                                                                0 -> viewModel.resendAcSettings()
                                                                1 -> viewModel.applyAcPreset(org.qinyan.rimoat.data.AcSettings(temperature = 26))
                                                                else -> viewModel.applyAcPreset(org.qinyan.rimoat.data.AcSettings(temperature = 24, mode = org.qinyan.rimoat.data.AcMode.HEAT))
                                                            } })
                                                    }
                                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        }
                        DeviceCategory.FAN -> {
                            fanRemoteItems(
                                profile = profile,
                                onSendButton = { viewModel.sendButton(it) },
                                onLearnButton = { viewModel.startLearningButton(it) },
                                isLearningMode = isLearningMode,
                                onRepeatButton = viewModel::repeatButton, morePage = morePage,
                                inlineExtensions = inline,
                                onUnavailable = { explainUnavailable(it, AcUnavailableReason.MISSING_SIGNAL) }
                            )
                        }
                        else -> {
                            genericRemoteItems(
                                profile = profile,
                                onSendButton = { viewModel.sendButton(it) },
                                onLearnButton = { viewModel.startLearningButton(it) },
                                isLearningMode = isLearningMode,
                                onRepeatButton = viewModel::repeatButton, morePage = morePage,
                                inlineExtensions = inline,
                                onUnavailable = { explainUnavailable(it, AcUnavailableReason.MISSING_SIGNAL) }
                            )
                        }
                    }

                    remoteItem("footer") {
                        Spacer(modifier = Modifier.height(Spacing.section))
                    }
                }
                CenteredContent(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = if (morePage) moreListState else regularListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) { panelItems() }
                }
                }
                }
                }
                }
                }
            }
        }
    }
    }

    if (screenActive && showTransmitterSheet && viewModel.transmitterManager != null) {
        TransmitterSelectionBottomSheet(
            transmitterManager = viewModel.transmitterManager,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismissRequest = { showTransmitterSheet = false }
        )
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (true) {
        if (context is Activity) return context
        if (context !is ContextWrapper) return null
        context = context.baseContext
    }
}

private fun LazyListScope.remoteItem(key: String, content: @Composable ColumnScope.() -> Unit) {
    item(key = key, contentType = "control_section") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

/**
 * TV Appliance Layout: Power/Source/Mute -> D-Pad -> Back/Home/Menu -> Vol & Ch -> Number Pad
 */
internal fun LazyListScope.tvRemoteItems(
    profile: RemoteProfile,
    onSendButton: (RemoteButton) -> Unit,
    onLearnButton: ((RemoteButton) -> Unit)? = null,
    isLearningMode: Boolean = false,
    onRepeatButton: ((RemoteButton) -> Unit)? = null,
    morePage: Boolean = false,
    inlineExtensions: Boolean = false,
    onUnavailable: ((RemoteButton) -> Unit)? = null,
    showNumberPad: Boolean? = null,
    onNumberPadChange: ((Boolean) -> Unit)? = null
) {
    val standard = tvStandardButtons(profile)
    val powerBtn = standard.power
    val sourceBtn = standard.source
    val muteBtn = standard.mute
    val upBtn = standard.up
    val downBtn = standard.down
    val leftBtn = standard.left
    val rightBtn = standard.right
    val okBtn = standard.ok
    val backBtn = standard.back
    val homeBtn = standard.home
    val menuBtn = standard.menu
    val volUpBtn = standard.volUp
    val volDownBtn = standard.volDown
    val chUpBtn = standard.chUp
    val chDownBtn = standard.chDown
    val dpad = standard.dpad
    if (morePage) {
        remoteItem("tv_functions") { RemoteFunctionGroups(remoteExtraButtons(profile), onSendButton, onLearnButton, isLearningMode, onRepeatButton, preserveOrder = profile.panelLayout.order.isNotEmpty(), onUnavailable = onUnavailable) }
        return
    }

    remoteItem("tv_section_0") {
        val keys = listOfNotNull(powerBtn) + listOfNotNull(sourceBtn,muteBtn).sortedBy { profile.panelLayout.rank(panelKey(it)) }
        if (keys.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ExpressiveSurface.card,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    RemoteFunctionGrid(keys,onSendButton,onLearnButton,isLearningMode,onRepeatButton, onUnavailable = onUnavailable)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    remoteItem("tv_section_1") {
        val visibleDpadKeys = if (isLearningMode) {
            org.qinyan.rimoat.ui.components.DpadDirection.entries.filterTo(mutableSetOf()) { direction ->
                dpad[direction] != null || panelKey(direction.placeholderId) !in profile.panelLayout.hidden
            }
        } else {
            org.qinyan.rimoat.ui.components.DpadDirection.entries.toSet()
        }
        if ((!isLearningMode && dpad.isEmpty()) || visibleDpadKeys.isEmpty()) return@remoteItem
        val dpadPlaceholders = org.qinyan.rimoat.ui.components.DpadDirection.entries.associateWith { direction ->
            dpad[direction] ?: direction.placeholderButton(stringResource(direction.labelRes))
        }
        // 2. D-Pad Controller
        DpadController(
            onUnavailable = onUnavailable?.let { explain -> { direction ->
                explain(dpadPlaceholders.getValue(direction))
            } },
            onRepeat = { direction -> dpad[direction]?.let { onRepeatButton?.invoke(it) } },
            onUp = { upBtn?.let { onSendButton(it) } },
            onDown = { downBtn?.let { onSendButton(it) } },
            onLeft = { leftBtn?.let { onSendButton(it) } },
            onRight = { rightBtn?.let { onSendButton(it) } },
            onOk = { okBtn?.takeIf { it.hasSignal }?.let(onSendButton) },
            isLearningMode = isLearningMode,
            availableKeys = dpad.filterValues { it.hasSignal }.keys,
            visibleKeys = visibleDpadKeys,
            onLearn = onLearnButton?.let { learn -> { direction ->
                learn(dpadPlaceholders.getValue(direction))
            } }
        )

        Spacer(modifier = Modifier.height(10.dp))

    }

    remoteItem("tv_section_2") {
        val keys = listOfNotNull(backBtn,homeBtn,menuBtn).sortedBy { profile.panelLayout.rank(panelKey(it)) }
        if (keys.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ExpressiveSurface.card,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    RemoteFunctionGrid(keys,onSendButton,onLearnButton,isLearningMode,onRepeatButton, onUnavailable = onUnavailable)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    remoteItem("tv_section_3") {
        if (listOfNotNull(volUpBtn, volDownBtn, chUpBtn, chDownBtn).isEmpty()) return@remoteItem
        // 4. Dual Pill Rockers: Volume & Channel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            if (volUpBtn != null || volDownBtn != null) {
                Surface(
                    shape = RoundedCornerShape(36.dp),
                    color = ExpressiveSurface.card,
                    modifier = Modifier.weight(1f).widthIn(max = 148.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (volUpBtn != null) {
                            RemoteButtonView(
                                label = stringResource(R.string.screen_volume_up),
                                iconName = "volume_up",
                                onClick = { onSendButton(volUpBtn) },
                                enabled = isLearningMode || volUpBtn.hasSignal,
                                onUnavailable = onUnavailable?.let { explain -> { explain(volUpBtn) } },
                                onRepeat = onRepeatButton?.let { action -> { action(volUpBtn) } },
                                onLongClick = onLearnButton?.let { { it(volUpBtn) } },
                                isLearningMode = isLearningMode,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (volUpBtn != null && volDownBtn != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        }
                        if (volDownBtn != null) {
                            RemoteButtonView(
                                label = stringResource(R.string.screen_volume_down),
                                iconName = "volume_down",
                                onClick = { onSendButton(volDownBtn) },
                                enabled = isLearningMode || volDownBtn.hasSignal,
                                onUnavailable = onUnavailable?.let { explain -> { explain(volDownBtn) } },
                                onRepeat = onRepeatButton?.let { action -> { action(volDownBtn) } },
                                onLongClick = onLearnButton?.let { { it(volDownBtn) } },
                                isLearningMode = isLearningMode,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (chUpBtn != null || chDownBtn != null) {
                Surface(
                    shape = RoundedCornerShape(36.dp),
                    color = ExpressiveSurface.card,
                    modifier = Modifier.weight(1f).widthIn(max = 148.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (chUpBtn != null) {
                            RemoteButtonView(
                                label = stringResource(R.string.screen_channel_up),
                                iconName = "arrow_drop_up",
                                onClick = { onSendButton(chUpBtn) },
                                enabled = isLearningMode || chUpBtn.hasSignal,
                                onUnavailable = onUnavailable?.let { explain -> { explain(chUpBtn) } },
                                onRepeat = onRepeatButton?.let { action -> { action(chUpBtn) } },
                                onLongClick = onLearnButton?.let { { it(chUpBtn) } },
                                isLearningMode = isLearningMode,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (chUpBtn != null && chDownBtn != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        }
                        if (chDownBtn != null) {
                            RemoteButtonView(
                                label = stringResource(R.string.screen_channel_down),
                                iconName = "arrow_drop_down",
                                onClick = { onSendButton(chDownBtn) },
                                enabled = isLearningMode || chDownBtn.hasSignal,
                                onUnavailable = onUnavailable?.let { explain -> { explain(chDownBtn) } },
                                onRepeat = onRepeatButton?.let { action -> { action(chDownBtn) } },
                                onLongClick = onLearnButton?.let { { it(chDownBtn) } },
                                isLearningMode = isLearningMode,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }

    // Keep every configured key accessible, including brand-specific functions.
    val numericIds = (0..9).mapNotNull { number -> profile.buttons.firstOrNull { remoteButtonNumber(it) == number }?.id }.toSet()
    val extraButtons = if (inlineExtensions) remoteExtraButtons(profile) else emptyList()
    remoteItem("tv_numpad") {
        val hasNumpad = numericIds.isNotEmpty() || isLearningMode
        if (!hasNumpad && extraButtons.isEmpty()) return@remoteItem
        if (hasNumpad) {
        var localNumberPad by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
        val showNumpad = showNumberPad ?: localNumberPad
        // 5. Collapsible Numpad Toggle
        ActionTooltip(if (showNumpad) stringResource(R.string.screen_collapse_numbers) else stringResource(R.string.screen_expand_numbers)) {
        FilledTonalIconToggleButton(
            checked = showNumpad,
            onCheckedChange = { onNumberPadChange?.invoke(it) ?: run { localNumberPad = it } },
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                containerColor = ExpressiveSurface.card,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shapes = IconButtonDefaults.toggleableShapes()
        ) {
            Icon(if (showNumpad) Icons.Rounded.KeyboardHide else Icons.Rounded.Dialpad,
                contentDescription = if (showNumpad) stringResource(R.string.screen_collapse_numbers) else stringResource(R.string.screen_expand_numbers), modifier = Modifier.size(24.dp))
        }

        }
        ExpandSection(visible = showNumpad) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = ExpressiveSurface.card,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val numRows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("", "0", "")
                    )
                    for (row in numRows) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            for (num in row) {
                                if (num.isNotEmpty()) {
                                    val numBtn = profile.buttons.firstOrNull { remoteButtonNumber(it) == num.toInt() }
                                        ?: RemoteButton(id = "num_$num", label = num)
                                    RemoteButtonView(
                                        label = num,
                                        onClick = { if (numBtn.hasSignal) onSendButton(numBtn) },
                                        onRepeat = onRepeatButton?.let { action -> { action(numBtn) } },
                                        enabled = numBtn.hasSignal || isLearningMode,
                                        onUnavailable = onUnavailable?.let { explain -> { explain(numBtn) } },
                                        onLongClick = onLearnButton?.let { { it(numBtn) } },
                                        isLearningMode = isLearningMode,
                                        modifier = Modifier.width(56.dp),
                                        diameter = 56.dp
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(56.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        if (extraButtons.isNotEmpty()) {
            if (hasNumpad) Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ExpressiveSurface.card,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    RemoteFunctionGrid(extraButtons, onSendButton, onLearnButton, isLearningMode, onRepeatButton, onUnavailable = onUnavailable)
                }
            }
        }
    }

}

/**
 * Air Conditioner Layout: Status Screen -> Power/Temp -> Modes & Fan Controls
 */
enum class AcLayoutPart { ALL, TEMPERATURE, FUNCTIONS, EXTENSIONS }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AcRemoteLayout(
    profile: RemoteProfile,
    acState: AcControlState,
    isDemonstrating: Boolean,
    onTogglePower: () -> Unit,
    onTempUp: () -> Unit,
    onTempDown: () -> Unit,
    onCycleMode: () -> Unit,
    onCycleWind: () -> Unit,
    availableActions: Set<AcAction>,
    onAction: (AcAction, Int) -> Unit,
    onSendButton: (RemoteButton) -> Unit,
    onLoadChoices: suspend (AcAction) -> List<org.qinyan.rimoat.data.AcStateChoice> = { emptyList() },
    onSelectChoice: (org.qinyan.rimoat.data.AcStateChoice) -> Unit = {},
    onLearnButton: ((RemoteButton) -> Unit)? = null,
    isLearningMode: Boolean = false,
    isTransmitting: Boolean = false,
    onRepeatButton: ((RemoteButton) -> Unit)? = null,
    morePage: Boolean = false,
    inlineExtensions: Boolean = false,
    sideBySide: Boolean = false,
    presetContent: (@Composable () -> Unit)? = null,
    part: AcLayoutPart = AcLayoutPart.ALL,
    unavailableReasons: Map<AcAction, AcUnavailableReason> = emptyMap(),
    onUnavailable: (RemoteButton, AcUnavailableReason) -> Unit = { _, _ -> }
) {
    val staticAc = profile.useStaticAcButtons || profile.acProtocolId == null
    val context = androidx.compose.ui.platform.LocalContext.current
    fun key(action: AcAction): RemoteButton {
        val bound = acButtonForAction(profile, action)
            ?: if (action == AcAction.POWER_OFF) profile.buttons.firstOrNull {
                it.key == org.qinyan.rimoat.data.ButtonKey.POWER && it.acAction == AcAction.POWER_ON
            } else null
        return bound?.let { if (it.acAction == null) it.copy(acAction = action) else it }
            ?: RemoteButton(id = "ac_function_${action.name}", label = action.name,
                sourceKeyType = action.keyType, acAction = action)
    }
    fun usesRecordedSignal(button: RemoteButton): Boolean = staticAc || button.isLearned
    val tempDownBtn = key(AcAction.TEMP_DOWN)
    val powerAction = if (acState.powerOn) AcAction.POWER_OFF else AcAction.POWER_ON
    val powerBtn = key(powerAction)
    val tempUpBtn = key(AcAction.TEMP_UP)
    val headerReasons = listOf(AcAction.TEMP_DOWN, AcAction.TEMP_UP, powerAction).mapNotNull { action ->
        val button = key(action)
        val reason = when {
            usesRecordedSignal(button) -> AcUnavailableReason.MISSING_SIGNAL.takeUnless { button.hasSignal }
            action !in availableActions -> unavailableReasons[action] ?: AcUnavailableReason.CAPABILITY_UNAVAILABLE
            else -> acState.temperatureBoundaryReason(action)
        }
        reason?.let { action to it }
    }.toMap()
    fun click(button: RemoteButton, send: () -> Unit) {
        when {
            isLearningMode -> onLearnButton?.invoke(button)
            usesRecordedSignal(button) -> onSendButton(button)
            else -> send()
        }
    }
    var showTimer by androidx.compose.runtime.saveable.rememberSaveable(profile.id) { mutableStateOf(false) }
    val timerOptions = remember(profile.acProtocolId, acState.powerOn) {
        profile.acProtocolId?.let { org.qinyan.rimoat.data.AcTimerOptions.forProtocol(it, acState.powerOn) }.orEmpty()
    }

    val modeAppearance = acState.controlAppearance(AcAction.MODE, context)
    val fanAppearance = acState.controlAppearance(AcAction.FAN, context)
    val timerBadge = if (acState.timerMinutes > 0) org.qinyan.rimoat.i18n.localizedTimerDurationLabel(acState.timerMinutes, context) else null
    val temperatureCard: @Composable () -> Unit = {
    if (staticAc) {
        Surface(shape = RoundedCornerShape(28.dp), color = ExpressiveSurface.card,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                val powers = listOf(key(AcAction.POWER_ON)) + listOfNotNull(
                    acButtonForAction(profile, AcAction.POWER_OFF)?.let { key(AcAction.POWER_OFF) }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    powers.forEach { button ->
                        RemoteButtonView(
                            label = stringResource(if (button.key == org.qinyan.rimoat.data.ButtonKey.POWER || powers.size == 1)
                                R.string.control_power else checkNotNull(button.acAction).labelRes),
                            iconName = "power", onClick = { click(button) {} },
                            modifier = Modifier.weight(1f), enabled = !isTransmitting && (isLearningMode || button.hasSignal),
                            currentState = if (!isLearningMode && !button.hasSignal) stringResource(R.string.control_reason_missing_signal) else null,
                            onUnavailable = if (isTransmitting) null else ({ onUnavailable(button, AcUnavailableReason.MISSING_SIGNAL) }),
                            onLongClick = onLearnButton?.let { { it(button) } },
                            isLearningMode = isLearningMode, variant = RemoteButtonVariant.POWER, diameter = 72.dp
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    listOf(tempDownBtn, tempUpBtn).forEach { button ->
                        val action = checkNotNull(button.acAction)
                        RemoteButtonView(stringResource(action.labelRes), icon = acState.controlAppearance(action, context).icon,
                            onClick = { click(button) {} }, modifier = Modifier.weight(1f),
                            enabled = !isTransmitting && (isLearningMode || button.hasSignal),
                            currentState = if (!isLearningMode && !button.hasSignal) stringResource(R.string.control_reason_missing_signal) else null,
                            onUnavailable = if (isTransmitting) null else ({ onUnavailable(button, AcUnavailableReason.MISSING_SIGNAL) }),
                            onLongClick = onLearnButton?.let { { it(button) } },
                            onRepeat = onRepeatButton?.let { { it(button) } }, isLearningMode = isLearningMode)
                    }
                }
            }
        }
    } else {
    org.qinyan.rimoat.ui.components.AcTemperatureCard(
        temperature = acState.temperature,
        powerOn = acState.powerOn,
        isDemonstrating = isDemonstrating,
        canDecrease = !isTransmitting && (isLearningMode || AcAction.TEMP_DOWN !in headerReasons),
        canIncrease = !isTransmitting && (isLearningMode || AcAction.TEMP_UP !in headerReasons),
        canTogglePower = !isTransmitting && (isLearningMode || powerAction !in headerReasons),
        isLearningMode = isLearningMode,
        onDecrease = { click(tempDownBtn, onTempDown) },
        onIncrease = { click(tempUpBtn, onTempUp) },
        onTogglePower = { click(powerBtn, onTogglePower) },
        unavailableDescriptions = headerReasons.mapValues { stringResource(it.value.messageResource()) },
        onUnavailable = if (isTransmitting) null else ({ action -> headerReasons[action]?.let { onUnavailable(key(action), it) } }),
        modeLabel = modeAppearance.label,
        modeIcon = modeAppearance.icon,
        fanLabel = fanAppearance.label,
        fanIcon = fanAppearance.icon,
        timerLabel = timerBadge,
        statusSummary = stringResource(R.string.control_ac_status,
            stringResource(if (isDemonstrating) R.string.control_simulation_setting else R.string.control_target_temp),
            stringResource(if (acState.powerOn) R.string.control_enabled else R.string.control_disabled), acState.temperature,
            stringResource(acState.mode.labelRes), stringResource(acState.fanSpeed.labelRes))
    )
    }
    }
    val actions = acPanelActions(profile, availableActions, isLearningMode)
    val actionButtons = actions.associateWith { key(it) }
    val renderAction: @Composable androidx.compose.foundation.layout.RowScope.(AcAction) -> Unit = { action ->
        val button = actionButtons.getValue(action)
                val appearance = acState.controlAppearance(action, context)
                val recorded = usesRecordedSignal(button)
                val available = if (recorded) button.hasSignal else
                    action in availableActions && (action != AcAction.TIMER || timerOptions.isNotEmpty())
                val scope = rememberCoroutineScope()
                var showChoices by remember(action) { mutableStateOf(false) }
                var choices by remember(action) { mutableStateOf(emptyList<org.qinyan.rimoat.data.AcStateChoice>()) }
                val canSelect = !isTransmitting && !recorded && !isLearningMode && available && org.qinyan.rimoat.data.AcStateChoice.forAction(action).isNotEmpty()
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                RemoteButtonView(
                    label = if (recorded || isLearningMode || !available) stringResource(action.labelRes) else appearance.label,
                    icon = appearance.icon,
                    accessibilityLabel = stringResource(action.labelRes),
                    currentState = when {
                        isLearningMode || recorded && available -> null
                        recorded -> stringResource(R.string.control_reason_missing_signal)
                        !available -> stringResource((unavailableReasons[action] ?: AcUnavailableReason.CAPABILITY_UNAVAILABLE).messageResource())
                        else -> appearance.state?.let { stringResource(R.string.screen_setting_state, if (isDemonstrating) stringResource(R.string.screen_simulated_setting) else stringResource(R.string.screen_setting), it) }
                    },
                    active = appearance.active.takeUnless { recorded },
                    isToggle = !recorded && appearance.active != null && action != AcAction.TIMER,
                    onClick = { click(button) {
                        when (action) {
                            AcAction.MODE -> onCycleMode()
                            AcAction.FAN -> onCycleWind()
                            AcAction.TIMER -> {
                                showTimer = true
                            }
                            else -> onAction(action, 0)
                        }
                    } },
                    onLongClick = onLearnButton?.let { learn -> { learn(button) } },
                    onSelectState = if (canSelect) ({ scope.launch {
                        choices = onLoadChoices(action)
                        showChoices = choices.isNotEmpty()
                    } }) else null,
                    isLearningMode = isLearningMode,
                    enabled = !isTransmitting && (isLearningMode || available),
                    showSelectionEntry = !recorded && org.qinyan.rimoat.data.AcStateChoice.forAction(action).isNotEmpty(),
                    onUnavailable = if (!isTransmitting && !available && !isLearningMode) ({
                        onUnavailable(button, if (recorded) AcUnavailableReason.MISSING_SIGNAL else
                            unavailableReasons[action] ?: AcUnavailableReason.CAPABILITY_UNAVAILABLE)
                    }) else null
                )
                ExpressiveDropdownMenu(
                    expanded = showChoices && !isLearningMode && LocalMotionActive.current,
                    onDismissRequest = { showChoices = false },
                    selectable = true
                ) {
                    choices.forEachIndexed { index, choice ->
                        val isSelected = choice.isSelected(acState)
                        ExpressiveSelectableMenuItem(
                            selected = isSelected,
                            onClick = { showChoices = false; onSelectChoice(choice) },
                            text = { Text(stringResource(choice.labelRes)) },
                            index = index,
                            count = choices.size
                        )
                    }
                }
                }
    }
    val mainActions = actions.filter { it in setOf(AcAction.MODE, AcAction.FAN, AcAction.SWING, AcAction.HORIZONTAL_SWING, AcAction.WIND_DIRECTION) }
    val extensionGroups = listOf(actions.filter { it !in mainActions }.map { actionButtons.getValue(it) }, acRawExtensionButtons(profile))
    val renderExtensionButton: @Composable androidx.compose.foundation.layout.RowScope.(RemoteButton) -> Unit = { button ->
        val action = button.acAction
        if (action != null && action in actionButtons) renderAction(action)
        else RemoteButtonView(localizedAcRawButtonLabel(button), button.iconName, { onSendButton(button) },
            modifier = Modifier.weight(1f), enabled = !isTransmitting && (isLearningMode || button.hasSignal),
            onUnavailable = if (isTransmitting) null else ({ onUnavailable(button, AcUnavailableReason.MISSING_SIGNAL) }),
            onRepeat = onRepeatButton?.let { { it(button) } },
            onLongClick = onLearnButton?.let { { it(button) } }, isLearningMode = isLearningMode,
            buttonId = button.id, builtinLabel = button.builtinLabel, buttonKey = button.key)
    }
    if (part == AcLayoutPart.TEMPERATURE) {
        temperatureCard()
        return
    }
    if (!morePage && part != AcLayoutPart.EXTENSIONS) {
        val displayButtons = mainActions.map { actionButtons.getValue(it) } +
            if (inlineExtensions) extensionGroups.flatten() else emptyList()
        val buttonRows: @Composable () -> Unit = {
            if (displayButtons.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = ExpressiveSurface.card,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
                        val columns = org.qinyan.rimoat.ui.components.extensionColumns(displayButtons.map {
                            it.acAction?.let { action -> stringResource(action.labelRes) } ?: localizedAcRawButtonLabel(it)
                        }, maxWidth)
                            .coerceAtMost(displayButtons.size.coerceAtLeast(1))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            displayButtons.chunked(columns).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { renderExtensionButton(it) }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (sideBySide) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(0.46f), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                    temperatureCard()
                    presetContent?.invoke()
                }
                Column(Modifier.weight(0.54f)) { buttonRows() }
            }
        } else {
            if (part == AcLayoutPart.ALL) {
                temperatureCard()
                Spacer(Modifier.height(Spacing.page))
            }
            buttonRows()
        }
    }
    if (showTimer && timerOptions.isNotEmpty()) {
        org.qinyan.rimoat.ui.components.AcTimerBottomSheet(timerOptions, acState.timerMinutes, acState.powerOn,
            onDismiss = { showTimer = false }, onSend = { minutes ->
                onAction(AcAction.TIMER, minutes)
            })
    }

    if (morePage || part == AcLayoutPart.EXTENSIONS) {
        extensionGroups.filter { it.isNotEmpty() }.forEachIndexed { index, extendedButtons ->
            if (index > 0) Spacer(Modifier.height(24.dp))
            RemoteFunctionGroups(extendedButtons, onSendButton, onLearnButton, isLearningMode, onRepeatButton,
                preserveOrder = true, buttonContent = renderExtensionButton,
                buttonLabel = { it.acAction?.let { action -> context.getString(action.labelRes) } ?: localizedAcRawButtonLabel(it, context) })
        }
    }

}

/**
 * Fan Appliance Layout: Power -> Speed -> Oscillation -> Timer -> Mode
 */
internal fun LazyListScope.fanRemoteItems(
    profile: RemoteProfile,
    onSendButton: (RemoteButton) -> Unit,
    onLearnButton: ((RemoteButton) -> Unit)? = null,
    isLearningMode: Boolean = false,
    onRepeatButton: ((RemoteButton) -> Unit)? = null,
    morePage: Boolean = false,
    inlineExtensions: Boolean = false,
    onUnavailable: ((RemoteButton) -> Unit)? = null
) {
    if (morePage) {
        remoteItem("fan_functions") { RemoteFunctionGroups(remoteExtraButtons(profile), onSendButton, onLearnButton, isLearningMode, onRepeatButton, preserveOrder = profile.panelLayout.order.isNotEmpty(), onUnavailable = onUnavailable) }
        return
    }

    val powerBtn = profile.buttons.firstOrNull { it.isPowerKey() }
    val otherButtons = profile.buttons.filter { it != powerBtn }

    remoteItem("fan_power") {
        if (powerBtn != null) {
            RemoteButtonView(
                label = localizedButtonLabel(powerBtn),
                iconName = "power",
                onClick = { onSendButton(powerBtn) },
                enabled = isLearningMode || powerBtn.hasSignal,
                onUnavailable = onUnavailable?.let { explain -> { explain(powerBtn) } },
                    onRepeat = onRepeatButton?.let { action -> { action(powerBtn) } },
                onLongClick = onLearnButton?.let { { it(powerBtn) } },
                isLearningMode = isLearningMode,
                variant = RemoteButtonVariant.POWER,
                modifier = Modifier.fillMaxWidth(0.5f),
                diameter = 72.dp
            )
            Spacer(modifier = Modifier.height(Spacing.extraLarge))
        }
    }

    remoteItem("fan_functions") {
        val visibleButtons = if (inlineExtensions) otherButtons else otherButtons.take(org.qinyan.rimoat.ui.components.MAIN_PANEL_KEY_COUNT)
        if (visibleButtons.isNotEmpty()) Surface(
            shape = RoundedCornerShape(24.dp),
            color = ExpressiveSurface.card,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                RemoteFunctionGrid(visibleButtons, onSendButton, onLearnButton, isLearningMode, onRepeatButton, onUnavailable = onUnavailable)
            }
        }
    }

}

/**
 * Generic Grid Remote Layout for Audio, Projector, and Custom Profiles
 */
internal fun LazyListScope.genericRemoteItems(
    profile: RemoteProfile,
    onSendButton: (RemoteButton) -> Unit,
    onLearnButton: ((RemoteButton) -> Unit)? = null,
    isLearningMode: Boolean = false,
    onRepeatButton: ((RemoteButton) -> Unit)? = null,
    morePage: Boolean = false,
    inlineExtensions: Boolean = false,
    onUnavailable: ((RemoteButton) -> Unit)? = null
) {
    if (morePage) {
        remoteItem("generic_functions") { RemoteFunctionGroups(remoteExtraButtons(profile), onSendButton, onLearnButton, isLearningMode, onRepeatButton, preserveOrder = profile.panelLayout.order.isNotEmpty(), onUnavailable = onUnavailable) }
        return
    }

    if (profile.buttons.isEmpty()) {
        remoteItem("empty_buttons") {
            Text(stringResource(R.string.screen_no_buttons), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.screen_learn_custom_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val powerBtn = profile.buttons.firstOrNull { it.isPowerKey() }
    val directions = org.qinyan.rimoat.ui.components.genericDirectionButtons(profile.buttons)
    val directionButtons = directions.values.toList()
    val useDpad = directions.keys.containsAll(org.qinyan.rimoat.ui.components.DpadDirection.cardinal)
    val otherButtons = profile.buttons.filter { it != powerBtn && (!useDpad || it !in directionButtons) }

    remoteItem("generic_header") {
        // Top power if available
        if (powerBtn != null) {
            RemoteButtonView(
                label = localizedButtonLabel(powerBtn),
                iconName = "power",
                onClick = { onSendButton(powerBtn) },
                enabled = isLearningMode || powerBtn.hasSignal,
                onUnavailable = onUnavailable?.let { explain -> { explain(powerBtn) } },
                    onRepeat = onRepeatButton?.let { action -> { action(powerBtn) } },
                onLongClick = onLearnButton?.let { { it(powerBtn) } },
                isLearningMode = isLearningMode,
                variant = RemoteButtonVariant.POWER,
                modifier = Modifier.fillMaxWidth(0.5f),
                diameter = 72.dp
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // D-Pad if direction buttons exist
        if (useDpad) {
            val up = directions[org.qinyan.rimoat.ui.components.DpadDirection.UP]
            val down = directions[org.qinyan.rimoat.ui.components.DpadDirection.DOWN]
            val left = directions[org.qinyan.rimoat.ui.components.DpadDirection.LEFT]
            val right = directions[org.qinyan.rimoat.ui.components.DpadDirection.RIGHT]
            val ok = directions[org.qinyan.rimoat.ui.components.DpadDirection.OK]
            val dpadPlaceholders = org.qinyan.rimoat.ui.components.DpadDirection.entries.associateWith { direction ->
                directions[direction] ?: direction.placeholderButton(stringResource(direction.labelRes))
            }

            DpadController(
                onUnavailable = onUnavailable?.let { explain -> { direction ->
                    explain(dpadPlaceholders.getValue(direction))
                } },
                onRepeat = { direction -> directions[direction]?.let { onRepeatButton?.invoke(it) } },
                onUp = { up?.let { onSendButton(it) } },
                onDown = { down?.let { onSendButton(it) } },
                onLeft = { left?.let { onSendButton(it) } },
                onRight = { right?.let { onSendButton(it) } },
                onOk = { ok?.let { onSendButton(it) } },
                isLearningMode = isLearningMode,
                availableKeys = directions.filterValues { it.hasSignal }.keys,
                onLearn = onLearnButton?.let { learn -> { direction ->
                    learn(dpadPlaceholders.getValue(direction))
                } }
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
    }

    remoteItem("generic_functions") {
        val numbers = otherButtons.filter { remoteButtonNumber(it) != null }.sortedBy { remoteButtonNumber(it)?.let { n -> if (n == 0) 10 else n } }
        val functions = otherButtons - numbers.toSet()
        if (numbers.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ExpressiveSurface.card,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                    RemoteFunctionGrid(numbers, onSendButton, onLearnButton, isLearningMode, onRepeatButton, onUnavailable = onUnavailable)
                }
            }
            if (functions.isNotEmpty()) Spacer(Modifier.height(14.dp))
        }
        val visibleFunctions = if (inlineExtensions) functions else functions.take(org.qinyan.rimoat.ui.components.MAIN_PANEL_KEY_COUNT)
        if (visibleFunctions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ExpressiveSurface.card,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                    RemoteFunctionGrid(visibleFunctions, onSendButton, onLearnButton, isLearningMode, onRepeatButton, onUnavailable = onUnavailable)
                }
            }
        }
    }
}

internal fun AcUnavailableReason.messageResource(): Int = when (this) {
    AcUnavailableReason.MISSING_SIGNAL -> R.string.control_reason_missing_signal
    AcUnavailableReason.ENCODER_UNAVAILABLE -> R.string.control_reason_encoder
    AcUnavailableReason.STATE_RESTRICTED -> R.string.control_reason_state
    AcUnavailableReason.HARDWARE_UNAVAILABLE -> R.string.control_reason_hardware
    AcUnavailableReason.CAPABILITY_UNAVAILABLE -> R.string.control_reason_unknown
    AcUnavailableReason.TEMPERATURE_MINIMUM -> R.string.control_reason_temperature_minimum
    AcUnavailableReason.TEMPERATURE_MAXIMUM -> R.string.control_reason_temperature_maximum
}

internal fun acPanelActions(profile: RemoteProfile, available: Set<AcAction>, learning: Boolean): List<AcAction> {
    val staticAc = profile.useStaticAcButtons || profile.acProtocolId == null
    val actions = listOf(AcAction.MODE, AcAction.FAN) + AcAction.entries.filter { action ->
        action.ordinal >= AcAction.SWING.ordinal && (action == AcAction.SWING ||
            !staticAc && (action in available || action == AcAction.HORIZONTAL_SWING || learning) ||
            acButtonForAction(profile, action) != null)
    }
    return if (learning) actions else actions.filter { panelAction(it) !in profile.panelLayout.hidden }
        .sortedBy { profile.panelLayout.rank(panelAction(it)) }
}

private data class UnavailableControl(val button: RemoteButton, val reason: AcUnavailableReason)
