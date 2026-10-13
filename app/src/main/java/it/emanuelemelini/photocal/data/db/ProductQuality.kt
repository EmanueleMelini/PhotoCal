package it.emanuelemelini.photocal.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Quality data of a packaged product from Open Food Facts, as received: the score is computed
 * from it when shown (QualityRater), so a change of the formula also applies to past products.
 */
@Entity(tableName = "product_quality")
data class ProductQuality(
    @PrimaryKey val barcode: String,
    /**
     * Nutri-Score grade from Open Food Facts: "a".."e", "unknown" (missing data),
     * "not-applicable" (e.g. coffee, spices) or null. Only places [nutritionPoints] in its band:
     * the letter is never shown.
     */
    val nutritionGrade: String?,
    /** Nutri-Score 2023 points: the lower, the better. */
    val nutritionPoints: Int?,
    val nutritionCategory: NutritionCategory,
    /** Points of each nutrient, to explain the nutrition part of the score. */
    val nutrients: List<NutrientComponent>,
    /** Additive codes such as "e250"; null when the ingredients are unknown. */
    val additives: List<String>?,
    val organic: Boolean,
    /** NOVA processing group 1-4, shown as information only. */
    val novaGroup: Int?,
    val updatedAt: Instant,
)

/** Nutri-Score categories with their own grade thresholds. */
enum class NutritionCategory { GENERAL, FAT, BEVERAGE, WATER }

/** One nutrient of the Nutri-Score computation, e.g. sugars 9 points of 15, 32 g. */
@Serializable
data class NutrientComponent(
    val id: String,
    val points: Int,
    val maxPoints: Int,
    val value: Double?,
    val unit: String?,
)
