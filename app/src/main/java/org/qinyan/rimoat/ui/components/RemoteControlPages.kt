package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.i18n.localizedLabel
import org.qinyan.rimoat.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import org.qinyan.rimoat.data.*
import kotlinx.coroutines.launch

/** The indicator and gestures use one PagerState; each page owns its vertical scroll state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlPages(hasMore: Boolean, labels: List<String> = listOf(localizedLabel("常规功能"), localizedLabel("更多功能")),
    selectedPage: Int = 0, onPageSelected: (Int) -> Unit = {}, content: @Composable (more: Boolean) -> Unit) {
    if (!hasMore) { content(false); return }
    val motion = MaterialTheme.motionScheme
    val pager = rememberPagerState(initialPage = selectedPage, pageCount = { 2 })
    LaunchedEffect(selectedPage) { if (pager.currentPage != selectedPage) pager.scrollToPage(selectedPage) }
    LaunchedEffect(pager.currentPage) { onPageSelected(pager.currentPage) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        ExpressivePageSwitcher(
            labels = labels,
            selectedIndex = pager.currentPage,
            onSelect = { index ->
                scope.launch { pager.animateScrollToPage(index, animationSpec = motion.defaultSpatialSpec()) }
            }
        )
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), key = { it }) { page ->
            content(page == 1)
        }
    }
}

/** Same classification supplies both page availability and the controls drawn on the first page. */
internal fun tvStandardButtons(profile: RemoteProfile): List<RemoteButton?> {
    fun find(predicate: (RemoteButton) -> Boolean) = profile.buttons.firstOrNull(predicate)
    return listOf(
        find { it.iconName == "power" || it.id.contains("power") || it.label.contains("电源") || it.label == "开关" },
        find { it.id.contains("source") || it.label.contains("信源") },
        find { it.id.contains("mute") || it.label.contains("静音") },
        find { it.id == "tv_up" || it.label == "上" }, find { it.id == "tv_down" || it.label == "下" },
        find { it.id == "tv_left" || it.label == "左" }, find { it.id == "tv_right" || it.label == "右" },
        find { it.id == "tv_ok" || it.label == "OK" },
        find { it.id.contains("back") || it.label.contains("返回") },
        find { it.id.contains("home") || it.label.contains("主页") },
        find { it.id.contains("menu") || it.label.contains("菜单") },
        find { it.id.contains("vol_up") || it.label.contains("音量+") },
        find { it.id.contains("vol_down") || it.label.contains("音量-") },
        find { it.id.contains("ch_up") || it.label.contains("频道+") },
        find { it.id.contains("ch_down") || it.label.contains("频道-") }
    )
}

internal fun remoteExtraButtons(profile: RemoteProfile): List<RemoteButton> {
    if (profile.category == DeviceCategory.TV) {
        val used = tvStandardButtons(profile).filterNotNull().map { it.id }.toSet() +
            (0..9).mapNotNull { number -> profile.buttons.firstOrNull { remoteButtonNumber(it) == number }?.id }
        return profile.buttons.filter { it.id !in used }
    }
    val power = profile.buttons.firstOrNull { it.iconName == "power" || it.id.contains("power") || it.label.contains("电源") || it.label == "开关" }
    if (profile.category == DeviceCategory.FAN) return profile.buttons.filter { it != power }.drop(6)
    val directions = org.qinyan.rimoat.ui.screens.genericDirectionButtons(profile.buttons)
    val useDpad = directions.keys.containsAll(listOf("上", "下", "左", "右"))
    return profile.buttons.filter { it != power && (!useDpad || it !in directions.values) && remoteButtonNumber(it) == null }.drop(6)
}
