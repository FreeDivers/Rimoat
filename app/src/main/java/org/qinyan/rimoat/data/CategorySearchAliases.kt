package org.qinyan.rimoat.data

/** Preserve catalog category search terms independently of the display language. */
internal val DeviceCategory.catalogSearchAlias: String
    get() = when (this) {
        DeviceCategory.TV -> "电视"
        DeviceCategory.AC -> "空调"
        DeviceCategory.FAN -> "风扇"
        DeviceCategory.AUDIO -> "音响"
        DeviceCategory.PROJECTOR -> "投影仪"
        DeviceCategory.CUSTOM -> "自定义"
    }
