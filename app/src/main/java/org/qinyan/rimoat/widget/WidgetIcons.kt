package org.qinyan.rimoat.widget

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.glance.ImageProvider
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.ui.components.controlAppearance
import org.qinyan.rimoat.ui.components.getIconVector

// Reuse the App's vector paths. Only the resulting small bitmap crosses RemoteViews.
// 96 px is sufficient for a 24 dp icon at xxxhdpi; the bounded cache is at most 3 MB.
private val iconBitmaps = LruCache<String, Bitmap>(80)
internal val LocalWidgetRtl = staticCompositionLocalOf { false }

@Composable
internal fun widgetImage(vector: ImageVector): ImageProvider {
    val rtl = LocalWidgetRtl.current
    val density = Density(4f)
    var rendered: Bitmap? = null
    CompositionLocalProvider(LocalDensity provides density) {
        val painter = rememberVectorPainter(vector)
        rendered = remember(vector, rtl) {
            val key = "${vector.name}:$rtl"
            synchronized(iconBitmaps) {
                iconBitmaps[key] ?: Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { bitmap ->
                    CanvasDrawScope().draw(density, if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                        Canvas(bitmap.asImageBitmap()), Size(96f, 96f)) {
                        with(painter) { draw(Size(96f, 96f)) }
                    }
                    iconBitmaps.put(key, bitmap)
                }
            }
        }
    }
    return ImageProvider(checkNotNull(rendered))
}

internal fun widgetCommandVector(profile: RemoteProfile?, command: WidgetCommand, state: WidgetSnapshot, context: Context? = null): ImageVector {
    val action = widgetAcAction(profile, command)
    if (profile != null && action != null) {
        return widgetAcSettings(profile, state.records[profile.id], state.simulated).controlAppearance(action).icon
    }
    return getIconVector(command.iconName) ?: when (command.iconName) {
        "add" -> Icons.Rounded.Add
        "remove" -> Icons.Rounded.Remove
        "fan" -> Icons.Rounded.Air
        else -> when {
            command.isPower(profile, context) -> Icons.Rounded.PowerSettingsNew
            command.label in setOf("OK", "确认", "确定") -> Icons.Rounded.Check
            else -> Icons.Rounded.Tune
        }
    }
}
