package org.qinyan.rimoat

import android.app.Application
import android.content.res.Configuration
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.os.LocaleList
import java.util.Locale
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

    private fun expectedMeasure(locale: Locale, vararg measures: Measure): String =
        MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT).formatMeasures(*measures)

    @Test fun timerAppearanceUsesLocalizedDurationFormat() {
        val zhLocale = app.resources.configuration.locales[0]
        val eightHours = AcSettings(timerMinutes = 480).controlAppearance(AcAction.TIMER, app)
        assertEquals(localizedTimerDurationLabel(480, app), eightHours.label)
        assertEquals(localizedTimerDurationLabel(480, app), eightHours.state)
        assertEquals(expectedMeasure(zhLocale, Measure(8, MeasureUnit.HOUR)), eightHours.label)

        val ninetyMinutes = AcSettings(timerMinutes = 90).controlAppearance(AcAction.TIMER, app)
        assertEquals(localizedTimerDurationLabel(90, app), ninetyMinutes.label)
        assertEquals(localizedTimerDurationLabel(90, app), ninetyMinutes.state)
        assertEquals(
            expectedMeasure(zhLocale, Measure(1, MeasureUnit.HOUR), Measure(30, MeasureUnit.MINUTE)),
            ninetyMinutes.label
        )

        val thirtyMinutes = AcSettings(timerMinutes = 30).controlAppearance(AcAction.TIMER, app)
        assertEquals(localizedTimerDurationLabel(30, app), thirtyMinutes.label)
        assertEquals(localizedTimerDurationLabel(30, app), thirtyMinutes.state)
        assertEquals(expectedMeasure(zhLocale, Measure(30, MeasureUnit.MINUTE)), thirtyMinutes.label)
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
        val enLocale = app.resources.configuration.locales[0]
        assertEquals(expectedMeasure(enLocale, Measure(1, MeasureUnit.MINUTE)), localizedTimerDurationLabel(1, app))
        assertEquals(expectedMeasure(enLocale, Measure(30, MeasureUnit.MINUTE)), localizedTimerDurationLabel(30, app))
        assertEquals(expectedMeasure(enLocale, Measure(1, MeasureUnit.HOUR)), localizedTimerDurationLabel(60, app))
        assertEquals(expectedMeasure(enLocale, Measure(8, MeasureUnit.HOUR)), localizedTimerDurationLabel(480, app))
        assertEquals(
            expectedMeasure(enLocale, Measure(1, MeasureUnit.HOUR), Measure(1, MeasureUnit.MINUTE)),
            localizedTimerDurationLabel(61, app)
        )
        assertEquals(
            expectedMeasure(enLocale, Measure(2, MeasureUnit.HOUR), Measure(30, MeasureUnit.MINUTE)),
            localizedTimerDurationLabel(150, app)
        )

        val eightHours = AcSettings(timerMinutes = 480).controlAppearance(AcAction.TIMER, app)
        assertEquals(expectedMeasure(enLocale, Measure(8, MeasureUnit.HOUR)), eightHours.label)
        assertEquals(expectedMeasure(enLocale, Measure(8, MeasureUnit.HOUR)), eightHours.state)
    }

    @Test
    fun timerDurationFollowsPassedContextLocaleAcrossAllSupportedLanguages() {
        for (tag in listOf("zh-CN", "zh-Hant", "en-US", "ja-JP")) {
            val localizedContext = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            })
            val locale = localizedContext.resources.configuration.locales[0]
            assertEquals(
                tag,
                expectedMeasure(locale, Measure(1, MeasureUnit.HOUR), Measure(30, MeasureUnit.MINUTE)),
                localizedTimerDurationLabel(90, localizedContext)
            )
            assertEquals(
                tag,
                localizedContext.getString(R.string.control_disable_timer),
                localizedTimerDurationLabel(0, localizedContext)
            )
        }
    }
}
