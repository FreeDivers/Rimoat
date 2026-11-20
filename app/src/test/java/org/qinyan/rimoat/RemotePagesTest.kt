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
import kotlin.math.abs

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

    private fun remote(category: DeviceCategory, labels: List<String>, name: String = "客厅风扇") = RemoteProfile(
        id = "test", name = name, category = category,
        buttons = labels.mapIndexed { index, label ->
            RemoteButton("key_$index", label, rawPattern = listOf(9000, 4500, 560))
        }
    )

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

    @Test fun fewExtensionKeysMergeDirectlyWithRegularControlsWhileManyUseTabs() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val compactFan = remote(DeviceCategory.FAN, listOf("电源", "风速", "摇头", "定时", "模式", "自然风", "睡眠", "灯光", "静音"))
        val manyFan = remote(DeviceCategory.FAN, listOf("电源", "风速", "摇头", "定时", "模式", "自然风", "睡眠", "灯光", "静音", "负离子", "屏显", "儿童锁", "节能", "强风", "柔风"))
        val store = ViewModelStore()
        val vm = RemoteControlViewModel("test", initialProfile = compactFan).also { store.put("fan", it) }

        try {
            composeRule.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    RemoteControlScreen(viewModel = vm, onBack = {})
                }
            }
            composeRule.waitForIdle()

            // 2 extension keys ("灯光", "静音") merge into the regular grid without tabs or group headings.
            composeRule.onNodeWithText("常规功能").assertDoesNotExist()
            composeRule.onNodeWithText("更多功能").assertDoesNotExist()
            composeRule.onNodeWithText("声音").assertDoesNotExist()
            composeRule.onNodeWithText("其他").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("灯光").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("静音").assertIsDisplayed()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/remote_inline_extensions.webp", roborazziOptions = options)

            // 8 extension keys split into "常规功能 / 更多功能" tabs.
            vm.setProfileForTesting(manyFan)
            composeRule.waitForIdle()
            composeRule.onNodeWithText("常规功能").assertIsDisplayed()
            composeRule.onNodeWithText("更多功能").assertIsDisplayed()
            composeRule.onNodeWithText("更多功能").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("声音").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("静音").assertIsDisplayed()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/remote_paged_extensions.webp", roborazziOptions = options)
        } finally {
            store.clear()
        }
    }

    @Test fun tvAndGenericWithDigitsAndDpadInlineFewExtensionsBehindRegularKeys() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val tvCompact = remote(
            DeviceCategory.TV,
            listOf("0", "1", "2", "电源", "信源", "静音", "上", "下", "左", "右", "OK", "返回", "主页", "菜单", "音量+", "音量-", "频道+", "频道-", "退出", "画中画"),
            name = "客厅电视"
        )
        val genericWithDigits = remote(
            DeviceCategory.CUSTOM,
            listOf("0", "1", "2", "电源", "上", "下", "左", "右", "OK", "确定", "锁定", "参数", "查看", "切换", "退出", "调光"),
            name = "红外开关"
        )
        val store = ViewModelStore()
        val vm = RemoteControlViewModel("test", initialProfile = tvCompact).also { store.put("tv", it) }

        try {
            composeRule.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    RemoteControlScreen(viewModel = vm, onBack = {})
                }
            }
            composeRule.waitForIdle()

            // TV with 2 extension keys must inline them instead of creating a "更多功能" tab.
            composeRule.onNodeWithText("常规功能").assertDoesNotExist()
            composeRule.onNodeWithText("更多功能").assertDoesNotExist()
            composeRule.onNodeWithContentDescription("退出").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithContentDescription("画中画").performScrollTo().assertIsDisplayed()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/remote_tv_inline_extensions.webp", roborazziOptions = options)

            // Generic remote with digits + D-Pad + 1 extra key ("调光") must inline "调光" behind regular keys.
            vm.setProfileForTesting(genericWithDigits)
            composeRule.waitForIdle()
            composeRule.onNodeWithText("常规功能").assertDoesNotExist()
            composeRule.onNodeWithText("更多功能").assertDoesNotExist()
            val exitBounds = composeRule.onNodeWithContentDescription("退出").performScrollTo().fetchSemanticsNode().boundsInRoot
            val dimBounds = composeRule.onNodeWithContentDescription("调光").performScrollTo().fetchSemanticsNode().boundsInRoot
            assertTrue("调光 should appear below 退出 in the same function grid", dimBounds.top > exitBounds.top)
            composeRule.onNodeWithContentDescription("调光").assertIsDisplayed()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/remote_generic_inline_extensions.webp", roborazziOptions = options)
        } finally {
            store.clear()
        }
    }

    @Test fun dynamicAcFillsFewExtensionsDirectlyBehindMainControlsAndPagesWhenMany() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        var actions = setOf(
            AcAction.POWER_ON, AcAction.POWER_OFF, AcAction.TEMP_UP, AcAction.TEMP_DOWN,
            AcAction.MODE, AcAction.FAN, AcAction.SWING, AcAction.HORIZONTAL_SWING,
            AcAction.WIND_DIRECTION, AcAction.QUICK_COOL, AcAction.QUICK_HEAT
        )
        val encoder = object : AcSignalEncoder {
            override suspend fun protocolFor(sourceRemoteId: String) = 1
            override suspend fun supportedModes(protocol: Int) = listOf(AcMode.COOL, AcMode.HEAT)
            override suspend fun availableActions(protocol: Int) = actions
            override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction) =
                listOf(IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560)))
        }
        val compactAc = RemoteProfile(id = "ac", name = "客厅空调", category = DeviceCategory.AC, acProtocolId = 1)
        val store = ViewModelStore()
        val vm = RemoteControlViewModel("ac", initialProfile = compactAc, acSignalEncoder = encoder).also { store.put("ac", it) }

        try {
            composeRule.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    RemoteControlScreen(viewModel = vm, onBack = {})
                }
            }
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.availableAcActions.value.contains(AcAction.QUICK_COOL)
            }
            composeRule.waitForIdle()

            // 2 extension actions ("一键制冷", "一键制热") merge without tabs and fill directly behind "风向" on row 2.
            composeRule.onNodeWithText("常规功能").assertDoesNotExist()
            composeRule.onNodeWithText("更多功能").assertDoesNotExist()
            val windDirBounds = composeRule.onNodeWithContentDescription(AcAction.WIND_DIRECTION.label).fetchSemanticsNode().boundsInRoot
            val quickCoolBounds = composeRule.onNodeWithContentDescription(AcAction.QUICK_COOL.label).fetchSemanticsNode().boundsInRoot
            assertTrue("一键制冷 should fill the trailing slot on the same row as 风向", abs(windDirBounds.top - quickCoolBounds.top) < 2f)
            composeRule.onNodeWithContentDescription(AcAction.QUICK_HEAT.label).performScrollTo().assertIsDisplayed()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/remote_ac_inline_extensions.webp", roborazziOptions = options)

            // 8 extra actions (> 6) must still split into "常规功能 / 更多功能" tabs.
            actions = actions + setOf(
                AcAction.LIGHT, AcAction.TURBO, AcAction.SLEEP, AcAction.VENTILATION,
                AcAction.AUXILIARY_HEAT, AcAction.DRY, AcAction.ECO, AcAction.QUIET
            )
            vm.setProfileForTesting(compactAc.copy(name = "全功能空调", acProtocolId = 2))
            composeRule.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.availableAcActions.value.contains(AcAction.QUIET)
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("常规功能").assertIsDisplayed()
            composeRule.onNodeWithText("更多功能").assertIsDisplayed()
            composeRule.onNodeWithText("更多功能").performClick()
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/remote_ac_paged_extensions.webp", roborazziOptions = options)
        } finally {
            store.clear()
        }
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
