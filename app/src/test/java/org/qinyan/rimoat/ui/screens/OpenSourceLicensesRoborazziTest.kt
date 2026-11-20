package org.qinyan.rimoat.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    qualifiers = "zh-rCN-w411dp-h891dp-mdpi",
    // This isolated page needs resources, not the application's hardware startup.
    application = Application::class
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class OpenSourceLicensesRoborazziTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchFiltersLibrariesAndOpensSelectedLicense() {
        AppStrings.initialize(RuntimeEnvironment.getApplication())
        var selectedLibrary: String? = null
        val screenshotOptions = RoborazziOptions(
            recordOptions = RoborazziOptions.RecordOptions(
                imageIoFormat = LosslessWebPImageIoFormat()
            )
        )

        composeRule.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                OpenSourceLicensesScreen(
                    onBack = {},
                    onLibraryClick = { selectedLibrary = it }
                )
            }
        }
        // The real screen loads its bundled license catalog on Dispatchers.IO.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Activity").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/licenses_before.webp",
            roborazziOptions = screenshotOptions
        )

        composeRule.onNode(hasSetTextAction()).performClick().performTextInput("mik3y")
        composeRule.onNodeWithText("usb-serial-for-android").assertIsDisplayed()
        composeRule.onNodeWithText("Activity").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/licenses_after.webp",
            roborazziOptions = screenshotOptions
        )

        composeRule.onNodeWithText("usb-serial-for-android").performClick()
        composeRule.runOnIdle {
            assertEquals("com.github.mik3y:usb-serial-for-android:3.9.0", selectedLibrary)
        }
    }
}
