package org.qinyan.rimoat.ui.components

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import kotlin.math.abs

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
            darkOption.performClick()
            composeRule.waitForIdle()
            assertEquals(AppColorMode.DARK, themePreferences.colorMode.value)

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
