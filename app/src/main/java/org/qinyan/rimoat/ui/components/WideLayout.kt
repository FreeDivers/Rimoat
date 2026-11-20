package org.qinyan.rimoat.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Tightens non-interactive button label padding in wide landscape decks without altering portrait layout. */
internal val LocalCompactRemoteButtons = compositionLocalOf { false }

/** Readable content width for text and list pages on wide windows. */
val ContentMaxWidth: Dp = 600.dp

/** Landscape phones, tablets and split windows: noticeably wider than tall and at least 480dp wide. */
fun isWideLayout(width: Dp, height: Dp): Boolean = width >= 480.dp && width.value > height.value * 1.2f

/** Caps this element's width and centers it; the surrounding container keeps its full size. */
fun Modifier.contentWidth(max: Dp = ContentMaxWidth): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max).fillMaxWidth()

/** Centers [content] and caps its width so rows do not stretch across a wide window. */
@Composable
fun CenteredContent(modifier: Modifier = Modifier, maxWidth: Dp = ContentMaxWidth, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
    }
}

/** One recorded LazyColumn entry, replayed into whichever pane the key belongs to. */
internal class RecordedItem(
    val key: Any?,
    val contentType: Any?,
    val content: @Composable LazyItemScope.() -> Unit
)

/** Collects the items a [LazyListScope] builder emits so they can be split across panes. */
internal class RecordingLazyListScope : LazyListScope {
    val recorded = mutableListOf<RecordedItem>()

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        recorded += RecordedItem(key, contentType, content)
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit
    ) {
        repeat(count) { index ->
            recorded += RecordedItem(key?.invoke(index), contentType(index)) { itemContent(index) }
        }
    }
}

internal fun recordItems(build: LazyListScope.() -> Unit): List<RecordedItem> =
    RecordingLazyListScope().apply(build).recorded

internal fun LazyListScope.replay(items: List<RecordedItem>) {
    items.forEach { item(it.key, it.contentType, it.content) }
}
