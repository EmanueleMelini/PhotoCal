package it.emanuelemelini.photocal.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.PhotoCalApp
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.WaterRepository
import it.emanuelemelini.photocal.data.nutrition.WaterCalculator
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import it.emanuelemelini.photocal.ui.LaunchRequest
import it.emanuelemelini.photocal.ui.formatAmount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Home screen widget: today's glasses of water ("3/8") between − and +, and below two
 * shortcuts to add food to today's diary (photo, manual entry).
 * Plain RemoteViews: the widget is tiny and needs no extra library.
 */
class WaterWidget(
    private val context: Context,
    private val waterRepository: WaterRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val manager = AppWidgetManager.getInstance(context)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Redraws every placed widget with today's count; called after any change. */
    suspend fun update() {
        val ids = manager.getAppWidgetIds(ComponentName(context, WaterWidgetProvider::class.java))
        if (ids.isEmpty()) {
            cancelMidnightRefresh()
            return
        }
        manager.updateAppWidget(ids, views())
        scheduleMidnightRefresh()
    }

    suspend fun addGlass() = waterRepository.addGlass(LocalDate.now(), settingsRepository.settings.first().glassMl)

    suspend fun removeGlass() = waterRepository.removeGlass(LocalDate.now(), settingsRepository.settings.first().glassMl)

    fun cancelMidnightRefresh() = alarmManager.cancel(broadcast(WaterWidgetActionReceiver.ACTION_REFRESH))

    private suspend fun views(): RemoteViews {
        val settings = settingsRepository.settings.first()
        val ml = waterRepository.mlFor(LocalDate.now())
        // Text is set here, not in the layout: the launcher would inflate it in the device
        // language instead of the one chosen in the app
        val res = AppLocale.localizedContext(context).resources
        val locale = res.configuration.locales[0]
        val drunk = WaterCalculator.glasses(ml, settings.glassMl).formatAmount(locale)
        val goal = WaterCalculator.goalGlasses(settings.waterGoalMl, settings.glassMl)

        return RemoteViews(context.packageName, R.layout.widget_water).apply {
            setTextViewText(R.id.water_count, res.getString(R.string.widget_water_count, drunk, goal))
            setTextViewText(R.id.water_label, res.getString(R.string.water_title))
            setContentDescription(
                R.id.water_counter,
                res.getQuantityString(R.plurals.water_glasses_of_goal, goal, drunk, goal),
            )
            setContentDescription(R.id.water_remove, res.getString(R.string.water_remove_glass))
            setContentDescription(R.id.water_add, res.getString(R.string.water_add_glass))
            setContentDescription(R.id.food_photo, res.getString(R.string.widget_add_photo))
            setContentDescription(R.id.food_manual, res.getString(R.string.widget_add_manual))
            // Nothing to remove: dimmed (a tap does nothing, the count never goes below zero)
            setInt(R.id.water_remove, "setImageAlpha", if (ml > 0) 255 else DISABLED_ALPHA)

            setOnClickPendingIntent(R.id.water_remove, broadcast(WaterWidgetActionReceiver.ACTION_REMOVE))
            setOnClickPendingIntent(R.id.water_add, broadcast(WaterWidgetActionReceiver.ACTION_ADD))
            setOnClickPendingIntent(R.id.water_counter, activity(0, LaunchRequest.OpenToday))
            setOnClickPendingIntent(R.id.food_photo, activity(1, LaunchRequest.AddPhoto(meal = null)))
            setOnClickPendingIntent(R.id.food_manual, activity(2, LaunchRequest.AddManual(meal = null)))
        }
    }

    /**
     * Starts the new day's count from zero, within 10 minutes after midnight (a plain inexact
     * alarm could be an hour late). Not a wakeup alarm: while the phone sleeps it fires as soon
     * as it is used, which is also when the widget can be seen.
     */
    private fun scheduleMidnightRefresh() {
        val now = ZonedDateTime.now()
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        alarmManager.setWindow(
            AlarmManager.RTC,
            midnight.toInstant().toEpochMilli() + MIDNIGHT_MARGIN_MILLIS,
            REFRESH_WINDOW_MILLIS,
            broadcast(WaterWidgetActionReceiver.ACTION_REFRESH),
        )
    }

    private fun broadcast(action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WaterWidgetActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Request codes apart from the reminder notifications, which use the same activity intent. */
    private fun activity(index: Int, request: LaunchRequest): PendingIntent = PendingIntent.getActivity(
        context,
        ACTIVITY_REQUEST_BASE + index,
        LaunchRequest.intent(context, request),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val DISABLED_ALPHA = 97
        const val ACTIVITY_REQUEST_BASE = 1000
        const val MIDNIGHT_MARGIN_MILLIS = 60_000L

        /** Smallest window Android allows for non-exact alarms. */
        val REFRESH_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(10)
    }
}

/** Placed, resized or restored widgets (and after a reboot): redraws them. */
class WaterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        runAsync(context) { it.update() }
    }

    override fun onDisabled(context: Context) {
        (context.applicationContext as PhotoCalApp).container.waterWidget.cancelMidnightRefresh()
    }
}

/**
 * − / + buttons (widget and water reminders) and the midnight refresh: explicit intents from
 * the widget, the notifications and AlarmManager only.
 */
class WaterWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // The repository redraws the widget after the change
            ACTION_ADD -> runAsync(context) { it.addGlass() }
            ACTION_REMOVE -> runAsync(context) { it.removeGlass() }
            ACTION_REFRESH -> runAsync(context) { it.update() }
        }
    }

    companion object {
        const val ACTION_ADD = "it.emanuelemelini.photocal.widget.ADD_GLASS"
        const val ACTION_REMOVE = "it.emanuelemelini.photocal.widget.REMOVE_GLASS"
        const val ACTION_REFRESH = "it.emanuelemelini.photocal.widget.REFRESH"
    }
}

private fun BroadcastReceiver.runAsync(context: Context, block: suspend (WaterWidget) -> Unit) {
    val container = (context.applicationContext as PhotoCalApp).container
    val pending = goAsync()
    container.applicationScope.launch {
        try {
            block(container.waterWidget)
        } finally {
            pending.finish()
        }
    }
}
