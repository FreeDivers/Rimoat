package org.qinyan.rimoat.ui.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
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
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.labelRes
import org.qinyan.rimoat.ui.components.panelKey
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w480dp-h320dp-land-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class LandscapeRemotePanelRoborazziTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))
    private val sent = mutableListOf<String>()
    private val learned = mutableListOf<String>()
    private val repeated = mutableListOf<String>()

    private fun key(id: String, label: String, icon: String? = null, buttonKey: ButtonKey? = null) =
        RemoteButton(id, label, iconName = icon, key = buttonKey, rawPattern = listOf(9000, 4500, 560))

    private fun tv() = RemoteProfile("pane_tv", "客厅电视", category = DeviceCategory.TV, buttons = listOf(
        key("power", "电源", "power", ButtonKey.POWER),
        key("tv_up", "上", buttonKey = ButtonKey.UP),
        key("tv_down", "下", buttonKey = ButtonKey.DOWN),
        key("tv_left", "左", buttonKey = ButtonKey.LEFT),
        key("tv_right", "右", buttonKey = ButtonKey.RIGHT),
        key("tv_ok", "OK", buttonKey = ButtonKey.OK),
        key("back", "返回", buttonKey = ButtonKey.BACK),
        key("vol_up", "音量+", buttonKey = ButtonKey.VOLUME_UP),
        key("vol_down", "音量-", buttonKey = ButtonKey.VOLUME_DOWN)
    ) + (0..9).map { key("tv_num_$it", "$it") } + (1..8).map { key("extra_$it", "扩展功能$it") })

    private fun show(profile: RemoteProfile, dark: Boolean = false, fontScale: Float = 1f, learning: Boolean = false) {
        AppStrings.initialize(RuntimeEnvironment.getApplication())
        composeRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                IrRemoteTheme(darkTheme = dark, dynamicColor = false) {
                    var page by rememberSaveable { mutableStateOf(0) }
                    var numbers by rememberSaveable { mutableStateOf(false) }
                    var ac by rememberSaveable { mutableStateOf(26) }
                    Surface(Modifier.fillMaxSize()) {
                        LandscapeRemotePanel(profile, AcSettings(temperature = ac), false, AcAction.entries.toSet(), emptyMap(),
                            learning, false, page, { page = it }, numbers, { numbers = it },
                            onSendButton = { sent += it.id }, onLearnButton = { learned += it.id }, onRepeatButton = { repeated += it.id },
                            onUnavailable = { _, _ -> }, onTogglePower = {}, onTempUp = { ac++ }, onTempDown = { ac-- },
                            onCycleMode = {}, onCycleWind = {}, onAction = { _, _ -> }, onLoadChoices = { emptyList() },
                            onSelectChoice = {}, onResend = {}, onPreset = {}, transmitterType = TransmitterType.MOCK)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun capture(name: String) = composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.webp", roborazziOptions = options)

    @Test
    fun narrowTvKeepsDirectionPadWhileRightPaneExpandsAndScrolls() {
        val app = RuntimeEnvironment.getApplication()
        show(tv())
        val leftBefore = composeRule.onNodeWithContentDescription("OK").fetchSemanticsNode().boundsInRoot
        val primary = composeRule.onNodeWithTag("two_pane_primary").fetchSemanticsNode().boundsInRoot
        val secondary = composeRule.onNodeWithTag("two_pane_secondary").fetchSemanticsNode().boundsInRoot
        assertTrue(primary.width >= 216f)
        assertTrue(leftBefore.center.x < secondary.left)
        composeRule.onNodeWithContentDescription("电源").performClick()
        assertEquals(listOf("power"), sent)
        capture("landscape_tv_narrow_before")

        composeRule.onNodeWithContentDescription(app.getString(R.string.screen_expand_numbers)).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("9").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf("power", "tv_num_9"), sent)
        assertEquals(leftBefore, composeRule.onNodeWithContentDescription("OK").fetchSemanticsNode().boundsInRoot)
        capture("landscape_tv_narrow_numbers")

        composeRule.onNodeWithText("更多功能").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("扩展功能8").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf("power", "tv_num_9", "extra_8"), sent)
        assertEquals(leftBefore, composeRule.onNodeWithContentDescription("OK").fetchSemanticsNode().boundsInRoot)
        capture("landscape_tv_narrow_more")
    }

    @Test
    fun tvKeepsEditedNavigationAndInputOrder() {
        val profile = tv().copy(buttons = tv().buttons + listOf(
            key("source", "信源", buttonKey = ButtonKey.SOURCE),
            key("mute", "静音", buttonKey = ButtonKey.MUTE),
            key("home", "主页", buttonKey = ButtonKey.HOME),
            key("menu", "菜单", buttonKey = ButtonKey.MENU)
        ),
            panelLayout = RemotePanelLayout(order = listOf("key:menu", "key:back", "key:home", "key:mute", "key:source")))
        show(profile)
        val mute = composeRule.onNodeWithContentDescription("静音").fetchSemanticsNode().boundsInRoot
        val source = composeRule.onNodeWithContentDescription("信源").fetchSemanticsNode().boundsInRoot
        assertTrue("edited input order survives pane grouping", mute.top < source.top)
        composeRule.onNodeWithContentDescription("菜单").performScrollTo()
        val menu = composeRule.onNodeWithContentDescription("菜单").fetchSemanticsNode().boundsInRoot
        val back = composeRule.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
        assertTrue("edited navigation order survives pane grouping", menu.left < back.left)
        capture("landscape_tv_edited_order")
    }

    @Test
    fun tvLearningOffersMissingDirectionAndNumberKeys() {
        val app = RuntimeEnvironment.getApplication()
        show(RemoteProfile("pane_tv_learning", "学习电视", category = DeviceCategory.TV, buttons = listOf(key("power", "电源", "power").copy(key = ButtonKey.POWER))), learning = true)
        capture("landscape_tv_learning_missing_keys_before")
        val direction = composeRule.onNodeWithContentDescription(app.getString(R.string.control_learn, "上"))
        direction.performTouchInput { click() }
        assertEquals(listOf("direction_up"), learned)
        assertTrue(sent.isEmpty())
        assertTrue(repeated.isEmpty())
        direction.performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        assertEquals(listOf("direction_up"), learned)
        assertTrue(sent.isEmpty())
        assertTrue(repeated.isEmpty())
        direction.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(listOf("direction_up", "direction_up"), learned)
        composeRule.onNodeWithContentDescription(app.getString(R.string.screen_expand_numbers)).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription(app.getString(R.string.control_learn, "3")).performScrollTo().performClick()
        assertEquals(listOf("direction_up", "direction_up", "num_3"), learned)
        assertTrue(sent.isEmpty())
        assertTrue(repeated.isEmpty())
        capture("landscape_tv_learning_missing_keys_after")
    }

    @Test
    fun tvLearningKeepsRenamedNumberUniqueAndDoesNotRestoreHiddenNumber() {
        val app = RuntimeEnvironment.getApplication()
        val profile = RemoteProfile("pane_tv_number_metadata", "数字学习电视", category = DeviceCategory.TV,
            buttons = listOf(key("power", "电源", "power").copy(key = ButtonKey.POWER), key("catalog_key", "自定义数字").copy(builtinLabel = "3")),
            panelLayout = RemotePanelLayout(hidden = setOf("key:num_4", "key:direction_down")))
        show(profile, learning = true)
        composeRule.onNodeWithContentDescription(app.getString(R.string.control_learn, "下")).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(app.getString(R.string.screen_expand_numbers)).performScrollTo().performClick()
        val three = app.getString(R.string.control_learn, "3")
        composeRule.onAllNodesWithContentDescription(three).assertCountEquals(1)
        composeRule.onNodeWithContentDescription(app.getString(R.string.control_learn, "4")).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(three).performScrollTo().performClick()
        assertEquals(listOf("catalog_key"), learned)
        assertTrue(sent.isEmpty())
        capture("landscape_tv_learning_number_metadata")
    }

    @Test
    fun migratedLegacyHiddenDirectionsStayHiddenInLandscapeLearningMode() {
        val app = RuntimeEnvironment.getApplication()
        val legacy = RemoteProfile(
            id = "pane_tv_migrated_hidden",
            name = "客厅旧电视",
            category = DeviceCategory.TV,
            keySchemaVersion = 1,
            buttons = listOf(
                key("power", "电源", "power").copy(key = ButtonKey.POWER),
                RemoteButton("direction_上", "上", builtinLabel = "上", key = ButtonKey.UP,
                    isLearned = true, rawPattern = listOf(9000, 4500, 560))
            ),
            panelLayout = RemotePanelLayout(hidden = setOf("key:direction_下", "key:direction_左"))
        )
        val migrated = LegacyRemoteMigration.normalizeKeys(legacy)
        assertEquals(setOf("key:direction_down", "key:direction_left"), migrated.panelLayout.hidden)
        show(migrated, learning = true)
        composeRule.onNodeWithContentDescription(app.getString(R.string.control_learn, app.getString(R.string.control_up))).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(app.getString(R.string.control_learn, app.getString(R.string.control_right))).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(app.getString(R.string.control_learn, app.getString(R.string.control_down))).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(app.getString(R.string.control_learn, app.getString(R.string.control_left))).assertDoesNotExist()
        capture("remote_tv_migrated_hidden_learning_landscape")
    }

    @Test
    @Config(qualifiers = "en-rUS-w891dp-h411dp-land-mdpi")
    fun learnedTvRemoteRendersDpadIconsAndGroupHeadingsInEnglishLandscape() =
        exerciseLearnedTvLandscapeLocale("en")

    @Test
    @Config(qualifiers = "ja-w891dp-h411dp-land-mdpi")
    fun learnedTvRemoteRendersDpadIconsAndGroupHeadingsInJapaneseLandscape() =
        exerciseLearnedTvLandscapeLocale("ja")

    private fun exerciseLearnedTvLandscapeLocale(localeSuffix: String) {
        val app = RuntimeEnvironment.getApplication()
        fun learned(id: String, key: ButtonKey) = RemoteButton(
            id = id,
            label = app.getString(key.labelRes),
            builtinLabel = key.name,
            key = key,
            isLearned = true,
            rawPattern = listOf(9000, 4500, 560)
        )
        val profile = RemoteProfile(
            id = "land_learned_tv_$localeSuffix",
            name = "Living room TV",
            category = DeviceCategory.TV,
            keySchemaVersion = 2,
            buttons = listOf(
                learned("power", ButtonKey.POWER),
                learned("source", ButtonKey.SOURCE),
                learned("mute", ButtonKey.MUTE),
                learned("direction_up", ButtonKey.UP),
                learned("direction_down", ButtonKey.DOWN),
                learned("direction_left", ButtonKey.LEFT),
                learned("direction_right", ButtonKey.RIGHT),
                learned("direction_ok", ButtonKey.OK),
                learned("back", ButtonKey.BACK),
                learned("home", ButtonKey.HOME),
                learned("menu", ButtonKey.MENU),
                learned("vol_up", ButtonKey.VOLUME_UP),
                learned("vol_down", ButtonKey.VOLUME_DOWN),
                learned("ch_up", ButtonKey.CHANNEL_UP),
                learned("ch_down", ButtonKey.CHANNEL_DOWN),
                learned("play", ButtonKey.PLAY),
                learned("pause", ButtonKey.PAUSE),
                learned("stop", ButtonKey.STOP),
                learned("subtitles", ButtonKey.SUBTITLES),
                learned("brightness", ButtonKey.BRIGHTNESS),
                learned("osd", ButtonKey.OSD),
                learned("timer", ButtonKey.TIMER),
                learned("sleep", ButtonKey.SLEEP),
                learned("info", ButtonKey.INFO)
            )
        )
        show(profile)
        composeRule.onNodeWithContentDescription(app.getString(R.string.control_up)).assertIsDisplayed()
        composeRule.onNodeWithText(app.getString(R.string.control_more_page)).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(app.getString(R.string.control_play)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(app.getString(R.string.control_picture_group)).performScrollTo().assertIsDisplayed()
        capture("remote_tv_learned_${localeSuffix}_landscape")
    }

    @Test
    fun narrowAcUsesSeparateRowsForModeFanAndSwing() {
        show(RemoteProfile("pane_ac_rows", "空调", category = DeviceCategory.AC, acProtocolId = 670))
        val mode = composeRule.onNodeWithContentDescription("模式").fetchSemanticsNode().boundsInRoot
        val fan = composeRule.onNodeWithContentDescription("风速").fetchSemanticsNode().boundsInRoot
        val swing = composeRule.onNodeWithContentDescription("上下扫风").fetchSemanticsNode().boundsInRoot
        assertTrue("mode and fan share the first row", mode.right <= fan.left)
        assertTrue("swing starts below the first row", mode.bottom <= swing.top && fan.bottom <= swing.top)
        capture("landscape_ac_narrow_control_rows")
    }

    @Test
    @Config(qualifiers = "en-rUS-w480dp-h320dp-land-mdpi")
    fun largeTextAcKeepsReadoutUnitAndTemperatureActionsReachable() {
        val app = RuntimeEnvironment.getApplication()
        show(RemoteProfile("pane_ac", "Air conditioner", category = DeviceCategory.AC, acProtocolId = 670), dark = true, fontScale = 2f)
        composeRule.onNodeWithText("°C", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        val readout = composeRule.onNodeWithText("°C", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pane = composeRule.onNodeWithTag("two_pane_primary").fetchSemanticsNode().boundsInRoot
        assertTrue(readout.left >= pane.left && readout.right <= pane.right)
        capture("landscape_ac_narrow_large_text_before")
        composeRule.onNodeWithContentDescription(app.getString(R.string.control_temp_up)).performScrollTo().performClick()
        composeRule.onNode(hasContentDescription("27", substring = true)).performScrollTo().assertIsDisplayed()
        capture("landscape_ac_narrow_large_text_after")
    }

    @Test
    fun genericKeysWithoutStandardControlsRemainCompleteAndClickable() {
        val profile = RemoteProfile("pane_generic", "学习遥控器", buttons = (1..12).map { key("custom_$it", "自定义$it") })
        show(profile)
        val split = remotePaneButtons(profile)
        val placed = split.primary + split.common + split.numbers + split.extensions
        assertEquals(profile.buttons.map { it.id }, placed.map { it.id })
        composeRule.onNodeWithContentDescription("自定义6").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("自定义12").performScrollTo().performClick()
        assertEquals(listOf("custom_6", "custom_12"), sent)
        capture("landscape_generic_without_standard_controls")
    }

    @Test
    fun genericDirectionOnlyPanelKeepsCompletePadInPrimaryPane() {
        val profile = RemoteProfile("pane_direction_only", "导航遥控器", buttons = listOf(
            key("nav_up", "上"), key("nav_down", "下"), key("nav_left", "左"), key("nav_right", "右"), key("nav_ok", "OK")))
        val split = remotePaneButtons(profile)
        assertEquals(profile.buttons.map { it.id }, split.primary.map { it.id })
        assertTrue((split.common + split.numbers + split.extensions).isEmpty())
        show(profile)
        val pane = composeRule.onNodeWithTag("two_pane_primary").fetchSemanticsNode().boundsInRoot
        listOf("上", "下", "左", "右", "OK").forEach { label ->
            val key = composeRule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertTrue(key.left >= pane.left && key.right <= pane.right)
        }
        composeRule.onNodeWithContentDescription("OK").performTouchInput { click() }
        assertEquals(listOf("nav_ok"), sent)
        composeRule.onNodeWithText(profile.name).assertIsDisplayed()
        capture("landscape_generic_direction_only")
    }

    @Test
    fun singleKeyKeepsIdentityBesideItsOnlyAction() {
        show(RemoteProfile("pane_single", "照明开关", room = "客厅", buttons = listOf(key("toggle", "开关", "power", ButtonKey.POWER))))
        val name = composeRule.onNodeWithText("照明开关").fetchSemanticsNode().boundsInRoot
        val action = composeRule.onNodeWithContentDescription("开关").fetchSemanticsNode().boundsInRoot
        assertTrue(name.right < action.left)
        composeRule.onNodeWithContentDescription("开关").performClick()
        assertEquals(listOf("toggle"), sent)
        capture("landscape_single_key")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w640dp-h360dp-land-mdpi")
    fun editorPreviewTracksVisibilityAndRestoringDefaults() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val profile = RemoteProfile("pane_editor", "风扇", category = DeviceCategory.FAN,
            buttons = listOf(
                key("power", "电源", "power", ButtonKey.POWER),
                key("speed", "风速", buttonKey = ButtonKey.FAN_SPEED),
                key("timer", "定时", buttonKey = ButtonKey.TIMER),
                key("mode", "模式", buttonKey = ButtonKey.MODE)
            ))
        var saved = RemotePanelLayout()
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                RemotePanelEditor(profile, emptySet(), onSave = { saved = it }, onBack = {})
            }
        }
        val tile = composeRule.onNodeWithContentDescription(app.getString(R.string.panel_show_control, "风速"))
        val previewSpeed = hasContentDescription("风速") and hasAnyAncestor(hasTestTag("panel_editor_preview"))
        composeRule.onNode(previewSpeed).performScrollTo().assertIsDisplayed()
        capture("landscape_editor_before")
        tile.performClick().assertIsOff()
        assertTrue("key:speed" in saved.hidden)
        composeRule.onNode(previewSpeed).assertDoesNotExist()
        capture("landscape_editor_hidden")
        composeRule.onNodeWithText(app.getString(R.string.panel_restore_default)).performScrollTo().performClick()
        composeRule.onAllNodesWithText(app.getString(R.string.panel_restore_default)).onLast().performClick()
        assertTrue(saved.hidden.isEmpty())
        tile.performScrollTo().assertIsOn()
        composeRule.onNode(previewSpeed).performScrollTo().assertIsDisplayed()
        capture("landscape_editor_restored")
    }
}
