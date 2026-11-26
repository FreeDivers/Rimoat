package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.R
import org.qinyan.rimoat.ui.motion.AppMotion
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.SoundEffectOnInteraction
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.ui.HapticHelper
import org.qinyan.rimoat.ui.motion.LocalMotionActive

@Composable
fun DpadController(
    onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit, onOk: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 208.dp,
    isLearningMode: Boolean = false,
    onLearn: ((String) -> Unit)? = null,
    availableKeys: Set<String> = setOf("上", "下", "左", "右", "OK"),
    onRepeat: ((String) -> Unit)? = null,
    onUnavailable: ((String) -> Unit)? = null,
    visibleKeys: Set<String> = setOf("上", "下", "左", "右", "OK")
) {
    SoundEffectOnInteraction(enabled = false) {
    MaterialTheme(motionScheme = MotionScheme.standard()) {
    Surface(
        modifier = modifier.size(size.coerceAtLeast(196.dp)).semantics { isTraversalGroup = true },
        shape = CircleShape,
        color = ExpressiveSurface.card
    ) {
        Box(
            Modifier
                .padding(10.dp)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f), CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            listOf(
                Triple("上", Alignment.TopCenter, onUp), Triple("下", Alignment.BottomCenter, onDown),
                Triple("左", androidx.compose.ui.AbsoluteAlignment.CenterLeft, onLeft), Triple("右", androidx.compose.ui.AbsoluteAlignment.CenterRight, onRight),
                Triple("OK", Alignment.Center, onOk)
            ).filter { it.first in visibleKeys }.forEach { (label, alignment, send) ->
                DpadKey(
                    label = label,
                    onClick = send,
                    onRepeat = onRepeat?.let { { it(label) } },
                    enabled = label in availableKeys || (isLearningMode && onLearn != null),
                    isLearningMode = isLearningMode,
                    onLearn = { onLearn?.invoke(label) },
                    onUnavailable = onUnavailable?.let { explain -> { explain(label) } },
                    traversalOrder = when (label) { "上" -> 0f; "左" -> 1f; "右" -> 3f; "下" -> 4f; else -> 2f },
                    modifier = Modifier.align(alignment)
                )
            }
        }
    }
    }
    }
}

@Composable
private fun DpadKey(
    label: String,
    onClick: () -> Unit,
    onRepeat: (() -> Unit)?,
    enabled: Boolean,
    isLearningMode: Boolean,
    onLearn: () -> Unit,
    onUnavailable: (() -> Unit)?,
    traversalOrder: Float,
    modifier: Modifier
) {
    val motion = AppMotion.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val active = LocalMotionActive.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val interactionSource = remember { MutableInteractionSource() }
    val pressShape = rememberExpressiveShape(
        interactionSource,
        CircleShape,
        if (label == "OK") RoundedCornerShape(20.dp) else RoundedCornerShape(40)
    )
    val clickHaptic = org.qinyan.rimoat.ui.rememberPressHaptic(interactionSource, enabled, isLearningMode)
    val pressed by interactionSource.collectIsPressedAsState()
    var repeated by remember { mutableStateOf(false) }
    var unavailableTaps by remember(label, enabled) { mutableStateOf(0) }
    var lastUnavailableTap by remember(label, enabled) { mutableStateOf(0L) }
    val diagnose: () -> Unit = {
        if (active && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            val now = android.os.SystemClock.uptimeMillis()
            unavailableTaps = if (now - lastUnavailableTap > 5000) 1 else unavailableTaps + 1
            lastUnavailableTap = now
            if (unavailableTaps >= 3) { unavailableTaps = 0; onUnavailable?.invoke() }
        }
    }
    val acceptsClick = enabled || onUnavailable != null
    var holdCancelled by remember { mutableStateOf(false) }
    val repeatAction by rememberUpdatedState(onRepeat)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) holdCancelled = true
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active, enabled, isLearningMode) {
        if (!active || !enabled || isLearningMode) holdCancelled = true
    }
    LaunchedEffect(pressed) {
        if (pressed && active && enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            repeated = false
            holdCancelled = false
        }
    }
    LaunchedEffect(pressed, enabled, isLearningMode, active, holdCancelled) {
        if (pressed && active && !holdCancelled) {
            if (enabled && !isLearningMode && repeatAction != null) {
                delay(450)
                while (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    repeated = true
                    HapticHelper.performSelection(haptic, context)
                    repeatAction?.invoke()
                    delay(180)
                }
            }
        }
    }
    val scale = animateFloatAsState(if (pressed) 0.97f else 1f, motion.fastSpatialSpec(), label = "DpadPress")
    val click = {
        if (!enabled && onUnavailable != null) diagnose()
        if (active && enabled && (isLearningMode || !holdCancelled) && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if (isLearningMode) {
                clickHaptic()
                onLearn()
            } else if (!repeated) {
                clickHaptic()
                onClick()
            }
        }
        repeated = false
    }
    val accessibilityRepeat = rememberAccessibleRepeat(enabled && !isLearningMode, onRepeat)
    val keyModifier = Modifier.then(clickHaptic.modifier).size(if (label == "OK") 64.dp else 56.dp)
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .clearAndSetSemantics {
            role = Role.Button
            if (!enabled && onUnavailable == null) disabled()
            val spokenLabel = if (isLearningMode) AppStrings.get(R.string.control_learn, "学习%1\$s", localizedLabel(label)) else localizedLabel(label)
            contentDescription = spokenLabel
            traversalIndex = traversalOrder
            if (enabled) onClick {
                if (!active || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@onClick false
                clickHaptic()
                if (isLearningMode) onLearn() else onClick()
                true
            }
            if (!enabled && onUnavailable != null) {
                onClick(context.getString(R.string.control_unavailable_action, spokenLabel)) { diagnose(); true }
                customActions = listOf(CustomAccessibilityAction(AppStrings.get(R.string.control_show_reason)) {
                    if (!active || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) false
                    else { onUnavailable(); true }
                })
            }
            if (enabled && !isLearningMode && onRepeat != null) {
                customActions = listOf(CustomAccessibilityAction(
                    AppStrings.get(R.string.control_repeat_action, "短时连发%1\$s", spokenLabel)
                ) { accessibilityRepeat() })
            }
        }
    Box(modifier) {
    HoldTooltip(if (isLearningMode) AppStrings.get(R.string.control_learn, "学习%1\$s", localizedLabel(label)) else localizedLabel(label), pressed) {
    if (label == "OK") {
        val content: @Composable () -> Unit = {
            Text("OK", style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics {})
        }
        if (isLearningMode) OutlinedButton(
            onClick = click, enabled = acceptsClick, modifier = keyModifier,
            shape = pressShape, contentPadding = PaddingValues(0.dp), interactionSource = interactionSource
        ) { content() }
        else Button(
            colors = if (enabled) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)),
            onClick = click, enabled = acceptsClick, modifier = keyModifier,
            shape = pressShape, contentPadding = PaddingValues(0.dp), interactionSource = interactionSource
        ) { content() }
    } else {
        val content: @Composable () -> Unit = {
            Icon(requireNotNull(getIconVector(when (label) {
                "上" -> "keyboard_arrow_up"; "下" -> "keyboard_arrow_down"
                "左" -> "keyboard_arrow_left"; else -> "keyboard_arrow_right"
            })), contentDescription = null, modifier = Modifier.size(28.dp))
        }
        if (isLearningMode) OutlinedIconButton(
            onClick = click, enabled = acceptsClick, modifier = keyModifier,
            interactionSource = interactionSource, shape = pressShape, content = content
        )
        else IconButton(onClick = click, enabled = acceptsClick, modifier = keyModifier, interactionSource = interactionSource, shape = pressShape,
            colors = if (enabled) IconButtonDefaults.iconButtonColors() else IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)), content = content)
    }
    }
    }
}
