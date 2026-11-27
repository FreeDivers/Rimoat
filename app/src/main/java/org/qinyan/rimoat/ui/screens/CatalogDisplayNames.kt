package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.i18n.AppStrings
import android.content.Context
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.db.IrBrand
import org.qinyan.rimoat.data.db.IrCategory
import org.qinyan.rimoat.data.db.IrRemoteModel
import org.qinyan.rimoat.i18n.catalogBrandText
import org.qinyan.rimoat.i18n.displayText

/** Catalog identity and search fields stay unchanged; only visible names follow the UI language. */
internal fun IrBrand.displayName(context: Context): String =
    catalogBrandName(context, nameCn, nameEn)

internal fun IrBrand.displayName(preferEnglish: Boolean, otherBrandLabel: String): String {
    if (displayText is org.qinyan.rimoat.i18n.UiText.Resource) return otherBrandLabel
    return if (preferEnglish) nameEn.ifBlank { nameCn } else nameCn.ifBlank { nameEn }
}

internal fun IrRemoteModel.brandDisplayName(context: Context): String =
    catalogBrandName(context, brandCn, brandEn)

private fun catalogBrandName(context: Context, chinese: String, english: String): String =
    catalogBrandText(chinese, english).resolve(context)

internal fun IrCategory.displayName(context: Context? = null): String =
    displayText.resolve(context ?: AppStrings.resourceContext())
