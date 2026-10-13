package it.emanuelemelini.photocal.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductQualityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(quality: ProductQuality)

    @Query("SELECT * FROM product_quality WHERE barcode = :barcode")
    suspend fun get(barcode: String): ProductQuality?

    /** One row per product ever scanned: small enough to keep in memory. */
    @Query("SELECT * FROM product_quality")
    fun observeAll(): Flow<List<ProductQuality>>

    /** Barcodes of the diary and of the saved foods without quality data yet. */
    @Query(
        """
        SELECT barcode FROM (
            SELECT barcode, MAX(createdAt) AS usedAt FROM food_entries WHERE barcode IS NOT NULL GROUP BY barcode
            UNION ALL
            SELECT barcode, lastUsedAt AS usedAt FROM saved_foods WHERE barcode IS NOT NULL
        )
        WHERE barcode NOT IN (SELECT barcode FROM product_quality)
        GROUP BY barcode ORDER BY MAX(usedAt) DESC
        """
    )
    suspend fun getMissingBarcodes(): List<String>
}
