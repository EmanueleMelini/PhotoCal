package it.emanuelemelini.photocal.data.quality

import android.util.Log
import it.emanuelemelini.photocal.data.db.NutritionCategory
import it.emanuelemelini.photocal.data.db.ProductQuality
import it.emanuelemelini.photocal.data.db.ProductQualityDao
import it.emanuelemelini.photocal.data.openfoodfacts.OpenFoodFactsClient
import it.emanuelemelini.photocal.data.openfoodfacts.ProductLookupException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/** Quality data of the scanned products and their scores. */
class QualityRepository(
    private val dao: ProductQualityDao,
    private val additiveTable: AdditiveTable,
    private val openFoodFacts: OpenFoodFactsClient,
    private val scope: CoroutineScope,
) {
    private val fetching = AtomicBoolean(false)

    private suspend fun rater() = QualityRater(additiveTable.all())

    /** Score of every product with quality data, by barcode. */
    fun observeRatings(): Flow<Map<String, QualityRating>> = dao.observeAll()
        .map { products ->
            val rater = rater()
            products.associate { it.barcode to rater.rate(it) }
        }
        .flowOn(Dispatchers.Default)

    suspend fun rating(barcode: String): QualityRating? = dao.get(barcode)?.let { rater().rate(it) }

    suspend fun rate(quality: ProductQuality): QualityRating = rater().rate(quality)

    /** Saves the data of a product just looked up, so it's also there offline. */
    suspend fun save(quality: ProductQuality) = dao.upsert(quality)

    /**
     * Looks up on Open Food Facts the products of the diary without quality data (scanned
     * before 1.9.0, or restored from a backup), newest first. One request every
     * [REQUEST_INTERVAL]: Open Food Facts allows 15 product reads a minute. Stops at the first
     * network error; the next call goes on. Runs once at a time, outside the caller's scope.
     */
    fun fetchMissing() {
        if (!fetching.compareAndSet(false, true)) return
        scope.launch {
            try {
                dao.getMissingBarcodes().forEachIndexed { index, barcode ->
                    if (index > 0) delay(REQUEST_INTERVAL)
                    val quality = try {
                        openFoodFacts.getProduct(barcode).quality
                    } catch (_: ProductLookupException.NotFound) {
                        null
                    }
                    // A product no longer on Open Food Facts is saved without data: not asked again
                    dao.upsert(quality ?: emptyQuality(barcode))
                }
            } catch (e: ProductLookupException) {
                Log.i(TAG, "Quality lookup stopped: ${e.message}")
            } finally {
                fetching.set(false)
            }
        }
    }

    private fun emptyQuality(barcode: String) = ProductQuality(
        barcode = barcode,
        nutritionGrade = null,
        nutritionPoints = null,
        nutritionCategory = NutritionCategory.GENERAL,
        nutrients = emptyList(),
        additives = null,
        organic = false,
        novaGroup = null,
        updatedAt = Instant.now(),
    )

    private companion object {
        const val TAG = "QualityRepository"
        val REQUEST_INTERVAL = 6.seconds
    }
}
