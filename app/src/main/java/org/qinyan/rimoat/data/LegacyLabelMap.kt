package org.qinyan.rimoat.data

/** Exact legacy spellings, used only at saved-data migration boundaries. */
internal object LegacyLabelMap {
    private val labels = mapOf(
        "锁定" to ButtonKey.LOCK, "儿童锁" to ButtonKey.LOCK, "锁" to ButtonKey.LOCK,
        "切换" to ButtonKey.SWITCH, "Lock" to ButtonKey.LOCK, "Switch" to ButtonKey.SWITCH,
        "电源" to ButtonKey.POWER,
        "开机" to ButtonKey.POWER_ON,
        "关机" to ButtonKey.POWER_OFF,
        "音量+" to ButtonKey.VOLUME_UP,
        "音量-" to ButtonKey.VOLUME_DOWN,
        "静音" to ButtonKey.MUTE,
        "频道+" to ButtonKey.CHANNEL_UP,
        "频道-" to ButtonKey.CHANNEL_DOWN,
        "上" to ButtonKey.UP,
        "下" to ButtonKey.DOWN,
        "左" to ButtonKey.LEFT,
        "右" to ButtonKey.RIGHT,
        "确认" to ButtonKey.OK,
        "返回" to ButtonKey.BACK,
        "主页" to ButtonKey.HOME,
        "菜单" to ButtonKey.MENU,
        "信源" to ButtonKey.SOURCE,
        "信息" to ButtonKey.INFO,
        "退出" to ButtonKey.EXIT,
        "回看" to ButtonKey.RECALL,
        "数位" to ButtonKey.DIGITS,
        "播放" to ButtonKey.PLAY,
        "暂停" to ButtonKey.PAUSE,
        "播放/暂停" to ButtonKey.PLAY_PAUSE,
        "停止" to ButtonKey.STOP,
        "快进" to ButtonKey.FAST_FORWARD,
        "快退" to ButtonKey.REWIND,
        "上一曲" to ButtonKey.PREVIOUS,
        "下一曲" to ButtonKey.NEXT,
        "录制" to ButtonKey.RECORD,
        "风速" to ButtonKey.FAN_SPEED,
        "风量" to ButtonKey.AIRFLOW,
        "扫风" to ButtonKey.SWING,
        "左右扫风" to ButtonKey.HORIZONTAL_SWING,
        "上下扫风" to ButtonKey.VERTICAL_SWING,
        "摇头" to ButtonKey.OSCILLATION,
        "风类" to ButtonKey.WIND_TYPE,
        "风向" to ButtonKey.WIND_DIRECTION,
        "定时" to ButtonKey.TIMER,
        "模式" to ButtonKey.MODE,
        "灯光" to ButtonKey.LIGHT,
        "对焦+" to ButtonKey.FOCUS_PLUS,
        "对焦-" to ButtonKey.FOCUS_MINUS,
        "放大" to ButtonKey.ZOOM_IN,
        "缩小" to ButtonKey.ZOOM_OUT,
        "温度+" to ButtonKey.TEMPERATURE_UP,
        "温度-" to ButtonKey.TEMPERATURE_DOWN,
        "记忆1" to ButtonKey.MEMORY_1,
        "记忆2" to ButtonKey.MEMORY_2,
        "记忆键" to ButtonKey.MEMORY,
        "到顶" to ButtonKey.TOP,
        "到底" to ButtonKey.BOTTOM,
        "开仓" to ButtonKey.EJECT,
        "标题" to ButtonKey.TITLE,
        "语言" to ButtonKey.LANGUAGE,
        "屏显" to ButtonKey.OSD,
        "声道" to ButtonKey.AUDIO_CHANNEL,
        "制式" to ButtonKey.STANDARD,
        "字幕" to ButtonKey.SUBTITLES,
        "双画面" to ButtonKey.PICTURE_IN_PICTURE,
        "冻结" to ButtonKey.FREEZE,
        "复位" to ButtonKey.RESET,
        "视频" to ButtonKey.VIDEO,
        "慢放" to ButtonKey.SLOW_PLAY,
        "快门" to ButtonKey.SHUTTER,
        "副快门" to ButtonKey.SUB_SHUTTER,
        "连续上" to ButtonKey.CONTINUOUS_UP,
        "连续下" to ButtonKey.CONTINUOUS_DOWN,
        "连续右" to ButtonKey.CONTINUOUS_RIGHT,
        "连续左" to ButtonKey.CONTINUOUS_LEFT,
        "超强" to ButtonKey.TURBO,
        "睡眠" to ButtonKey.SLEEP,
        "换气" to ButtonKey.VENTILATION,
        "辅热" to ButtonKey.AUXILIARY_HEAT,
        "干燥" to ButtonKey.DRY,
        "加湿" to ButtonKey.HUMIDIFY,
        "负离子" to ButtonKey.ION,
        "节能" to ButtonKey.ECO,
        "舒适" to ButtonKey.COMFORT,
        "温度显示" to ButtonKey.TEMPERATURE_DISPLAY,
        "一键制冷" to ButtonKey.QUICK_COOL,
        "一键制热" to ButtonKey.QUICK_HEAT,
        "自清洁" to ButtonKey.CLEAN,
        "自动" to ButtonKey.AUTO,
        "信号" to ButtonKey.SIGNAL,
        "亮度" to ButtonKey.BRIGHTNESS,
        "电脑" to ButtonKey.COMPUTER,
        "冷风" to ButtonKey.COOL_WIND,
        "设置" to ButtonKey.SETTINGS,
        "弹出菜单" to ButtonKey.POPUP_MENU,
        "顶菜单" to ButtonKey.TOP_MENU,
        "中温保温" to ButtonKey.KEEP_WARM,
        "预约洗浴" to ButtonKey.BATH_TIMER,
        "小数点" to ButtonKey.DECIMAL,
        "连接符" to ButtonKey.HYPHEN,
        "第二电源" to ButtonKey.SECOND_POWER,
        "收藏" to ButtonKey.FAVORITE,
        "数字" to ButtonKey.NUMBERS,
        "红色键" to ButtonKey.RED,
        "橙色键" to ButtonKey.ORANGE,
        "黄色键" to ButtonKey.YELLOW,
        "绿色键" to ButtonKey.GREEN,
        "蓝色键" to ButtonKey.BLUE,
        "青色键" to ButtonKey.CYAN,
        "紫色键" to ButtonKey.PURPLE,
        "0" to ButtonKey.DIGIT_0,
        "1" to ButtonKey.DIGIT_1,
        "2" to ButtonKey.DIGIT_2,
        "3" to ButtonKey.DIGIT_3,
        "4" to ButtonKey.DIGIT_4,
        "5" to ButtonKey.DIGIT_5,
        "6" to ButtonKey.DIGIT_6,
        "7" to ButtonKey.DIGIT_7,
        "8" to ButtonKey.DIGIT_8,
        "9" to ButtonKey.DIGIT_9,
        "+10" to ButtonKey.PLUS_TEN,
        "Power" to ButtonKey.POWER,
        "Power on" to ButtonKey.POWER_ON,
        "Power off" to ButtonKey.POWER_OFF,
        "Volume +" to ButtonKey.VOLUME_UP,
        "Volume −" to ButtonKey.VOLUME_DOWN,
        "Mute" to ButtonKey.MUTE,
        "Channel +" to ButtonKey.CHANNEL_UP,
        "Channel −" to ButtonKey.CHANNEL_DOWN,
        "Up" to ButtonKey.UP,
        "Down" to ButtonKey.DOWN,
        "Left" to ButtonKey.LEFT,
        "Right" to ButtonKey.RIGHT,
        "Confirm" to ButtonKey.OK,
        "Back" to ButtonKey.BACK,
        "Home" to ButtonKey.HOME,
        "Menu" to ButtonKey.MENU,
        "Input" to ButtonKey.SOURCE,
        "Info" to ButtonKey.INFO,
        "Exit" to ButtonKey.EXIT,
        "Recall" to ButtonKey.RECALL,
        "Digits" to ButtonKey.DIGITS,
        "Play" to ButtonKey.PLAY,
        "Pause" to ButtonKey.PAUSE,
        "Play / pause" to ButtonKey.PLAY_PAUSE,
        "Stop" to ButtonKey.STOP,
        "Fast forward" to ButtonKey.FAST_FORWARD,
        "Rewind" to ButtonKey.REWIND,
        "Previous" to ButtonKey.PREVIOUS,
        "Next" to ButtonKey.NEXT,
        "Record" to ButtonKey.RECORD,
        "Fan speed" to ButtonKey.FAN_SPEED,
        "Airflow" to ButtonKey.AIRFLOW,
        "Swing" to ButtonKey.SWING,
        "Horizontal swing" to ButtonKey.HORIZONTAL_SWING,
        "Vertical swing" to ButtonKey.VERTICAL_SWING,
        "Oscillate" to ButtonKey.OSCILLATION,
        "Wind mode" to ButtonKey.WIND_TYPE,
        "Air direction" to ButtonKey.WIND_DIRECTION,
        "Timer" to ButtonKey.TIMER,
        "Mode" to ButtonKey.MODE,
        "Light" to ButtonKey.LIGHT,
        "Focus +" to ButtonKey.FOCUS_PLUS,
        "Focus −" to ButtonKey.FOCUS_MINUS,
        "Zoom in" to ButtonKey.ZOOM_IN,
        "Zoom out" to ButtonKey.ZOOM_OUT,
        "Temp +" to ButtonKey.TEMPERATURE_UP,
        "Temp −" to ButtonKey.TEMPERATURE_DOWN,
        "Memory 1" to ButtonKey.MEMORY_1,
        "Memory 2" to ButtonKey.MEMORY_2,
        "Memory" to ButtonKey.MEMORY,
        "Top" to ButtonKey.TOP,
        "Bottom" to ButtonKey.BOTTOM,
        "Eject" to ButtonKey.EJECT,
        "Title" to ButtonKey.TITLE,
        "Language" to ButtonKey.LANGUAGE,
        "On-screen display" to ButtonKey.OSD,
        "Audio channel" to ButtonKey.AUDIO_CHANNEL,
        "TV standard" to ButtonKey.STANDARD,
        "Subtitles" to ButtonKey.SUBTITLES,
        "Picture in picture" to ButtonKey.PICTURE_IN_PICTURE,
        "Freeze" to ButtonKey.FREEZE,
        "Reset" to ButtonKey.RESET,
        "Video" to ButtonKey.VIDEO,
        "Slow play" to ButtonKey.SLOW_PLAY,
        "Shutter" to ButtonKey.SHUTTER,
        "Secondary shutter" to ButtonKey.SUB_SHUTTER,
        "Hold up" to ButtonKey.CONTINUOUS_UP,
        "Hold down" to ButtonKey.CONTINUOUS_DOWN,
        "Hold right" to ButtonKey.CONTINUOUS_RIGHT,
        "Hold left" to ButtonKey.CONTINUOUS_LEFT,
        "Turbo" to ButtonKey.TURBO,
        "Sleep" to ButtonKey.SLEEP,
        "Ventilation" to ButtonKey.VENTILATION,
        "Aux. heat" to ButtonKey.AUXILIARY_HEAT,
        "Drying" to ButtonKey.DRY,
        "Humidify" to ButtonKey.HUMIDIFY,
        "Ionizer" to ButtonKey.ION,
        "Eco" to ButtonKey.ECO,
        "Comfort" to ButtonKey.COMFORT,
        "Temperature display" to ButtonKey.TEMPERATURE_DISPLAY,
        "Quick cool" to ButtonKey.QUICK_COOL,
        "Quick heat" to ButtonKey.QUICK_HEAT,
        "Self-clean" to ButtonKey.CLEAN,
        "Auto" to ButtonKey.AUTO,
        "Signal" to ButtonKey.SIGNAL,
        "Brightness" to ButtonKey.BRIGHTNESS,
        "PC" to ButtonKey.COMPUTER,
        "Cool air" to ButtonKey.COOL_WIND,
        "Settings" to ButtonKey.SETTINGS,
        "Pop-up menu" to ButtonKey.POPUP_MENU,
        "Top menu" to ButtonKey.TOP_MENU,
        "Keep warm" to ButtonKey.KEEP_WARM,
        "Bath timer" to ButtonKey.BATH_TIMER,
        "Decimal point" to ButtonKey.DECIMAL,
        "Hyphen" to ButtonKey.HYPHEN,
        "Power 2" to ButtonKey.SECOND_POWER,
        "Favorite" to ButtonKey.FAVORITE,
        "Numbers" to ButtonKey.NUMBERS,
        "Red" to ButtonKey.RED,
        "Orange" to ButtonKey.ORANGE,
        "Yellow" to ButtonKey.YELLOW,
        "Green" to ButtonKey.GREEN,
        "Blue" to ButtonKey.BLUE,
        "Cyan" to ButtonKey.CYAN,
        "Purple" to ButtonKey.PURPLE,
        "开关" to ButtonKey.POWER,
        "电源键" to ButtonKey.POWER,
        "Volume up" to ButtonKey.VOLUME_UP,
        "Volume down" to ButtonKey.VOLUME_DOWN,
        "音量加" to ButtonKey.VOLUME_UP,
        "音量减" to ButtonKey.VOLUME_DOWN,
        "温度加" to ButtonKey.TEMPERATURE_UP,
        "温度减" to ButtonKey.TEMPERATURE_DOWN,
        "升温" to ButtonKey.TEMPERATURE_UP,
        "降温" to ButtonKey.TEMPERATURE_DOWN,
        "OK" to ButtonKey.OK,
        "OK键" to ButtonKey.OK,
        "确定" to ButtonKey.OK,
        "菜单键" to ButtonKey.MENU,
        "信源键" to ButtonKey.SOURCE,
        "输入源" to ButtonKey.SOURCE,
        "输入选择" to ButtonKey.SOURCE,
        "信息显示" to ButtonKey.INFO,
        "上翻" to ButtonKey.UP,
        "上方" to ButtonKey.UP,
        "下翻" to ButtonKey.DOWN,
        "下方" to ButtonKey.DOWN,
        "左翻" to ButtonKey.LEFT,
        "左方" to ButtonKey.LEFT,
        "右翻" to ButtonKey.RIGHT,
        "右方" to ButtonKey.RIGHT,
        "频道加" to ButtonKey.CHANNEL_UP,
        "频道减" to ButtonKey.CHANNEL_DOWN,
        "暂停/播放" to ButtonKey.PLAY_PAUSE,
        "水平风向" to ButtonKey.HORIZONTAL_SWING,
        "垂直风向" to ButtonKey.VERTICAL_SWING,
        "画面冻结" to ButtonKey.FREEZE,
        "重置" to ButtonKey.RESET,
        "收藏按钮" to ButtonKey.FAVORITE,
        "数字按钮" to ButtonKey.NUMBERS,
        "第二电源键" to ButtonKey.SECOND_POWER,
        "首页" to ButtonKey.HOME,
        "红" to ButtonKey.RED,
        "橙" to ButtonKey.ORANGE,
        "黄" to ButtonKey.YELLOW,
        "绿" to ButtonKey.GREEN,
        "蓝" to ButtonKey.BLUE,
        "青" to ButtonKey.CYAN,
        "青鍵" to ButtonKey.CYAN,
        "紫" to ButtonKey.PURPLE,
        "錄影" to ButtonKey.RECORD,
        "上一首" to ButtonKey.PREVIOUS,
        "下一首" to ButtonKey.NEXT,
        "数字键 0" to ButtonKey.DIGIT_0,
        "数字键0" to ButtonKey.DIGIT_0,
        "数字键 1" to ButtonKey.DIGIT_1,
        "数字键1" to ButtonKey.DIGIT_1,
        "数字键 2" to ButtonKey.DIGIT_2,
        "数字键2" to ButtonKey.DIGIT_2,
        "数字键 3" to ButtonKey.DIGIT_3,
        "数字键3" to ButtonKey.DIGIT_3,
        "数字键 4" to ButtonKey.DIGIT_4,
        "数字键4" to ButtonKey.DIGIT_4,
        "数字键 5" to ButtonKey.DIGIT_5,
        "数字键5" to ButtonKey.DIGIT_5,
        "数字键 6" to ButtonKey.DIGIT_6,
        "数字键6" to ButtonKey.DIGIT_6,
        "数字键 7" to ButtonKey.DIGIT_7,
        "数字键7" to ButtonKey.DIGIT_7,
        "数字键 8" to ButtonKey.DIGIT_8,
        "数字键8" to ButtonKey.DIGIT_8,
        "数字键 9" to ButtonKey.DIGIT_9,
        "数字键9" to ButtonKey.DIGIT_9,
    )

    fun key(label: String?): ButtonKey? = ButtonKey.entries.firstOrNull { it.name == label } ?: labels[label]

    fun wizardAcAction(label: String?): AcAction? = when (label) {
        "开关" -> AcAction.POWER_ON
        "温度+" -> AcAction.TEMP_UP
        "温度-" -> AcAction.TEMP_DOWN
        "模式" -> AcAction.MODE
        "风速" -> AcAction.FAN
        "扫风" -> AcAction.SWING
        else -> null
    }

    fun catalogAlias(button: RemoteButton): ButtonKey? = when (button.sourceKeyType to button.label) {
        824 to "前进" -> ButtonKey.FAST_FORWARD
        825 to "后退键" -> ButtonKey.REWIND
        828 to "上一个" -> ButtonKey.PREVIOUS
        829 to "下一个" -> ButtonKey.NEXT
        839 to "打开" -> ButtonKey.EJECT
        843 to "屏幕" -> ButtonKey.OSD
        852 to "单反主键" -> ButtonKey.SHUTTER
        853 to "单反副键" -> ButtonKey.SUB_SHUTTER
        854 to "连续+" -> ButtonKey.CONTINUOUS_UP
        855 to "连续-" -> ButtonKey.CONTINUOUS_DOWN
        else -> null
    }

    private val ids = mapOf(
        "vol_up" to ButtonKey.VOLUME_UP, "vol_down" to ButtonKey.VOLUME_DOWN,
        "ch_up" to ButtonKey.CHANNEL_UP, "ch_down" to ButtonKey.CHANNEL_DOWN,
        "enter" to ButtonKey.OK, "confirm" to ButtonKey.OK, "input" to ButtonKey.SOURCE,
        "wind" to ButtonKey.FAN_SPEED, "fan" to ButtonKey.FAN_SPEED,
        "temp_up" to ButtonKey.TEMPERATURE_UP, "temp_down" to ButtonKey.TEMPERATURE_DOWN,
        "num_0" to ButtonKey.DIGIT_0, "num_1" to ButtonKey.DIGIT_1, "num_2" to ButtonKey.DIGIT_2,
        "num_3" to ButtonKey.DIGIT_3, "num_4" to ButtonKey.DIGIT_4, "num_5" to ButtonKey.DIGIT_5,
        "num_6" to ButtonKey.DIGIT_6, "num_7" to ButtonKey.DIGIT_7, "num_8" to ButtonKey.DIGIT_8,
        "num_9" to ButtonKey.DIGIT_9
    )
    fun id(id: String): ButtonKey? {
        if (id.startsWith("direction_")) {
            val migrated = LegacyDirectionIds.migrateButtonId(id)
            return LegacyDirectionIds.keyForMigratedId(migrated) ?: key(id.removePrefix("direction_"))
        }
        val name = listOf("tv_", "ac_", "fan_", "audio_", "projector_", "nav_")
            .firstOrNull(id::startsWith)?.let(id::removePrefix) ?: id
        return ids[name] ?: ButtonKey.entries.firstOrNull { it.name.lowercase(java.util.Locale.ROOT) == name }
    }

    fun icon(name: String?): ButtonKey? = when (name) {
        "lock" -> ButtonKey.LOCK
        "switch" -> ButtonKey.SWITCH
        "power", "power_settings_new" -> ButtonKey.POWER
        "volume_up" -> ButtonKey.VOLUME_UP
        "volume_down" -> ButtonKey.VOLUME_DOWN
        "volume_off", "mute" -> ButtonKey.MUTE
        "input", "source" -> ButtonKey.SOURCE
        "arrow_back", "back" -> ButtonKey.BACK
        "home" -> ButtonKey.HOME
        "menu" -> ButtonKey.MENU
        "keyboard_arrow_up" -> ButtonKey.UP
        "keyboard_arrow_down" -> ButtonKey.DOWN
        "keyboard_arrow_left" -> ButtonKey.LEFT
        "keyboard_arrow_right" -> ButtonKey.RIGHT
        "arrow_drop_up", "ch_up" -> ButtonKey.CHANNEL_UP
        "arrow_drop_down", "ch_down" -> ButtonKey.CHANNEL_DOWN
        "tune", "mode" -> ButtonKey.MODE
        "air", "wind", "speed" -> ButtonKey.FAN_SPEED
        "timer" -> ButtonKey.TIMER
        "sync_alt", "oscillation" -> ButtonKey.OSCILLATION
        "swap_calls", "swing" -> ButtonKey.SWING
        "center_focus_strong" -> ButtonKey.FOCUS_PLUS
        "filter_center_focus" -> ButtonKey.FOCUS_MINUS
        "lightbulb", "light" -> ButtonKey.LIGHT
        "bedtime", "sleep" -> ButtonKey.SLEEP
        "play_arrow", "play" -> ButtonKey.PLAY
        "pause" -> ButtonKey.PAUSE
        "stop" -> ButtonKey.STOP
        "fast_forward" -> ButtonKey.FAST_FORWARD
        "fast_rewind", "rewind" -> ButtonKey.REWIND
        "skip_previous" -> ButtonKey.PREVIOUS
        "skip_next" -> ButtonKey.NEXT
        "info" -> ButtonKey.INFO
        "settings" -> ButtonKey.SETTINGS
        "ac_unit" -> ButtonKey.QUICK_COOL
        "wb_sunny" -> ButtonKey.QUICK_HEAT
        else -> null
    }
    fun catalogFallbackLabel(label: String): CatalogFallbackLabel? = when (label) {
        "清除" -> CatalogFallbackLabel.CLEAR
        "关" -> CatalogFallbackLabel.OFF
        "开" -> CatalogFallbackLabel.ON
        "关闭" -> CatalogFallbackLabel.DISABLED
        "取消" -> CatalogFallbackLabel.CANCEL
        "显示" -> CatalogFallbackLabel.DISPLAY
        "声音" -> CatalogFallbackLabel.SOUND
        "电视" -> CatalogFallbackLabel.TELEVISION
        "自定义" -> CatalogFallbackLabel.CUSTOM
        "定时关机" -> CatalogFallbackLabel.TIMER_OFF
        "保存" -> CatalogFallbackLabel.SAVE
        "降温" -> CatalogFallbackLabel.TEMPERATURE_DOWN
        "开启" -> CatalogFallbackLabel.ENABLED
        "音响" -> CatalogFallbackLabel.AUDIO
        "画面" -> CatalogFallbackLabel.PICTURE
        "开关" -> CatalogFallbackLabel.TOGGLE
        else -> null
    }

    fun catalogFallbackKey(label: String): ButtonKey? = when (label) {
        "扫风" -> ButtonKey.SWING
        "对焦+" -> ButtonKey.FOCUS_PLUS
        "对焦-" -> ButtonKey.FOCUS_MINUS
        "电源" -> ButtonKey.POWER
        "信源" -> ButtonKey.SOURCE
        "信息" -> ButtonKey.INFO
        "回看" -> ButtonKey.RECALL
        "静音" -> ButtonKey.MUTE
        "数位" -> ButtonKey.DIGITS
        "返回" -> ButtonKey.BACK
        "频道+" -> ButtonKey.CHANNEL_UP
        "频道-" -> ButtonKey.CHANNEL_DOWN
        "音量+" -> ButtonKey.VOLUME_UP
        "音量-" -> ButtonKey.VOLUME_DOWN
        "温度+" -> ButtonKey.TEMPERATURE_UP
        "温度-" -> ButtonKey.TEMPERATURE_DOWN
        "放大" -> ButtonKey.ZOOM_IN
        "缩小" -> ButtonKey.ZOOM_OUT
        "记忆1" -> ButtonKey.MEMORY_1
        "记忆2" -> ButtonKey.MEMORY_2
        "上" -> ButtonKey.UP
        "下" -> ButtonKey.DOWN
        "左" -> ButtonKey.LEFT
        "右" -> ButtonKey.RIGHT
        "菜单" -> ButtonKey.MENU
        "退出" -> ButtonKey.EXIT
        "快进" -> ButtonKey.FAST_FORWARD
        "快退" -> ButtonKey.REWIND
        "播放/暂停" -> ButtonKey.PLAY_PAUSE
        "停止" -> ButtonKey.STOP
        "上一曲" -> ButtonKey.PREVIOUS
        "下一曲" -> ButtonKey.NEXT
        "到顶" -> ButtonKey.TOP
        "到底" -> ButtonKey.BOTTOM
        "模式" -> ButtonKey.MODE
        "风量" -> ButtonKey.AIRFLOW
        "左右扫风" -> ButtonKey.HORIZONTAL_SWING
        "上下扫风" -> ButtonKey.VERTICAL_SWING
        "摇头" -> ButtonKey.OSCILLATION
        "风类" -> ButtonKey.WIND_TYPE
        "风速" -> ButtonKey.FAN_SPEED
        "开仓" -> ButtonKey.EJECT
        "标题" -> ButtonKey.TITLE
        "语言" -> ButtonKey.LANGUAGE
        "屏显" -> ButtonKey.OSD
        "声道" -> ButtonKey.AUDIO_CHANNEL
        "制式" -> ButtonKey.STANDARD
        "字幕" -> ButtonKey.SUBTITLES
        "双画面" -> ButtonKey.PICTURE_IN_PICTURE
        "冻结" -> ButtonKey.FREEZE
        "复位" -> ButtonKey.RESET
        "视频" -> ButtonKey.VIDEO
        "慢放" -> ButtonKey.SLOW_PLAY
        "快门" -> ButtonKey.SHUTTER
        "副快门" -> ButtonKey.SUB_SHUTTER
        "连续上" -> ButtonKey.CONTINUOUS_UP
        "连续下" -> ButtonKey.CONTINUOUS_DOWN
        "连续右" -> ButtonKey.CONTINUOUS_RIGHT
        "连续左" -> ButtonKey.CONTINUOUS_LEFT
        "风向" -> ButtonKey.WIND_DIRECTION
        "灯光" -> ButtonKey.LIGHT
        "超强" -> ButtonKey.TURBO
        "睡眠" -> ButtonKey.SLEEP
        "换气" -> ButtonKey.VENTILATION
        "辅热" -> ButtonKey.AUXILIARY_HEAT
        "定时" -> ButtonKey.TIMER
        "干燥" -> ButtonKey.DRY
        "加湿" -> ButtonKey.HUMIDIFY
        "负离子" -> ButtonKey.ION
        "节能" -> ButtonKey.ECO
        "舒适" -> ButtonKey.COMFORT
        "温度显示" -> ButtonKey.TEMPERATURE_DISPLAY
        "一键制冷" -> ButtonKey.QUICK_COOL
        "一键制热" -> ButtonKey.QUICK_HEAT
        "自清洁" -> ButtonKey.CLEAN
        "自动" -> ButtonKey.AUTO
        "信号" -> ButtonKey.SIGNAL
        "亮度" -> ButtonKey.BRIGHTNESS
        "电脑" -> ButtonKey.COMPUTER
        "冷风" -> ButtonKey.COOL_WIND
        "主页" -> ButtonKey.HOME
        "设置" -> ButtonKey.SETTINGS
        "弹出菜单" -> ButtonKey.POPUP_MENU
        "顶菜单" -> ButtonKey.TOP_MENU
        "中温保温" -> ButtonKey.KEEP_WARM
        "预约洗浴" -> ButtonKey.BATH_TIMER
        "确认" -> ButtonKey.OK
        "小数点" -> ButtonKey.DECIMAL
        "连接符" -> ButtonKey.HYPHEN
        "开机" -> ButtonKey.POWER_ON
        "关机" -> ButtonKey.POWER_OFF
        "播放" -> ButtonKey.PLAY
        "暂停" -> ButtonKey.PAUSE
        "录制" -> ButtonKey.RECORD
        "第二电源" -> ButtonKey.SECOND_POWER
        "收藏" -> ButtonKey.FAVORITE
        "数字" -> ButtonKey.NUMBERS
        "红色键" -> ButtonKey.RED
        "橙色键" -> ButtonKey.ORANGE
        "黄色键" -> ButtonKey.YELLOW
        "绿色键" -> ButtonKey.GREEN
        "蓝色键" -> ButtonKey.BLUE
        "青色键" -> ButtonKey.CYAN
        "紫色键" -> ButtonKey.PURPLE
        "记忆键" -> ButtonKey.MEMORY
        else -> null
    }

    fun catalogLabel(button: RemoteButton): CatalogLabelMetadata? {
        val type = button.sourceKeyType ?: return null
        val raw = button.label.trim()
        if (type in 0..9 && raw in setOf(type.toString(), "数字键 $type", "数字键$type")) {
            return CatalogLabelMetadata(key = ButtonKey.valueOf("DIGIT_$type"))
        }
        val preset = Regex("开机_(制冷|制热)_(\\d{2})度").matchEntire(raw)
        if (preset != null) return CatalogLabelMetadata(
            mode = if (preset.groupValues[1] == "制冷") AcMode.COOL else AcMode.HEAT,
            temperature = preset.groupValues[2]
        )
        val canonical = catalogAliases[type]?.get(raw) ?: catalogLabelAliases[raw]
            ?: catalogCanonicalNames[type]?.takeIf { it == raw }
            ?: return when {
                catalogFallbackKey(button.label) != null -> CatalogLabelMetadata(fallbackKey = catalogFallbackKey(button.label))
                catalogFallbackLabel(button.label) != null -> CatalogLabelMetadata(fallbackLabel = catalogFallbackLabel(button.label))
                else -> null
            }
        if (canonical == "OK") return CatalogLabelMetadata(literalOk = true)
        val key = if (canonical == "+10") ButtonKey.PLUS_TEN else key(canonical)
        return CatalogLabelMetadata(key = key, customType = type.takeIf { key == null })
    }

    private val catalogAliases: Map<Int, Map<String, String>> = mapOf(
        824 to mapOf("前进" to "快进"),
        825 to mapOf("后退键" to "快退"),
        828 to mapOf("上一个" to "上一曲"),
        829 to mapOf("下一个" to "下一曲"),
        839 to mapOf("打开" to "开仓"),
        843 to mapOf("屏幕" to "屏显"),
        852 to mapOf("单反主键" to "快门"),
        853 to mapOf("单反副键" to "副快门"),
        854 to mapOf("连续+" to "连续上"),
        855 to mapOf("连续-" to "连续下"),
        // Legacy AC type 876 means humidify for these labels, not the current timer action.
        876 to mapOf("加湿" to "加湿")
    )

    private val catalogLabelAliases = mapOf(
        "信源键" to "信源", "输入源" to "信源", "输入选择" to "信源", "信息显示" to "信息",
        "OK键" to "OK", "确定" to "确认", "菜单键" to "菜单", "电源键" to "电源",
        "上翻" to "上", "上方" to "上", "下翻" to "下", "下方" to "下",
        "左翻" to "左", "左方" to "左", "右翻" to "右", "右方" to "右",
        "温度加" to "温度+", "温度减" to "温度-", "音量加" to "音量+", "音量减" to "音量-",
        "频道加" to "频道+", "频道减" to "频道-", "暂停/播放" to "播放/暂停",
        "水平风向" to "左右扫风", "垂直风向" to "上下扫风", "画面冻结" to "冻结",
        "重置" to "复位", "收藏按钮" to "收藏", "数字按钮" to "数字", "第二电源键" to "第二电源",
        "首页" to "主页",
        "红" to "红色键", "橙" to "橙色键", "黄" to "黄色键", "绿" to "绿色键",
        "蓝" to "蓝色键", "青" to "青色键", "青鍵" to "青色键", "紫" to "紫色键",
        "錄影" to "录制"
    )

    // Canonical catalog key names; names are matched before numeric IDs.
    private val catalogCanonicalNames = mapOf(
        0 to "0",
        1 to "1",
        2 to "2",
        3 to "3",
        4 to "4",
        5 to "5",
        6 to "6",
        7 to "7",
        8 to "8",
        9 to "9",
        800 to "电源",
        801 to "信源",
        802 to "信息",
        803 to "回看",
        804 to "静音",
        805 to "数位",
        806 to "返回",
        807 to "频道+",
        808 to "频道-",
        809 to "音量+",
        810 to "音量-",
        811 to "温度+",
        812 to "温度-",
        813 to "放大",
        814 to "缩小",
        815 to "记忆1",
        816 to "记忆2",
        817 to "OK",
        818 to "上",
        819 to "下",
        820 to "左",
        821 to "右",
        822 to "菜单",
        823 to "退出",
        824 to "快进",
        825 to "快退",
        826 to "播放/暂停",
        827 to "停止",
        828 to "上一曲",
        829 to "下一曲",
        830 to "到顶",
        831 to "到底",
        832 to "模式",
        833 to "风量",
        834 to "左右扫风",
        835 to "上下扫风",
        836 to "摇头",
        837 to "风类",
        838 to "风速",
        839 to "开仓",
        840 to "标题",
        841 to "+10",
        842 to "语言",
        843 to "屏显",
        844 to "声道",
        845 to "制式",
        846 to "字幕",
        847 to "双画面",
        848 to "冻结",
        849 to "复位",
        850 to "视频",
        851 to "慢放",
        852 to "快门",
        853 to "副快门",
        854 to "连续上",
        855 to "连续下",
        856 to "连续右",
        857 to "连续左",
        870 to "风向",
        871 to "灯光",
        872 to "超强",
        873 to "睡眠",
        874 to "换气",
        875 to "辅热",
        876 to "定时",
        877 to "干燥",
        878 to "负离子",
        879 to "节能",
        880 to "舒适",
        881 to "温度显示",
        882 to "一键制冷",
        883 to "一键制热",
        884 to "自清洁",
        900 to "自动",
        901 to "信号",
        902 to "亮度",
        903 to "电脑",
        1000 to "冷风",
        1010 to "主页",
        1011 to "设置",
        1012 to "弹出菜单",
        1013 to "顶菜单",
        1020 to "中温保温",
        1021 to "预约洗浴",
        1022 to "确认",
        1023 to "小数点",
        1024 to "连接符",
        1025 to "开机",
        1026 to "关机",
        1027 to "播放",
        1028 to "暂停",
        1029 to "录制",
        1800 to "第二电源",
        2001 to "收藏",
        2002 to "数字",
        2003 to "自定义",
        -100 to "自定义圆形",
        -99 to "自定义椭圆",
        -98 to "自定义方形",
        -97 to "红色键",
        -96 to "橙色键",
        -95 to "黄色键",
        -94 to "绿色键",
        -93 to "蓝色键",
        -92 to "青色键",
        -91 to "紫色键",
        -90 to "记忆键"
    )
}
