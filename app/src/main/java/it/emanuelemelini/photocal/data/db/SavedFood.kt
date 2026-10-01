package it.emanuelemelini.photocal.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A food already logged, for the recent foods and favorites: values per 100 g (= 100 ml) and
 * the last quantity used. Saved again (same [foodKey]) at every use.
 */
@Entity(tableName = "saved_foods", indices = [Index(value = ["foodKey"], unique = true), Index("lastUsedAt")])
data class SavedFood(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "barcode:<code>" or "name:<lowercase name>": the same food is saved only once. */
    val foodKey: String,
    val name: String,
    val barcode: String?,
    val source: Source,
    val kcalPer100: Double,
    val proteinPer100: Double?,
    val carbsPer100: Double?,
    val fatPer100: Double?,
    val fiberPer100: Double?,
    val sugarsPer100: Double?,
    val saltPer100: Double?,
    /** Last quantity, in grams (= ml). */
    val grams: Double,
    val servingUnit: ServingUnit?,
    val servings: Double?,
    /** Grams of one piece, when the last quantity was in pieces. */
    val pieceGrams: Double?,
    val pieceLabel: String?,
    val favorite: Boolean = false,
    val useCount: Int = 1,
    val lastUsedAt: Instant,
) {
    companion object {
        fun keyFor(name: String, barcode: String?): String =
            barcode?.takeIf { it.isNotBlank() }?.let { "barcode:$it" } ?: "name:${name.trim().lowercase()}"

        /**
         * The food of a diary entry, merged with the one already saved (favorite and use count
         * are kept). Null for entries without grams: the values per 100 g can't be computed.
         */
        fun from(entry: FoodEntry, barcode: String?, existing: SavedFood?, now: Instant): SavedFood? {
            val grams = entry.grams?.takeIf { it > 0 } ?: return null
            val per100 = { value: Double? -> value?.let { it / grams * 100 } }
            val pieceGrams = if (entry.servingUnit == ServingUnit.PIECE) {
                entry.servings?.takeIf { it > 0 }?.let { grams / it }
            } else {
                existing?.pieceGrams
            }
            return SavedFood(
                id = existing?.id ?: 0,
                foodKey = keyFor(entry.name, barcode),
                name = entry.name.trim(),
                barcode = barcode?.takeIf { it.isNotBlank() },
                source = entry.source,
                kcalPer100 = entry.kcal / grams * 100,
                proteinPer100 = per100(entry.proteinG),
                carbsPer100 = per100(entry.carbsG),
                fatPer100 = per100(entry.fatG),
                fiberPer100 = per100(entry.fiberG),
                sugarsPer100 = per100(entry.sugarsG),
                saltPer100 = per100(entry.saltG),
                grams = grams,
                servingUnit = entry.servingUnit,
                servings = entry.servings,
                pieceGrams = pieceGrams,
                pieceLabel = if (entry.servingUnit == ServingUnit.PIECE) entry.servingLabel else existing?.pieceLabel,
                favorite = existing?.favorite ?: false,
                useCount = (existing?.useCount ?: 0) + 1,
                lastUsedAt = now,
            )
        }
    }
}
