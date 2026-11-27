package org.qinyan.rimoat.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import org.qinyan.rimoat.data.*
import java.util.Locale

private val namedIcons: Map<String, ImageVector> = mapOf(
    "power" to Icons.Rounded.PowerSettingsNew,
    "power_settings_new" to Icons.Rounded.PowerSettingsNew,
    "volume_up" to Icons.AutoMirrored.Rounded.VolumeUp,
    "volume_down" to Icons.AutoMirrored.Rounded.VolumeDown,
    "volume_off" to Icons.AutoMirrored.Rounded.VolumeOff,
    "mute" to Icons.AutoMirrored.Rounded.VolumeOff,
    "input" to Icons.AutoMirrored.Rounded.Input,
    "source" to Icons.AutoMirrored.Rounded.Input,
    "arrow_back" to Icons.AutoMirrored.Rounded.ArrowBack,
    "back" to Icons.AutoMirrored.Rounded.ArrowBack,
    "home" to Icons.Rounded.Home,
    "menu" to Icons.Rounded.Menu,
    "keyboard_arrow_up" to Icons.Rounded.KeyboardArrowUp,
    "up" to Icons.Rounded.KeyboardArrowUp,
    "keyboard_arrow_down" to Icons.Rounded.KeyboardArrowDown,
    "down" to Icons.Rounded.KeyboardArrowDown,
    "keyboard_arrow_left" to Icons.Rounded.KeyboardArrowLeft,
    "left" to Icons.Rounded.KeyboardArrowLeft,
    "keyboard_arrow_right" to Icons.Rounded.KeyboardArrowRight,
    "right" to Icons.Rounded.KeyboardArrowRight,
    "arrow_drop_up" to Icons.Rounded.ArrowDropUp,
    "ch_up" to Icons.Rounded.ArrowDropUp,
    "arrow_drop_down" to Icons.Rounded.ArrowDropDown,
    "ch_down" to Icons.Rounded.ArrowDropDown,
    "tune" to Icons.Rounded.Tune,
    "mode" to Icons.Rounded.Tune,
    "air" to Icons.Rounded.Air,
    "wind" to Icons.Rounded.Air,
    "speed" to Icons.Rounded.Speed,
    "timer" to Icons.Rounded.Timer,
    "sync_alt" to Icons.Rounded.SyncAlt,
    "oscillation" to Icons.Rounded.SyncAlt,
    "swap_calls" to Icons.Rounded.SwapCalls,
    "swing" to Icons.Rounded.SwapCalls,
    "nature" to Icons.Rounded.Nature,
    "center_focus_strong" to Icons.Rounded.CenterFocusStrong,
    "filter_center_focus" to Icons.Rounded.FilterCenterFocus,
    "lightbulb" to Icons.Rounded.Lightbulb,
    "light" to Icons.Rounded.Lightbulb,
    "bedtime" to Icons.Rounded.Bedtime,
    "sleep" to Icons.Rounded.Bedtime,
    "play_arrow" to Icons.Rounded.PlayArrow,
    "play" to Icons.Rounded.PlayArrow,
    "pause" to Icons.Rounded.Pause,
    "stop" to Icons.Rounded.Stop,
    "fast_forward" to Icons.Rounded.FastForward,
    "fast_rewind" to Icons.Rounded.FastRewind,
    "rewind" to Icons.Rounded.FastRewind,
    "skip_previous" to Icons.Rounded.SkipPrevious,
    "skip_next" to Icons.Rounded.SkipNext,
    "info" to Icons.Rounded.Info,
    "settings" to Icons.Rounded.Settings,
    "ac_unit" to Icons.Rounded.AcUnit,
    "wb_sunny" to Icons.Rounded.WbSunny,
    "lock" to Icons.Rounded.Lock,
    "eco" to Icons.Rounded.Eco,
    "osd" to Icons.Rounded.Monitor,
    "monitor" to Icons.Rounded.Monitor,
    "switch" to Icons.Rounded.SwapHoriz,
    "brightness" to Icons.Rounded.Brightness6,
    "brightness_6" to Icons.Rounded.Brightness6,
    "ion" to Icons.Rounded.Air,
    "add" to Icons.Rounded.Add,
    "remove" to Icons.Rounded.Remove,
    "fan" to Icons.Rounded.Air,
)

private val buttonIcons: Map<ButtonKey, ImageVector> = mapOf(
    ButtonKey.POWER to Icons.Rounded.PowerSettingsNew,
    ButtonKey.POWER_ON to Icons.Rounded.PowerSettingsNew,
    ButtonKey.POWER_OFF to Icons.Rounded.PowerSettingsNew,
    ButtonKey.SECOND_POWER to Icons.Rounded.PowerSettingsNew,
    ButtonKey.VOLUME_UP to Icons.AutoMirrored.Rounded.VolumeUp,
    ButtonKey.VOLUME_DOWN to Icons.AutoMirrored.Rounded.VolumeDown,
    ButtonKey.MUTE to Icons.AutoMirrored.Rounded.VolumeOff,
    ButtonKey.CHANNEL_UP to Icons.Rounded.ArrowDropUp,
    ButtonKey.CHANNEL_DOWN to Icons.Rounded.ArrowDropDown,
    ButtonKey.UP to Icons.Rounded.KeyboardArrowUp,
    ButtonKey.TEMPERATURE_UP to Icons.Rounded.KeyboardArrowUp,
    ButtonKey.DOWN to Icons.Rounded.KeyboardArrowDown,
    ButtonKey.TEMPERATURE_DOWN to Icons.Rounded.KeyboardArrowDown,
    ButtonKey.LEFT to Icons.Rounded.KeyboardArrowLeft,
    ButtonKey.RIGHT to Icons.Rounded.KeyboardArrowRight,
    ButtonKey.BACK to Icons.AutoMirrored.Rounded.ArrowBack,
    ButtonKey.HOME to Icons.Rounded.Home,
    ButtonKey.MENU to Icons.Rounded.Menu,
    ButtonKey.POPUP_MENU to Icons.Rounded.Menu,
    ButtonKey.TOP_MENU to Icons.Rounded.Menu,
    ButtonKey.SOURCE to Icons.AutoMirrored.Rounded.Input,
    ButtonKey.INFO to Icons.Rounded.Info,
    ButtonKey.EXIT to Icons.AutoMirrored.Rounded.ExitToApp,
    ButtonKey.PLAY to Icons.Rounded.PlayArrow,
    ButtonKey.PLAY_PAUSE to Icons.Rounded.PlayArrow,
    ButtonKey.PAUSE to Icons.Rounded.Pause,
    ButtonKey.STOP to Icons.Rounded.Stop,
    ButtonKey.FAST_FORWARD to Icons.Rounded.FastForward,
    ButtonKey.REWIND to Icons.Rounded.FastRewind,
    ButtonKey.PREVIOUS to Icons.Rounded.SkipPrevious,
    ButtonKey.NEXT to Icons.Rounded.SkipNext,
    ButtonKey.FAN_SPEED to Icons.Rounded.Speed,
    ButtonKey.AIRFLOW to Icons.Rounded.Speed,
    ButtonKey.SWING to Icons.Rounded.SyncAlt,
    ButtonKey.HORIZONTAL_SWING to Icons.Rounded.SyncAlt,
    ButtonKey.VERTICAL_SWING to Icons.Rounded.SyncAlt,
    ButtonKey.OSCILLATION to Icons.Rounded.SyncAlt,
    ButtonKey.TIMER to Icons.Rounded.Timer,
    ButtonKey.BATH_TIMER to Icons.Rounded.Timer,
    ButtonKey.MODE to Icons.Rounded.Tune,
    ButtonKey.LIGHT to Icons.Rounded.Lightbulb,
    ButtonKey.FOCUS_PLUS to Icons.Rounded.CenterFocusStrong,
    ButtonKey.FOCUS_MINUS to Icons.Rounded.FilterCenterFocus,
    ButtonKey.ZOOM_IN to Icons.Rounded.ZoomIn,
    ButtonKey.ZOOM_OUT to Icons.Rounded.ZoomOut,
    ButtonKey.SLEEP to Icons.Rounded.Bedtime,
    ButtonKey.QUICK_COOL to Icons.Rounded.AcUnit,
    ButtonKey.COOL_WIND to Icons.Rounded.AcUnit,
    ButtonKey.QUICK_HEAT to Icons.Rounded.WbSunny,
    ButtonKey.EJECT to Icons.Rounded.Eject,
    ButtonKey.SUBTITLES to Icons.Rounded.Subtitles,
    ButtonKey.SETTINGS to Icons.Rounded.Settings,
    ButtonKey.WIND_TYPE to Icons.Rounded.Nature,
    ButtonKey.LOCK to Icons.Rounded.Lock,
    ButtonKey.ECO to Icons.Rounded.Eco,
    ButtonKey.OSD to Icons.Rounded.Monitor,
    ButtonKey.SWITCH to Icons.Rounded.SwapHoriz,
    ButtonKey.BRIGHTNESS to Icons.Rounded.Brightness6,
    ButtonKey.ION to Icons.Rounded.Air,
)

val ButtonKey.icon: ImageVector? get() = buttonIcons[this]

fun getIconVector(name: String?): ImageVector? = name?.let { namedIcons[it.lowercase(Locale.ROOT)] }

fun resolveButtonIcon(key: ButtonKey?, iconName: String?): ImageVector? {
    if (key == ButtonKey.OK || key?.name?.startsWith("DIGIT_") == true || key == ButtonKey.PLUS_TEN) return null
    return key?.icon ?: getIconVector(iconName)
}

fun resolveButtonIcon(button: RemoteButton): ImageVector? = resolveButtonIcon(button.key, button.iconName)

/** Legacy wizard metadata is translated without interpreting its editable display label. */
@Suppress("UNUSED_PARAMETER")
fun resolveButtonIcon(iconName: String?, label: String, id: String = "", builtinLabel: String? = null): ImageVector? =
    resolveButtonIcon(builtinLabel?.let(LegacyLabelMap::key) ?: LegacyLabelMap.id(id), iconName)
