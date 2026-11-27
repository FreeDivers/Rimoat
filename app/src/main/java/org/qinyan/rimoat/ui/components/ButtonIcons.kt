package org.qinyan.rimoat.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Eject
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FilterCenterFocus
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Nature
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import org.qinyan.rimoat.data.ButtonKey

fun resolveButtonIcon(
    iconName: String?,
    label: String,
    id: String = "",
    builtinLabel: String? = null
): ImageVector? {
    getIconVector(iconName)?.let { return it }
    if (label.length == 1 || label == "OK") return null
    val candidates = listOfNotNull(builtinLabel, label, id)
    for (raw in candidates) {
        val v = raw.trim().lowercase()
        when {
            v in setOf("电源", "开关", "power", "tv_power", "fan_power") -> return Icons.Rounded.PowerSettingsNew
            v in setOf("返回", "back", "tv_back") -> return Icons.AutoMirrored.Rounded.ArrowBack
            v in setOf("主页", "首页", "home", "tv_home") -> return Icons.Rounded.Home
            v in setOf("菜单", "菜单键", "menu", "tv_menu") -> return Icons.Rounded.Menu
            v in setOf("静音", "mute", "tv_mute") -> return Icons.AutoMirrored.Rounded.VolumeOff
            v in setOf("信源", "输入", "信号源", "source", "input", "tv_source") -> return Icons.AutoMirrored.Rounded.Input
            v.contains("音量+") || v.contains("vol+") || v == "vol_up" || v == "volume_up" -> return Icons.AutoMirrored.Rounded.VolumeUp
            v.contains("音量-") || v.contains("vol-") || v == "vol_down" || v == "volume_down" -> return Icons.AutoMirrored.Rounded.VolumeDown
            v.contains("频道+") || v == "ch_up" || v == "channel_up" -> return Icons.Rounded.ArrowDropUp
            v.contains("频道-") || v == "ch_down" || v == "channel_down" -> return Icons.Rounded.ArrowDropDown
            v in setOf("设置", "settings") -> return Icons.Rounded.Settings
            v in setOf("退出", "exit") -> return Icons.AutoMirrored.Rounded.ExitToApp
            v in setOf("信息", "info") -> return Icons.Rounded.Info
            v in setOf("播放", "play") -> return Icons.Rounded.PlayArrow
            v in setOf("暂停", "pause") -> return Icons.Rounded.Pause
            v in setOf("播放/暂停", "play_pause") -> return Icons.Rounded.PlayArrow
            v in setOf("停止", "stop") -> return Icons.Rounded.Stop
            v in setOf("快进", "fast_forward") -> return Icons.Rounded.FastForward
            v in setOf("快退", "rewind") -> return Icons.Rounded.FastRewind
            v in setOf("上一曲", "上一首", "previous", "prev", "previous_track") -> return Icons.Rounded.SkipPrevious
            v in setOf("下一曲", "下一首", "next", "next_track") -> return Icons.Rounded.SkipNext
            v in setOf("定时", "timer") -> return Icons.Rounded.Timer
            v in setOf("风速", "风量", "speed") -> return Icons.Rounded.Speed
            v in setOf("摇头", "摆风", "扫风", "oscillation", "swing") -> return Icons.Rounded.SyncAlt
            v in setOf("自然风", "nature") -> return Icons.Rounded.Nature
            v in setOf("灯光", "照明", "light") -> return Icons.Rounded.Lightbulb
            v in setOf("睡眠", "sleep") -> return Icons.Rounded.Bedtime
            v in setOf("制冷", "一键制冷", "quick_cool") -> return Icons.Rounded.AcUnit
            v in setOf("制热", "一键制热", "quick_heat") -> return Icons.Rounded.WbSunny
            v in setOf("弹出", "出仓", "eject") -> return Icons.Rounded.Eject
            v in setOf("字幕", "subtitles") -> return Icons.Rounded.Subtitles
            v in setOf("放大", "缩放+", "zoom_in") -> return Icons.Rounded.ZoomIn
            v in setOf("缩小", "缩放-", "zoom_out") -> return Icons.Rounded.ZoomOut
        }
    }
    return null
}

fun getIconVector(name: String?): ImageVector? {
    if (name.isNullOrBlank()) return null
    return when (name.lowercase()) {
        "power", "power_settings_new" -> Icons.Rounded.PowerSettingsNew
        "volume_up" -> Icons.AutoMirrored.Rounded.VolumeUp
        "volume_down" -> Icons.AutoMirrored.Rounded.VolumeDown
        "volume_off", "mute" -> Icons.AutoMirrored.Rounded.VolumeOff
        "input", "source" -> Icons.AutoMirrored.Rounded.Input
        "arrow_back", "back", "返回" -> Icons.AutoMirrored.Rounded.ArrowBack
        "home", "主页" -> Icons.Rounded.Home
        "menu", "菜单", "菜单键" -> Icons.Rounded.Menu
        "keyboard_arrow_up", "up" -> Icons.Rounded.KeyboardArrowUp
        "keyboard_arrow_down", "down" -> Icons.Rounded.KeyboardArrowDown
        "keyboard_arrow_left", "left" -> Icons.Rounded.KeyboardArrowLeft
        "keyboard_arrow_right", "right" -> Icons.Rounded.KeyboardArrowRight
        "arrow_drop_up", "ch_up" -> Icons.Rounded.ArrowDropUp
        "arrow_drop_down", "ch_down" -> Icons.Rounded.ArrowDropDown
        "tune", "mode" -> Icons.Rounded.Tune
        "air", "wind" -> Icons.Rounded.Air
        "speed" -> Icons.Rounded.Speed
        "timer" -> Icons.Rounded.Timer
        "sync_alt", "oscillation" -> Icons.Rounded.SyncAlt
        "swap_calls", "swing" -> Icons.Rounded.SwapCalls
        "nature" -> Icons.Rounded.Nature
        "center_focus_strong" -> Icons.Rounded.CenterFocusStrong
        "filter_center_focus" -> Icons.Rounded.FilterCenterFocus
        "lightbulb", "light" -> Icons.Rounded.Lightbulb
        "bedtime", "sleep" -> Icons.Rounded.Bedtime
        "play_arrow", "play" -> Icons.Rounded.PlayArrow
        "pause" -> Icons.Rounded.Pause
        "stop" -> Icons.Rounded.Stop
        "fast_forward" -> Icons.Rounded.FastForward
        "fast_rewind", "rewind" -> Icons.Rounded.FastRewind
        "skip_previous" -> Icons.Rounded.SkipPrevious
        "skip_next" -> Icons.Rounded.SkipNext
        "info" -> Icons.Rounded.Info
        "settings" -> Icons.Rounded.Settings
        "ac_unit" -> Icons.Rounded.AcUnit
        "wb_sunny" -> Icons.Rounded.WbSunny
        else -> null
    }
}

/** Shared semantic icons for the app, panel editor and widget adapters. */
val ButtonKey.icon: ImageVector?
    get() = when (this) {
        ButtonKey.POWER, ButtonKey.POWER_ON, ButtonKey.POWER_OFF, ButtonKey.SECOND_POWER -> Icons.Rounded.PowerSettingsNew
        ButtonKey.VOLUME_UP -> Icons.AutoMirrored.Rounded.VolumeUp
        ButtonKey.VOLUME_DOWN -> Icons.AutoMirrored.Rounded.VolumeDown
        ButtonKey.MUTE -> Icons.AutoMirrored.Rounded.VolumeOff
        ButtonKey.CHANNEL_UP -> Icons.Rounded.ArrowDropUp
        ButtonKey.CHANNEL_DOWN -> Icons.Rounded.ArrowDropDown
        ButtonKey.UP, ButtonKey.TEMPERATURE_UP -> Icons.Rounded.KeyboardArrowUp
        ButtonKey.DOWN, ButtonKey.TEMPERATURE_DOWN -> Icons.Rounded.KeyboardArrowDown
        ButtonKey.LEFT -> Icons.Rounded.KeyboardArrowLeft
        ButtonKey.RIGHT -> Icons.Rounded.KeyboardArrowRight
        ButtonKey.BACK -> Icons.AutoMirrored.Rounded.ArrowBack
        ButtonKey.HOME -> Icons.Rounded.Home
        ButtonKey.MENU, ButtonKey.POPUP_MENU, ButtonKey.TOP_MENU -> Icons.Rounded.Menu
        ButtonKey.SOURCE -> Icons.AutoMirrored.Rounded.Input
        ButtonKey.INFO -> Icons.Rounded.Info
        ButtonKey.EXIT -> Icons.AutoMirrored.Rounded.ExitToApp
        ButtonKey.PLAY, ButtonKey.PLAY_PAUSE -> Icons.Rounded.PlayArrow
        ButtonKey.PAUSE -> Icons.Rounded.Pause
        ButtonKey.STOP -> Icons.Rounded.Stop
        ButtonKey.FAST_FORWARD -> Icons.Rounded.FastForward
        ButtonKey.REWIND -> Icons.Rounded.FastRewind
        ButtonKey.PREVIOUS -> Icons.Rounded.SkipPrevious
        ButtonKey.NEXT -> Icons.Rounded.SkipNext
        ButtonKey.FAN_SPEED, ButtonKey.AIRFLOW -> Icons.Rounded.Speed
        ButtonKey.SWING, ButtonKey.HORIZONTAL_SWING, ButtonKey.VERTICAL_SWING, ButtonKey.OSCILLATION -> Icons.Rounded.SyncAlt
        ButtonKey.TIMER, ButtonKey.BATH_TIMER -> Icons.Rounded.Timer
        ButtonKey.MODE -> Icons.Rounded.Tune
        ButtonKey.LIGHT -> Icons.Rounded.Lightbulb
        ButtonKey.FOCUS_PLUS -> Icons.Rounded.CenterFocusStrong
        ButtonKey.FOCUS_MINUS -> Icons.Rounded.FilterCenterFocus
        ButtonKey.ZOOM_IN -> Icons.Rounded.ZoomIn
        ButtonKey.ZOOM_OUT -> Icons.Rounded.ZoomOut
        ButtonKey.SLEEP -> Icons.Rounded.Bedtime
        ButtonKey.QUICK_COOL, ButtonKey.COOL_WIND -> Icons.Rounded.AcUnit
        ButtonKey.QUICK_HEAT -> Icons.Rounded.WbSunny
        ButtonKey.EJECT -> Icons.Rounded.Eject
        ButtonKey.SUBTITLES -> Icons.Rounded.Subtitles
        ButtonKey.SETTINGS -> Icons.Rounded.Settings
        else -> null
    }
