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
