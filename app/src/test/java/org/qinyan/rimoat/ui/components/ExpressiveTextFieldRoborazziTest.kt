package org.qinyan.rimoat.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.ui.motion.MotionAlertDialog
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
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
class ExpressiveTextFieldRoborazziTest {
    @get:Rule val compose = createComposeRule()

    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    @Test
    fun formFieldsAdaptAcrossFocusErrorDisabledAndTheme() {
        var dark by mutableStateOf(false)
        var name by mutableStateOf("")
        var brand by mutableStateOf("")
        var isError by mutableStateOf(false)
        compose.setContent {
            IrRemoteTheme(darkTheme = dark, dynamicColor = false) {
                ProvideExpressiveCanvas {
                    Surface(color = ExpressiveSurface.canvas) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = ExpressiveSurface.card,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ExpressiveTextField(
                                    value = name,
                                    onValueChange = { name = it; isError = false },
                                    label = { Text("遥控器名称") },
                                    supportingText = if (isError) ({ Text("请输入名称") }) else null,
                                    isError = isError,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                ExpressiveTextField(
                                    value = brand,
                                    onValueChange = { brand = it },
                                    label = { Text("品牌（可选）") },
                                    trailingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                ExpressiveTextField(
                                    value = "已锁定型号",
                                    onValueChange = {},
                                    enabled = false,
                                    label = { Text("只读预设") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        val nameField = compose.onNode(hasSetTextAction() and hasText("遥控器名称"))
        compose.onNode(hasText("只读预设")).assertIsNotEnabled()
        nameField.performTouchInput { click() }
        nameField.assertIsFocused()
        nameField.performTextInput("客厅空调")
        assertEquals("客厅空调", name)
        compose.onRoot().captureRoboImage("src/test/screenshots/expressive_textfield_card_light.webp", roborazziOptions = options)

        compose.runOnIdle {
            isError = true
            dark = true
        }
        compose.onNodeWithText("请输入名称").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/expressive_textfield_card_dark_error.webp", roborazziOptions = options)
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h640dp-mdpi")
    fun dialogFieldsRemainCrispAndReadableAtDoubleFontScale() {
        verifyDialogAtDoubleFontScale(dark = false, screenshotName = "expressive_textfield_dialog_light")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h640dp-mdpi")
    fun dialogFieldsRemainCrispInDarkModeAtDoubleFontScale() {
        verifyDialogAtDoubleFontScale(dark = true, screenshotName = "expressive_textfield_dialog_dark")
    }

    private fun verifyDialogAtDoubleFontScale(dark: Boolean, screenshotName: String) {
        RuntimeEnvironment.setFontScale(2f)
        var room by mutableStateOf("客厅")
        compose.setContent {
            IrRemoteTheme(darkTheme = dark, dynamicColor = false) {
                MotionAlertDialog(
                    onDismissRequest = {},
                    title = { Text("编辑房间") },
                    text = {
                        ExpressiveTextField(
                            value = room,
                            onValueChange = { room = it },
                            label = { Text("房间名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = { TextButton(onClick = {}, shapes = ButtonDefaults.shapes()) { Text("保存") } },
                    dismissButton = { TextButton(onClick = {}, shapes = ButtonDefaults.shapes()) { Text("取消") } }
                )
            }
        }
        compose.onNode(hasSetTextAction() and hasText("房间名称")).performTouchInput { click() }
        compose.waitForIdle()
        compose.onNode(isDialog()).captureRoboImage("src/test/screenshots/$screenshotName.webp", roborazziOptions = options)
    }
}
