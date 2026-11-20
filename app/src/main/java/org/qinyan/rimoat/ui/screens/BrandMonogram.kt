package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.data.db.IrBrand

/** Every brand uses the first two Latin letters, uppercase; punctuation never becomes a mark. */
internal fun brandMonogram(brand: IrBrand): String {
    fun firstTwoLatinUpper(value: String): String? {
        var first = '\u0000'
        for (i in 0 until value.length) {
            val ch = when (val c = value[i]) {
                in 'A'..'Z' -> c
                in 'a'..'z' -> (c.code - 32).toChar()
                else -> continue
            }
            if (first == '\u0000') {
                first = ch
            } else {
                return charArrayOf(first, ch).concatToString()
            }
        }
        return null
    }
    return firstTwoLatinUpper(brand.nameEn)
        ?: firstTwoLatinUpper(brand.pinyin)
        ?: firstTwoLatinUpper(brand.nameCn)
        ?: "IR"
}
