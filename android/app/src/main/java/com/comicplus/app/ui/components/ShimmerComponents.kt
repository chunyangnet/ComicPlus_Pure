package com.comicplus.app.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Creates one shared shimmer brush per loading surface. Callers should create the brush once
 * and pass it to all visible placeholders instead of starting an animation for every card.
 */
@Composable
fun rememberShimmerBrush(animated: Boolean = true): Brush {
    return rememberShimmerBrush(
        animated = animated,
        colors = listOf(Color(0xFFE8EBEE), Color(0xFFF8F9FA), Color(0xFFE8EBEE)),
    )
}

@Composable
fun rememberShimmerBrush(animated: Boolean = true, colors: List<Color>): Brush {
    require(colors.size >= 2)
    val motionAllowed = rememberMotionAllowed()
    if (!animated || !motionAllowed) {
        return remember(colors) { Brush.linearGradient(listOf(colors.first(), colors.last())) }
    }
    val transition = rememberInfiniteTransition(label = "comicplus-shimmer")
    val offset = transition.animateFloat(
        initialValue = -520f,
        targetValue = 1_420f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_150, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "comicplus-shimmer-offset",
    ).value
    return Brush.linearGradient(
        colors = colors,
        start = Offset(offset - 360f, 0f),
        end = Offset(offset, 520f),
    )
}

@Composable
fun ShimmerBlock(
    brush: Brush,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    Box(modifier.background(brush, shape))
}
