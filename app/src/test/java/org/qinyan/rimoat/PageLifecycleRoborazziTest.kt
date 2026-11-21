package org.qinyan.rimoat

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-mdpi", application = IrRemoteApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class PageLifecycleRoborazziTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = RuntimeEnvironment.getApplication() as IrRemoteApplication
    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))

    private fun launch(destination: String, remoteId: String? = null): ActivityScenario<MainActivity> =
        ActivityScenario.launch(Intent(app, MainActivity::class.java).apply {
            action = "org.qinyan.rimoat.OPEN_WIDGET"
            putExtra("widget_destination", destination)
            putExtra("widget_remote_id", remoteId)
        })

    private fun awaitText(res: Int) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(app.getString(res)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.webp", roborazziOptions = options)
    }

    @Test fun systemBackLeavesWizardAndRemovesDraft() {
        launch("add").use { scenario ->
            awaitText(R.string.screen_choose_category)
            awaitText(R.string.screen_learn_physical)
            compose.onNodeWithText(app.getString(R.string.screen_learn_physical)).performClick()
            awaitText(R.string.screen_remote_name)
            compose.onNodeWithText(app.getString(R.string.screen_remote_name)).performTextInput("Living room remote")
            val drafts = app.filesDir.resolve("wizard-drafts")
            compose.waitUntil(10_000) { drafts.listFiles()?.any { it.extension == "json" } == true }
            capture("page_lifecycle_wizard_before_back")

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitText(R.string.screen_choose_category)
            compose.waitUntil(10_000) { drafts.listFiles().orEmpty().none { it.extension == "json" } }
            capture("page_lifecycle_wizard_after_back")
        }
    }

    @Test fun leavingRemoteClearsItsViewModelAndReopeningCreatesAnother() {
        val profile = RemoteProfile(id = "fixture-remote", name = "Living room TV")
        runBlocking { app.repository.awaitLoaded(); app.repository.saveRemote(profile) }
        val route = AppScreen.RemoteControl(profile.id)
        launch("remote", profile.id).use { scenario ->
            lateinit var pageStores: PageViewModelStores
            lateinit var first: RemoteControlViewModel
            compose.waitUntil(10_000) {
                var shown = false
                scenario.onActivity {
                    pageStores = ViewModelProvider(it)[PageViewModelStores::class.java]
                    pageStores.findOwner(route)?.let { owner ->
                        first = ViewModelProvider(owner)[RemoteControlViewModel::class.java]
                        shown = true
                    }
                }
                shown
            }
            scenario.onActivity { first.setLearningMode(true) }
            compose.waitForIdle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            scenario.onActivity { assertNull(pageStores.findOwner(route)) }
            assertFalse(first.viewModelScope.isActive)

            compose.onNodeWithText(profile.name).performClick()
            compose.waitForIdle()
            scenario.onActivity {
                val owner = checkNotNull(pageStores.findOwner(route))
                val reopened = ViewModelProvider(owner)[RemoteControlViewModel::class.java]
                assertNotSame(first, reopened)
                assertFalse(reopened.isLearningMode.value)
            }
            capture("page_lifecycle_remote_reopened")
        }
    }
}
