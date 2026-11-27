package org.qinyan.rimoat.ui.components

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.lifecycle.ViewModelStore
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.i18n.labelRes
import org.qinyan.rimoat.ui.screens.AcLayoutPart
import org.qinyan.rimoat.ui.screens.AcRemoteLayout
import org.qinyan.rimoat.ui.screens.RemoteControlScreen
import org.qinyan.rimoat.ui.screens.tvRemoteItems
import org.qinyan.rimoat.ui.screens.fanRemoteItems
import org.qinyan.rimoat.ui.screens.genericRemoteItems
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import org.robolectric.Shadows
import kotlin.math.abs
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoundEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoundEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedTimerDurationLabel
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.theme.SilenceWindowClickSounds
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class ExpressiveRemoteControlsRoborazziTest {
    @get:Rule val screenshotDifferences = org.junit.rules.ErrorCollector()

    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val originalTimeZone = TimeZone.getDefault()
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    @Before fun initializeStrings() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        AppStrings.initialize(app)
    }

    @After fun restoreEnvironment() {
        TimeZone.setDefault(originalTimeZone)
        RuntimeEnvironment.setFontScale(1f)
    }

    @Test fun remoteAccessibilityLabelsFollowCompositionLocaleChanges() {
        fun localizedContext(tag: String) = app.createConfigurationContext(
            android.content.res.Configuration(app.resources.configuration).apply {
                setLocale(java.util.Locale.forLanguageTag(tag))
            }
        )
        val english = localizedContext("en")
        val chinese = localizedContext("zh-CN")
        var displayContext by mutableStateOf(english)
        var learned = 0
        var sent = 0
        compose.setContent {
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides displayContext,
                androidx.compose.ui.platform.LocalConfiguration provides displayContext.resources.configuration
            ) {
                IrRemoteTheme(dynamicColor = false) {
                    RemoteButtonView("Device", iconName = "power", onClick = { sent++ },
                        onLongClick = { learned++ }, isLearningMode = true)
                }
            }
        }
        val englishLabel = english.getString(R.string.control_learn, "Device")
        val chineseLabel = chinese.getString(R.string.control_learn, "Device")
        compose.onNodeWithContentDescription(englishLabel).assertIsDisplayed()
        compose.runOnIdle { displayContext = chinese }
        compose.onNodeWithContentDescription(englishLabel).assertDoesNotExist()
        compose.onNodeWithContentDescription(chineseLabel).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, learned)
            assertEquals(0, sent)
        }
    }

    @Test fun pressedRemoteRestoresBoundsAndSendsOnceWithoutSystemClickSound() {
        var sends = 0
        var clickSounds = 0
        val sound = object : SoundEffect {
            override fun playClickSound() { clickSounds++ }
        }
        val label = AppStrings.get(R.string.control_power_on)
        compose.setContent {
            CompositionLocalProvider(LocalSoundEffect provides sound) {
                IrRemoteTheme(dynamicColor = false) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            RemoteButtonView(label, iconName = "power", onClick = { sends++ },
                                variant = RemoteButtonVariant.POWER, diameter = 72.dp)
                            TextButton(onClick = {}, modifier = Modifier.testTag("ordinary_action")) {
                                Text(AppStrings.get(R.string.control_done))
                            }
                        }
                    }
                }
            }
        }
        val key = compose.onNodeWithContentDescription(label)
        val restingBounds = key.fetchSemanticsNode().boundsInRoot
        capture("expressive_remote_resting")
        compose.mainClock.autoAdvance = false
        key.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertEquals(0, sends) }
        capture("expressive_remote_pressed")
        key.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, sends)
            assertEquals(0, clickSounds)
        }
        assertEquals(restingBounds, key.fetchSemanticsNode().boundsInRoot)
        capture("expressive_remote_released")
        compose.onNodeWithTag("ordinary_action").performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, clickSounds) }
    }

    @Test fun timerTogglePresetUpdatesSelectionBeforeToolbarSends() = exerciseTimer(fontScale = 1f)

    @Test fun timerSliderRebuildsItsRangeWhenOptionsExpand() {
        var timerOptions by mutableStateOf(listOf(0, 60))
        var selected by mutableIntStateOf(0)
        compose.setContent {
            IrRemoteTheme(dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(AppStrings.get(R.string.control_timer_duration))
                        TimerDurationSlider(timerOptions, selected, enabled = true,
                            onSelect = { selected = it })
                        Text(localizedTimerDurationLabel(timerOptions[selected]))
                    }
                }
            }
        }
        capture("expressive_timer_slider_short_range")
        compose.runOnIdle { timerOptions = listOf(0, 30, 60, 120, 240, 480) }
        compose.onNodeWithContentDescription(AppStrings.get(R.string.control_timer_duration))
            .performSemanticsAction(SemanticsActions.SetProgress) { changeProgress ->
                assertTrue(changeProgress(5f))
            }
        compose.runOnIdle { assertEquals(5, selected) }
        compose.onNodeWithText(localizedTimerDurationLabel(480)).assertIsDisplayed()
        capture("expressive_timer_slider_expanded_range")
    }

    @Test fun dialogHostDisablesClickSoundAndRestoresItsOriginalFlag() {
        var showDialog by mutableStateOf(true)
        lateinit var dialogView: View
        var originalSoundEnabled = false
        compose.setContent {
            IrRemoteTheme(dynamicColor = false) {
                if (showDialog) Dialog(onDismissRequest = { showDialog = false }) {
                    val view = LocalView.current
                    val original = remember(view) { view.isSoundEffectsEnabled }
                    SilenceWindowClickSounds()
                    SideEffect {
                        dialogView = view
                        originalSoundEnabled = original
                    }
                    Surface { Text(AppStrings.get(R.string.control_done)) }
                }
            }
        }
        compose.onNode(isDialog()).assertIsDisplayed()
        compose.runOnIdle {
            assertTrue("The new Dialog host starts with platform click sounds enabled", originalSoundEnabled)
            assertFalse("Every control in the Dialog host must be silent", dialogView.isSoundEffectsEnabled)
            showDialog = false
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(originalSoundEnabled, dialogView.isSoundEffectsEnabled) }
    }

    @Test
    @Config(qualifiers = "en-rUS-w320dp-h891dp-mdpi")
    fun timerActionsStayReadableOnNarrowScreenWithLargeText() = exerciseTimer(fontScale = 2f)

    private fun exerciseTimer(fontScale: Float) {
        RuntimeEnvironment.setFontScale(fontScale)
        var sentMinutes: Int? = null
        compose.setContent {
            IrRemoteTheme(dynamicColor = false) {
                AcTimerBottomSheet(
                    options = listOf(0, 30, 60, 120, 240, 480),
                    currentMinutes = 60,
                    powerOn = true,
                    onDismiss = {},
                    onSend = { sentMinutes = it },
                    nowMillis = { 1_704_110_400_000L }
                )
            }
        }
        val titleLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(AppStrings.get(R.string.control_timer_off), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleLayouts) }
        val titleLayout = titleLayouts.single()
        assertEquals(fontScale, titleLayout.layoutInput.density.fontScale, 0.001f)
        if (fontScale > 1f) {
            assertTrue("Dialog title must render with enlarged text",
                titleLayout.getLineBottom(0) - titleLayout.getLineTop(0) > 40f)
        }
        val prefix = if (fontScale == 1f) "expressive_timer" else "expressive_timer_large_text"
        capture("${prefix}_initial", dialog = true)
        val preset = compose.onNode(hasText(localizedTimerDurationLabel(120)) and isToggleable())
        preset.performScrollTo().performClick().assertIsOn().assertIsSelected()
        compose.runOnIdle { assertEquals(null, sentMinutes) }
        capture("${prefix}_selected", dialog = true)
        compose.onNodeWithText(AppStrings.get(R.string.control_disable_timer)).assertIsDisplayed()
        compose.onNodeWithText(AppStrings.get(R.string.control_set_timer)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(120, sentMinutes) }
    }

    private fun screenshotRemote(
        category: DeviceCategory,
        labels: List<String>,
        keys: List<ButtonKey?>,
        name: String = "客厅风扇"
    ): RemoteProfile {
        require(labels.size == keys.size)
        return RemoteProfile(id = "test", name = name, category = category,
            buttons = labels.mapIndexed { index, label ->
                RemoteButton("key_$index", label, key = keys[index], rawPattern = listOf(9000, 4500, 560))
            })
    }

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-mdpi")
    fun fewExtensionKeysMergeDirectlyWithRegularControlsWhileManyUseTabs() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val fanKeys = listOf(ButtonKey.POWER, ButtonKey.FAN_SPEED, ButtonKey.OSCILLATION, ButtonKey.TIMER,
            ButtonKey.MODE, ButtonKey.WIND_TYPE, ButtonKey.SLEEP, ButtonKey.LIGHT, ButtonKey.MUTE)
        val compactFan = screenshotRemote(DeviceCategory.FAN,
            listOf("电源", "风速", "摇头", "定时", "模式", "自然风", "睡眠", "灯光", "静音"), fanKeys)
        val manyFan = screenshotRemote(DeviceCategory.FAN,
            listOf("电源", "风速", "摇头", "定时", "模式", "自然风", "睡眠", "灯光", "静音", "负离子", "屏显", "儿童锁", "节能", "强风", "柔风"),
            fanKeys + listOf(ButtonKey.ION, ButtonKey.OSD, ButtonKey.LOCK, ButtonKey.ECO, null, null))
        val store = ViewModelStore()
        val vm = RemoteControlViewModel("test", initialProfile = compactFan).also { store.put("fan", it) }

        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    RemoteControlScreen(viewModel = vm, onBack = {})
                }
            }
            compose.waitForIdle()

            compose.onNodeWithText("常规功能").assertDoesNotExist()
            compose.onNodeWithText("更多功能").assertDoesNotExist()
            compose.onNodeWithText("声音").assertDoesNotExist()
            compose.onNodeWithText("其他").assertDoesNotExist()
            compose.onNodeWithContentDescription("灯光").assertIsDisplayed()
            compose.onNodeWithContentDescription("静音").assertIsDisplayed()
            compose.onRoot().captureScreenshot("src/test/screenshots/remote_inline_extensions.webp", roborazziOptions = options)

            vm.setProfileForTesting(manyFan)
            compose.waitForIdle()
            compose.onNodeWithText("常规功能").assertIsDisplayed()
            compose.onNodeWithText("更多功能").assertIsDisplayed()
            compose.onNodeWithText("更多功能").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("声音").assertIsDisplayed()
            compose.onNodeWithContentDescription("静音").assertIsDisplayed()
            compose.onRoot().captureScreenshot("src/test/screenshots/remote_paged_extensions.webp", roborazziOptions = options)
        } finally {
            store.clear()
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-mdpi")
    fun tvAndGenericWithDigitsAndDpadInlineFewExtensionsBehindRegularKeys() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val tvCompact = screenshotRemote(
            DeviceCategory.TV,
            listOf("0", "1", "2", "电源", "信源", "静音", "上", "下", "左", "右", "OK", "返回", "主页", "菜单", "音量+", "音量-", "频道+", "频道-", "退出", "画中画"),
            keys = listOf(ButtonKey.DIGIT_0, ButtonKey.DIGIT_1, ButtonKey.DIGIT_2, ButtonKey.POWER,
                ButtonKey.SOURCE, ButtonKey.MUTE, ButtonKey.UP, ButtonKey.DOWN, ButtonKey.LEFT, ButtonKey.RIGHT,
                ButtonKey.OK, ButtonKey.BACK, ButtonKey.HOME, ButtonKey.MENU, ButtonKey.VOLUME_UP,
                ButtonKey.VOLUME_DOWN, ButtonKey.CHANNEL_UP, ButtonKey.CHANNEL_DOWN, ButtonKey.EXIT, ButtonKey.PICTURE_IN_PICTURE),
            name = "客厅电视"
        )
        val genericWithDigits = screenshotRemote(
            DeviceCategory.CUSTOM,
            listOf("0", "1", "2", "电源", "上", "下", "左", "右", "OK", "确定", "锁定", "参数", "查看", "切换", "退出", "调光"),
            keys = listOf(ButtonKey.DIGIT_0, ButtonKey.DIGIT_1, ButtonKey.DIGIT_2, ButtonKey.POWER,
                ButtonKey.UP, ButtonKey.DOWN, ButtonKey.LEFT, ButtonKey.RIGHT, ButtonKey.OK, null,
                ButtonKey.LOCK, ButtonKey.MODE, ButtonKey.INFO, ButtonKey.SWITCH, ButtonKey.EXIT, ButtonKey.BRIGHTNESS),
            name = "红外开关"
        )
        val store = ViewModelStore()
        val vm = RemoteControlViewModel("test", initialProfile = tvCompact).also { store.put("tv", it) }

        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    RemoteControlScreen(viewModel = vm, onBack = {})
                }
            }
            compose.waitForIdle()

            compose.onNodeWithText("常规功能").assertDoesNotExist()
            compose.onNodeWithText("更多功能").assertDoesNotExist()
            compose.onNodeWithContentDescription("退出").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("画中画").performScrollTo().assertIsDisplayed()
            compose.onRoot().captureScreenshot("src/test/screenshots/remote_tv_inline_extensions.webp", roborazziOptions = options)

            vm.setProfileForTesting(genericWithDigits)
            compose.waitForIdle()
            compose.onNodeWithText("常规功能").assertDoesNotExist()
            compose.onNodeWithText("更多功能").assertDoesNotExist()
            val exitBounds = compose.onNodeWithContentDescription("退出").performScrollTo().fetchSemanticsNode().boundsInRoot
            val dimBounds = compose.onNodeWithContentDescription("调光").performScrollTo().fetchSemanticsNode().boundsInRoot
            assertTrue("调光 should appear below 退出 in the same function grid", dimBounds.top > exitBounds.top)
            compose.onNodeWithContentDescription("调光").assertIsDisplayed()
            compose.onRoot().captureScreenshot("src/test/screenshots/remote_generic_inline_extensions.webp", roborazziOptions = options)
        } finally {
            store.clear()
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-mdpi")
    fun dynamicAcFillsFewExtensionsDirectlyBehindMainControlsAndPagesWhenMany() {
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
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    RemoteControlScreen(viewModel = vm, onBack = {})
                }
            }
            compose.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.availableAcActions.value.contains(AcAction.QUICK_COOL)
            }
            compose.waitForIdle()

            compose.onNodeWithText("常规功能").assertDoesNotExist()
            compose.onNodeWithText("更多功能").assertDoesNotExist()
            val windDirBounds = compose.onNodeWithContentDescription(AcAction.WIND_DIRECTION.label).fetchSemanticsNode().boundsInRoot
            val quickCoolBounds = compose.onNodeWithContentDescription(AcAction.QUICK_COOL.label).fetchSemanticsNode().boundsInRoot
            assertTrue("一键制冷 should fill the trailing slot on the same row as 风向", abs(windDirBounds.top - quickCoolBounds.top) < 2f)
            compose.onNodeWithContentDescription(AcAction.QUICK_HEAT.label).performScrollTo().assertIsDisplayed()
            compose.onRoot().captureScreenshot("src/test/screenshots/remote_ac_inline_extensions.webp", roborazziOptions = options)

            actions = actions + setOf(
                AcAction.LIGHT, AcAction.TURBO, AcAction.SLEEP, AcAction.VENTILATION,
                AcAction.AUXILIARY_HEAT, AcAction.DRY, AcAction.ECO, AcAction.QUIET
            )
            vm.setProfileForTesting(compactAc.copy(name = "全功能空调", acProtocolId = 2))
            compose.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                vm.availableAcActions.value.contains(AcAction.QUIET)
            }
            compose.waitForIdle()
            compose.onNodeWithText("常规功能").assertIsDisplayed()
            compose.onNodeWithText("更多功能").assertIsDisplayed()
            compose.onNodeWithText("更多功能").performClick()
            compose.waitForIdle()
            compose.onRoot().captureScreenshot("src/test/screenshots/remote_ac_paged_extensions.webp", roborazziOptions = options)
        } finally {
            store.clear()
        }
    }

    @Test fun powerKeysKeepTheirAccentInLightTheme() = exercisePowerPanels("light")

    @Test fun powerKeysKeepTheirAccentInDarkTheme() = exercisePowerPanels("dark", dark = true)

    @Test fun powerKeysKeepTheirAccentOnAmoledCanvas() = exercisePowerPanels("amoled", dark = true, pureBlack = true)

    @Test fun powerKeysKeepTheirAccentWithDynamicColors() = exercisePowerPanels("dynamic", dynamic = true)

    private fun exercisePowerPanels(name: String, dark: Boolean = false, pureBlack: Boolean = false, dynamic: Boolean = false) {
        val television = RemoteProfile("tv", "Television", category = DeviceCategory.TV,
            buttons = listOf(RemoteButton("tv_power", "TV power", key = ButtonKey.POWER,
                rawPattern = listOf(9000, 4500, 560))))
        val fan = RemoteProfile("fan", "Fan", category = DeviceCategory.FAN,
            buttons = listOf(RemoteButton("fan_power", "Fan power", key = ButtonKey.POWER,
                rawPattern = listOf(9000, 4500, 560))))
        val generic = RemoteProfile("remote", "Universal remote", category = DeviceCategory.CUSTOM,
            buttons = listOf(RemoteButton("custom_power", "Remote power", key = ButtonKey.POWER,
                rawPattern = listOf(9000, 4500, 560))))
        val profiles = listOf(television, fan, generic)
        var sentIds by mutableStateOf(emptyList<String>())
        fun availableProfile(profile: RemoteProfile) = profile.copy(buttons = profile.buttons.map {
            if (it.id in sentIds) it.copy(rawPattern = emptyList()) else it
        })
        val send: (RemoteButton) -> Unit = { sentIds = sentIds + it.id }
        compose.setContent {
            IrRemoteTheme(darkTheme = dark, dynamicColor = dynamic, pureBlackBackground = pureBlack) {
                ProvideExpressiveCanvas {
                    Surface(Modifier.fillMaxSize(), color = ExpressiveSurface.canvas) {
                        LazyColumn(Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item("tv_title") { Text(television.name, style = MaterialTheme.typography.titleMedium) }
                            tvRemoteItems(availableProfile(television), send)
                            item("fan_title") { Text(fan.name, style = MaterialTheme.typography.titleMedium) }
                            fanRemoteItems(availableProfile(fan), send)
                            item("generic_title") { Text(generic.name, style = MaterialTheme.typography.titleMedium) }
                            genericRemoteItems(availableProfile(generic), send)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        profiles.forEach { profile ->
            val key = compose.onNodeWithContentDescription(profile.buttons.single().label)
            key.assertIsDisplayed()
            val bounds = key.fetchSemanticsNode().boundsInRoot
            assertEquals("Power controls keep the same 72dp target", 72f, bounds.width, 0.1f)
            assertEquals(72f, bounds.height, 0.1f)
        }
        capture("remote_power_${name}_before")
        profiles.forEach { profile ->
            val key = compose.onNodeWithContentDescription(profile.buttons.single().label)
            key.performTouchInput { click() }
            compose.waitForIdle()
            key.assertIsNotEnabled()
            key.performTouchInput { click() }
        }
        compose.runOnIdle { assertEquals(profiles.map { it.buttons.single().id }, sentIds) }
        capture("remote_power_${name}_after")
    }

    @Test
    @Config(qualifiers = "en-rUS-w320dp-h891dp-mdpi")
    fun customTextKeysRemainReachableAtDoubleFontSize() {
        RuntimeEnvironment.setFontScale(2f)
        assertEquals(2f, app.resources.configuration.fontScale, 0f)
        val buttons = listOf("Desk lamp", "Scene A", "Movie", "Power").mapIndexed { index, label ->
            RemoteButton("custom_$index", label, rawPattern = listOf(9000, 4500, 560))
        }
        var sentIds by mutableStateOf(emptyList<String>())
        compose.setContent {
            IrRemoteTheme(dynamicColor = false) {
                ProvideExpressiveCanvas {
                    Surface(Modifier.fillMaxSize(), color = ExpressiveSurface.canvas) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            RemoteFunctionGrid(buttons, onSend = { sentIds = sentIds + it.id },
                                onLearn = null, learning = false, enabled = { it.id !in sentIds })
                        }
                    }
                }
            }
        }
        buttons.forEach { button ->
            assertEquals(null, resolveButtonIcon(button))
            val bounds = compose.onNodeWithContentDescription(button.label).assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            assertEquals("A custom label does not turn its button into a power key", 64f, bounds.width, 0.1f)
        }
        capture("remote_custom_text_large_before")
        val lamp = compose.onNodeWithContentDescription(buttons.first().label)
        lamp.performTouchInput { click() }
        compose.waitForIdle()
        lamp.assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(buttons.first().id), sentIds) }
        capture("remote_custom_text_large_after")
    }

    @Test fun acSemanticIconsKeepDistinctFunctionsAndToggleStates() = exerciseAcIcons("light")

    @Test fun acSemanticIconsKeepDistinctFunctionsInDarkTheme() = exerciseAcIcons("dark", dark = true)

    private fun exerciseAcIcons(name: String, dark: Boolean = false) {
        var settings by mutableStateOf(AcSettings(mode = AcMode.HEAT))
        val actions = setOf(AcAction.MODE, AcAction.SWING, AcAction.HORIZONTAL_SWING,
            AcAction.VENTILATION, AcAction.AUXILIARY_HEAT, AcAction.DRY,
            AcAction.QUICK_COOL, AcAction.QUICK_HEAT)
        val profile = RemoteProfile("ac_icons", "Air conditioner", category = DeviceCategory.AC, acProtocolId = 670)
        val pressed = mutableListOf<AcAction>()
        compose.setContent {
            IrRemoteTheme(darkTheme = dark, dynamicColor = false) {
                ProvideExpressiveCanvas {
                    Surface(Modifier.fillMaxSize(), color = ExpressiveSurface.canvas) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            AcRemoteLayout(profile, settings, false,
                                onTogglePower = {}, onTempUp = {}, onTempDown = {},
                                onCycleMode = {}, onCycleWind = {}, availableActions = actions,
                                onAction = { action, minutes ->
                                    pressed += action
                                    settings = settings.apply(action, minutes)
                                }, onSendButton = {}, inlineExtensions = true, part = AcLayoutPart.FUNCTIONS)
                        }
                    }
                }
            }
        }
        actions.forEach { action -> compose.onNodeWithContentDescription(app.getString(action.labelRes)).assertIsDisplayed() }
        capture("remote_ac_semantic_icons_${name}_before")
        val toggles = listOf(AcAction.SWING, AcAction.HORIZONTAL_SWING, AcAction.VENTILATION,
            AcAction.AUXILIARY_HEAT, AcAction.DRY)
        toggles.forEach { action ->
            compose.onNodeWithContentDescription(app.getString(action.labelRes)).performTouchInput { click() }
                .assertIsOn()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(toggles, pressed)
            assertTrue(settings.isSwingOn && settings.horizontalSwingOn && settings.ventilationOn &&
                settings.auxiliaryHeatOn && settings.dryOn)
            assertEquals(AcMode.HEAT, settings.mode)
        }
        capture("remote_ac_semantic_icons_${name}_after")
    }

    private fun capture(name: String, dialog: Boolean = false) =
        (if (dialog) compose.onNode(isDialog()) else compose.onRoot()).captureScreenshot(
        "src/test/screenshots/$name.webp", roborazziOptions = options
    )

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.captureScreenshot(
        path: String, roborazziOptions: RoborazziOptions
    ) {
        screenshotDifferences.checkSucceeds {
            captureRoboImage(path, roborazziOptions = roborazziOptions)
        }
    }
}
