package org.qinyan.rimoat.ui.learning

import org.qinyan.rimoat.R

import org.qinyan.rimoat.ui.theme.Spacing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.qinyan.rimoat.hardware.IrSignal
import java.text.NumberFormat

/**
 * Canvas-based visualization of alternating Mark/Space IR microsecond pulse sequences,
 * accompanied by key metrics: pulse count, total duration, and carrier frequency.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IrWaveformPreview(
    pulses: List<Int>,
    frequencyHz: Int = 38000,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    baselineColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
) {
    if (pulses.isEmpty()) return

    val totalDurationUs = pulses.fold(0L) { acc, v -> acc + v.coerceAtLeast(0) }.coerceAtLeast(1L)
    val durationMs = totalDurationUs / 1000.0

    val waveformDescription = stringResource(R.string.screen_received_waveform)
    val locale = LocalConfiguration.current.locales[0]
    val durationText = remember(locale, durationMs) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
            isGroupingUsed = false
        }.format(durationMs)
    }
    val frequencyText = remember(locale, frequencyHz) {
        NumberFormat.getIntegerInstance(locale).apply {
            isGroupingUsed = false
        }.format(frequencyHz)
    }

    Column(modifier.fillMaxWidth()) {

            // Canvas drawing Mark / Space pulses
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .semantics { contentDescription = waveformDescription }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    val widthPx = size.width
                    val heightPx = size.height
                    val baselineY = heightPx * 0.85f
                    val highY = heightPx * 0.15f
                    val pulseHeight = baselineY - highY

                    // Draw baseline
                    drawLine(
                        color = baselineColor,
                        start = Offset(0f, baselineY),
                        end = Offset(widthPx, baselineY),
                        strokeWidth = 1.5f
                    )

                    var currentX = 0f
                    pulses.forEachIndexed { index, pulseUs ->
                        val sliceWidth = (pulseUs.coerceAtLeast(0).toFloat() / totalDurationUs.toFloat()) * widthPx
                        val isMark = (index % 2 == 0)

                        if (isMark) {
                            val markWidth = sliceWidth.coerceAtLeast(2f)
                            // Draw filled pulse block for Mark (carrier burst)
                            drawRect(
                                color = barColor,
                                topLeft = Offset(currentX, highY),
                                size = Size(markWidth, pulseHeight)
                            )
                        } else {
                            // Space: low level line along the baseline
                            drawLine(
                                // Space is signal data, so retain the same contrast as each mark.
                                color = barColor,
                                start = Offset(currentX, baselineY),
                                end = Offset(currentX + sliceWidth, baselineY),
                                strokeWidth = 1.5f
                            )
                        }
                        currentX += sliceWidth
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Let the measured waveform carry the accent; metadata stays quiet.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.large),
                verticalArrangement = Arrangement.spacedBy(Spacing.compact)
            ) {
                WaveformMetric(stringResource(R.string.screen_pulse_count, pulses.size))
                WaveformMetric(stringResource(R.string.screen_duration_ms, durationText))
                WaveformMetric(stringResource(R.string.screen_frequency_hz, frequencyText))
            }
    }
}

@Composable
fun IrWaveformPreview(
    signal: IrSignal.Raw,
    modifier: Modifier = Modifier
) {
    IrWaveformPreview(
        pulses = signal.patternMicros.toList(),
        frequencyHz = signal.frequencyHz,
        modifier = modifier
    )
}

@Composable
private fun WaveformMetric(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

