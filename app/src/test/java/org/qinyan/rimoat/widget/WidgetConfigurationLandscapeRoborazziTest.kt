package org.qinyan.rimoat.widget

import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.IrRemoteApplication
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w640dp-h360dp-land-mdpi", application = IrRemoteApplication::class,
    shadows = [PickerMetadataShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class WidgetConfigurationLandscapeRoborazziTest {
    @get:Rule val composeRule = createComposeRule()
    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-port-mdpi")
    fun portraitConfigurationKeepsCancelAndSaveInBottomRow() {
        val app = RuntimeEnvironment.getApplication() as IrRemoteApplication
        val profile = RemoteProfile("portrait_widget_remote", "Living room television", category = DeviceCategory.TV,
            buttons = listOf(RemoteButton("power", "Power", iconName = "power", rawPattern = listOf(9000, 4500, 560))))
        val widgetId = 43
        runBlocking {
            app.repository.awaitLoaded()
            app.repository.saveRemote(profile)
            app.widgets.awaitReady()
            app.widgets.save(widgetId, WidgetConfiguration(WidgetKind.PANEL, shortcuts = listOf(WidgetShortcut(profile.id, "power"))))
        }
        try {
            composeRule.waitUntil(10_000) { shadowOf(Looper.getMainLooper()).idle(); app.widgets.state.value.commands[profile.id]?.isNotEmpty() == true }
            composeRule.setContent { IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                WidgetConfigurationScreen(app, widgetId, WidgetKind.PANEL, onCancel = {}, onSaved = {})
            } }
            composeRule.waitForIdle()
            val cancel = composeRule.onNodeWithText(app.getString(R.string.widget_cancel)).fetchSemanticsNode().boundsInRoot
            val save = composeRule.onNodeWithText(app.getString(R.string.widget_done)).fetchSemanticsNode().boundsInRoot
            assertTrue("Portrait actions stay beside each other", cancel.right < save.left)
            assertTrue("Portrait actions share the bottom row", kotlin.math.abs(cancel.center.y - save.center.y) < 1f)
            composeRule.onRoot().captureRoboImage(
                "src/test/screenshots/widget_configuration_portrait_actions.webp",
                roborazziOptions = options
            )
        } finally {
            app.transmitterManager.release()
            app.learnerManager.unregisterReceiver()
            runBlocking { app.applicationScope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

    @Test fun shortcutsReorderInConfigurationPaneAndSaveOnce() {
        val app = RuntimeEnvironment.getApplication() as IrRemoteApplication
        val profile = RemoteProfile("widget_remote", "Living room television", category = DeviceCategory.TV,
            buttons = listOf(
                RemoteButton("power", "Power", iconName = "power", rawPattern = listOf(9000, 4500, 560)),
                RemoteButton("input", "Input", rawPattern = listOf(9000, 4500, 560))))
        val widgetId = 42
        val power = WidgetShortcut(profile.id, "power")
        val input = WidgetShortcut(profile.id, "input")
        runBlocking {
            app.repository.awaitLoaded()
            app.repository.saveRemote(profile)
            app.widgets.awaitReady()
            app.widgets.save(widgetId, WidgetConfiguration(WidgetKind.PANEL, shortcuts = listOf(power, input)))
        }
        var saved = 0
        var cancelled = 0
        try {
            composeRule.waitUntil(10_000) { shadowOf(Looper.getMainLooper()).idle(); app.widgets.state.value.commands[profile.id]?.size == 2 }
            composeRule.setContent { IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                WidgetConfigurationScreen(app, widgetId, WidgetKind.PANEL, onCancel = { cancelled++ }, onSaved = { saved++ })
            } }
            val powerLabel = app.widgets.state.value.commands.getValue(profile.id).first { it.id == power.buttonId }.displayLabel(profile, app)
            assertEquals("Power", powerLabel)
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.waitForIdle()
            val configurationPane = composeRule.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag("two_pane_secondary")))
            configurationPane.performScrollToNode(hasText(powerLabel))
            composeRule.onNodeWithText(powerLabel).assertIsDisplayed()
            val left = composeRule.onNodeWithTag("two_pane_primary").fetchSemanticsNode().boundsInRoot
            val right = composeRule.onNodeWithTag("two_pane_secondary").fetchSemanticsNode().boundsInRoot
            assertTrue(left.right < right.left)
            composeRule.onRoot().captureRoboImage("src/test/screenshots/widget_configuration_landscape_before.webp", roborazziOptions = options)
            composeRule.onNodeWithContentDescription(app.getString(R.string.widget_move_down, powerLabel)).performScrollTo().performClick()
            assertEquals(listOf(power, input), app.widgets.store.configurations.value[widgetId]?.shortcuts)
            configurationPane.performScrollToNode(hasText(app.getString(R.string.widget_add_shortcut)))
            composeRule.onNodeWithText(app.getString(R.string.widget_add_shortcut)).performClick()
            val dialog = hasAnyAncestor(isDialog())
            val chooserLeft = composeRule.onNode(hasTestTag("two_pane_primary") and dialog).fetchSemanticsNode().boundsInRoot
            val chooserRight = composeRule.onNode(hasTestTag("two_pane_secondary") and dialog).fetchSemanticsNode().boundsInRoot
            assertTrue(chooserLeft.right < chooserRight.left)
            composeRule.onNode(isDialog()).captureRoboImage("src/test/screenshots/widget_configuration_landscape_chooser.webp", roborazziOptions = options)
            composeRule.onNode(hasText("Input") and dialog).performClick()
            composeRule.onNode(isDialog()).assertDoesNotExist()
            configurationPane.performScrollToNode(hasText(app.getString(R.string.widget_list)))
            composeRule.onNodeWithText(app.getString(R.string.widget_list)).performClick()
            configurationPane.performScrollToNode(hasText(app.getString(R.string.widget_done)))
            composeRule.onNodeWithText(app.getString(R.string.widget_done)).performClick()
            composeRule.waitUntil(10_000) { saved == 1 }
            assertEquals(listOf(input, power), app.widgets.store.configurations.value[widgetId]?.shortcuts)
            assertEquals(WidgetArrangement.LIST, app.widgets.store.configurations.value[widgetId]?.arrangement)
            assertEquals(0, cancelled)
            composeRule.onRoot().captureRoboImage("src/test/screenshots/widget_configuration_landscape_saved.webp", roborazziOptions = options)
        } finally {
            app.transmitterManager.release()
            app.learnerManager.unregisterReceiver()
            runBlocking { app.applicationScope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }
}
