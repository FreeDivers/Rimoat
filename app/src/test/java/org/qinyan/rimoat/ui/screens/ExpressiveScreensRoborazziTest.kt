package org.qinyan.rimoat.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
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

    @Test
    @Config(qualifiers = "en-w320dp-h568dp-mdpi")
    fun pairingAnswersFitNarrowPaneAndOverflowCommitsOnlyOnce() = exercisePairingOverflow(fontScale = 1f)

    @Test
    @Config(qualifiers = "en-w320dp-h568dp-mdpi")
    fun pairingOverflowKeepsBothAnswersReachableWithLargeText() = exercisePairingOverflow(fontScale = 2f)

    private fun exercisePairingOverflow(fontScale: Float) {
        RuntimeEnvironment.setFontScale(fontScale)
        AppStrings.initialize(app)
        val model = IrRemoteModel("expressive_code", 1, "TV", 1, "Example", "Example", 1, "", 1)
        val power = RemoteButton(id = "expressive_power", label = "Power", iconName = "power")
        var page by mutableStateOf(AddRemoteUiState(
            remotes = listOf(model), testedButton = power, canConfirmTest = true
        ))
        val answers = mutableListOf<Boolean>()
        val paneWidth = if (fontScale == 1f) 200.dp else 296.dp
        val prefix = if (fontScale == 1f) "pairing_answers_narrow" else "pairing_answers_large"
        val noResponse = app.getString(R.string.screen_no_response)
        val responded = app.getString(R.string.screen_responded)
        compose.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.width(paneWidth).verticalScroll(rememberScrollState())) {
                            CodeMatchingActions(page, onMatched = {}, onConfirmTest = {
                                answers += it
                                page = page.copy(canConfirmTest = false, verifiedFunctions = setOf(power.id))
                            })
                        }
                    }
                }
            }
        }
        val overflow = compose.onNodeWithTag("pairing_answers_overflow")
        fun assertAnswerLayout() {
            val overflowBounds = overflow.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val groupBounds = compose.onNodeWithTag("pairing_answers_group")
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val toolbarBounds = compose.onNodeWithTag("pairing_confirmation_toolbar")
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val minimumTouchSize = with(compose.density) { 48.dp.toPx() }
            assertTrue("The overflow control keeps its full 48dp target",
                overflowBounds.width >= minimumTouchSize && overflowBounds.height >= minimumTouchSize)
            assertTrue("The answer group contains the complete overflow control",
                groupBounds.left <= overflowBounds.left && groupBounds.right >= overflowBounds.right &&
                    groupBounds.top <= overflowBounds.top && groupBounds.bottom >= overflowBounds.bottom)
            assertTrue("The confirmation toolbar remains below the answer group",
                groupBounds.bottom < toolbarBounds.top)
        }
        assertAnswerLayout()
        capture("${prefix}_before")
        val directNoResponse = compose.onNodeWithText(noResponse)
        if (directNoResponse.isDisplayed()) {
            directNoResponse.assertIsDisplayed().performTouchInput { click() }
        } else {
            overflow.assertIsDisplayed().performTouchInput { click() }
            compose.onNode(hasText(responded) and hasAnyAncestor(isPopup())).assertIsDisplayed()
            compose.onNode(hasText(noResponse) and hasAnyAncestor(isPopup()))
                .assertIsDisplayed().performTouchInput { click() }
        }
        compose.waitForIdle()
        compose.onNode(isPopup()).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(false), answers)
            page = page.copy(canConfirmTest = true, verifiedFunctions = emptySet())
        }
        assertAnswerLayout()
        overflow.assertIsDisplayed().performTouchInput { click() }
        val responseItem = compose.onNode(hasText(responded) and hasAnyAncestor(isPopup()))
        responseItem.assertIsDisplayed()
        compose.onNode(isPopup()).captureRoboImage(
            "src/test/screenshots/expressive_${prefix}_overflow.webp", roborazziOptions = options
        )
        responseItem.performTouchInput { click() }
        compose.waitForIdle()
        compose.onNode(isPopup()).assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(false, true), answers) }
        capture("${prefix}_after")

        compose.runOnIdle { page = page.copy(canConfirmTest = true, verifiedFunctions = emptySet()) }
        compose.waitForIdle()
        compose.runOnIdle { page = page.copy(canConfirmTest = false, isSendingTest = true) }
        compose.onNodeWithText(app.getString(R.string.screen_response_question, "Power")).assertIsDisplayed()
        assertAnswerLayout()
        overflow.assertIsDisplayed().performTouchInput { click() }
        responseItem.assertIsDisplayed().assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(false, true), answers) }
    }
}
