package it.emanuelemelini.photocal.data.reminders

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

object ReminderSchedule {

    /** Day of the weekly summary. */
    val WEEKLY_DAY: DayOfWeek = DayOfWeek.SUNDAY

    /**
     * Next moment strictly after [now] at [time]: today or tomorrow for daily reminders, the
     * next [WEEKLY_DAY] for weekly ones. Daylight saving gaps are handled by [ZonedDateTime].
     */
    fun nextTrigger(type: ReminderType, time: LocalTime, now: ZonedDateTime): ZonedDateTime {
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            if (type.weekly && date.dayOfWeek != WEEKLY_DAY) continue
            val candidate = date.atTime(time).atZone(now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        error("No trigger found for $type")
    }
}
