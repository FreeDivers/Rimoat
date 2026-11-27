package org.qinyan.rimoat

import android.app.Application
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.github.takahirom.roborazzi.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.data.db.*
import org.qinyan.rimoat.hardware.*
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.components.RemoteSearchBar
import org.qinyan.rimoat.ui.learning.*
import org.qinyan.rimoat.ui.motion.LocalMotionActive
import org.qinyan.rimoat.ui.motion.MotionContent
import org.qinyan.rimoat.ui.screens.AddRemoteScreen
import org.qinyan.rimoat.ui.screens.RemoteControlScreen
import org.qinyan.rimoat.ui.screens.RemoteListScreen
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.AddRemoteStep
import org.qinyan.rimoat.ui.viewmodel.AddRemoteViewModel
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ClassName
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSystemVibrator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class BugFixInteractionRoborazziTest {
    @Implements(className = "android.os.SystemVibrator", isInAndroidSdk = false)
    class CountingSystemVibrator : ShadowSystemVibrator() {
        val effects = mutableListOf<VibrationEffect>()

        @Implementation(minSdk = 31)
        override fun vibrate(
            uid: Int,
            opPkg: String?,
            @ClassName("android.os.VibrationEffect") effect: Any,
            reason: String?,
            @ClassName("android.os.VibrationAttributes") attributes: Any
        ) {
            effects += effect as VibrationEffect
            super.vibrate(uid, opPkg, effect, reason, attributes)
        }
    }

    @get:Rule val compose = createComposeRule()
    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))
    private val app get() = RuntimeEnvironment.getApplication()
    private fun capture(name: String, dialog: Boolean = false) {
        (if (dialog) compose.onNode(isDialog()) else compose.onRoot())
            .captureRoboImage("src/test/screenshots/$name.webp", roborazziOptions = options)
    }

    @Test fun extendedNecButtonRetainsAddressFormatWhenPressed() {
        AppStrings.initialize(app)
        val button = RemoteButton("power", "电源", iconName = "power", signalType = SignalType.NEC,
            necAddress = 0x00FE, necCommand = 0x45, necExtended = true)
        val original = RemoteProfile("tv", "客厅电视", category = DeviceCategory.TV, buttons = listOf(button))
        val profile = Json.decodeFromString(RemoteProfile.serializer(), Json.encodeToString(RemoteProfile.serializer(), original))
        val transmitter = IrTransmitterManager(app).apply {
            setForceMockMode(true)
            mockTransmitter.simulateDelayMs = 0
        }
        val store = ViewModelStore()
        val vm = RemoteControlViewModel(profile.id, initialProfile = profile, transmitterManager = transmitter)
            .also { store.put("remote", it) }
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) { RemoteControlScreen(vm, onBack = {}) }
            }
            compose.onNodeWithText(profile.name).assertIsDisplayed()
            capture("nec_extended_before")
            compose.onNodeWithContentDescription(button.label).performClick()
            compose.waitUntil(5_000) { transmitter.mockTransmitter.transmitCount.value == 1 }
            compose.runOnIdle {
                val signal = transmitter.mockTransmitter.lastSignal.value as IrSignal.Nec
                assertEquals(IrSignal.Nec(0x00FE, 0x45, extended = true), signal)
                val raw = IrProtocolEncoder.encodeNec(signal)
                for (bit in 8 until 16) assertEquals(560, raw.patternMicros[3 + bit * 2])
                assertNull(vm.operationError.value)
            }
            capture("nec_extended_after")
        } finally {
            compose.runOnIdle { store.clear() }
            transmitter.release()
        }
    }

    @Test fun rapidNavigationAndCancelledBackLeaveFinalPageTouchable() {
        var target by mutableStateOf("客厅电视")
        var clicks = 0
        var interactive = false
        lateinit var dispatcher: OnBackPressedDispatcher
        val action = app.getString(R.string.screen_resend)
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                MotionContent(target, { it }, { _, _ -> false }, backTarget = "首页", onBack = { target = "首页" }) { page ->
                    if (page == target) interactive = LocalMotionActive.current
                    Column {
                        Text(page)
                        Button(onClick = { clicks++ }) { Text(action) }
                    }
                }
            }
        }
        capture("motion_recovery_before")
        compose.mainClock.autoAdvance = false
        repeat(20) { index ->
            compose.runOnUiThread { target = if (index % 2 == 0) "卧室风扇" else "客厅电视" }
            compose.mainClock.advanceTimeBy(32)
        }
        compose.runOnUiThread { target = "卧室风扇" }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 0f, .5f, BackEventCompat.EDGE_LEFT))
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnUiThread {
            dispatcher.dispatchOnBackCancelled()
            target = "书房投影"
        }
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("书房投影").assertIsDisplayed()
        compose.onNodeWithText(action).performTouchInput { click() }
        compose.runOnIdle { assertTrue(interactive); assertEquals(1, clicks) }
        capture("motion_recovery_after")
    }

    @Test fun switchingButtonDuringTestTransmissionClearsFeedbackAndCancelsOldJob() {
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val learner = IrLearnerManager(app, scope).apply {
            setForceMockMode(true); mockLearner.setSimulatedDelayMs(0)
        }
        val transmitter = IrTransmitterManager(app, scope).apply {
            setForceMockMode(true); mockTransmitter.simulateDelayMs = 5_000
        }
        var button by mutableStateOf(RemoteButton("power", "电源"))
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    SingleButtonLearningDialog(button, learner, transmitter, {}, { _, _ -> }, autoStart = true)
                }
            }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText(app.getString(R.string.screen_test_transmit)).fetchSemanticsNodes().isNotEmpty()
            }
            capture("learning_button_before", true)
            compose.onNodeWithText(app.getString(R.string.screen_test_transmit)).performClick()
            compose.waitUntil(2_000) {
                compose.onAllNodesWithText(app.getString(R.string.screen_testing_transmit)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.runOnIdle { button = RemoteButton("mute", "静音") }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText(app.getString(R.string.screen_test_transmit)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(app.getString(R.string.screen_testing_transmit)).assertDoesNotExist()
            compose.onNodeWithText(app.getString(R.string.screen_simulated_send)).assertDoesNotExist()
            compose.onNodeWithText(app.getString(R.string.screen_test_transmit)).assertIsEnabled()
            capture("learning_button_after", true)
        } finally { learner.cancel(); learner.unregisterReceiver(); transmitter.release(); scope.cancel() }
    }

    @Test fun waveformWithNegativePulsesMatchesClampedDuration() {
        compose.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                IrWaveformPreview(listOf(9000, -4500, 560, 560, -560, 1690))
            }
        }
        compose.onNodeWithContentDescription(app.getString(R.string.screen_received_waveform)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.screen_duration_ms, "11.8")).assertIsDisplayed()
        capture("waveform_negative_pulses")
    }

    @Test fun wizardBackAndContinueKeepCapturedSignalVisible() {
        AppStrings.initialize(app)
        val original = CustomRemoteWizardUiState(step = WizardStep.LEARNING, remoteName = "客厅电视",
            keyQueue = listOf(WizardKeyItem(id = "power", label = "电源", status = KeyLearnStatus.LEARNED,
                capturedSignal = IrSignal.Raw(patternMicros = intArrayOf(9000, 4500, 560)), pulseCount = 3, durationMs = 14.06)))
        val store = ViewModelStore()
        val vm = CustomRemoteWizardViewModel(savedStateHandle = SavedStateHandle(mapOf("wizard_state" to
            Json.encodeToString(CustomRemoteWizardUiState.serializer(), original)))).also { store.put("wizard", it) }
        try {
            compose.setContent { IrRemoteTheme(darkTheme = false, dynamicColor = false) { CustomRemoteWizardScreen(vm, {}, {}) } }
            compose.waitForIdle()
            capture("wizard_resume_before")
            compose.runOnIdle { vm.handleBack() }
            compose.waitForIdle()
            compose.runOnIdle { assertTrue(vm.proceedToLearning()) }
            compose.waitForIdle()
            assertEquals(1, vm.uiState.value.learnedCount)
            assertEquals(original.keyQueue.single().capturedSignal, vm.uiState.value.currentKey?.capturedSignal)
            capture("wizard_resume_after")
        } finally { compose.runOnIdle { store.clear() } }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w891dp-h411dp-land-mdpi", shadows = [CountingSystemVibrator::class])
    fun sidePresetsPerformOneHapticPerClick() {
        AppStrings.initialize(app)
        Settings.System.putInt(app.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1)
        val vibrator = app.getSystemService(VibratorManager::class.java).defaultVibrator
        val shadow = Shadow.extract<CountingSystemVibrator>(vibrator)
        shadow.setHasVibrator(true)
        val store = ViewModelStore()
        val vm = RemoteControlViewModel("ac", initialProfile = RemoteProfile("ac", "卧室空调",
            category = DeviceCategory.AC, acProtocolId = 670)).also { store.put("remote", it) }
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) { RemoteControlScreen(vm, onBack = {}) }
            }
            capture("side_presets_before")
            compose.runOnIdle { assertTrue(shadow.effects.isEmpty()) }
            compose.onNodeWithContentDescription(app.getString(R.string.screen_resend)).performTouchInput { click() }
            compose.runOnIdle {
                assertEquals(1, shadow.effects.size)
                val attributes = shadow.vibrationAttributesFromLastVibration as VibrationAttributes
                assertEquals(VibrationAttributes.USAGE_TOUCH, attributes.usage)
            }
            capture("side_presets_after")
        } finally { compose.runOnIdle { store.clear() } }
    }

    @Test fun homeSearchBarClearsFocusAndHidesKeyboardWhenTappingOutside() {
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = RemoteRepository(app, scope)
        val tv = RemoteProfile("living_tv", "客厅电视", category = DeviceCategory.TV, room = "客厅")
        val fan = RemoteProfile("bedroom_fan", "卧室风扇", category = DeviceCategory.FAN, room = "卧室")
        runBlocking {
            repository.awaitLoaded()
            repository.saveRemote(tv)
            repository.saveRemote(fan)
        }
        val transmitter = IrTransmitterManager(app, scope)
        val store = ViewModelStore()
        val vm = RemoteListViewModel(repository, transmitter, ThemePreferences(app), BackupFileStore(app))
            .also { store.put("home", it) }
        var showCount = 0
        var hideCount = 0
        val keyboard = object : SoftwareKeyboardController {
            override fun show() { showCount++ }
            override fun hide() { hideCount++ }
        }
        try {
            compose.setContent {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    IrRemoteTheme(darkTheme = false, dynamicColor = false) { RemoteListScreen(vm, {}, {}) }
                }
            }
            compose.waitUntil(5_000) { vm.filteredRemotes.value.size == 2 }
            val searchField = compose.onNode(hasSetTextAction())
            searchField.performTouchInput { click() }
            searchField.assertIsFocused()
            searchField.performTextInput("客厅")
            compose.waitUntil(5_000) { vm.filteredRemotes.value.map { it.id } == listOf(tv.id) }
            capture("home_search_focused")

            compose.onNodeWithContentDescription(app.getString(R.string.control_clear_search)).performTouchInput { click() }
            compose.waitUntil(5_000) { vm.filteredRemotes.value.size == 2 }
            searchField.assertIsFocused()

            val hidesBeforeOutsideTap = hideCount
            compose.onNodeWithTag("home_results").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.8f)) }
            compose.waitForIdle()
            searchField.assertIsNotFocused()
            assertTrue(hideCount > hidesBeforeOutsideTap)

            searchField.performTouchInput { click() }
            searchField.assertIsFocused()
            val hidesBeforeTabTap = hideCount
            compose.onNodeWithText(app.getString(R.string.screen_category_tv)).performTouchInput { click() }
            compose.waitUntil(5_000) { vm.filteredRemotes.value.map { it.id } == listOf(tv.id) }
            searchField.assertIsNotFocused()
            assertTrue(hideCount > hidesBeforeTabTap)
            capture("home_search_unfocused")
        } finally {
            compose.runOnIdle { store.clear() }
            transmitter.release()
            scope.cancel()
        }
    }

    @Test fun leavingRetainedMotionPageClearsSearchFocusAndHidesKeyboard() {
        var page by mutableStateOf("home")
        var query by mutableStateOf("")
        var hideCount = 0
        val keyboard = object : SoftwareKeyboardController {
            override fun show() {}
            override fun hide() { hideCount++ }
        }
        compose.setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    MotionContent(
                        targetState = page,
                        contentKey = { it },
                        isBack = { _, to -> to == "home" },
                        retain = { it == "home" }
                    ) { current ->
                        if (current == "home") {
                            androidx.compose.foundation.layout.Column {
                                RemoteSearchBar(query = query, onQueryChange = { query = it }, placeholder = "搜索遥控器")
                                Button(onClick = { page = "settings" }) { Text("设置") }
                            }
                        } else {
                            androidx.compose.foundation.layout.Column {
                                Text("设置页")
                                Button(onClick = { page = "home" }) { Text("返回") }
                            }
                        }
                    }
                }
            }
        }
        val searchField = compose.onNode(hasSetTextAction())
        searchField.performTouchInput { click() }
        searchField.assertIsFocused()
        val hidesBeforeNav = hideCount

        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("设置页").assertIsDisplayed()
        compose.runOnIdle { assertTrue(hideCount > hidesBeforeNav) }

        compose.onNodeWithText("返回").performClick()
        compose.waitForIdle()
        searchField.assertIsNotFocused()
    }

    @Test fun predictiveBackRollsBackWhenOnBackDoesNotNavigate() {
        var target by mutableStateOf("确认保存")
        var allowBack by mutableStateOf(false)
        var onBackCalls = 0
        var clicks = 0
        var interactive = false
        lateinit var dispatcher: OnBackPressedDispatcher
        val action = app.getString(R.string.screen_resend)
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                MotionContent(
                    targetState = target,
                    contentKey = { it },
                    isBack = { _, to -> to == "向导步骤" },
                    backTarget = "向导步骤",
                    onBack = {
                        onBackCalls++
                        if (allowBack) target = "向导步骤"
                    }
                ) { page ->
                    if (page == target) interactive = LocalMotionActive.current
                    Column {
                        Text(page)
                        Button(onClick = { clicks++ }) { Text("$action-$page") }
                    }
                }
            }
        }
        compose.onNodeWithText("确认保存").assertIsDisplayed()
        capture("predictive_back_rejected_before")

        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 0f, 0.6f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()

        compose.onNodeWithText("确认保存").assertIsDisplayed()
        compose.onNodeWithText("向导步骤").assertDoesNotExist()
        compose.onNodeWithText("$action-确认保存").performClick()
        compose.runOnIdle {
            assertEquals(1, onBackCalls)
            assertTrue(interactive)
            assertEquals(1, clicks)
        }
        capture("predictive_back_rejected_after")

        allowBack = true
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 0f, 0.6f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()

        compose.onNodeWithText("向导步骤").assertIsDisplayed()
        compose.onNodeWithText("$action-向导步骤").performClick()
        compose.runOnIdle {
            assertEquals(2, onBackCalls)
            assertTrue(interactive)
            assertEquals(2, clicks)
        }
    }

    @Test fun predictiveBackRollsBackWhenBackTargetBecomesNullDuringCompletion() {
        var target by mutableStateOf("学习匹配")
        var backTarget by mutableStateOf<String?>("选择品牌")
        var onBackCalls = 0
        var clicks = 0
        var interactive = false
        lateinit var dispatcher: OnBackPressedDispatcher
        val action = app.getString(R.string.screen_resend)
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                MotionContent(
                    targetState = target,
                    contentKey = { it },
                    isBack = { _, to -> to == "选择品牌" },
                    backTarget = backTarget,
                    onBack = {
                        onBackCalls++
                        target = "选择品牌"
                    }
                ) { page ->
                    if (page == target) interactive = LocalMotionActive.current
                    Column {
                        Text(page)
                        Button(onClick = { clicks++ }) { Text(action) }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        compose.mainClock.advanceTimeBy(16)
        compose.runOnUiThread {
            dispatcher.onBackPressed()
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnUiThread {
            backTarget = null
        }
        compose.mainClock.advanceTimeBy(800)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        compose.onNodeWithText("学习匹配").assertIsDisplayed()
        compose.onNodeWithText(action).performClick()
        compose.runOnIdle {
            assertEquals(0, onBackCalls)
            assertTrue(interactive)
            assertEquals(1, clicks)
        }
    }

    @Test fun confirmRemoteNameTruncatesOverlongInputAndSaveFailureStaysInlineWithoutSnackbar() {
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = RemoteRepository(app, scope)
        val transmitter = IrTransmitterManager(app, scope)
        val category = IrCategory(1, "电视", "TV", "tv")
        val brand = IrBrand(1, "索尼", "Sony", "suoni", 1)
        val model = IrRemoteModel("R_tv_1", 1, "电视", 1, "索尼", "Sony", 1, "方案1", 1)
        val power = RemoteButton("power", "电源", rawPattern = listOf(9000, 4500, 560))
        var failSave = false
        val catalog = object : IrCatalog {
            override suspend fun getCategories() = listOf(category)
            override suspend fun getBrandsForCategory(categoryId: Int, searchQuery: String) = listOf(brand)
            override suspend fun getRemotesForBrand(categoryId: Int, brandIdx: Int) = listOf(model)
            override suspend fun getTestKeysForRemote(remoteId: String) = power to null
            override suspend fun buildRemoteProfile(model: IrRemoteModel, customName: String?): RemoteProfile {
                if (failSave) throw java.io.IOException("storage full")
                return RemoteProfile(model.remoteId, customName ?: "索尼 电视", category = DeviceCategory.TV, buttons = listOf(power))
            }
        }
        val store = ViewModelStore()
        val vm = AddRemoteViewModel(catalog, repository, transmitter).also { store.put("add", it) }
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    AddRemoteScreen(viewModel = vm, onBack = {}, onRemoteCreated = {})
                }
            }
            compose.waitUntil(5_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                vm.uiState.value.categories.isNotEmpty()
            }
            compose.runOnIdle { vm.selectCategory(category) }
            compose.waitUntil(5_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                vm.uiState.value.brands.isNotEmpty()
            }
            compose.runOnIdle { vm.selectBrand(brand) }
            compose.waitUntil(5_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                vm.uiState.value.step == AddRemoteStep.WIZARD
            }
            compose.runOnIdle { vm.proceedToConfirm() }
            compose.waitUntil(5_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                vm.uiState.value.step == AddRemoteStep.CONFIRM
            }
            compose.waitForIdle()
            capture("confirm_remote_before")

            val nameField = compose.onNode(hasSetTextAction())
            val overlong = "A".repeat(90) + "B".repeat(30)
            nameField.performTextReplacement(overlong)
            compose.waitForIdle()
            assertEquals(100, vm.uiState.value.customRemoteName.length)
            assertEquals("A".repeat(90) + "B".repeat(10), vm.uiState.value.customRemoteName)

            failSave = true
            compose.onNodeWithText(app.getString(R.string.screen_save)).performScrollTo().performClick()
            compose.waitUntil(5_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                !vm.uiState.value.isSaving && vm.uiState.value.error != null
            }
            compose.waitForIdle()
            val expectedError = app.getString(R.string.msg_remote_save_failed, "storage full")
            compose.onAllNodesWithText(expectedError).assertCountEquals(1)
            capture("confirm_remote_save_error")

            // Advancing well past SnackbarDuration.Long must keep the inline error visible.
            compose.mainClock.advanceTimeBy(15_000)
            compose.waitForIdle()
            compose.onAllNodesWithText(expectedError).assertCountEquals(1)
            assertNotNull(vm.uiState.value.error)

            // Editing the name clears the stale save error.
            nameField.performTextReplacement("客厅电视")
            compose.waitForIdle()
            compose.onAllNodesWithText(expectedError).assertCountEquals(0)
            assertNull(vm.uiState.value.error)
        } finally {
            compose.runOnIdle { store.clear() }
            transmitter.release()
            scope.cancel()
        }
    }

    @Test fun menuMoveToTrashExecutesDirectlyAndRestoresFromUndoSnackbar() {
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = RemoteRepository(app, scope)
        val tv = RemoteProfile("delete_restore_tv", "客厅电视", category = DeviceCategory.TV, room = "客厅")
        runBlocking {
            repository.awaitLoaded()
            repository.saveRemote(tv)
        }
        val transmitter = IrTransmitterManager(app, scope)
        val store = ViewModelStore()
        val vm = RemoteListViewModel(repository, transmitter, ThemePreferences(app), BackupFileStore(app))
            .also { store.put("home_delete_restore", it) }
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    RemoteListScreen(vm, {}, {})
                }
            }
            compose.waitUntil(5_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                vm.filteredRemotes.value.any { it.id == tv.id }
            }
            compose.onNodeWithContentDescription(app.getString(R.string.screen_manage_name, tv.name)).performClick()
            compose.waitForIdle()
            compose.onNodeWithText(app.getString(R.string.screen_trash_remote)).performClick()
            compose.waitUntil(5_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                repository.remotes.value.none { it.id == tv.id }
            }
            compose.onNodeWithText(app.getString(R.string.screen_delete_remote)).assertDoesNotExist()
            val trashedMessage = app.resources.getQuantityString(R.plurals.screen_trashed_remotes, 1, 1)
            val undoLabel = app.getString(R.string.screen_undo)
            compose.onNodeWithText(trashedMessage).assertIsDisplayed()
            compose.onNodeWithText(undoLabel).assertIsDisplayed()
            capture("trash_snackbar_after_menu")

            compose.onNodeWithText(undoLabel).performClick()
            compose.waitUntil(5_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                repository.remotes.value.any { it.id == tv.id }
            }
            compose.onNodeWithText(tv.name).assertIsDisplayed()
            capture("trash_snackbar_after_undo")
            assertTrue(runBlocking { repository.deletedEntries() }.isEmpty())
        } finally {
            compose.runOnIdle { store.clear() }
            transmitter.release()
            runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }
}
