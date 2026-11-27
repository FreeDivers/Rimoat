@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.i18n.AppStrings
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.i18n.localizedButtonLabel
import org.qinyan.rimoat.i18n.localizedAcRawButtonLabel
import org.qinyan.rimoat.i18n.labelRes
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.ui.components.*
import org.qinyan.rimoat.ui.viewmodel.AcControlState
import androidx.compose.foundation.shape.RoundedCornerShape
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.IrRemoteTheme

internal data class RemotePaneButtons(
    val primary: List<RemoteButton>,
    val common: List<RemoteButton>,
    val numbers: List<RemoteButton>,
    val extensions: List<RemoteButton>
)

/** Every configured key has one location; missing standard controls use the existing key order. */
internal fun remotePaneButtons(profile: RemoteProfile): RemotePaneButtons {
    val buttons = profile.buttons.distinctBy { it.id }
    val numeric = buttons.filter { remoteButtonNumber(it) != null }
    if (profile.category == DeviceCategory.TV) {
        val standard = tvStandardButtons(profile)
        val primary = (standard.dpadButtons +
            standard.navigation.sortedBy { profile.panelLayout.rank(panelKey(it)) }).distinctBy { it.id }
        val primaryIds = primary.map { it.id }.toSet()
        val common = (listOfNotNull(standard.power) +
            listOfNotNull(standard.source, standard.mute).sortedBy { profile.panelLayout.rank(panelKey(it)) } +
            standard.rockers).distinctBy { it.id }.filter { it.id !in primaryIds }
        val commonIds = common.map { it.id }.toSet()
        val numbers = numeric.filter { it.id !in primaryIds && it.id !in commonIds }
        val used = primaryIds + commonIds + numbers.map { it.id }
        if (primary.isNotEmpty()) return RemotePaneButtons(primary, common, numbers, buttons.filter { it.id !in used })
    } else {
        val power = buttons.firstOrNull { it.isPowerKey() }
        val directions = genericDirectionButtons(buttons)
        val primary = if (profile.category == DeviceCategory.FAN) {
            listOfNotNull(
                power,
                buttons.firstOrNull {
                    it != power && (it.key in setOf(ButtonKey.FAN_SPEED, ButtonKey.AIRFLOW) ||
                        (it.key == null && "speed" in it.id.lowercase()))
                }
            )
        } else listOfNotNull(power) + if (directions.keys.containsAll(DpadDirection.cardinal)) directions.values else emptyList()
        if (primary.isNotEmpty()) {
            val remaining = buttons.filter { it !in primary && it !in numeric }
            return RemotePaneButtons(
                primary.distinctBy { it.id },
                remaining.take(MAIN_PANEL_KEY_COUNT),
                numeric.filter { it !in primary },
                remaining.drop(MAIN_PANEL_KEY_COUNT)
            )
        }
    }
    val nonNumeric = buttons.filter { it !in numeric }
    val split = ((nonNumeric.size + 1) / 2).coerceAtLeast(1)
    return RemotePaneButtons(nonNumeric.take(split), emptyList(), numeric, nonNumeric.drop(split))
}

private fun tvLearningProfile(profile: RemoteProfile): RemoteProfile {
    val standard = tvStandardButtons(profile)
    val directions = DpadDirection.entries
        .filter { direction -> standard.dpadButton(direction) == null && panelKey(direction.placeholderId) !in profile.panelLayout.hidden }
        .map { direction -> direction.placeholderButton(AppStrings.get(direction.labelRes)) }
    val numbers = (0..9).mapNotNull { number ->
        if (profile.buttons.any { remoteButtonNumber(it) == number } || "key:num_$number" in profile.panelLayout.hidden ||
            "key:tv_num_$number" in profile.panelLayout.hidden) null
        else RemoteButton("num_$number", "$number")
    }
    return profile.copy(buttons = profile.buttons + directions + numbers)
}

@Composable
internal fun LandscapeRemotePanel(
    profile: RemoteProfile,
    acState: AcControlState,
    isDemonstrating: Boolean,
    availableAcActions: Set<AcAction>,
    unavailableReasons: Map<AcAction, AcUnavailableReason>,
    isLearningMode: Boolean,
    isTransmitting: Boolean,
    selectedPage: Int,
    onPageSelected: (Int) -> Unit,
    showNumberPad: Boolean,
    onNumberPadChange: (Boolean) -> Unit,
    onSendButton: (RemoteButton) -> Unit,
    onLearnButton: (RemoteButton) -> Unit,
    onRepeatButton: (RemoteButton) -> Unit,
    onUnavailable: (RemoteButton, AcUnavailableReason) -> Unit,
    onTogglePower: () -> Unit,
    onTempUp: () -> Unit,
    onTempDown: () -> Unit,
    onCycleMode: () -> Unit,
    onCycleWind: () -> Unit,
    onAction: (AcAction, Int) -> Unit,
    onLoadChoices: suspend (AcAction) -> List<AcStateChoice>,
    onSelectChoice: (AcStateChoice) -> Unit,
    onResend: () -> Unit,
    onPreset: (AcSettings) -> Unit,
    transmitterType: TransmitterType,
    singleConfiguredKey: Boolean = profile.buttons.size == 1,
    primaryScroll: ScrollState = rememberScrollState(),
    secondaryScroll: ScrollState = rememberScrollState()
) {
    val acPanel = profile.hasAcControlPanel()
    val dynamicAc = acPanel && !profile.useStaticAcButtons && profile.acProtocolId != null
    val density = LocalDensity.current
    var commonHeight by remember(profile.id) { mutableStateOf(0.dp) }
    val explain: (RemoteButton) -> Unit = { onUnavailable(it, AcUnavailableReason.MISSING_SIGNAL) }
    val acContent: @Composable (AcLayoutPart) -> Unit = { part ->
        AcRemoteLayout(
            profile = profile, acState = acState, isDemonstrating = isDemonstrating,
            onTogglePower = onTogglePower, onTempUp = onTempUp, onTempDown = onTempDown,
            onCycleMode = onCycleMode, onCycleWind = onCycleWind, availableActions = availableAcActions,
            onAction = onAction, onSendButton = onSendButton, onLoadChoices = onLoadChoices,
            onSelectChoice = onSelectChoice, onLearnButton = onLearnButton, isLearningMode = isLearningMode,
            isTransmitting = isTransmitting,
            onRepeatButton = onRepeatButton, part = part, unavailableReasons = unavailableReasons,
            onUnavailable = onUnavailable
        )
    }
    CompositionLocalProvider(LocalCompactRemoteButtons provides true) {
        if (acPanel) {
            TwoPaneLayout(primary = {
                PanelScroll("remote_primary", primaryScroll) {
                    acContent(AcLayoutPart.TEMPERATURE)
                    if (dynamicAc && !isLearningMode) AcPanelPresets(profile, isTransmitting, onResend, onPreset)
                }
            }, secondary = {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                val availableHeight = maxHeight
                PanelScroll("remote_secondary", secondaryScroll) {
                    Box(Modifier.fillMaxWidth().onSizeChanged { commonHeight = with(density) { it.height.toDp() } }) {
                        acContent(AcLayoutPart.FUNCTIONS)
                    }
                    val main = setOf(AcAction.MODE, AcAction.FAN, AcAction.SWING, AcAction.HORIZONTAL_SWING, AcAction.WIND_DIRECTION)
                    val labels = acPanelActions(profile, availableAcActions, isLearningMode).filter { it !in main }.map { stringResource(it.labelRes) } +
                        acRawExtensionButtons(profile).map { localizedAcRawButtonLabel(it) }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val inline = compactExtensionsFit(listOf(labels), maxWidth, (availableHeight - commonHeight - 16.dp).coerceAtLeast(0.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (labels.isNotEmpty()) {
                                if (!inline) PanelTabs(selectedPage, onPageSelected)
                                if (inline || selectedPage == 1) acContent(AcLayoutPart.EXTENSIONS)
                            }
                        }
                    }
                }
                }
            })
        } else if (singleConfiguredKey && !(isLearningMode && profile.category == DeviceCategory.TV)) {
            TwoPaneLayout(primary = {
                PanelScroll("remote_primary", primaryScroll) { RemoteIdentity(profile, transmitterType) }
            }, secondary = {
                PanelScroll("remote_secondary", secondaryScroll) {
                    RemoteFunctionGrid(profile.buttons, onSendButton, onLearnButton, isLearningMode, onRepeatButton, onUnavailable = explain)
                }
            })
        } else {
            val paneProfile = if (isLearningMode && profile.category == DeviceCategory.TV) tvLearningProfile(profile) else profile
            val split = remotePaneButtons(paneProfile)
            TwoPaneLayout(primary = {
                PanelScroll("remote_primary", primaryScroll) {
                    if (split.primary.isEmpty()) RemoteIdentity(profile, transmitterType)
                    else PrimaryRemoteControls(paneProfile, split.primary, onSendButton, onLearnButton, isLearningMode, onRepeatButton, explain)
                }
            }, secondary = {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                val availableHeight = maxHeight
                PanelScroll("remote_secondary", secondaryScroll) {
                    if ((split.common + split.numbers + split.extensions).isEmpty()) RemoteIdentity(profile, transmitterType)
                    if (split.common.isNotEmpty()) {
                        Box(Modifier.fillMaxWidth().onSizeChanged { commonHeight = with(density) { it.height.toDp() } }) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = ExpressiveSurface.card,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                                    RemoteFunctionGrid(split.common, onSendButton, onLearnButton, isLearningMode, onRepeatButton, onUnavailable = explain)
                                }
                            }
                        }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val inline = compactExtensionsFit(listOf(split.extensions.map { localizedButtonLabel(it) }), maxWidth,
                            (availableHeight - commonHeight - 16.dp).coerceAtLeast(0.dp))
                        val tabs = split.extensions.isNotEmpty() && !inline && (split.common.isNotEmpty() || split.numbers.isNotEmpty())
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (tabs) PanelTabs(selectedPage, onPageSelected)
                            if (split.numbers.isNotEmpty() && (!tabs || selectedPage == 0)) {
                                val label = stringResource(if (showNumberPad) R.string.screen_collapse_numbers else R.string.screen_expand_numbers)
                                ActionTooltip(label) {
                                    FilledTonalIconToggleButton(checked = showNumberPad, onCheckedChange = onNumberPadChange, modifier = Modifier.size(48.dp), shapes = IconButtonDefaults.toggleableShapes()) {
                                        Icon(if (showNumberPad) Icons.Rounded.KeyboardHide else Icons.Rounded.Dialpad, contentDescription = label)
                                    }
                                }
                                if (showNumberPad) NumberGrid(split.numbers, onSendButton, onLearnButton, isLearningMode, onRepeatButton, explain)
                            }
                            if (!tabs || selectedPage == 1) {
                                RemoteFunctionGroups(split.extensions, onSendButton, onLearnButton, isLearningMode, onRepeatButton,
                                    preserveOrder = profile.panelLayout.order.isNotEmpty(), onUnavailable = explain)
                            }
                            if (profile.buttons.isEmpty()) Text(stringResource(R.string.screen_no_buttons))
                        }
                    }
                }
                }
            })
        }
    }
}

@Composable
private fun PanelScroll(tag: String, state: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().testTag(tag).verticalScroll(state).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp), content = content
    )
}

@Composable
private fun PanelTabs(selected: Int, onSelect: (Int) -> Unit) {
    ExpressivePageSwitcher(
        labels = listOf(stringResource(R.string.control_regular_page), stringResource(R.string.control_more_page)),
        selectedIndex = selected,
        onSelect = onSelect
    )
}

@Composable
private fun RemoteIdentity(profile: RemoteProfile, transmitterType: TransmitterType) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = ExpressiveSurface.card,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(profile.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth().semantics { heading() })
            if (profile.room.isNotBlank()) Text(profile.room, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
            val category = when (profile.category) {
                DeviceCategory.TV -> R.string.screen_category_tv
                DeviceCategory.AC -> R.string.screen_category_ac
                DeviceCategory.FAN -> R.string.screen_category_fan
                DeviceCategory.AUDIO -> R.string.screen_category_audio
                DeviceCategory.PROJECTOR -> R.string.screen_category_projector
                DeviceCategory.CUSTOM -> R.string.screen_category_custom
            }
            Text(stringResource(category), modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(transmitterType.displayName, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrimaryRemoteControls(profile: RemoteProfile, buttons: List<RemoteButton>, onSend: (RemoteButton) -> Unit,
    onLearn: (RemoteButton) -> Unit, learning: Boolean, onRepeat: (RemoteButton) -> Unit, onUnavailable: (RemoteButton) -> Unit) {
    val directions = if (profile.category == DeviceCategory.TV) tvStandardButtons(profile).dpad
    else genericDirectionButtons(buttons)
    val useDpad = directions.isNotEmpty() && (profile.category == DeviceCategory.TV || directions.keys.containsAll(DpadDirection.cardinal))
    val dpadKeys = if (useDpad) directions.values.map { it.id }.toSet() else emptySet()
    if (useDpad) {
        val dpadPlaceholders = DpadDirection.entries.associateWith {
            directions[it] ?: it.placeholderButton(stringResource(it.labelRes))
        }
        DpadController(
            onUp = { directions[DpadDirection.UP]?.let(onSend) },
            onDown = { directions[DpadDirection.DOWN]?.let(onSend) },
            onLeft = { directions[DpadDirection.LEFT]?.let(onSend) },
            onRight = { directions[DpadDirection.RIGHT]?.let(onSend) },
            onOk = { directions[DpadDirection.OK]?.let(onSend) },
            availableKeys = directions.filterValues { it.hasSignal }.keys,
            isLearningMode = learning,
            onRepeat = { direction -> directions[direction]?.let(onRepeat) },
            onLearn = { direction -> onLearn(dpadPlaceholders.getValue(direction)) },
            onUnavailable = { direction -> onUnavailable(dpadPlaceholders.getValue(direction)) },
            visibleKeys = if (learning && profile.category == DeviceCategory.TV) directions.keys else DpadDirection.entries.toSet()
        )
    }
    val remaining = buttons.filter { it.id !in dpadKeys }
    if (remaining.isNotEmpty()) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ExpressiveSurface.card,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                RemoteFunctionGrid(remaining, onSend, onLearn, learning, onRepeat, onUnavailable = onUnavailable)
            }
        }
    }
}

@Composable
private fun NumberGrid(buttons: List<RemoteButton>, onSend: (RemoteButton) -> Unit, onLearn: (RemoteButton) -> Unit,
    learning: Boolean, onRepeat: (RemoteButton) -> Unit, onUnavailable: (RemoteButton) -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = ExpressiveSurface.card,
        modifier = Modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
            val columns = ((maxWidth.value + 16) / 72).toInt().coerceIn(1, 3)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons.sortedBy { if (remoteButtonNumber(it) == 0) 10 else remoteButtonNumber(it) ?: 0 }.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                        row.forEach { button ->
                            RemoteButtonView(localizedButtonLabel(button), onClick = { onSend(button) }, modifier = Modifier.width(56.dp), diameter = 56.dp,
                                enabled = learning || button.hasSignal, isLearningMode = learning, onRepeat = { onRepeat(button) },
                                onLongClick = { onLearn(button) }, onUnavailable = { onUnavailable(button) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AcPanelPresets(profile: RemoteProfile, transmitting: Boolean, onResend: () -> Unit, onPreset: (AcSettings) -> Unit) {
    val labels = listOf(stringResource(R.string.screen_resend), stringResource(R.string.screen_cool_preset), stringResource(R.string.screen_heat_preset))
    val visible = (0..2).filter { "preset:$it" !in profile.panelLayout.hidden }.sortedBy { profile.panelLayout.rank("preset:$it") }
    if (visible.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = ExpressiveSurface.card,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            visible.forEach { index ->
                RemoteButtonView(labels[index], icon = when (index) { 0 -> Icons.Rounded.Refresh; 1 -> Icons.Rounded.AcUnit; else -> Icons.Rounded.WbSunny },
                    onClick = { when (index) { 0 -> onResend(); 1 -> onPreset(AcSettings(temperature = 26)); else -> onPreset(AcSettings(temperature = 24, mode = AcMode.HEAT)) } },
                    enabled = !transmitting, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 480, heightDp = 320)
@Preview(showBackground = true, widthDp = 891, heightDp = 411)
@Preview(showBackground = true, widthDp = 640, heightDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
@Composable
private fun LandscapeAcPanelPreview() {
    IrRemoteTheme(dynamicColor = false) {
        LandscapePanelPreviewContent(RemoteProfile("preview_ac", stringResource(R.string.screen_category_ac),
            category = DeviceCategory.AC, acProtocolId = 670))
    }
}

@Preview(showBackground = true, widthDp = 480, heightDp = 320)
@Preview(showBackground = true, widthDp = 891, heightDp = 411, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LandscapeTvPanelPreview() {
    val keys = listOf(
        "power" to ButtonKey.POWER,
        "tv_up" to ButtonKey.UP,
        "tv_down" to ButtonKey.DOWN,
        "tv_left" to ButtonKey.LEFT,
        "tv_right" to ButtonKey.RIGHT,
        "tv_ok" to ButtonKey.OK,
        "back" to ButtonKey.BACK,
        "vol_up" to ButtonKey.VOLUME_UP,
        "vol_down" to ButtonKey.VOLUME_DOWN
    ).map { (id, key) ->
        RemoteButton(id, stringResource(key.labelRes), builtinLabel = key.name, key = key, rawPattern = listOf(9000, 4500, 560))
    }
    IrRemoteTheme(dynamicColor = false) {
        LandscapePanelPreviewContent(RemoteProfile("preview_tv", stringResource(R.string.screen_category_tv), category = DeviceCategory.TV, buttons = keys))
    }
}

@Composable
private fun LandscapePanelPreviewContent(profile: RemoteProfile) {
    LandscapeRemotePanel(profile, AcSettings(), false, AcAction.entries.toSet(), emptyMap(), false, false, 0, {}, false, {},
        {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, { _, _ -> }, { emptyList() }, {}, {}, {}, TransmitterType.MOCK)
}
