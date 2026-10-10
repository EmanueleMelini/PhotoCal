package it.emanuelemelini.photocal.data.ai

import it.emanuelemelini.photocal.data.ai.openai.OpenAiClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodAnalysisTest {

    @Test
    fun strictSchemaRequiresEveryProperty() {
        fun JsonObject.check() {
            val properties = getValue("properties").jsonObject.keys
            val required = getValue("required").jsonArray.map { it.jsonPrimitive.content }.toSet()
            assertEquals(properties, required)
            assertEquals("false", getValue("additionalProperties").jsonPrimitive.content)
        }
        FOOD_ANALYSIS_JSON_SCHEMA.check()
        FOOD_ANALYSIS_JSON_SCHEMA.getValue("properties").jsonObject.getValue("items").jsonObject
            .getValue("items").jsonObject.check()
    }

    /** Gemini refuses the whole request (400) for a key it doesn't know in the schema. */
    @Test
    fun geminiSchemaUsesOnlyKnownKeys() {
        val allowed = setOf("type", "properties", "items", "required", "propertyOrdering", "description", "enum", "nullable")
        fun JsonObject.check() {
            assertEquals(emptySet<String>(), keys - allowed)
            this["properties"]?.jsonObject?.let { properties ->
                properties.values.forEach { it.jsonObject.check() }
                val listed = (this["required"]?.jsonArray.orEmpty() + this["propertyOrdering"]?.jsonArray.orEmpty())
                    .map { it.jsonPrimitive.content }.toSet()
                assertEquals(emptySet<String>(), listed - properties.keys)
            }
            this["items"]?.jsonObject?.check()
        }
        GEMINI_FOOD_ANALYSIS_SCHEMA.check()
        val item = GEMINI_FOOD_ANALYSIS_SCHEMA.getValue("properties").jsonObject.getValue("items").jsonObject
            .getValue("items").jsonObject.getValue("properties").jsonObject
        assertEquals(true, "meal" in item)
    }

    @Test
    fun nullsAndCodeFencesAreAccepted() {
        val text = """
            ```json
            {"items":[{"name":"Pasta","grams":80,"kcal":290,"protein_g":10,"carbs_g":57,"fat_g":1.2,
            "fiber_g":null,"sugars_g":null,"salt_g":null,"crea_code":"","confidence":"high"}],"notes":""}
            ```
        """.trimIndent()
        val item = parseFoodAnalysis(text)!!.items.single()
        assertEquals("Pasta", item.name)
        assertNull(item.fiberG)
        assertNull(parseFoodAnalysis("not json"))
    }

    @Test
    fun theMealOfADayIsReadAndOptional() {
        val text = """
            {"items":[{"name":"Cappuccino","grams":150,"kcal":80,"protein_g":4,"carbs_g":6,"fat_g":4,
            "crea_code":"","confidence":"high","meal":"breakfast"},
            {"name":"Mela","grams":150,"kcal":80,"protein_g":0.4,"carbs_g":19,"fat_g":0.2,
            "crea_code":"","confidence":"medium","meal":null}],"notes":""}
        """.trimIndent()
        val items = parseFoodAnalysis(text)!!.items
        assertEquals("breakfast", items[0].meal)
        assertNull(items[1].meal)
    }

    @Test
    fun theDayPromptAsksForMealsOnlyWhenSplitting() {
        assertEquals(true, AiPrompts.daySystem("Italian", splitMeals = true).contains("\"breakfast\""))
        assertEquals(true, AiPrompts.daySystem("Italian", splitMeals = false).contains("- meal: null."))
    }

    @Test
    fun compatibleBaseUrlMustBeHttps() {
        assertEquals("https://openrouter.ai/api/v1", OpenAiClient.normalizeBaseUrl(" https://openrouter.ai/api/v1/ "))
        assertEquals("https://api.mistral.ai/v1", OpenAiClient.normalizeBaseUrl("https://api.mistral.ai/v1/chat/completions"))
        assertEquals("", OpenAiClient.normalizeBaseUrl("http://192.168.1.10:11434/v1"))
        assertEquals("", OpenAiClient.normalizeBaseUrl("openrouter.ai"))
    }
}
