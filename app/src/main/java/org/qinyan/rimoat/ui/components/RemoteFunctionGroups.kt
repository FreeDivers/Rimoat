package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.i18n.localizedButtonLabel
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.data.buttonKey
import org.qinyan.rimoat.ui.theme.ExpressiveSurface

internal fun remoteFunctionGroup(button: RemoteButton): FunctionGroup = when (button.acAction) {
    AcAction.LIGHT, AcAction.TEMPERATURE_DISPLAY -> FunctionGroup.DISPLAY
    AcAction.SLEEP, AcAction.ECO, AcAction.COMFORT, AcAction.QUIET, AcAction.TURBO -> FunctionGroup.OPERATING_MODE
    AcAction.TIMER -> FunctionGroup.TIMER
    AcAction.VENTILATION, AcAction.ION, AcAction.CLEAN, AcAction.DRY, AcAction.AUXILIARY_HEAT,
    AcAction.QUICK_COOL, AcAction.QUICK_HEAT -> FunctionGroup.AIR
    else -> (button.key ?: button.acAction?.buttonKey)?.group ?: FunctionGroup.OTHER
}

internal sealed interface FunctionCard {
    data class Single(val group: FunctionGroup, val buttons: List<RemoteButton>) : FunctionCard
    data class Merged(val groups: List<Pair<FunctionGroup, List<RemoteButton>>>) : FunctionCard
}

private const val SMALL_GROUP_THRESHOLD = 2

internal fun buildFunctionCards(buttons: List<RemoteButton>): List<FunctionCard> {
    if (buttons.isEmpty()) return emptyList()
    val grouped: List<Pair<FunctionGroup, List<RemoteButton>>> =
        buttons.groupByTo(LinkedHashMap()) { remoteFunctionGroup(it) }.entries.map { it.key to it.value }
    val isolatedIndices = mutableSetOf<Int>()
    var idx = 0
    while (idx < grouped.size) {
        if (grouped[idx].second.size <= SMALL_GROUP_THRESHOLD) {
            val start = idx
            while (idx < grouped.size && grouped[idx].second.size <= SMALL_GROUP_THRESHOLD) idx++
            if (idx - start == 1) isolatedIndices += start
        } else {
            idx++
        }
    }
    val hasStandaloneOther = grouped.any { it.first == FunctionGroup.OTHER && it.second.size > SMALL_GROUP_THRESHOLD }
    val mergeIsolatedIntoOther = isolatedIndices.isNotEmpty() && (hasStandaloneOther || isolatedIndices.size >= 2)
    val consolidated: List<Pair<FunctionGroup, List<RemoteButton>>> = if (!mergeIsolatedIntoOther) grouped else buildList {
        val absorbed = isolatedIndices.flatMap { grouped[it].second }
        val otherIndex = grouped.indexOfFirst { it.first == FunctionGroup.OTHER }
        val firstIsolated = isolatedIndices.min()
        grouped.forEachIndexed { index, pair ->
            when {
                index in isolatedIndices -> {
                    if (otherIndex < 0 && index == firstIsolated) {
                        add(FunctionGroup.OTHER to absorbed)
                    }
                }
                pair.first == FunctionGroup.OTHER -> {
                    add(FunctionGroup.OTHER to (pair.second + absorbed))
                }
                else -> add(pair)
            }
        }
    }
    return buildList {
        var cursor = 0
        while (cursor < consolidated.size) {
            if (consolidated[cursor].second.size <= SMALL_GROUP_THRESHOLD) {
                val start = cursor
                while (cursor < consolidated.size && consolidated[cursor].second.size <= SMALL_GROUP_THRESHOLD) cursor++
                val run = consolidated.subList(start, cursor)
                if (run.size >= 2) add(FunctionCard.Merged(run))
                else add(FunctionCard.Single(run.single().first, run.single().second))
            } else {
                val (group, keys) = consolidated[cursor++]
                add(FunctionCard.Single(group, keys))
            }
        }
    }
}

@Composable
fun RemoteFunctionGroups(buttons: List<RemoteButton>, onSend: (RemoteButton) -> Unit,
    onLearn: ((RemoteButton) -> Unit)?, learning: Boolean,
    onRepeat: ((RemoteButton) -> Unit)? = null, enabled: (RemoteButton) -> Boolean = { it.hasSignal },
    buttonContent: (@Composable RowScope.(RemoteButton) -> Unit)? = null,
    preserveOrder: Boolean = false, onUnavailable: ((RemoteButton) -> Unit)? = null,
    buttonLabel: (RemoteButton) -> String = { localizedButtonLabel(it) }) {
    if (buttons.isEmpty()) return
    val cards = remember(buttons, preserveOrder) { buildFunctionCards(buttons) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        cards.forEach { card ->
            when (card) {
                is FunctionCard.Single -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(card.group.labelRes),
                        modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = ExpressiveSurface.card,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                            RemoteFunctionGrid(card.buttons, onSend, onLearn, learning, onRepeat, enabled, buttonContent, onUnavailable, buttonLabel)
                        }
                    }
                }
                is FunctionCard.Merged -> Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = ExpressiveSurface.card,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
                        val allCardButtons = card.groups.flatMap { it.second }
                        val columns = extensionColumns(allCardButtons.map(buttonLabel), maxWidth)
                            .coerceAtMost(allCardButtons.size.coerceAtLeast(1))
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            card.groups.forEach { (group, keys) ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        stringResource(group.labelRes),
                                        modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    RemoteFunctionGridRows(
                                        keys, columns, onSend, onLearn, learning,
                                        onRepeat, enabled, buttonContent, onUnavailable, buttonLabel
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RemoteFunctionGrid(buttons: List<RemoteButton>, onSend: (RemoteButton) -> Unit,
    onLearn: ((RemoteButton) -> Unit)?, learning: Boolean, onRepeat: ((RemoteButton) -> Unit)? = null,
    enabled: (RemoteButton) -> Boolean = { it.hasSignal },
    buttonContent: (@Composable RowScope.(RemoteButton) -> Unit)? = null,
    onUnavailable: ((RemoteButton) -> Unit)? = null,
    buttonLabel: (RemoteButton) -> String = { localizedButtonLabel(it) }) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = extensionColumns(buttons.map(buttonLabel), maxWidth).coerceAtMost(buttons.size.coerceAtLeast(1))
        RemoteFunctionGridRows(buttons, columns, onSend, onLearn, learning, onRepeat, enabled, buttonContent, onUnavailable, buttonLabel)
    }
}

@Composable
private fun RemoteFunctionGridRows(
    buttons: List<RemoteButton>,
    columns: Int,
    onSend: (RemoteButton) -> Unit,
    onLearn: ((RemoteButton) -> Unit)?,
    learning: Boolean,
    onRepeat: ((RemoteButton) -> Unit)?,
    enabled: (RemoteButton) -> Boolean,
    buttonContent: (@Composable RowScope.(RemoteButton) -> Unit)?,
    onUnavailable: ((RemoteButton) -> Unit)?,
    buttonLabel: (RemoteButton) -> String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        buttons.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { button ->
                    val isPower = button.isPowerKey()
                    if (buttonContent != null) buttonContent(button) else
                    RemoteButtonView(buttonLabel(button), button.iconName, { onSend(button) }, modifier = Modifier.weight(1f),
                        onRepeat = onRepeat?.let { { it(button) } }, onLongClick = onLearn?.let { { it(button) } },
                        isLearningMode = learning, enabled = learning || enabled(button),
                        onUnavailable = onUnavailable?.let { explain -> { explain(button) } },
                        diameter = if (isPower) 72.dp else 64.dp,
                        variant = if (isPower) RemoteButtonVariant.POWER else RemoteButtonVariant.STANDARD,
                        buttonId = button.id, builtinLabel = button.builtinLabel, buttonKey = button.key)
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
