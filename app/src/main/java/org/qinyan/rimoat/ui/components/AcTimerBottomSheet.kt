package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.i18n.localizedTimerDurationLabel
import org.qinyan.rimoat.i18n.localizedTimerTargetLabel
import org.qinyan.rimoat.i18n.rememberLocalizedDateTimeFormatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.data.timerDurationLabel
import org.qinyan.rimoat.ui.HapticHelper
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
    Slider(selected.toFloat(), { value ->
        val next = value.roundToInt().coerceIn(options.indices)
        if (next != selected) {
            onSelect(next)
            // One tick per changed stop, capped by the shared 60ms selection throttle.
            HapticHelper.performSelection(context = context)
        }
    }, modifier = modifier.semantics {
        contentDescription = AppStrings.get(R.string.control_timer_duration, "定时时长"); stateDescription = localizedTimerDurationLabel(options[selected])
    }, enabled = enabled, valueRange = 0f..options.lastIndex.toFloat(), steps = options.size - 2)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AcTimerBottomSheet(options: List<Int>, currentMinutes: Int, powerOn: Boolean,
    onDismiss: () -> Unit, onSend: (Int) -> Unit) {
    if (options.size < 2) return
    var selected by rememberSaveable(options, currentMinutes) {
        val target = currentMinutes.takeIf { it > 0 } ?: 60
        mutableIntStateOf(options.indices.minBy { abs(options[it] - target) })
    }
    val minutes = options[selected.coerceIn(options.indices)]
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sending by remember { mutableStateOf(false) }
    val timeFormatter = rememberLocalizedDateTimeFormatter(includeDate = false)
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(60_000 - System.currentTimeMillis() % 60_000); value = System.currentTimeMillis() }
    }
    fun select(index: Int) { selected = index; HapticHelper.performSelection(context = context) }
    fun send(value: Int) {
        if (sending) return
        sending = true
        HapticHelper.performRemoteClick(context = context)
        onSend(value)
        scope.launch { sheet.hide(); onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (powerOn) AppStrings.get(R.string.control_timer_off, "定时关机") else AppStrings.get(R.string.control_timer_on, "定时开机"), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (minutes == 0) AppStrings.get(R.string.control_zero_minutes, "0 分钟") else localizedTimerDurationLabel(minutes), style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { contentDescription = AppStrings.get(R.string.control_set_duration, "设定时长") })
                Text(if (minutes == 0) "" else if (powerOn) AppStrings.get(R.string.control_expected_off, "预计关机 %1\$s", localizedTimerTargetLabel(context, now, minutes, timeFormatter)) else AppStrings.get(R.string.control_expected_on, "预计开机 %1\$s", localizedTimerTargetLabel(context, now, minutes, timeFormatter)),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ActionIconButton(AppStrings.get(R.string.control_shorten_timer, "缩短定时"), { select(selected - 1) }, enabled = selected > 0 && !sending) {
                    Icon(Icons.Rounded.Remove, AppStrings.get(R.string.control_shorten_timer, "缩短定时"))
                }
                TimerDurationSlider(options, selected, !sending, { selected = it }, Modifier.weight(1f))
                ActionIconButton(AppStrings.get(R.string.control_extend_timer, "延长定时"), { select(selected + 1) }, enabled = selected < options.lastIndex && !sending) {
                    Icon(Icons.Rounded.Add, AppStrings.get(R.string.control_extend_timer, "延长定时"))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                timerPresets(options).forEach { preset ->
                    FilterChip(selected = minutes == preset, onClick = { select(options.indexOf(preset)) },
                        enabled = !sending, label = { Text(localizedTimerDurationLabel(preset)) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (0 in options && minutes > 0) TextButton({ send(0) }, enabled = !sending) { Text(AppStrings.get(R.string.control_disable_timer, "关闭定时")) }
            Button({ send(minutes) }, Modifier.weight(1f).heightIn(min = 48.dp), enabled = !sending) {
                Text(if (minutes == 0) AppStrings.get(R.string.control_disable_timer, "关闭定时") else AppStrings.get(R.string.control_set_timer, "设置定时"))
            }
        }
    }
}

