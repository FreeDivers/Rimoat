package org.qinyan.rimoat.i18n

import android.content.Context
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteButton

/** The data boundary supplies semantic metadata; presentation never reinterprets source text. */
fun localizedCatalogKeyLabel(button: RemoteButton, context: Context? = null): String? {
    if (button.sourceKeyType == null) return null
    val metadata = button.catalogLabel ?: return null
    fun text(id: Int, vararg args: Any) = context?.getString(id, *args) ?: AppStrings.get(id, *args)
    metadata.mode?.let { mode ->
        return text(R.string.catalog_power_temperature, text(mode.labelRes), metadata.temperature.orEmpty())
    }
    if (metadata.literalOk) return text(R.string.control_ok)
    metadata.key?.let { return text(it.labelRes) }
    val resource = when (metadata.customType) {
        2003 -> R.string.control_custom
        -100 -> R.string.control_custom_circle
        -99 -> R.string.control_custom_oval
        -98 -> R.string.control_custom_square
        else -> return null
    }
    return text(resource)
}

val org.qinyan.rimoat.data.CatalogFallbackLabel.labelRes: Int
    @androidx.annotation.StringRes get() = when (this) {
        org.qinyan.rimoat.data.CatalogFallbackLabel.CLEAR -> R.string.control_clear
        org.qinyan.rimoat.data.CatalogFallbackLabel.OFF -> R.string.control_off
        org.qinyan.rimoat.data.CatalogFallbackLabel.ON -> R.string.control_on
        org.qinyan.rimoat.data.CatalogFallbackLabel.DISABLED -> R.string.control_disabled
        org.qinyan.rimoat.data.CatalogFallbackLabel.CANCEL -> R.string.control_cancel
        org.qinyan.rimoat.data.CatalogFallbackLabel.DISPLAY -> R.string.control_display_group
        org.qinyan.rimoat.data.CatalogFallbackLabel.SOUND -> R.string.control_sound_group
        org.qinyan.rimoat.data.CatalogFallbackLabel.TELEVISION -> R.string.control_category_tv
        org.qinyan.rimoat.data.CatalogFallbackLabel.CUSTOM -> R.string.control_custom
        org.qinyan.rimoat.data.CatalogFallbackLabel.TIMER_OFF -> R.string.control_timer_off
        org.qinyan.rimoat.data.CatalogFallbackLabel.SAVE -> R.string.control_save
        org.qinyan.rimoat.data.CatalogFallbackLabel.TEMPERATURE_DOWN -> R.string.control_temp_down
        org.qinyan.rimoat.data.CatalogFallbackLabel.ENABLED -> R.string.control_enabled
        org.qinyan.rimoat.data.CatalogFallbackLabel.AUDIO -> R.string.control_category_audio
        org.qinyan.rimoat.data.CatalogFallbackLabel.PICTURE -> R.string.control_picture_group
        org.qinyan.rimoat.data.CatalogFallbackLabel.TOGGLE -> R.string.control_toggle
    }
