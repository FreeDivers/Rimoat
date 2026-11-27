package org.qinyan.rimoat.ui.components

import org.qinyan.rimoat.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.qinyan.rimoat.data.*
import kotlinx.coroutines.launch

/** The indicator and gestures use one PagerState; each page owns its vertical scroll state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlPages(
    hasMore: Boolean,
    labels: List<String> = listOf(stringResource(R.string.control_regular_page), stringResource(R.string.control_more_page)),
    selectedPage: Int = 0,
    onPageSelected: (Int) -> Unit = {},
    content: @Composable (more: Boolean) -> Unit
) {
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

internal data class TvStandardKeys(
    val power: RemoteButton? = null,
    val source: RemoteButton? = null,
    val mute: RemoteButton? = null,
    val up: RemoteButton? = null,
    val down: RemoteButton? = null,
    val left: RemoteButton? = null,
    val right: RemoteButton? = null,
    val ok: RemoteButton? = null,
    val back: RemoteButton? = null,
    val home: RemoteButton? = null,
    val menu: RemoteButton? = null,
    val volUp: RemoteButton? = null,
    val volDown: RemoteButton? = null,
    val chUp: RemoteButton? = null,
    val chDown: RemoteButton? = null
) {
    val dpad: Map<DpadDirection, RemoteButton>
        get() = buildMap {
            up?.let { put(DpadDirection.UP, it) }
            down?.let { put(DpadDirection.DOWN, it) }
            left?.let { put(DpadDirection.LEFT, it) }
            right?.let { put(DpadDirection.RIGHT, it) }
            ok?.let { put(DpadDirection.OK, it) }
        }
    val dpadButtons: List<RemoteButton> get() = listOfNotNull(up, down, left, right, ok)
    val navigation: List<RemoteButton> get() = listOfNotNull(back, home, menu)
    val rockers: List<RemoteButton> get() = listOfNotNull(volUp, volDown, chUp, chDown)
    val all: List<RemoteButton>
        get() = listOfNotNull(
            power, source, mute, up, down, left, right, ok, back, home, menu, volUp, volDown, chUp, chDown
        )

    fun dpadButton(direction: DpadDirection): RemoteButton? = when (direction) {
        DpadDirection.UP -> up
        DpadDirection.DOWN -> down
        DpadDirection.LEFT -> left
        DpadDirection.RIGHT -> right
        DpadDirection.OK -> ok
    }
}

/** Same classification supplies both page availability and the controls drawn on the first page. */
internal fun tvStandardButtons(profile: RemoteProfile): TvStandardKeys {
    fun find(key: ButtonKey, legacyMatch: (RemoteButton) -> Boolean) =
        profile.buttons.firstOrNull { it.key == key }
            ?: profile.buttons.firstOrNull { it.key == null && legacyMatch(it) }
    return TvStandardKeys(
        power = profile.buttons.firstOrNull { it.isPowerKey() },
        source = find(ButtonKey.SOURCE) { "source" in it.id },
        mute = find(ButtonKey.MUTE) { "mute" in it.id },
        up = find(ButtonKey.UP) { it.id == "tv_up" || it.id == "direction_up" },
        down = find(ButtonKey.DOWN) { it.id == "tv_down" || it.id == "direction_down" },
        left = find(ButtonKey.LEFT) { it.id == "tv_left" || it.id == "direction_left" },
        right = find(ButtonKey.RIGHT) { it.id == "tv_right" || it.id == "direction_right" },
        ok = find(ButtonKey.OK) { it.id == "tv_ok" || it.id == "direction_ok" },
        back = find(ButtonKey.BACK) { "back" in it.id },
        home = find(ButtonKey.HOME) { "home" in it.id },
        menu = find(ButtonKey.MENU) { "menu" in it.id },
        volUp = find(ButtonKey.VOLUME_UP) { "vol_up" in it.id },
        volDown = find(ButtonKey.VOLUME_DOWN) { "vol_down" in it.id },
        chUp = find(ButtonKey.CHANNEL_UP) { "ch_up" in it.id },
        chDown = find(ButtonKey.CHANNEL_DOWN) { "ch_down" in it.id }
    )
}

internal fun remoteExtraButtons(profile: RemoteProfile): List<RemoteButton> {
    if (profile.category == DeviceCategory.TV) {
        val used = tvStandardButtons(profile).all.map { it.id }.toSet() +
            (0..9).mapNotNull { number -> profile.buttons.firstOrNull { remoteButtonNumber(it) == number }?.id }
        return profile.buttons.filter { it.id !in used }
    }
    val power = profile.buttons.firstOrNull { it.isPowerKey() }
    if (profile.category == DeviceCategory.FAN) return profile.buttons.filter { it != power }.drop(MAIN_PANEL_KEY_COUNT)
    val directions = genericDirectionButtons(profile.buttons)
    val useDpad = directions.keys.containsAll(DpadDirection.cardinal)
    return profile.buttons.filter {
        it != power && (!useDpad || it !in directions.values) && remoteButtonNumber(it) == null
    }.drop(MAIN_PANEL_KEY_COUNT)
}
