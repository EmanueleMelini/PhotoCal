package it.emanuelemelini.photocal.data.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderScheduleTest {

    private val rome = ZoneId.of("Europe/Rome")
    private fun at(text: String): ZonedDateTime = LocalDateTime.parse(text).atZone(rome)

    @Test
    fun dailyReminderLaterTodayFiresToday() {
        val next = ReminderSchedule.nextTrigger(ReminderType.LUNCH, LocalTime.of(13, 30), at("2026-09-29T10:00"))
        assertEquals(at("2026-09-29T13:30"), next)
    }

    @Test
    fun dailyReminderAlreadyPassedFiresTomorrow() {
        val next = ReminderSchedule.nextTrigger(ReminderType.LUNCH, LocalTime.of(13, 30), at("2026-09-29T13:30"))
        assertEquals(at("2026-09-30T13:30"), next)
    }

    @Test
    fun weeklyReminderFiresOnTheNextSunday() {
        // 29 September 2026 is a Tuesday
        val next = ReminderSchedule.nextTrigger(ReminderType.WEEKLY_SUMMARY, LocalTime.of(22, 0), at("2026-09-29T10:00"))
        assertEquals(at("2026-10-04T22:00"), next)
        assertEquals(DayOfWeek.SUNDAY, next.dayOfWeek)
    }

    @Test
    fun weeklyReminderOnSundayAfterItsTimeWaitsAWeek() {
        val next = ReminderSchedule.nextTrigger(ReminderType.WEEKLY_SUMMARY, LocalTime.of(22, 0), at("2026-10-04T22:30"))
        assertEquals(at("2026-10-11T22:00"), next)
    }

    @Test
    fun daylightSavingChangeKeepsTheLocalTime() {
        // On 25 October 2026 clocks go back one hour in Italy
        val next = ReminderSchedule.nextTrigger(ReminderType.BREAKFAST, LocalTime.of(8, 30), at("2026-10-24T09:00"))
        assertEquals(LocalDateTime.parse("2026-10-25T08:30"), next.toLocalDateTime())
    }
}
