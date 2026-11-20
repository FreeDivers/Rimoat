package org.qinyan.rimoat.i18n

import android.content.Context
import org.qinyan.rimoat.R
import org.qinyan.rimoat.data.RemoteButton

/**
 * Catalog key types are not universally semantic IDs: legacy AC IDs shifted and negative
 * shape/color IDs carry arbitrary names. Only recognized labels are normalized here.
 */
fun localizedCatalogKeyLabel(button: RemoteButton, context: Context? = null): String? {
    val type = button.sourceKeyType ?: return null
    val raw = button.label.trim()
    if (type in 0..9 && raw in setOf(type.toString(), "数字键 $type", "数字键$type")) return type.toString()
    val preset = Regex("开机_(制冷|制热)_(\\d{2})度").matchEntire(raw)
    if (preset != null) {
        val mode = localizedLabel(preset.groupValues[1], context)
        return context?.getString(R.string.catalog_power_temperature, mode, preset.groupValues[2])
            ?: AppStrings.get(R.string.catalog_power_temperature, "开机 %1\$s %2\$s°C", mode, preset.groupValues[2])
    }
    val byType = catalogAliases[type]?.get(raw)
    if (byType != null) return localizedLabel(byType, context)
    val alias = catalogLabelAliases[raw]
    if (alias != null) return localizedLabel(alias, context)
    val canonical = catalogCanonicalNames[type]
    if (raw == canonical) return localizedLabel(canonical, context)
    return null
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
