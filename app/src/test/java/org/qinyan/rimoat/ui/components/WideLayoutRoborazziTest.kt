package org.qinyan.rimoat.ui.components

import android.app.Application
import android.os.Build
import android.view.View
import android.view.WindowInsets as PlatformWindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.screens.ConfirmRemoteView
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.AddRemoteUiState
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w640dp-h360dp-land-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class WideLayoutRoborazziTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var composeView: View
    private var observedWide = false
    private var observedImeBottom = 0
    private var draft by mutableStateOf(AddRemoteUiState())
    private var saves = 0
    private val app get() = RuntimeEnvironment.getApplication()
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    private fun showForm() {
        AppStrings.initialize(app)
        compose.setContent {
            val view = LocalView.current
            val wide = rememberWideLayout()
            val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
            SideEffect {
                composeView = view
                observedWide = wide
                observedImeBottom = imeBottom
            }
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    ConfirmRemoteView(draft, { draft = draft.copy(customRemoteName = it) }, { saves++ })
                }
            }
        }
        compose.waitForIdle()
    }

    @Suppress("DEPRECATION")
    private fun dispatchInsets(imeBottom: Int = 0, rightBar: Int = 0) {
        compose.runOnUiThread {
            // Apply a completed inset change, without a synthetic IME animation between frames.
            ViewCompat.setWindowInsetsAnimationCallback(composeView, null)
            val insets = if (Build.VERSION.SDK_INT >= 30) {
                PlatformWindowInsets.Builder()
                    .setInsets(PlatformWindowInsets.Type.ime(), android.graphics.Insets.of(0, 0, 0, imeBottom))
                    .setVisible(PlatformWindowInsets.Type.ime(), imeBottom > 0)
                    .setInsets(PlatformWindowInsets.Type.navigationBars(), android.graphics.Insets.of(0, 0, rightBar, 0))
                    .setVisible(PlatformWindowInsets.Type.navigationBars(), rightBar > 0)
                    .build()
            } else {
                // Before API 30 the compatibility layer derives IME from system vs stable insets.
                checkNotNull(WindowInsetsCompat.Builder()
                    .setSystemWindowInsets(androidx.core.graphics.Insets.of(0, 0, rightBar, imeBottom))
                    .setStableInsets(androidx.core.graphics.Insets.NONE)
                    .build().toWindowInsets())
            }
            composeView.dispatchApplyWindowInsets(insets)
        }
        compose.waitForIdle()
        assertEquals("The form must receive the simulated IME inset", imeBottom, observedImeBottom)
    }

    private fun capture(name: String) = compose.onRoot().captureRoboImage(
        "src/test/screenshots/landscape_window_$name.webp", roborazziOptions = options
    )

    private fun assertFormKeepsTwoPanesWithIme(screenshot: String) {
        showForm()
        dispatchInsets()
        assertTrue(observedWide)
        compose.onNodeWithTag("two_pane_secondary").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("Study television")
        capture("${screenshot}_before")

        dispatchInsets(imeBottom = 180)
        assertTrue("IME visibility must not change the window layout", observedWide)
        compose.onNodeWithTag("two_pane_secondary").assertExists()
        compose.onNodeWithText(app.getString(R.string.screen_save)).performScrollTo().assertIsDisplayed().performClick()
        assertEquals("Study television", draft.customRemoteName)
        assertEquals(1, saves)
        capture("${screenshot}_after")

        dispatchInsets()
        assertTrue(observedWide)
        assertEquals("Study television", draft.customRemoteName)
    }

    @Test
    @Config(sdk = [28])
    fun legacyWindowKeepsTwoPanesWhileImeInsetsChange() {
        assertFormKeepsTwoPanesWithIme("api28_ime")
    }

    @Test
    fun currentWindowKeepsTwoPanesWhileImeInsetsChange() {
        assertFormKeepsTwoPanesWithIme("api35_ime")
    }

    @Test
    @Config(qualifiers = "en-w480dp-h320dp-land-mdpi")
    fun currentWindowSubtractsSafeInsetsAtThe480DpBoundary() {
        assertFalse(isWideLayout(479.dp, 320.dp))
        assertTrue(isWideLayout(480.dp, 320.dp))
        assertFalse(isWideLayout(480.dp, 400.dp))
        assertFalse(isWideLayout(480.dp, 640.dp))
        showForm()
        dispatchInsets()
        assertTrue(observedWide)
        compose.onNodeWithTag("two_pane_primary").assertExists()
        capture("api35_480_before")

        dispatchInsets(rightBar = 1)
        assertFalse("Safe horizontal space below 480dp must use one pane", observedWide)
        compose.onNodeWithTag("two_pane_primary").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        capture("api35_479_after")

        dispatchInsets()
        assertTrue(observedWide)
        compose.onNodeWithTag("two_pane_primary").assertExists()
    }
}
