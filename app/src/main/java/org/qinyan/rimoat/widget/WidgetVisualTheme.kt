package org.qinyan.rimoat.widget

import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.material3.ColorProviders
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.AppColorMode
import org.qinyan.rimoat.ui.theme.DarkColorScheme
import org.qinyan.rimoat.ui.theme.LightColorScheme

internal data class WidgetSurfaceColors(
    val background: androidx.glance.unit.ColorProvider,
    val container: androidx.glance.unit.ColorProvider
)

internal val LocalWidgetSurfaces = staticCompositionLocalOf {
    WidgetSurfaceColors(
        background = ColorProvider(LightColorScheme.surfaceContainerLow, DarkColorScheme.surfaceContainerLow),
        container = ColorProvider(LightColorScheme.surfaceContainerHigh, DarkColorScheme.surfaceContainerHigh)
    )
}

/** Map the application's color roles to static RemoteViews surfaces. */
@Composable
internal fun WidgetVisualTheme(snapshot: WidgetSnapshot, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dynamic = snapshot.dynamicColor && Build.VERSION.SDK_INT >= 31
    val light = if (dynamic) dynamicLightColorScheme(context) else LightColorScheme
    val dark = if (dynamic) dynamicDarkColorScheme(context) else DarkColorScheme
    val day = if (snapshot.colorMode == AppColorMode.DARK) dark else light
    val night = if (snapshot.colorMode == AppColorMode.LIGHT) light else dark
    CompositionLocalProvider(LocalWidgetSurfaces provides WidgetSurfaceColors(
        background = ColorProvider(day.surfaceContainerLow, night.surfaceContainerLow),
        container = ColorProvider(day.surfaceContainerHigh, night.surfaceContainerHigh)
    )) {
        GlanceTheme(colors = ColorProviders(day, night), content = content)
    }
}

internal fun GlanceModifier.widgetContainer(color: androidx.glance.unit.ColorProvider): GlanceModifier {
    val background = background(ImageProvider(R.drawable.widget_inner_container), colorFilter = ColorFilter.tint(color))
    return if (Build.VERSION.SDK_INT >= 31) {
        background.cornerRadius(android.R.dimen.system_app_widget_inner_radius)
    } else background
}
