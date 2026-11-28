package org.qinyan.rimoat.ui.components

import androidx.compose.ui.res.stringResource
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.localizedTimerDurationLabel
import org.qinyan.rimoat.i18n.localizedTimerTargetLabel
import org.qinyan.rimoat.i18n.rememberLocalizedDateTimeFormatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.SoundEffectOnInteraction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import org.qinyan.rimoat.ui.HapticHelper
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
import org.qinyan.rimoat.ui.theme.SilenceWindowClickSounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun timerPresets(options: List<Int>): List<Int> =
    listOf(30, 60, 120, 240, 480).filter { it in options }.take(4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimerDurationSlider(options: List<Int>, selected: Int, enabled: Boolean,
    onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val durationLabel = stringResource(R.string.control_timer_duration)
    val selectedDurationLabel = localizedTimerDurationLabel(options[selected], context)
    key(options.size) {
        val sliderState = rememberSliderState(
            value = selected.toFloat(), steps = options.size - 2,
            trackRange = 0f..options.lastIndex.toFloat()
        )
        sliderState.value = selected.toFloat()
        Slider(state = sliderState, onValueChange = { value ->
            val next = value.roundToInt().coerceIn(options.indices)
            if (next != selected) {
                onSelect(next)
                // One tick per changed stop, capped by the shared 60ms selection throttle.
                HapticHelper.performSelection(context = context)
            }
        }, modifier = modifier.semantics {
            contentDescription = durationLabel; stateDescription = selectedDurationLabel
        }, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AcTimerBottomSheet(options: List<Int>, currentMinutes: Int, powerOn: Boolean,
    onDismiss: () -> Unit, onSend: (Int) -> Unit,
    nowMillis: () -> Long = System::currentTimeMillis) {
    if (options.size < 2) return
    var selected by rememberSaveable(options, currentMinutes) {
        val target = currentMinutes.takeIf { it > 0 } ?: 60
        mutableIntStateOf(options.indices.minBy { abs(options[it] - target) })
    }
    val minutes = options[selected.coerceIn(options.indices)]
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val setDurationLabel = stringResource(R.string.control_set_duration)
    var sending by remember { mutableStateOf(false) }
    val timeFormatter = rememberLocalizedDateTimeFormatter(includeDate = false)
    val now by produceState(nowMillis()) {
        while (true) { delay(60_000 - nowMillis() % 60_000); value = nowMillis() }
    }
    fun select(index: Int) { selected = index; HapticHelper.performSelection(context = context) }
    fun send(value: Int) {
        if (sending) return
        sending = true
        HapticHelper.performRemoteClick(context = context)
        onSend(value)
        scope.launch { sheet.hide(); onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = ExpressiveSurface.canvas) {
    ProvideExpressiveCanvas {
    SilenceWindowClickSounds()
    SoundEffectOnInteraction(enabled = false) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (powerOn) stringResource(R.string.control_timer_off) else stringResource(R.string.control_timer_on), style = MaterialTheme.typography.titleLarge)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ExpressiveSurface.card,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (minutes == 0) stringResource(R.string.control_zero_minutes) else localizedTimerDurationLabel(minutes, context), style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { contentDescription = setDurationLabel })
                        Text(if (minutes == 0) "" else if (powerOn) stringResource(R.string.control_expected_off, localizedTimerTargetLabel(context, now, minutes, timeFormatter)) else stringResource(R.string.control_expected_on, localizedTimerTargetLabel(context, now, minutes, timeFormatter)),
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ActionIconButton(stringResource(R.string.control_shorten_timer), { select(selected - 1) }, enabled = selected > 0 && !sending) {
                            Icon(Icons.Rounded.Remove, stringResource(R.string.control_shorten_timer))
                        }
                        TimerDurationSlider(options, selected, !sending, { selected = it }, Modifier.weight(1f))
                        ActionIconButton(stringResource(R.string.control_extend_timer), { select(selected + 1) }, enabled = selected < options.lastIndex && !sending) {
                            Icon(Icons.Rounded.Add, stringResource(R.string.control_extend_timer))
                        }
                    }
                }
            }
            val presetShapes = ToggleButtonShapes(
                shape = CircleShape,
                pressedShape = RoundedCornerShape(14.dp),
                checkedShape = CircleShape
            )
            val presetColors = ToggleButtonDefaults.colors(
                containerColor = ExpressiveSurface.card,
                contentColor = MaterialTheme.colorScheme.onSurface,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary
            )
            FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                timerPresets(options).forEach { preset ->
                    ToggleButton(checked = minutes == preset,
                        onCheckedChange = { select(options.indexOf(preset)) }, enabled = !sending,
                        shapes = presetShapes,
                        colors = presetColors,
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            this.selected = minutes == preset
                            role = Role.RadioButton
                        }) {
                        Text(localizedTimerDurationLabel(preset, context))
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            val disableLabel = stringResource(R.string.control_disable_timer)
            val confirmLabel = if (minutes == 0) disableLabel else stringResource(R.string.control_set_timer)
            val showDisable = 0 in options && minutes > 0
            val density = LocalDensity.current
            val textMeasurer = rememberTextMeasurer()
            val style = MaterialTheme.typography.labelLarge
            val labelWidth = with(density) {
                (textMeasurer.measure(confirmLabel, style).size.width +
                    if (showDisable) textMeasurer.measure(disableLabel, style).size.width else 0).toDp()
            }
            val stacked = labelWidth + (if (showDisable) 104.dp else 48.dp) > maxWidth
            val disableAction: @Composable (Modifier) -> Unit = { buttonModifier ->
                OutlinedButton(
                    onClick = { send(0) },
                    enabled = !sending,
                    modifier = buttonModifier.heightIn(min = 52.dp),
                    shapes = ButtonDefaults.shapes()
                ) { Text(disableLabel) }
            }
            val confirmAction: @Composable (Modifier) -> Unit = { buttonModifier ->
                Button(
                    onClick = { send(minutes) },
                    modifier = buttonModifier.heightIn(min = 52.dp),
                    enabled = !sending,
                    shapes = ButtonDefaults.shapes()
                ) { Text(confirmLabel) }
            }
            if (stacked) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showDisable) disableAction(Modifier.fillMaxWidth())
                    confirmAction(Modifier.fillMaxWidth())
                }
            } else Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showDisable) disableAction(Modifier.weight(1f))
                confirmAction(Modifier.weight(1f))
            }
        }
    }
    }
    }
}

