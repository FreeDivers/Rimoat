package org.qinyan.rimoat.ui.motion

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.LocalExpressiveCanvas

/** Previewed and outgoing pages must not register back handlers or perform visible-page effects. */
val LocalMotionActive = compositionLocalOf { true }

private class Scene<T>(val from: T, val to: T, val back: Boolean, val direction: Float) {
    val progress = Animatable(0f)
    var reverting = false
}

/**
 * Opaque pages move in graphics layers: progress never invalidates composition or measurement.
 * Retains the outgoing snapshot and keyed scroll/form state, including on a cancelled gesture.
 * Only a completed gesture calls onBack; previews never mutate the navigation or ViewModel.
 */
@Composable
fun <T : Any> MotionContent(
    targetState: T,
    contentKey: (T) -> String,
    isBack: (T, T) -> Boolean,
    modifier: Modifier = Modifier,
    backTarget: T? = null,
    onBack: () -> Unit = {},
    retain: (T) -> Boolean = { false },
    discardSavedStateOnExit: (T) -> Boolean = { false },
    content: @Composable (T) -> Unit
) {
    val motion = AppMotion.current
    val active = LocalMotionActive.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val latestTarget by rememberUpdatedState(targetState)
    val latestBack by rememberUpdatedState(backTarget)
    val latestOnBack by rememberUpdatedState(onBack)
    val savedState = rememberSaveableStateHolder()
    var settled by remember { mutableStateOf(targetState) }
    var scene by remember { mutableStateOf<Scene<T>?>(null) }
    var pendingBack by remember { mutableStateOf<Scene<T>?>(null) }
    var retained by remember { mutableStateOf<List<T>>(emptyList()) }
    val targetKey = contentKey(targetState)

    SideEffect {
        if (scene == null && contentKey(settled) == targetKey) settled = targetState
        if (retain(targetState) && retained.none { contentKey(it) == targetKey }) {
            retained = retained + targetState
        }
    }

    LaunchedEffect(targetKey, active) {
        val pending = pendingBack
        if (pending != null && scene === pending && !pending.reverting && contentKey(pending.to) == targetKey) {
            settled = latestTarget
            scene = null
            pendingBack = null
            return@LaunchedEffect
        }
        if (pending != null) pendingBack = null
        if (contentKey(settled) != targetKey) {
            if (!active) {
                settled = latestTarget
                scene = null
                return@LaunchedEffect
            }
            // External navigation supersedes a preview. Old gesture cleanup checks scene identity.
            val origin = if (pending != null && scene === pending && !pending.reverting) pending.to else settled
            settled = origin
            val next = Scene(origin, latestTarget, isBack(origin, latestTarget), direction)
            scene = next
            try {
                next.progress.animateTo(1f, motion.defaultSpatialSpec())
                if (scene === next) settled = latestTarget
            } finally {
                if (scene === next) scene = null
            }
        } else {
            // A programmatic reversal can return to the settled page before the
            // previous transition completes. Its cancelled effect cannot clear scene.
            settled = latestTarget
            scene = null
        }
    }

    LaunchedEffect(pendingBack) {
        val pending = pendingBack ?: return@LaunchedEffect
        // Wait two frames so synchronous or flow-collected state updates from onBack() recompose first.
        withFrameNanos { }
        withFrameNanos { }
        if (scene !== pending || pendingBack !== pending) return@LaunchedEffect
        if (!active || contentKey(latestTarget) == contentKey(pending.to)) {
            if (contentKey(latestTarget) == contentKey(pending.to)) settled = latestTarget
            scene = null
            pendingBack = null
            return@LaunchedEffect
        }
        pending.reverting = true
        try {
            pending.progress.animateTo(0f, motion.fastSpatialSpec())
        } finally {
            if (scene === pending) scene = null
            if (pendingBack === pending) pendingBack = null
        }
    }

    // Register before child content so a step, dialog or sheet can take precedence.
    PredictiveBackHandler(enabled = active && backTarget != null) { events ->
        val destination = latestBack
        if (scene != null || destination == null) {
            events.collect { }
        } else {
            val origin = latestTarget
            var preview: Scene<T>? = null
            try {
                events.collect { event ->
                    if (preview == null) {
                        preview = Scene(origin, destination, true,
                            if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f)
                        scene = preview
                    }
                    if (scene === preview) preview?.progress?.snapTo(event.progress.coerceIn(0f, 1f))
                }
                val stillValid = contentKey(latestTarget) == contentKey(origin) &&
                    latestBack?.let(contentKey) == contentKey(destination)
                if (preview == null) {
                    // Three-button navigation and toolbar back use the same ordinary transition.
                    if (stillValid) latestOnBack()
                } else if (scene === preview) {
                    val progress = checkNotNull(preview).progress
                    if (stillValid) {
                        progress.animateTo(1f, motion.defaultSpatialSpec())
                        if (scene === preview && contentKey(latestTarget) == contentKey(origin) &&
                            latestBack?.let(contentKey) == contentKey(destination)
                        ) {
                            pendingBack = preview
                            latestOnBack()
                        } else if (scene === preview) {
                            progress.animateTo(0f, motion.fastSpatialSpec())
                            if (scene === preview) scene = null
                        }
                    } else {
                        progress.animateTo(0f, motion.fastSpatialSpec())
                        if (scene === preview) scene = null
                    }
                }
            } catch (cancelled: CancellationException) {
                val cancelledScene = preview
                if (cancelledScene != null && scene === cancelledScene && pendingBack !== cancelledScene) {
                    withContext(NonCancellable) {
                        cancelledScene.progress.animateTo(0f, motion.fastSpatialSpec())
                    }
                }
                throw cancelled
            } finally {
                if (preview != null && scene === preview && pendingBack !== preview) scene = null
            }
        }
    }

    val currentScene = scene
    val visiblePages = if (currentScene == null) listOf(settled) else listOf(currentScene.from, currentScene.to)
    val pages = (retained + visiblePages).distinctBy(contentKey)
    Box(modifier.fillMaxSize().clipToBounds()) {
        for (page in pages) {
            val pageKey = contentKey(page)
            val visible = visiblePages.any { contentKey(it) == pageKey }
            val outgoing = currentScene != null && pageKey == contentKey(currentScene.from)
            val interactive = active && visible && currentScene == null && pageKey == targetKey
            key(pageKey) {
                var pageHasFocus by remember { mutableStateOf(false) }
                val isPageTarget = pageKey == targetKey
                LaunchedEffect(isPageTarget, pageHasFocus) {
                    if (!isPageTarget && pageHasFocus) {
                        keyboardController?.hide()
                        focusManager.clearFocus(force = true)
                    }
                }
                if (discardSavedStateOnExit(page)) {
                    DisposableEffect(pageKey) {
                        onDispose {
                            if (contentKey(latestTarget) != pageKey) savedState.removeState(pageKey)
                        }
                    }
                }
                Box(
                    motionPageModifier(visible, outgoing, interactive, currentScene) {
                        pageHasFocus = it
                    }
                ) {
                    savedState.SaveableStateProvider(pageKey) {
                        CompositionLocalProvider(LocalMotionActive provides interactive) {
                            content(if (pageKey == targetKey) targetState else page)
                        }
                    }
                }
            }
        }
        if (currentScene != null) {
            // Prevent a second click on either moving page; predictive back itself is system input.
            Box(Modifier.fillMaxSize().zIndex(2f).pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            })
        }
    }
}

/**
 * Keep conditional modifier composition outside the saved page's composition path. In Compose 1.8,
 * inlining it at the Box call changed nested saveable keys between an entering and a settled page.
 */
@Composable
private fun motionPageModifier(
    visible: Boolean,
    outgoing: Boolean,
    interactive: Boolean,
    scene: Scene<*>?,
    onFocusChanged: (Boolean) -> Unit
): Modifier = Modifier.fillMaxSize()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            if (visible) placeable.place(0, 0)
        }
    }
    .zIndex(if (!visible) -1f else if (scene != null && outgoing == scene.back) 1f else 0f)
    .graphicsLayer {
        translationX = if (scene == null) 0f else pageOffset(
            scene.progress.value, outgoing, scene.back, scene.direction
        ) * size.width
    }
    .background(if (LocalExpressiveCanvas.current) ExpressiveSurface.canvas else MaterialTheme.colorScheme.background)
    .focusProperties { canFocus = visible && !outgoing }
    .onFocusChanged { onFocusChanged(it.hasFocus) }
    .then(if (interactive) Modifier else Modifier.clearAndSetSemantics { })

internal fun pageOffset(progress: Float, outgoing: Boolean, back: Boolean, direction: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return direction * when {
        back && outgoing -> p
        back -> (p - 1f) * 0.25f
        outgoing -> -p * 0.25f
        else -> 1f - p
    }
}
