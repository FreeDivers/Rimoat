package org.qinyan.rimoat.ui.screens

import android.content.Context
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.db.IrBrand
import org.qinyan.rimoat.data.db.IrCategory
import org.qinyan.rimoat.data.db.IrRemoteModel
import org.qinyan.rimoat.i18n.AppLanguage
import org.qinyan.rimoat.i18n.AppStrings

/** Catalog identity and search fields stay unchanged; only visible names follow the UI language. */
internal fun IrBrand.displayName(context: Context): String =
    catalogBrandName(context, nameCn, nameEn)

internal fun IrBrand.displayName(preferEnglish: Boolean, otherBrandLabel: String): String {
    if (nameCn == "其它" || nameCn == "其他") return otherBrandLabel
    return if (preferEnglish) nameEn.ifBlank { nameCn } else nameCn.ifBlank { nameEn }
}

internal fun IrRemoteModel.brandDisplayName(context: Context): String =
    catalogBrandName(context, brandCn, brandEn)

private fun catalogBrandName(context: Context, chinese: String, english: String): String {
    if (chinese == "其它" || chinese == "其他") return context.getString(R.string.screen_other_brand)
    val language = AppLanguage.wrap(context).resources.configuration.locales[0].language
    return if (language == "zh") chinese.ifBlank { english } else english.ifBlank { chinese }
}

internal fun IrCategory.displayName(context: Context? = null): String {
    val resId = when (code) {
        "tv" -> R.string.screen_category_tv
        "air" -> R.string.screen_category_ac
        "fan" -> R.string.screen_category_fan
        "pjt" -> R.string.screen_category_projector
        "amp" -> R.string.screen_category_amplifier
        "stb" -> R.string.screen_category_set_top_box
        "dvd" -> R.string.screen_category_dvd
        "cam" -> R.string.screen_category_camera
        "light" -> R.string.screen_category_light
        "box" -> R.string.screen_category_streaming_box
        "ipt" -> R.string.screen_category_iptv
        else -> null
    }
    return if (resId != null) context?.getString(resId) ?: AppStrings.get(resId)
    else org.qinyan.rimoat.i18n.localizedLabel(nameCn, context)
}
