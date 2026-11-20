package org.qinyan.rimoat.ui.components

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.qinyan.rimoat.ui.motion.LocalMotionActive

/** A short, bounded burst through the existing busy-aware repeat pipeline. Never queues bursts. */
@Composable
internal fun rememberAccessibleRepeat(enabled: Boolean, action: (() -> Unit)?): () -> Boolean {
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val active = LocalMotionActive.current
    val latestAction by rememberUpdatedState(action)
    val latestEnabled by rememberUpdatedState(enabled && active)
    var job by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) job?.cancel()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            job?.cancel()
        }
    }
    LaunchedEffect(enabled, active) { if (!enabled || !active) job?.cancel() }
    return {
        if (!latestEnabled || latestAction == null || job?.isActive == true ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) false
        else {
            job = scope.launch {
                repeat(3) {
                    if (!latestEnabled || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
                    latestAction?.invoke()
                    delay(300)
                }
            }
            true
        }
    }
}
