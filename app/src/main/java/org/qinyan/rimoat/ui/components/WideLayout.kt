package org.qinyan.rimoat.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Tightens non-interactive button label padding in wide landscape decks without altering portrait layout. */
internal val LocalCompactRemoteButtons = compositionLocalOf { false }

/** Readable content width for text and list pages on wide windows. */
val ContentMaxWidth: Dp = 600.dp

/** Landscape phones, tablets and split windows: noticeably wider than tall and at least 480dp wide. */
fun isWideLayout(width: Dp, height: Dp): Boolean = width >= 480.dp && width.value > height.value * 1.2f

/** Uses window bounds so opening the IME does not switch a portrait form to two panes. */
@Composable
fun rememberWideLayout(): Boolean {
    val configuration = LocalConfiguration.current
    if (LocalInspectionMode.current) return isWideLayout(configuration.screenWidthDp.dp, configuration.screenHeightDp.dp)
    val context = LocalContext.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.getSystemService(WindowManager::class.java)?.currentWindowMetrics?.bounds
    } else null
    return with(density) {
        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            isWideLayout(
                (bounds.width() - safeInsets.getLeft(this, direction) - safeInsets.getRight(this, direction)).toDp(),
                (bounds.height() - safeInsets.getTop(this) - safeInsets.getBottom(this)).toDp()
            )
        } else {
            // Legacy configuration dimensions already exclude stable decor and do not shrink for IME.
            isWideLayout(configuration.screenWidthDp.dp, configuration.screenHeightDp.dp)
        }
    }
}

/** Each bounded pane owns its scrolling and task actions. */
@Composable
fun TwoPaneLayout(
    modifier: Modifier = Modifier,
    primaryFraction: Float = .5f,
    primaryMaxWidth: Dp = Dp.Infinity,
    contentPadding: PaddingValues? = null,
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val spacing = if (maxWidth < 600.dp) 8.dp else 16.dp
        val padding = contentPadding ?: PaddingValues(horizontal = spacing, vertical = 8.dp)
        val direction = LocalLayoutDirection.current
        val horizontalPadding = padding.calculateLeftPadding(direction) + padding.calculateRightPadding(direction)
        val available = (maxWidth - horizontalPadding - spacing).coerceAtLeast(0.dp)
        val primaryWidth = (available * primaryFraction.coerceIn(.3f, .7f)).coerceAtMost(primaryMaxWidth)
        Row(
            Modifier.fillMaxSize().padding(padding),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            Box(Modifier.width(primaryWidth).fillMaxHeight().testTag("two_pane_primary")
                .semantics { isTraversalGroup = true }) { primary() }
            Box(Modifier.weight(1f).fillMaxHeight().testTag("two_pane_secondary")
                .semantics { isTraversalGroup = true }) { secondary() }
        }
    }
}

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
