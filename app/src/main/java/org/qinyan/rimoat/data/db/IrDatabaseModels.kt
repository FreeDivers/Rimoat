package org.qinyan.rimoat.data.db

data class IrCategory(
    val id: Int,
    val nameCn: String,
    val nameEn: String,
    val code: String
)

data class IrBrand(
    val brandIdx: Int,
    val nameCn: String,
    val nameEn: String,
    val pinyin: String,
    val remoteCount: Int
)

data class IrRemoteModel(
    val remoteId: String,
    val categoryId: Int,
    val categoryName: String,
    val brandIdx: Int,
    val brandCn: String,
    val brandEn: String,
    val modelId: Int,
    val modelName: String,
    val keyCount: Int
)

data class IrKeyDetail(
    val keyName: String,
    val keyType: Int,
    val carrierFreq: Int,
    val pulsesCsv: String,
    val sourceSlot: String? = null,
    val alternateFrequency: Int = 38000,
    val alternatePulses: String = ""
)

