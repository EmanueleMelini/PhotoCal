package it.emanuelemelini.photocal.data.openfoodfacts

import it.emanuelemelini.photocal.data.db.NutrientComponent
import it.emanuelemelini.photocal.data.db.NutritionCategory
import it.emanuelemelini.photocal.data.db.ProductQuality
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import kotlin.math.roundToInt

/** Product fields the quality score needs, added to the barcode lookup: no extra request. */
internal val QUALITY_FIELDS = listOf(
    "nutriscore_grade", "nutriscore_score", "nutriscore_data", "additives_tags", "ingredients_n", "labels_tags", "nova_group",
)

/** Quality data of an Open Food Facts product (fields [QUALITY_FIELDS]). */
internal fun JsonObject.toProductQuality(barcode: String, now: Instant): ProductQuality {
    val data = obj("nutriscore_data")
    val category = when {
        data?.flag("is_water") == true -> NutritionCategory.WATER
        data?.flag("is_beverage") == true -> NutritionCategory.BEVERAGE
        data?.flag("is_fat_oil_nuts_seeds") == true -> NutritionCategory.FAT
        else -> NutritionCategory.GENERAL
    }
    val components = data?.obj("components")
    val nutrients = listOf("negative", "positive").flatMap { side ->
        (components?.get(side) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toComponent() }
    }
    // "en:e322i" -> "e322i"; tags in other forms aren't E numbers
    val additives = strings("additives_tags").mapNotNull { tag ->
        tag.removePrefix("en:").lowercase().takeIf { ADDITIVE_CODE.matches(it) }
    }.distinct()
    // Without ingredients an empty list would wrongly mean "no additives"
    val ingredientsKnown = (int("ingredients_n") ?: 0) > 0 || additives.isNotEmpty()
    return ProductQuality(
        barcode = barcode,
        nutritionGrade = string("nutriscore_grade")?.lowercase(),
        nutritionPoints = int("nutriscore_score"),
        nutritionCategory = category,
        nutrients = nutrients,
        additives = additives.takeIf { ingredientsKnown },
        organic = ORGANIC_LABEL in strings("labels_tags"),
        novaGroup = int("nova_group")?.takeIf { it in 1..4 },
        updatedAt = now,
    )
}

private fun JsonObject.toComponent(): NutrientComponent? = NutrientComponent(
    id = string("id") ?: return null,
    points = int("points") ?: return null,
    maxPoints = int("points_max")?.takeIf { it > 0 } ?: return null,
    value = double("value"),
    unit = string("unit"),
)

// Open Food Facts fields have unreliable types (numbers sometimes as strings)
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.content
internal fun JsonObject.double(key: String): Double? =
    (this[key] as? JsonPrimitive)?.content?.replace(',', '.')?.toDoubleOrNull()

private fun JsonObject.int(key: String): Int? = double(key)?.takeIf { it.isFinite() }?.roundToInt()

private fun JsonObject.flag(key: String): Boolean = int(key) == 1

private fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }

private const val ORGANIC_LABEL = "en:organic"
private val ADDITIVE_CODE = Regex("""e\d{3,4}[a-z]*""")
