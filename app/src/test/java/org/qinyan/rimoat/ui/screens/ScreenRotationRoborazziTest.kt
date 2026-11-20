package org.qinyan.rimoat.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.RemoteRepository
import org.qinyan.rimoat.data.ScreenRotationCondition
import org.qinyan.rimoat.data.ScreenRotationDirection
import org.qinyan.rimoat.data.ScreenRotationScope
import org.qinyan.rimoat.data.ThemePreferences
import org.qinyan.rimoat.data.db.IrBrand
import org.qinyan.rimoat.data.db.IrCatalog
import org.qinyan.rimoat.data.db.IrCategory
import org.qinyan.rimoat.data.db.IrRemoteModel
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.hardware.IrTransmitterManager
import org.qinyan.rimoat.hardware.TransmitterType
import org.qinyan.rimoat.hardware.learning.IrLearnerManager
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.components.ScreenRotationHost
import org.qinyan.rimoat.ui.learning.CustomRemoteWizardScreen
import org.qinyan.rimoat.ui.learning.CustomRemoteWizardViewModel
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.AddRemoteStep
import org.qinyan.rimoat.ui.viewmodel.AddRemoteViewModel
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import org.qinyan.rimoat.ui.viewmodel.RemoteListViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    qualifiers = "zh-rCN-w411dp-h891dp-mdpi"
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class ScreenRotationRoborazziTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val screenshotOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(
            imageIoFormat = LosslessWebPImageIoFormat()
        )
    )

    @Test
    fun externalIrPromptAndTopBarRotationWorkAcrossRemoteLearningAndMatchingScreens() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = RemoteRepository(app, scope)
        val transmitterManager = IrTransmitterManager(app, scope)
        val learnerManager = IrLearnerManager(app, scope)
        val themePreferences = ThemePreferences(app).apply {
            setAutoRotateScreen(false)
            setSuppressRotationPrompt(false)
            setRotationDirection(ScreenRotationDirection.REVERSE_PORTRAIT)
            setRotationCondition(ScreenRotationCondition.EXTERNAL_ONLY)
            setRotationScope(ScreenRotationScope.REMOTE_AND_MATCHING)
        }

        // Simulate external audio IR transmitter plugged in and active
        transmitterManager.setForceMockMode(false)
        transmitterManager.audioTransmitter.updateHeadsetPlugged(true)
        transmitterManager.selectTransmitterType(TransmitterType.AUDIO)

        val sampleRemote = RemoteProfile(
            id = "rotation_tv",
            name = "客厅电视",
            category = DeviceCategory.TV,
            buttons = listOf(
                RemoteButton("power", "电源", iconName = "power", rawPattern = listOf(9000, 4500, 560)),
                RemoteButton("vol_up", "音量+", iconName = "volume_up", rawPattern = listOf(9000, 4500, 560))
            )
        )
        runBlocking {
            repository.awaitLoaded()
            repository.saveRemote(sampleRemote)
        }

        val remoteVm = RemoteControlViewModel(
            remoteId = sampleRemote.id,
            initialProfile = sampleRemote,
            repository = repository,
            transmitterManager = transmitterManager,
            learnerManager = learnerManager
        )
        val wizardVm = CustomRemoteWizardViewModel(
            repository = repository,
            transmitterManager = transmitterManager,
            learnerManager = learnerManager,
            initialCategory = DeviceCategory.TV
        )
        val fakeCatalog = object : IrCatalog {
            val cat = IrCategory(1, "电视", "TV", "tv")
            val brand = IrBrand(1, "测试品牌", "TestBrand", "cs", 1)
            val model = IrRemoteModel("R_tv_1", 1, "电视", 1, "测试品牌", "TestBrand", 1, "方案1", 1)
            override suspend fun getCategories() = listOf(cat)
            override suspend fun getBrandsForCategory(categoryId: Int, searchQuery: String) = listOf(brand)
            override suspend fun getRemotesForBrand(categoryId: Int, brandIdx: Int) = listOf(model)
            override suspend fun getTestKeysForRemote(remoteId: String) = null to null
            override suspend fun buildRemoteProfile(model: IrRemoteModel, customName: String?) = sampleRemote
            override suspend fun testSignalsFor(model: IrRemoteModel, button: RemoteButton) =
                listOf(IrSignal.Raw(38000, intArrayOf(9000, 4500, 560)))
        }
        val addVm = AddRemoteViewModel(
            irDbRepository = fakeCatalog,
            remoteRepository = repository,
            transmitterManager = transmitterManager
        )

        var currentPage by mutableStateOf("remote")

        try {
            composeRule.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    val isTarget = when (currentPage) {
                        "remote", "learning", "matching" -> true
                        else -> false
                    }
                    ScreenRotationHost(
                        themePreferences = themePreferences,
                        isTargetRotationScreen = isTarget,
                        targetScreenKey = currentPage,
                        isExternalIrConnected = true
                    ) {
                        when (currentPage) {
                            "remote" -> RemoteControlScreen(viewModel = remoteVm, onBack = {})
                            "learning" -> CustomRemoteWizardScreen(
                                viewModel = wizardVm,
                                onBack = {},
                                onRemoteCreated = {}
                            )
                            "matching" -> AddRemoteScreen(
                                viewModel = addVm,
                                onBack = {},
                                onRemoteCreated = {}
                            )
                        }
                    }
                }
            }
            composeRule.waitForIdle()

            // 1. Entering RemoteControlScreen with external IR connected shows the prompt dialog
            composeRule.onNodeWithText(app.getString(R.string.rotation_prompt_title)).assertIsDisplayed()
            composeRule.onNodeWithText(app.getString(R.string.rotation_prompt_dont_ask_again)).assertIsDisplayed()
            composeRule.onNode(isDialog()).captureRoboImage(
                filePath = "src/test/screenshots/rotation_prompt_dialog.webp",
                roborazziOptions = screenshotOptions
            )

            // Confirm rotation -> Activity rotates to REVERSE_PORTRAIT and top bar icon turns active
            composeRule.onNodeWithText(app.getString(R.string.rotation_prompt_confirm)).performClick()
            composeRule.waitForIdle()

            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                composeRule.activity.requestedOrientation
            )
            composeRule.onNodeWithContentDescription(app.getString(R.string.screen_restore_orientation))
                .assertIsDisplayed()
            composeRule.onRoot().captureRoboImage(
                filePath = "src/test/screenshots/remote_rotation_active.webp",
                roborazziOptions = screenshotOptions
            )

            // Click top-bar rotation icon to restore upright portrait
            composeRule.onNodeWithContentDescription(app.getString(R.string.screen_restore_orientation))
                .performClick()
            composeRule.waitForIdle()
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                composeRule.activity.requestedOrientation
            )
            composeRule.onNodeWithContentDescription(app.getString(R.string.screen_rotate_screen))
                .assertIsDisplayed()

            // 2. Navigate to Learning Wizard screen -> prompt appears again because "Don't ask again" was not checked
            currentPage = "home"
            composeRule.waitForIdle()
            currentPage = "learning"
            composeRule.waitForIdle()

            composeRule.onNodeWithText(app.getString(R.string.rotation_prompt_title)).assertIsDisplayed()
            // Check "Don't ask again" and dismiss
            composeRule.onNodeWithText(app.getString(R.string.rotation_prompt_dont_ask_again)).performClick()
            composeRule.onNodeWithText(app.getString(R.string.rotation_prompt_cancel)).performClick()
            composeRule.waitForIdle()

            assertTrue(themePreferences.suppressRotationPrompt.value)
            composeRule.onNodeWithContentDescription(app.getString(R.string.screen_rotate_screen)).assertIsDisplayed()

            // 3. Navigate to Code Matching Wizard screen -> prompt is suppressed, top bar rotate button is present
            composeRule.runOnIdle { addVm.selectCategory(fakeCatalog.cat) }
            composeRule.waitUntil(5_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                addVm.uiState.value.brands.isNotEmpty()
            }
            composeRule.runOnIdle { addVm.selectBrand(fakeCatalog.brand) }
            composeRule.waitUntil(5_000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                addVm.uiState.value.step == AddRemoteStep.WIZARD
            }
            currentPage = "matching"
            composeRule.waitForIdle()

            composeRule.onNodeWithText(app.getString(R.string.rotation_prompt_title)).assertDoesNotExist()
            composeRule.onNodeWithContentDescription(app.getString(R.string.screen_rotate_screen))
                .assertIsDisplayed()
                .performClick()
            composeRule.waitForIdle()
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                composeRule.activity.requestedOrientation
            )
        } finally {
            transmitterManager.release()
            learnerManager.unregisterReceiver()
            runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

    @Test
    fun settingsScreenConfiguresRotationDirectionAutoRotateConditionAndScope() {
        val app = RuntimeEnvironment.getApplication()
        AppStrings.initialize(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = RemoteRepository(app, scope)
        val transmitterManager = IrTransmitterManager(app, scope)
        val themePreferences = ThemePreferences(app).apply {
            setRotationDirection(ScreenRotationDirection.REVERSE_PORTRAIT)
            setAutoRotateScreen(false)
            setRotationCondition(ScreenRotationCondition.EXTERNAL_ONLY)
            setRotationScope(ScreenRotationScope.REMOTE_AND_MATCHING)
        }
        val viewModel = RemoteListViewModel(
            repository = repository,
            transmitterManager = transmitterManager,
            themePreferences = themePreferences
        )

        try {
            composeRule.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    ScreenRotationHost(
                        themePreferences = themePreferences,
                        isTargetRotationScreen = false,
                        targetScreenKey = "settings",
                        isExternalIrConnected = false
                    ) {
                        SettingsScreen(viewModel = viewModel, onBack = {})
                    }
                }
            }
            composeRule.waitForIdle()

            val autoRotateTitle = app.getString(R.string.settings_auto_rotate_screen)
            val conditionTitle = app.getString(R.string.settings_rotation_condition)
            val scopeTitle = app.getString(R.string.settings_rotation_scope)

            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(autoRotateTitle))
            composeRule.waitForIdle()

            composeRule.onNodeWithText(app.getString(R.string.settings_group_rotation)).assertIsDisplayed()
            composeRule.onNodeWithText(app.getString(R.string.settings_rotation_direction)).assertIsDisplayed()
            composeRule.onNodeWithText(autoRotateTitle).assertIsDisplayed()
            composeRule.onNodeWithText(conditionTitle).assertDoesNotExist()
            composeRule.onNodeWithText(scopeTitle).assertDoesNotExist()

            // Toggle Auto-rotate screen ON -> sub-options expand inside the bordered sub-box
            composeRule.onNodeWithText(autoRotateTitle).performClick()
            composeRule.waitForIdle()
            assertTrue(themePreferences.autoRotateScreen.value)

            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(scopeTitle))
            composeRule.waitForIdle()
            composeRule.onNodeWithText(conditionTitle).assertIsDisplayed()
            composeRule.onNodeWithText(scopeTitle).assertIsDisplayed()

            composeRule.onRoot().captureRoboImage(
                filePath = "src/test/screenshots/settings_screen_rotation.webp",
                roborazziOptions = screenshotOptions
            )

            // Set condition to ALWAYS and scope to ALL_SCREENS -> even Settings screen rotates automatically
            themePreferences.setRotationCondition(ScreenRotationCondition.ALWAYS)
            themePreferences.setRotationScope(ScreenRotationScope.ALL_SCREENS)
            composeRule.waitForIdle()

            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                composeRule.activity.requestedOrientation
            )

            // Change direction to LANDSCAPE -> Activity updates orientation immediately
            themePreferences.setRotationDirection(ScreenRotationDirection.LANDSCAPE)
            composeRule.waitForIdle()
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                composeRule.activity.requestedOrientation
            )

            // Turn off Auto-rotate -> Activity restores to upright PORTRAIT
            composeRule.onNodeWithText(app.getString(R.string.settings_auto_rotate_screen)).performClick()
            composeRule.waitForIdle()
            assertFalse(themePreferences.autoRotateScreen.value)
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                composeRule.activity.requestedOrientation
            )
        } finally {
            transmitterManager.release()
            runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }
}
