package it.emanuelemelini.photocal.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.data.nutrition.WaterCalculator
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import it.emanuelemelini.photocal.data.share.SharedCard
import it.emanuelemelini.photocal.data.share.SharedDay
import it.emanuelemelini.photocal.data.share.SharedEntry
import it.emanuelemelini.photocal.data.share.SharedGoals
import it.emanuelemelini.photocal.ui.components.Avatar
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.formatKcal
import it.emanuelemelini.photocal.ui.formatLiters
import it.emanuelemelini.photocal.ui.formatSignedAmount
import it.emanuelemelini.photocal.ui.history.CalorieChart
import it.emanuelemelini.photocal.ui.history.DayKcal
import it.emanuelemelini.photocal.ui.longLabel
import it.emanuelemelini.photocal.ui.pluralCount
import it.emanuelemelini.photocal.ui.quantityLabel
import it.emanuelemelini.photocal.ui.shortLabel
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Read-only view of a shared day or week: the receiver's screen and the sender's preview.
 * Hidden parts are simply missing from [card].
 */
@Composable
fun SharedCardView(card: SharedCard, avatar: ImageBitmap?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header(card, avatar)
        if (card.week) WeekContent(card) else DayContent(card, card.days.single())
        val sharedOn = Instant.ofEpochSecond(card.sharedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        Text(
            stringResource(R.string.shared_footer, sharedOn.shortLabel()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Header(card: SharedCard, avatar: ImageBitmap?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(image = avatar, name = card.name, size = 64.dp)
        Column(Modifier.padding(start = 16.dp)) {
            Text(card.name, style = MaterialTheme.typography.titleLarge)
            Text(
                if (card.week) {
                    stringResource(R.string.shared_week_range, card.days.first().date.shortLabel(), card.days.last().date.shortLabel())
                } else {
                    card.days.single().date.longLabel()
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayContent(card: SharedCard, day: SharedDay) {
    val goals = card.goals
    if (day.kcal != null || day.proteinG != null) {
        Section(stringResource(R.string.shared_calories)) {
            day.kcal?.let { kcal -> KcalProgress(kcal, goals?.kcal) }
            if (day.proteinG != null) {
                if (day.kcal != null) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    MacroValue(stringResource(R.string.macro_protein), day.proteinG, goals?.proteinG)
                    MacroValue(stringResource(R.string.macro_carbs), day.carbsG ?: 0.0, goals?.carbsG)
                    MacroValue(stringResource(R.string.macro_fat), day.fatG ?: 0.0, goals?.fatG)
                }
            }
        }
    }
    day.waterMl?.let { ml ->
        Section(stringResource(R.string.water_title)) { WaterProgress(ml, card.glassMl, goals) }
    }
    day.weightKg?.let { kg ->
        Section(stringResource(R.string.shared_weight)) {
            Text(stringResource(R.string.shared_weight_value, kg.formatAmount()), style = MaterialTheme.typography.titleMedium)
        }
    }
    day.entries?.let { entries -> Meals(entries) }
}

@Composable
private fun KcalProgress(kcal: Double, goal: Int?) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(kcal.formatKcal(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text(
            if (goal != null) " / $goal kcal" else " kcal",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    if (goal == null || goal <= 0) return
    val over = kcal > goal
    LinearProgressIndicator(
        progress = { (kcal / goal).toFloat().coerceIn(0f, 1f) },
        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(10.dp),
    )
    Text(
        if (over) stringResource(R.string.today_over_goal, (kcal - goal).formatKcal()) else stringResource(R.string.shared_within_goal),
        style = MaterialTheme.typography.bodyMedium,
        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun MacroValue(label: String, grams: Double, goal: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (goal != null) "${grams.formatAmount()} / $goal g" else "${grams.formatAmount()} g",
            style = MaterialTheme.typography.titleMedium,
        )
        if (goal != null && goal > 0) {
            LinearProgressIndicator(
                progress = { (grams / goal).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .width(72.dp),
            )
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WaterProgress(ml: Int, glassMl: Int?, goals: SharedGoals?) {
    val glass = glassMl ?: SettingsRepository.DEFAULT_GLASS_ML
    val drunk = WaterCalculator.glasses(ml, glass)
    val goalMl = goals?.waterMl
    if (goalMl == null || goalMl <= 0) {
        Text(
            pluralStringResource(R.plurals.shared_water_glasses, pluralCount(drunk), drunk.formatAmount()),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(formatLiters(ml), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val goal = WaterCalculator.goalGlasses(goalMl, glass)
    Text(
        pluralStringResource(R.plurals.water_glasses_of_goal, goal, drunk.formatAmount(), goal),
        style = MaterialTheme.typography.titleMedium,
    )
    LinearProgressIndicator(
        progress = { (ml.toFloat() / goalMl).coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
    Text(
        stringResource(R.string.water_liters, formatLiters(ml), formatLiters(goalMl)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (ml >= goalMl) {
        Text(
            stringResource(R.string.shared_goal_reached),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun Meals(entries: List<SharedEntry>) {
    Section(stringResource(R.string.shared_meals)) {
        if (entries.isEmpty()) {
            Text(stringResource(R.string.shared_no_meals), style = MaterialTheme.typography.bodyMedium)
            return@Section
        }
        MealType.entries.forEach { meal ->
            val items = entries.filter { it.mealType == meal }
            if (items.isEmpty()) return@forEach
            Row(Modifier.padding(top = 8.dp)) {
                Text(
                    stringResource(meal.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${items.sumOf { it.kcal }.formatKcal()} kcal",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                        quantityLabel(entry.grams, entry.servingUnit, entry.servings)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("${entry.kcal.formatKcal()} kcal", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun WeekContent(card: SharedCard) {
    val days = card.days
    val goals = card.goals
    // A day without food is a day not logged, not a day at 0 kcal
    val logged = days.filter { (it.kcal ?: 0.0) > 0.0 }

    if (days.first().kcal != null || days.first().proteinG != null) {
        Section(stringResource(R.string.shared_calories)) {
            if (logged.isEmpty()) {
                Text(stringResource(R.string.shared_no_days), style = MaterialTheme.typography.bodyMedium)
                return@Section
            }
            if (days.first().kcal != null) {
                val average = logged.map { it.kcal!! }.average()
                Text(stringResource(R.string.shared_avg_kcal, average.formatKcal()), style = MaterialTheme.typography.titleMedium)
                goals?.kcal?.takeIf { it > 0 }?.let { goal ->
                    Text(
                        pluralStringResource(R.plurals.shared_days_within, logged.size, logged.count { it.kcal!! <= goal }, logged.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    WeekChart(days, goal)
                }
            }
            if (days.first().proteinG != null) {
                Text(
                    listOf(
                        stringResource(R.string.macro_protein_g, logged.map { it.proteinG ?: 0.0 }.average().formatAmount()),
                        stringResource(R.string.macro_carbs_g, logged.map { it.carbsG ?: 0.0 }.average().formatAmount()),
                        stringResource(R.string.macro_fat_g, logged.map { it.fatG ?: 0.0 }.average().formatAmount()),
                    ).joinToString(" · ", prefix = stringResource(R.string.shared_avg_macros) + " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (days.first().waterMl != null) {
        Section(stringResource(R.string.water_title)) {
            val average = days.map { it.waterMl ?: 0 }.average().roundToInt()
            Text(stringResource(R.string.shared_avg_water, formatLiters(average)), style = MaterialTheme.typography.titleMedium)
            goals?.waterMl?.takeIf { it > 0 }?.let { goal ->
                Text(
                    pluralStringResource(R.plurals.shared_days_water, days.size, days.count { (it.waterMl ?: 0) >= goal }, days.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    val weights = days.mapNotNull { it.weightKg }
    if (weights.isNotEmpty()) {
        Section(stringResource(R.string.shared_weight)) {
            Text(
                if (weights.size == 1) {
                    stringResource(R.string.shared_weight_value, weights.single().formatAmount())
                } else {
                    stringResource(
                        R.string.shared_weight_change,
                        weights.first().formatAmount(),
                        weights.last().formatAmount(),
                        (weights.last() - weights.first()).formatSignedAmount(),
                    )
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }

    Section(stringResource(R.string.shared_days)) {
        days.forEach { day ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(day.date.shortLabel(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                day.waterMl?.let {
                    Text(
                        formatLiters(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                }
                day.kcal?.let { kcal ->
                    Text(if (kcal > 0) "${kcal.formatKcal()} kcal" else "–", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** The History chart: a tap shows the kcal of that day. */
@Composable
private fun WeekChart(days: List<SharedDay>, goal: Int) {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    CalorieChart(
        days = days.map { DayKcal(it.date, it.kcal ?: 0.0) },
        kcalGoal = goal,
        selectedIndex = selected,
        onSelect = { index -> selected = if (selected == index) null else index },
        modifier = Modifier.padding(top = 8.dp),
    )
    selected?.let { index ->
        val day = days[index]
        Text(
            stringResource(R.string.shared_selected_day, day.date.shortLabel(), (day.kcal ?: 0.0).formatKcal()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
