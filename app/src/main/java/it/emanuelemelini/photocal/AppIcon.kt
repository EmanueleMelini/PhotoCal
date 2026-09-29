package it.emanuelemelini.photocal

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Launcher icons, one activity-alias each in the manifest (".LauncherGreen" etc.).
 * Exactly one alias is enabled: the launcher shows its icon.
 */
enum class AppIcon(
    private val aliasName: String,
    @StringRes val labelRes: Int,
    /** Background of the adaptive icon, used for the preview in Settings. */
    @DrawableRes val backgroundRes: Int,
) {
    GREEN(".LauncherGreen", R.string.app_icon_green, R.drawable.ic_launcher_background),
    BLACK(".LauncherBlack", R.string.app_icon_black, R.drawable.ic_launcher_background_black),
    PURPLE(".LauncherPurple", R.string.app_icon_purple, R.drawable.ic_launcher_background_purple);

    private fun component(context: Context) = ComponentName(context.packageName, context.packageName + aliasName)

    companion object {
        /** The icon enabled in the manifest before any choice. */
        private val DEFAULT = GREEN

        fun current(context: Context): AppIcon {
            val packageManager = context.packageManager
            return entries.firstOrNull { icon ->
                when (packageManager.getComponentEnabledSetting(icon.component(context))) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == DEFAULT
                    else -> false
                }
            } ?: DEFAULT
        }

        /** Enables the chosen alias first, so there is never a moment without a launcher entry. */
        fun select(context: Context, icon: AppIcon) {
            val packageManager = context.packageManager
            packageManager.setComponentEnabledSetting(
                icon.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            entries.filter { it != icon }.forEach {
                packageManager.setComponentEnabledSetting(
                    it.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
