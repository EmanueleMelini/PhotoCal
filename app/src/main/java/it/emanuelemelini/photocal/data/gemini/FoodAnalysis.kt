package it.emanuelemelini.photocal.data.gemini

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Meal analysis result returned by Gemini. */
@Serializable
data class FoodAnalysis(
    val items: List<AnalyzedFood> = emptyList(),
    val notes: String = "",
)

@Serializable
data class AnalyzedFood(
    val name: String,
    val grams: Double,
    val kcal: Double,
    @SerialName("protein_g") val proteinG: Double? = null,
    @SerialName("carbs_g") val carbsG: Double? = null,
    @SerialName("fat_g") val fatG: Double? = null,
    @SerialName("fiber_g") val fiberG: Double? = null,
    @SerialName("sugars_g") val sugarsG: Double? = null,
    @SerialName("salt_g") val saltG: Double? = null,
    /** "high", "medium" or "low". */
    val confidence: String? = null,
    /** Code of the matching CREA food (empty if none or if the tables are disabled). */
    @SerialName("crea_code") val creaCode: String? = null,
)

/** responseSchema (the OpenAPI subset used by Gemini) that constrains the response to [FoodAnalysis]. */
internal val FOOD_ANALYSIS_SCHEMA: JsonObject = buildJsonObject {
    put("type", "OBJECT")
    putJsonObject("properties") {
        putJsonObject("items") {
            put("type", "ARRAY")
            putJsonObject("items") {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    property("name", "STRING", "Short food name")
                    property("grams", "NUMBER", "Estimated weight in grams (ml for drinks)")
                    property("kcal", "NUMBER", "Kilocalories for the estimated weight")
                    property("protein_g", "NUMBER", "Protein in grams")
                    property("carbs_g", "NUMBER", "Carbohydrates in grams")
                    property("fat_g", "NUMBER", "Fat in grams")
                    property("fiber_g", "NUMBER", "Fiber in grams, if known")
                    property("sugars_g", "NUMBER", "Sugars in grams, if known")
                    property("salt_g", "NUMBER", "Salt in grams, if known")
                    property("crea_code", "STRING", "CREA code of the matching food, empty if none")
                    putJsonObject("confidence") {
                        put("type", "STRING")
                        putJsonArray("enum") {
                            add("high")
                            add("medium")
                            add("low")
                        }
                    }
                }
                val fields = listOf("name", "grams", "kcal", "protein_g", "carbs_g", "fat_g", "confidence", "crea_code")
                // Fiber, sugars and salt are optional: the model leaves them out when unsure
                val optional = listOf("fiber_g", "sugars_g", "salt_g")
                putJsonArray("required") { fields.forEach { add(it) } }
                putJsonArray("propertyOrdering") { (fields + optional).forEach { add(it) } }
            }
        }
        property("notes", "STRING", "Short remarks: assumed condiments, uncertainties")
    }
    putJsonArray("required") {
        add("items")
        add("notes")
    }
    putJsonArray("propertyOrdering") {
        add("items")
        add("notes")
    }
}

private fun JsonObjectBuilder.property(name: String, type: String, description: String) {
    putJsonObject(name) {
        put("type", type)
        put("description", description)
    }
}
