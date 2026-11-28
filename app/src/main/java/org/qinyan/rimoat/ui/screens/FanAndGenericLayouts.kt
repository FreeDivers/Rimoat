@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.ui.components.isPowerKey

import org.qinyan.rimoat.i18n.labelRes
import org.qinyan.rimoat.i18n.localizedButtonLabel
import org.qinyan.rimoat.R
import org.qinyan.rimoat.ui.components.remoteButtonNumber
import org.qinyan.rimoat.ui.components.remoteExtraButtons
import org.qinyan.rimoat.ui.components.RemoteFunctionGroups
import org.qinyan.rimoat.ui.components.RemoteFunctionGrid
import org.qinyan.rimoat.ui.theme.Spacing
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.ui.components.DpadController
import org.qinyan.rimoat.ui.components.RemoteButtonVariant
import org.qinyan.rimoat.ui.components.RemoteButtonView
import androidx.compose.foundation.shape.RoundedCornerShape
import org.qinyan.rimoat.ui.theme.ExpressiveSurface

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
