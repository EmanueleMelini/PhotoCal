package it.emanuelemelini.photocal.ui

import android.content.Context
import android.content.Intent
import it.emanuelemelini.photocal.BuildConfig
import it.emanuelemelini.photocal.MainActivity
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.data.share.ShareBuilder

/** What the app should open when started from a notification, a shortcut or a share link. */
sealed interface LaunchRequest {
    data object OpenToday : LaunchRequest
    data object OpenHistory : LaunchRequest
    data class AddPhoto(val meal: MealType?) : LaunchRequest
    data class AddManual(val meal: MealType?) : LaunchRequest

    /** Share options of today (app shortcut). */
    data object ShareToday : LaunchRequest

    /** A shared day: [payload] is the part of the link after '#', still to be checked. */
    data class OpenShared(val payload: String) : LaunchRequest

    /** Health Connect shows why PhotoCal asks for its permissions. */
    data object HealthPrivacy : LaunchRequest

    /** "New version" notification: checks again and shows the update dialog. */
    data object ShowUpdate : LaunchRequest

    companion object {
        private const val EXTRA_ACTION = "launch_action"
        private const val EXTRA_MEAL = "launch_meal"
        private const val EXTRA_PAYLOAD = "launch_payload"

        /** Health Connect permission rationale: up to Android 13 and from Android 14 (activity-alias). */
        private val HEALTH_RATIONALE_ACTIONS = setOf(
            "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE",
            "android.intent.action.VIEW_PERMISSION_USAGE",
        )

        /** Intent that brings the existing MainActivity to front (singleTop) with the request. */
        fun intent(context: Context, request: LaunchRequest): Intent {
            val (action, meal) = when (request) {
                OpenToday -> "today" to null
                OpenHistory -> "history" to null
                is AddPhoto -> "photo" to request.meal
                is AddManual -> "manual" to request.meal
                ShareToday -> "share_today" to null
                is OpenShared -> "shared" to null
                HealthPrivacy -> "health_privacy" to null
                ShowUpdate -> "show_update" to null
            }
            return Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ACTION, action)
                .putExtra(EXTRA_MEAL, meal?.name)
                .putExtra(EXTRA_PAYLOAD, (request as? OpenShared)?.payload)
        }

        fun from(intent: Intent?): LaunchRequest? {
            if (intent?.action in HEALTH_RATIONALE_ACTIONS) return HealthPrivacy
            // App Link: https://<share host>/d#<payload>
            val data = intent?.data
            if (intent?.action == Intent.ACTION_VIEW && data != null) {
                if (data.scheme == "https" && data.host == BuildConfig.SHARE_HOST && data.path == ShareBuilder.PATH) {
                    return OpenShared(data.encodedFragment.orEmpty())
                }
                return null
            }
            val meal = MealType.entries.find { it.name == intent?.getStringExtra(EXTRA_MEAL) }
            return when (intent?.getStringExtra(EXTRA_ACTION)) {
                "today" -> OpenToday
                "history" -> OpenHistory
                "photo" -> AddPhoto(meal)
                "manual" -> AddManual(meal)
                "share_today" -> ShareToday
                "shared" -> OpenShared(intent.getStringExtra(EXTRA_PAYLOAD).orEmpty())
                "health_privacy" -> HealthPrivacy
                "show_update" -> ShowUpdate
                else -> null
            }
        }
    }
}
