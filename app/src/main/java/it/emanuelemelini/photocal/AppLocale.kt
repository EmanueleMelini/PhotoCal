package it.emanuelemelini.photocal

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Languages the app is translated into; the first one is the default (values/). */
enum class AppLanguage(val tag: String, val englishName: String) {
    ITALIAN("it", "Italian"),
    ENGLISH("en", "English"),
}

/**
 * Locale currently used by the app, for formatting and for the language of AI answers.
 * Updated by MainActivity from its configuration, which already reflects the in-app choice.
 */
object AppLocale {
    @Volatile
    var current: Locale = Locale.getDefault()

    /** Language actually shown by the UI: untranslated languages fall back to Italian. */
    val language: AppLanguage
        get() = AppLanguage.entries.find { it.tag == current.language } ?: AppLanguage.ITALIAN

    /** Language chosen in the app, or null when it follows the device language. */
    fun chosen(): AppLanguage? {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return null
        return AppLanguage.entries.find { it.tag == locales[0]?.language }
    }

    /** null = follow the device language. The activity is recreated with the new language. */
    fun choose(language: AppLanguage?) {
        AppCompatDelegate.setApplicationLocales(
            language?.let { LocaleListCompat.forLanguageTags(it.tag) } ?: LocaleListCompat.getEmptyLocaleList()
        )
    }
}
