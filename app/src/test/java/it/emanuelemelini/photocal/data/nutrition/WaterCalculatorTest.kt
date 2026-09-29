package it.emanuelemelini.photocal.data.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaterCalculatorTest {

    @Test
    fun sedentaryAdultsDrinkEightyPercentOfTheReference() {
        // 2500 / 2000 / 2250 ml x 0.8
        assertEquals(2000, WaterCalculator.suggestedMl(Sex.MALE, ActivityLevel.SEDENTARY, weightKg = 70.0))
        assertEquals(1600, WaterCalculator.suggestedMl(Sex.FEMALE, ActivityLevel.SEDENTARY, weightKg = 60.0))
        assertEquals(1800, WaterCalculator.suggestedMl(Sex.UNSPECIFIED, ActivityLevel.SEDENTARY, weightKg = null))
    }

    @Test
    fun heavierPeopleGetThirtyMlPerKg() {
        // 100 kg x 30 = 3000 ml > 2500; x 0.8 = 2400
        assertEquals(2400, WaterCalculator.suggestedMl(Sex.MALE, ActivityLevel.SEDENTARY, weightKg = 100.0))
    }

    @Test
    fun activityAddsTwoHundredMlPerLevel() {
        assertEquals(2400, WaterCalculator.suggestedMl(Sex.MALE, ActivityLevel.MODERATE, weightKg = 70.0))
        assertEquals(2800, WaterCalculator.suggestedMl(Sex.MALE, ActivityLevel.VERY_ACTIVE, weightKg = 70.0))
    }

    @Test
    fun needsSexAndActivity() {
        assertNull(WaterCalculator.suggestedMl(null, ActivityLevel.LIGHT, 70.0))
        assertNull(WaterCalculator.suggestedMl(Sex.FEMALE, null, 70.0))
    }

    @Test
    fun glassesRoundUpToReachTheSuggestion() {
        assertEquals(10, WaterCalculator.glassesToReach(2000, 200))
        assertEquals(9, WaterCalculator.glassesToReach(2100, 250))
        assertEquals(1, WaterCalculator.glassesToReach(0, 200))
    }

    @Test
    fun goalGlassesFollowTheGlassSize() {
        assertEquals(8, WaterCalculator.goalGlasses(1600, 200))
        assertEquals(6, WaterCalculator.goalGlasses(1600, 250))
        assertEquals(1.5, WaterCalculator.glasses(300, 200), 0.0)
    }

    @Test
    fun missingWaterForTheReminders() {
        // Goal 1600 ml in 200 ml glasses: half is 800 ml
        assertEquals(200, WaterCalculator.missingMl(drunkMl = 600, goalMl = 1600, glassMl = 200, share = 0.5))
        assertEquals(0, WaterCalculator.missingMl(drunkMl = 800, goalMl = 1600, glassMl = 200, share = 0.5))
        assertEquals(1000, WaterCalculator.missingMl(drunkMl = 600, goalMl = 1600, glassMl = 200, share = 1.0))
        assertEquals(0, WaterCalculator.missingMl(drunkMl = 2000, goalMl = 1600, glassMl = 200, share = 1.0))
        // The goal counts as shown: 2400 ml with 250 ml glasses is 10 glasses = 2500 ml
        assertEquals(1250, WaterCalculator.missingMl(drunkMl = 0, goalMl = 2400, glassMl = 250, share = 0.5))
    }
}
