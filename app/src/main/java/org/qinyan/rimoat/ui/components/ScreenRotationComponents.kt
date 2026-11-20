package org.qinyan.rimoat.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import org.qinyan.rimoat.IrRemoteApplication
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.ScreenRotationCondition
import org.qinyan.rimoat.data.ScreenRotationDirection
import org.qinyan.rimoat.data.ScreenRotationScope
import org.qinyan.rimoat.data.ThemePreferences
import org.qinyan.rimoat.data.isExternalIrActive
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.hardware.learning.LearnerType
import org.qinyan.rimoat.ui.HapticHelper
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import org.qinyan.rimoat.ui.motion.MotionAlertDialog

@Stable
data class ScreenRotationState(
    val isRotated: Boolean,
    val rotationDirection: ScreenRotationDirection,
    val onToggleRotation: () -> Unit
)

val LocalScreenRotationState = compositionLocalOf<ScreenRotationState?> { null }

@Composable
fun ScreenRotationHost(
    themePreferences: ThemePreferences,
    isTargetRotationScreen: Boolean,
    targetScreenKey: String,
    isExternalIrConnected: Boolean,
    content: @Composable () -> Unit
) {
    val rotationDirection by themePreferences.rotationDirection.collectAsStateWithLifecycle()
    val autoRotateScreen by themePreferences.autoRotateScreen.collectAsStateWithLifecycle()
    val rotationCondition by themePreferences.rotationCondition.collectAsStateWithLifecycle()
    val rotationScope by themePreferences.rotationScope.collectAsStateWithLifecycle()
    val suppressRotationPrompt by themePreferences.suppressRotationPrompt.collectAsStateWithLifecycle()

    var manualRotationOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var promptedSessionKey by rememberSaveable { mutableStateOf("") }
    var orientationApplied by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isTargetRotationScreen) {
        if (!isTargetRotationScreen) {
            manualRotationOverride = null
            promptedSessionKey = ""
        }
    }

    LaunchedEffect(isExternalIrConnected) {
        if (!isExternalIrConnected) {
            promptedSessionKey = ""
        }
    }

    val activeManualOverride = if (isTargetRotationScreen) manualRotationOverride else null
    val inScope = when (rotationScope) {
        ScreenRotationScope.ALL_SCREENS -> true
        ScreenRotationScope.REMOTE_AND_MATCHING -> isTargetRotationScreen
    }
    val conditionMet = when (rotationCondition) {
        ScreenRotationCondition.ALWAYS -> true
        ScreenRotationCondition.EXTERNAL_ONLY -> isExternalIrConnected
    }
    val autoRotateQualified = autoRotateScreen && inScope && conditionMet
    val isScreenRotated = activeManualOverride ?: autoRotateQualified

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val targetOrientation = if (isScreenRotated) {
        rotationDirection.activityOrientation
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    LaunchedEffect(activity, isScreenRotated, rotationDirection) {
        val act = activity ?: return@LaunchedEffect
        if (isScreenRotated) {
            orientationApplied = true
            if (act.requestedOrientation != targetOrientation) {
                act.requestedOrientation = targetOrientation
            }
        } else if (
            orientationApplied ||
            (act.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED &&
                act.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        ) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val currentPromptKey = if (isTargetRotationScreen && isExternalIrConnected) targetScreenKey else ""
    val shouldShowPrompt = isTargetRotationScreen &&
        isExternalIrConnected &&
        !autoRotateScreen &&
        !suppressRotationPrompt &&
        !isScreenRotated &&
        currentPromptKey.isNotEmpty() &&
        promptedSessionKey != currentPromptKey

    val rotationState = remember(isScreenRotated, rotationDirection, currentPromptKey) {
        ScreenRotationState(
            isRotated = isScreenRotated,
            rotationDirection = rotationDirection,
            onToggleRotation = {
                if (currentPromptKey.isNotEmpty()) {
                    promptedSessionKey = currentPromptKey
                }
                val nextRotated = !isScreenRotated
                manualRotationOverride = nextRotated
                activity?.requestedOrientation = if (nextRotated) {
                    rotationDirection.activityOrientation
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
        )
    }

    CompositionLocalProvider(LocalScreenRotationState provides rotationState) {
        content()
        if (shouldShowPrompt && LocalMotionActive.current) {
            ScreenRotationPromptDialog(
                onConfirmRotate = { dontAskAgain ->
                    if (dontAskAgain) {
                        themePreferences.setSuppressRotationPrompt(true)
                    }
                    promptedSessionKey = currentPromptKey
                    manualRotationOverride = true
                    orientationApplied = true
                    activity?.requestedOrientation = rotationDirection.activityOrientation
                },
                onDismissPrompt = { dontAskAgain ->
                    if (dontAskAgain) {
                        themePreferences.setSuppressRotationPrompt(true)
                    }
                    promptedSessionKey = currentPromptKey
                }
            )
        }
    }
}

/**
 * Ensures standalone screen compositions (e.g. in unit/Roborazzi tests without MainActivity)
 * still provide [LocalScreenRotationState] and external IR rotation prompts.
 */
@Composable
fun StandaloneScreenRotationScope(
    isTargetRotationScreen: Boolean,
    targetScreenKey: String,
    transmitterManager: IrTransmitterManager? = null,
    learnerManager: IrLearnerManager? = null,
    content: @Composable () -> Unit
) {
    if (LocalScreenRotationState.current != null) {
        content()
        return
    }
    val context = LocalContext.current
    val themePreferences = remember(context) {
        (context.applicationContext as? IrRemoteApplication)?.themePreferences
            ?: ThemePreferences(context)
    }
    val fallbackTypeFlow = remember { MutableStateFlow(TransmitterType.NONE) }
    val fallbackBoolFlow = remember { MutableStateFlow(false) }
    val fallbackLearnerTypeFlow = remember { MutableStateFlow(LearnerType.NONE) }

    val transmitterType by (transmitterManager?.transmitterType ?: fallbackTypeFlow).collectAsStateWithLifecycle()
    val isTransmitterReady by (transmitterManager?.isReady ?: fallbackBoolFlow).collectAsStateWithLifecycle()
    val isTransmitterMock by (transmitterManager?.forceMockMode ?: fallbackBoolFlow).collectAsStateWithLifecycle()

    val learnerType by (learnerManager?.learnerType ?: fallbackLearnerTypeFlow).collectAsStateWithLifecycle()
    val isLearnerAvailable by (learnerManager?.isAvailable ?: fallbackBoolFlow).collectAsStateWithLifecycle()
    val isLearnerMock by (learnerManager?.forceMockMode ?: fallbackBoolFlow).collectAsStateWithLifecycle()

    val isExternalConnected = isExternalIrActive(
        transmitterType = transmitterType,
        isTransmitterReady = isTransmitterReady,
        isTransmitterForceMock = isTransmitterMock,
        learnerType = learnerType,
        isLearnerAvailable = isLearnerAvailable,
        isLearnerForceMock = isLearnerMock
    )

    ScreenRotationHost(
        themePreferences = themePreferences,
        isTargetRotationScreen = isTargetRotationScreen,
        targetScreenKey = targetScreenKey,
        isExternalIrConnected = isExternalConnected,
        content = content
    )
}

@Composable
fun ScreenRotationIconButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val rotationState = LocalScreenRotationState.current ?: return
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val isRotated = rotationState.isRotated
    val label = stringResource(
        if (isRotated) R.string.screen_restore_orientation else R.string.screen_rotate_screen
    )
    ActionIconButton(
        label = label,
        onClick = {
            HapticHelper.performSelection(haptic, context)
            rotationState.onToggleRotation()
        },
        modifier = modifier,
        enabled = enabled
    ) {
        Icon(
            imageVector = Icons.Rounded.ScreenRotation,
            contentDescription = null,
            tint = if (isRotated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ScreenRotationPromptDialog(
    onConfirmRotate: (dontAskAgain: Boolean) -> Unit,
    onDismissPrompt: (dontAskAgain: Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var dontAskAgain by rememberSaveable { mutableStateOf(false) }

    MotionAlertDialog(
        onDismissRequest = { onDismissPrompt(dontAskAgain) },
        title = {
            Text(
                text = stringResource(R.string.rotation_prompt_title),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.rotation_prompt_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .toggleable(
                            value = dontAskAgain,
                            role = Role.Checkbox,
                            onValueChange = {
                                HapticHelper.performSelection(haptic, context)
                                dontAskAgain = it
                            }
                        )
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontAskAgain,
                        onCheckedChange = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.rotation_prompt_dont_ask_again),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    HapticHelper.performRemoteClick(haptic, context)
                    onConfirmRotate(dontAskAgain)
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.rotation_prompt_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    HapticHelper.performSelection(haptic, context)
                    onDismissPrompt(dontAskAgain)
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.rotation_prompt_cancel))
            }
        }
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
