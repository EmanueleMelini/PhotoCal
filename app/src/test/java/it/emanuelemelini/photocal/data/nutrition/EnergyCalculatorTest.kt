package it.emanuelemelini.photocal.data.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyCalculatorTest {

    private val man = Profile(Sex.MALE, birthYear = 1990, heightCm = 180, activity = ActivityLevel.MODERATE)

    @Test
    fun mifflinStJeorForAMan() {
        // 10*80 + 6.25*180 - 5*36 + 5 = 1750; x1.55 = 2712.5
        val estimate = EnergyCalculator.estimate(man, weightKg = 80.0, currentYear = 2026)!!
        assertEquals(1750, estimate.bmr)
        assertEquals(2710, estimate.tdee)
        assertEquals(2710, estimate.suggestedKcal)
        assertFalse(estimate.limited)
    }

    @Test
    fun womanConstantIsMinus161() {
        val woman = man.copy(sex = Sex.FEMALE)
        assertEquals(1584, EnergyCalculator.estimate(woman, 80.0, 2026)!!.bmr)
    }

    @Test
    fun unspecifiedSexUsesTheMidpoint() {
        val person = man.copy(sex = Sex.UNSPECIFIED)
        assertEquals(1667, EnergyCalculator.estimate(person, 80.0, 2026)!!.bmr)
    }

    @Test
    fun goalChangesTheSuggestion() {
        val losing = EnergyCalculator.estimate(man.copy(goal = WeightGoal.LOSE), 80.0, 2026)!!
        assertEquals(2210, losing.suggestedKcal)
        val gaining = EnergyCalculator.estimate(man.copy(goal = WeightGoal.GAIN), 80.0, 2026)!!
        assertEquals(2960, gaining.suggestedKcal)
    }

    @Test
    fun deficitNeverGoesBelowTheMinimumIntake() {
        // Small sedentary woman: 10*50 + 6.25*155 - 5*56 - 161 = 1027.75; x1.2 = 1233.3
        val profile = Profile(Sex.FEMALE, birthYear = 1970, heightCm = 155, activity = ActivityLevel.SEDENTARY, goal = WeightGoal.LOSE)
        val estimate = EnergyCalculator.estimate(profile, 50.0, 2026)!!
        // Minimum 1200 kcal (below maintenance), instead of 733
        assertEquals(1200, estimate.suggestedKcal)
        assertTrue(estimate.limited)
    }

    @Test
    fun deficitIsAtMostAQuarterOfTheExpenditure() {
        // 10*55 + 6.25*160 - 5*30 - 161 = 1239; x1.375 = 1703.6
        // -500 would give 1204, but -25% stops at 1277.7 (above the 1200 minimum)
        val profile = Profile(Sex.FEMALE, birthYear = 1996, heightCm = 160, activity = ActivityLevel.LIGHT, goal = WeightGoal.LOSE)
        val estimate = EnergyCalculator.estimate(profile, 55.0, 2026)!!
        assertEquals(1280, estimate.suggestedKcal)
        assertTrue(estimate.limited)
    }

    @Test
    fun macrosAddUpToTheSuggestedKcal() {
        val estimate = EnergyCalculator.estimate(man, 80.0, 2026)!!
        assertEquals(128, estimate.proteinG) // 1.6 g/kg
        val fromMacros = EnergyCalculator.kcalFromMacros(estimate.proteinG, estimate.carbsG, estimate.fatG)
        assertTrue(kotlin.math.abs(fromMacros - estimate.suggestedKcal) < 10)
    }

    @Test
    fun missingDataGivesNoEstimate() {
        assertNull(EnergyCalculator.estimate(man.copy(heightCm = null), 80.0, 2026))
        assertNull(EnergyCalculator.estimate(man, null, 2026))
    }
}
