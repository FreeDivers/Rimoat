package org.qinyan.rimoat.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemotePanelLayout
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class RemotePanelEditorRoborazziTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))

    private fun key(id: String, label: String, icon: String? = null) =
        RemoteButton(id, label, iconName = icon, rawPattern = listOf(9000, 4500, 560))

    private val profile = RemoteProfile(
        id = "panel_editor_fan", name = "风扇", category = DeviceCategory.FAN,
        buttons = listOf(
            key("power", "电源", "power"), key("speed", "风速", "speed"), key("swing", "摇头", "oscillation"),
            key("timer", "定时", "timer"), key("mode", "模式", "mode"), key("natural", "自然风", "nature"),
            key("light", "灯光"), key("sleep", "睡眠")
        )
    )

    @Test
    fun tilesMirrorPanelGridAndSupportHideAndDragReorder() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val saved = mutableStateOf<RemotePanelLayout?>(null)
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                RemotePanelEditor(profile, emptySet(), onSave = { saved.value = it }, onBack = {})
            }
        }
        fun tile(label: String) = composeRule.onNodeWithContentDescription(app.getString(R.string.panel_show_control, label))

        tile("风速").assertIsOn()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/panel_editor_before.webp", roborazziOptions = options)

        tile("定时").performClick()
        composeRule.waitForIdle()
        tile("定时").assertIsOff()
        assertTrue("key:timer" in saved.value!!.hidden)

        // Long-press the first key and drop it on the first slot of the next row.
        val from = tile("风速").fetchSemanticsNode().boundsInRoot.center
        val to = tile("模式").fetchSemanticsNode().boundsInRoot.center
        val steps = 12
        val delta = (to - from) / steps.toFloat()
        tile("风速").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(steps) { moveBy(delta); advanceEventTime(16) }
            up()
        }
        composeRule.waitForIdle()
        assertEquals(listOf("key:swing", "key:timer", "key:mode", "key:speed"), saved.value!!.order.take(4))
        assertTrue("key:timer" in saved.value!!.hidden)
        tile("风速").assertIsOn()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/panel_editor_after.webp", roborazziOptions = options)
    }
}
