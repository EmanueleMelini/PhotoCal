package it.emanuelemelini.photocal.data.update

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.ArrayRes
import it.emanuelemelini.photocal.BuildConfig
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.prefs.SettingsRepository

/** Notes of one release; the texts are string arrays, so they follow the app language. */
data class ChangelogEntry(val versionCode: Int, val versionName: String, @ArrayRes val notesRes: Int)

object Changelog {
    /**
     * Newest first. Every release adds its entry here and a "changelog_x_y_z" array in both
     * strings.xml files: ChangelogTest fails when the current version is missing.
     */
    val entries = listOf(
        ChangelogEntry(9, "1.8.0", R.array.changelog_1_8_0),
        ChangelogEntry(8, "1.7.0", R.array.changelog_1_7_0),
        ChangelogEntry(7, "1.6.0", R.array.changelog_1_6_0),
        ChangelogEntry(6, "1.5.0", R.array.changelog_1_5_0),
        ChangelogEntry(5, "1.4.0", R.array.changelog_1_4_0),
        ChangelogEntry(4, "1.3.0", R.array.changelog_1_3_0),
        ChangelogEntry(3, "1.2.0", R.array.changelog_1_2_0),
        ChangelogEntry(2, "1.1.0", R.array.changelog_1_1_0),
    )

    /** Entries after [lastSeenCode] up to [currentCode], newest first. */
    fun between(lastSeenCode: Int, currentCode: Int): List<ChangelogEntry> =
        entries.filter { it.versionCode in (lastSeenCode + 1)..currentCode }
}

/** Shows the changelog once, at the first start after an update. */
class WhatsNew(private val context: Context, private val settingsRepository: SettingsRepository) {

    /** Notes still to show; empty after a fresh install or when already seen. */
    suspend fun pending(): List<ChangelogEntry> {
        val current = BuildConfig.VERSION_CODE
        val lastSeen = settingsRepository.lastSeenVersionCode()
        val notes = when {
            // Nothing new for someone who has just installed the app
            lastSeen == null && isFreshInstall() -> emptyList()
            // Update from a version without this key (1.2.0 or older): only the current notes
            lastSeen == null -> Changelog.between(current - 1, current)
            else -> Changelog.between(lastSeen, current)
        }
        // With nothing to show there is no dialog to close, so it's seen now
        if (notes.isEmpty() && lastSeen != current) markSeen()
        return notes
    }

    suspend fun markSeen() {
        settingsRepository.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
    }

    private fun isFreshInstall(): Boolean = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    } catch (_: PackageManager.NameNotFoundException) {
        true
    }
}
