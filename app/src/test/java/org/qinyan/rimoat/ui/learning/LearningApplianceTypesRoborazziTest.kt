package org.qinyan.rimoat.ui.learning

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import com.github.takahirom.roborazzi.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.ApplianceType
import org.qinyan.rimoat.data.ButtonKey
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.db.IrDatabaseRepository
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.components.getCategoryIcon
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-port-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class LearningApplianceTypesRoborazziTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val store = ViewModelStore()
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )
    private val labels = mapOf(
        ApplianceType.TV to R.string.screen_category_tv,
        ApplianceType.AC to R.string.screen_category_ac,
        ApplianceType.FAN to R.string.screen_category_fan,
        ApplianceType.AUDIO to R.string.screen_category_audio,
        ApplianceType.PROJECTOR to R.string.screen_category_projector,
        ApplianceType.SET_TOP_BOX to R.string.screen_category_set_top_box,
        ApplianceType.DVD to R.string.screen_category_dvd,
        ApplianceType.CAMERA to R.string.screen_category_camera,
        ApplianceType.LIGHT to R.string.screen_category_light,
        ApplianceType.STREAMING_BOX to R.string.screen_category_streaming_box,
        ApplianceType.IPTV to R.string.screen_category_iptv,
        ApplianceType.CUSTOM to R.string.screen_category_custom
    )

    @After fun release() { compose.runOnUiThread { store.clear() } }

    private fun show(dark: Boolean = false, fontScale: Float = 1f): CustomRemoteWizardViewModel {
        AppStrings.initialize(app)
        val vm = CustomRemoteWizardViewModel().also { store.put("wizard", it) }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                IrRemoteTheme(darkTheme = dark, dynamicColor = false) {
                    CustomRemoteWizardScreen(vm, {}, {})
                }
            }
        }
        return vm
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/learning_types_$name.webp", roborazziOptions = options)
    }

    private fun select(type: ApplianceType, vm: CustomRemoteWizardViewModel) {
        compose.onNodeWithText(app.getString(labels.getValue(type)))
            .performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        assertEquals(type, vm.uiState.value.selectedApplianceType)
        assertEquals(type.category, vm.uiState.value.category)
    }

    @Test fun allApplianceTypesCanBeSelectedAndCameraStartsWithShutter() {
        val vm = show()
        assertEquals(ApplianceType.entries.toSet(), labels.keys)
        capture("portrait_before")
        labels.keys.forEach { select(it, vm) }
        select(ApplianceType.CAMERA, vm)
        compose.onNode(hasSetTextAction() and hasText(app.getString(R.string.screen_remote_name)))
            .performScrollTo().performTextInput("相机遥控器")
        capture("portrait_selected")
        compose.onNodeWithText(app.getString(R.string.screen_start_learning)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(WizardStep.LEARNING, vm.uiState.value.step)
        assertEquals(ButtonKey.SHUTTER, vm.uiState.value.currentKey?.key)
        assertEquals(ButtonKey.SHUTTER.name, vm.uiState.value.currentKey?.builtinLabel)
        compose.onAllNodesWithText(app.getString(R.string.control_shutter))[0].assertIsDisplayed()
        capture("camera_learning")
    }

    @Test
    @Config(qualifiers = "en-w320dp-h740dp-port-mdpi")
    fun narrowDarkSetupAtDoubleFontSizeKeepsAllTypesAndStartReachable() {
        val vm = show(dark = true, fontScale = 2f)
        select(ApplianceType.SET_TOP_BOX, vm)
        capture("narrow_dark_before")
        labels.keys.forEach { select(it, vm) }
        select(ApplianceType.LIGHT, vm)
        compose.onNode(hasSetTextAction() and hasText(app.getString(R.string.screen_remote_name)))
            .performScrollTo().performTextInput("Desk light")
        compose.onNodeWithText(app.getString(R.string.screen_start_learning)).performScrollTo().assertIsDisplayed()
        capture("narrow_dark_after")
        compose.onNodeWithText(app.getString(R.string.screen_start_learning)).performClick()
        compose.waitForIdle()
        assertEquals(WizardStep.LEARNING, vm.uiState.value.step)
        assertEquals(ApplianceType.LIGHT, vm.uiState.value.selectedApplianceType)
    }

    @Test
    @Config(qualifiers = "en-w640dp-h360dp-land-mdpi")
    fun landscapeKeepsNameVisibleWhileScrollingToIptv() {
        val vm = show()
        compose.onNode(hasSetTextAction() and hasText(app.getString(R.string.screen_remote_name)))
            .performTextInput("Living room")
        capture("landscape_before")
        select(ApplianceType.IPTV, vm)
        compose.onNodeWithText("Living room").assertIsDisplayed()
        val left = compose.onNodeWithTag("two_pane_primary").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("two_pane_secondary").fetchSemanticsNode().boundsInRoot
        assertTrue(left.right < right.left)
        capture("landscape_after")
        compose.onNodeWithText(app.getString(R.string.screen_start_learning)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(WizardStep.LEARNING, vm.uiState.value.step)
        assertTrue(vm.uiState.value.keyQueue.any { it.key == ButtonKey.CHANNEL_UP })
    }

    @Test fun typesCoverTheCatalogAndFilterBrandsWithoutLosingSavedIcons() = runBlocking {
        AppStrings.initialize(app)
        val catalog = IrDatabaseRepository(app)
        val categories = catalog.getCategories()
        val types = ApplianceType.entries.filter { it != ApplianceType.CUSTOM }
        assertEquals(categories.map { it.code }.toSet(), types.map { it.catalogCode }.toSet())
        val vm = CustomRemoteWizardViewModel(brandLibrary = catalog).also { store.put("brands", it) }
        types.forEach { type ->
            val category = categories.single { it.code == type.catalogCode }
            val expected = catalog.getBrandsForCategory(category.id).distinctBy { it.nameCn to it.nameEn }
            assertTrue(expected.isNotEmpty())
            assertEquals(expected, vm.brandsFor(type))
            val profile = RemoteProfile("learned", "Remote", category = type.category, applianceType = type)
            val restored = Json.decodeFromString<RemoteProfile>(Json.encodeToString(profile))
            assertSame(getCategoryIcon(type.catalogCode), getCategoryIcon(restored))
        }
    }

    @Test fun returningFromLearningAndSwitchingTypePreservesLearnedQueueAndCustomKeys() {
        AppStrings.initialize(app)
        val learner = org.qinyan.rimoat.hardware.learning.IrLearnerManager(context = null)
        learner.mockLearner.setSimulatedDelayMs(0L)
        val vm = CustomRemoteWizardViewModel(learnerManager = learner).also { store.put("switch_preserve", it) }
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    CustomRemoteWizardScreen(vm, {}, {})
                }
            }
            select(ApplianceType.TV, vm)
            compose.onNode(hasSetTextAction() and hasText(app.getString(R.string.screen_remote_name)))
                .performScrollTo().performTextInput("客厅电视")
            compose.onNodeWithText(app.getString(R.string.screen_start_learning)).performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(WizardStep.LEARNING, vm.uiState.value.step)

            val signal = org.qinyan.rimoat.hardware.IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 1690))
            learner.mockLearner.triggerManualSignal(signal)
            vm.startLearningCurrentKey()
            compose.waitUntil(3000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                vm.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED
            }
            vm.confirmCurrentKey()
            vm.addCustomKey("自定义键")
            compose.waitForIdle()
            val expectedQueue = vm.uiState.value.keyQueue
            val expectedIndex = vm.uiState.value.currentIndex

            compose.onNodeWithContentDescription(app.getString(R.string.screen_back)).performClick()
            compose.waitForIdle()
            assertEquals(WizardStep.SETUP, vm.uiState.value.step)

            select(ApplianceType.AC, vm)
            assertEquals(expectedQueue, vm.uiState.value.keyQueue)
            assertEquals(expectedIndex, vm.uiState.value.currentIndex)

            compose.onNodeWithText(app.getString(R.string.screen_start_learning)).performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(WizardStep.LEARNING, vm.uiState.value.step)
            assertEquals(1, vm.uiState.value.learnedCount)
            assertEquals(expectedQueue, vm.uiState.value.keyQueue)
            assertEquals(expectedIndex, vm.uiState.value.currentIndex)
            compose.onNodeWithText("自定义键").performScrollTo().assertIsDisplayed()
        } finally {
            learner.cancel()
        }
    }
}
