package org.qinyan.rimoat.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** Silence controls owned by a new window, including the sheet's built-in drag handle. */
@Composable
internal fun SilenceWindowClickSounds() {
    val view = LocalView.current
    DisposableEffect(view) {
        val soundEffectsEnabled = view.isSoundEffectsEnabled
        view.isSoundEffectsEnabled = false
        onDispose { view.isSoundEffectsEnabled = soundEffectsEnabled }
    }
}
