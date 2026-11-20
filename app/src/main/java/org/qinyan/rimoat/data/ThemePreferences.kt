package org.qinyan.rimoat.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages theme preferences: choosing between the app's custom built-in
 * green palette or Android's system dynamic colors (Material You).
 */
class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("rimoat_theme_prefs", Context.MODE_PRIVATE)

    private val _useDynamicColor = MutableStateFlow(prefs.getBoolean(KEY_USE_DYNAMIC_COLOR, false))
    val useDynamicColor: StateFlow<Boolean> = _useDynamicColor.asStateFlow()

    private val _colorMode = MutableStateFlow(AppColorMode.fromPreference(prefs.getString(KEY_COLOR_MODE, null)))
    val colorMode: StateFlow<AppColorMode> = _colorMode.asStateFlow()

    private val _pureBlackBackground = MutableStateFlow(prefs.getBoolean(KEY_PURE_BLACK_BACKGROUND, false))
    val pureBlackBackground: StateFlow<Boolean> = _pureBlackBackground.asStateFlow()

    private val _rotationDirection = MutableStateFlow(
        ScreenRotationDirection.fromPreference(prefs.getString(KEY_ROTATION_DIRECTION, null))
    )
    val rotationDirection: StateFlow<ScreenRotationDirection> = _rotationDirection.asStateFlow()

    private val _autoRotateScreen = MutableStateFlow(prefs.getBoolean(KEY_AUTO_ROTATE_SCREEN, false))
    val autoRotateScreen: StateFlow<Boolean> = _autoRotateScreen.asStateFlow()

    private val _rotationCondition = MutableStateFlow(
        ScreenRotationCondition.fromPreference(prefs.getString(KEY_ROTATION_CONDITION, null))
    )
    val rotationCondition: StateFlow<ScreenRotationCondition> = _rotationCondition.asStateFlow()

    private val _rotationScope = MutableStateFlow(
        ScreenRotationScope.fromPreference(prefs.getString(KEY_ROTATION_SCOPE, null))
    )
    val rotationScope: StateFlow<ScreenRotationScope> = _rotationScope.asStateFlow()

    private val _suppressRotationPrompt = MutableStateFlow(prefs.getBoolean(KEY_SUPPRESS_ROTATION_PROMPT, false))
    val suppressRotationPrompt: StateFlow<Boolean> = _suppressRotationPrompt.asStateFlow()

    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_USE_DYNAMIC_COLOR -> _useDynamicColor.value = prefs.getBoolean(KEY_USE_DYNAMIC_COLOR, false)
            KEY_COLOR_MODE -> _colorMode.value = AppColorMode.fromPreference(prefs.getString(KEY_COLOR_MODE, null))
            KEY_PURE_BLACK_BACKGROUND -> _pureBlackBackground.value = prefs.getBoolean(KEY_PURE_BLACK_BACKGROUND, false)
            KEY_ROTATION_DIRECTION -> _rotationDirection.value =
                ScreenRotationDirection.fromPreference(prefs.getString(KEY_ROTATION_DIRECTION, null))
            KEY_AUTO_ROTATE_SCREEN -> _autoRotateScreen.value = prefs.getBoolean(KEY_AUTO_ROTATE_SCREEN, false)
            KEY_ROTATION_CONDITION -> _rotationCondition.value =
                ScreenRotationCondition.fromPreference(prefs.getString(KEY_ROTATION_CONDITION, null))
            KEY_ROTATION_SCOPE -> _rotationScope.value =
                ScreenRotationScope.fromPreference(prefs.getString(KEY_ROTATION_SCOPE, null))
            KEY_SUPPRESS_ROTATION_PROMPT -> _suppressRotationPrompt.value =
                prefs.getBoolean(KEY_SUPPRESS_ROTATION_PROMPT, false)
        }
    }
    init { prefs.registerOnSharedPreferenceChangeListener(preferenceListener) }

    val isDynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun setUseDynamicColor(useDynamic: Boolean) {
        prefs.edit().putBoolean(KEY_USE_DYNAMIC_COLOR, useDynamic).apply()
        _useDynamicColor.value = useDynamic
    }

    fun setColorMode(mode: AppColorMode) {
        prefs.edit().putString(KEY_COLOR_MODE, mode.name).apply()
        _colorMode.value = mode
    }

    companion object {
        private const val KEY_USE_DYNAMIC_COLOR = "use_dynamic_color"
        private const val KEY_COLOR_MODE = "app_color_mode"
        private const val KEY_PURE_BLACK_BACKGROUND = "pure_black_background"
        private const val KEY_ROTATION_DIRECTION = "screen_rotation_direction"
        private const val KEY_AUTO_ROTATE_SCREEN = "auto_rotate_screen"
        private const val KEY_ROTATION_CONDITION = "screen_rotation_condition"
        private const val KEY_ROTATION_SCOPE = "screen_rotation_scope"
        private const val KEY_SUPPRESS_ROTATION_PROMPT = "suppress_rotation_prompt"
    }

    fun setPureBlackBackground(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PURE_BLACK_BACKGROUND, enabled).apply()
        _pureBlackBackground.value = enabled
    }

    fun setRotationDirection(direction: ScreenRotationDirection) {
        prefs.edit().putString(KEY_ROTATION_DIRECTION, direction.name).apply()
        _rotationDirection.value = direction
    }

    fun setAutoRotateScreen(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ROTATE_SCREEN, enabled).apply()
        _autoRotateScreen.value = enabled
    }

    fun setRotationCondition(condition: ScreenRotationCondition) {
        prefs.edit().putString(KEY_ROTATION_CONDITION, condition.name).apply()
        _rotationCondition.value = condition
    }

    fun setRotationScope(scope: ScreenRotationScope) {
        prefs.edit().putString(KEY_ROTATION_SCOPE, scope.name).apply()
        _rotationScope.value = scope
    }

    fun setSuppressRotationPrompt(suppressed: Boolean) {
        prefs.edit().putBoolean(KEY_SUPPRESS_ROTATION_PROMPT, suppressed).apply()
        _suppressRotationPrompt.value = suppressed
    }
}

