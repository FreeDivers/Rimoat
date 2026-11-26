package org.qinyan.rimoat.i18n

import android.content.Context
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteButton

fun localizedTimerDurationLabel(minutes: Int): String = when {
    minutes == 0 -> AppStrings.get(R.string.control_disable_timer, "关闭定时")
    minutes < 60 -> AppStrings.quantity(R.plurals.control_minutes, minutes, "%1\$d 分钟", minutes)
    minutes % 60 == 0 -> {
        val hours = minutes / 60
        AppStrings.quantity(R.plurals.control_hours, hours, "%1\$d 小时", hours)
    }
    else -> {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        val hoursText = AppStrings.quantity(R.plurals.control_hours, hours, "%1\$d 小时", hours)
        val minutesText = AppStrings.quantity(R.plurals.control_minutes, remainingMinutes, "%1\$d 分钟", remainingMinutes)
        AppStrings.get(R.string.control_hours_minutes, "%1\$s %2\$s", hoursText, minutesText)
    }
}

/** Translate known built-in labels at the display boundary. Never write translations into profiles. */
private fun builtinLabelResource(raw: String): Int? = when (raw) {
    "空调" -> R.string.control_category_ac
    "电视" -> R.string.control_category_tv
    "风扇" -> R.string.control_category_fan
    "音响" -> R.string.control_category_audio
    "投影仪" -> R.string.control_category_projector
    "扫风" -> R.string.control_swing
    "对焦+" -> R.string.control_focus_plus
    "对焦-" -> R.string.control_focus_minus
    "电源" -> R.string.control_power
    "信源" -> R.string.control_source
    "信息" -> R.string.control_info
    "回看" -> R.string.control_recall
    "静音" -> R.string.control_mute
    "数位" -> R.string.control_digits
    "返回" -> R.string.control_back
    "频道+" -> R.string.control_channel_up
    "频道-" -> R.string.control_channel_down
    "音量+" -> R.string.control_volume_up
    "音量-" -> R.string.control_volume_down
    "温度+" -> R.string.control_temperature_up
    "温度-" -> R.string.control_temperature_down
    "放大" -> R.string.control_zoom_in
    "缩小" -> R.string.control_zoom_out
    "记忆1" -> R.string.control_memory1
    "记忆2" -> R.string.control_memory2
    "上" -> R.string.control_up
    "下" -> R.string.control_down
    "左" -> R.string.control_left
    "右" -> R.string.control_right
    "菜单" -> R.string.control_menu
    "退出" -> R.string.control_exit
    "快进" -> R.string.control_fast_forward
    "快退" -> R.string.control_rewind
    "播放/暂停" -> R.string.control_play_pause
    "停止" -> R.string.control_stop
    "上一曲" -> R.string.control_previous_track
    "下一曲" -> R.string.control_next_track
    "到顶" -> R.string.control_top
    "到底" -> R.string.control_bottom
    "模式" -> R.string.control_mode
    "风量" -> R.string.control_airflow
    "左右扫风" -> R.string.control_horizontal_swing
    "上下扫风" -> R.string.control_vertical_swing
    "摇头" -> R.string.control_oscillation
    "风类" -> R.string.control_wind_type
    "风速" -> R.string.control_fan_speed
    "开仓" -> R.string.control_eject
    "标题" -> R.string.control_title
    "语言" -> R.string.control_language
    "屏显" -> R.string.control_osd
    "声道" -> R.string.control_audio_channel
    "制式" -> R.string.control_standard
    "字幕" -> R.string.control_subtitles
    "双画面" -> R.string.control_pip
    "冻结" -> R.string.control_freeze
    "复位" -> R.string.control_reset
    "视频" -> R.string.control_video
    "慢放" -> R.string.control_slow_play
    "快门" -> R.string.control_shutter
    "副快门" -> R.string.control_sub_shutter
    "连续上" -> R.string.control_continuous_up
    "连续下" -> R.string.control_continuous_down
    "连续右" -> R.string.control_continuous_right
    "连续左" -> R.string.control_continuous_left
    "风向" -> R.string.control_wind_direction
    "灯光" -> R.string.control_light
    "超强" -> R.string.control_turbo
    "睡眠" -> R.string.control_sleep
    "换气" -> R.string.control_ventilation
    "辅热" -> R.string.control_aux_heat
    "定时" -> R.string.control_timer
    "干燥" -> R.string.control_drying
    "加湿" -> R.string.control_humidify
    "负离子" -> R.string.control_ion
    "节能" -> R.string.control_eco
    "舒适" -> R.string.control_comfort
    "温度显示" -> R.string.control_temp_display
    "一键制冷" -> R.string.control_quick_cool
    "一键制热" -> R.string.control_quick_heat
    "自清洁" -> R.string.control_clean
    "自动" -> R.string.control_auto
    "信号" -> R.string.control_signal
    "亮度" -> R.string.control_brightness
    "电脑" -> R.string.control_computer
    "冷风" -> R.string.control_cool_wind
    "主页" -> R.string.control_home
    "设置" -> R.string.control_settings
    "弹出菜单" -> R.string.control_popup_menu
    "顶菜单" -> R.string.control_top_menu
    "中温保温" -> R.string.control_keep_warm
    "预约洗浴" -> R.string.control_bath_timer
    "确认" -> R.string.control_confirm
    "小数点" -> R.string.control_decimal
    "连接符" -> R.string.control_hyphen
    "开机" -> R.string.control_power_on
    "关机" -> R.string.control_power_off
    "播放" -> R.string.control_play
    "暂停" -> R.string.control_pause
    "录制" -> R.string.control_record
    "第二电源" -> R.string.control_second_power
    "收藏" -> R.string.control_favorite
    "数字" -> R.string.control_numbers
    "自定义" -> R.string.control_custom
    "自定义圆形" -> R.string.control_custom_circle
    "自定义椭圆" -> R.string.control_custom_oval
    "自定义方形" -> R.string.control_custom_square
    "红色键" -> R.string.control_red
    "橙色键" -> R.string.control_orange
    "黄色键" -> R.string.control_yellow
    "绿色键" -> R.string.control_green
    "蓝色键" -> R.string.control_blue
    "青色键" -> R.string.control_cyan
    "紫色键" -> R.string.control_purple
    "记忆键" -> R.string.control_memory
    "制冷" -> R.string.control_cool
    "制热" -> R.string.control_heat
    "除湿" -> R.string.control_dry
    "送风" -> R.string.control_fan
    "低速" -> R.string.control_low
    "中速" -> R.string.control_medium
    "高速" -> R.string.control_high
    "向上" -> R.string.control_upward
    "居中" -> R.string.control_center
    "向下" -> R.string.control_downward
    "室外温度" -> R.string.control_outdoor_temp
    "室内温度" -> R.string.control_indoor_temp
    "设定温度" -> R.string.control_target_temp
    "关闭显示" -> R.string.control_display_off
    "升温" -> R.string.control_temp_up
    "降温" -> R.string.control_temp_down
    "开" -> R.string.control_on
    "关" -> R.string.control_off
    "开启" -> R.string.control_enabled
    "关闭" -> R.string.control_disabled
    "开关" -> R.string.control_toggle
    "自动风向" -> R.string.control_auto_direction
    "向上送风" -> R.string.control_air_up
    "水平送风" -> R.string.control_air_horizontal
    "向下送风" -> R.string.control_air_down
    "声音" -> R.string.control_sound_group
    "频道与节目" -> R.string.control_channel_group
    "画面" -> R.string.control_picture_group
    "环境与定时" -> R.string.control_climate_group
    "导航与设置" -> R.string.control_navigation_group
    "其他" -> R.string.control_other_group
    "显示" -> R.string.control_display_group
    "运行模式" -> R.string.control_mode_group
    "空气与温控" -> R.string.control_air_group
    "常规功能" -> R.string.control_regular_page
    "更多功能" -> R.string.control_more_page
    "定时时长" -> R.string.control_timer_duration
    "定时关机" -> R.string.control_timer_off
    "定时开机" -> R.string.control_timer_on
    "0 分钟" -> R.string.control_zero_minutes
    "设定时长" -> R.string.control_set_duration
    "缩短定时" -> R.string.control_shorten_timer
    "延长定时" -> R.string.control_extend_timer
    "关闭定时" -> R.string.control_disable_timer
    "设置定时" -> R.string.control_set_timer
    "模拟设定" -> R.string.control_simulation_setting
    "学习电源按键" -> R.string.control_learn_power
    "设定开启" -> R.string.control_set_on
    "设定关闭" -> R.string.control_set_off
    "关闭提示" -> R.string.control_dismiss_message
    "清除搜索" -> R.string.control_clear_search
    "清除" -> R.string.control_clear
    "音频已连接" -> R.string.control_audio_connected
    "可用" -> R.string.control_available
    "等待系统授权" -> R.string.control_waiting_system_permission
    "授权未通过" -> R.string.control_permission_denied
    "需要 USB 授权" -> R.string.control_usb_permission_required
    "此设备无内置红外" -> R.string.control_no_builtin_ir
    "设备已断开" -> R.string.control_device_disconnected
    "未连接" -> R.string.control_not_connected
    "正在读取配置" -> R.string.control_loading_config
    "需要配置模块" -> R.string.control_module_config_required
    "设备未就绪" -> R.string.control_device_not_ready
    "遥控精灵 USB 配件" -> R.string.control_tiqiaa_accessory
    "小蛮腰 USB 配件" -> R.string.control_elksmart_accessory
    "USB 串口红外模块" -> R.string.control_serial_accessory
    "3.5 mm 音频发射头" -> R.string.control_audio_accessory
    "手机内置红外" -> R.string.control_builtin_accessory
    "串口模块配置" -> R.string.control_serial_config
    "取消" -> R.string.control_cancel
    "未连接 USB 串口" -> R.string.control_no_serial_usb
    "选择 USB 串口" -> R.string.control_select_serial
    "保留已绑定的 USB 串口" -> R.string.control_keep_serial
    "连接已断开，请重新选择" -> R.string.control_select_again
    "授权所选 USB 设备" -> R.string.control_authorize_usb
    "串口序号" -> R.string.control_port_index
    "从 0 开始" -> R.string.control_zero_based
    "波特率" -> R.string.control_baud_rate
    "模块地址" -> R.string.control_module_address
    "十六进制" -> R.string.control_hexadecimal
    "配置保存失败" -> R.string.control_config_save_failed
    "保存中…" -> R.string.control_saving
    "保存" -> R.string.control_save
    "请输入有效的串口序号" -> R.string.control_invalid_port
    "请输入有效的波特率" -> R.string.control_invalid_baud
    "请输入有效的十六进制地址" -> R.string.control_invalid_address
    "红外发射器通道设置" -> R.string.control_channel_settings
    "模拟模式" -> R.string.control_simulation_mode
    "遥控精灵 USB" -> R.string.control_tiqiaa
    "小蛮腰 USB" -> R.string.control_elksmart
    "串口红外" -> R.string.control_serial_ir
    "USB 外设" -> R.string.control_usb
    "音频口红外" -> R.string.control_audio_ir
    "内置红外" -> R.string.control_builtin_ir
    "无红外硬件" -> R.string.control_no_ir
    "未连接硬件" -> R.string.control_no_hardware
    "模拟发送" -> R.string.control_simulated_send
    "信号已发送" -> R.string.control_signal_sent
    "未知错误" -> R.string.control_unknown_error
    "红外发射通道" -> R.string.control_ir_channel
    "完成" -> R.string.control_done
    "自动选择" -> R.string.control_auto_select
    "可用设备" -> R.string.control_available_devices
    "待处理设备" -> R.string.control_pending_devices
    "已展开" -> R.string.control_expanded
    "已收起" -> R.string.control_collapsed
    "演示模式" -> R.string.control_demo_mode
    "当前通道未就绪" -> R.string.control_current_not_ready
    "当前发射" -> R.string.control_current_output
    "等待授权" -> R.string.control_waiting_permission
    "重新授权" -> R.string.control_reauthorize
    "授权设备" -> R.string.control_authorize_device
    "配置模块" -> R.string.control_configure_module
    "已选通道，不可用" -> R.string.control_selected_unavailable
    "不可用" -> R.string.control_unavailable
    "无可用设备" -> R.string.control_no_devices
    "串口红外模块" -> R.string.control_serial_module
    else -> null
}

fun localizedLabel(raw: String, context: Context? = null): String {
    val resource = builtinLabelResource(raw) ?: return raw
    return context?.getString(resource) ?: AppStrings.get(resource, raw)
}

/** A user-created or learned button keeps its chosen label, even if it resembles a built-in one. */
fun localizedButtonLabel(button: RemoteButton, context: Context? = null): String = when {
    button.builtinLabel != null -> localizedLabel(button.builtinLabel, context)
    button.acAction != null || button.id.startsWith("ac_function_") -> localizedLabel(button.label, context)
    button.sourceKeyType != null -> localizedCatalogKeyLabel(button, context) ?: localizedLabel(button.label, context)
    else -> button.label
}

/** A learned snapshot remains distinct from the protocol's adjustable state controls. */
fun localizedAcRawButtonLabel(button: RemoteButton, context: Context? = null): String {
    val label = localizedButtonLabel(button, context)
    if (!button.isLearned) return label
    return context?.getString(R.string.control_learned_button_name, label)
        ?: AppStrings.get(R.string.control_learned_button_name, "已学习：%1\$s", label)
}
