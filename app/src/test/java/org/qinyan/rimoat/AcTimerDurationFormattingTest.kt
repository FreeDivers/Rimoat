package org.qinyan.rimoat

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.data.AcAction
import org.qinyan.rimoat.data.AcMode
import org.qinyan.rimoat.data.AcSettings
import org.qinyan.rimoat.data.AcSignalEncoder
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedTimerDurationLabel
import org.qinyan.rimoat.ui.components.controlAppearance
import org.qinyan.rimoat.ui.screens.RemoteControlScreen
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.viewmodel.RemoteControlViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AcTimerDurationFormattingTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    @Before fun initializeStrings() = AppStrings.initialize(app)

    @Test fun timerAppearanceUsesLocalizedDurationFormat() {
        val eightHours = AcSettings(timerMinutes = 480).controlAppearance(AcAction.TIMER)
        assertEquals(localizedTimerDurationLabel(480), eightHours.label)
        assertEquals(localizedTimerDurationLabel(480), eightHours.state)
        assertEquals("8 小时", eightHours.label)

        val ninetyMinutes = AcSettings(timerMinutes = 90).controlAppearance(AcAction.TIMER)
        assertEquals(localizedTimerDurationLabel(90), ninetyMinutes.label)
        assertEquals(localizedTimerDurationLabel(90), ninetyMinutes.state)
        assertEquals("1 小时 30 分钟", ninetyMinutes.label)

        val thirtyMinutes = AcSettings(timerMinutes = 30).controlAppearance(AcAction.TIMER)
        assertEquals(localizedTimerDurationLabel(30), thirtyMinutes.label)
        assertEquals(localizedTimerDurationLabel(30), thirtyMinutes.state)
        assertEquals("30 分钟", thirtyMinutes.label)
    }

    @Test fun timerStatusChipUsesLocalizedDurationFormat() {
        val profile = RemoteProfile(
            id = "ac_timer",
            name = "AC",
            category = DeviceCategory.AC,
            acProtocolId = 670,
            acSettings = AcSettings(powerOn = true, timerMinutes = 480)
        )
        val encoder = object : AcSignalEncoder {
            override suspend fun protocolFor(sourceRemoteId: String) = 670
            override suspend fun supportedModes(protocol: Int) = listOf(AcMode.COOL)
            override suspend fun availableActions(protocol: Int) = setOf(AcAction.TIMER, AcAction.MODE, AcAction.FAN)
            override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction) = emptyList<IrSignal.Raw>()
        }
        val store = ViewModelStore()
        val vm = RemoteControlViewModel(profile.id, initialProfile = profile, acSignalEncoder = encoder)
            .also { store.put("remote", it) }
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                    RemoteControlScreen(vm, onBack = {})
                }
            }
            compose.waitUntil(5_000) { AcAction.TIMER in vm.availableAcActions.value }
            compose.onAllNodesWithText(localizedTimerDurationLabel(480), useUnmergedTree = true)
                .onFirst().assertIsDisplayed()
            compose.onNodeWithText("480 分钟", useUnmergedTree = true).assertDoesNotExist()
        } finally {
            compose.runOnIdle { store.clear() }
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-mdpi")
    fun englishTimerDurationHandlesSingularAndPluralUnits() {
        AppStrings.initialize(app)
        assertEquals("1 min", localizedTimerDurationLabel(1))
        assertEquals("30 mins", localizedTimerDurationLabel(30))
        assertEquals("1 hr", localizedTimerDurationLabel(60))
        assertEquals("8 hrs", localizedTimerDurationLabel(480))
        assertEquals("1 hr 1 min", localizedTimerDurationLabel(61))
        assertEquals("2 hrs 30 mins", localizedTimerDurationLabel(150))

        val eightHours = AcSettings(timerMinutes = 480).controlAppearance(AcAction.TIMER)
        assertEquals("8 hrs", eightHours.label)
        assertEquals("8 hrs", eightHours.state)
    }
}
