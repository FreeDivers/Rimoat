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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
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
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-mdpi")
    fun categorySwitchingKeepsChineseLabelsSingleLineWithoutVisualOverflow() {
        AppStrings.initialize(app)
        var category by mutableStateOf<DeviceCategory?>(null)
        compose.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column {
                        CategoryFilterBar(category) { category = it }
                    }
                }
            }
        }
        val labels = listOf(
            R.string.screen_category_all,
            R.string.screen_category_tv,
            R.string.screen_category_ac,
            R.string.screen_category_fan,
            R.string.screen_category_projector,
            R.string.screen_category_audio,
            R.string.screen_category_custom
        ).map { app.getString(it) }

        fun assertAllLabelsStable(stage: String) {
            labels.forEach { label ->
                val node = compose.onNodeWithText(label).fetchSemanticsNode()
                val layoutResults = mutableListOf<TextLayoutResult>()
                val gotLayout = node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layoutResults) == true
                assertTrue("$stage: expected TextLayoutResult for $label", gotLayout && layoutResults.isNotEmpty())
                val layout = layoutResults.first()
                assertEquals("$stage: $label must stay single-line", 1, layout.lineCount)
                assertTrue("$stage: $label must not overflow height", !layout.didOverflowHeight)
                assertTrue(
                    "$stage: $label text width (${layout.multiParagraph.maxIntrinsicWidth}) exceeds available (${layout.layoutInput.constraints.maxWidth})",
                    layout.multiParagraph.maxIntrinsicWidth <= layout.layoutInput.constraints.maxWidth
                )
                assertTrue(
                    "$stage: $label glyph right (${layout.getLineRight(0)}) exceeds drawn width (${layout.size.width})",
                    layout.getLineRight(0) <= layout.size.width + 0.5f
                )
            }
        }

        assertAllLabelsStable("initial")
        capture("categories_zh_before")

        // Press projector so adjacent fan button experiences maximum neighbor compression.
        compose.mainClock.autoAdvance = false
        val projector = compose.onNodeWithText(app.getString(R.string.screen_category_projector))
        projector.performTouchInput { down(center) }
        repeat(8) { step ->
            compose.mainClock.advanceTimeBy(32)
            assertAllLabelsStable("projector_press_$step")
        }
        capture("categories_zh_projector_pressed")
        projector.performTouchInput { up() }
        repeat(10) { step ->
            compose.mainClock.advanceTimeBy(32)
            assertAllLabelsStable("projector_release_$step")
        }
        compose.mainClock.autoAdvance = true
        projector.assertIsSelected()
        assertEquals(DeviceCategory.PROJECTOR, category)

        // Tap AC and verify every press and release-overshoot frame stays single-line.
        compose.mainClock.autoAdvance = false
        val ac = compose.onNodeWithText(app.getString(R.string.screen_category_ac))
        ac.performTouchInput { down(center) }
        repeat(6) { step ->
            compose.mainClock.advanceTimeBy(32)
            assertAllLabelsStable("ac_press_$step")
        }
        ac.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(48)
        assertAllLabelsStable("ac_release_overshoot")
        capture("categories_zh_ac_release")
        repeat(10) { step ->
            compose.mainClock.advanceTimeBy(32)
            assertAllLabelsStable("ac_settle_$step")
        }
        compose.mainClock.autoAdvance = true
        ac.assertIsSelected()
        assertEquals(DeviceCategory.AC, category)
        capture("categories_zh_ac_selected")
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
    fun pairingAnswersFitNarrowPaneAndCommitOnlyOnce() = exercisePairingAnswers(fontScale = 1f)

    @Test
    @Config(qualifiers = "en-w320dp-h568dp-mdpi")
    fun pairingAnswersKeepBothChoicesReachableWithLargeText() = exercisePairingAnswers(fontScale = 2f)

    private fun exercisePairingAnswers(fontScale: Float) {
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
        val noResponseNode = compose.onNode(hasText(noResponse) and hasClickAction())
        val respondedNode = compose.onNode(hasText(responded) and hasClickAction())
        fun assertAnswerLayout() {
            compose.onNodeWithTag("pairing_answers_overflow").assertDoesNotExist()
            val noBounds = noResponseNode.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val yesBounds = respondedNode.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val groupBounds = compose.onNodeWithTag("pairing_answers_group")
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val toolbarBounds = compose.onNodeWithTag("pairing_confirmation_toolbar")
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val minimumTouchSize = with(compose.density) { 48.dp.toPx() }
            assertTrue("Both answer buttons keep full 48dp touch height",
                noBounds.height >= minimumTouchSize && yesBounds.height >= minimumTouchSize)
            assertTrue("The answer group contains both answer buttons",
                groupBounds.left <= noBounds.left && groupBounds.right >= noBounds.right &&
                    groupBounds.top <= noBounds.top && groupBounds.bottom >= noBounds.bottom &&
                    groupBounds.left <= yesBounds.left && groupBounds.right >= yesBounds.right &&
                    groupBounds.top <= yesBounds.top && groupBounds.bottom >= yesBounds.bottom)
            assertTrue("Answer buttons do not overlap each other",
                noBounds.right <= yesBounds.left || yesBounds.right <= noBounds.left ||
                    noBounds.bottom <= yesBounds.top || yesBounds.bottom <= noBounds.top)
            assertTrue("The confirmation toolbar remains below the answer group",
                groupBounds.bottom < toolbarBounds.top)
        }
        assertAnswerLayout()
        capture("${prefix}_before")
        noResponseNode.performTouchInput { click() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf(false), answers)
            page = page.copy(canConfirmTest = true, verifiedFunctions = emptySet())
        }
        assertAnswerLayout()
        respondedNode.performTouchInput { click() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(false, true), answers) }
        capture("${prefix}_after")

        compose.runOnIdle { page = page.copy(canConfirmTest = true, verifiedFunctions = emptySet()) }
        compose.waitForIdle()
        compose.runOnIdle { page = page.copy(canConfirmTest = false, isSendingTest = true) }
        compose.onNodeWithText(app.getString(R.string.screen_response_question, "Power")).assertIsDisplayed()
        assertAnswerLayout()
        respondedNode.assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(false, true), answers) }
    }
}
