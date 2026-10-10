package it.emanuelemelini.photocal.data.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType
import com.google.firebase.crashlytics.FirebaseCrashlytics
import it.emanuelemelini.photocal.AppLanguage
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.BuildConfig
import it.emanuelemelini.photocal.data.ai.AiException
import it.emanuelemelini.photocal.data.ai.AiProvider
import it.emanuelemelini.photocal.data.db.Source
import it.emanuelemelini.photocal.data.prefs.Settings

/**
 * Usage statistics (Google Analytics for Firebase) and crash reports (Crashlytics), both off
 * until the user agrees (the manifest disables them at install). Only names of screens and
 * features are sent: never foods, amounts, weight, photos or other health data. Debug builds
 * never send anything, so development doesn't end up in the statistics.
 */
class Telemetry(context: Context) {
    private val analytics = FirebaseAnalytics.getInstance(context)
    private val crashlytics = FirebaseCrashlytics.getInstance()

    /** Choices last applied: [update] applies them again only when they change. */
    private var applied: Pair<Boolean, Boolean>? = null

    /**
     * Called with every new [Settings] (the first one at start), always in order: the choices
     * are applied before the properties, which are dropped while statistics are off.
     */
    fun update(settings: Settings) {
        val choices = (settings.usageStats ?: false) to (settings.crashReports ?: false)
        if (choices != applied) {
            apply(choices.first, choices.second)
            applied = choices
        }
        setUserProperties(settings)
    }

    private fun apply(usageStats: Boolean, crashReports: Boolean) {
        val stats = usageStats && !BuildConfig.DEBUG
        // Consent mode: statistics only, never advertising
        analytics.setConsent(
            mapOf(
                ConsentType.ANALYTICS_STORAGE to if (stats) ConsentStatus.GRANTED else ConsentStatus.DENIED,
                ConsentType.AD_STORAGE to ConsentStatus.DENIED,
                ConsentType.AD_USER_DATA to ConsentStatus.DENIED,
                ConsentType.AD_PERSONALIZATION to ConsentStatus.DENIED,
            )
        )
        analytics.setAnalyticsCollectionEnabled(stats)
        // A new random id if the user agrees again later: the old data can't be linked to it
        if (!stats) analytics.resetAnalyticsData()

        val crashes = crashReports && !BuildConfig.DEBUG
        crashlytics.isCrashlyticsCollectionEnabled = crashes
        // Crashes are kept on the phone while collection is off: they must never be sent
        if (!crashes) crashlytics.deleteUnsentReports()
    }

    /** Settings worth comparing in the statistics (which AI, which theme...), no personal values. */
    private fun setUserProperties(settings: Settings) {
        analytics.setUserProperty(PROPERTY_AI_PROVIDER, settings.aiProvider.prefKey)
        analytics.setUserProperty(PROPERTY_THEME, settings.themeMode.name.lowercase())
        analytics.setUserProperty(PROPERTY_HEALTH_CONNECT, settings.healthConnected.toString())
        analytics.setUserProperty(PROPERTY_REMINDERS, settings.reminders.values.count { it.enabled }.toString())
        setLanguage(AppLocale.language)
    }

    /** Also from MainActivity: a language change recreates it without changing the settings. */
    fun setLanguage(language: AppLanguage) {
        analytics.setUserProperty(PROPERTY_LANGUAGE, language.tag)
    }

    /** [name]: fixed screen name, never values typed by the user. */
    fun logScreen(name: String) {
        analytics.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, name)
            },
        )
    }

    /** A new diary entry: only how it was added. */
    fun logFoodAdded(source: Source) {
        analytics.logEvent(EVENT_FOOD_ADDED, Bundle().apply { putString(PARAM_SOURCE, source.name.lowercase()) })
    }

    /** One meal analysis by the AI ([photo] or text) and how it ended; [error] null = success. */
    fun logAiRequest(provider: AiProvider, photo: Boolean, error: AiException?) {
        analytics.logEvent(
            EVENT_AI_REQUEST,
            Bundle().apply {
                putString(PARAM_PROVIDER, provider.prefKey)
                putString(PARAM_KIND, if (photo) "photo" else "text")
                putString(PARAM_OUTCOME, error?.let(::outcome) ?: "success")
            },
        )
    }

    /** Fixed names: the class names are shortened by R8 in release builds. */
    private fun outcome(error: AiException): String = when (error) {
        is AiException.MissingApiKey -> "missing_api_key"
        is AiException.InvalidApiKey -> "invalid_api_key"
        is AiException.MissingModel -> "missing_model"
        is AiException.InvalidBaseUrl -> "invalid_base_url"
        is AiException.ModelNotFound -> "model_not_found"
        is AiException.RateLimited -> "rate_limited"
        is AiException.Network -> "network"
        is AiException.InvalidResponse -> "invalid_response"
        is AiException.Blocked -> "blocked"
        is AiException.Http -> "http_${error.code}"
    }

    private companion object {
        const val EVENT_FOOD_ADDED = "food_added"
        const val EVENT_AI_REQUEST = "ai_request"
        const val PARAM_SOURCE = "source"
        const val PARAM_PROVIDER = "provider"
        const val PARAM_KIND = "kind"
        const val PARAM_OUTCOME = "outcome"
        const val PROPERTY_AI_PROVIDER = "ai_provider"
        const val PROPERTY_THEME = "theme"
        const val PROPERTY_LANGUAGE = "app_language"
        const val PROPERTY_HEALTH_CONNECT = "health_connect"
        const val PROPERTY_REMINDERS = "reminders_on"
    }
}
