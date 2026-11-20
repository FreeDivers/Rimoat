package org.qinyan.rimoat.data

/** The app's color appearance choice, independent of the device-wide night setting. */
enum class AppColorMode {
    SYSTEM,
    LIGHT,
    DARK;

    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromPreference(value: String?): AppColorMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
