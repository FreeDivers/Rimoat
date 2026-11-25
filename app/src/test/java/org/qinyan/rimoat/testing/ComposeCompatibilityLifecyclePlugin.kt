package org.qinyan.rimoat.testing

import org.qinyan.rimoat.ui.ComposeCompatibility
import org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin

class ComposeCompatibilityLifecyclePlugin : TestEnvironmentLifecyclePlugin {
    override fun onSetupApplicationState() {
        ComposeCompatibility.initialize()
    }
}
