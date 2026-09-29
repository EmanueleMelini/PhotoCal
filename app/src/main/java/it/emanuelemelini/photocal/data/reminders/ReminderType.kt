package it.emanuelemelini.photocal.data.reminders

import androidx.annotation.StringRes
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.db.MealType
import java.time.LocalTime

/**
 * Local reminders (no push server: they are scheduled on the phone). The constant names
 * are stored in the preferences: don't rename them. New types go at the end: the ordinal
 * is part of the notification and alarm ids.
 */
enum class ReminderType(
    @StringRes val labelRes: Int,
    val defaultTime: LocalTime,
    /** Meal the reminder is about; it is skipped when that meal is already logged today. */
    val meal: MealType? = null,
    /** Weekly reminders fire on Sunday only. */
    val weekly: Boolean = false,
    /** Share of the water goal to reach by then; the reminder is skipped once it is reached. */
    val waterShare: Double? = null,
    /** Line under the name in Settings. */
    @StringRes val hintRes: Int? = null,
) {
    BREAKFAST(R.string.meal_breakfast, LocalTime.of(8, 30), meal = MealType.BREAKFAST),
    LUNCH(R.string.meal_lunch, LocalTime.of(13, 30), meal = MealType.LUNCH),
    SNACK(R.string.meal_snack, LocalTime.of(16, 30), meal = MealType.SNACK),
    DINNER(R.string.meal_dinner, LocalTime.of(20, 30), meal = MealType.DINNER),
    DAILY_SUMMARY(R.string.reminder_daily_summary, LocalTime.of(21, 30)),
    WEEKLY_SUMMARY(R.string.reminder_weekly_summary, LocalTime.of(22, 0), weekly = true, hintRes = R.string.reminder_weekly_day),
    WATER_HALF(R.string.reminder_water_half, LocalTime.of(15, 0), waterShare = 0.5, hintRes = R.string.reminder_water_half_hint),
    WATER_GOAL(R.string.reminder_water_goal, LocalTime.of(22, 0), waterShare = 1.0, hintRes = R.string.reminder_water_goal_hint),
}

data class ReminderConfig(
    val type: ReminderType,
    val enabled: Boolean = false,
    val time: LocalTime = type.defaultTime,
)
