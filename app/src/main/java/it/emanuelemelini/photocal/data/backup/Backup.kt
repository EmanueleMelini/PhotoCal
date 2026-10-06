package it.emanuelemelini.photocal.data.backup

import kotlinx.serialization.Serializable

/**
 * Backup file (JSON): diary, weight, water, saved foods and settings. Not the Gemini API key
 * and not the photos (only their file names). Enums are stored by name, dates as epoch days,
 * instants as epoch milliseconds except [exportedAt]. Field names are part of the format: change
 * their meaning only with a new [BackupCodec.VERSION]; new optional fields don't need one.
 */
@Serializable
data class Backup(
    val format: String = BackupCodec.FORMAT,
    val version: Int = BackupCodec.VERSION,
    val appVersion: String,
    /** Epoch seconds, unlike the other instants: 1.4.0 already writes it so and its files must stay readable. */
    val exportedAt: Long,
    val entries: List<BackupEntry> = emptyList(),
    val weights: List<BackupWeight> = emptyList(),
    val water: List<BackupWater> = emptyList(),
    val savedFoods: List<BackupSavedFood> = emptyList(),
    val settings: BackupSettings? = null,
)

@Serializable
data class BackupEntry(
    val date: Long,
    val meal: String,
    val name: String,
    val grams: Double? = null,
    val kcal: Double,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val fiberG: Double? = null,
    val sugarsG: Double? = null,
    val saltG: Double? = null,
    val source: String,
    /** File name of the photo, reconnected on restore if it is still on the phone. */
    val photo: String? = null,
    val createdAt: Long,
    val unit: String? = null,
    val servings: Double? = null,
    val pieceLabel: String? = null,
)

@Serializable
data class BackupWeight(val date: Long, val weightKg: Double, val createdAt: Long)

@Serializable
data class BackupWater(val date: Long, val ml: Int, val updatedAt: Long)

@Serializable
data class BackupSavedFood(
    val name: String,
    val barcode: String? = null,
    val source: String,
    val kcalPer100: Double,
    val proteinPer100: Double? = null,
    val carbsPer100: Double? = null,
    val fatPer100: Double? = null,
    val fiberPer100: Double? = null,
    val sugarsPer100: Double? = null,
    val saltPer100: Double? = null,
    val grams: Double,
    val unit: String? = null,
    val servings: Double? = null,
    val pieceGrams: Double? = null,
    val pieceLabel: String? = null,
    val favorite: Boolean = false,
    val useCount: Int = 1,
    val lastUsedAt: Long,
)

@Serializable
data class BackupSettings(
    val kcalGoal: Int,
    val proteinGoalG: Int? = null,
    val carbsGoalG: Int? = null,
    val fatGoalG: Int? = null,
    val waterGoalMl: Int,
    val glassMl: Int,
    /** Water bottle (since 1.7.0): without [bottleMl] the current bottle is kept. */
    val bottleMl: Int? = null,
    val bottleName: String = "",
    val name: String = "",
    val sex: String? = null,
    val birthYear: Int? = null,
    val heightCm: Int? = null,
    val activity: String? = null,
    val weightGoal: String? = null,
    /** Model of Gemini, also read by the versions before 1.5.0. */
    val geminiModel: String? = null,
    /** Name of the chosen AI provider; the API keys are never exported. */
    val aiProvider: String? = null,
    /** Model by AI provider name. */
    val aiModels: Map<String, String> = emptyMap(),
    val compatibleBaseUrl: String? = null,
    val themeMode: String? = null,
    val dynamicColor: Boolean = true,
    val useCrea: Boolean = true,
    val reminders: List<BackupReminder> = emptyList(),
    val healthWrite: Boolean = true,
    val healthAddBurned: Boolean = false,
)

/** [minutes] after midnight. */
@Serializable
data class BackupReminder(val type: String, val enabled: Boolean, val minutes: Int)

/** How a backup is restored. */
enum class RestoreMode {
    /** Deletes the current data and settings and puts the backup ones. */
    REPLACE,

    /** Adds what is missing: entries without duplicates, weight and water of the days without them. */
    MERGE,
}
