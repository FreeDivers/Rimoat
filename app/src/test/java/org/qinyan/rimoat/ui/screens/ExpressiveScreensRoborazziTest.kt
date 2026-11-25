package org.qinyan.rimoat.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.db.IrRemoteModel
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.AddRemoteUiState
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w411dp-h891dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class ExpressiveScreensRoborazziTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    private fun capture(name: String) = compose.onRoot().captureRoboImage(
        "src/test/screenshots/expressive_$name.webp", roborazziOptions = options
    )

    @Test
    fun categoryPressDefersSelectionUntilReleaseAndPreservesSelectedSemantics() {
        AppStrings.initialize(app)
        var category by mutableStateOf<DeviceCategory?>(null)
        var selections = 0
        compose.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column {
                        CategoryFilterBar(category) { category = it; selections++ }
                    }
                }
            }
        }
        compose.onNodeWithText(app.getString(R.string.screen_category_all)).assertIsSelected()
        capture("categories_before")
        val television = compose.onNodeWithText(app.getString(R.string.screen_category_tv))
        compose.mainClock.autoAdvance = false
        television.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        assertEquals(0, selections)
        capture("categories_pressed")
        television.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        television.assertIsSelected()
        assertEquals(DeviceCategory.TV, category)
        assertEquals(1, selections)
        capture("categories_selected")
    }

    @Test
    fun remoteCardMorphKeepsItsNameAndOpensOnlyOnRelease() {
        AppStrings.initialize(app)
        var opened = 0
        val remote = RemoteProfile(id = "expressive_remote", name = "Living room television", category = DeviceCategory.TV)
        compose.setContent {
            IrRemoteTheme(darkTheme = true, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp)) {
                        RemoteCard(remote, onClick = { opened++ }, onToggleFavorite = {}, onDelete = {})
                    }
                }
            }
        }
        val card = compose.onNodeWithText(remote.name)
        capture("card_before")
        compose.mainClock.autoAdvance = false
        card.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        card.assertIsDisplayed()
        assertEquals(0, opened)
        capture("card_pressed")
        card.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    @Config(qualifiers = "en-w320dp-h568dp-mdpi")
    fun pairingToolbarKeepsLargeTextActionReachableAndHonorsBusyState() {
        AppStrings.initialize(app)
        val model = IrRemoteModel("expressive_code", 1, "TV", 1, "Example", "Example", 1, "", 1)
        var page by mutableStateOf(AddRemoteUiState(remotes = listOf(model)))
        var saves = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                IrRemoteTheme(darkTheme = true, dynamicColor = false) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            CodeMatchingActions(page, onMatched = { saves++ }, onConfirmTest = {})
                        }
                    }
                }
            }
        }
        val save = compose.onNodeWithText(app.getString(R.string.screen_use_code))
        save.performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, saves)
        capture("pairing_toolbar_large")
        compose.runOnIdle { page = page.copy(isSendingTest = true) }
        save.assertIsNotEnabled()
        capture("pairing_toolbar_busy")
    }
}
