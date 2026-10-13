package it.emanuelemelini.photocal.data.quality

import it.emanuelemelini.photocal.data.db.NutrientComponent
import it.emanuelemelini.photocal.data.db.NutritionCategory
import it.emanuelemelini.photocal.data.db.ProductQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/** Scores of real products (Open Food Facts data of October 2026) with the real additives table. */
class QualityRaterTest {

    private val additives = File("src/main/assets/off_additives.tsv").useLines { AdditiveFile.parse(it) }
    private val rater = QualityRater(additives)

    private fun product(
        grade: String?,
        points: Int?,
        additives: List<String>? = emptyList(),
        organic: Boolean = false,
        category: NutritionCategory = NutritionCategory.GENERAL,
        nutrients: List<NutrientComponent> = emptyList(),
    ) = ProductQuality(
        barcode = "0",
        nutritionGrade = grade,
        nutritionPoints = points,
        nutritionCategory = category,
        nutrients = nutrients,
        additives = additives,
        organic = organic,
        novaGroup = 4,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun organicOatsAreExcellent() {
        val rating = rater.rate(product("a", -4, organic = true))
        assertEquals(96, rating.score)
        assertEquals(QualityBand.EXCELLENT, rating.band)
        assertEquals(10, rating.organicPoints)
    }

    @Test
    fun tomatoSauceWithoutAdditives() {
        val rating = rater.rate(product("a", 0))
        assertEquals(49, rating.nutritionPoints)
        assertEquals(79, rating.score)
    }

    @Test
    fun hamWithNitriteLosesTheAdditivePoints() {
        val rating = rater.rate(product("d", 12, listOf("e250", "e301")))
        assertEquals(22, rating.nutritionPoints)
        assertEquals(15, rating.additivePoints)
        assertEquals(37, rating.score)
        assertFalse(rating.capped)
        assertEquals(listOf(AdditiveRisk.HIGH, AdditiveRisk.NONE), rating.additives!!.map { it.risk })
    }

    @Test
    fun aFamilyAndItsAdditiveCountOnce() {
        // Open Food Facts lists both "e322" and "e322i": only the lecithin is kept
        val nutella = rater.rate(product("e", 31, listOf("e322", "e322i")))
        assertEquals(listOf("e322i"), nutella.additives!!.map { it.code })
        assertEquals("E322i", nutella.additives!!.single().label)
        assertEquals(0, nutella.nutritionPoints)
        assertEquals(30, nutella.score)
        assertEquals(QualityBand.MEDIOCRE, nutella.band)
    }

    @Test
    fun childrenInheritTheRiskOfTheirFamily() {
        val biscuits = rater.rate(product("e", 28, listOf("e322", "e322i", "e450", "e450i", "e500", "e500ii", "e503", "e503ii")))
        assertEquals(listOf("e322i", "e450i", "e500ii", "e503ii"), biscuits.additives!!.map { it.code })
        assertEquals(15, biscuits.additivePoints)
        assertEquals(QualityBand.POOR, biscuits.band)
    }

    @Test
    fun beveragesHaveTheirOwnThresholds() {
        val cola = rater.rate(product("e", 12, listOf("e150d", "e338"), category = NutritionCategory.BEVERAGE))
        assertEquals(9, cola.nutritionPoints)
        assertEquals(24, cola.score)
        assertEquals(QualityBand.POOR, cola.band)
        assertTrue(cola.perMl)
    }

    @Test
    fun oliveOilUsesTheFatThresholds() {
        assertEquals(70, rater.rate(product("b", 0, category = NutritionCategory.FAT)).score)
        assertEquals(77, rater.rate(product("b", 2, organic = true, category = NutritionCategory.FAT)).score)
    }

    @Test
    fun parmigiano() {
        assertEquals(49, rater.rate(product("d", 14)).score)
    }

    @Test
    fun closeProductsGetCloseScoresAcrossAGradeBoundary() {
        val lastA = rater.rate(product("a", 0)).nutritionPoints!!
        val firstB = rater.rate(product("b", 1)).nutritionPoints!!
        assertTrue(lastA - firstB in 0..4)
    }

    @Test
    fun aHighRiskAdditiveCapsTheScore() {
        val rating = rater.rate(product("a", -6, listOf("e250")))
        assertEquals(49, rating.score)
        assertTrue(rating.capped)
    }

    @Test
    fun additivePointsDontGoBelowZero() {
        val rating = rater.rate(product("c", 5, listOf("e250", "e338", "e131")))
        assertEquals(0, rating.additivePoints)
    }

    @Test
    fun waterNeedsNoIngredients() {
        val rating = rater.rate(product("a", 0, additives = null, category = NutritionCategory.WATER))
        assertEquals(90, rating.score)
        assertTrue(rating.isWater)
        assertTrue(rating.nutrients.isEmpty())
    }

    @Test
    fun unratedProductsSayWhy() {
        assertEquals(UnratedReason.NOT_APPLICABLE, rater.rate(product("not-applicable", null)).unrated)
        assertEquals(UnratedReason.MISSING_NUTRITION, rater.rate(product("unknown", null)).unrated)
        assertEquals(UnratedReason.MISSING_NUTRITION, rater.rate(product(null, null)).unrated)
        val noIngredients = rater.rate(product("c", 5, additives = null))
        assertEquals(UnratedReason.MISSING_INGREDIENTS, noIngredients.unrated)
        assertNull(noIngredients.score)
        assertEquals(32, noIngredients.nutritionPoints)
    }

    @Test
    fun nutrientLevelsFromTheirShareOfPoints() {
        val rating = rater.rate(
            product(
                "c", 5,
                nutrients = listOf(
                    // 1528 kJ = 365 kcal: dense, like dry cereals
                    NutrientComponent("energy", 4, 10, 1528.0, "kJ"),
                    NutrientComponent("sugars", 9, 15, 32.0, "g"),
                    NutrientComponent("salt", 0, 20, 0.1, "g"),
                    NutrientComponent("non_nutritive_sweeteners", 0, 4, 0.0, null),
                    NutrientComponent("proteins", 4, 7, 11.8, "g"),
                    NutrientComponent("fiber", 0, 5, 1.0, "g"),
                    NutrientComponent("unknown", 1, 1, 1.0, "g"),
                ),
            ),
        )
        assertEquals(
            listOf(
                NutrientKind.ENERGY to NutrientLevel.HIGH,
                NutrientKind.SUGARS to NutrientLevel.HIGH,
                NutrientKind.SALT to NutrientLevel.LOW,
                NutrientKind.PROTEINS to NutrientLevel.MEDIUM,
            ),
            rating.nutrients.map { it.kind to it.level },
        )
        assertEquals(listOf(false, false, true, true), rating.nutrients.map { it.isGood })
    }

    @Test
    fun bands() {
        assertEquals(QualityBand.POOR, QualityBand.of(24))
        assertEquals(QualityBand.MEDIOCRE, QualityBand.of(25))
        assertEquals(QualityBand.GOOD, QualityBand.of(50))
        assertEquals(QualityBand.EXCELLENT, QualityBand.of(75))
    }

    @Test
    fun dayAverageIsWeightedOnKcal() {
        assertEquals(35, QualityRating.average(listOf(80 to 100.0, 20 to 300.0)))
        // Only drinks without kcal: plain average
        assertEquals(50, QualityRating.average(listOf(90 to 0.0, 10 to 0.0)))
        assertNull(QualityRating.average(emptyList()))
    }
}
