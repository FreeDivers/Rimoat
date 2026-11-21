package org.qinyan.rimoat.ui.learning

import org.qinyan.rimoat.i18n.localizedButtonLabel
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.asString
import org.qinyan.rimoat.i18n.uiTextDetail

import org.qinyan.rimoat.R


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.DialogProperties
import org.qinyan.rimoat.ui.components.TwoPaneLayout
import org.qinyan.rimoat.ui.components.rememberWideLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import org.qinyan.rimoat.ui.motion.MotionAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.hardware.learning.LearnerType
import org.qinyan.rimoat.hardware.learning.LearningState
import org.qinyan.rimoat.ui.HapticHelper
import org.qinyan.rimoat.ui.components.ActionIconButton
import kotlinx.coroutines.launch

/**
 * MD3 Single Button IR Learning Dialog with full lifecycle support:
 * Ready -> Listening (with IrListeningRipple) -> Captured (with IrWaveformPreview + Test-Before-Save)
 * -> Timeout / Error handling.
 *
 * Ensures safe defensive cancellation on dismiss or back press.
 */
@Composable
fun SingleButtonLearningDialog(
    button: RemoteButton,
    learnerManager: IrLearnerManager,
    transmitterManager: IrTransmitterManager,
    onDismissRequest: () -> Unit,
    onSaveLearnedButton: (button: RemoteButton, signal: IrSignal.Raw) -> Unit,
    autoStart: Boolean = false,
    isSaving: Boolean = false,
    saveError: String? = null,
    initialCapturedSignal: IrSignal.Raw? = null,
    onCaptureChanged: (IrSignal.Raw?) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val backendState by learnerManager.state.collectAsStateWithLifecycle()
    var learningState by remember(button.id) {
        mutableStateOf<LearningState>(initialCapturedSignal?.let { LearningState.Captured(it) } ?: LearningState.Idle)
    }
    var learningJob by remember(button.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var learningEpoch by remember(button.id) { mutableStateOf(0) }
    val learnerType by learnerManager.learnerType.collectAsStateWithLifecycle()
    val forceMockMode by learnerManager.forceMockMode.collectAsStateWithLifecycle()
    val receiveConfirmed by learnerManager.receiveConfirmed.collectAsStateWithLifecycle()
    val isHardwareAvailable by learnerManager.isAvailable.collectAsStateWithLifecycle()

    var isTestingEmit by remember(button.id) { mutableStateOf(false) }
    var testFeedbackMessage by remember(button.id) { mutableStateOf<UiText?>(null) }
    var testFeedbackError by remember(button.id) { mutableStateOf(false) }
    var testJob by remember(button.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun cancelTest() {
        testJob?.cancel()
        testJob = null
        isTestingEmit = false
        testFeedbackMessage = null
        testFeedbackError = false
    }

    fun cancelLearning() {
        learningEpoch++
        learningJob?.cancel()
        learningJob = null
        learnerManager.cancel()
        learningState = LearningState.Idle
    }

    fun startLearning() {
        if (isSaving || isTestingEmit || learningJob?.isActive == true) return
        cancelLearning()
        onCaptureChanged(null)
        val epoch = ++learningEpoch
        testFeedbackMessage = null
        testFeedbackError = false
        learningState = LearningState.Listening(30, 30)
        HapticHelper.performStartListening(haptic, context)
        learningJob = scope.launch {
            try {
                val result = learnerManager.startLearning(timeoutSeconds = 30)
                if (epoch == learningEpoch) {
                    learningState = result.fold(
                        onSuccess = { signal -> onCaptureChanged(signal); LearningState.Captured(signal) },
                        onFailure = { error -> when {
                            error is kotlinx.coroutines.CancellationException -> LearningState.Idle
                            error.message?.contains("timed out", ignoreCase = true) == true -> LearningState.Timeout
                            else -> LearningState.Error(error.localizedMessage.orEmpty())
                        } })
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (epoch == learningEpoch) learningState = LearningState.Error(e.localizedMessage.orEmpty())
            }
        }
    }

    LaunchedEffect(backendState) {
        if (learningJob?.isActive == true && backendState is LearningState.Listening) learningState = backendState
    }

    DisposableEffect(button.id) {
        if (autoStart) startLearning()
            onDispose { cancelTest(); cancelLearning() }
    }
    org.qinyan.rimoat.ui.CancelOperationsOnStop {
        if (learningJob?.isActive == true) cancelLearning()
        cancelTest()
    }

    // Trigger haptics when reaching Captured state
    LaunchedEffect(learningState) {
        if (learningState is LearningState.Captured) {
            HapticHelper.performCaptured(haptic, context)
        }
    }

    SingleButtonLearningDialogContent(
        button = button,
        state = learningState,
        isTesting = isTestingEmit,
        testFeedbackMessage = testFeedbackMessage,
        testFeedbackError = testFeedbackError,
        isSaving = isSaving,
        saveError = saveError,
        learnerType = learnerType,
        receiveConfirmed = receiveConfirmed,
        isHardwareAvailable = isHardwareAvailable,
        onDisableMockMode = if (forceMockMode) ({ cancelLearning(); learnerManager.setForceMockMode(false) }) else null,
        onEnableMockMode = {
            learnerManager.setForceMockMode(true)
            startLearning()
        },
        onStartLearning = { startLearning() },
        onCancelLearning = {
            HapticHelper.performSelection(haptic, context)
            cancelLearning()
        },
        onTestEmit = { signal ->
            testJob = scope.launch {
                isTestingEmit = true
                testFeedbackError = false
                HapticHelper.performTestEmit(haptic, context)
                try {
                    val receipt = transmitterManager.transmitWithReceipt(listOf(signal)).getOrThrow()
                    testFeedbackMessage = if (receipt.simulated) UiText.Resource(R.string.screen_simulated_send) else UiText.Resource(R.string.screen_test_sent)
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (t: Exception) {
                    testFeedbackError = true
                    testFeedbackMessage = UiText.resource(R.string.screen_send_error_t, t.uiTextDetail(R.string.screen_hardware_error))
                } finally {
                    isTestingEmit = false
                }
            }
        },
        onSave = { signal ->
            HapticHelper.performSaveSuccess(haptic, context)
            onSaveLearnedButton(button, signal)
        },
        onDismiss = {
            if (!isSaving) { cancelTest(); cancelLearning(); onDismissRequest() }
        }
    )
}

@Composable
fun SingleButtonLearningDialogContent(
    button: RemoteButton,
    state: LearningState,
    isTesting: Boolean,
    testFeedbackMessage: UiText? = null,
    learnerType: LearnerType = LearnerType.NONE,
    isHardwareAvailable: Boolean = false,
    onEnableMockMode: () -> Unit = {},
    onDisableMockMode: (() -> Unit)? = null,
    onStartLearning: () -> Unit,
    onCancelLearning: () -> Unit,
    onTestEmit: (IrSignal.Raw) -> Unit,
    onSave: (IrSignal.Raw) -> Unit,
    onDismiss: () -> Unit,
    isSaving: Boolean = false,
    saveError: String? = null,
    receiveConfirmed: Boolean = false,
    testFeedbackError: Boolean = false
) {
    val configuration = LocalConfiguration.current
    val splitDialog = rememberWideLayout() && configuration.screenWidthDp >= 600
    val buttonLabel = localizedButtonLabel(button, LocalContext.current)
    MotionAlertDialog(
        onDismissRequest = onDismiss,
        modifier = if (splitDialog) Modifier.fillMaxWidth(.96f).widthIn(max = 960.dp) else Modifier,
        properties = DialogProperties(usePlatformDefaultWidth = !splitDialog),
        title = {
            Text(
                stringResource(R.string.screen_learn_button_title, buttonLabel),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            val hardware: @Composable () -> Unit = {
                HardwareStatusBanner(learnerType, isHardwareAvailable, onEnableMockMode,
                    receiveConfirmed = receiveConfirmed, onDisableMockMode = onDisableMockMode, enabled = !isSaving && !isTesting)
            }
            val captureDetails: @Composable () -> Unit = {
                saveError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                org.qinyan.rimoat.ui.motion.StateFade(state, contentKey = { it::class }) { visibleState ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (visibleState) {
                            is LearningState.Idle -> Unit
                            is LearningState.Listening -> {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    if (learnerType == LearnerType.MOCK) stringResource(R.string.screen_simulated_receive)
                                    else stringResource(R.string.screen_press_original_button, buttonLabel),
                                    Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                IrListeningRipple(remainingSeconds = visibleState.remainingSeconds, totalSeconds = visibleState.totalSeconds,
                                    modifier = Modifier)
                                }
                            }
                            is LearningState.Captured -> {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        stringResource(R.string.screen_signal_received),
                                        Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (!splitDialog) ActionIconButton(stringResource(R.string.screen_rerecord), onStartLearning, enabled = isHardwareAvailable && !isSaving && !isTesting) {
                                        Icon(Icons.Rounded.Replay, contentDescription = null)
                                    }
                                }
                                IrWaveformPreview(signal = visibleState.signal)
                                OutlinedButton(onClick = { onTestEmit(visibleState.signal) },
                                    enabled = !isTesting && !isSaving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    if (isTesting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (isTesting) stringResource(R.string.screen_testing_transmit) else stringResource(R.string.screen_test_transmit))
                                }
                                testFeedbackMessage?.asString()?.takeIf { it.isNotBlank() }?.let { feedback ->
                                    Text(
                                        feedback,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (testFeedbackError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                    )
                                }
                            }
                            is LearningState.Timeout -> Text(
                                stringResource(R.string.screen_no_valid_ir),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                            is LearningState.Error -> Text(
                                visibleState.message.ifBlank { stringResource(R.string.screen_learn_failed) },
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                        }
                    }
                }
            }
            if (splitDialog) {
                BoxWithConstraints(Modifier.fillMaxWidth().heightIn(
                    max = (configuration.screenHeightDp.dp - 160.dp).coerceAtLeast(80.dp))) {
                    TwoPaneLayout(primaryFraction = .4f, primary = {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            hardware()
                            if (state is LearningState.Listening) {
                                TextButton(onCancelLearning) { Text(stringResource(R.string.screen_cancel_receive)) }
                            } else {
                                Button(onStartLearning, enabled = isHardwareAvailable && !isSaving && !isTesting) {
                                    Text(stringResource(if (state is LearningState.Captured) R.string.screen_rerecord else R.string.screen_start_record))
                                }
                            }
                        }
                    }, secondary = {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(buttonLabel, style = MaterialTheme.typography.titleMedium)
                            captureDetails()
                            if (state is LearningState.Captured) Button({ onSave(state.signal) }, enabled = !isSaving && !isTesting) {
                                Text(stringResource(if (isSaving) R.string.screen_saving_progress else R.string.screen_save_button))
                            }
                        }
                    })
                }
            } else {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    hardware()
                    captureDetails()
                }
            }
        },
        confirmButton = {
            if (!splitDialog) when (state) {
                is LearningState.Captured -> Button({ onSave(state.signal) }, enabled = !isSaving && !isTesting, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (isSaving) stringResource(R.string.screen_saving_progress) else stringResource(R.string.screen_save_button))
                }
                is LearningState.Listening -> TextButton(onCancelLearning, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.screen_cancel_receive))
                }
                else -> Button(onStartLearning, enabled = isHardwareAvailable, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (state is LearningState.Idle) stringResource(R.string.screen_start_record) else stringResource(R.string.screen_retry))
                }
            }
        },
        dismissButton = {
            TextButton(onDismiss, enabled = !isSaving, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.screen_close))
            }
        }
    )
}
