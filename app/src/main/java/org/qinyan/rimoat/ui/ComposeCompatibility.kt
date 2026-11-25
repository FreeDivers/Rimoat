package org.qinyan.rimoat.ui

import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi

object ComposeCompatibility {
    @OptIn(ExperimentalComposeUiApi::class)
    fun initialize() {
        // Glance vector rendering has no Compose UI owner; keep painter caches independent.
        ComposeUiFlags.isVectorDrawCacheSharingEnabled = false
    }
}
