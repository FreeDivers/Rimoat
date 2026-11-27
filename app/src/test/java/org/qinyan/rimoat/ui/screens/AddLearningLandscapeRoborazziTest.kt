package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.testing.collectScreenshotDifference
import org.junit.rules.ErrorCollector
import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.db.*
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.learning.LearnerType
import org.qinyan.rimoat.hardware.learning.LearningState
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.learning.*
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
class AddLearningLandscapeRoborazziTest {
    @get:Rule val screenshotDifferences = ErrorCollector()
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))
    private val category = IrCategory(1, "电视", "TV", "tv")
    private val model = IrRemoteModel("code_one", 1, "TV", 1, "示例", "Example", 1, "", 2)
    private val power = RemoteButton("power", "Power", iconName = "power", rawPattern = listOf(9000, 4500, 560))

    private fun show(fontScale: Float = 1f, content: @Composable () -> Unit) {
        AppStrings.initialize(app)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    Surface(Modifier.fillMaxSize()) { content() }
                }
            }
        }
    }
    private fun capture(name: String) {
        val root = compose.onRoot()
        screenshotDifferences.collectScreenshotDifference {
            root.captureRoboImage("src/test/screenshots/landscape_flow_$name.webp", roborazziOptions = options)
        }
    }
    private fun panes() {
        val left = compose.onNodeWithTag("two_pane_primary").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("two_pane_secondary").fetchSemanticsNode().boundsInRoot
        assertTrue(left.right < right.left)
        assertEquals(left.top, right.top, 1f)
    }

    @Test fun pairingGroupsFollowStableKeysInsteadOfVisibleLabels() {
        assertEquals(app.getString(R.string.screen_navigation_sources),
            pairingKeyGroup(RemoteButton("navigation", "Personal label", key = org.qinyan.rimoat.data.ButtonKey.UP), app))
        assertEquals(app.getString(R.string.screen_volume_channels),
            pairingKeyGroup(RemoteButton("volume", "Personal label", key = org.qinyan.rimoat.data.ButtonKey.VOLUME_UP), app))
        assertEquals(app.getString(R.string.screen_power_group),
            pairingKeyGroup(RemoteButton("switch", "Personal label", key = org.qinyan.rimoat.data.ButtonKey.POWER), app))
    }

    @Test fun categoriesKeepLearningEntryBesideSelectableTypes() {
        var clicks = 0
        var selected: IrCategory? = null
        show { CategorySelectionView(listOf(category, IrCategory(2, "空调", "Air conditioner", "air")), false,
            { selected = it; clicks++ }) }
        panes()
        val learning = compose.onNodeWithText(app.getString(R.string.screen_learn_physical)).fetchSemanticsNode().boundsInRoot
        val tv = compose.onNodeWithText(category.displayName(app)).fetchSemanticsNode().boundsInRoot
        assertTrue(learning.right < tv.left)
        capture("categories_before")
        compose.onNodeWithText(category.displayName(app)).performClick()
        assertEquals(category, selected)
        assertEquals(1, clicks)
        capture("categories_after")
    }

    @Test fun brandsSearchAndAlphabetIndexReachFirstMiddleAndLastGroup() {
        val brands = listOf(
            IrBrand(1, "阿尔法", "Alpha", "alpha", 1), IrBrand(2, "中间", "Middle", "middle", 1),
            IrBrand(3, "末尾", "Zulu", "zulu", 1), IrBrand(4, "小米", "Xiaomi", "xiaomi", 1))
        var query by mutableStateOf("")
        var selected: IrBrand? = null
        show {
            BrandSelectionView(brands.filter { it.nameEn.contains(query, true) }, rememberLazyListState(), query,
                category, false, { query = it }, { selected = it })
        }
        panes()
        capture("brands_before")
        val rail = compose.onNodeWithContentDescription(app.getString(R.string.screen_brand_index))
        listOf(0f to "Alpha", 12f to "Middle", 25f to "Zulu").forEach { (index, label) ->
            rail.performSemanticsAction(SemanticsActions.SetProgress) { it(index) }
            compose.waitForIdle()
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        compose.onNode(hasSetTextAction()).performTextInput("Zulu")
        compose.onNode(hasText("Zulu") and hasAnyAncestor(hasTestTag("two_pane_secondary")))
            .assertIsDisplayed().performClick()
        assertEquals("Zulu", selected?.nameEn)
        capture("brands_after")
    }

    @Test fun pairingKeepsTestAndConfirmationInSeparatePanes() {
        var sends = 0
        var confirms = 0
        var saves = 0
        var state by mutableStateOf(AddRemoteUiState(remotes = listOf(model), primaryTestKey = power,
            allPreviewButtons = listOf(power)))
        show {
            CodeMatchingWizardView(state, onTestKey = {
                sends++; state = state.copy(testedButton = it, canConfirmTest = true)
            }, onNextModel = {}, onPrevModel = {}, onMatched = { saves++ }, onConfirmTest = {
                assertTrue(it); confirms++; state = state.copy(canConfirmTest = false, verifiedFunctions = setOf("Power"))
            })
        }
        panes()
        capture("pairing_before")
        compose.onNodeWithContentDescription("Power").performClick()
        val question = compose.onNodeWithText(app.getString(R.string.screen_response_question, "Power"))
        question.assertIsDisplayed()
        assertTrue(question.fetchSemanticsNode().boundsInRoot.left > compose.onRoot().fetchSemanticsNode().boundsInRoot.width / 2)
        capture("pairing_feedback")
        val response = app.getString(R.string.screen_responded)
        if (compose.onAllNodesWithText(response).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("pairing_answers_overflow").performScrollTo().performClick()
        }
        compose.onNodeWithText(response).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.screen_use_code)).performScrollTo().performClick()
        assertEquals(1, sends); assertEquals(1, confirms); assertEquals(1, saves)
        capture("pairing_after")
    }

    @Test
    @Config(qualifiers = "en-w480dp-h320dp-land-mdpi")
    fun savingNameRemainsReachableAtDoubleFontSize() {
        var state by mutableStateOf(AddRemoteUiState(selectedCategory = category, remotes = listOf(model)))
        var saves = 0
        show(2f) { ConfirmRemoteView(state, { state = state.copy(customRemoteName = it) }, { saves++ }) }
        panes()
        capture("save_large_before")
        compose.onNode(hasSetTextAction()).performTextInput("Living room television")
        compose.onNodeWithText(app.getString(R.string.screen_save)).performScrollTo().assertIsDisplayed().performClick()
        assertEquals("Living room television", state.customRemoteName)
        assertEquals(1, saves)
        capture("save_large_after")
    }

    @Test fun learningSetupKeepsTypesBesideEditableDraft() {
        var page by mutableStateOf(CustomRemoteWizardUiState())
        var starts = 0
        show {
            LearningSetupContent(page, { page = page.copy(category = it.category, applianceType = it) }, { page = page.copy(remoteName = it) },
                { page = page.copy(brand = it) }, {}, { starts++ })
        }
        panes()
        capture("learning_setup_before")
        compose.onNode(hasSetTextAction() and hasText(app.getString(R.string.screen_remote_name))).performTextInput("Study remote")
        compose.onNodeWithText(app.getString(R.string.screen_start_learning)).performScrollTo().performClick()
        assertEquals("Study remote", page.remoteName); assertEquals(1, starts)
        capture("learning_setup_after")
    }

    @Test fun learningSelectionChangesWorkspaceWithoutScrollingKeyList() {
        var page by mutableStateOf(CustomRemoteWizardUiState(step = WizardStep.LEARNING, remoteName = "Living room",
            keyQueue = listOf(WizardKeyItem("power", "Power"), WizardKeyItem("mute", "Mute"))))
        var records = 0
        val record = {
            records++
            page = page.copy(keyQueue = page.keyQueue.mapIndexed { index, key ->
                if (index == page.currentIndex) key.copy(status = KeyLearnStatus.LEARNING) else key })
        }
        show {
            LearningKeyWorkspace(page, LearningState.Idle, LearnerType.MOCK, true, false, true, {},
                { page = page.copy(currentIndex = it) }, {}, record, Modifier.fillMaxSize(), actions = {
                    LearningKeyActions(page, true, record, {}, {}, {}, {}, inPane = true)
                })
        }
        panes()
        capture("learning_keys_before")
        compose.onNodeWithText("Mute").performClick()
        assertEquals(1, page.currentIndex)
        compose.onNodeWithText(app.getString(R.string.screen_record_signal)).performScrollTo().performClick()
        assertEquals(1, records)
        assertEquals(KeyLearnStatus.LEARNING, page.currentKey?.status)
        compose.onNodeWithText(app.getString(R.string.screen_cancel_receive)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Power").assertIsDisplayed()
        capture("learning_keys_after")
    }

    @Test fun learningReviewKeepsSaveBesideLearnedButtons() {
        var saves = 0
        val signal = IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560))
        val page = CustomRemoteWizardUiState(step = WizardStep.REVIEW, remoteName = "Study remote",
            keyQueue = listOf(WizardKeyItem("power", "Power", status = KeyLearnStatus.LEARNED, capturedSignal = signal)))
        show { LearningReviewContent(page, {}, { saves++ }, Modifier.fillMaxSize()) }
        panes()
        capture("learning_review_before")
        val save = compose.onNodeWithText(app.getString(R.string.screen_save)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Power").assertIsDisplayed()
        save.performClick()
        assertEquals(1, saves)
        capture("learning_review_after")
    }
}
