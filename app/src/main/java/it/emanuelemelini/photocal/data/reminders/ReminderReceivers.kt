package it.emanuelemelini.photocal.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.emanuelemelini.photocal.PhotoCalApp
import kotlinx.coroutines.launch

/** Fired by the alarm: shows the reminder (if still relevant) and schedules the next one. */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = ReminderType.entries.find { it.name == intent.getStringExtra(EXTRA_TYPE) } ?: return
        val container = (context.applicationContext as PhotoCalApp).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.reminderNotifier.show(type)
                container.reminderScheduler.reschedule(type)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TYPE = "reminder_type"
    }
}

/** Alarms are lost on reboot and must follow time or time zone changes: schedule them again. */
class ReminderRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val container = (context.applicationContext as PhotoCalApp).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.reminderScheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
