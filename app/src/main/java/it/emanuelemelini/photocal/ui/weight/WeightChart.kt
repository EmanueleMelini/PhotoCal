package it.emanuelemelini.photocal.ui.weight

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.db.WeightEntry
import it.emanuelemelini.photocal.ui.formatAmount
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

private val AXIS_WIDTH = 44.dp
private val X_LABELS_HEIGHT = 22.dp
private val TOP_PADDING = 8.dp

/** Room for the dot of the latest weigh-in, which sits on the right edge. */
private val END_PADDING = 10.dp
private const val DRAW_MILLIS = 800

/**
 * Weight over time: a single 2dp line with 8dp dots (ringed with the surface color),
 * x proportional to the date within [from]..today. A tap selects the nearest weigh-in.
 * The line draws itself from left to right whenever the range or the weigh-ins change.
 */
@Composable
fun WeightChart(
    entries: List<WeightEntry>,
    from: LocalDate,
    selected: WeightEntry?,
    onSelect: (WeightEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val description = pluralStringResource(R.plurals.weight_chart_description, entries.size, entries.size)
    val locale = AppLocale.current
    val dateFormatter = DateTimeFormatter.ofPattern("d MMM", locale)

    val reveal = remember(entries, from) { Animatable(0f) }
    LaunchedEffect(entries, from) { reveal.animateTo(1f, tween(DRAW_MILLIS, easing = FastOutSlowInEasing)) }

    val today = LocalDate.now()
    val totalDays = ChronoUnit.DAYS.between(from, today).coerceAtLeast(1).toFloat()
    val min = entries.minOf { it.weightKg }
    val max = entries.maxOf { it.weightKg }
    // "Round" step so that 3-5 lines cover the range, with some air around the values
    val step = when {
        max - min <= 2 -> 0.5
        max - min <= 5 -> 1.0
        max - min <= 12 -> 2.0
        else -> 5.0
    }
    val axisMin = floor((min - step / 2) / step) * step
    val axisMax = ceil((max + step / 2) / step) * step

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(entries, from) {
                detectTapGestures { offset ->
                    val plotLeft = AXIS_WIDTH.toPx()
                    val plotWidth = size.width - plotLeft - END_PADDING.toPx()
                    val nearest = entries.minBy { entry ->
                        val x = plotLeft + ChronoUnit.DAYS.between(from, entry.date) / totalDays * plotWidth
                        abs(x - offset.x)
                    }
                    onSelect(nearest)
                }
            }
            .semantics { contentDescription = description },
    ) {
        val plotLeft = AXIS_WIDTH.toPx()
        val plotTop = TOP_PADDING.toPx()
        val plotBottom = size.height - X_LABELS_HEIGHT.toPx()
        val plotWidth = size.width - plotLeft - END_PADDING.toPx()
        fun xOf(date: LocalDate) = plotLeft + ChronoUnit.DAYS.between(from, date) / totalDays * plotWidth
        fun yOf(kg: Double) = plotBottom - ((kg - axisMin) / (axisMax - axisMin) * (plotBottom - plotTop)).toFloat()

        // Horizontal grid: thin solid lines, labels in kg
        var tick = axisMin
        while (tick <= axisMax + 1e-9) {
            val y = yOf(tick)
            drawLine(colors.outlineVariant, Offset(plotLeft, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            val layout = textMeasurer.measure(tick.formatAmount(), labelStyle)
            drawText(layout, topLeft = Offset(plotLeft - 6.dp.toPx() - layout.size.width, y - layout.size.height / 2))
            tick += step
        }

        // Line and dots, uncovered by the reveal
        val points = entries.map { Offset(xOf(it.date), yOf(it.weightKg)) }
        clipRect(right = plotLeft + (size.width - plotLeft) * reveal.value) {
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, colors.primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            entries.forEachIndexed { index, entry ->
                val isSelected = entry == selected
                val radius = (if (isSelected) 6.dp else 4.dp).toPx()
                drawCircle(colors.surface, radius = radius + 2.dp.toPx(), center = points[index])
                drawCircle(colors.primary, radius = radius, center = points[index])
            }
        }

        // Date labels: start, middle and end of the range
        listOf(from, from.plusDays((totalDays / 2).toLong()), today).forEachIndexed { index, date ->
            val layout = textMeasurer.measure(date.format(dateFormatter), labelStyle)
            val x = when (index) {
                0 -> plotLeft
                2 -> size.width - layout.size.width
                else -> xOf(date) - layout.size.width / 2
            }
            drawText(layout, topLeft = Offset(x, plotBottom + 4.dp.toPx()))
        }
    }
}
