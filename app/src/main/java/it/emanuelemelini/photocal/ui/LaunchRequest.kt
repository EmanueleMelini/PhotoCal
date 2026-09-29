package it.emanuelemelini.photocal.ui

import android.content.Context
import android.content.Intent
import it.emanuelemelini.photocal.MainActivity
import it.emanuelemelini.photocal.data.db.MealType

/** What the app should open when started from a notification. */
sealed interface LaunchRequest {
    data object OpenToday : LaunchRequest
    data object OpenHistory : LaunchRequest
    data class AddPhoto(val meal: MealType?) : LaunchRequest
    data class AddManual(val meal: MealType?) : LaunchRequest

    companion object {
        private const val EXTRA_ACTION = "launch_action"
        private const val EXTRA_MEAL = "launch_meal"

        /** Intent that brings the existing MainActivity to front (singleTop) with the request. */
        fun intent(context: Context, request: LaunchRequest): Intent {
            val (action, meal) = when (request) {
                OpenToday -> "today" to null
                OpenHistory -> "history" to null
                is AddPhoto -> "photo" to request.meal
                is AddManual -> "manual" to request.meal
            }
            return Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ACTION, action)
                .putExtra(EXTRA_MEAL, meal?.name)
        }

        fun from(intent: Intent?): LaunchRequest? {
            val meal = MealType.entries.find { it.name == intent?.getStringExtra(EXTRA_MEAL) }
            return when (intent?.getStringExtra(EXTRA_ACTION)) {
                "today" -> OpenToday
                "history" -> OpenHistory
                "photo" -> AddPhoto(meal)
                "manual" -> AddManual(meal)
                else -> null
            }
        }
    }
}
