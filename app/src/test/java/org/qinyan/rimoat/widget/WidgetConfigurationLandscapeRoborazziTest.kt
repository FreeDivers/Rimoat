package org.qinyan.rimoat.widget

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.runner.RunWith
import org.qinyan.rimoat.IrRemoteApplication
import org.qinyan.rimoat.R
import org.qinyan.rimoat.testing.collectScreenshotDifference
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.AppColorMode
import org.qinyan.rimoat.data.ButtonKey
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
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
    @get:Rule val screenshotDifferences = ErrorCollector()
    @get:Rule val composeRule = createComposeRule()
    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-port-mdpi")
    fun selectingRemoteDefaultsToStablePowerKeyWithoutAnIcon() {
        val app = RuntimeEnvironment.getApplication() as IrRemoteApplication
        val profile = RemoteProfile("remote", "Living room television", category = DeviceCategory.TV,
            buttons = listOf(
                RemoteButton("input", "Input", rawPattern = listOf(9000, 4500, 560)),
                RemoteButton("custom", "Power", key = ButtonKey.POWER, rawPattern = listOf(9000, 4500, 560))))
        val widgetId = 44
        val initial = WidgetConfiguration(WidgetKind.SINGLE, profile.id, listOf(WidgetShortcut(profile.id, "input")))
        fun capturePreview(configuration: WidgetConfiguration, filename: String, label: String) = runBlocking {
            val snapshot = app.widgets.state.value.copy(dynamicColor = false, colorMode = AppColorMode.LIGHT, localeTags = "en")
            val host = Robolectric.buildActivity(Activity::class.java).setup()
            try {
                val parent = FrameLayout(host.get())
                host.get().setContentView(parent, ViewGroup.LayoutParams(250, 160))
                val views = composeConfigurationPreview(WidgetPreviewComposer.previewContext(host.get()),
                    configuration, snapshot, DpSize(250.dp, 160.dp))
                parent.addView(views.apply(host.get(), parent))
                parent.measure(View.MeasureSpec.makeMeasureSpec(250, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY))
                parent.layout(0, 0, 250, 160)
                fun descendants(view: View): List<View> = listOf(view) +
                    if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
                val children = descendants(parent)
                assertTrue(children.filterIsInstance<TextView>().any { it.text.toString() == profile.name })
                assertTrue(children.any { it.contentDescription?.toString() == "${profile.name} $label" && it.isEnabled })
                screenshotDifferences.collectScreenshotDifference {
                    parent.captureRoboImage("src/test/screenshots/$filename.webp", roborazziOptions = options)
                }
                assertEquals(250, parent.width)
                assertEquals(160, parent.height)
            } finally { host.pause().stop().destroy() }
        }
        var saved = 0
        try {
            runBlocking {
                app.repository.awaitLoaded()
                app.repository.saveRemote(profile)
                app.widgets.awaitReady()
                app.widgets.save(widgetId, initial)
            }
            composeRule.waitUntil(10_000) {
                shadowOf(Looper.getMainLooper()).idle()
                app.widgets.state.value.commands[profile.id]?.size == 2
            }
            val storedProfile = app.repository.remotes.value.first { it.id == profile.id }
            val storedPower = storedProfile.buttons.single { it.id == "custom" }
            assertEquals(ButtonKey.POWER, storedPower.key)
            assertNull(storedPower.iconName)
            assertEquals(WidgetShortcut(profile.id, "custom"),
                WidgetCommands.default(storedProfile.copy(buttons = storedProfile.buttons.map {
                    if (it.id == "custom") it.copy(label = "Primary switch") else it
                })))
            capturePreview(initial, "widget_power_selection_before", "Input")
            composeRule.setContent { IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                WidgetConfigurationScreen(app, widgetId, WidgetKind.SINGLE, onCancel = {}, onSaved = { saved++ })
            } }
            composeRule.onNodeWithText("Input").assertIsDisplayed()
            composeRule.onNodeWithText(profile.name).performClick()
            composeRule.onNode(hasText(profile.name) and hasAnyAncestor(isDialog())).performClick()
            composeRule.onNodeWithText("Power").assertIsDisplayed()
            assertEquals(initial, app.widgets.store.configurations.value[widgetId])
            composeRule.onNodeWithText(app.getString(R.string.widget_done)).performClick()
            composeRule.waitUntil(10_000) { saved == 1 }
            val configuration = app.widgets.store.configurations.value.getValue(widgetId)
            assertEquals(listOf(WidgetShortcut(profile.id, "custom")), configuration.shortcuts)
            assertEquals(storedProfile, app.repository.remotes.value.first { it.id == profile.id })
            capturePreview(configuration, "widget_power_selection_after", "Power")
        } finally {
            app.transmitterManager.release()
            app.learnerManager.unregisterReceiver()
            runBlocking { app.applicationScope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

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
            screenshotDifferences.collectScreenshotDifference {
                composeRule.onRoot().captureRoboImage(
                    "src/test/screenshots/widget_configuration_portrait_actions.webp",
                    roborazziOptions = options
                )
            }
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
            val configurationPaneMatcher = hasScrollAction() and hasAnyAncestor(hasTestTag("two_pane_secondary"))
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag("two_pane_primary").fetchSemanticsNodes().size == 1 &&
                    composeRule.onAllNodesWithTag("two_pane_secondary").fetchSemanticsNodes().size == 1 &&
                    composeRule.onAllNodes(configurationPaneMatcher).fetchSemanticsNodes().size == 1
            }
            val configurationPane = composeRule.onNode(configurationPaneMatcher)
            configurationPane.performScrollToIndex(0)
            composeRule.onNodeWithText(powerLabel).assertIsDisplayed()
            val left = composeRule.onNodeWithTag("two_pane_primary").fetchSemanticsNode().boundsInRoot
            val right = composeRule.onNodeWithTag("two_pane_secondary").fetchSemanticsNode().boundsInRoot
            assertTrue(left.right < right.left)
            screenshotDifferences.collectScreenshotDifference {
                composeRule.onRoot().captureRoboImage("src/test/screenshots/widget_configuration_landscape_before.webp", roborazziOptions = options)
            }
            composeRule.onNodeWithContentDescription(app.getString(R.string.widget_move_down, powerLabel)).performScrollTo().performClick()
            assertEquals(listOf(power, input), app.widgets.store.configurations.value[widgetId]?.shortcuts)
            configurationPane.performScrollToNode(hasText(app.getString(R.string.widget_add_shortcut)))
            composeRule.onNodeWithText(app.getString(R.string.widget_add_shortcut)).performClick()
            val dialog = hasAnyAncestor(isDialog())
            val chooserLeft = composeRule.onNode(hasTestTag("two_pane_primary") and dialog).fetchSemanticsNode().boundsInRoot
            val chooserRight = composeRule.onNode(hasTestTag("two_pane_secondary") and dialog).fetchSemanticsNode().boundsInRoot
            assertTrue(chooserLeft.right < chooserRight.left)
            screenshotDifferences.collectScreenshotDifference {
                composeRule.onNode(isDialog()).captureRoboImage("src/test/screenshots/widget_configuration_landscape_chooser.webp", roborazziOptions = options)
            }
            composeRule.onNode(hasText("Input") and dialog).performClick()
            composeRule.onNode(isDialog()).assertDoesNotExist()
            configurationPane.performScrollToNode(hasText(app.getString(R.string.widget_list)))
            composeRule.onNodeWithText(app.getString(R.string.widget_grid)).assertIsSelected()
            composeRule.onNodeWithText(app.getString(R.string.widget_list)).assertIsNotSelected().performClick().assertIsSelected()
            composeRule.onNodeWithText(app.getString(R.string.widget_grid)).assertIsNotSelected()
            configurationPane.performScrollToNode(hasText(app.getString(R.string.widget_done)))
            composeRule.onNodeWithText(app.getString(R.string.widget_done)).performClick()
            composeRule.waitUntil(10_000) { saved == 1 }
            assertEquals(listOf(input, power), app.widgets.store.configurations.value[widgetId]?.shortcuts)
            assertEquals(WidgetArrangement.LIST, app.widgets.store.configurations.value[widgetId]?.arrangement)
            assertEquals(0, cancelled)
            screenshotDifferences.collectScreenshotDifference {
                composeRule.onRoot().captureRoboImage("src/test/screenshots/widget_configuration_landscape_saved.webp", roborazziOptions = options)
            }
        } finally {
            app.transmitterManager.release()
            app.learnerManager.unregisterReceiver()
            runBlocking { app.applicationScope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

    @Test fun wideStatusShortcutChooserKeepsBoundRemoteValidAndSaves() {
        val app = RuntimeEnvironment.getApplication() as IrRemoteApplication
        val tv = RemoteProfile("tv_remote", "Living room television", category = DeviceCategory.TV,
            buttons = listOf(
                RemoteButton("power", "Power", iconName = "power", rawPattern = listOf(9000, 4500, 560)),
                RemoteButton("mute", "Mute", iconName = "volume_off", rawPattern = listOf(9000, 4500, 560))))
        val fan = RemoteProfile("fan_remote", "Bedroom fan", category = DeviceCategory.FAN,
            buttons = listOf(
                RemoteButton("speed", "Speed", rawPattern = listOf(9000, 4500, 560))))
        val widgetId = 45
        val initial = WidgetConfiguration(WidgetKind.STATUS, tv.id, listOf(WidgetShortcut(tv.id, "power")))
        runBlocking {
            app.repository.awaitLoaded()
            app.repository.saveRemote(tv)
            app.repository.saveRemote(fan)
            app.widgets.awaitReady()
            app.widgets.save(widgetId, initial)
        }
        var saved = 0
        try {
            composeRule.waitUntil(10_000) {
                shadowOf(Looper.getMainLooper()).idle()
                app.widgets.state.value.commands[tv.id]?.size == 2 &&
                    app.widgets.state.value.commands[fan.id]?.size == 1
            }
            composeRule.setContent { IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                WidgetConfigurationScreen(app, widgetId, WidgetKind.STATUS, onCancel = {}, onSaved = { saved++ })
            } }
            shadowOf(Looper.getMainLooper()).idle()
            composeRule.waitForIdle()
            val configurationPane = composeRule.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag("two_pane_secondary")))
            configurationPane.performScrollToNode(hasText(app.getString(R.string.widget_add_shortcut)))
            composeRule.onNodeWithText(app.getString(R.string.widget_add_shortcut)).performClick()
            val dialog = hasAnyAncestor(isDialog())
            // Status widgets are bound to one remote: switching to another remote inside
            // "Add shortcut" must not be allowed to mix foreign shortcuts into the draft.
            composeRule.onNode(hasText(fan.name) and dialog).assertDoesNotExist()
            composeRule.onNode(hasText("Mute") and dialog).assertIsDisplayed()
            screenshotDifferences.collectScreenshotDifference {
                composeRule.onNode(isDialog()).captureRoboImage(
                    "src/test/screenshots/widget_configuration_landscape_status_chooser.webp",
                    roborazziOptions = options
                )
            }
            composeRule.onNode(hasText("Mute") and dialog).performClick()
            composeRule.onNode(isDialog()).assertDoesNotExist()
            configurationPane.performScrollToNode(hasText(app.getString(R.string.widget_done)))
            composeRule.onNodeWithText(app.getString(R.string.widget_done)).performClick()
            composeRule.waitUntil(10_000) { saved == 1 }
            val stored = app.widgets.store.configurations.value.getValue(widgetId)
            assertEquals(tv.id, stored.remoteId)
            assertEquals(listOf(WidgetShortcut(tv.id, "power"), WidgetShortcut(tv.id, "mute")), stored.shortcuts)
        } finally {
            app.transmitterManager.release()
            app.learnerManager.unregisterReceiver()
            runBlocking { app.applicationScope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }
}
