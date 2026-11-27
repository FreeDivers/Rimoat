package org.qinyan.rimoat.i18n

import android.content.Context
import org.qinyan.rimoat.data.RemoteProfile
import org.qinyan.rimoat.data.db.IrBrand

/** The caller supplies the catalog row for the stable brand ID; user-entered brands remain literal. */
fun RemoteProfile.localizedBrand(catalogBrand: IrBrand?, context: Context): String {
    if (brandIdx == null || catalogBrand?.brandIdx != brandIdx) return brand
    if (brand !in setOf(catalogBrand.nameCn, catalogBrand.nameEn)) return brand
    return UiText.CatalogBrand(catalogBrand.nameCn, catalogBrand.nameEn).resolve(context)
}

val IrBrand.displayText: UiText get() = catalogBrandText(nameCn, nameEn)

fun catalogBrandText(chinese: String, english: String): UiText =
    if (chinese == "其它" || chinese == "其他") UiText.Resource(org.qinyan.rimoat.R.string.screen_other_brand)
    else UiText.CatalogBrand(chinese, english)
