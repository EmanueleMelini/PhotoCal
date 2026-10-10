package it.emanuelemelini.photocal.data.push

import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import it.emanuelemelini.photocal.AppLanguage
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.BuildConfig
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import it.emanuelemelini.photocal.ui.LaunchRequest
import kotlinx.coroutines.flow.first

/**
 * PhotoCal news, sent from the Firebase console (Messaging) to topics: [ALL] reaches every
 * install, "news-it" / "news-en" only who uses the app in that language, [DEBUG] only debug
 * builds (to try a message before sending it to everyone). No server and no account: the
 * topic is all Firebase knows.
 */
class NewsTopics(private val settingsRepository: SettingsRepository) {

    /** At every start (the language may have changed) and when the switch in Settings changes. */
    suspend fun sync() {
        val enabled = settingsRepository.settings.first().newsNotifications
        val wanted = if (!enabled) emptySet() else buildSet {
            add(ALL)
            add(languageTopic(AppLocale.language))
            if (BuildConfig.DEBUG) add(DEBUG)
        }
        val messaging = FirebaseMessaging.getInstance()
        // Subscribing again is harmless; Firebase retries by itself when offline
        (AppLanguage.entries.map(::languageTopic) + ALL + DEBUG).forEach { topic ->
            val task = if (topic in wanted) messaging.subscribeToTopic(topic) else messaging.unsubscribeFromTopic(topic)
            // e.g. no Google Play services on the phone: there are no push notifications at all
            task.addOnFailureListener { Log.i(TAG, "Topic $topic not updated: ${it.message}") }
        }
    }

    companion object {
        const val ALL = "news"
        const val DEBUG = "news-debug"
        private const val TAG = "NewsTopics"

        fun languageTopic(language: AppLanguage) = "news-${language.tag}"
    }
}

/**
 * With the app in the background the system shows the notification by itself (channel and
 * icon from the manifest); with the app in the foreground it comes here and is shown the same way.
 */
class NewsMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        val title = notification.title ?: getString(R.string.app_name)
        val text = notification.body.orEmpty()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            LaunchRequest.intent(this, LaunchRequest.OpenToday),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * Topics don't need the token: nothing to send anywhere. Kept (although deprecated in the
     * SDK) because lint asks every messaging service to handle token refreshes.
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) = Unit

    companion object {
        /** Also the default channel of the manifest, for the notifications shown by the system. */
        const val CHANNEL = "news"

        /** A new message replaces the previous one still shown. */
        private const val NOTIFICATION_ID = 2000
    }
}
