package org.qinyan.rimoat.ui.screens

import android.app.Application
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w480dp-h320dp-land-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class TwoPaneFoundationRoborazziTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val managers = mutableListOf<IrTransmitterManager>()
    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))

    @After fun cleanup() {
        managers.forEach { it.release() }
        runBlocking { job.cancelAndJoin() }
    }

    private fun model(profiles: List<RemoteProfile> = emptyList()): RemoteListViewModel {
        AppStrings.initialize(app)
        val repository = RemoteRepository(app, scope)
        runBlocking {
            repository.awaitLoaded()
            profiles.forEach { repository.saveRemote(it) }
        }
        val manager = IrTransmitterManager(app, scope).also { managers += it }
        return RemoteListViewModel(repository, manager, ThemePreferences(app), BackupFileStore(app))
    }

    private fun scrollPane(tag: String) = compose.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag(tag)))
    private fun capture(name: String) = compose.onRoot().captureRoboImage(
        "src/test/screenshots/landscape_foundation_$name.webp", roborazziOptions = options)

    @Test fun narrowHomeKeepsAddAndSearchBesideResults() {
        val tv = RemoteProfile(id = "living_tv", name = "客厅电视", category = DeviceCategory.TV)
        val fan = RemoteProfile(id = "bedroom_fan", name = "卧室风扇", category = DeviceCategory.FAN)
        val vm = model(listOf(tv, fan))
        var addClicks = 0
        var opens = 0
        var opened: String? = null
        compose.setContent { IrRemoteTheme(darkTheme = false, dynamicColor = false) {
            RemoteListScreen(vm, { opened = it; opens++ }, { addClicks++ })
        } }
        val left = compose.onNodeWithTag("two_pane_primary").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("two_pane_secondary").fetchSemanticsNode().boundsInRoot
        assertTrue(left.right < right.left)
        assertTrue(right.width > 240)
        capture("home_480_before")
        compose.onNode(hasSetTextAction()).performTextInput("客厅")
        compose.waitUntil(5_000) { vm.filteredRemotes.value.size == 1 }
        compose.onNodeWithText(tv.name).assertIsDisplayed().performClick()
        assertEquals(tv.id, opened)
        assertEquals(1, opens)
        scrollPane("two_pane_primary").performScrollToNode(hasText(app.getString(R.string.screen_add_remote)))
        compose.onNodeWithText(app.getString(R.string.screen_add_remote)).performClick()
        assertEquals(1, addClicks)
        capture("home_480_after")
    }

    @Test fun emptyHomeKeepsOneAddActionAndRealEmptyState() {
        val vm = model()
        var clicks = 0
        compose.setContent { IrRemoteTheme(darkTheme = false, dynamicColor = false) {
            RemoteListScreen(vm, {}, { clicks++ })
        } }
        compose.onNodeWithText(app.getString(R.string.screen_no_saved_remotes)).assertIsDisplayed()
        compose.onAllNodesWithText(app.getString(R.string.screen_add_remote)).assertCountEquals(1)
        compose.onNodeWithText(app.getString(R.string.screen_add_remote)).performClick()
        assertEquals(1, clicks)
        capture("home_empty_480")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w891dp-h411dp-land-mdpi")
    fun homeRestoresStableAnchorAcrossLayoutRecreation() {
        val vm = model((0..24).map { RemoteProfile("remote_$it", "遥控器 $it", category = DeviceCategory.TV) })
        var portrait = true
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = if (portrait) 411 else 891
                screenHeightDp = if (portrait) 891 else 411
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalInspectionMode provides true) {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    Box(Modifier.width(configuration.screenWidthDp.dp)) { RemoteListScreen(vm, {}, {}) }
                }
            }
        }
        compose.onNodeWithTag("home_results").performScrollToIndex(8)
        compose.waitForIdle()
        val anchor = vm.filteredRemotes.value[8].name
        compose.onNodeWithText(anchor).assertIsDisplayed()
        portrait = false
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithTag("two_pane_secondary").assertExists()
        compose.onNodeWithText(anchor).assertIsDisplayed()
        capture("home_restored_anchor")
    }

    @Test fun narrowSettingsScrollEachTaskIndependently() {
        val vm = model()
        var aboutClicks = 0
        compose.setContent { IrRemoteTheme(darkTheme = false, dynamicColor = false) {
            SettingsScreen(vm, {}, onAboutClick = { aboutClicks++ })
        } }
        val rooms = app.getString(R.string.settings_group_remotes)
        val initialRight = compose.onNodeWithText(rooms).fetchSemanticsNode().boundsInRoot
        capture("settings_480_before")
        scrollPane("two_pane_primary").performScrollToNode(hasText(app.getString(R.string.settings_backup_all)))
        assertEquals(initialRight, compose.onNodeWithText(rooms).fetchSemanticsNode().boundsInRoot)
        scrollPane("two_pane_secondary").performScrollToNode(hasText(app.getString(R.string.settings_about)))
        compose.onNodeWithText(app.getString(R.string.settings_about)).assertIsDisplayed().performClick()
        assertEquals(1, aboutClicks)
        compose.onNodeWithText(app.getString(R.string.settings_backup_all)).assertIsDisplayed()
        capture("settings_480_after")
    }

    @Test
    @Config(qualifiers = "en-w1024dp-h600dp-land-mdpi")
    fun largeTextDarkSettingsKeepValuesAndActionsReachable() {
        RuntimeEnvironment.setFontScale(2f)
        val vm = model()
        compose.setContent { IrRemoteTheme(darkTheme = true, dynamicColor = false) { SettingsScreen(vm, {}) } }
        compose.onNodeWithText(app.getString(R.string.settings_color_mode)).assertIsDisplayed().performClick()
        compose.onNodeWithText(app.getString(R.string.color_mode_dark)).assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(AppColorMode.DARK, vm.colorMode.value)
        scrollPane("two_pane_primary").performScrollToNode(hasText(app.getString(R.string.settings_backup_all)))
        scrollPane("two_pane_secondary").performScrollToNode(hasText(app.getString(R.string.settings_about)))
        compose.onNodeWithText(app.getString(R.string.settings_about)).assertIsDisplayed()
        capture("settings_1024_dark_200")
    }
}
