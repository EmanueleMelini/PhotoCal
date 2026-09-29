package it.emanuelemelini.photocal.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.FoodRepository
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import it.emanuelemelini.photocal.ui.LaunchRequest
import it.emanuelemelini.photocal.ui.formatKcal
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Builds and shows the reminder notifications. */
class ReminderNotifier(
    private val context: Context,
    private val foodRepository: FoodRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val notifications = NotificationManagerCompat.from(context)

    /** Called at every app start, so channel names follow the current language. */
    fun createChannels() {
        val res = AppLocale.localizedContext(context).resources
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_MEALS, res.getString(R.string.channel_meal_reminders), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_SUMMARIES, res.getString(R.string.channel_summaries), NotificationManager.IMPORTANCE_LOW),
            )
        )
    }

    suspend fun show(type: ReminderType) {
        val settings = settingsRepository.settings.first()
        if (settings.reminders[type]?.enabled != true) return
        // The runtime permission exists only from Android 13; before, only the app switch counts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!notifications.areNotificationsEnabled()) return
        val res = AppLocale.localizedContext(context).resources
        val today = LocalDate.now()

        val builder = when {
            type.meal != null -> {
                // A meal that is already in the diary needs no reminder
                if (foodRepository.countMeal(today, type.meal) > 0) return
                base(CHANNEL_MEALS, res.getString(mealTitle(type.meal)), res.getString(R.string.reminder_meal_text))
                    .setContentIntent(activity(type, 0, LaunchRequest.OpenToday))
                    .addAction(0, res.getString(R.string.fab_photo), activity(type, 1, LaunchRequest.AddPhoto(type.meal)))
                    .addAction(0, res.getString(R.string.fab_manual), activity(type, 2, LaunchRequest.AddManual(type.meal)))
            }
            type.weekly -> {
                val days = foodRepository.totalsBetween(today.minusDays(6), today).filter { it.kcal > 0 }
                val goal = settings.dailyKcalGoal
                val text = if (days.isEmpty()) res.getString(R.string.reminder_weekly_empty)
                else res.getString(
                    R.string.reminder_weekly_text,
                    days.map { it.kcal }.average().formatKcal(),
                    days.count { it.kcal <= goal },
                    days.size,
                )
                base(CHANNEL_SUMMARIES, res.getString(R.string.reminder_weekly_title), text)
                    .setContentIntent(activity(type, 0, LaunchRequest.OpenHistory))
            }
            else -> {
                val kcal = foodRepository.totalsFor(today).kcal
                val goal = settings.dailyKcalGoal
                val text = when {
                    kcal <= 0 -> res.getString(R.string.reminder_daily_empty)
                    kcal <= goal -> res.getString(R.string.reminder_daily_within, kcal.formatKcal(), goal, (goal - kcal).formatKcal())
                    else -> res.getString(R.string.reminder_daily_over, kcal.formatKcal(), goal, (kcal - goal).formatKcal())
                }
                base(CHANNEL_SUMMARIES, res.getString(R.string.reminder_daily_title), text)
                    .setContentIntent(activity(type, 0, LaunchRequest.OpenToday))
            }
        }
        notifications.notify(notificationId(type), builder.build())
    }

    /** Removes the reminder of a meal as soon as it is logged. */
    fun cancelMeal(meal: MealType) {
        ReminderType.entries.filter { it.meal == meal }.forEach { notifications.cancel(notificationId(it)) }
    }

    private fun base(channel: String, title: String, text: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)

    private fun activity(type: ReminderType, index: Int, request: LaunchRequest): PendingIntent =
        PendingIntent.getActivity(
            context,
            type.ordinal * 10 + index,
            LaunchRequest.intent(context, request),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun mealTitle(meal: MealType): Int = when (meal) {
        MealType.BREAKFAST -> R.string.reminder_breakfast_title
        MealType.LUNCH -> R.string.reminder_lunch_title
        MealType.SNACK -> R.string.reminder_snack_title
        MealType.DINNER -> R.string.reminder_dinner_title
    }

    private fun notificationId(type: ReminderType) = 1000 + type.ordinal

    private companion object {
        const val CHANNEL_MEALS = "meal_reminders"
        const val CHANNEL_SUMMARIES = "summaries"
    }
}
