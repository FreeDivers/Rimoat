package org.qinyan.rimoat

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.components.AcTemperatureCard
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AcPowerLearningSemanticsTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    @Before fun initializeStrings() = AppStrings.initialize(app)

    @Test fun learningModePowerButtonOmitsStateDescriptionAndToggleableState() {
        compose.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                AcTemperatureCard(
                    temperature = 26,
                    powerOn = true,
                    isDemonstrating = false,
                    canIncrease = true,
                    canDecrease = true,
                    canTogglePower = true,
                    isLearningMode = true,
                    onIncrease = {},
                    onDecrease = {},
                    onTogglePower = {}
                )
            }
        }
        val learnPower = compose.onNodeWithContentDescription(app.getString(R.string.control_learn_power))
        learnPower.assertIsDisplayed()
        val config = learnPower.fetchSemanticsNode().config
        assertFalse(
            "Learning mode power button must not expose ToggleableState",
            SemanticsProperties.ToggleableState in config
        )
        assertFalse(
            "Learning mode power button must not expose StateDescription",
            SemanticsProperties.StateDescription in config
        )
    }

    @Test fun normalModePowerButtonKeepsSwitchSemanticsAndStateDescription() {
        compose.setContent {
            IrRemoteTheme(darkTheme = false, dynamicColor = false) {
                AcTemperatureCard(
                    temperature = 26,
                    powerOn = true,
                    isDemonstrating = false,
                    canIncrease = true,
                    canDecrease = true,
                    canTogglePower = true,
                    isLearningMode = false,
                    onIncrease = {},
                    onDecrease = {},
                    onTogglePower = {}
                )
            }
        }
        val powerOff = compose.onNodeWithContentDescription(app.getString(R.string.control_power_off))
        powerOff.assertIsDisplayed()
        powerOff.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        powerOff.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                app.getString(R.string.control_set_on)
            )
        )
    }
}
