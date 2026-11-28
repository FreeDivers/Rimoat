package org.qinyan.rimoat.ui.viewmodel

import org.qinyan.rimoat.data.*
import org.qinyan.rimoat.data.ThemePreferences
import kotlinx.coroutines.flow.StateFlow

internal class AppearanceSettingsViewModel(private val themePreferences: ThemePreferences) {
    val useDynamicColor: StateFlow<Boolean> = themePreferences.useDynamicColor
    val colorMode: StateFlow<AppColorMode> = themePreferences.colorMode
    val pureBlackBackground: StateFlow<Boolean> = themePreferences.pureBlackBackground
    val isDynamicColorSupported: Boolean = themePreferences.isDynamicColorSupported
    val rotationDirection: StateFlow<ScreenRotationDirection> = themePreferences.rotationDirection
    val autoRotateScreen: StateFlow<Boolean> = themePreferences.autoRotateScreen
    val rotationCondition: StateFlow<ScreenRotationCondition> = themePreferences.rotationCondition
    val rotationScope: StateFlow<ScreenRotationScope> = themePreferences.rotationScope
    val suppressRotationPrompt: StateFlow<Boolean> = themePreferences.suppressRotationPrompt

    fun setUseDynamicColor(useDynamic: Boolean) {
        themePreferences.setUseDynamicColor(useDynamic)
    }

    fun setColorMode(mode: AppColorMode) {
        themePreferences.setColorMode(mode)
    }

    fun setPureBlackBackground(enabled: Boolean) {
        themePreferences.setPureBlackBackground(enabled)
    }

    fun setRotationDirection(direction: ScreenRotationDirection) {
        themePreferences.setRotationDirection(direction)
    }

    fun setAutoRotateScreen(enabled: Boolean) {
        themePreferences.setAutoRotateScreen(enabled)
    }

    fun setRotationCondition(condition: ScreenRotationCondition) {
        themePreferences.setRotationCondition(condition)
    }

    fun setRotationScope(scope: ScreenRotationScope) {
        themePreferences.setRotationScope(scope)
    }

    fun setSuppressRotationPrompt(suppressed: Boolean) {
        themePreferences.setSuppressRotationPrompt(suppressed)
    }
}
