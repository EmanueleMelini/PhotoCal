package it.emanuelemelini.photocal.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/*
 * Shared motion of the app. Compose follows the system "Remove animations" setting: with it,
 * these animations jump straight to their end.
 */

/** Progress bars fill with a light bounce instead of jumping. */
@Composable
fun animatedProgress(
    target: Float,
    label: String = "progress",
    animationSpec: AnimationSpec<Float> = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
): Float {
    val value by animateFloatAsState(
        targetValue = target.coerceIn(0f, 1f),
        animationSpec = animationSpec,
        label = label,
    )
    return value
}

/** Numbers count towards their new value (kcal, glasses...). */
@Composable
fun countUp(
    target: Double,
    label: String = "count",
    animationSpec: AnimationSpec<Float> = tween(durationMillis = COUNT_MILLIS),
): Double {
    val value by animateFloatAsState(
        targetValue = target.toFloat(),
        animationSpec = animationSpec,
        label = label,
    )
    // Exact at rest: the float would show 1849.99 instead of 1850
    return if (value == target.toFloat()) target else value.toDouble()
}

/**
 * Enters with a fade and a short rise, [index] steps after the first item: lists and menus
 * appear one item after the other. Only the first time the item is shown.
 */
fun Modifier.staggeredEntrance(index: Int): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, staggerSpec(index))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * ENTRANCE_RISE.toPx()
    }
}

private fun staggerSpec(index: Int): AnimationSpec<Float> =
    tween(durationMillis = 280, delayMillis = (index.coerceAtMost(MAX_STAGGERED) * STAGGER_MILLIS))

/** Loading placeholder: a light band sweeps across the shape. */
fun Modifier.shimmer(): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer_shift",
    )
    background(base).drawWithContent {
        val width = size.width
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight.copy(alpha = 0.6f), Color.Transparent),
                start = Offset(width * shift - width / 2, 0f),
                end = Offset(width * shift + width / 2, size.height),
            )
        )
        drawContent()
    }
}

private const val COUNT_MILLIS = 600
private const val STAGGER_MILLIS = 45
private const val MAX_STAGGERED = 10
private val ENTRANCE_RISE = 16.dp
