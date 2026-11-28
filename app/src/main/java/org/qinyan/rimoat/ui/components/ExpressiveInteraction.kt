package org.qinyan.rimoat.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

object ExpressiveShapeTokens {
    val SegmentedOuterRadius: Dp = 20.dp
    val SegmentedInnerRadius: Dp = 4.dp
    val SegmentedPressedRadius: Dp = 14.dp
    val SegmentedGap: Dp = 2.dp
}

/** Computes MD3 Expressive segmented corner radii for an item at [index] within [count] items. */
fun segmentedItemShape(
    index: Int,
    count: Int,
    outerRadius: Dp = ExpressiveShapeTokens.SegmentedOuterRadius,
    innerRadius: Dp = ExpressiveShapeTokens.SegmentedInnerRadius
): RoundedCornerShape {
    if (count <= 1) return RoundedCornerShape(outerRadius)
    val top = if (index == 0) outerRadius else innerRadius
    val bottom = if (index >= count - 1) outerRadius else innerRadius
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Morph only the outline so a pressed card keeps its layout and touch target. */
@Composable
fun rememberExpressiveShape(
    interactionSource: MutableInteractionSource,
    restingShape: CornerBasedShape = MaterialTheme.shapes.medium,
    pressedShape: CornerBasedShape = MaterialTheme.shapes.extraLarge
): RoundedCornerShape {
    val pressed by interactionSource.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "CardPressShape"
    )
    val fraction = progress.coerceIn(0f, 1f)
    return RoundedCornerShape(
        topStart = InterpolatedCorner(restingShape.topStart, pressedShape.topStart, fraction),
        topEnd = InterpolatedCorner(restingShape.topEnd, pressedShape.topEnd, fraction),
        bottomEnd = InterpolatedCorner(restingShape.bottomEnd, pressedShape.bottomEnd, fraction),
        bottomStart = InterpolatedCorner(restingShape.bottomStart, pressedShape.bottomStart, fraction)
    )
}

/** Segmented list variant that morphs smoothly toward a uniform pressed radius without losing group continuity. */
@Composable
fun rememberSegmentedExpressiveShape(
    interactionSource: MutableInteractionSource,
    index: Int,
    count: Int,
    outerRadius: Dp = ExpressiveShapeTokens.SegmentedOuterRadius,
    innerRadius: Dp = ExpressiveShapeTokens.SegmentedInnerRadius,
    pressedRadius: Dp = ExpressiveShapeTokens.SegmentedPressedRadius
): RoundedCornerShape {
    val resting = segmentedItemShape(index, count, outerRadius, innerRadius)
    val pressed = RoundedCornerShape(pressedRadius)
    return rememberExpressiveShape(interactionSource, restingShape = resting, pressedShape = pressed)
}

private data class InterpolatedCorner(
    val resting: CornerSize,
    val pressed: CornerSize,
    val fraction: Float
) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float = lerp(
        resting.toPx(shapeSize, density),
        pressed.toPx(shapeSize, density),
        fraction
    )
}
