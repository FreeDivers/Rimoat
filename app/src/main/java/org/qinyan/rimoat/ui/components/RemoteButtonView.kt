package org.qinyan.rimoat.ui.components

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.qinyan.rimoat.i18n.AppStrings
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.FilterCenterFocus
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Nature
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune

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
    onUnavailable: (() -> Unit)? = null
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
    val buttonModifier = Modifier.then(clickHaptic.modifier).width(diameter.coerceAtLeast(48.dp)).aspectRatio(1f)
        .graphicsLayer { scaleX = pressScale.value; scaleY = pressScale.value }
        .clearAndSetSemantics {
            role = Role.Button
            if (!enabled && onUnavailable == null) disabled()
            contentDescription = if (isLearningMode) AppStrings.get(R.string.control_learn, "学习%1\$s", accessibilityLabel) else accessibilityLabel
            if (enabled) onClick {
                clickHaptic()
                if (isLearningMode && onLongClick != null) onLongClick() else onClick()
                true
            }
            else if (onUnavailable != null) {
                onClick(context.getString(R.string.control_unavailable_action, accessibilityLabel)) { diagnose(); true }
                customActions = listOf(CustomAccessibilityAction(AppStrings.get(R.string.control_show_reason)) {
                    onUnavailable(); true
                })
            }
            currentState?.let { stateDescription = it }
            if (active != null && !isLearningMode && isToggle) {
                role = Role.Switch
                toggleableState = ToggleableState(active)
                if (currentState == null) stateDescription = AppStrings.get(if (active) R.string.control_set_on else R.string.control_set_off)
            }
            if (enabled && !isLearningMode && onSelectState != null) {
                onLongClick(AppStrings.get(R.string.control_select, "选择%1\$s", accessibilityLabel)) { onSelectState(); true }
            }
            if (enabled && !isLearningMode && onRepeat != null) {
                customActions = listOf(CustomAccessibilityAction(
                    AppStrings.get(R.string.control_repeat_action, "短时连发%1\$s", accessibilityLabel)
                ) { accessibilityRepeat() })
            }
        }
    val resolvedIcon = icon ?: getIconVector(iconName)
    val acceptsClick = enabled || onUnavailable != null
    // Short text-only keys (digits and OK) stay inside their circle. Longer names
    // get a neutral function icon and a label below, preserving the full accessible name.
    val textKey = resolvedIcon == null && (label.length == 1 || label == "OK")
    val content: @Composable RowScope.() -> Unit = {
        if (textKey) {
            Text(label, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
        } else {
            org.qinyan.rimoat.ui.motion.StateFade(resolvedIcon ?: Icons.Rounded.Tune, Modifier.size(28.dp)) {
                Icon(it, contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
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
            if (showSelectionEntry && onSelectState != null && enabled && !isLearningMode) {
                val selectionLabel = AppStrings.get(R.string.control_select_current, "%1\$s，选择%2\$s", label, accessibilityLabel)
                TextButton(onClick = onSelectState,
                    modifier = Modifier.padding(top = if (compact) 4.dp else 8.dp).heightIn(min = 48.dp).clearAndSetSemantics {
                        role = Role.Button
                        contentDescription = selectionLabel
                        onClick { onSelectState(); true }
                    }, contentPadding = PaddingValues(horizontal = 4.dp), shapes = ButtonDefaults.shapes()) {
                    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f, false), textAlign = TextAlign.Center)
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            } else Box(
                if (compact) Modifier.padding(top = 4.dp) else Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
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
    val spokenLabel = if (isLearningMode) AppStrings.get(R.string.control_learn, "学习%1\$s", label) else label
    val accessibleModifier = modifier.then(clickHaptic.modifier).size(56.dp).clearAndSetSemantics {
        contentDescription = spokenLabel
        role = Role.Button
        if (enabled) onClick { click(); true } else disabled()
    }
    ActionTooltip(if (isLearningMode) AppStrings.get(R.string.control_learn, "学习%1\$s", label) else label) {
    if (isLearningMode) OutlinedIconButton(onClick = click, enabled = enabled, modifier = accessibleModifier, shape = pressShape, interactionSource = interactionSource) {
        Icon(icon, contentDescription = null)
    } else FilledTonalIconButton(onClick = click, enabled = enabled, modifier = accessibleModifier, shape = pressShape, interactionSource = interactionSource) {
        Icon(icon, contentDescription = null)
    }
    }
    }
    }
}

fun getIconVector(name: String?): ImageVector? {
    if (name.isNullOrBlank()) return null
    return when (name.lowercase()) {
        "power", "power_settings_new" -> Icons.Rounded.PowerSettingsNew
        "volume_up" -> Icons.AutoMirrored.Rounded.VolumeUp
        "volume_down" -> Icons.AutoMirrored.Rounded.VolumeDown
        "volume_off", "mute" -> Icons.AutoMirrored.Rounded.VolumeOff
        "input", "source" -> Icons.AutoMirrored.Rounded.Input
        "arrow_back", "back" -> Icons.AutoMirrored.Rounded.ArrowBack
        "home" -> Icons.Rounded.Home
        "menu" -> Icons.Rounded.Menu
        "keyboard_arrow_up", "up" -> Icons.Rounded.KeyboardArrowUp
        "keyboard_arrow_down", "down" -> Icons.Rounded.KeyboardArrowDown
        "keyboard_arrow_left", "left" -> Icons.Rounded.KeyboardArrowLeft
        "keyboard_arrow_right", "right" -> Icons.Rounded.KeyboardArrowRight
        "arrow_drop_up", "ch_up" -> Icons.Rounded.ArrowDropUp
        "arrow_drop_down", "ch_down" -> Icons.Rounded.ArrowDropDown
        "tune", "mode" -> Icons.Rounded.Tune
        "air", "wind" -> Icons.Rounded.Air
        "speed" -> Icons.Rounded.Speed
        "timer" -> Icons.Rounded.Timer
        "sync_alt", "oscillation" -> Icons.Rounded.SyncAlt
        "swap_calls", "swing" -> Icons.Rounded.SwapCalls
        "nature" -> Icons.Rounded.Nature
        "center_focus_strong" -> Icons.Rounded.CenterFocusStrong
        "filter_center_focus" -> Icons.Rounded.FilterCenterFocus
        else -> null
    }
}
