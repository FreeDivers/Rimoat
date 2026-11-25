package org.qinyan.rimoat.ui.components

import android.app.Application
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
class SimpleDropdownMenuRoborazziTest {
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
    private lateinit var popupView: View
    private val app get() = RuntimeEnvironment.getApplication()
    private val menuTitle get() = app.getString(R.string.settings_color_mode)
    private val lightLabel get() = app.getString(R.string.color_mode_light)

    private fun openMenu() {
        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        Box {
                            Button(onClick = { expanded = true }) { Text(menuTitle) }
                            SimpleDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                selectedIndex = selectedIndex,
                                itemCount = 3
                            ) {
                                val view = LocalView.current
                                SideEffect { popupView = view }
                                listOf(R.string.color_mode_system, R.string.color_mode_light, R.string.color_mode_dark)
                                    .forEachIndexed { index, label ->
                                        SimpleDropdownMenuItem(
                                            text = { Text(app.getString(label)) },
                                            selected = selectedIndex == index,
                                            onClick = {
                                                selections++
                                                selectedIndex = index
                                                expanded = false
                                            }
                                        )
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
        composeRule.runOnIdle {
            assertTrue("The whole popup must fade before removal: alpha=${popupView.alpha}, expanded=$expanded",
                popupView.alpha > 0f && popupView.alpha < 1f)
            assertTrue("The popup must shrink smoothly", popupView.scaleX > 0.98f && popupView.scaleX < 1f)
            assertEquals(popupView.scaleX, popupView.scaleY, 0f)
        }
    }

    private fun finishClosing() {
        composeRule.mainClock.advanceTimeUntil(timeoutMillis = 2_000) {
            !popupView.isAttachedToWindow || popupView.alpha == 0f
        }
        composeRule.runOnIdle {
            if (popupView.isAttachedToWindow) {
                assertEquals("The fade must be complete", 0f, popupView.alpha, 0f)
            }
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNode(isPopup()).assertDoesNotExist()
        composeRule.onNodeWithText(menuTitle).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
    }

    @Test
    fun selectingOptionFadesAndShrinksWholePopupBeforeRemoval() {
        openMenu()
        val popupWindow = popupView.parent as View
        val initialPosition = IntArray(2).also { popupWindow.getLocationOnScreen(it) }
        popupWindow.captureRoboImage("src/test/screenshots/simple_dropdown_exit_open.webp", roborazziOptions = screenshotOptions)

        composeRule.onNodeWithText(lightLabel).performClick()
        assertClosing()
        composeRule.onNodeWithText(lightLabel).assertIsSelected().assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(1, selections)
            val closingPosition = IntArray(2).also { popupWindow.getLocationOnScreen(it) }
            assertEquals(initialPosition.toList(), closingPosition.toList())
        }
        popupWindow.captureRoboImage("src/test/screenshots/simple_dropdown_exit_closing.webp", roborazziOptions = screenshotOptions)

        finishClosing()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/simple_dropdown_exit_closed.webp", roborazziOptions = screenshotOptions)
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
        val originalView = popupView
        composeRule.runOnUiThread { expanded = false }
        assertClosing()
        composeRule.runOnUiThread { expanded = true }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeUntil(timeoutMillis = 2_000) {
            popupView.alpha == 1f && popupView.scaleX == 1f
        }
        composeRule.onNode(isPopup()).assertIsDisplayed()
        composeRule.runOnIdle {
            assertSame(originalView, popupView)
            assertEquals(1f, popupView.alpha, 0f)
            assertEquals(1f, popupView.scaleX, 0f)
        }
        composeRule.onNodeWithText(lightLabel).performClick()
        assertClosing()
        finishClosing()
        assertEquals(1, selections)

        composeRule.onNodeWithText(menuTitle).performClick()
        composeRule.mainClock.advanceTimeBy(240)
        composeRule.onNodeWithText(lightLabel).assertIsSelected().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1f, popupView.alpha, 0f) }
    }

    @Test
    fun settingsDropdownUsesSimpleMenuHighlightAndAlignsSelectedOptionOverTrigger() {
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
                filePath = "src/test/screenshots/simple_dropdown_before.webp",
                roborazziOptions = screenshotOptions
            )

            val systemOption = composeRule.onNode(hasText(systemLabel) and hasAnyAncestor(isPopup()))
            val lightOption = composeRule.onNode(hasText(lightLabel) and hasAnyAncestor(isPopup()))
            val darkOption = composeRule.onNode(hasText(darkLabel) and hasAnyAncestor(isPopup()))

            systemOption.assertIsSelected()
            lightOption.assertIsNotSelected()
            darkOption.assertIsNotSelected()

            // Click Dark mode option and re-open to verify selection updates and aligns to the third item.
            composeRule.mainClock.autoAdvance = false
            darkOption.performClick()
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(80)
            composeRule.onNode(isPopup()).assertExists().captureRoboImage(
                filePath = "src/test/screenshots/simple_dropdown_settings_closing.webp",
                roborazziOptions = screenshotOptions
            )
            assertEquals(AppColorMode.SYSTEM, themePreferences.colorMode.value)
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            composeRule.onNode(isPopup()).assertDoesNotExist()
            assertEquals(AppColorMode.DARK, themePreferences.colorMode.value)
            composeRule.onRoot().captureRoboImage(
                filePath = "src/test/screenshots/simple_dropdown_settings_closed.webp",
                roborazziOptions = screenshotOptions
            )

            composeRule.onNodeWithText(colorModeTitle).performClick()
            composeRule.waitForIdle()

            val reopenedDarkOption = composeRule.onNode(hasText(darkLabel) and hasAnyAncestor(isPopup()))
            reopenedDarkOption.assertIsSelected()
            composeRule.onNode(hasText(systemLabel) and hasAnyAncestor(isPopup())).assertIsNotSelected()

            // Verify the selected third item is positioned near the bottom of the 3-item menu.
            val popupBounds = composeRule.onNode(isPopup()).fetchSemanticsNode().boundsInRoot
            val selectedBounds = reopenedDarkOption.fetchSemanticsNode().boundsInRoot
            val expectedBottomPadding = 8f
            assertEquals(popupBounds.bottom - expectedBottomPadding, selectedBounds.bottom, 2f)

            composeRule.onNode(isPopup()).captureRoboImage(
                filePath = "src/test/screenshots/simple_dropdown_after.webp",
                roborazziOptions = screenshotOptions
            )
        } finally {
            transmitterManager.release()
            runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }
}
