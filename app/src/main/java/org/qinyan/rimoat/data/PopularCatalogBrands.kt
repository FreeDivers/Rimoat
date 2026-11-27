package org.qinyan.rimoat.data

/** Catalog source names identify recommended brands; presentation resolves their localized names. */
internal fun popularCatalogBrandNames(categoryCode: String?): List<String> = when (categoryCode) {
    "tv" -> listOf("小米", "创维", "海信", "索尼", "TCL", "飞利浦", "长虹", "三星", "其它")
    "air" -> listOf("格力", "美的", "海尔", "奥克斯", "志高", "三菱", "大金", "松下", "其它")
    "fan" -> listOf("美的", "艾美特", "格力", "先锋", "华生", "联创", "其它")
    else -> listOf("小米", "华为", "索尼", "飞利浦", "创维", "其它")
}
