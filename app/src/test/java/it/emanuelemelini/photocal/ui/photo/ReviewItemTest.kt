package it.emanuelemelini.photocal.ui.photo

import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.data.estimate.PerGram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class ReviewItemTest {

    @Before
    fun italianLocale() {
        // Expected values below use the Italian decimal comma
        AppLocale.current = Locale.ITALIAN
    }

    private val ai = PerGram(kcal = 1.5, protein = 0.05, carbs = 0.3, fat = 0.01)
    private val crea = PerGram(kcal = 1.3, protein = 0.04, carbs = 0.28, fat = 0.005)

    private fun item() = ReviewItem(
        key = 0, name = "Pasta", grams = "100",
        kcal = "150", protein = "5", carbs = "30", fat = "1",
        perGram = ai, aiPerGram = ai, creaPerGram = crea, creaName = "Pasta di semola, cotta",
    )

    @Test
    fun changingGramsRescalesKcalAndMacros() {
        val updated = item().withGrams("200")
        assertEquals("300", updated.kcal)
        assertEquals("10", updated.protein)
        assertEquals("60", updated.carbs)
    }

    @Test
    fun typingGramsOneDigitAtATimeKeepsPrecision() {
        // Going through intermediate values (empty, "2", "25") the final result stays exact
        val updated = item().withGrams("").withGrams("2").withGrams("25").withGrams("250")
        assertEquals("375", updated.kcal)
    }

    @Test
    fun switchingToCreaAndBackToAiEstimate() {
        val withCrea = item().withCrea(true)
        assertTrue(withCrea.usingCrea)
        assertEquals("130", withCrea.kcal)
        assertEquals("0,5", withCrea.fat)

        val backToAi = withCrea.withCrea(false)
        assertFalse(backToAi.usingCrea)
        assertEquals("150", backToAi.kcal)
    }

    @Test
    fun manualRowCreatesRatiosFromFields() {
        val manual = ReviewItem(key = 1).copy(name = "Insalata").withGrams("150").withKcal("60")
        assertEquals("120", manual.withGrams("300").kcal)
    }

    @Test
    fun kcalEditedByHandUpdatesTheRatio() {
        val edited = item().withKcal("200") // 2 kcal/g
        assertEquals("400", edited.withGrams("200").kcal)
    }
}
