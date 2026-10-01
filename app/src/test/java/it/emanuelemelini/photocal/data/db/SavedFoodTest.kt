package it.emanuelemelini.photocal.data.db

import it.emanuelemelini.photocal.data.FoodRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SavedFoodTest {

    private val entry = FoodEntry(
        date = LocalDate.of(2026, 9, 30), mealType = MealType.SNACK, name = " Biscotti ", grams = 25.0, kcal = 125.0,
        proteinG = 2.0, carbsG = null, fatG = 5.0, source = Source.BARCODE, photoPath = null, createdAt = Instant.EPOCH,
        servingUnit = ServingUnit.PIECE, servings = 4.0, servingLabel = "biscotti", saltG = 0.1,
    )

    @Test
    fun keyUsesTheBarcodeOrTheName() {
        assertEquals("barcode:123", SavedFood.keyFor("Biscotti", "123"))
        assertEquals("name:biscotti", SavedFood.keyFor(" Biscotti ", null))
        assertEquals("name:biscotti", SavedFood.keyFor("biscotti", " "))
    }

    @Test
    fun valuesArePer100Grams() {
        val food = SavedFood.from(entry, null, null, Instant.EPOCH)!!
        assertEquals("Biscotti", food.name)
        assertEquals(500.0, food.kcalPer100, 1e-9)
        assertEquals(8.0, food.proteinPer100!!, 1e-9)
        assertNull(food.carbsPer100)
        assertEquals(0.4, food.saltPer100!!, 1e-9)
        assertEquals(6.25, food.pieceGrams!!, 1e-9)
        assertEquals("biscotti", food.pieceLabel)
        assertEquals(1, food.useCount)
    }

    @Test
    fun aNewUseKeepsFavoriteAndCountsIt() {
        val first = SavedFood.from(entry, null, null, Instant.EPOCH)!!.copy(id = 9, favorite = true)
        val again = SavedFood.from(entry.copy(servingUnit = null, servings = null, servingLabel = null), null, first, Instant.ofEpochSecond(60))!!
        assertEquals(9, again.id)
        assertEquals(true, again.favorite)
        assertEquals(2, again.useCount)
        // In grams this time: the grams of a piece from last time are kept
        assertEquals(6.25, again.pieceGrams!!, 1e-9)
        assertEquals("biscotti", again.pieceLabel)
    }

    @Test
    fun entriesWithoutGramsAreNotSaved() {
        assertNull(SavedFood.from(entry.copy(grams = null), null, null, Instant.EPOCH))
    }

    @Test
    fun copiesAreNewEntriesInOrder() {
        val a = entry.copy(id = 1, createdAt = Instant.ofEpochSecond(20))
        val b = entry.copy(id = 2, mealType = MealType.LUNCH, createdAt = Instant.ofEpochSecond(10))
        val target = LocalDate.of(2026, 10, 1)
        val now = Instant.ofEpochSecond(1000)
        val copies = FoodRepository.copiesOf(listOf(a, b), target, meal = null, now = now)
        assertEquals(listOf(0L, 0L), copies.map { it.id })
        assertEquals(listOf(MealType.LUNCH, MealType.SNACK), copies.map { it.mealType })
        assertEquals(listOf(now, now.plusMillis(1)), copies.map { it.createdAt })
        assertEquals(setOf(target), copies.map { it.date }.toSet())
        assertEquals(listOf(MealType.DINNER, MealType.DINNER), FoodRepository.copiesOf(listOf(a, b), target, MealType.DINNER, now).map { it.mealType })
    }
}
