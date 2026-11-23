package org.qinyan.rimoat.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WindPower
import androidx.compose.ui.graphics.vector.ImageVector
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.RemoteProfile

fun getCategoryIcon(category: DeviceCategory): ImageVector = getCategoryIcon(
    when (category) {
        DeviceCategory.TV -> "tv"
        DeviceCategory.AC -> "air"
        DeviceCategory.FAN -> "fan"
        DeviceCategory.PROJECTOR -> "pjt"
        DeviceCategory.AUDIO -> "amp"
        DeviceCategory.CUSTOM -> "custom"
    }
)

fun getCategoryIcon(code: String): ImageVector = catalogCategoryIcon(code) ?: Icons.Rounded.DevicesOther

fun getCategoryIcon(profile: RemoteProfile): ImageVector {
    // Catalog IDs retain the specific appliance type even when saved categories group several types.
    val code = profile.sourceRemoteId?.takeIf { it.startsWith("R_") }
        ?.removePrefix("R_")?.removePrefix("compat_")?.removePrefix("spec_")?.substringBefore('_')
    return code?.let(::catalogCategoryIcon) ?: getCategoryIcon(profile.category)
}

private fun catalogCategoryIcon(code: String): ImageVector? = when (code.lowercase()) {
    "tv" -> Icons.Rounded.Tv
    "air" -> Icons.Rounded.AcUnit
    "fan" -> Icons.Rounded.WindPower
    "pjt" -> Icons.Rounded.Videocam
    "stb" -> Icons.Rounded.SettingsInputHdmi
    "dvd" -> Icons.Rounded.Album
    "amp" -> Icons.Rounded.Speaker
    "ipt" -> Icons.Rounded.Router
    "box" -> Icons.Rounded.DeveloperBoard
    "light" -> Icons.Rounded.Lightbulb
    "cam" -> Icons.Rounded.CameraAlt
    else -> null
}
