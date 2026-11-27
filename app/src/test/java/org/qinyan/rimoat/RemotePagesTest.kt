package org.qinyan.rimoat

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.components.remoteExtraButtons
import org.qinyan.rimoat.ui.components.tvStandardButtons
import org.qinyan.rimoat.ui.screens.RemoteControlScreen
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class RemotePagesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    private fun remote(category: DeviceCategory, labels: List<String>, name: String = "客厅风扇") = LegacyRemoteMigration.normalizeKeys(RemoteProfile(
        id = "test", name = name, category = category,
        buttons = labels.mapIndexed { index, label ->
            RemoteButton("key_$index", label, rawPattern = listOf(9000, 4500, 560))
        }
    ))

    @Test fun televisionKeepsDigitsAndDirectionKeysOnRegularPage() {
        val p = remote(DeviceCategory.TV, listOf("电源", "上", "下", "左", "右", "OK", "返回", "0", "1", "播放", "字幕"))
        assertEquals(listOf("播放", "字幕"), remoteExtraButtons(p).map { it.label })
        assertEquals(7, tvStandardButtons(p).filterNotNull().size)
    }

    @Test fun simpleRemotesHaveNoEmptySecondPage() {
        DeviceCategory.entries.forEach { assertTrue(remoteExtraButtons(remote(it, listOf("电源", "音量+"))).isEmpty()) }
    }

    @Test fun genericKeepsNumbersAndOnlyFirstMatchingDirectionsSoDuplicateKeysRemainAccessible() {
        val p = remote(DeviceCategory.CUSTOM, listOf("电源", "上", "下", "左", "右", "OK", "0", "1", "A", "B", "C", "D", "E", "F", "G", "上"))
        assertEquals(listOf("G", "上"), remoteExtraButtons(p).map { it.label })
    }

    @Test fun acTemperatureLimitReasonDialogShowsConciseCopy() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val encoder = object : AcSignalEncoder {
            override suspend fun protocolFor(sourceRemoteId: String) = 1
            override suspend fun supportedModes(protocol: Int) = listOf(AcMode.COOL, AcMode.HEAT)
            override suspend fun availableActions(protocol: Int) = setOf(
                AcAction.POWER_ON, AcAction.POWER_OFF, AcAction.TEMP_UP, AcAction.TEMP_DOWN, AcAction.MODE, AcAction.FAN
            )
            override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction) =
                listOf(IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560)))
        }
        val minAc = RemoteProfile(
            id = "ac_min",
            name = "客厅空调",
            category = DeviceCategory.AC,
            acProtocolId = 1,
            acSettings = AcSettings(powerOn = true, mode = AcMode.COOL, temperature = 16)
        )
        val maxAc = minAc.copy(acSettings = AcSettings(powerOn = true, mode = AcMode.COOL, temperature = 30))
        val store = ViewModelStore()
        val vm = RemoteControlViewModel("ac_min", initialProfile = minAc, acSignalEncoder = encoder).also { store.put("ac_limit", it) }

        try {
            composeRule.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    RemoteControlScreen(viewModel = vm, onBack = {})
                }
            }
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.availableAcActions.value.contains(AcAction.TEMP_UP)
            }
            composeRule.waitForIdle()

            repeat(3) { composeRule.onNodeWithContentDescription("降低温度").performClick() }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("原因").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("已达最低温度 16 °C。").assertIsDisplayed()
            composeRule.onNodeWithText("学习模式").assertDoesNotExist()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/remote_ac_temp_min_reason_dialog.webp", roborazziOptions = options)

            composeRule.onNodeWithText("关闭").performClick()
            composeRule.waitForIdle()

            vm.setProfileForTesting(maxAc)
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.availableAcActions.value.contains(AcAction.TEMP_DOWN)
            }
            composeRule.waitForIdle()
            repeat(3) { composeRule.onNodeWithContentDescription("升高温度").performClick() }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("原因").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("已达最高温度 30 °C。").assertIsDisplayed()
            composeRule.onNodeWithText("学习模式").assertDoesNotExist()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/remote_ac_temp_max_reason_dialog.webp", roborazziOptions = options)
        } finally {
            store.clear()
        }
    }
}
