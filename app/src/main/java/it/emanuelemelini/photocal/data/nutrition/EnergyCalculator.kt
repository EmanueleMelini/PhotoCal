package it.emanuelemelini.photocal.data.nutrition

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class EnergyEstimate(
    /** Basal metabolic rate (Mifflin-St Jeor). */
    val bmr: Int,
    /** Daily energy expenditure: basal metabolism × activity factor. */
    val tdee: Int,
    /** Suggested daily kcal for the goal, after the safety limits. */
    val suggestedKcal: Int,
    /** true if the safety limits raised the suggestion above maintenance + goal delta. */
    val limited: Boolean,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

/**
 * Energy and macro estimate. It is a population formula, not medical advice: the UI always
 * lets the user type their own targets instead.
 */
object EnergyCalculator {

    /** Minimum suggested intake while losing weight (common dietetic guidance). */
    private fun minimumKcal(sex: Sex) = when (sex) {
        Sex.MALE -> 1500
        Sex.FEMALE -> 1200
        Sex.UNSPECIFIED -> 1350
    }

    /** A deficit is never larger than this share of the daily expenditure. */
    private const val MAX_DEFICIT_SHARE = 0.25

    private const val FAT_SHARE = 0.27

    fun estimate(profile: Profile, weightKg: Double?, currentYear: Int): EnergyEstimate? {
        val sex = profile.sex ?: return null
        val age = currentYear - (profile.birthYear ?: return null)
        val height = profile.heightCm ?: return null
        val activity = profile.activity ?: return null
        val weight = weightKg ?: return null

        val bmr = 10 * weight + 6.25 * height - 5 * age + sex.bmrOffset
        val tdee = bmr * activity.factor
        val target = tdee + profile.goal.kcalDelta

        val suggested = if (profile.goal.kcalDelta < 0) {
            // Losing weight: never below the minimum intake nor more than 25% under expenditure,
            // and never above maintenance (for very small expenditures the minimum is capped)
            val floor = max(tdee * (1 - MAX_DEFICIT_SHARE), min(minimumKcal(sex).toDouble(), tdee))
            max(target, floor)
        } else {
            target
        }
        val kcal = roundTo10(suggested)

        // Protein per kg of body weight (a bit more while losing), fat as a share of kcal,
        // carbohydrates for the rest
        val proteinPerKg = if (profile.goal.kcalDelta < 0) 1.8 else 1.6
        val protein = (weight * proteinPerKg).roundToInt()
        val fat = (kcal * FAT_SHARE / 9).roundToInt()
        val carbs = max(0, ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt())

        return EnergyEstimate(
            bmr = bmr.roundToInt(),
            tdee = roundTo10(tdee),
            suggestedKcal = kcal,
            limited = suggested > target + 0.5,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
        )
    }

    /** kcal from macros (4 / 4 / 9 kcal per gram), to compare with the kcal goal. */
    fun kcalFromMacros(proteinG: Int, carbsG: Int, fatG: Int): Int = proteinG * 4 + carbsG * 4 + fatG * 9

    private fun roundTo10(value: Double): Int = (value / 10).roundToInt() * 10
}
