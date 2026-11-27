package org.qinyan.rimoat.data

import kotlinx.serialization.Serializable

/** Catalog controls whose display identity is more specific than their physical button key. */
@Serializable
enum class CatalogFallbackLabel {
    CLEAR,
    OFF,
    ON,
    DISABLED,
    CANCEL,
    DISPLAY,
    SOUND,
    TELEVISION,
    CUSTOM,
    TIMER_OFF,
    SAVE,
    TEMPERATURE_DOWN,
    ENABLED,
    AUDIO,
    PICTURE,
    TOGGLE
}
