@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import androidx.compose.material3.ButtonDefaults
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import org.qinyan.rimoat.i18n.labelRes
import org.qinyan.rimoat.i18n.localizedButtonLabel
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppError
import org.qinyan.rimoat.i18n.ErrorAction
import org.qinyan.rimoat.ui.components.openStorageSettings
import org.qinyan.rimoat.ui.components.showAppError
import org.qinyan.rimoat.ui.components.withPanelPresentation
import org.qinyan.rimoat.ui.components.acRawExtensionButtons
import org.qinyan.rimoat.ui.components.hasAcControlPanel
import org.qinyan.rimoat.i18n.localizedAcRawButtonLabel
import org.qinyan.rimoat.ui.components.RemoteControlPages
import org.qinyan.rimoat.ui.components.remoteExtraButtons
import org.qinyan.rimoat.ui.components.CenteredContent
import org.qinyan.rimoat.ui.components.LocalCompactRemoteButtons
import org.qinyan.rimoat.ui.components.rememberWideLayout
import androidx.compose.runtime.CompositionLocalProvider
import org.qinyan.rimoat.ui.theme.Spacing
import org.qinyan.rimoat.ui.motion.ExpandSection
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import org.qinyan.rimoat.ui.motion.MotionContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.res.stringResource
import org.qinyan.rimoat.data.AcUnavailableReason
import org.qinyan.rimoat.ui.components.compactExtensionsFit
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import org.qinyan.rimoat.ui.components.ActionIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import org.qinyan.rimoat.ui.learning.SingleButtonLearningDialog
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.ui.components.HardwareStatusButton
import org.qinyan.rimoat.ui.components.TransmitterSelectionBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import org.qinyan.rimoat.ui.components.RemoteButtonView
import org.qinyan.rimoat.ui.components.TransmitFeedbackBanner
import androidx.compose.foundation.shape.RoundedCornerShape
import org.qinyan.rimoat.ui.components.ExpressiveCompactTopBar
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
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
        RemoteUnavailableDialog(
            detail = detail,
            onDismiss = { unavailableDetail = null },
            onLearn = { button ->
                viewModel.setLearningMode(true)
                viewModel.startLearningButton(button)
            }
        )
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
