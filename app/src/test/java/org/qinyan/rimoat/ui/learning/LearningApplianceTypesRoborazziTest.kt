package org.qinyan.rimoat.ui.learning

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import com.github.takahirom.roborazzi.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.data.ApplianceType
import org.qinyan.rimoat.data.ButtonKey
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.db.IrDatabaseRepository
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.labelRes
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
    @get:Rule val screenshotErrors = ErrorCollector()
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

    @After fun release() {
        compose.runOnUiThread { store.clear() }
        RuntimeEnvironment.setFontScale(1f)
    }

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

    private fun capture(name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        if (name in setOf("portrait_before", "portrait_selected", "camera_learning", "landscape_before",
                "landscape_after", "narrow_dark_before", "narrow_dark_after")) {
            // Keep the existing text cursor blink phase in the unchanged reference images.
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
        }
        val root = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        screenshotErrors.checkSucceeds {
            root.captureRoboImage("src/test/screenshots/learning_types_$name.webp", roborazziOptions = options)
            Unit
        }
    }

    private fun select(type: ApplianceType, vm: CustomRemoteWizardViewModel) {
        compose.onNodeWithText(app.getString(labels.getValue(type)))
            .performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        assertEquals(type, vm.uiState.value.selectedApplianceType)
        assertEquals(type.category, vm.uiState.value.category)
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-port-mdpi")
    fun learnedStandardLabelsStayLocalizedWhileCustomNamesStayUnchanged() {
        AppStrings.initialize(app)
        val signal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560))
        val standard = org.qinyan.rimoat.data.RemoteButton(
            id = "temperature", label = AcAction.TEMP_UP.label,
            sourceKeyType = AcAction.TEMP_UP.keyType, acAction = AcAction.TEMP_UP
        ).withLearnedSignal(signal)
        val localized = app.getString(AcAction.TEMP_UP.labelRes)
        assertEquals(localized, org.qinyan.rimoat.i18n.localizedButtonLabel(standard, app))
        assertEquals(localized, org.qinyan.rimoat.i18n.localizedButtonLabel(
            standard.copy(id = "ac_function_TEMP_UP", label = AcAction.TEMP_UP.name), app))
        val custom = standard.copy(id = "custom_temperature", sourceKeyType = null, label = "My warm button")
        assertEquals(custom.label, org.qinyan.rimoat.i18n.localizedButtonLabel(custom, app))
        assertEquals("OK", org.qinyan.rimoat.i18n.localizedButtonLabel(
            standard.copy(key = ButtonKey.OK, builtinLabel = ButtonKey.OK.name, acAction = null), app))
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
        typeChangeFlow("change_light", keepLearnedKeys = true)
    }

    @Test fun changingTypeCanDiscardProgressAndStartWithTheNewTemplate() {
        typeChangeFlow("change_dark", dark = true, keepLearnedKeys = false)
    }

    @Test
    @Config(qualifiers = "en-w320dp-h891dp-port-mdpi")
    fun narrowAmoledTypeChangeAtDoubleFontSizeKeepsEveryChoiceReachable() {
        RuntimeEnvironment.setFontScale(2f)
        assertEquals(2f, app.resources.configuration.fontScale, 0f)
        typeChangeFlow("change_amoled_large", dark = true, pureBlack = true, keepLearnedKeys = true)
    }

    @Test fun dynamicColorTypeChangeUsesTheNewTemplateAfterReset() {
        typeChangeFlow("change_dynamic", dynamic = true, keepLearnedKeys = false)
    }

    private fun typeChangeFlow(
        screenshot: String,
        dark: Boolean = false,
        pureBlack: Boolean = false,
        dynamic: Boolean = false,
        keepLearnedKeys: Boolean
    ) {
        AppStrings.initialize(app)
        val learner = IrLearnerManager(context = null)
        learner.mockLearner.setSimulatedDelayMs(0L)
        val vm = CustomRemoteWizardViewModel(learnerManager = learner).also { store.put("switch_preserve", it) }
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = dark, pureBlackBackground = pureBlack, dynamicColor = dynamic) {
                    CustomRemoteWizardScreen(vm, {}, {})
                }
            }
            select(ApplianceType.TV, vm)
            compose.onNode(hasSetTextAction() and hasText(app.getString(R.string.screen_remote_name)))
                .performScrollTo().performTextInput("Living room")
            compose.onNodeWithText(app.getString(R.string.screen_start_learning)).performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(WizardStep.LEARNING, vm.uiState.value.step)

            val signal = IrSignal.Raw(38000, intArrayOf(9000, 4500, 560, 1690))
            learner.mockLearner.triggerManualSignal(signal)
            compose.onNodeWithText(app.getString(R.string.screen_record_signal)).assertIsEnabled()
                .performTouchInput { click() }
            waitForCapture(vm)
            compose.runOnIdle {
                vm.confirmCurrentKey()
                vm.addCustomKey("Extra key")
            }
            compose.waitForIdle()
            val expectedQueue = vm.uiState.value.keyQueue
            val expectedIndex = vm.uiState.value.currentIndex
            val retained = expectedQueue.filter { it.capturedSignal != null || it.builtinLabel == null }

            compose.onNodeWithContentDescription(app.getString(R.string.screen_back)).performClick()
            compose.waitForIdle()
            assertEquals(WizardStep.SETUP, vm.uiState.value.step)
            capture("${screenshot}_before")

            requestAcType()
            assertEquals(ApplianceType.AC, vm.uiState.value.pendingApplianceType)
            assertEquals(ApplianceType.TV, vm.uiState.value.selectedApplianceType)
            assertEquals(expectedQueue, vm.uiState.value.keyQueue)
            assertEquals(expectedIndex, vm.uiState.value.currentIndex)
            compose.onNodeWithTag("wizard_change_type_dialog").assertExists()
            capture("${screenshot}_dialog", dialog = true)
            listOf(R.string.wizard_change_type_keep, R.string.wizard_change_type_reset, R.string.screen_cancel).forEach { label ->
                compose.onNodeWithText(app.getString(label)).performScrollTo()
                    .assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp)
            }
            if (app.resources.configuration.fontScale == 2f) {
                capture("${screenshot}_dialog_actions", dialog = true)
            }
            compose.onNodeWithText(app.getString(R.string.screen_cancel)).performTouchInput { click() }
            compose.onNodeWithTag("wizard_change_type_dialog").assertDoesNotExist()
            assertNull(vm.uiState.value.pendingApplianceType)
            assertEquals(ApplianceType.TV, vm.uiState.value.selectedApplianceType)
            assertEquals(expectedQueue, vm.uiState.value.keyQueue)
            assertEquals(expectedIndex, vm.uiState.value.currentIndex)

            requestAcType()
            val choice = if (keepLearnedKeys) R.string.wizard_change_type_keep else R.string.wizard_change_type_reset
            compose.onNodeWithText(app.getString(choice)).performScrollTo().performTouchInput { click() }
            compose.onNodeWithTag("wizard_change_type_dialog").assertDoesNotExist()
            assertEquals(ApplianceType.AC, vm.uiState.value.selectedApplianceType)
            compose.onNodeWithText(app.getString(R.string.screen_category_ac)).assertIsSelected()
            assertNull(vm.uiState.value.pendingApplianceType)
            val newQueue = vm.uiState.value.keyQueue
            val defaults = CustomRemoteWizardViewModel.generateDefaultQueue(ApplianceType.AC)
            if (keepLearnedKeys) {
                assertEquals(retained, newQueue.take(retained.size))
                assertEquals(1, vm.uiState.value.learnedCount)
                assertTrue(newQueue.none { it.key == ButtonKey.VOLUME_UP })
                assertTrue(defaults.all { expected -> newQueue.any { it.acAction == expected.acAction } })
            } else {
                assertEquals(defaults.map { it.key to it.acAction }, newQueue.map { it.key to it.acAction })
                assertTrue(newQueue.all { it.status == KeyLearnStatus.PENDING && it.capturedSignal == null })
                assertEquals(0, vm.uiState.value.learnedCount)
                assertTrue(newQueue.none { it.id in expectedQueue.map(WizardKeyItem::id) })
            }
            capture("${screenshot}_after")

            compose.onNodeWithText(app.getString(R.string.screen_start_learning)).performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(WizardStep.LEARNING, vm.uiState.value.step)
            assertEquals(newQueue, vm.uiState.value.keyQueue)
        } finally {
            learner.cancel()
        }
    }

    private fun requestAcType() {
        compose.onNodeWithText(app.getString(R.string.screen_category_ac))
            .performScrollTo().performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun waitForCapture(vm: CustomRemoteWizardViewModel) {
        compose.waitUntil(3000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            vm.uiState.value.currentKey?.status == KeyLearnStatus.CAPTURED
        }
        compose.waitForIdle()
    }

    @Test fun customPlusKeyCanBecomeTheStandardTemperatureControl() {
        assignmentFlow("assign_light", assign = true, customLabel = "+")
    }

    @Test fun customKeyCanRemainIndependentAfterTheAssignmentPrompt() {
        assignmentFlow("assign_dark", dark = true, assign = false)
    }

    @Test
    @Config(qualifiers = "en-w320dp-h891dp-port-mdpi")
    fun narrowAmoledAssignmentAtDoubleFontSizeKeepsBothActionsReachable() {
        RuntimeEnvironment.setFontScale(2f)
        assertEquals(2f, app.resources.configuration.fontScale, 0f)
        assignmentFlow("assign_amoled_large", dark = true, pureBlack = true, assign = true)
    }

    @Test fun dynamicColorAssignmentCanKeepTheOriginalCustomKey() {
        assignmentFlow("assign_dynamic", dynamic = true, assign = false)
    }

    private fun assignmentFlow(
        screenshot: String,
        dark: Boolean = false,
        pureBlack: Boolean = false,
        dynamic: Boolean = false,
        assign: Boolean,
        customLabel: String = app.getString(ButtonKey.TEMPERATURE_UP.labelRes)
    ) {
        AppStrings.initialize(app)
        val learner = IrLearnerManager(context = null)
        learner.mockLearner.setSimulatedDelayMs(0L)
        val vm = CustomRemoteWizardViewModel(learnerManager = learner).also { store.put("assignment", it) }
        try {
            vm.setApplianceType(ApplianceType.AC)
            vm.setRemoteName("Study AC")
            vm.addCustomKey(customLabel)
            val customIndex = vm.uiState.value.keyQueue.lastIndex
            val customId = vm.uiState.value.keyQueue.last().id
            assertTrue(vm.proceedToLearning())
            vm.selectKey(customIndex)
            compose.setContent {
                IrRemoteTheme(darkTheme = dark, pureBlackBackground = pureBlack, dynamicColor = dynamic) {
                    CustomRemoteWizardScreen(vm, {}, {})
                }
            }
            compose.onNodeWithText(app.getString(R.string.screen_record_signal)).assertIsEnabled()
            capture("${screenshot}_before")
            compose.onNodeWithText(app.getString(R.string.screen_record_signal)).performTouchInput { click() }
            compose.onNodeWithTag("wizard_assign_ac_key_dialog").assertExists()
            compose.onNodeWithText(app.getString(R.string.wizard_assign_ac_key_title, app.getString(AcAction.TEMP_UP.labelRes)))
                .assertIsDisplayed()
            assertNull(vm.uiState.value.currentKey?.acAction)
            assertEquals(KeyLearnStatus.PENDING, vm.uiState.value.currentKey?.status)
            capture("${screenshot}_dialog", dialog = true)
            listOf(R.string.wizard_assign_ac_key_confirm, R.string.wizard_assign_ac_key_keep_custom).forEach { label ->
                compose.onNodeWithText(app.getString(label)).assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp)
            }
            val signal = IrSignal.Raw(40000, intArrayOf(9000, 4500, 560, 1690))
            learner.mockLearner.triggerManualSignal(signal)
            val choice = if (assign) R.string.wizard_assign_ac_key_confirm else R.string.wizard_assign_ac_key_keep_custom
            compose.onNodeWithText(app.getString(choice)).performTouchInput { click() }
            waitForCapture(vm)
            compose.onNodeWithTag("wizard_assign_ac_key_dialog").assertDoesNotExist()
            val learned = vm.uiState.value.currentKey!!
            assertEquals(customId, learned.id)
            assertEquals(customLabel, learned.label)
            assertNull(learned.builtinLabel)
            assertEquals(if (assign) AcAction.TEMP_UP else null, learned.acAction)
            assertEquals(if (assign) ButtonKey.TEMPERATURE_UP else null, learned.key)
            assertEquals(1, vm.uiState.value.keyQueue.count { it.acAction == AcAction.TEMP_UP })
            val standardKey = vm.uiState.value.keyQueue.single { it.acAction == AcAction.TEMP_UP }
            if (assign) assertEquals(customId, standardKey.id) else assertNotEquals(customId, standardKey.id)
            assertEquals(signal.frequencyHz, learned.capturedSignal?.frequencyHz)
            assertArrayEquals(signal.patternMicros, learned.capturedSignal?.patternMicros)
            capture("${screenshot}_after")
        } finally {
            learner.cancel()
        }
    }
}
