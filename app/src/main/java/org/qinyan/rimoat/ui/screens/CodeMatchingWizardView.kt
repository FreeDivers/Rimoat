@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import androidx.compose.foundation.layout.fillMaxHeight
import org.qinyan.rimoat.ui.components.contentWidth
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.SoundEffectOnInteraction
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.i18n.UiText
import org.qinyan.rimoat.i18n.asString
import org.qinyan.rimoat.i18n.localizedButtonLabel
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.ui.HapticHelper
import org.qinyan.rimoat.ui.components.*
import org.qinyan.rimoat.ui.motion.*
import org.qinyan.rimoat.ui.viewmodel.AddRemoteUiState
import org.qinyan.rimoat.ui.viewmodel.PairingResult

internal fun pairingKeys(state: AddRemoteUiState): List<RemoteButton> =
    (listOfNotNull(state.primaryTestKey, state.secondaryTestKey) + state.allPreviewButtons).distinctBy { it.id }

internal fun pairingKeyGroup(button: RemoteButton, context: Context): String = when {
    button.label.toIntOrNull() in 0..9 -> context.getString(R.string.screen_number_keys)
    button.iconName == "power" || button.label in setOf("电源", "开关", "开机", "关机") -> context.getString(R.string.screen_power_group)
    button.label in setOf("上", "下", "左", "右", "OK", "确定", "返回", "主页", "菜单", "信源", "输入", "信息") -> context.getString(R.string.screen_navigation_sources)
    button.label.contains("音量") || button.label.contains("静音") || button.label.contains("频道") -> context.getString(R.string.screen_volume_channels)
    else -> localizedLabel(remoteFunctionGroup(button), context)
}

@Composable
fun CodeMatchingWizardView(
    state: AddRemoteUiState,
    onTestKey: (RemoteButton) -> Unit,
    onNextModel: () -> Unit,
    onPrevModel: () -> Unit,
    onMatched: () -> Unit,
    onConfirmTest: (Boolean) -> Unit = {},
    showActions: Boolean = true
) {
    val context = LocalContext.current
    val busy = state.isSendingTest || state.isLoading
    val prevCodeLabel = stringResource(R.string.screen_previous_code)
    val nextCodeLabel = stringResource(R.string.screen_next_code)
    val wide = rememberWideLayout()
    var selectedPage by rememberSaveable { mutableIntStateOf(0) }
    val navigation: @Composable () -> Unit = {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ExpressiveSurface.card,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionIconButton(prevCodeLabel, onPrevModel, enabled = !busy && state.currentRemoteIndex > 0) {
                    Icon(Icons.AutoMirrored.Rounded.NavigateBefore, contentDescription = null)
                }
                Text(
                    stringResource(R.string.screen_code_position, state.currentRemoteIndex + 1, state.remotes.size),
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                ActionIconButton(nextCodeLabel, onNextModel, enabled = !busy && state.currentRemoteIndex < state.remotes.lastIndex) {
                    Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = null)
                }
            }
        }
    }
    val keysContent: @Composable () -> Unit = {
        MotionContent(targetState = state, contentKey = { it.currentRemote?.remoteId ?: "empty" },
            isBack = { from, to -> to.currentRemoteIndex < from.currentRemoteIndex }, modifier = Modifier.fillMaxSize()) { page ->
            val keys = remember(page.primaryTestKey, page.secondaryTestKey, page.allPreviewButtons) { pairingKeys(page) }
            val recommended = (keys.filter { it.id in page.distinguishingKeys } +
                keys.filter { it.label.toIntOrNull() == null } + keys).distinctBy { it.id }.take(6)
            val measurer = rememberTextMeasurer()
            val style = MaterialTheme.typography.labelLarge
            val minimumWidth = with(LocalDensity.current) {
                (keys.maxOfOrNull { measurer.measure(localizedButtonLabel(it, context), style, softWrap = false).size.width } ?: 0).toDp()
            }.plus(16.dp).coerceAtLeast(80.dp)
            BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = (((maxWidth - 24.dp) + 12.dp) / (minimumWidth + 12.dp)).toInt().coerceIn(1, 3)
            RemoteControlPages(keys.size > recommended.size, labels = listOf(stringResource(R.string.screen_test_keys), stringResource(R.string.screen_all_keys)),
                selectedPage = selectedPage, onPageSelected = { selectedPage = it }) { all ->
                val groups = if (all) keys.groupBy { pairingKeyGroup(it, context) } else linkedMapOf("" to recommended)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    groups.entries.forEach { (name, buttons) ->
                        item(key = "group_$name") {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = ExpressiveSurface.card,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (name.isNotBlank()) {
                                        Text(
                                            name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.semantics { heading() }
                                        )
                                    }
                                    buttons.chunked(columns).forEach { row ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            row.forEach { button ->
                                                val observation = page.observations[button.id]
                                                val result = observation?.result
                                                val statusLabel = stringResource(when (result) {
                                                    PairingResult.VALID -> R.string.pairing_result_valid
                                                    PairingResult.INVALID -> R.string.pairing_result_invalid
                                                    else -> R.string.pairing_result_untested
                                                })
                                                Column(
                                                    Modifier.weight(1f),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    RemoteButtonView(
                                                        localizedButtonLabel(button, context),
                                                        button.iconName,
                                                        { onTestKey(button) },
                                                        Modifier.fillMaxWidth(),
                                                        enabled = !busy && button.hasSignal,
                                                        currentState = statusLabel,
                                                        active = page.testedButton?.id == button.id,
                                                        isToggle = false,
                                                        variant = if (button.iconName == "power" || button.label == "电源") RemoteButtonVariant.POWER else RemoteButtonVariant.STANDARD,
                                                        buttonId = button.id,
                                                        builtinLabel = button.builtinLabel
                                                    )
                                                    if (result == PairingResult.VALID || result == PairingResult.INVALID) {
                                                        val isValid = result == PairingResult.VALID
                                                        val badgeColor = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        Row(
                                                            modifier = Modifier.clearAndSetSemantics { },
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isValid) Icons.Rounded.Check else Icons.Rounded.Close,
                                                                contentDescription = null,
                                                                tint = badgeColor,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Text(
                                                                text = statusLabel,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = badgeColor,
                                                                textAlign = TextAlign.Center
                                                            )
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
                    }
                }
            }
        }
            }
    }
    if (wide) {
        TwoPaneLayout(
            modifier = Modifier.fillMaxSize(),
            primary = keysContent,
            secondary = {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    navigation()
                    if (showActions) CodeMatchingActions(state, onMatched, onConfirmTest)
                }
            }
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            navigation()
            Box(Modifier.weight(1f).fillMaxWidth()) { keysContent() }
            if (showActions) CodeMatchingActions(state, onMatched, onConfirmTest)
        }
    }
}

private data class PairingFeedback(
    val remoteId: String?, val question: String?, val message: UiText?, val verifiedCount: Int
)

@Composable
fun CodeMatchingActions(state: AddRemoteUiState, onMatched: () -> Unit, onConfirmTest: (Boolean) -> Unit) {
    val wide = rememberWideLayout()
    val motion = AppMotion.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val noResponse = stringResource(R.string.screen_no_response)
    val responded = stringResource(R.string.screen_responded)
    val busy = state.isSendingTest || state.isLoading
    val remoteId = state.currentRemote?.remoteId
    val currentFeedback = if (state.canConfirmTest || state.testMessage != null ||
        (!state.isSendingTest && state.verifiedCount > 0)) {
        PairingFeedback(
            remoteId,
            state.testedButton?.let { localizedButtonLabel(it, context) }?.takeIf { state.canConfirmTest },
            state.testMessage,
            state.verifiedCount
        )
    } else null
    var lastFeedback by remember { mutableStateOf<PairingFeedback?>(null) }
    SideEffect { if (currentFeedback != null) lastFeedback = currentFeedback }
    // Retain the measured content during another send and while the exit animation finishes.
    // It is presentation only: confirmation always uses the live state and is disabled while busy.
    val feedback = currentFeedback ?: lastFeedback
    val visible = currentFeedback != null ||
        (state.isSendingTest && lastFeedback != null && lastFeedback?.remoteId == remoteId)
    Surface(
        shape = if (wide) RoundedCornerShape(24.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = ExpressiveSurface.card
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedVisibility(visible, Modifier.fillMaxWidth(),
                enter = expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Bottom) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec(), shrinkTowards = Alignment.Bottom) + fadeOut(motion.fastEffectsSpec())) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (feedback?.question != null) {
                        Text(
                            stringResource(R.string.screen_response_question, feedback.question),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                        val canAnswer = state.canConfirmTest && !busy && feedback.remoteId == remoteId
                        val measurer = rememberTextMeasurer()
                        val labelStyle = MaterialTheme.typography.labelLarge
                        val density = LocalDensity.current
                        BoxWithConstraints(
                            Modifier.fillMaxWidth().testTag("pairing_answers_group")
                        ) {
                            val sideBySideWidth = with(density) {
                                (measurer.measure(noResponse, labelStyle, softWrap = false).size.width +
                                    measurer.measure(responded, labelStyle, softWrap = false).size.width).toDp()
                            } + 84.dp
                            val answerNo = {
                                HapticHelper.performSelection(haptic, context)
                                onConfirmTest(false)
                            }
                            val answerYes = {
                                HapticHelper.performSelection(haptic, context)
                                onConfirmTest(true)
                            }
                            if (maxWidth >= sideBySideWidth) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = answerNo,
                                        shapes = ButtonDefaults.shapes(),
                                        enabled = canAnswer,
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    ) { Text(noResponse, textAlign = TextAlign.Center) }
                                    Button(
                                        onClick = answerYes,
                                        shapes = ButtonDefaults.shapes(),
                                        enabled = canAnswer,
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    ) { Text(responded, textAlign = TextAlign.Center) }
                                }
                            } else {
                                Column(
                                    Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = answerNo,
                                        shapes = ButtonDefaults.shapes(),
                                        enabled = canAnswer,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    ) { Text(noResponse, textAlign = TextAlign.Center) }
                                    Button(
                                        onClick = answerYes,
                                        shapes = ButtonDefaults.shapes(),
                                        enabled = canAnswer,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    ) { Text(responded, textAlign = TextAlign.Center) }
                                }
                            }
                        }
                    } else feedback?.message?.let { message ->
                        Text(
                            message.asString(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                    if (feedback != null && feedback.verifiedCount > 0) {
                        Text(
                            stringResource(R.string.screen_verified_count, feedback.verifiedCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().testTag("pairing_confirmation_toolbar")) {
                Button(
                    onClick = onMatched,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    enabled = state.currentRemote != null && !busy,
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.screen_use_code)) }
            }
        }
    }
}
