package it.emanuelemelini.photocal.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules each enabled reminder with a 10-minute window alarm: no "exact alarm" permission
 * needed. (setAndAllowWhileIdle would get up to a 1-hour window.) If the phone is in Doze
 * the reminder shows up as soon as it is picked up. After firing, the receiver schedules
 * the next occurrence.
 */
class ReminderScheduler(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll() {
        val reminders = settingsRepository.settings.first().reminders
        ReminderType.entries.forEach { apply(reminders.getValue(it)) }
    }

    suspend fun reschedule(type: ReminderType) {
        apply(settingsRepository.settings.first().reminders.getValue(type))
    }

    private fun apply(config: ReminderConfig) {
        val intent = pendingIntent(config.type)
        alarmManager.cancel(intent)
        if (!config.enabled) return
        val trigger = ReminderSchedule.nextTrigger(config.type, config.time, ZonedDateTime.now())
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, trigger.toInstant().toEpochMilli(), WINDOW_MILLIS, intent)
    }

    private companion object {
        /** Smallest window Android allows for non-exact alarms. */
        val WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(10)
    }

    private fun pendingIntent(type: ReminderType): PendingIntent = PendingIntent.getBroadcast(
        context,
        type.ordinal,
        Intent(context, ReminderAlarmReceiver::class.java).putExtra(ReminderAlarmReceiver.EXTRA_TYPE, type.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
