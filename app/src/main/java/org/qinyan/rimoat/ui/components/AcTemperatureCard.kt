package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.ui.motion.AppMotion
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.SoundEffectOnInteraction
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.ui.HapticHelper
import org.qinyan.rimoat.ui.theme.ExpressiveSurface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AcTemperatureCard(
    temperature: Int,
    powerOn: Boolean,
    isDemonstrating: Boolean,
    canIncrease: Boolean,
    canDecrease: Boolean,
    canTogglePower: Boolean,
    isLearningMode: Boolean,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onTogglePower: () -> Unit,
    statusSummary: String? = null,
    unavailableDescriptions: Map<AcAction, String> = emptyMap(),
    onUnavailable: ((AcAction) -> Unit)? = null,
    modeLabel: String? = null,
    modeIcon: ImageVector? = null,
    fanLabel: String? = null,
    fanIcon: ImageVector? = null,
    timerLabel: String? = null
) {
    SoundEffectOnInteraction(enabled = false) {
    MaterialTheme(motionScheme = MotionScheme.standard()) {
    val motion = AppMotion.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val powerInteraction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val powerHaptic = org.qinyan.rimoat.ui.rememberPressHaptic(powerInteraction, canTogglePower, isLearningMode)
    val powerShape = rememberExpressiveShape(powerInteraction, CircleShape, RoundedCornerShape(40))
    val powerLabel = if (isLearningMode) AppStrings.get(R.string.control_learn_power) else AppStrings.get(if (powerOn) R.string.control_power_off else R.string.control_power_on)
    val powerAction = if (powerOn) AcAction.POWER_OFF else AcAction.POWER_ON
    val fontScale = LocalDensity.current.fontScale
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = ExpressiveSurface.card
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stackedHeader = maxWidth < (72 + 112 * fontScale).dp
                val title: @Composable () -> Unit = {
                    Surface(
                        shape = CircleShape,
                        color = if (isDemonstrating) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (isDemonstrating) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            text = if (isDemonstrating) AppStrings.get(R.string.control_simulation_setting, "模拟设定") else AppStrings.get(R.string.control_target_temp, "设定温度"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                val power: @Composable () -> Unit = {
                UnavailableAcControl(canTogglePower, powerLabel, unavailableDescriptions[powerAction],
                    onUnavailable?.let { { it(powerAction) } }, checked = powerOn.takeUnless { isLearningMode }) {
                ActionTooltip(if (isLearningMode) AppStrings.get(R.string.control_learn_power, "学习电源按键") else if (powerOn) AppStrings.get(R.string.control_power_off, "关机") else AppStrings.get(R.string.control_power_on, "开机")) {
                FilledIconToggleButton(
                    checked = powerOn,
                    interactionSource = powerInteraction,
                    shape = powerShape,
                    modifier = Modifier.then(powerHaptic.modifier).size(72.dp).clearAndSetSemantics {
                        contentDescription = powerLabel
                        if (canTogglePower) onClick { powerHaptic(); onTogglePower(); true } else disabled()
                        if (!isLearningMode) {
                            toggleableState = ToggleableState(powerOn)
                            stateDescription = if (powerOn) AppStrings.get(R.string.control_set_on, "设定开启") else AppStrings.get(R.string.control_set_off, "设定关闭")
                        }
                        role = if (isLearningMode) Role.Button else Role.Switch
                    },
                    enabled = canTogglePower,
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary),
                    onCheckedChange = { powerHaptic(); onTogglePower() }
                ) {
                    Icon(if (powerOn) Icons.Rounded.PowerSettingsNew else Icons.Rounded.PowerOff,
                        contentDescription = null, modifier = Modifier.size(28.dp))
                }
                }
                }
                }
                if (stackedHeader) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    title(); power()
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { title() }
                    power()
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stacked = maxWidth < (144 + 136 * fontScale).dp
                val decrease: @Composable () -> Unit = {
                    UnavailableAcControl(canDecrease, AppStrings.get(R.string.control_temp_down),
                        unavailableDescriptions[AcAction.TEMP_DOWN], onUnavailable?.let { { it(AcAction.TEMP_DOWN) } }) {
                    RemoteIconButton(AppStrings.get(R.string.control_temp_down, "降温"), Icons.Rounded.Remove, onDecrease, enabled = canDecrease,
                        isLearningMode = isLearningMode, onLearn = onDecrease)
                    }
                }
                val increase: @Composable () -> Unit = {
                    UnavailableAcControl(canIncrease, AppStrings.get(R.string.control_temp_up),
                        unavailableDescriptions[AcAction.TEMP_UP], onUnavailable?.let { { it(AcAction.TEMP_UP) } }) {
                    RemoteIconButton(AppStrings.get(R.string.control_temp_up, "升温"), Icons.Rounded.Add, onIncrease, enabled = canIncrease,
                        isLearningMode = isLearningMode, onLearn = onIncrease)
                    }
                }
                val settingLabel = stringResource(if (isDemonstrating) R.string.control_simulation_setting else R.string.control_target_temp)
                val readout: @Composable () -> Unit = {
                    AnimatedContent(
                        targetState = temperature,
                        transitionSpec = { fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                        label = "Temperature",
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = statusSummary ?: AppStrings.get(R.string.control_temperature_description, "%1\$s %2\$d 摄氏度", settingLabel, temperature)
                            liveRegion = LiveRegionMode.Polite
                        }
                    ) { value ->
                        val digits: @Composable () -> Unit = {
                            Text(value.toString(), style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                                color = if (powerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val unit: @Composable () -> Unit = {
                            Text("°C", style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(start = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (maxWidth < (176 * fontScale).dp) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            digits(); unit()
                        } else Row(verticalAlignment = Alignment.CenterVertically) { digits(); unit() }
                    }
                }
                if (stacked) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    readout()
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) { decrease(); increase() }
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    decrease(); readout(); increase()
                }
            }
            val statusChips = buildList {
                if (!modeLabel.isNullOrBlank()) add(Triple(modeIcon, modeLabel, false))
                if (!fanLabel.isNullOrBlank()) add(Triple(fanIcon, fanLabel, false))
                if (!timerLabel.isNullOrBlank()) add(Triple(null, timerLabel, true))
            }
            if (statusChips.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    statusChips.forEach { (chipIcon, chipLabel, highlighted) ->
                        Surface(
                            shape = CircleShape,
                            color = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (highlighted) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                chipIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                Text(chipLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
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

/** Disabled controls keep their appearance and expose one diagnostic node, never a send action. */
@Composable
private fun UnavailableAcControl(
    enabled: Boolean,
    label: String,
    reason: String?,
    onUnavailable: (() -> Unit)?,
    checked: Boolean? = null,
    content: @Composable () -> Unit
) {
    if (enabled || onUnavailable == null) {
        content()
        return
    }
    var taps by remember(label, reason) { mutableIntStateOf(0) }
    var lastTap by remember(label, reason) { mutableLongStateOf(0L) }
    val sceneActive = org.qinyan.rimoat.ui.motion.LocalMotionActive.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val unavailableActionLabel = stringResource(R.string.control_unavailable_action, label)
    val reasonActionLabel = stringResource(R.string.control_show_reason)
    val reasonDescription = reason ?: stringResource(R.string.control_reason_unknown)
    val canDiagnose = {
        sceneActive && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    }
    val diagnose = {
        if (canDiagnose()) {
            val now = android.os.SystemClock.uptimeMillis()
            taps = if (now - lastTap > 5000) 1 else taps + 1
            lastTap = now
            if (taps >= 3) { taps = 0; onUnavailable() }
        }
    }
    Box(Modifier.clearAndSetSemantics {
        contentDescription = label
        role = if (checked == null) Role.Button else Role.Switch
        checked?.let { toggleableState = ToggleableState(it) }
        stateDescription = reasonDescription
        onClick(unavailableActionLabel) { diagnose(); true }
        customActions = listOf(CustomAccessibilityAction(reasonActionLabel) {
            if (canDiagnose()) { taps = 0; onUnavailable(); true } else false
        })
    }, contentAlignment = Alignment.Center) {
        content()
        // Disabled Material buttons consume pointer input. A sibling above the disabled
        // visual receives taps without enabling its transmission callback or adding a focus target.
        Box(Modifier.matchParentSize().clip(CircleShape).clickable(onClick = diagnose))
    }
}
