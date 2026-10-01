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
    fun compatibleBaseUrlMustBeHttps() {
        assertEquals("https://openrouter.ai/api/v1", OpenAiClient.normalizeBaseUrl(" https://openrouter.ai/api/v1/ "))
        assertEquals("https://api.mistral.ai/v1", OpenAiClient.normalizeBaseUrl("https://api.mistral.ai/v1/chat/completions"))
        assertEquals("", OpenAiClient.normalizeBaseUrl("http://192.168.1.10:11434/v1"))
        assertEquals("", OpenAiClient.normalizeBaseUrl("openrouter.ai"))
    }
}
