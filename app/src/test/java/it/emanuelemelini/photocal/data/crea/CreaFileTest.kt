package it.emanuelemelini.photocal.data.crea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The generated table (tools/crea/build_crea_table.py) has the columns CreaTable reads. */
class CreaFileTest {

    private val rows = File("src/main/assets/crea_foods.tsv").readLines()
        .filterNot { it.startsWith("#") || it.isBlank() }

    @Test
    fun headerHasFiberSugarsAndSalt() {
        assertEquals(
            listOf("code", "name", "category", "edible_pct", "portion_g", "kcal", "protein_g", "fat_g", "carbs_g", "fiber_g", "sugars_g", "salt_g"),
            rows.first().split('\t'),
        )
    }

    @Test
    fun everyFoodHasAllColumnsAndPlausibleSalt() {
        val foods = rows.drop(1).map { it.split('\t') }
        assertTrue(foods.size > 800)
        assertTrue(foods.all { it.size == 12 })
        val salt = foods.mapNotNull { it[11].toDoubleOrNull() }
        assertTrue(salt.size > 500)
        assertTrue(salt.all { it in 0.0..100.0 })
    }
}
