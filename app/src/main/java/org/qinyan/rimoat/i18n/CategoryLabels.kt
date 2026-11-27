package org.qinyan.rimoat.i18n

import android.content.Context
import androidx.annotation.StringRes
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.DeviceCategory
import org.qinyan.rimoat.data.db.IrCategory

val DeviceCategory.labelRes: Int
    @StringRes get() = when (this) {
        DeviceCategory.TV -> R.string.control_category_tv
        DeviceCategory.AC -> R.string.control_category_ac
        DeviceCategory.FAN -> R.string.control_category_fan
        DeviceCategory.AUDIO -> R.string.control_category_audio
        DeviceCategory.PROJECTOR -> R.string.control_category_projector
        DeviceCategory.CUSTOM -> R.string.control_custom
    }

fun localizedLabel(value: DeviceCategory, context: Context? = null): String =
    context?.getString(value.labelRes) ?: AppStrings.get(value.labelRes)

val IrCategory.displayText: UiText
    get() = when (code) {
        "tv" -> UiText.Resource(R.string.screen_category_tv)
        "air" -> UiText.Resource(R.string.screen_category_ac)
        "fan" -> UiText.Resource(R.string.screen_category_fan)
        "pjt" -> UiText.Resource(R.string.screen_category_projector)
        "amp" -> UiText.Resource(R.string.screen_category_amplifier)
        "stb" -> UiText.Resource(R.string.screen_category_set_top_box)
        "dvd" -> UiText.Resource(R.string.screen_category_dvd)
        "cam" -> UiText.Resource(R.string.screen_category_camera)
        "light" -> UiText.Resource(R.string.screen_category_light)
        "box" -> UiText.Resource(R.string.screen_category_streaming_box)
        "ipt" -> UiText.Resource(R.string.screen_category_iptv)
        else -> UiText.Dynamic(nameCn)
    }
