package it.emanuelemelini.photocal.data.openfoodfacts

import android.util.Log
import it.emanuelemelini.photocal.AppLanguage
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.data.http.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Open Food Facts errors; the UI shows a translated text chosen from the type. */
sealed class ProductLookupException(message: String) : Exception(message) {
    class NotFound : ProductLookupException("Product not found")
    class Network : ProductLookupException("Network error")
    class Server(val code: Int) : ProductLookupException("Server error $code")
}

/** Reads products from the public Open Food Facts API v3. */
class OpenFoodFactsClient(private val httpClient: OkHttpClient) {

    suspend fun getProduct(barcode: String): Product = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/product/$barcode?fields=$FIELDS")
            // Open Food Facts asks for a User-Agent that identifies the app
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val (code, body) = try {
            httpClient.newCall(request).await().use { it.code to it.body.string() }
        } catch (e: IOException) {
            Log.w(TAG, "Open Food Facts request failed", e)
            throw ProductLookupException.Network()
        }

        val root = try {
            Json.parseToJsonElement(body).jsonObject
        } catch (_: SerializationException) {
            throw ProductLookupException.Server(code)
        } catch (_: IllegalArgumentException) {
            throw ProductLookupException.Server(code)
        }
        val resultId = root.obj("result")?.string("id")
        val product = root.obj("product")
        if (resultId == "product_not_found" || code == 404 || product == null) {
            if (code in 500..599) throw ProductLookupException.Server(code)
            throw ProductLookupException.NotFound()
        }
        product.toProduct(barcode)
    }

    private fun JsonObject.toProduct(barcode: String): Product {
        val nutriments = obj("nutriments")
        // If the kcal value is missing, derive it from kJ
        val kcal = nutriments?.double("energy-kcal_100g")
            ?: nutriments?.double("energy-kj_100g")?.let { it / KJ_PER_KCAL }
        val quantityUnit = string("product_quantity_unit")?.lowercase()
        val quantityText = string("quantity")?.lowercase().orEmpty()
        return Product(
            barcode = barcode,
            // Name in the app language when available, otherwise the generic one
            name = string("product_name_${AppLocale.language.tag}")?.takeIf { it.isNotBlank() }
                ?: string("product_name")?.takeIf { it.isNotBlank() }
                ?: barcode,
            brand = string("brands")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
            kcalPer100 = kcal,
            proteinPer100 = nutriments?.double("proteins_100g"),
            carbsPer100 = nutriments?.double("carbohydrates_100g"),
            fatPer100 = nutriments?.double("fat_100g"),
            servingQuantity = double("serving_quantity")?.takeIf { it > 0 },
            packageQuantity = double("product_quantity")?.takeIf { it > 0 },
            isLiquid = quantityUnit == "ml" || LIQUID_QUANTITY.containsMatchIn(quantityText),
            imageUrl = string("image_front_small_url"),
        )
    }

    // Open Food Facts fields have unreliable types (numbers sometimes as strings)
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.content
    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.content?.replace(',', '.')?.toDoubleOrNull()

    private companion object {
        const val TAG = "OpenFoodFacts"
        const val BASE_URL = "https://world.openfoodfacts.org/api/v3"
        const val USER_AGENT = "PhotoCal/0.1 (personal Android app)"
        const val KJ_PER_KCAL = 4.184
        val FIELDS = listOf(
            "product_name", *AppLanguage.entries.map { "product_name_${it.tag}" }.toTypedArray(), "brands", "nutriments", "quantity",
            "serving_quantity", "product_quantity", "product_quantity_unit", "image_front_small_url",
        ).joinToString(",")
        val LIQUID_QUANTITY = Regex("""\d\s*(ml|cl|l)\b""")
    }
}
