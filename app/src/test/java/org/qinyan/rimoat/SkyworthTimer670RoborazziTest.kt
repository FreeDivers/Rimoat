package org.qinyan.rimoat

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.github.takahirom.roborazzi.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.hardware.IrSignal
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.i18n.localizedTimerDurationLabel
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
@OptIn(ExperimentalRoborazziApi::class)
class SkyworthTimer670RoborazziTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val options = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat()))

    private class TimerEncoder : AcSignalEncoder {
        override suspend fun protocolFor(sourceRemoteId: String) = 670
        override suspend fun supportedModes(protocol: Int) = listOf(AcMode.COOL)
        override suspend fun availableActions(protocol: Int) = setOf(AcAction.TIMER)
        override suspend fun supportsAction(protocol: Int, settings: AcSettings, action: AcAction) =
            protocol == 670 && action == AcAction.TIMER && AcTimerOptions.supports(protocol, settings)

        override suspend fun encode(protocol: Int, settings: AcSettings, action: AcAction): List<IrSignal.Raw> {
            check(supportsAction(protocol, settings, action))
            val original = SkyworthTimer670Fixture.frame(settings.timerMinutes, if (settings.powerOn) 0x20 else 0x40)
            val frame = SkyworthTimer670.restoreHours(original, settings.timerMinutes)
            return listOf(IrSignal.Raw(frame[0], frame.copyOfRange(1, frame.size)))
        }
    }

    @Test fun fullDayShutdownMatchesSelectedDurationAndSentFrame() = sendFullDay(powerOn = true)

    @Test fun fullDayStartupMatchesSelectedDurationAndSentFrame() = sendFullDay(powerOn = false)

    private fun sendFullDay(powerOn: Boolean) {
        AppStrings.initialize(app)
        val profile = RemoteProfile("ac", "AC", category = DeviceCategory.AC, acProtocolId = 670,
            acSettings = AcSettings(powerOn = powerOn))
        val sent = mutableListOf<IrSignal>()
        val store = ViewModelStore()
        val vm = RemoteControlViewModel(profile.id, initialProfile = profile, acSignalEncoder = TimerEncoder(),
            onTransmitSignal = { sent += it }).also { store.put("remote", it) }
        val prefix = if (powerOn) "skyworth_timer_670_shutdown" else "skyworth_timer_670_startup"
        try {
            compose.setContent {
                IrRemoteTheme(darkTheme = false, dynamicColor = false) { RemoteControlScreen(vm, onBack = {}) }
            }
            compose.waitUntil(5_000) { AcAction.TIMER in vm.availableAcActions.value }
            compose.onNodeWithContentDescription(localizedLabel(AcAction.TIMER.label))
                .performScrollTo().assertIsEnabled().performClick()
            compose.onNodeWithText(app.getString(if (powerOn) R.string.control_timer_off else R.string.control_timer_on))
                .assertIsDisplayed()
            capture("${prefix}_before", dialog = true)

            compose.onNodeWithContentDescription(app.getString(R.string.control_timer_duration))
                .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(1440f)) }
            compose.onNodeWithContentDescription(app.getString(R.string.control_set_duration))
                .assertTextEquals(localizedTimerDurationLabel(1440))
            compose.onNodeWithContentDescription(app.getString(R.string.control_extend_timer)).assertIsNotEnabled()
            assertTrue(sent.isEmpty())
            capture("${prefix}_selected", dialog = true)
            compose.onNodeWithText(app.getString(R.string.control_set_timer)).assertIsEnabled().performClick()
            compose.waitUntil(5_000) {
                vm.acState.value.timerMinutes == 1440 && !vm.isTransmitting.value &&
                    compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()
            }
            compose.runOnIdle {
                val signal = sent.single() as IrSignal.Raw
                val bytes = SkyworthTimer670Fixture.decode(intArrayOf(signal.frequencyHz) + signal.patternMicros)
                assertEquals(24, bytes[7])
                assertEquals(0, bytes[8] and 0x3f)
                assertEquals(bytes.take(13).sum() and 0xff, bytes[13])
                assertEquals(1440, vm.acState.value.timerMinutes)
                assertEquals(powerOn, vm.acState.value.powerOn)
                assertNull(vm.operationError.value)
            }
            compose.onAllNodesWithText(localizedTimerDurationLabel(1440), useUnmergedTree = true)
                .onFirst().performScrollTo().assertIsDisplayed()
            capture("${prefix}_sent")
        } finally {
            compose.runOnIdle { store.clear() }
        }
    }

    private fun capture(name: String, dialog: Boolean = false) =
        (if (dialog) compose.onNode(isDialog()) else compose.onRoot()).captureRoboImage(
            "src/test/screenshots/$name.webp", roborazziOptions = options)
}
