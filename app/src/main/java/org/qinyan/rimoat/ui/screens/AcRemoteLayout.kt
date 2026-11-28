@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.i18n.labelRes
import org.qinyan.rimoat.R
import org.qinyan.rimoat.ui.components.acButtonForAction
import org.qinyan.rimoat.ui.components.acRawExtensionButtons
import org.qinyan.rimoat.i18n.localizedAcRawButtonLabel
import org.qinyan.rimoat.ui.components.RemoteFunctionGroups
import org.qinyan.rimoat.ui.theme.Spacing
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.res.stringResource
import org.qinyan.rimoat.data.AcUnavailableReason
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import org.qinyan.rimoat.ui.components.ExpressiveDropdownMenu
import org.qinyan.rimoat.ui.components.ExpressiveSelectableMenuItem
import androidx.compose.ui.semantics.selected
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.ui.components.RemoteButtonVariant
import org.qinyan.rimoat.ui.components.controlAppearance
import org.qinyan.rimoat.ui.components.RemoteButtonView
import androidx.compose.foundation.shape.RoundedCornerShape
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.viewmodel.AcControlState

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
