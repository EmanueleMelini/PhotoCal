package it.emanuelemelini.photocal.data.db

import androidx.annotation.StringRes
import it.emanuelemelini.photocal.R
import java.time.LocalTime

/** The constant names are stored in the database: renaming them needs a migration. */
enum class MealType(@StringRes val labelRes: Int) {
    BREAKFAST(R.string.meal_breakfast),
    LUNCH(R.string.meal_lunch),
    DINNER(R.string.meal_dinner),
    SNACK(R.string.meal_snack);

    companion object {
        /** Default meal suggested from the current time. */
        fun suggestedFor(time: LocalTime = LocalTime.now()): MealType = when (time.hour) {
            in 5..10 -> BREAKFAST
            in 11..14 -> LUNCH
            in 18..22 -> DINNER
            else -> SNACK
        }
    }
}
