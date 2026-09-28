package it.emanuelemelini.photocal.data.prefs

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import it.emanuelemelini.photocal.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** The constant names are stored in the preferences: don't rename them. */
enum class ThemeMode(@StringRes val labelRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
}

data class Settings(
    val dailyKcalGoal: Int = SettingsRepository.DEFAULT_KCAL_GOAL,
    val geminiApiKey: String = "",
    val geminiModel: String = SettingsRepository.DEFAULT_GEMINI_MODEL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You colors taken from the wallpaper (Android 12+). */
    val dynamicColor: Boolean = true,
    /** Kcal and macros from the CREA tables when the AI finds the matching food. */
    val useCrea: Boolean = true,
)

class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            dailyKcalGoal = prefs[KCAL_GOAL] ?: DEFAULT_KCAL_GOAL,
            geminiApiKey = prefs[GEMINI_API_KEY].orEmpty(),
            geminiModel = prefs[GEMINI_MODEL]?.takeIf { it.isNotBlank() } ?: DEFAULT_GEMINI_MODEL,
            themeMode = prefs[THEME_MODE]?.let { name -> ThemeMode.entries.find { it.name == name } } ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: true,
            useCrea = prefs[USE_CREA] ?: true,
        )
    }

    suspend fun save(kcalGoal: Int, geminiApiKey: String, geminiModel: String) {
        context.dataStore.edit {
            it[KCAL_GOAL] = kcalGoal
            it[GEMINI_API_KEY] = geminiApiKey
            it[GEMINI_MODEL] = geminiModel
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setUseCrea(enabled: Boolean) {
        context.dataStore.edit { it[USE_CREA] = enabled }
    }

    companion object {
        const val DEFAULT_KCAL_GOAL = 2000

        /** Latest stable Flash model with a free tier (checked on ai.google.dev, September 2026). */
        const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"

        /** Suggestions shown in Settings. */
        val SUGGESTED_GEMINI_MODELS = listOf(DEFAULT_GEMINI_MODEL, "gemini-3.5-flash-lite")

        private val KCAL_GOAL = intPreferencesKey("daily_kcal_goal")
        private val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val USE_CREA = booleanPreferencesKey("use_crea")
    }
}
