package org.qinyan.rimoat.ui.components

import android.app.Application
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.ui.screens.tvRemoteItems
import org.qinyan.rimoat.ui.screens.fanRemoteItems
import org.qinyan.rimoat.ui.screens.genericRemoteItems
import org.qinyan.rimoat.ui.theme.ExpressiveSurface
import org.qinyan.rimoat.ui.theme.ProvideExpressiveCanvas
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoundEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoundEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.R
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedTimerDurationLabel
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.theme.SilenceWindowClickSounds
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class ExpressiveRemoteControlsRoborazziTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val originalTimeZone = TimeZone.getDefault()
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    @Before fun initializeStrings() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        AppStrings.initialize(app)
    }

    @After fun restoreTimeZone() = TimeZone.setDefault(originalTimeZone)

    @Test fun pressedRemoteRestoresBoundsAndSendsOnceWithoutSystemClickSound() {
        var sends = 0
        var clickSounds = 0
        val sound = object : SoundEffect {
            override fun playClickSound() { clickSounds++ }
        }
        val label = AppStrings.get(R.string.control_power_on)
        compose.setContent {
            CompositionLocalProvider(LocalSoundEffect provides sound) {
                IrRemoteTheme(dynamicColor = false) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            RemoteButtonView(label, iconName = "power", onClick = { sends++ },
                                variant = RemoteButtonVariant.POWER, diameter = 72.dp)
                            TextButton(onClick = {}, modifier = Modifier.testTag("ordinary_action")) {
                                Text(AppStrings.get(R.string.control_done))
                            }
                        }
                    }
                }
            }
        }
        val key = compose.onNodeWithContentDescription(label)
        val restingBounds = key.fetchSemanticsNode().boundsInRoot
        capture("expressive_remote_resting")
        compose.mainClock.autoAdvance = false
        key.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertEquals(0, sends) }
        capture("expressive_remote_pressed")
        key.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, sends)
            assertEquals(0, clickSounds)
        }
        assertEquals(restingBounds, key.fetchSemanticsNode().boundsInRoot)
        capture("expressive_remote_released")
        compose.onNodeWithTag("ordinary_action").performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, clickSounds) }
    }

    @Test fun timerTogglePresetUpdatesSelectionBeforeToolbarSends() = exerciseTimer(fontScale = 1f)

    @Test fun timerSliderRebuildsItsRangeWhenOptionsExpand() {
        var timerOptions by mutableStateOf(listOf(0, 60))
        var selected by mutableIntStateOf(0)
        compose.setContent {
            IrRemoteTheme(dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(AppStrings.get(R.string.control_timer_duration))
                        TimerDurationSlider(timerOptions, selected, enabled = true,
                            onSelect = { selected = it })
                        Text(localizedTimerDurationLabel(timerOptions[selected]))
                    }
                }
            }
        }
        capture("expressive_timer_slider_short_range")
        compose.runOnIdle { timerOptions = listOf(0, 30, 60, 120, 240, 480) }
        compose.onNodeWithContentDescription(AppStrings.get(R.string.control_timer_duration))
            .performSemanticsAction(SemanticsActions.SetProgress) { changeProgress ->
                assertTrue(changeProgress(5f))
            }
        compose.runOnIdle { assertEquals(5, selected) }
        compose.onNodeWithText(localizedTimerDurationLabel(480)).assertIsDisplayed()
        capture("expressive_timer_slider_expanded_range")
    }

    @Test fun dialogHostDisablesClickSoundAndRestoresItsOriginalFlag() {
        var showDialog by mutableStateOf(true)
        lateinit var dialogView: View
        var originalSoundEnabled = false
        compose.setContent {
            IrRemoteTheme(dynamicColor = false) {
                if (showDialog) Dialog(onDismissRequest = { showDialog = false }) {
                    val view = LocalView.current
                    val original = remember(view) { view.isSoundEffectsEnabled }
                    SilenceWindowClickSounds()
                    SideEffect {
                        dialogView = view
                        originalSoundEnabled = original
                    }
                    Surface { Text(AppStrings.get(R.string.control_done)) }
                }
            }
        }
        compose.onNode(isDialog()).assertIsDisplayed()
        compose.runOnIdle {
            assertTrue("The new Dialog host starts with platform click sounds enabled", originalSoundEnabled)
            assertFalse("Every control in the Dialog host must be silent", dialogView.isSoundEffectsEnabled)
            showDialog = false
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(originalSoundEnabled, dialogView.isSoundEffectsEnabled) }
    }

    @Test
    @Config(qualifiers = "en-rUS-w320dp-h891dp-mdpi")
    fun timerActionsStayReadableOnNarrowScreenWithLargeText() = exerciseTimer(fontScale = 2f)

    private fun exerciseTimer(fontScale: Float) {
        RuntimeEnvironment.setFontScale(fontScale)
        var sentMinutes: Int? = null
        compose.setContent {
            IrRemoteTheme(dynamicColor = false) {
                AcTimerBottomSheet(
                    options = listOf(0, 30, 60, 120, 240, 480),
                    currentMinutes = 60,
                    powerOn = true,
                    onDismiss = {},
                    onSend = { sentMinutes = it },
                    nowMillis = { 1_704_110_400_000L }
                )
            }
        }
        val titleLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(AppStrings.get(R.string.control_timer_off), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleLayouts) }
        val titleLayout = titleLayouts.single()
        assertEquals(fontScale, titleLayout.layoutInput.density.fontScale, 0.001f)
        if (fontScale > 1f) {
            assertTrue("Dialog title must render with enlarged text",
                titleLayout.getLineBottom(0) - titleLayout.getLineTop(0) > 40f)
        }
        val prefix = if (fontScale == 1f) "expressive_timer" else "expressive_timer_large_text"
        capture("${prefix}_initial", dialog = true)
        val preset = compose.onNode(hasText(localizedTimerDurationLabel(120)) and isToggleable())
        preset.performScrollTo().performClick().assertIsOn().assertIsSelected()
        compose.runOnIdle { assertEquals(null, sentMinutes) }
        capture("${prefix}_selected", dialog = true)
        compose.onNodeWithText(AppStrings.get(R.string.control_disable_timer)).assertIsDisplayed()
        compose.onNodeWithText(AppStrings.get(R.string.control_set_timer)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(120, sentMinutes) }
    }

    @Test fun powerKeysKeepTheirAccentInLightTheme() = exercisePowerPanels("light")

    @Test fun powerKeysKeepTheirAccentInDarkTheme() = exercisePowerPanels("dark", dark = true)

    @Test fun powerKeysKeepTheirAccentOnAmoledCanvas() = exercisePowerPanels("amoled", dark = true, pureBlack = true)

    @Test fun powerKeysKeepTheirAccentWithDynamicColors() = exercisePowerPanels("dynamic", dynamic = true)

    private fun exercisePowerPanels(name: String, dark: Boolean = false, pureBlack: Boolean = false, dynamic: Boolean = false) {
        val television = RemoteProfile("tv", "Television", category = DeviceCategory.TV,
            buttons = listOf(RemoteButton("tv_power", "TV power", key = ButtonKey.POWER,
                rawPattern = listOf(9000, 4500, 560))))
        val fan = RemoteProfile("fan", "Fan", category = DeviceCategory.FAN,
            buttons = listOf(RemoteButton("fan_power", "Fan power", key = ButtonKey.POWER,
                rawPattern = listOf(9000, 4500, 560))))
        val generic = RemoteProfile("remote", "Universal remote", category = DeviceCategory.CUSTOM,
            buttons = listOf(RemoteButton("custom_power", "Remote power", key = ButtonKey.POWER,
                rawPattern = listOf(9000, 4500, 560))))
        val profiles = listOf(television, fan, generic)
        var sentIds by mutableStateOf(emptyList<String>())
        fun availableProfile(profile: RemoteProfile) = profile.copy(buttons = profile.buttons.map {
            if (it.id in sentIds) it.copy(rawPattern = emptyList()) else it
        })
        val send: (RemoteButton) -> Unit = { sentIds = sentIds + it.id }
        compose.setContent {
            IrRemoteTheme(darkTheme = dark, dynamicColor = dynamic, pureBlackBackground = pureBlack) {
                ProvideExpressiveCanvas {
                    Surface(Modifier.fillMaxSize(), color = ExpressiveSurface.canvas) {
                        LazyColumn(Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item("tv_title") { Text(television.name, style = MaterialTheme.typography.titleMedium) }
                            tvRemoteItems(availableProfile(television), send)
                            item("fan_title") { Text(fan.name, style = MaterialTheme.typography.titleMedium) }
                            fanRemoteItems(availableProfile(fan), send)
                            item("generic_title") { Text(generic.name, style = MaterialTheme.typography.titleMedium) }
                            genericRemoteItems(availableProfile(generic), send)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        profiles.forEach { profile ->
            val key = compose.onNodeWithContentDescription(profile.buttons.single().label)
            key.assertIsDisplayed()
            val bounds = key.fetchSemanticsNode().boundsInRoot
            assertEquals("Power controls keep the same 72dp target", 72f, bounds.width, 0.1f)
            assertEquals(72f, bounds.height, 0.1f)
        }
        capture("remote_power_${name}_before")
        profiles.forEach { profile ->
            val key = compose.onNodeWithContentDescription(profile.buttons.single().label)
            key.performTouchInput { click() }
            compose.waitForIdle()
            key.assertIsNotEnabled()
            key.performTouchInput { click() }
        }
        compose.runOnIdle { assertEquals(profiles.map { it.buttons.single().id }, sentIds) }
        capture("remote_power_${name}_after")
    }

    private fun capture(name: String, dialog: Boolean = false) =
        (if (dialog) compose.onNode(isDialog()) else compose.onRoot()).captureRoboImage(
        "src/test/screenshots/$name.webp", roborazziOptions = options
    )
}
