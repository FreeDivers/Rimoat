package org.qinyan.rimoat.ui.components

import android.app.Application
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoundEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoundEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.AppColorMode
import org.qinyan.rimoat.data.BackupFileStore
import org.qinyan.rimoat.data.RemoteRepository
import org.qinyan.rimoat.data.ThemePreferences
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.screens.SettingsScreen
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    qualifiers = "zh-rCN-w411dp-h891dp-mdpi",
    application = Application::class
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class ExpressiveDropdownMenuRoborazziTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val screenshotOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(
            imageIoFormat = LosslessWebPImageIoFormat()
        )
    )

    private var expanded by mutableStateOf(false)
    private var selectedIndex by mutableIntStateOf(0)
    private var selections = 0
    private var clickSounds = 0
    private lateinit var popupView: View
    private val app get() = RuntimeEnvironment.getApplication()
    private val menuTitle get() = app.getString(R.string.settings_color_mode)
    private val lightLabel get() = app.getString(R.string.color_mode_light)

    private fun openMenu() {
        val sound = object : SoundEffect {
            override fun playClickSound() {
                clickSounds++
            }
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalSoundEffect provides sound) {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    Surface(Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.Center) {
                            Box {
                                Button(onClick = { expanded = true }) { Text(menuTitle) }
                                val options = listOf(
                                    R.string.color_mode_system,
                                    R.string.color_mode_light,
                                    R.string.color_mode_dark
                                )
                                ExpressiveDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    selectable = true
                                ) {
                                    val view = LocalView.current
                                    SideEffect { popupView = view }
                                    options.forEachIndexed { index, label ->
                                        ExpressiveSelectableMenuItem(
                                            selected = selectedIndex == index,
                                            onClick = {
                                                selections++
                                                selectedIndex = index
                                                expanded = false
                                            },
                                            text = { Text(app.getString(label)) },
                                            index = index,
                                            count = options.size
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithText(menuTitle).performClick()
        composeRule.onNode(isPopup()).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
    }

    private fun assertClosing() {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.onNode(isPopup()).assertExists()
    }

    private fun finishClosing() {
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNode(isPopup()).assertDoesNotExist()
        composeRule.onNodeWithText(menuTitle).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
    }

    @Test
    fun selectingOptionAnimatesPopupExitAndSilencesClickSound() {
        openMenu()
        val popupWindow = popupView.parent as View
        val initialPosition = IntArray(2).also { popupWindow.getLocationOnScreen(it) }
        popupWindow.captureRoboImage(
            "src/test/screenshots/expressive_dropdown_exit_open.webp",
            roborazziOptions = screenshotOptions
        )

        composeRule.onNodeWithText(lightLabel).performTouchInput { click() }
        assertClosing()
        composeRule.onNodeWithText(lightLabel).assertIsSelected().assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(1, selections)
            assertEquals(0, clickSounds)
            val closingPosition = IntArray(2).also { popupWindow.getLocationOnScreen(it) }
            assertEquals(initialPosition.toList(), closingPosition.toList())
        }
        popupWindow.captureRoboImage(
            "src/test/screenshots/expressive_dropdown_exit_closing.webp",
            roborazziOptions = screenshotOptions
        )

        finishClosing()
        composeRule.onRoot().captureRoboImage(
            "src/test/screenshots/expressive_dropdown_exit_closed.webp",
            roborazziOptions = screenshotOptions
        )
    }

    @Test
    fun outsideTapAnimatesDismissal() {
        openMenu()
        composeRule.runOnUiThread {
            val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_OUTSIDE, -1f, -1f, 0)
            try {
                (popupView.parent as View).dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }
        assertClosing()
        finishClosing()
        assertEquals(0, selections)
    }

    @Test
    fun backKeyAnimatesDismissal() {
        openMenu()
        composeRule.runOnUiThread {
            val window = popupView.parent as View
            window.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            window.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        assertClosing()
        finishClosing()
        assertEquals(0, selections)
    }

    @Test
    fun reopeningDuringExitRestoresPopupAndAcceptsSelection() {
        openMenu()
        composeRule.runOnUiThread { expanded = false }
        assertClosing()
        composeRule.runOnUiThread { expanded = true }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNode(isPopup()).assertIsDisplayed()

        composeRule.onNodeWithText(lightLabel).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(isPopup()).assertDoesNotExist()
        assertEquals(1, selections)

        composeRule.onNodeWithText(menuTitle).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(lightLabel).assertIsSelected().assertIsDisplayed()
    }

    @Test
    fun segmentedActionMenuRendersTwoExpressiveGroups() {
        var open by mutableStateOf(false)
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        Box {
                            Button(onClick = { open = true }) { Text("Actions") }
                            ExpressiveDropdownMenuPopup(
                                expanded = open,
                                onDismissRequest = { open = false }
                            ) {
                                ExpressiveDropdownMenuGroup(index = 0, count = 2) {
                                    ExpressiveMenuItem(
                                        onClick = { open = false },
                                        text = { Text(app.getString(R.string.screen_favorite)) },
                                        index = 0,
                                        count = 3,
                                        leadingIcon = { Icon(Icons.Rounded.Star, null) }
                                    )
                                    ExpressiveMenuItem(
                                        onClick = { open = false },
                                        text = { Text(app.getString(R.string.screen_edit_details)) },
                                        index = 1,
                                        count = 3,
                                        leadingIcon = { Icon(Icons.Rounded.Edit, null) }
                                    )
                                    ExpressiveMenuItem(
                                        onClick = { open = false },
                                        text = { Text(app.getString(R.string.screen_export_remote)) },
                                        index = 2,
                                        count = 3,
                                        leadingIcon = { Icon(Icons.Rounded.Upload, null) }
                                    )
                                }
                                ExpressiveDropdownMenuGroup(index = 1, count = 2) {
                                    ExpressiveMenuItem(
                                        onClick = { open = false },
                                        text = { Text(app.getString(R.string.screen_trash_remote)) },
                                        index = 0,
                                        count = 1,
                                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithText("Actions").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(isPopup()).assertIsDisplayed().captureRoboImage(
            filePath = "src/test/screenshots/expressive_dropdown_segmented.webp",
            roborazziOptions = screenshotOptions
        )
    }

    @Test
    fun settingsDropdownUsesExpressiveMenuAndUpdatesSelection() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = RemoteRepository(app, scope)
        runBlocking { repository.awaitLoaded() }
        val transmitterManager = IrTransmitterManager(app, scope)
        val themePreferences = ThemePreferences(app)
        themePreferences.setColorMode(AppColorMode.SYSTEM)
        val viewModel = RemoteListViewModel(
            repository = repository,
            transmitterManager = transmitterManager,
            themePreferences = themePreferences,
            backupFiles = BackupFileStore(app)
        )

        try {
            composeRule.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    SettingsScreen(viewModel = viewModel, onBack = {})
                }
            }

            val colorModeTitle = app.getString(R.string.settings_color_mode)
            val systemLabel = app.getString(R.string.color_mode_system)
            val lightLabel = app.getString(R.string.color_mode_light)
            val darkLabel = app.getString(R.string.color_mode_dark)

            composeRule.onNodeWithText(colorModeTitle).assertIsDisplayed().performClick()
            composeRule.waitForIdle()

            val popupNode = composeRule.onNode(isPopup())
            popupNode.assertIsDisplayed()
            popupNode.captureRoboImage(
                filePath = "src/test/screenshots/expressive_dropdown_before.webp",
                roborazziOptions = screenshotOptions
            )

            val systemOption = composeRule.onNode(hasText(systemLabel) and hasAnyAncestor(isPopup()))
            val lightOption = composeRule.onNode(hasText(lightLabel) and hasAnyAncestor(isPopup()))
            val darkOption = composeRule.onNode(hasText(darkLabel) and hasAnyAncestor(isPopup()))

            systemOption.assertIsSelected()
            lightOption.assertIsNotSelected()
            darkOption.assertIsNotSelected()

            composeRule.mainClock.autoAdvance = false
            darkOption.performClick()
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(80)
            composeRule.onNode(isPopup()).assertExists().captureRoboImage(
                filePath = "src/test/screenshots/expressive_dropdown_settings_closing.webp",
                roborazziOptions = screenshotOptions
            )
            assertEquals(AppColorMode.SYSTEM, themePreferences.colorMode.value)
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            composeRule.onNode(isPopup()).assertDoesNotExist()
            assertEquals(AppColorMode.DARK, themePreferences.colorMode.value)
            composeRule.onRoot().captureRoboImage(
                filePath = "src/test/screenshots/expressive_dropdown_settings_closed.webp",
                roborazziOptions = screenshotOptions
            )

            composeRule.onNodeWithText(colorModeTitle).performClick()
            composeRule.waitForIdle()

            val reopenedDarkOption = composeRule.onNode(hasText(darkLabel) and hasAnyAncestor(isPopup()))
            reopenedDarkOption.assertIsSelected()
            composeRule.onNode(hasText(systemLabel) and hasAnyAncestor(isPopup())).assertIsNotSelected()

            composeRule.onNode(isPopup()).captureRoboImage(
                filePath = "src/test/screenshots/expressive_dropdown_after.webp",
                roborazziOptions = screenshotOptions
            )
        } finally {
            transmitterManager.release()
            runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }
}
