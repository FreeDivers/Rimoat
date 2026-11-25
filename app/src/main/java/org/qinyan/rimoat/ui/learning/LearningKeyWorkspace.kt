package org.qinyan.rimoat.ui.learning

import org.qinyan.rimoat.R

import org.qinyan.rimoat.i18n.asString

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import org.qinyan.rimoat.ui.HapticHelper
import org.qinyan.rimoat.ui.motion.AppMotion
import org.qinyan.rimoat.ui.motion.StateFade
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.SoundEffectOnInteraction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.hardware.learning.LearnerType
import org.qinyan.rimoat.hardware.learning.LearningState
import org.qinyan.rimoat.ui.components.*

/** One current key, one status region, and a separate fixed action dock. */
@Composable
internal fun LearningKeyWorkspace(
    page: CustomRemoteWizardUiState, learner: LearningState, learnerType: LearnerType,
    available: Boolean, receiveConfirmed: Boolean, mock: Boolean,
    onMock: (Boolean) -> Unit, onSelect: (Int) -> Unit, onAdd: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}
) {
    val primaryScroll = rememberScrollState()
    val secondaryScroll = rememberScrollState()
    val key = page.currentKey ?: return
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val motion = AppMotion.current
    val learnedProgress = animateFloatAsState(page.learnedCount.toFloat() / page.keyQueue.size.coerceAtLeast(1), motion.defaultEffectsSpec(), label = "LearnedProgress")
    val status: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(page.remoteName, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.screen_learned_position, page.learnedCount, page.keyQueue.size), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            LinearWavyProgressIndicator(progress = { learnedProgress.value }, modifier = Modifier.fillMaxWidth(), waveSpeed = 0.dp)
        }
        Spacer(Modifier.height(12.dp))
        HardwareStatusBanner(learnerType, available, { onMock(true) }, receiveConfirmed = receiveConfirmed,
            onDisableMockMode = if (mock) ({ onMock(false) }) else null, enabled = !page.isTestingTransmit)
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                StateFade(key.displayLabel(), Modifier.weight(1f)) {
                    Text(it, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                }
                if (key.status == KeyLearnStatus.CAPTURED || key.status == KeyLearnStatus.LEARNED) {
                    ActionIconButton(stringResource(R.string.screen_rerecord), onRecord, enabled = available && !page.isTestingTransmit) {
                        Icon(Icons.Rounded.Replay, contentDescription = null)
                    }
                } else Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.CenterStart) {
                StateFade(key.status) { status ->
                when (status) {
                    KeyLearnStatus.LEARNING -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            if (learnerType == LearnerType.MOCK) stringResource(R.string.screen_simulated_receive) else stringResource(R.string.screen_press_original),
                            Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        IrListeningRipple(
                            remainingSeconds = (learner as? LearningState.Listening)?.remainingSeconds ?: 30,
                            totalSeconds = (learner as? LearningState.Listening)?.totalSeconds ?: 30)
                    }
                    KeyLearnStatus.CAPTURED, KeyLearnStatus.LEARNED -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (status == KeyLearnStatus.LEARNED) stringResource(R.string.screen_learned) else stringResource(R.string.screen_signal_received),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                        key.capturedSignal?.let { signal ->
                            IrWaveformPreview(signal)
                        }
                    }
                    else -> Text(
                        if (status == KeyLearnStatus.SKIPPED) stringResource(R.string.screen_skipped) else stringResource(R.string.screen_awaiting_record),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                }
            }
            page.testFeedbackMessage?.let {
                Text(
                    it.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            page.errorMessage?.let {
                Text(
                    it.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
    }
    val keyGrid: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.screen_buttons), Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            ActionIconButton(stringResource(R.string.screen_add_button), onAdd) { Icon(Icons.Rounded.Add, contentDescription = null) }
        }
        val textMeasurer = rememberTextMeasurer()
        val labelStyle = MaterialTheme.typography.labelLarge
        val labelWidth = page.keyQueue.maxOfOrNull { textMeasurer.measure(it.displayLabel(), labelStyle, softWrap = false).size.width } ?: 0
        // Reserve enough room for the label, checkmark, and toggle button padding.
        val minimumKeyWidth = with(LocalDensity.current) { labelWidth.toDp() } + 80.dp
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (3 downTo 1).first { maxWidth >= minimumKeyWidth * it + 8.dp * (it - 1) || it == 1 }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            page.keyQueue.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { columnIndex, item ->
                        val index = rowIndex * columns + columnIndex
                        val learned = item.status == KeyLearnStatus.LEARNED
                        val current = page.currentIndex == index
                        val container by animateColorAsState(
                            if (learned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            motion.fastEffectsSpec(), label = "LearnedKeyColor")
                        val content = if (learned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        val keyStateDescription = stringResource(when (item.status) {
                            KeyLearnStatus.LEARNED -> R.string.screen_learned
                            KeyLearnStatus.CAPTURED -> R.string.screen_awaiting_confirm
                            KeyLearnStatus.LEARNING -> R.string.screen_receiving
                            KeyLearnStatus.SKIPPED -> R.string.screen_skipped
                            KeyLearnStatus.PENDING -> R.string.screen_unlearned
                        })
                        ToggleButton(checked = current, onCheckedChange = {
                                HapticHelper.performSelection(haptic, context)
                                onSelect(index)
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                stateDescription = keyStateDescription
                                selected = current
                                role = Role.RadioButton
                            },
                            colors = ToggleButtonDefaults.colors(containerColor = container, checkedContainerColor = container,
                                contentColor = content, checkedContentColor = content),
                            border = BorderStroke(if (current) 2.dp else 1.dp,
                                if (current) MaterialTheme.colorScheme.primary else if (learned) Color.Transparent else MaterialTheme.colorScheme.outlineVariant),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(item.displayLabel(), Modifier.fillMaxWidth().padding(horizontal = 20.dp), textAlign = TextAlign.Center)
                                    if (learned)
                                        Icon(Icons.Rounded.Check, null, Modifier.align(Alignment.CenterStart).size(16.dp))
                                }
                            }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        }
    }
    val wide = rememberWideLayout()
    BoxWithConstraints(modifier) {
        if (wide) {
            TwoPaneLayout(Modifier.fillMaxSize(),
                primary = {
                    Column(Modifier.fillMaxSize().verticalScroll(primaryScroll).padding(8.dp)) {
                        status()
                        actions()
                    }
                },
                secondary = {
                    Column(Modifier.fillMaxSize().verticalScroll(secondaryScroll).padding(8.dp)) { keyGrid() }
                })
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(primaryScroll).padding(horizontal = 20.dp, vertical = 8.dp)) {
                status()
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
                keyGrid()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LearningKeyActions(
    page: CustomRemoteWizardUiState, available: Boolean, onRecord: () -> Unit,
    onCancel: () -> Unit, onSkip: () -> Unit, onConfirm: () -> Unit, onTest: () -> Unit,
    inPane: Boolean = false
) {
    val key = page.currentKey ?: return
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth().then(if (inPane) Modifier else Modifier.navigationBarsPadding())
        .padding(horizontal = if (inPane) 0.dp else 20.dp, vertical = 12.dp)) {
        when (key.status) {
            KeyLearnStatus.LEARNING -> HorizontalFloatingToolbar(
                expanded = true, modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                OutlinedButton(
                    onClick = { HapticHelper.performSelection(haptic, context); onCancel() },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.heightIn(min = 56.dp)
                ) { Text(stringResource(R.string.screen_cancel_receive)) }
            }
            KeyLearnStatus.CAPTURED, KeyLearnStatus.LEARNED -> LearningActionBar(
                primaryLabel = stringResource(R.string.screen_confirm_continue),
                secondary = {
                    SoundEffectOnInteraction(enabled = false) {
                        ActionTooltip(stringResource(R.string.screen_test_transmit)) {
                            val testLabel = stringResource(
                                if (page.isTestingTransmit) R.string.screen_sending else R.string.screen_test_transmit
                            )
                            FilledTonalIconButton(
                                onTest,
                                modifier = Modifier.size(56.dp).semantics { contentDescription = testLabel },
                                enabled = !page.isTestingTransmit,
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                if (page.isTestingTransmit) LoadingIndicator(Modifier.size(24.dp))
                                else Icon(Icons.Rounded.PlayArrow, null, Modifier.size(24.dp))
                            }
                        }
                    }
                },
                primary = { actionModifier ->
                    Button(
                        onClick = { HapticHelper.performRemoteClick(haptic, context); onConfirm() },
                        shapes = ButtonDefaults.shapes(),
                        modifier = actionModifier.heightIn(min = 56.dp),
                        enabled = !page.isTestingTransmit,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                    ) { Text(stringResource(R.string.screen_confirm_continue), textAlign = TextAlign.Center) }
                }
            )
            else -> LearningActionBar(
                primaryLabel = stringResource(R.string.screen_record_signal),
                secondaryLabel = stringResource(R.string.screen_skip),
                secondary = {
                    TextButton(
                        onClick = { HapticHelper.performSelection(haptic, context); onSkip() },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.screen_skip)) }
                },
                primary = { actionModifier ->
                    Button(onRecord, shapes = ButtonDefaults.shapes(),
                        modifier = actionModifier.heightIn(min = 56.dp), enabled = available) {
                        Text(stringResource(R.string.screen_record_signal), textAlign = TextAlign.Center)
                    }
                }
            )
        }
    }
}
