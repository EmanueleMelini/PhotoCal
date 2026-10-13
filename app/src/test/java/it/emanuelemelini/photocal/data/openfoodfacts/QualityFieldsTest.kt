package it.emanuelemelini.photocal.data.openfoodfacts

import it.emanuelemelini.photocal.data.db.NutrientComponent
import it.emanuelemelini.photocal.data.db.NutritionCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class QualityFieldsTest {

    private fun parse(json: String) = Json.parseToJsonElement(json).jsonObject.toProductQuality("123", Instant.EPOCH)

    @Test
    fun readsABeverageWithAdditives() {
        // Trimmed from the Open Food Facts API v3 answer for Coca-Cola Original
        val quality = parse(
            """
            {
              "nutriscore_grade": "e", "nutriscore_score": 12, "nova_group": 4, "ingredients_n": 6,
              "additives_tags": ["en:e150d", "en:e338"],
              "labels_tags": ["en:made-in-the-eu", "fr:triman"],
              "nutriscore_data": {
                "is_beverage": 1, "is_water": 0, "is_fat_oil_nuts_seeds": 0,
                "components": {
                  "negative": [{"id": "sugars", "points": 7, "points_max": 10, "unit": "g", "value": 10.6}],
                  "positive": [{"id": "proteins", "points": "0", "points_max": 7, "unit": "g", "value": 0}]
                }
              }
            }
            """,
        )
        assertEquals("e", quality.nutritionGrade)
        assertEquals(12, quality.nutritionPoints)
        assertEquals(NutritionCategory.BEVERAGE, quality.nutritionCategory)
        assertEquals(listOf("e150d", "e338"), quality.additives)
        assertFalse(quality.organic)
        assertEquals(4, quality.novaGroup)
        assertEquals(
            listOf(NutrientComponent("sugars", 7, 10, 10.6, "g"), NutrientComponent("proteins", 0, 7, 0.0, "g")),
            quality.nutrients,
        )
    }

    @Test
    fun organicFatWithoutAdditives() {
        val quality = parse(
            """
            {"nutriscore_grade": "b", "nutriscore_score": 2, "ingredients_n": 1, "additives_tags": [],
             "labels_tags": ["en:organic", "en:eu-organic"], "nutriscore_data": {"is_fat_oil_nuts_seeds": 1}}
            """,
        )
        assertEquals(NutritionCategory.FAT, quality.nutritionCategory)
        assertEquals(emptyList<String>(), quality.additives)
        assertTrue(quality.organic)
    }

    @Test
    fun withoutIngredientsTheAdditivesAreUnknown() {
        val quality = parse("""{"nutriscore_grade": "unknown", "additives_tags": []}""")
        assertNull(quality.additives)
        assertNull(quality.nutritionPoints)
        assertEquals(NutritionCategory.GENERAL, quality.nutritionCategory)
        assertNull(quality.novaGroup)
    }
}
