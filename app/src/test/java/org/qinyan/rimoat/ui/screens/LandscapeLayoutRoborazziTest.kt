package org.qinyan.rimoat.ui.screens

import android.app.Application
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.BackupFileStore
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemotePanelLayout
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.RemoteRepository
import org.qinyan.rimoat.data.ThemePreferences
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w891dp-h411dp-land-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class LandscapeLayoutRoborazziTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun key(id: String, label: String, icon: String? = null) =
        RemoteButton(id, label, iconName = icon, rawPattern = listOf(9000, 4500, 560))

    private fun tv() = RemoteProfile(
        id = "land_tv", name = "客厅电视", category = DeviceCategory.TV,
        buttons = listOf(
            key("power", "电源", "power"), key("source", "信源"), key("mute", "静音"),
            key("tv_up", "上"), key("tv_down", "下"), key("tv_left", "左"), key("tv_right", "右"), key("tv_ok", "OK"),
            key("back", "返回"), key("home", "主页"), key("menu", "菜单"),
            key("vol_up", "音量+", "volume_up"), key("vol_down", "音量-", "volume_down"),
            key("ch_up", "频道+", "arrow_drop_up"), key("ch_down", "频道-", "arrow_drop_down")
        ) + (0..9).map { key("tv_num_$it", "$it") }
    )

    private fun fan() = RemoteProfile(
        id = "land_fan", name = "卧室风扇", category = DeviceCategory.FAN,
        buttons = listOf(
            key("power", "电源", "power"), key("speed", "风速"), key("swing", "摇头"),
            key("timer", "定时"), key("mode", "模式"), key("natural", "自然风")
        )
    )

    private fun ac() = RemoteProfile(
        id = "land_ac", name = "主卧空调", category = DeviceCategory.AC, acProtocolId = 670
    )

    private fun remoteViewModel(profile: RemoteProfile): RemoteControlViewModel {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val repository = RemoteRepository(app, scope)
        runBlocking {
            repository.awaitLoaded()
            repository.saveRemote(profile)
        }
        return RemoteControlViewModel(
            remoteId = profile.id,
            initialProfile = profile,
            repository = repository,
            transmitterManager = IrTransmitterManager(app, scope),
            learnerManager = IrLearnerManager(app, scope)
        )
    }

    private fun showRemote(profile: RemoteProfile) {
        val viewModel = remoteViewModel(profile)
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                RemoteControlScreen(viewModel = viewModel, onBack = {})
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(text: String): Rect =
        composeRule.onAllNodesWithText(text).onFirst().also { it.assertIsDisplayed() }.fetchSemanticsNode().boundsInRoot

    private fun buttonBounds(description: String): Rect =
        composeRule.onAllNodes(androidx.compose.ui.test.hasContentDescription(description)).onFirst()
            .also { it.assertIsDisplayed() }.fetchSemanticsNode().boundsInRoot

    private fun capture(name: String) =
        composeRule.onRoot().captureRoboImage("src/test/screenshots/landscape_$name.webp", roborazziOptions = options)

    @Test
    fun tvPanelUsesTwoPanesWithoutScrolling() {
        val app = RuntimeEnvironment.getApplication()
        showRemote(tv())
        val ok = buttonBounds("OK")
        val power = buttonBounds("电源")
        val volumeDown = buttonBounds(app.getString(R.string.screen_volume_down))
        val back = buttonBounds("返回")
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("D-pad sits on the left", ok.center.x < power.center.x)
        assertTrue("navigation stays beside the direction pad", back.center.x < root.width / 2)
        assertTrue("power and volume stay in the right pane", power.center.x > root.width / 2 && volumeDown.center.x > root.width / 2)
        assertTrue("D-pad and rockers fit without clipping", ok.bottom <= root.height && volumeDown.bottom <= root.height)
        capture("tv")
    }

    @Test
    fun fanPanelPutsFrequentControlsLeftAndExtensionsRight() {
        showRemote(fan())
        val power = buttonBounds("电源")
        val swing = buttonBounds("摇头")
        assertTrue("power stays left of extension grid", power.right < swing.left)
        capture("fan")
    }

    @Test
    fun singleHiddenCustomButtonShowsEmptyStateInLandscape() {
        val app = RuntimeEnvironment.getApplication()
        val onlyKey = key("mute", "静音")
        val profile = RemoteProfile(
            id = "land_hidden_single", name = "单键遥控器", category = DeviceCategory.CUSTOM,
            buttons = listOf(onlyKey),
            panelLayout = RemotePanelLayout(hidden = setOf("key:mute"))
        )
        showRemote(profile)
        composeRule.onNodeWithText(app.getString(R.string.screen_no_buttons)).assertIsDisplayed()
        composeRule.onAllNodes(androidx.compose.ui.test.hasContentDescription("静音")).fetchSemanticsNodes().let {
            assertTrue("hidden single button should not be displayed", it.isEmpty())
        }
        capture("hidden_single_empty")
    }

    @Test
    fun twoCustomButtonsWithOneHiddenUsesSingleKeyLandscapeLayout() {
        val visible = key("mute", "静音")
        val hidden = key("menu", "菜单")
        val profile = RemoteProfile(
            id = "land_two_one_hidden", name = "简化遥控器", room = "书房", category = DeviceCategory.CUSTOM,
            buttons = listOf(visible, hidden),
            panelLayout = RemotePanelLayout(hidden = setOf("key:menu"))
        )
        showRemote(profile)
        val name = bounds("简化遥控器")
        val action = buttonBounds("静音")
        assertTrue("identity sits beside the only visible button", name.right < action.left)
        composeRule.onAllNodes(androidx.compose.ui.test.hasContentDescription("菜单")).fetchSemanticsNodes().let {
            assertTrue("hidden button should not be displayed", it.isEmpty())
        }
    }

    @Test
    fun acPanelShowsTemperatureBesideModeKeys() {
        val app = RuntimeEnvironment.getApplication()
        showRemote(ac())
        val width = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val tempUp = buttonBounds(app.getString(R.string.control_temp_up))
        assertTrue("temperature controls on the left half", tempUp.center.x < width / 2)
        assertTrue("mode key on the right half", buttonBounds("模式").center.x > width / 2)
        capture("ac")
    }

    @Test
    fun homeListKeepsFiltersInLeftPaneAndOpensRemote() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        // Saved timestamps keep this layout fixture independent of wall-clock save timing.
        val profiles = listOf(
            tv().copy(updatedAt = 1_000L),
            fan().copy(updatedAt = 3_000L),
            ac().copy(updatedAt = 2_000L),
            tv().copy(id = "land_tv2", name = "书房电视", updatedAt = 4_000L)
        )
        val directory = File(app.filesDir, "remotes").apply { mkdirs() }
        val json = Json { encodeDefaults = true }
        profiles.forEach { File(directory, "${it.id}.json").writeText(json.encodeToString(it)) }
        val repository = RemoteRepository(app, scope)
        runBlocking { repository.awaitLoaded() }
        assertEquals(listOf("land_tv2", "land_fan", "land_ac", "land_tv"), repository.remotes.value.map { it.id })
        assertEquals(profiles.associate { it.id to it.updatedAt }, repository.remotes.value.associate { it.id to it.updatedAt })
        var opened: String? = null
        val viewModel = RemoteListViewModel(repository, IrTransmitterManager(app, scope), ThemePreferences(app), BackupFileStore(app))
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                RemoteListScreen(viewModel = viewModel, onRemoteClick = { opened = it }, onAddRemoteClick = {})
            }
        }
        composeRule.waitForIdle()
        val filter = bounds(app.getString(R.string.screen_category_all))
        val card = bounds("客厅电视")
        assertTrue("filters sit left of the list", filter.right < card.left)
        capture("home")
        composeRule.onNodeWithText("客厅电视").performClick()
        composeRule.waitForIdle()
        assertTrue(opened == "land_tv")
    }

    @Test
    fun settingsGroupTasksIntoTwoPanes() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val repository = RemoteRepository(app, scope)
        runBlocking { repository.awaitLoaded() }
        val viewModel = RemoteListViewModel(repository, IrTransmitterManager(app, scope), ThemePreferences(app), BackupFileStore(app))
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreen(viewModel = viewModel, onBack = {})
            }
        }
        composeRule.waitForIdle()
        val width = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val appearance = bounds(app.getString(R.string.settings_group_appearance))
        val remotes = bounds(app.getString(R.string.settings_group_remotes))
        assertTrue("appearance sits in left column", appearance.center.x < width / 2)
        assertTrue("remotes sits in right column", remotes.center.x > width / 2)
        capture("settings")
    }
}
