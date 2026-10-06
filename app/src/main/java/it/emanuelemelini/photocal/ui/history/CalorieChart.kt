package it.emanuelemelini.photocal.ui.history

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.ui.formatKcal
import it.emanuelemelini.photocal.ui.theme.LocalChartColors
import java.time.format.TextStyle as DateTextStyle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private val AXIS_WIDTH = 40.dp
private val X_LABELS_HEIGHT = 22.dp
private val TOP_PADDING = 8.dp
private val MAX_BAR_WIDTH = 24.dp
private val BAR_GAP = 2.dp
private val BAR_RADIUS = 4.dp
private const val GROW_MILLIS = 700

/** Share of the animation by which the last column starts after the first. */
private const val GROW_SPREAD = 0.4f


/**
 * Daily kcal columns with the dashed goal line.
 * Columns over the goal are orange (and cross the line).
 * A tap selects the day ([onSelect] with the index in [days]).
 * The columns grow from the axis, from left to right, whenever the days change.
 */
@Composable
fun CalorieChart(
    days: List<DayKcal>,
    kcalGoal: Int,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val goalLabelStyle = labelStyle.copy(color = colors.onSurface, fontWeight = FontWeight.Bold)
    // Color-blind safe pair chosen by the theme
    val barColor = LocalChartColors.current.withinGoal
    val overGoalColor = LocalChartColors.current.overGoal

    val chartDescription = pluralStringResource(R.plurals.history_chart_description, days.size, days.size, kcalGoal)
    val locale = AppLocale.current

    val growth = remember(days) { Animatable(0f) }
    LaunchedEffect(days) { growth.animateTo(1f, tween(GROW_MILLIS, easing = FastOutSlowInEasing)) }

    val maxKcal = days.maxOf { it.kcal }
    val step = niceStep(max(kcalGoal.toDouble(), maxKcal))
    val axisMax = ceil(max(kcalGoal.toDouble(), maxKcal) * 1.05 / step) * step

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .pointerInput(days.size) {
                detectTapGestures { offset ->
                    val slot = (size.width - AXIS_WIDTH.toPx()) / days.size
                    val index = ((offset.x - AXIS_WIDTH.toPx()) / slot).toInt()
                    if (index in days.indices) onSelect(index)
                }
            }
            .semantics {
                contentDescription = chartDescription
            },
    ) {
        val plotLeft = AXIS_WIDTH.toPx()
        val plotTop = TOP_PADDING.toPx()
        val plotBottom = size.height - X_LABELS_HEIGHT.toPx()
        val plotHeight = plotBottom - plotTop
        fun yOf(value: Double) = plotBottom - (value / axisMax * plotHeight).toFloat()

        val goalY = yOf(kcalGoal.toDouble())
        val goalLabel = textMeasurer.measure(kcalGoal.toString(), goalLabelStyle)

        // Horizontal grid: thin solid lines, rounded labels. A label that would overlap the
        // goal label is skipped.
        var tick = 0.0
        while (tick <= axisMax) {
            val y = yOf(tick)
            drawLine(colors.outlineVariant, Offset(plotLeft, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            if (abs(y - goalY) > goalLabel.size.height) {
                drawLabel(textMeasurer, tick.formatKcal(), labelStyle, rightX = plotLeft - 6.dp.toPx(), centerY = y)
            }
            tick += step
        }

        // Columns: at most 24dp wide, 2dp gap between them, rounded corners only at the top
        val slot = (size.width - plotLeft) / days.size
        val barWidth = min(MAX_BAR_WIDTH.toPx(), slot - BAR_GAP.toPx())
        days.forEachIndexed { index, day ->
            if (day.kcal <= 0) return@forEachIndexed
            val left = plotLeft + slot * index + (slot - barWidth) / 2
            // Each column starts a little after the one on its left
            val delay = GROW_SPREAD * index / days.size
            val grown = (growth.value * (1 + GROW_SPREAD) - delay).coerceIn(0f, 1f)
            if (grown <= 0f) return@forEachIndexed
            val top = yOf(day.kcal * FastOutSlowInEasing.transform(grown))
            val radius = min(BAR_RADIUS.toPx(), (plotBottom - top) / 2)
            val baseColor = if (day.kcal > kcalGoal) overGoalColor else barColor
            val color = if (selectedIndex == null || selectedIndex == index) baseColor else baseColor.copy(alpha = 0.35f)
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left, top = top, right = left + barWidth, bottom = plotBottom,
                        topLeftCornerRadius = CornerRadius(radius),
                        topRightCornerRadius = CornerRadius(radius),
                    )
                )
            }
            drawPath(path, color)
        }

        // Goal: the only dashed line (it is a threshold, not a grid line), value on the axis
        drawLine(
            color = colors.onSurface,
            start = Offset(plotLeft, goalY),
            end = Offset(size.width, goalY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
        drawText(
            goalLabel,
            topLeft = Offset(plotLeft - 6.dp.toPx() - goalLabel.size.width, goalY - goalLabel.size.height / 2),
        )

        // Day labels: all of them for 7 days, one every 5 (counting from today) for 30
        val labelEvery = if (days.size <= 7) 1 else 5
        days.forEachIndexed { index, day ->
            if ((days.lastIndex - index) % labelEvery != 0) return@forEachIndexed
            val text = if (days.size <= 7) {
                day.date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale)
            } else {
                day.date.dayOfMonth.toString()
            }
            val layout = textMeasurer.measure(text, labelStyle)
            val centerX = plotLeft + slot * index + slot / 2
            drawText(layout, topLeft = Offset(centerX - layout.size.width / 2, plotBottom + 4.dp.toPx()))
        }
    }
}

private fun DrawScope.drawLabel(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    rightX: Float,
    centerY: Float,
) {
    val layout = textMeasurer.measure(text, style)
    drawText(layout, topLeft = Offset(rightX - layout.size.width, centerY - layout.size.height / 2))
}

/** "Round" grid step (250, 500, 1000...) to get 3-5 lines. */
private fun niceStep(maxValue: Double): Double = when {
    maxValue <= 1_000 -> 250.0
    maxValue <= 2_500 -> 500.0
    maxValue <= 5_000 -> 1_000.0
    else -> 2_000.0
}
