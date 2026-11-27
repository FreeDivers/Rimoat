package org.qinyan.rimoat.ui.components

import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.qinyan.rimoat.R
import org.qinyan.rimoat.ui.motion.AppMotion
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.SoundEffectOnInteraction
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.ui.HapticHelper
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import org.qinyan.rimoat.data.ButtonKey
import org.qinyan.rimoat.ui.theme.RemotePowerColors
import androidx.compose.ui.draw.clip

enum class RemoteButtonVariant { STANDARD, POWER, ACCENT, PILL, OUTLINED }

@Composable
fun RemoteButtonView(
    label: String,
    iconName: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null,
    isLearningMode: Boolean = false,
    variant: RemoteButtonVariant = RemoteButtonVariant.STANDARD,
    enabled: Boolean = true,
    diameter: Dp = 64.dp,
    icon: ImageVector? = null,
    accessibilityLabel: String = label,
    currentState: String? = null,
    active: Boolean? = null,
    onSelectState: (() -> Unit)? = null,
    isToggle: Boolean = active != null,
    showSelectionEntry: Boolean = false,
    onUnavailable: (() -> Unit)? = null,
    buttonId: String = "",
    builtinLabel: String? = null,
    buttonKey: ButtonKey? = null
) {
    SoundEffectOnInteraction(enabled = false) {
    MaterialTheme(motionScheme = MotionScheme.standard()) {
    val motion = AppMotion.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressShape = rememberExpressiveShape(interactionSource, CircleShape, RoundedCornerShape(40))
    val clickHaptic = org.qinyan.rimoat.ui.rememberPressHaptic(interactionSource, enabled, isLearningMode)
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = motion.fastSpatialSpec(), label = "RemotePress"
    )
    var holdCancelled by remember { mutableStateOf(false) }
    var repeated by remember { mutableStateOf(false) }
    var unavailableTaps by remember(accessibilityLabel, enabled) { mutableStateOf(0) }
    var lastUnavailableTap by remember(accessibilityLabel, enabled) { mutableStateOf(0L) }
    val diagnose: () -> Unit = {
        val now = android.os.SystemClock.uptimeMillis()
        unavailableTaps = if (now - lastUnavailableTap > 5000) 1 else unavailableTaps + 1
        lastUnavailableTap = now
        if (unavailableTaps >= 3) { unavailableTaps = 0; onUnavailable?.invoke() }
    }
    val latestRepeat by rememberUpdatedState(onRepeat)
    val latestSelectState by rememberUpdatedState(onSelectState)
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val sceneActive = org.qinyan.rimoat.ui.motion.LocalMotionActive.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle(
        minActiveState = androidx.lifecycle.Lifecycle.State.CREATED
    )
    LaunchedEffect(isPressed, enabled, isLearningMode, sceneActive, lifecycleState) {
        if (!isPressed) holdCancelled = false
        if (isPressed && (!sceneActive || !lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))) holdCancelled = true
        if (isPressed && !holdCancelled && sceneActive && lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            repeated = false
            if (enabled && !isLearningMode && latestSelectState != null) {
                delay(longPressTimeout)
                repeated = true
                HapticHelper.performSelection(haptic, context)
                latestSelectState?.invoke()
            } else if (enabled && !isLearningMode && latestRepeat != null) {
                delay(450)
                while (true) {
                    repeated = true
                    HapticHelper.performSelection(haptic, context)
                    latestRepeat?.invoke()
                    delay(180)
                }
            }
        }
    }
    val click: () -> Unit = {
        if (!sceneActive || !lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) || holdCancelled) {
            // A release after leaving the panel cannot send an old press.
        } else if (!enabled) {
            diagnose()
        } else if (isLearningMode && onLongClick != null) {
            clickHaptic()
            onLongClick()
        } else if (!repeated) {
            clickHaptic()
            onClick()
        }
        repeated = false
    }
    val accessibilityRepeat = rememberAccessibleRepeat(enabled && !isLearningMode, onRepeat)
    val spokenLabel = if (isLearningMode) stringResource(R.string.control_learn, accessibilityLabel) else accessibilityLabel
    val unavailableActionLabel = stringResource(R.string.control_unavailable_action, accessibilityLabel)
    val reasonActionLabel = stringResource(R.string.control_show_reason)
    val toggleStateLabel = stringResource(if (active == true) R.string.control_set_on else R.string.control_set_off)
    val selectActionLabel = stringResource(R.string.control_select, accessibilityLabel)
    val repeatActionLabel = stringResource(R.string.control_repeat_action, accessibilityLabel)
    val buttonModifier = Modifier.then(clickHaptic.modifier).width(diameter.coerceAtLeast(48.dp)).aspectRatio(1f)
        .graphicsLayer { scaleX = pressScale.value; scaleY = pressScale.value }
        .clearAndSetSemantics {
            role = Role.Button
            if (!enabled && onUnavailable == null) disabled()
            contentDescription = spokenLabel
            if (enabled) onClick {
                clickHaptic()
                if (isLearningMode && onLongClick != null) onLongClick() else onClick()
                true
            }
            else if (onUnavailable != null) {
                onClick(unavailableActionLabel) { diagnose(); true }
                customActions = listOf(CustomAccessibilityAction(reasonActionLabel) {
                    onUnavailable(); true
                })
            }
            currentState?.let { stateDescription = it }
            if (active != null && !isLearningMode && isToggle) {
                role = Role.Switch
                toggleableState = ToggleableState(active)
                if (currentState == null) stateDescription = toggleStateLabel
            }
            if (enabled && !isLearningMode && onSelectState != null) {
                onLongClick(selectActionLabel) { onSelectState(); true }
            }
            if (enabled && !isLearningMode && onRepeat != null) {
                customActions = listOf(CustomAccessibilityAction(
                    repeatActionLabel
                ) { accessibilityRepeat() })
            }
        }
    val resolvedIcon = remember(icon, iconName, buttonKey) {
        icon ?: resolveButtonIcon(buttonKey, iconName)
    }
    val acceptsClick = enabled || onUnavailable != null
    val textKey = resolvedIcon == null
    val content: @Composable RowScope.() -> Unit = {
        RemoteKeyGlyph(label, resolvedIcon, diameter)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // The touch target stays square while only the visible key shape responds to pressure.
        when {
            !enabled && onUnavailable != null -> FilledTonalButton(
                onClick = click, modifier = buttonModifier, enabled = acceptsClick, shape = pressShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                contentPadding = PaddingValues(0.dp), interactionSource = interactionSource, content = content
            )
            isLearningMode || variant == RemoteButtonVariant.OUTLINED -> OutlinedButton(
                onClick = click, modifier = buttonModifier, enabled = enabled, shape = pressShape,
                contentPadding = PaddingValues(0.dp), interactionSource = interactionSource, content = content
            )
            variant == RemoteButtonVariant.POWER -> Button(
                onClick = click, modifier = buttonModifier, enabled = enabled, shape = pressShape,
                colors = RemotePowerColors.buttonColors(),
                contentPadding = PaddingValues(0.dp), interactionSource = interactionSource, content = content
            )
            active == true -> Button(
                onClick = click, modifier = buttonModifier, enabled = enabled, shape = pressShape,
                contentPadding = PaddingValues(0.dp), interactionSource = interactionSource, content = content
            )
            else -> FilledTonalButton(
                onClick = click, modifier = buttonModifier, enabled = enabled, shape = pressShape,
                contentPadding = PaddingValues(0.dp), interactionSource = interactionSource, content = content
            )
        }
        if (!textKey && label.isNotEmpty()) {
            val compact = LocalCompactRemoteButtons.current
            val labelTopPadding = if (compact) 4.dp else 8.dp
            if (showSelectionEntry && onSelectState != null && enabled && !isLearningMode) {
                val selectionLabel = stringResource(R.string.control_select_current, label, accessibilityLabel)
                Box(
                    modifier = Modifier
                        .padding(top = labelTopPadding)
                        .heightIn(min = 48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onSelectState)
                        .clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = selectionLabel
                            onClick { onSelectState(); true }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f, fill = false),
                                textAlign = TextAlign.Center
                            )
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else Box(
                if (compact) Modifier.padding(top = 4.dp).heightIn(min = 48.dp) else Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
                contentAlignment = Alignment.Center
            ) { Text(
                label,
                modifier = Modifier
                    .clearAndSetSemantics {},
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = when { enabled -> MaterialTheme.colorScheme.onSurface
                    onUnavailable != null -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = .38f) },
                softWrap = true
            ) }
        }
    }
    }
    }
}

/** Icon-only remote controls retain the same haptic and learning behavior. */
@Composable
fun RemoteIconButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLearningMode: Boolean = false,
    onLearn: (() -> Unit)? = null
) {
    SoundEffectOnInteraction(enabled = false) {
    MaterialTheme(motionScheme = MotionScheme.standard()) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressShape = rememberExpressiveShape(interactionSource, CircleShape, RoundedCornerShape(40))
    val clickHaptic = org.qinyan.rimoat.ui.rememberPressHaptic(interactionSource, enabled, isLearningMode)
    val click: () -> Unit = {
        clickHaptic()
        if (isLearningMode) onLearn?.invoke() else onClick()
    }
    val spokenLabel = if (isLearningMode) stringResource(R.string.control_learn, label) else label
    val accessibleModifier = modifier.then(clickHaptic.modifier).size(56.dp).clearAndSetSemantics {
        contentDescription = spokenLabel
        role = Role.Button
        if (enabled) onClick { click(); true } else disabled()
    }
    ActionTooltip(spokenLabel) {
    if (isLearningMode) OutlinedIconButton(onClick = click, enabled = enabled, modifier = accessibleModifier, shape = pressShape, interactionSource = interactionSource) {
        Icon(icon, contentDescription = null)
    } else FilledTonalIconButton(onClick = click, enabled = enabled, modifier = accessibleModifier, shape = pressShape, interactionSource = interactionSource) {
        Icon(icon, contentDescription = null)
    }
    }
    }
    }
}
