package it.emanuelemelini.photocal.data.share

import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.data.db.ServingUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * What a share link carries: one day or a week, in read-only form. Short JSON names keep the
 * link short; null fields are the ones the sender chose to hide. Field names and meanings are
 * part of the link format: change them only with a new [ShareCodec.VERSION].
 */
@Serializable
data class SharedCard(
    @SerialName("v") val version: Int = ShareCodec.VERSION,
    @SerialName("n") val name: String,
    /** Base64 JPEG thumbnail of the profile photo. */
    @SerialName("a") val avatar: String? = null,
    /** Epoch seconds. */
    @SerialName("s") val sharedAt: Long,
    /** true: [days] are the 7 days of a week summary, without meals. */
    @SerialName("r") val week: Boolean = false,
    @SerialName("g") val goals: SharedGoals? = null,
    /** Glass size, to show the water in glasses. */
    @SerialName("b") val glassMl: Int? = null,
    /** From the oldest to the newest. */
    @SerialName("d") val days: List<SharedDay>,
)

@Serializable
data class SharedGoals(
    @SerialName("k") val kcal: Int? = null,
    @SerialName("p") val proteinG: Int? = null,
    @SerialName("c") val carbsG: Int? = null,
    @SerialName("f") val fatG: Int? = null,
    @SerialName("w") val waterMl: Int? = null,
)

@Serializable
data class SharedDay(
    @SerialName("t") val epochDay: Long,
    @SerialName("k") val kcal: Double? = null,
    @SerialName("p") val proteinG: Double? = null,
    @SerialName("c") val carbsG: Double? = null,
    @SerialName("f") val fatG: Double? = null,
    @SerialName("w") val waterMl: Int? = null,
    @SerialName("x") val weightKg: Double? = null,
    @SerialName("m") val entries: List<SharedEntry>? = null,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}

@Serializable
data class SharedEntry(
    /** [MealType] name. */
    @SerialName("m") val meal: String,
    @SerialName("n") val name: String,
    @SerialName("k") val kcal: Double,
    @SerialName("g") val grams: Double? = null,
    /** [ServingUnit] name. */
    @SerialName("u") val unit: String? = null,
    @SerialName("s") val servings: Double? = null,
) {
    val mealType: MealType? get() = MealType.entries.find { it.name == meal }
    val servingUnit: ServingUnit? get() = ServingUnit.entries.find { it.name == unit }
}
