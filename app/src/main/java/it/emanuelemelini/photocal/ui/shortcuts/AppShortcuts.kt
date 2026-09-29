package it.emanuelemelini.photocal.ui.shortcuts

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import it.emanuelemelini.photocal.AppIcon
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.PhotoCalApp
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.ui.LaunchRequest
import kotlinx.coroutines.flow.first

/**
 * Shortcuts shown when the app icon is long-pressed (and that can be dragged to the home
 * screen): meal photo, manual meal, +1 glass of water and, with a name in the profile, share
 * today.
 * Dynamic instead of XML shortcuts: labels follow the in-app language and the shortcuts can
 * move to the launcher alias of the chosen icon.
 */
object AppShortcuts {

    /**
     * Call from the foreground (background updates are rate-limited): app start, icon change,
     * profile name set or removed.
     */
    suspend fun publish(context: Context) {
        val settings = (context.applicationContext as PhotoCalApp).container.settingsRepository.settings.first()
        val canShare = settings.profile.name.isNotBlank()
        val res = AppLocale.localizedContext(context).resources
        val activity = AppIcon.current(context).launcherComponent(context)

        fun shortcut(id: String, @StringRes shortLabel: Int, @StringRes longLabel: Int, @DrawableRes icon: Int, intent: Intent) =
            ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(res.getString(shortLabel))
                .setLongLabel(res.getString(longLabel))
                .setIcon(IconCompat.createWithResource(context, icon))
                .setActivity(activity)
                // Shortcut intents need an action; LaunchRequest only reads the extras
                .setIntent(intent.setAction(Intent.ACTION_VIEW))
                .build()

        ShortcutManagerCompat.setDynamicShortcuts(
            context,
            listOf(
                shortcut(
                    ID_PHOTO, R.string.shortcut_photo_short, R.string.widget_add_photo, R.drawable.shortcut_photo,
                    LaunchRequest.intent(context, LaunchRequest.AddPhoto(meal = null)),
                ),
                shortcut(
                    ID_MANUAL, R.string.shortcut_manual_short, R.string.widget_add_manual, R.drawable.shortcut_manual,
                    LaunchRequest.intent(context, LaunchRequest.AddManual(meal = null)),
                ),
                shortcut(
                    ID_WATER, R.string.shortcut_water_short, R.string.shortcut_water_long, R.drawable.shortcut_water,
                    Intent(context, AddWaterShortcutActivity::class.java),
                ),
            ) + listOfNotNull(
                // Sharing needs a name: without it the shortcut isn't offered
                shortcut(
                    ID_SHARE, R.string.shortcut_share_short, R.string.shortcut_share_long, R.drawable.shortcut_share,
                    LaunchRequest.intent(context, LaunchRequest.ShareToday),
                ).takeIf { canShare },
            ),
        )
    }

    // Stored by the launcher (also for pinned shortcuts): don't rename them
    private const val ID_PHOTO = "meal_photo"
    private const val ID_MANUAL = "meal_manual"
    private const val ID_WATER = "water_glass"
    private const val ID_SHARE = "share_today"
}
