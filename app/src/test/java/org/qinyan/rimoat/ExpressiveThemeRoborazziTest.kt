package org.qinyan.rimoat

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qinyan.rimoat.i18n.AppStrings
import org.qinyan.rimoat.ui.components.AcTemperatureCard
import org.qinyan.rimoat.ui.motion.AppMotion
import org.qinyan.rimoat.ui.theme.DarkColorScheme
import org.qinyan.rimoat.ui.theme.IrRemoteTheme
import org.qinyan.rimoat.ui.theme.LightColorScheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalRoborazziApi::class)
class ExpressiveThemeRoborazziTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val options = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(imageIoFormat = LosslessWebPImageIoFormat())
    )

    @Before fun initializeStrings() = AppStrings.initialize(app)

    @Test fun brandedColorsKeepElevatedContrastOnAmoled() = exerciseTheme(dynamic = false)

    @Test fun wallpaperColorsSurviveAmoledAndLightModeChanges() = exerciseTheme(dynamic = true)

    @Test fun localMotionOverrideIsUsedByAppOwnedAnimations() {
        val localScheme = MotionScheme.standard()
        var observedScheme: MotionScheme? = null
        compose.setContent {
            IrRemoteTheme {
                MaterialTheme(motionScheme = localScheme) {
                    val current = AppMotion.current
                    SideEffect { observedScheme = current }
                }
            }
        }
        compose.runOnIdle { assertSame(localScheme, observedScheme) }
    }

    private fun exerciseTheme(dynamic: Boolean) {
        var dark by mutableStateOf(true)
        var pureBlack by mutableStateOf(false)
        var temperature by mutableIntStateOf(24)
        var observedColors: ColorScheme? = null
        var observedMotion: MotionScheme? = null
        val darkColors = if (dynamic) dynamicDarkColorScheme(app) else DarkColorScheme
        val lightColors = if (dynamic) dynamicLightColorScheme(app) else LightColorScheme
        val prefix = if (dynamic) "expressive_dynamic" else "expressive_branded"

        compose.setContent {
            IrRemoteTheme(darkTheme = dark, dynamicColor = dynamic, pureBlackBackground = pureBlack) {
                val colors = MaterialTheme.colorScheme
                val motion = AppMotion.current
                SideEffect {
                    observedColors = colors
                    observedMotion = motion
                }
                Surface(Modifier.fillMaxSize(), color = colors.background) {
                    Box(Modifier.padding(16.dp)) {
                        AcTemperatureCard(
                            temperature = temperature,
                            powerOn = true,
                            isDemonstrating = true,
                            canIncrease = temperature < 30,
                            canDecrease = temperature > 16,
                            canTogglePower = false,
                            isLearningMode = false,
                            onIncrease = { temperature++ },
                            onDecrease = { temperature-- },
                            onTogglePower = {}
                        )
                    }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(darkColors.background, observedColors!!.background)
            assertEquals(darkColors.primary, observedColors!!.primary)
            assertSame(MotionScheme.expressive(), observedMotion)
        }
        capture("${prefix}_dark")
        compose.onNodeWithContentDescription(AppStrings.get(R.string.control_temp_up)).performClick()
        compose.runOnIdle {
            assertEquals(25, temperature)
            pureBlack = true
        }
        compose.runOnIdle {
            val colors = observedColors!!
            assertEquals(Color.Black, colors.background)
            assertEquals(Color.Black, colors.surface)
            assertEquals(Color.Black, colors.surfaceDim)
            assertEquals(Color.Black, colors.surfaceContainerLowest)
            assertEquals(darkColors.primary, colors.primary)
            assertEquals(darkColors.surfaceContainer, colors.surfaceContainer)
            assertEquals(darkColors.surfaceContainerHigh, colors.surfaceContainerHigh)
        }
        capture("${prefix}_amoled_after_temperature_change")
        compose.runOnIdle { dark = false }
        compose.runOnIdle {
            assertEquals(lightColors.background, observedColors!!.background)
            assertEquals(lightColors.surface, observedColors!!.surface)
            assertEquals(lightColors.primary, observedColors!!.primary)
        }
        capture("${prefix}_light")
    }

    private fun capture(name: String) = compose.onRoot().captureRoboImage(
        "src/test/screenshots/$name.webp", roborazziOptions = options
    )
}
