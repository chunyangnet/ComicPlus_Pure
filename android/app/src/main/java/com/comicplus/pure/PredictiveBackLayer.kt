package com.comicplus.pure

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect

internal const val NAVIGATION_EXIT_MILLIS = 220L

@Composable
internal fun PredictiveBackLayer(
    visible: Boolean,
    enabled: Boolean,
    reduceMotion: Boolean,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    var rawProgress by remember { mutableFloatStateOf(0f) }
    var gestureInProgress by remember { mutableStateOf(false) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val currentOnBack by rememberUpdatedState(onBack)

    PredictiveBackHandler(enabled = enabled) { events ->
        gestureInProgress = true
        try {
            events.collect { event ->
                rawProgress = event.progress.coerceIn(0f, 1f)
                swipeEdge = event.swipeEdge
            }
            currentOnBack()
        } finally {
            gestureInProgress = false
            rawProgress = 0f
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (reduceMotion) 0f else rawProgress,
        animationSpec = if (gestureInProgress) snap() else tween(160),
        label = "predictive-back-progress",
    )
    val enter = if (reduceMotion) {
        EnterTransition.None
    } else {
        slideInHorizontally(tween(280)) { it / 8 } + fadeIn(tween(180))
    }
    val exit = if (reduceMotion) {
        ExitTransition.None
    } else {
        slideOutHorizontally(tween(NAVIGATION_EXIT_MILLIS.toInt())) { it / 7 } +
            fadeOut(tween(160))
    }
    var layerWidth by remember { mutableIntStateOf(1) }
    val layerShape = remember { RoundedCornerShape(22.dp) }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = enter,
        exit = exit,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .10f * (1f - progress))),
            )
            val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
            content(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { layerWidth = it.width.coerceAtLeast(1) }
                    .graphicsLayer {
                        translationX = direction * layerWidth * .14f * progress
                        scaleX = 1f - .04f * progress
                        scaleY = 1f - .04f * progress
                        alpha = 1f - .06f * progress
                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (direction > 0f) 1f else 0f,
                            pivotFractionY = .5f,
                        )
                        shadowElevation = 18.dp.toPx() * progress
                        shape = layerShape
                        clip = progress > .001f
                    },
            )
        }
    }
}
