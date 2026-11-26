package org.qinyan.rimoat.ui.components

import android.app.Application
import android.graphics.Insets
import android.view.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.serial.SerialDeviceOption
import org.qinyan.rimoat.hardware.serial.SerialIrModule
import org.qinyan.rimoat.hardware.serial.SerialIrSettings
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class, ExperimentalMaterial3Api::class)
class SerialIrSettingsRoborazziTest {
    @get:Rule val composeRule = createComposeRule()

    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    @Test
    fun channelSheetOpensSettingsAndRemainsAvailableAfterCancel() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val job = SupervisorJob()
        val manager = IrTransmitterManager(app, CoroutineScope(job + Dispatchers.Default))
        try {
            composeRule.waitUntil(10_000) { manager.serialSettingsLoaded.value }
            composeRule.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    TransmitterSelectionBottomSheet(
                        manager, rememberModalBottomSheetState(skipPartiallyExpanded = true), {}
                    )
                }
            }
            val settings = composeRule.onNodeWithText(app.getString(R.string.control_serial_config))
            settings.performScrollTo().assertIsDisplayed()
            composeRule.onNode(isDialog()).captureRoboImage(
                "src/test/screenshots/serial_channel_before.webp", roborazziOptions = options
            )
            settings.performClick()
            composeRule.onNodeWithText(SerialIrModule.UNCONFIGURED.label).assertIsSelected()
            composeRule.onNodeWithText(SerialIrModule.YS_IRTM.label).assertIsDisplayed()
            composeRule.onNodeWithText(app.getString(R.string.control_cancel)).performClick()
            settings.assertIsDisplayed().performClick()
            composeRule.onNodeWithText(SerialIrModule.UNCONFIGURED.label).assertIsSelected()
            composeRule.onNodeWithText(app.getString(R.string.control_cancel)).performClick()
            composeRule.onNodeWithText(app.getString(R.string.control_ir_channel)).assertIsDisplayed()
        } finally {
            manager.release()
            runBlocking { job.cancelAndJoin() }
        }
    }

    @Test
    fun systemBarInsetsDoNotResizeOrMoveTheOpenForm() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val showing = mutableStateOf(true)
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                if (showing.value) SerialIrSettingsForm(
                    saved = SerialIrSettings(), devices = emptyList(), onRequestPermission = {},
                    onSave = { _, _ -> Result.success(Unit) }, onDismiss = { showing.value = false }
                )
            }
        }
        val title = composeRule.onNodeWithText(app.getString(R.string.control_serial_config))
        title.assertIsDisplayed()
        val window = ShadowDialog.getLatestDialog().window!!
        fun dispatchBars(top: Int, bottom: Int) {
            composeRule.runOnUiThread {
                window.decorView.dispatchApplyWindowInsets(
                    WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(), Insets.of(0, top, 0, bottom))
                        .setVisible(WindowInsets.Type.systemBars(), true).build()
                )
            }
            composeRule.waitForIdle()
        }
        dispatchBars(0, 0)
        val initialTitle = title.fetchSemanticsNode().boundsInRoot
        val initialDialog = composeRule.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        composeRule.onNode(isDialog()).captureRoboImage(
            "src/test/screenshots/serial_dialog_open.webp", roborazziOptions = options
        )

        // System bars can arrive after the dialog's first layout during its entrance animation.
        dispatchBars(24, 48)
        assertEquals("System bars must not shift the title", initialTitle, title.fetchSemanticsNode().boundsInRoot)
        assertEquals("System bars must not resize the dialog", initialDialog,
            composeRule.onNode(isDialog()).fetchSemanticsNode().boundsInRoot)
        composeRule.onNode(isDialog()).captureRoboImage(
            "src/test/screenshots/serial_dialog_insets.webp", roborazziOptions = options
        )
        composeRule.onNodeWithText(app.getString(R.string.control_cancel)).performClick()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h640dp-mdpi")
    fun configuredFormRemainsScrollableAndSavesAtLargeFontSize() {
        RuntimeEnvironment.setFontScale(2f)
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val showing = mutableStateOf(true)
        var saved: SerialIrSettings? = null
        composeRule.setContent {
            IrRemoteTheme(darkTheme = true, dynamicColor = false) {
                if (showing.value) SerialIrSettingsForm(
                    saved = SerialIrSettings(module = SerialIrModule.YS_IRTM), devices = emptyList(),
                    onRequestPermission = {}, onSave = { settings, _ -> saved = settings; Result.success(Unit) },
                    onDismiss = { showing.value = false }
                )
            }
        }
        composeRule.onNodeWithText(SerialIrModule.YS_IRTM.label).assertIsSelected()
        val port = composeRule.onNode(hasText(app.getString(R.string.control_port_index)) and hasSetTextAction())
        port.performScrollTo().performTextReplacement("1")
        composeRule.onNodeWithText(SerialIrModule.IR_TOY.label).performScrollTo().performClick()
        composeRule.onNodeWithText(SerialIrModule.IR_TOY.label).assertIsSelected()
        composeRule.onNodeWithText(app.getString(R.string.control_firmware)).performScrollTo().assertIsDisplayed()
        composeRule.onNode(isDialog()).captureRoboImage(
            "src/test/screenshots/serial_dialog_large_font.webp", roborazziOptions = options
        )
        composeRule.onNodeWithText(app.getString(R.string.control_save)).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertNotNull(saved)
            assertEquals(SerialIrModule.IR_TOY, saved!!.module)
            assertEquals(1, saved!!.portIndex)
            assertEquals(115200, saved!!.baudRate)
            assertTrue(!showing.value)
        }
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h640dp-mdpi")
    fun deviceSelectorPicksUsbSerialAndRequestsPermissionWhenUngranted() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val device = SerialDeviceOption(
            deviceName = "/dev/bus/usb/001/002",
            label = "CH340 USB Serial",
            vendorId = 0x1A86,
            productId = 0x7523,
            portCount = 2,
            granted = false,
            serialNumber = "SN001",
            connectionId = "conn-1"
        )
        var requestedDevice: String? = null
        var savedConnection: String? = null
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                SerialIrSettingsForm(
                    saved = SerialIrSettings(module = SerialIrModule.YS_IRTM),
                    devices = listOf(device),
                    onRequestPermission = { requestedDevice = it },
                    onSave = { _, connectionId -> savedConnection = connectionId; Result.success(Unit) },
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithText(app.getString(R.string.control_select_serial)).performScrollTo().performClick()
        composeRule.onNodeWithText("CH340 USB Serial").assertIsDisplayed().performClick()
        composeRule.onNodeWithText(app.getString(R.string.control_authorize_usb)).performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("/dev/bus/usb/001/002", requestedDevice) }
        composeRule.onNode(isDialog()).captureRoboImage(
            "src/test/screenshots/serial_dialog_configured_portrait.webp", roborazziOptions = options
        )
        composeRule.onNodeWithText(app.getString(R.string.control_save)).performClick()
        composeRule.runOnIdle { assertEquals("conn-1", savedConnection) }
    }
}
