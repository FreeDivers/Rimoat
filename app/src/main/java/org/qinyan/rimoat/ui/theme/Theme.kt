package org.qinyan.rimoat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import org.qinyan.rimoat.data.AppColorMode
import android.app.Activity
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import androidx.compose.runtime.remember

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightSurfaceTint,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = LightScrim,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest
)

val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkSurfaceTint,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = DarkScrim,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest
)

@Composable
fun IrRemoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    pureBlackBackground: Boolean = false,
    colorMode: AppColorMode? = null,
    content: @Composable () -> Unit
) {
    val resolvedDarkTheme = colorMode?.isDark(darkTheme) ?: darkTheme
    val view = LocalView.current
    val activityWindow = remember(view) {
        generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()?.window
    }
    SideEffect {
        activityWindow?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !resolvedDarkTheme
                isAppearanceLightNavigationBars = !resolvedDarkTheme
            }
        }
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (resolvedDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        resolvedDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        // Override only the base surfaces; elevated containers keep their contrast.
        colorScheme = if (resolvedDarkTheme && pureBlackBackground) colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black
        ) else colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
