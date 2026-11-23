package org.qinyan.rimoat.data

import kotlinx.serialization.Serializable

/** Specific appliance identity, independent of the broad remote-list filters. */
@Serializable
enum class ApplianceType(val catalogCode: String, val category: DeviceCategory) {
    TV("tv", DeviceCategory.TV),
    AC("air", DeviceCategory.AC),
    FAN("fan", DeviceCategory.FAN),
    AUDIO("amp", DeviceCategory.AUDIO),
    PROJECTOR("pjt", DeviceCategory.PROJECTOR),
    SET_TOP_BOX("stb", DeviceCategory.TV),
    DVD("dvd", DeviceCategory.AUDIO),
    CAMERA("cam", DeviceCategory.CUSTOM),
    LIGHT("light", DeviceCategory.CUSTOM),
    STREAMING_BOX("box", DeviceCategory.TV),
    IPTV("ipt", DeviceCategory.TV),
    CUSTOM("custom", DeviceCategory.CUSTOM);

    companion object {
        fun fromCategory(category: DeviceCategory): ApplianceType = when (category) {
            DeviceCategory.TV -> TV
            DeviceCategory.AC -> AC
            DeviceCategory.FAN -> FAN
            DeviceCategory.AUDIO -> AUDIO
            DeviceCategory.PROJECTOR -> PROJECTOR
            DeviceCategory.CUSTOM -> CUSTOM
        }
    }
}
