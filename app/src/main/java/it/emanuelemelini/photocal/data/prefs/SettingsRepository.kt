package it.emanuelemelini.photocal.data.prefs

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.ai.AiConfig
import it.emanuelemelini.photocal.data.ai.AiProvider
import it.emanuelemelini.photocal.data.nutrition.ActivityLevel
import it.emanuelemelini.photocal.data.nutrition.DailyGoals
import it.emanuelemelini.photocal.data.nutrition.Profile
import it.emanuelemelini.photocal.data.nutrition.Sex
import it.emanuelemelini.photocal.data.nutrition.WeightGoal
import it.emanuelemelini.photocal.data.reminders.ReminderConfig
import it.emanuelemelini.photocal.data.reminders.ReminderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.dataStore by preferencesDataStore(name = "settings")

/** The constant names are stored in the preferences: don't rename them. */
enum class ThemeMode(@StringRes val labelRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),

    /** Medium purple surfaces with pink accents, whatever the system mode. */
    PURPLE(R.string.theme_purple),

    /** Deep purple surfaces with pink accents, whatever the system mode. */
    PURPLE_DARK(R.string.theme_purple_dark);

    /** Themes with their own palette, where dynamic colors don't apply. */
    val isPurple: Boolean get() = this == PURPLE || this == PURPLE_DARK
}

data class Settings(
    val dailyKcalGoal: Int = SettingsRepository.DEFAULT_KCAL_GOAL,
    /** Optional macro targets in grams (typed or suggested). */
    val proteinGoalG: Int? = null,
    val carbsGoalG: Int? = null,
    val fatGoalG: Int? = null,
    val waterGoalMl: Int = SettingsRepository.DEFAULT_WATER_GOAL_ML,
    val glassMl: Int = SettingsRepository.DEFAULT_GLASS_ML,
    val profile: Profile = Profile(),
    /** AI used for photos and text estimates. */
    val aiProvider: AiProvider = AiProvider.GEMINI,
    /** API key of each AI: they stay on this phone, never in backups. */
    val aiApiKeys: Map<AiProvider, String> = emptyMap(),
    /** Model of each AI; a missing one means the default model. */
    val aiModels: Map<AiProvider, String> = emptyMap(),
    /** Base URL of the OpenAI-compatible service, e.g. https://openrouter.ai/api/v1. */
    val compatibleBaseUrl: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You colors taken from the wallpaper (Android 12+). */
    val dynamicColor: Boolean = true,
    /** Kcal and macros from the CREA tables when the AI finds the matching food. */
    val useCrea: Boolean = true,
    /** All reminders are off until the user turns them on. */
    val reminders: Map<ReminderType, ReminderConfig> = ReminderType.entries.associateWith { ReminderConfig(it) },
    /** Health Connect turned on by the user (the permissions can still be revoked from Android). */
    val healthConnected: Boolean = false,
    /** Weight and water are written to Health Connect. */
    val healthWrite: Boolean = true,
    /** Active calories burned are added to the daily kcal goal. */
    val healthAddBurned: Boolean = false,
) {
    fun aiApiKey(provider: AiProvider): String = aiApiKeys[provider].orEmpty()

    fun aiModel(provider: AiProvider): String = aiModels[provider]?.takeIf { it.isNotBlank() } ?: provider.defaultModel

    fun aiConfig(provider: AiProvider) = AiConfig(aiApiKey(provider), aiModel(provider), compatibleBaseUrl)

    /** The chosen AI has what it needs to be called (the service can still reject it). */
    val aiConfigured: Boolean
        get() = if (aiProvider.requiresApiKey) aiApiKey(aiProvider).isNotBlank()
        else compatibleBaseUrl.isNotBlank() && aiModel(aiProvider).isNotBlank()
}

class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            dailyKcalGoal = prefs[KCAL_GOAL] ?: DEFAULT_KCAL_GOAL,
            proteinGoalG = prefs[PROTEIN_GOAL],
            carbsGoalG = prefs[CARBS_GOAL],
            fatGoalG = prefs[FAT_GOAL],
            waterGoalMl = prefs[WATER_GOAL] ?: DEFAULT_WATER_GOAL_ML,
            glassMl = prefs[GLASS_ML] ?: DEFAULT_GLASS_ML,
            profile = Profile(
                name = prefs[PROFILE_NAME].orEmpty(),
                sex = prefs[SEX]?.let { name -> Sex.entries.find { it.name == name } },
                birthYear = prefs[BIRTH_YEAR],
                heightCm = prefs[HEIGHT_CM],
                activity = prefs[ACTIVITY]?.let { name -> ActivityLevel.entries.find { it.name == name } },
                goal = prefs[WEIGHT_GOAL]?.let { name -> WeightGoal.entries.find { it.name == name } } ?: WeightGoal.MAINTAIN,
            ),
            aiProvider = prefs[AI_PROVIDER]?.let { name -> AiProvider.entries.find { it.name == name } } ?: AiProvider.GEMINI,
            aiApiKeys = AiProvider.entries.mapNotNull { provider ->
                prefs[apiKeyKey(provider)]?.takeIf { it.isNotEmpty() }?.let { provider to it }
            }.toMap(),
            aiModels = AiProvider.entries.mapNotNull { provider ->
                prefs[modelKey(provider)]?.takeIf { it.isNotBlank() }?.let { provider to it }
            }.toMap(),
            compatibleBaseUrl = prefs[COMPATIBLE_BASE_URL].orEmpty(),
            themeMode = prefs[THEME_MODE]?.let { name -> ThemeMode.entries.find { it.name == name } } ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: true,
            useCrea = prefs[USE_CREA] ?: true,
            reminders = ReminderType.entries.associateWith { type ->
                ReminderConfig(
                    type = type,
                    enabled = prefs[reminderEnabledKey(type)] ?: false,
                    time = prefs[reminderTimeKey(type)]?.let { LocalTime.ofSecondOfDay(it * 60L) } ?: type.defaultTime,
                )
            },
            healthConnected = prefs[HEALTH_CONNECTED] ?: false,
            healthWrite = prefs[HEALTH_WRITE] ?: true,
            healthAddBurned = prefs[HEALTH_ADD_BURNED] ?: false,
        )
    }

    /** The chosen AI with the key and model of every AI, so switching back keeps them. */
    suspend fun saveAi(
        provider: AiProvider,
        apiKeys: Map<AiProvider, String>,
        models: Map<AiProvider, String>,
        compatibleBaseUrl: String,
    ) {
        context.dataStore.edit { prefs ->
            prefs[AI_PROVIDER] = provider.name
            AiProvider.entries.forEach { p ->
                prefs.setOrRemove(apiKeyKey(p), apiKeys[p]?.trim()?.takeIf { it.isNotEmpty() })
                prefs.setOrRemove(modelKey(p), models[p]?.trim()?.takeIf { it.isNotEmpty() })
            }
            prefs.setOrRemove(COMPATIBLE_BASE_URL, compatibleBaseUrl.trim().takeIf { it.isNotEmpty() })
        }
    }

    suspend fun saveProfile(profile: Profile) {
        context.dataStore.edit { prefs ->
            prefs.setOrRemove(PROFILE_NAME, profile.name.trim().takeIf { it.isNotEmpty() })
            prefs.setOrRemove(SEX, profile.sex?.name)
            prefs.setOrRemove(BIRTH_YEAR, profile.birthYear)
            prefs.setOrRemove(HEIGHT_CM, profile.heightCm)
            prefs.setOrRemove(ACTIVITY, profile.activity?.name)
            prefs[WEIGHT_GOAL] = profile.goal.name
        }
    }

    suspend fun saveGoals(goals: DailyGoals) {
        context.dataStore.edit { prefs ->
            prefs[KCAL_GOAL] = goals.kcal
            prefs.setOrRemove(PROTEIN_GOAL, goals.proteinG)
            prefs.setOrRemove(CARBS_GOAL, goals.carbsG)
            prefs.setOrRemove(FAT_GOAL, goals.fatG)
            prefs[WATER_GOAL] = goals.waterMl
            prefs[GLASS_ML] = goals.glassMl
        }
    }

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
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

    suspend fun setReminderEnabled(type: ReminderType, enabled: Boolean) {
        context.dataStore.edit { it[reminderEnabledKey(type)] = enabled }
    }

    suspend fun setReminderTime(type: ReminderType, time: LocalTime) {
        context.dataStore.edit { it[reminderTimeKey(type)] = time.hour * 60 + time.minute }
    }

    suspend fun setHealthConnected(connected: Boolean) {
        context.dataStore.edit { it[HEALTH_CONNECTED] = connected }
    }

    suspend fun setHealthWrite(enabled: Boolean) {
        context.dataStore.edit { it[HEALTH_WRITE] = enabled }
    }

    suspend fun setHealthAddBurned(enabled: Boolean) {
        context.dataStore.edit { it[HEALTH_ADD_BURNED] = enabled }
    }

    /**
     * Settings from a backup, in a single write. The AI API keys, the update checks and the
     * Health Connect link (its permissions belong to this phone) are left as they are.
     */
    suspend fun restore(settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs[KCAL_GOAL] = settings.dailyKcalGoal
            prefs.setOrRemove(PROTEIN_GOAL, settings.proteinGoalG)
            prefs.setOrRemove(CARBS_GOAL, settings.carbsGoalG)
            prefs.setOrRemove(FAT_GOAL, settings.fatGoalG)
            prefs[WATER_GOAL] = settings.waterGoalMl
            prefs[GLASS_ML] = settings.glassMl
            val profile = settings.profile
            prefs.setOrRemove(PROFILE_NAME, profile.name.trim().takeIf { it.isNotEmpty() })
            prefs.setOrRemove(SEX, profile.sex?.name)
            prefs.setOrRemove(BIRTH_YEAR, profile.birthYear)
            prefs.setOrRemove(HEIGHT_CM, profile.heightCm)
            prefs.setOrRemove(ACTIVITY, profile.activity?.name)
            prefs[WEIGHT_GOAL] = profile.goal.name
            prefs[AI_PROVIDER] = settings.aiProvider.name
            AiProvider.entries.forEach { provider -> prefs.setOrRemove(modelKey(provider), settings.aiModels[provider]) }
            prefs.setOrRemove(COMPATIBLE_BASE_URL, settings.compatibleBaseUrl.takeIf { it.isNotBlank() })
            prefs[THEME_MODE] = settings.themeMode.name
            prefs[DYNAMIC_COLOR] = settings.dynamicColor
            prefs[USE_CREA] = settings.useCrea
            settings.reminders.values.forEach { config ->
                prefs[reminderEnabledKey(config.type)] = config.enabled
                prefs[reminderTimeKey(config.type)] = config.time.hour * 60 + config.time.minute
            }
            prefs[HEALTH_WRITE] = settings.healthWrite
            prefs[HEALTH_ADD_BURNED] = settings.healthAddBurned
        }
    }

    /** Epoch day of the last successful update check (not a setting: kept out of [Settings]). */
    suspend fun lastUpdateCheckDay(): Long? = context.dataStore.data.first()[LAST_UPDATE_CHECK]

    suspend fun setLastUpdateCheckDay(epochDay: Long) {
        context.dataStore.edit { it[LAST_UPDATE_CHECK] = epochDay }
    }

    /** Last version announced by the "new version" reminder (not a setting: kept out of [Settings]). */
    suspend fun lastNotifiedUpdate(): String? = context.dataStore.data.first()[LAST_NOTIFIED_UPDATE]

    suspend fun setLastNotifiedUpdate(version: String) {
        context.dataStore.edit { it[LAST_NOTIFIED_UPDATE] = version }
    }

    /** versionCode whose changelog was last shown; null before 1.3.0 and on a fresh install. */
    suspend fun lastSeenVersionCode(): Int? = context.dataStore.data.first()[LAST_SEEN_VERSION]

    suspend fun setLastSeenVersionCode(versionCode: Int) {
        context.dataStore.edit { it[LAST_SEEN_VERSION] = versionCode }
    }

    companion object {
        const val DEFAULT_KCAL_GOAL = 2000

        /** 8 glasses of 200 ml, the common advice for adults. */
        const val DEFAULT_WATER_GOAL_ML = 1600

        /** Same as the "glass" unit of the manual entry. */
        const val DEFAULT_GLASS_ML = 200

        private val KCAL_GOAL = intPreferencesKey("daily_kcal_goal")
        private val PROTEIN_GOAL = intPreferencesKey("protein_goal_g")
        private val CARBS_GOAL = intPreferencesKey("carbs_goal_g")
        private val FAT_GOAL = intPreferencesKey("fat_goal_g")
        private val WATER_GOAL = intPreferencesKey("water_goal_ml")
        private val GLASS_ML = intPreferencesKey("glass_ml")
        private val PROFILE_NAME = stringPreferencesKey("profile_name")
        private val SEX = stringPreferencesKey("profile_sex")
        private val BIRTH_YEAR = intPreferencesKey("profile_birth_year")
        private val HEIGHT_CM = intPreferencesKey("profile_height_cm")
        private val ACTIVITY = stringPreferencesKey("profile_activity")
        private val WEIGHT_GOAL = stringPreferencesKey("profile_weight_goal")
        private val AI_PROVIDER = stringPreferencesKey("ai_provider")
        private val COMPATIBLE_BASE_URL = stringPreferencesKey("openai_compatible_base_url")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val USE_CREA = booleanPreferencesKey("use_crea")
        private val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_day")
        private val LAST_SEEN_VERSION = intPreferencesKey("last_seen_version_code")
        private val LAST_NOTIFIED_UPDATE = stringPreferencesKey("last_notified_update")
        private val HEALTH_CONNECTED = booleanPreferencesKey("health_connected")
        private val HEALTH_WRITE = booleanPreferencesKey("health_write")
        private val HEALTH_ADD_BURNED = booleanPreferencesKey("health_add_burned")

        /** "gemini_api_key" and "gemini_model" are the keys of the versions before 1.5.0. */
        private fun apiKeyKey(provider: AiProvider) = stringPreferencesKey("${provider.prefKey}_api_key")

        private fun modelKey(provider: AiProvider) = stringPreferencesKey("${provider.prefKey}_model")

        private fun reminderEnabledKey(type: ReminderType) =
            booleanPreferencesKey("reminder_${type.name.lowercase()}_enabled")

        /** Minutes after midnight. */
        private fun reminderTimeKey(type: ReminderType) =
            intPreferencesKey("reminder_${type.name.lowercase()}_time")
    }
}
