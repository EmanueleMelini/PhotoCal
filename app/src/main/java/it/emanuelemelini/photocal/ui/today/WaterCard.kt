package it.emanuelemelini.photocal.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.nutrition.WaterCalculator
import it.emanuelemelini.photocal.ui.components.animatedProgress
import it.emanuelemelini.photocal.ui.components.countUp
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.formatLiters
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Glasses of water of the day, with − / + and, once set in the profile, the whole bottle;
 * tapping the card opens the goals.
 * The bottle tilts and pours drops into the bar; reaching the goal with a tap gets a small
 * celebration. The count and the bar move to their new value instead of jumping.
 */
@Composable
fun WaterCard(
    state: TodayUiState,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onAddBottle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val goal = WaterCalculator.goalGlasses(state.waterGoalMl, state.glassMl)
    val goalMl = goal * state.glassMl
    val haptics = LocalHapticFeedback.current

    // Pour: bottle tilt, then drops from the bottle to the bar
    var pours by remember { mutableIntStateOf(0) }
    // While pouring, bar and count wait for the first drops to land
    var pouring by remember { mutableStateOf(false) }
    val drunk = countUp(
        WaterCalculator.glasses(state.waterMl, state.glassMl),
        label = "water_glasses",
        animationSpec = if (pouring) tween(POUR_MILLIS, delayMillis = POUR_LAND_MILLIS) else tween(600),
    )
    val progress = animatedProgress(
        state.waterMl.toFloat() / goalMl,
        label = "water_progress",
        animationSpec = if (pouring) tween(POUR_MILLIS, delayMillis = POUR_LAND_MILLIS, easing = FastOutSlowInEasing)
        else spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
    )
    val tilt = remember { Animatable(0f) }
    val pour = remember { Animatable(1f) }
    // Goal reached with a tap of this card (not by changing day or from the widget)
    var celebrations by remember { mutableIntStateOf(0) }
    val burst = remember { Animatable(1f) }
    var goalReachedShown by remember { mutableStateOf(false) }
    var addTapped by remember { mutableStateOf(false) }
    var previous by remember { mutableStateOf(state.date to state.waterMl) }

    // Where the drops start and land, in the card's coordinates
    var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var bottleBounds by remember { mutableStateOf(Rect.Zero) }
    var barBounds by remember { mutableStateOf(Rect.Zero) }
    var iconBounds by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(pours) {
        if (pours == 0) return@LaunchedEffect
        coroutineScope {
            launch {
                tilt.animateTo(BOTTLE_TILT, tween(220, easing = FastOutSlowInEasing))
                delay(420)
                tilt.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
            launch {
                delay(120)
                pour.snapTo(0f)
                pour.animateTo(1f, tween(POUR_MILLIS, easing = LinearEasing))
            }
        }
        pouring = false
    }

    LaunchedEffect(state.date, state.waterMl) {
        val (lastDate, lastMl) = previous
        previous = state.date to state.waterMl
        if (lastDate != state.date) {
            addTapped = false
            goalReachedShown = false
            return@LaunchedEffect
        }
        if (addTapped && state.waterMl > lastMl) {
            addTapped = false
            if (lastMl < goalMl && state.waterMl >= goalMl) celebrations++
        }
        if (state.waterMl < goalMl) goalReachedShown = false
    }

    LaunchedEffect(celebrations) {
        if (celebrations == 0) return@LaunchedEffect
        // With the bottle, once the water has reached the bar
        if (pouring) delay(POUR_LAND_MILLIS + POUR_MILLIS / 2L)
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        goalReachedShown = true
        burst.snapTo(0f)
        burst.animateTo(1f, tween(BURST_MILLIS, easing = FastOutSlowInEasing))
        delay(GOAL_LABEL_MILLIS)
        goalReachedShown = false
    }

    val dropColor = MaterialTheme.colorScheme.primary
    val celebrating = burst.value < 1f || goalReachedShown

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(Modifier.onGloballyPositioned { cardCoordinates = it }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            ) {
                AnimatedContent(
                    targetState = celebrating,
                    transitionSpec = {
                        (fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioHighBouncy), initialScale = 0.4f))
                            .togetherWith(fadeOut())
                    },
                    label = "water_icon",
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        cardCoordinates?.let { iconBounds = it.boundsOf(coordinates) }
                    },
                ) { done ->
                    Icon(
                        if (done) rememberVectorPainter(Icons.Default.CheckCircle)
                        else painterResource(R.drawable.ic_water_drop),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        // One announcement for title and progress
                        .semantics(mergeDescendants = true) {},
                ) {
                    Text(stringResource(R.string.water_title), style = MaterialTheme.typography.labelLarge)
                    Text(
                        pluralStringResource(R.plurals.water_glasses_of_goal, goal, drunk.formatAmount(), goal),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .onGloballyPositioned { coordinates ->
                                cardCoordinates?.let { barBounds = it.boundsOf(coordinates) }
                            },
                    )
                    AnimatedContent(
                        targetState = goalReachedShown,
                        transitionSpec = { fadeIn().togetherWith(fadeOut()) },
                        label = "water_subtitle",
                    ) { reached ->
                        if (reached) {
                            Text(
                                stringResource(R.string.water_goal_reached),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        } else {
                            Text(
                                stringResource(R.string.water_liters, formatLiters(state.waterMl), formatLiters(goalMl)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                FilledTonalIconButton(onClick = onRemove, enabled = state.waterMl > 0) {
                    Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.water_remove_glass))
                }
                FilledTonalIconButton(onClick = {
                    addTapped = true
                    onAdd()
                }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.water_add_glass))
                }
                state.bottle?.let { bottle ->
                    FilledTonalIconButton(
                        onClick = {
                            addTapped = true
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            pouring = true
                            pours++
                            onAddBottle()
                        },
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            cardCoordinates?.let { bottleBounds = it.boundsOf(coordinates) }
                        },
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_water_bottle),
                            contentDescription = stringResource(R.string.water_add_bottle_ml, bottle.ml),
                            modifier = Modifier.graphicsLayer {
                                rotationZ = tilt.value
                                // Tilts around the bottom, like a hand pouring
                                transformOrigin = TransformOrigin(0.5f, 0.9f)
                            },
                        )
                    }
                }
            }
            // Drops and celebration, over the content; they don't take touches
            Canvas(Modifier.matchParentSize()) {
                if (pour.value < 1f && bottleBounds != Rect.Zero && barBounds != Rect.Zero) {
                    drawPour(pour.value, bottleBounds, barBounds, progress, dropColor)
                }
                if (burst.value < 1f && iconBounds != Rect.Zero) {
                    drawBurst(burst.value, iconBounds.center, dropColor)
                }
            }
        }
    }
}

/** Drops leave the bottle's mouth one after the other and fall along an arc into the bar. */
private fun DrawScope.drawPour(t: Float, bottle: Rect, bar: Rect, fill: Float, color: Color) {
    val start = Offset(bottle.left + bottle.width * 0.3f, bottle.top + bottle.height * 0.25f)
    val radius = 3.5.dp.toPx()
    val lift = 28.dp.toPx()
    repeat(DROPS) { i ->
        val begin = i * DROP_STAGGER
        val local = ((t - begin) / DROP_FLIGHT).coerceIn(0f, 1f)
        if (local <= 0f || local >= 1f) return@repeat
        // Spread along the filled part of the bar, never all on the same spot
        val share = 0.25f + 0.75f * ((i * 37) % DROPS) / (DROPS - 1f)
        val end = Offset(bar.left + bar.width * (fill.coerceAtLeast(0.2f) * share), bar.center.y)
        val control = Offset((start.x + end.x) / 2f, minOf(start.y, end.y) - lift)
        val position = quadratic(start, control, end, local)
        val fade = if (local > 0.8f) (1f - local) / 0.2f else 1f
        drawDrop(position, radius * (1f - 0.3f * local), color.copy(alpha = color.alpha * fade))
    }
}

/** Drops spray out of the water icon and fade. */
private fun DrawScope.drawBurst(t: Float, center: Offset, color: Color) {
    val distance = 34.dp.toPx() * t
    val radius = 3.dp.toPx() * (1f - 0.5f * t)
    repeat(BURST_DROPS) { i ->
        val angle = 2 * PI * i / BURST_DROPS - PI / 2
        val position = Offset(center.x + (cos(angle) * distance).toFloat(), center.y + (sin(angle) * distance).toFloat())
        drawCircle(color.copy(alpha = color.alpha * (1f - t)), radius, position)
    }
}

/** A water drop: round bottom, pointed top. */
private fun DrawScope.drawDrop(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius * 2.2f)
        cubicTo(
            center.x + radius * 0.4f, center.y - radius * 1.4f,
            center.x + radius, center.y - radius * 0.6f,
            center.x + radius, center.y,
        )
        cubicTo(
            center.x + radius, center.y + radius * 1.35f,
            center.x - radius, center.y + radius * 1.35f,
            center.x - radius, center.y,
        )
        cubicTo(
            center.x - radius, center.y - radius * 0.6f,
            center.x - radius * 0.4f, center.y - radius * 1.4f,
            center.x, center.y - radius * 2.2f,
        )
        close()
    }
    drawPath(path, color)
}

private fun quadratic(a: Offset, control: Offset, b: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        u * u * a.x + 2 * u * t * control.x + t * t * b.x,
        u * u * a.y + 2 * u * t * control.y + t * t * b.y,
    )
}

/** Bounds of [child] in these coordinates; empty while either is detached. */
private fun LayoutCoordinates.boundsOf(child: LayoutCoordinates): Rect =
    if (isAttached && child.isAttached) localBoundingBoxOf(child, clipBounds = false) else Rect.Zero

/** Counterclockwise: the mouth turns towards the bar on the left. */
private const val BOTTLE_TILT = -55f
private const val POUR_MILLIS = 900

/** When the first drops reach the bar. */
private const val POUR_LAND_MILLIS = 450
private const val DROPS = 7
private const val DROP_STAGGER = 0.07f
private const val DROP_FLIGHT = 0.55f
private const val BURST_DROPS = 10
private const val BURST_MILLIS = 700
private const val GOAL_LABEL_MILLIS = 2_500L
