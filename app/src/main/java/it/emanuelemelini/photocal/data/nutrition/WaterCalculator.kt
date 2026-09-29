package it.emanuelemelini.photocal.data.nutrition

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Suggested daily water to drink. Adequate intakes of total water (LARN 2014 / EFSA 2010):
 * 2.5 L for men and 2.0 L for women, about 20% of which comes from food. Heavier people get
 * 30 ml per kg when that is more, and physical activity adds a glass per level.
 * It is general guidance, not medical advice: the goal can always be typed by hand.
 */
object WaterCalculator {

    /** Share of the total water that comes from drinks (the rest comes from food). */
    private const val DRINKS_SHARE = 0.8

    private const val ML_PER_KG = 30

    private fun totalWaterMl(sex: Sex) = when (sex) {
        Sex.MALE -> 2500
        Sex.FEMALE -> 2000
        Sex.UNSPECIFIED -> 2250
    }

    /** Extra water for sweating during physical activity, averaged over the week. */
    private fun activityExtraMl(activity: ActivityLevel) = when (activity) {
        ActivityLevel.SEDENTARY -> 0
        ActivityLevel.LIGHT -> 200
        ActivityLevel.MODERATE -> 400
        ActivityLevel.ACTIVE -> 600
        ActivityLevel.VERY_ACTIVE -> 800
    }

    /** Suggested ml to drink per day, or null while sex or activity are missing. */
    fun suggestedMl(sex: Sex?, activity: ActivityLevel?, weightKg: Double?): Int? {
        sex ?: return null
        activity ?: return null
        val total = max(totalWaterMl(sex).toDouble(), (weightKg ?: 0.0) * ML_PER_KG)
        return (total * DRINKS_SHARE).roundToInt() + activityExtraMl(activity)
    }

    /** Glasses needed to drink at least [ml]. */
    fun glassesToReach(ml: Int, glassMl: Int): Int = max(1, ceil(ml.toDouble() / glassMl).toInt())

    /** Goal in glasses, as shown in the counter. */
    fun goalGlasses(goalMl: Int, glassMl: Int): Int = max(1, (goalMl.toDouble() / glassMl).roundToInt())

    /** Goal in ml as shown by the counter: a whole number of glasses. */
    fun goalMlInGlasses(goalMl: Int, glassMl: Int): Int = goalGlasses(goalMl, glassMl) * glassMl

    /** ml still missing to reach [share] of the goal (0 when reached). */
    fun missingMl(drunkMl: Int, goalMl: Int, glassMl: Int, share: Double): Int =
        max(0, (goalMlInGlasses(goalMl, glassMl) * share).roundToInt() - drunkMl)

    /** Glasses drunk: may have a decimal part if the glass size was changed during the day. */
    fun glasses(ml: Int, glassMl: Int): Double = ml.toDouble() / glassMl
}
