package org.qinyan.rimoat

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.data.AppColorMode
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteButton
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.i18n.AppLanguage
import org.qinyan.rimoat.widget.PickerMetadataShadow
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 35], qualifiers = "en-rUS-w411dp-h891dp-mdpi",
    application = IrRemoteApplication::class, shadows = [PickerMetadataShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class LanguageSwitchRoborazziTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private var previousActivityContexts: String? = null

    @Before
    fun separateActivityAndApplicationResources() {
        previousActivityContexts = System.getProperty("robolectric.createActivityContexts")
        // AppCompat reads system locales from Application resources on Android 12 and below.
        System.setProperty("robolectric.createActivityContexts", "true")
    }

    @After
    fun restoreActivityContextConfiguration() {
        previousActivityContexts?.let { System.setProperty("robolectric.createActivityContexts", it) }
            ?: System.clearProperty("robolectric.createActivityContexts")
    }

    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    @Test
    fun settingsSwitchRefreshesRetainedHomeAndWidgetStateAcrossRecreation() {
        val app = (RuntimeEnvironment.getApplication() as IrRemoteApplication)
        Shadows.shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        app.themePreferences.setColorMode(AppColorMode.LIGHT)
        val profile = RemoteProfile(id = "language_remote", name = "Living room TV", room = "Living room",
            category = DeviceCategory.TV,
            buttons = listOf(RemoteButton("power", "Power", rawPattern = listOf(9000, 4500, 560))))
        runBlocking {
            app.repository.awaitLoaded()
            app.repository.saveRemote(profile)
            app.widgets.awaitReady()
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        fun currentString(id: Int) = controller.get().getString(id)
        fun capture(name: String) {
            if (Build.VERSION.SDK_INT == 35) composeRule.onRoot().captureRoboImage(
                "src/test/screenshots/language_$name.webp", roborazziOptions = options)
        }
        try {
            composeRule.waitUntil(10_000) { app.repository.remotes.value.any { it.id == profile.id } }
            composeRule.onNodeWithText("Living room TV").assertIsDisplayed()
            composeRule.onNodeWithText("All rooms").assertIsDisplayed()
            capture("home_before")

            val choices = listOf(
                Triple("ja", R.string.language_japanese, "ja"),
                Triple("zh-CN", R.string.language_chinese, "zh"),
                Triple("zh-Hant", R.string.settings_language_traditional_chinese, "hant"),
                Triple("", R.string.language_system, "system"),
                Triple("en", R.string.language_english, "en")
            )
            for ((tag, label, screenshot) in choices) {
                composeRule.onNodeWithContentDescription(currentString(R.string.screen_settings)).performClick()
                composeRule.onNodeWithText(currentString(R.string.settings_language)).performScrollTo().performClick()
                val previous = controller.get()
                composeRule.onNodeWithText(currentString(label)).performClick()
                composeRule.waitForIdle()
                if (Build.VERSION.SDK_INT >= 33 && tag != "en") {
                    // The LocaleManager shadow stores locales but does not dispatch framework configuration changes.
                    composeRule.runOnUiThread {
                        controller.configurationChange(
                            Configuration(AppLanguage.wrap(app).resources.configuration),
                            previous.resources.displayMetrics,
                            ActivityInfo.CONFIG_LOCALE or ActivityInfo.CONFIG_LAYOUT_DIRECTION
                        )
                        controller.visible()
                    }
                }
                composeRule.waitForIdle()
                assertEquals(tag, AppLanguage.selectedTag(app))
                if (tag != "en") assertNotSame("Language '$tag' must recreate the retained pages", previous, controller.get())
                composeRule.onNodeWithText(currentString(R.string.settings_title)).assertIsDisplayed()
                if (tag == "ja") capture("settings_after")
                composeRule.onNodeWithContentDescription(currentString(R.string.settings_back)).performClick()
                composeRule.onNodeWithText(currentString(R.string.screen_all_rooms)).assertIsDisplayed()
                composeRule.onNodeWithText(currentString(R.string.screen_category_tv)).assertIsDisplayed()
                composeRule.onNodeWithText("Living room TV").assertIsDisplayed()
                capture("home_$screenshot")

                val expectedLocales = AppLanguage.wrap(app).resources.configuration.locales.toLanguageTags()
                composeRule.waitUntil(10_000) {
                    Shadows.shadowOf(Looper.getMainLooper()).idle()
                    app.widgets.state.value.localeTags == expectedLocales
                }
                // A later repository emission must retain the current language as well.
                runBlocking { app.repository.saveRemote(profile.copy(isFavorite = tag.isNotEmpty())) }
                composeRule.waitUntil(10_000) {
                    app.widgets.state.value.profiles.single().isFavorite == tag.isNotEmpty()
                }
                assertEquals(expectedLocales, app.widgets.state.value.localeTags)
                assertEquals(profile.name, app.repository.remotes.value.single().name)
                assertEquals(profile.room, app.repository.remotes.value.single().room)
            }
        } finally {
            controller.pause().stop().destroy()
            app.transmitterManager.release()
            app.learnerManager.unregisterReceiver()
            runBlocking { app.applicationScope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }

    @Test
    fun remoteControlOperationErrorResolvesInNewLanguageAfterLocaleChange() {
        val app = (RuntimeEnvironment.getApplication() as IrRemoteApplication)
        Shadows.shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        app.themePreferences.setColorMode(AppColorMode.LIGHT)
        val missingSignalButton = RemoteButton("power", "电源", iconName = "power")
        val profile = RemoteProfile(
            id = "language_error_remote",
            name = "Study TV",
            category = DeviceCategory.TV,
            buttons = listOf(missingSignalButton)
        )
        runBlocking {
            app.repository.awaitLoaded()
            app.repository.saveRemote(profile)
            app.widgets.awaitReady()
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        fun currentString(id: Int) = controller.get().getString(id)
        try {
            composeRule.waitUntil(10_000) { app.repository.remotes.value.any { it.id == profile.id } }

            // Switch to Simplified Chinese first, then enter the remote control page.
            composeRule.onNodeWithContentDescription(currentString(R.string.screen_settings)).performClick()
            composeRule.onNodeWithText(currentString(R.string.settings_language)).performScrollTo().performClick()
            val initialActivity = controller.get()
            composeRule.onNodeWithText(currentString(R.string.language_chinese)).performClick()
            composeRule.waitForIdle()
            if (Build.VERSION.SDK_INT >= 33) {
                composeRule.runOnUiThread {
                    controller.configurationChange(
                        Configuration(AppLanguage.wrap(app).resources.configuration),
                        initialActivity.resources.displayMetrics,
                        ActivityInfo.CONFIG_LOCALE or ActivityInfo.CONFIG_LAYOUT_DIRECTION
                    )
                    controller.visible()
                }
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(currentString(R.string.settings_back)).performClick()
            composeRule.onNodeWithText("Study TV").performClick()
            composeRule.waitForIdle()

            // Trigger an operationError on the retained RemoteControlViewModel, then switch language to English
            // and recreate the Activity so the same ViewModel displays its pending error in the new language.
            val zhActivity = controller.get()
            val controlOwner = androidx.lifecycle.ViewModelProvider(zhActivity)[PageViewModelStores::class.java]
                .ownerFor(AppScreen.RemoteControl(profile.id))
            val controlViewModel = androidx.lifecycle.ViewModelProvider(controlOwner)[org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel::class.java]

            val zhMessage = zhActivity.getString(R.string.msg_button_learn_first)
            composeRule.runOnUiThread { controlViewModel.sendButton(missingSignalButton) }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(androidx.compose.ui.test.hasText(zhMessage)).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(zhMessage).assertIsDisplayed()
            if (Build.VERSION.SDK_INT == 35) composeRule.onRoot().captureRoboImage(
                "src/test/screenshots/language_remote_error_zh.webp", roborazziOptions = options
            )

            // Switch language to English while the operationError is still pending on the retained ViewModel.
            composeRule.runOnUiThread {
                AppLanguage.set(zhActivity, "en")
                if (Build.VERSION.SDK_INT >= 33) {
                    controller.configurationChange(
                        Configuration(AppLanguage.wrap(app).resources.configuration),
                        zhActivity.resources.displayMetrics,
                        ActivityInfo.CONFIG_LOCALE or ActivityInfo.CONFIG_LAYOUT_DIRECTION
                    )
                    controller.visible()
                }
            }
            composeRule.waitForIdle()
            assertNotSame("Language switch must recreate MainActivity", zhActivity, controller.get())
            val enMessage = controller.get().getString(R.string.msg_button_learn_first)
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(androidx.compose.ui.test.hasText(enMessage)).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(enMessage).assertIsDisplayed()
            if (Build.VERSION.SDK_INT == 35) composeRule.onRoot().captureRoboImage(
                "src/test/screenshots/language_remote_error_en.webp", roborazziOptions = options
            )
        } finally {
            controller.pause().stop().destroy()
            app.transmitterManager.release()
            app.learnerManager.unregisterReceiver()
            runBlocking { app.applicationScope.coroutineContext[Job]!!.cancelAndJoin() }
        }
    }
}
