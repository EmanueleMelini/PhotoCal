package it.emanuelemelini.photocal.data.nutrition

import androidx.annotation.StringRes
import it.emanuelemelini.photocal.R

/** The constant names of these enums are stored in the preferences: don't rename them. */
enum class Sex(@StringRes val labelRes: Int, /** Mifflin-St Jeor constant, in kcal. */ val bmrOffset: Int) {
    MALE(R.string.sex_male, 5),
    FEMALE(R.string.sex_female, -161),

    /** Midpoint of the two constants. */
    UNSPECIFIED(R.string.sex_unspecified, -78),
}

enum class ActivityLevel(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    /** Multiplier from basal metabolism to daily energy expenditure. */
    val factor: Double,
) {
    SEDENTARY(R.string.activity_sedentary, R.string.activity_sedentary_hint, 1.2),
    LIGHT(R.string.activity_light, R.string.activity_light_hint, 1.375),
    MODERATE(R.string.activity_moderate, R.string.activity_moderate_hint, 1.55),
    ACTIVE(R.string.activity_active, R.string.activity_active_hint, 1.725),
    VERY_ACTIVE(R.string.activity_very_active, R.string.activity_very_active_hint, 1.9),
}

enum class WeightGoal(@StringRes val labelRes: Int, /** Daily kcal change from maintenance. */ val kcalDelta: Int) {
    LOSE(R.string.goal_lose, -500),
    LOSE_SLOWLY(R.string.goal_lose_slowly, -250),
    MAINTAIN(R.string.goal_maintain, 0),
    GAIN(R.string.goal_gain, 250),
}

/** Data for the energy estimate; the weight comes from the weight log. */
data class Profile(
    val sex: Sex? = null,
    val birthYear: Int? = null,
    val heightCm: Int? = null,
    val activity: ActivityLevel? = null,
    val goal: WeightGoal = WeightGoal.MAINTAIN,
)

/** Daily targets chosen by the user (suggested or typed); macros are optional. */
data class DailyGoals(
    val kcal: Int,
    val proteinG: Int? = null,
    val carbsG: Int? = null,
    val fatG: Int? = null,
)
