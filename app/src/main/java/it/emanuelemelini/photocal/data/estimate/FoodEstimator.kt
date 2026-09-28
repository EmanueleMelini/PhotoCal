package it.emanuelemelini.photocal.data.estimate

import it.emanuelemelini.photocal.data.crea.CreaFood
import it.emanuelemelini.photocal.data.crea.CreaTable
import it.emanuelemelini.photocal.data.gemini.AnalyzedFood
import it.emanuelemelini.photocal.data.gemini.FoodAnalysis
import it.emanuelemelini.photocal.data.gemini.GeminiClient
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first

/** Kcal and macros per gram of food. */
data class PerGram(val kcal: Double, val protein: Double?, val carbs: Double?, val fat: Double?)

/** Estimated food: AI values and, if found, the matching CREA food. */
data class EstimatedFood(
    val name: String,
    val grams: Double,
    val confidence: String?,
    /** Absolute values estimated by the AI for [grams] (used when grams are missing). */
    val aiKcal: Double,
    val aiProteinG: Double?,
    val aiCarbsG: Double?,
    val aiFatG: Double?,
    val aiPerGram: PerGram?,
    val crea: CreaFood?,
) {
    val creaPerGram: PerGram?
        get() = crea?.let { PerGram(it.kcal / 100, it.proteinG?.div(100), it.carbsG?.div(100), it.fatG?.div(100)) }

    /** Values to use: CREA when there is a match, otherwise the AI estimate. */
    val perGram: PerGram? get() = creaPerGram ?: aiPerGram
}

data class FoodEstimate(val items: List<EstimatedFood>, val notes: String)

/**
 * Estimates with Gemini and, when enabled in Settings, replaces the values with the CREA
 * tables: the AI recognizes food and grams, the kcal come from the table.
 */
class FoodEstimator(
    private val geminiClient: GeminiClient,
    private val creaTable: CreaTable,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun analyzePhoto(jpeg: ByteArray, userNotes: String): FoodEstimate =
        resolve(geminiClient.analyzePhoto(jpeg, userNotes, creaCatalog()))

    suspend fun estimateFromText(description: String, quantity: String?): FoodEstimate =
        resolve(geminiClient.estimateFromText(description, quantity, creaCatalog()))

    private suspend fun creaCatalog(): String? =
        if (settingsRepository.settings.first().useCrea) creaTable.catalog() else null

    private suspend fun resolve(analysis: FoodAnalysis): FoodEstimate = FoodEstimate(
        items = analysis.items.map { it.toEstimated() },
        notes = analysis.notes,
    )

    private suspend fun AnalyzedFood.toEstimated(): EstimatedFood {
        val g = grams.takeIf { it > 0 }
        return EstimatedFood(
            name = name,
            grams = grams,
            confidence = confidence,
            aiKcal = kcal,
            aiProteinG = proteinG,
            aiCarbsG = carbsG,
            aiFatG = fatG,
            aiPerGram = g?.let { PerGram(kcal / it, proteinG?.div(it), carbsG?.div(it), fatG?.div(it)) },
            // The code picked by the AI counts only if it really exists in the table
            crea = creaCode?.takeIf { it.isNotBlank() }?.let { creaTable.get(it) },
        )
    }
}
