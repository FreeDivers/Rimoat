@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.qinyan.rimoat.ui.screens

import androidx.compose.material3.ButtonDefaults
import org.qinyan.rimoat.i18n.localizedButtonLabel
import org.qinyan.rimoat.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import org.qinyan.rimoat.data.AcUnavailableReason
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.ui.motion.MotionAlertDialog
import org.qinyan.rimoat.data.RemoteButton

@Composable
internal fun RemoteUnavailableDialog(
    detail: UnavailableControl,
    onDismiss: () -> Unit,
    onLearn: (RemoteButton) -> Unit
) {
    val missingSignal = detail.reason == AcUnavailableReason.MISSING_SIGNAL
    val temperatureLimit = detail.reason == AcUnavailableReason.TEMPERATURE_MINIMUM ||
        detail.reason == AcUnavailableReason.TEMPERATURE_MAXIMUM
    MotionAlertDialog(onDismissRequest = onDismiss,
        title = { Text(localizedButtonLabel(detail.button)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (missingSignal) {
                Text(stringResource(R.string.control_reason_missing_signal_details))
            } else if (temperatureLimit) {
                Text(stringResource(detail.reason.messageResource()))
            } else {
                Text(stringResource(detail.reason.messageResource()))
                Text(stringResource(R.string.control_learning_help))
            }
        } },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically) {
                if (!temperatureLimit) {
                    TextButton(onClick = {
                        onDismiss()
                        onLearn(detail.button)
                    }, modifier = Modifier.weight(1f, fill = false), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(stringResource(R.string.control_enter_learning), textAlign = TextAlign.Center)
                    }
                }
                TextButton(onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.screen_close))
                }
            }
        })
}

internal fun AcUnavailableReason.messageResource(): Int = when (this) {
    AcUnavailableReason.MISSING_SIGNAL -> R.string.control_reason_missing_signal
    AcUnavailableReason.ENCODER_UNAVAILABLE -> R.string.control_reason_encoder
    AcUnavailableReason.STATE_RESTRICTED -> R.string.control_reason_state
    AcUnavailableReason.HARDWARE_UNAVAILABLE -> R.string.control_reason_hardware
    AcUnavailableReason.CAPABILITY_UNAVAILABLE -> R.string.control_reason_unknown
    AcUnavailableReason.TEMPERATURE_MINIMUM -> R.string.control_reason_temperature_minimum
    AcUnavailableReason.TEMPERATURE_MAXIMUM -> R.string.control_reason_temperature_maximum
}

internal data class UnavailableControl(val button: RemoteButton, val reason: AcUnavailableReason)
