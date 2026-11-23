package org.qinyan.rimoat.ui.components

import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import org.qinyan.rimoat.ui.motion.AppMotion
import kotlin.math.roundToInt

object SimpleDropdownMenuDefaults {
    val ItemMinHeight: Dp = 48.dp
    val ItemHorizontalPadding: Dp = 24.dp
    val MenuVerticalPadding: Dp = 8.dp
    val WindowVerticalMargin: Dp = 48.dp
    val TonalElevation: Dp = 3.dp
    val ShadowElevation: Dp = 6.dp
    val ItemContentPadding = PaddingValues(horizontal = ItemHorizontalPadding, vertical = 0.dp)

    val shape: Shape
        @Composable get() = MaterialTheme.shapes.small

    val containerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

    @Composable
    fun selectedItemContainerColor(): Color =
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
}

private val LocalSelectedMenuItemMeasurer = compositionLocalOf<((Int) -> Unit)?> { null }
private val LocalMenuInteractive = compositionLocalOf { true }
// Match the enter duration of the Material 3 DropdownMenu.
private const val MenuTransitionDurationMillis = 120

/**
 * SimpleMenu-styled [DropdownMenu] that aligns the currently selected item (or first item)
 * directly over the trigger anchor while clamping within the visible window margins.
 */
@Composable
fun SimpleDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    itemCount: Int = 0,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    shape: Shape = SimpleDropdownMenuDefaults.shape,
    containerColor: Color = SimpleDropdownMenuDefaults.containerColor,
    tonalElevation: Dp = SimpleDropdownMenuDefaults.TonalElevation,
    shadowElevation: Dp = SimpleDropdownMenuDefaults.ShadowElevation,
    content: @Composable ColumnScope.() -> Unit
) {
    var menuPresent by remember { mutableStateOf(expanded) }
    val dismissProgress = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            if (!menuPresent) dismissProgress.snapTo(0f)
            menuPresent = true
            dismissProgress.animateTo(0f, tween(MenuTransitionDurationMillis, easing = AppMotion.Enter))
        } else if (menuPresent) {
            dismissProgress.animateTo(1f, tween(MenuTransitionDurationMillis, easing = AppMotion.Exit))
            menuPresent = false
        }
    }
    val composeView = LocalView.current
    val density = LocalDensity.current
    var anchorBounds by remember { mutableStateOf<IntRect?>(null) }
    var measuredMenuHeightPx by remember { mutableIntStateOf(0) }
    var measuredSelectedCenterInColumnPx by remember(selectedIndex, itemCount) {
        mutableIntStateOf(-1)
    }

    Layout(
        content = {},
        modifier = Modifier.onGloballyPositioned { childCoordinates ->
            val parent = childCoordinates.parentLayoutCoordinates?.takeIf { it.isAttached }
                ?: return@onGloballyPositioned
            val pos = parent.positionInWindow()
            val rect = IntRect(IntOffset(pos.x.roundToInt(), pos.y.roundToInt()), parent.size)
            if (rect != anchorBounds) {
                anchorBounds = rect
            }
        }
    ) { _, _ -> layout(0, 0) {} }

    val itemHeightPx = with(density) { SimpleDropdownMenuDefaults.ItemMinHeight.roundToPx() }
    val verticalPaddingPx = with(density) { SimpleDropdownMenuDefaults.MenuVerticalPadding.roundToPx() }
    val verticalMarginPx = with(density) { SimpleDropdownMenuDefaults.WindowVerticalMargin.roundToPx() }
    val targetIndex = (selectedIndex ?: 0).coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    val count = itemCount.coerceAtLeast(1)

    val effectiveMenuHeightPx = if (measuredMenuHeightPx > 0) {
        measuredMenuHeightPx
    } else {
        verticalPaddingPx * 2 + count * itemHeightPx
    }
    val selectedCenterInColumnPx = if (selectedIndex != null && measuredSelectedCenterInColumnPx >= 0) {
        measuredSelectedCenterInColumnPx
    } else {
        targetIndex * itemHeightPx + itemHeightPx / 2
    }
    val maxCenterInMenuPx = (effectiveMenuHeightPx - verticalPaddingPx).coerceAtLeast(verticalPaddingPx)
    val effectiveTargetCenterInMenuPx =
        (verticalPaddingPx + selectedCenterInColumnPx - scrollState.value)
            .coerceIn(verticalPaddingPx, maxCenterInMenuPx)

    LaunchedEffect(expanded, selectedIndex, scrollState.maxValue, measuredSelectedCenterInColumnPx) {
        if (expanded && scrollState.maxValue > 0 && selectedIndex != null) {
            val viewportHeight = (effectiveMenuHeightPx - verticalPaddingPx * 2).coerceAtLeast(itemHeightPx)
            val desiredScroll = (selectedCenterInColumnPx - viewportHeight / 2).coerceIn(0, scrollState.maxValue)
            scrollState.scrollTo(desiredScroll)
        }
    }

    val resolvedOffset = remember(
        anchorBounds,
        composeView,
        density,
        offset,
        effectiveMenuHeightPx,
        effectiveTargetCenterInMenuPx,
        targetIndex
    ) {
        val anchor = anchorBounds
        if (anchor != null && anchor.height > 0) {
            val visibleFrame = Rect().also { composeView.getWindowVisibleDisplayFrame(it) }
            val windowHeightPx = (visibleFrame.bottom - visibleFrame.top).takeIf { it > 0 } ?: composeView.height
            val desiredTopPx = anchor.center.y - effectiveTargetCenterInMenuPx + with(density) { offset.y.roundToPx() }
            val clampedTopPx = if (windowHeightPx > 0) {
                val minTopPx = verticalMarginPx
                val maxTopPx = (windowHeightPx - verticalMarginPx - effectiveMenuHeightPx).coerceAtLeast(minTopPx)
                desiredTopPx.coerceIn(minTopPx, maxTopPx)
            } else {
                desiredTopPx
            }
            val offsetYPx = clampedTopPx - anchor.bottom
            DpOffset(offset.x, with(density) { offsetYPx.toDp() })
        } else {
            DpOffset(
                offset.x,
                offset.y - (SimpleDropdownMenuDefaults.MenuVerticalPadding + SimpleDropdownMenuDefaults.ItemMinHeight * (targetIndex + 1))
            )
        }
    }

    val reportSelectedCenter = remember {
        { centerInColumnPx: Int ->
            if (measuredSelectedCenterInColumnPx != centerInColumnPx) {
                measuredSelectedCenterInColumnPx = centerInColumnPx
            }
        }
    }

    // Keep the selected anchor fixed while selection updates underneath the closing menu.
    var displayedOffset by remember { mutableStateOf(resolvedOffset) }
    SideEffect { if (expanded) displayedOffset = resolvedOffset }

    if (expanded || menuPresent) DropdownMenu(
        expanded = true,
        onDismissRequest = { if (expanded) onDismissRequest() },
        modifier = modifier.onSizeChanged { size ->
            if (size.height > 0 && measuredMenuHeightPx != size.height) {
                measuredMenuHeightPx = size.height
            }
        },
        offset = if (expanded) resolvedOffset else displayedOffset,
        scrollState = scrollState,
        shape = shape,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation
    ) {
        // Animate the popup's Compose view so the official menu surface, shadow and items
        // leave together. Keeping the popup mounted also lets interrupted exits reverse.
        val popupView = LocalView.current
        val progress = dismissProgress.value
        SideEffect {
            popupView.alpha = 1f - progress
            popupView.scaleX = 1f - 0.02f * progress
            popupView.scaleY = 1f - 0.02f * progress
        }
        DisposableEffect(popupView) {
            onDispose {
                popupView.alpha = 1f
                popupView.scaleX = 1f
                popupView.scaleY = 1f
            }
        }
        CompositionLocalProvider(
            LocalSelectedMenuItemMeasurer provides reportSelectedCenter,
            LocalMenuInteractive provides expanded
        ) {
            content()
        }
    }
}

/**
 * SimpleMenu-styled [DropdownMenuItem] with a full-width selection highlight background
 * and no trailing checkmark icon.
 */
@Composable
fun SimpleDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    contentPadding: PaddingValues = SimpleDropdownMenuDefaults.ItemContentPadding,
    interactionSource: MutableInteractionSource? = null
) {
    val reportSelectedCenter = LocalSelectedMenuItemMeasurer.current
    val backgroundColor = if (selected) {
        SimpleDropdownMenuDefaults.selectedItemContainerColor()
    } else {
        Color.Transparent
    }
    DropdownMenuItem(
        text = {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                text()
            }
        },
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .onGloballyPositioned { coordinates ->
                if (selected && reportSelectedCenter != null && coordinates.isAttached) {
                    val centerInColumnPx =
                        coordinates.positionInParent().y.roundToInt() + coordinates.size.height / 2
                    reportSelectedCenter(centerInColumnPx)
                }
            }
            .semantics { this.selected = selected },
        enabled = enabled && LocalMenuInteractive.current,
        colors = MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.onSurface,
            // Blocking repeated taps during exit must not flash the disabled text style.
            disabledTextColor = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Unspecified
        ),
        contentPadding = contentPadding,
        interactionSource = interactionSource
    )
}
