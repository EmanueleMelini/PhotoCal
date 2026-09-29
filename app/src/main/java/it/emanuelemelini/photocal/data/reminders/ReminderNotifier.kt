package it.emanuelemelini.photocal.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.FoodRepository
import it.emanuelemelini.photocal.data.WaterRepository
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.data.nutrition.WaterCalculator
import it.emanuelemelini.photocal.data.prefs.Settings
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import it.emanuelemelini.photocal.ui.LaunchRequest
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.formatKcal
import it.emanuelemelini.photocal.ui.pluralCount
import it.emanuelemelini.photocal.ui.widget.WaterWidgetActionReceiver
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Builds and shows the reminder notifications. */
class ReminderNotifier(
    private val context: Context,
    private val foodRepository: FoodRepository,
    private val waterRepository: WaterRepository,
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
                NotificationChannel(CHANNEL_WATER, res.getString(R.string.channel_water_reminders), NotificationManager.IMPORTANCE_DEFAULT),
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
            type.waterShare != null -> waterNotification(type, type.waterShare, settings, res) ?: return
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
        notifyIfAllowed(type, builder)
    }

    /** Checked here as well: [refreshWater] updates notifications without going through [show]. */
    private fun notifyIfAllowed(type: ReminderType, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        notifications.notify(notificationId(type), builder.build())
    }

    /**
     * After any change to today's water: updates the water reminders still shown, or removes
     * them once their share of the goal is reached.
     */
    suspend fun refreshWater() {
        val shown = context.getSystemService(NotificationManager::class.java).activeNotifications.map { it.id }.toSet()
        val types = ReminderType.entries.filter { it.waterShare != null && notificationId(it) in shown }
        if (types.isEmpty()) return
        val settings = settingsRepository.settings.first()
        val res = AppLocale.localizedContext(context).resources
        types.forEach { type ->
            val builder = waterNotification(type, type.waterShare!!, settings, res)
            if (builder == null) notifications.cancel(notificationId(type))
            // Silent update: it already alerted once
            else notifyIfAllowed(type, builder.setOnlyAlertOnce(true))
        }
    }

    /** null when [share] of today's water goal is already reached. */
    private suspend fun waterNotification(
        type: ReminderType,
        share: Double,
        settings: Settings,
        res: Resources,
    ): NotificationCompat.Builder? {
        val drunkMl = waterRepository.mlFor(LocalDate.now())
        val missingMl = WaterCalculator.missingMl(drunkMl, settings.waterGoalMl, settings.glassMl, share)
        if (missingMl <= 0) return null
        val locale = res.configuration.locales[0]
        val goal = WaterCalculator.goalGlasses(settings.waterGoalMl, settings.glassMl)
        val drunk = WaterCalculator.glasses(drunkMl, settings.glassMl).formatAmount(locale)
        val missing = WaterCalculator.glasses(missingMl, settings.glassMl)
        val text = res.getString(
            R.string.reminder_water_text,
            res.getQuantityString(R.plurals.water_glasses_of_goal, goal, drunk, goal),
            res.getQuantityString(
                if (share < 1.0) R.plurals.reminder_water_missing_half else R.plurals.reminder_water_missing_goal,
                pluralCount(missing),
                missing.formatAmount(locale),
            ),
        )
        val title = res.getString(if (share < 1.0) R.string.reminder_water_half_title else R.string.reminder_water_goal_title)
        return base(CHANNEL_WATER, title, text)
            .setContentIntent(activity(type, 0, LaunchRequest.OpenToday))
            .addAction(0, res.getString(R.string.shortcut_water_long), addGlass(type))
    }

    /** The same broadcast as the widget "+": the repository then calls [refreshWater]. */
    private fun addGlass(type: ReminderType): PendingIntent = PendingIntent.getBroadcast(
        context,
        type.ordinal * 10 + 1,
        Intent(context, WaterWidgetActionReceiver::class.java).setAction(WaterWidgetActionReceiver.ACTION_ADD),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

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
        const val CHANNEL_WATER = "water_reminders"
    }
}
