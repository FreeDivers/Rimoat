@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import androidx.compose.material3.IconButtonDefaults
import org.qinyan.rimoat.i18n.labelRes
import org.qinyan.rimoat.R
import org.qinyan.rimoat.ui.components.panelKey
import org.qinyan.rimoat.ui.components.remoteButtonNumber
import org.qinyan.rimoat.ui.components.remoteExtraButtons
import org.qinyan.rimoat.ui.components.tvStandardButtons
import org.qinyan.rimoat.ui.components.RemoteFunctionGroups
import org.qinyan.rimoat.ui.components.RemoteFunctionGrid
import org.qinyan.rimoat.ui.components.ActionTooltip
import androidx.compose.foundation.layout.widthIn
import org.qinyan.rimoat.ui.theme.Spacing
import org.qinyan.rimoat.ui.motion.ExpandSection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.ui.components.DpadController
import org.qinyan.rimoat.ui.components.RemoteButtonView
import androidx.compose.foundation.shape.RoundedCornerShape
import org.qinyan.rimoat.ui.theme.ExpressiveSurface

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
