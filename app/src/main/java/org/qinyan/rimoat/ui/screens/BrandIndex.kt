package org.qinyan.rimoat.ui.screens

import org.qinyan.rimoat.data.db.IrBrand
import java.text.Normalizer
import java.util.Locale

data class BrandIndexGroup(val letter: Char, val brands: List<IrBrand>)

private val COMBINING_MARKS_REGEX = Regex("\\p{M}+")

private fun normalizeIndexKey(value: String): String {
    var allAscii = true
    for (i in 0 until value.length) {
        if (value[i].code >= 128) {
            allAscii = false
            break
        }
    }
    if (allAscii) return value.uppercase(Locale.ROOT)
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS_REGEX, "")
        .uppercase(Locale.ROOT)
}

private fun IrBrand.indexName(preferEnglish: Boolean): String {
    val name = (if (preferEnglish) nameEn.trim() else "").ifBlank {
        pinyin.trim().ifBlank {
            nameCn.trim().takeIf { it.firstOrNull()?.isLetter() == true && it.first().code < 128 }
                ?: nameEn.trim().ifBlank { nameCn.trim() }
        }
    }
    return normalizeIndexKey(name)
}

internal fun groupBrandsByIndex(brands: List<IrBrand>, preferEnglish: Boolean = false): List<BrandIndexGroup> {
    if (brands.isEmpty()) return emptyList()
    val keyed = ArrayList<Pair<IrBrand, String>>(brands.size)
    for (brand in brands) {
        keyed.add(brand to brand.indexName(preferEnglish))
    }
    keyed.sortWith(
        compareBy<Pair<IrBrand, String>> { it.second }
            .thenBy { it.first.nameCn }
            .thenBy { it.first.brandIdx }
    )
    val buckets = Array<MutableList<IrBrand>?>(27) { null }
    for ((brand, name) in keyed) {
        val ch = name.firstOrNull()
        val slot = if (ch != null && ch in 'A'..'Z') (ch - 'A') else 26
        val bucket = buckets[slot] ?: ArrayList<IrBrand>().also { buckets[slot] = it }
        bucket.add(brand)
    }
    val result = ArrayList<BrandIndexGroup>(27)
    for (i in 0..25) {
        buckets[i]?.let { result.add(BrandIndexGroup(('A'.code + i).toChar(), it)) }
    }
    buckets[26]?.let { result.add(BrandIndexGroup('#', it)) }
    return result
}

internal fun brandHeaderPositions(groups: List<BrandIndexGroup>, leadingItems: Int): Map<Char, Int> {
    var index = leadingItems
    return groups.associate { group ->
        (group.letter to index).also { index += 1 + group.brands.size }
    }
}

internal fun brandIndexAt(y: Float, height: Int, count: Int): Int {
    if (height <= 0 || count <= 0 || !y.isFinite()) return 0
    return (y / height * count).toInt().coerceIn(0, count - 1)
}
