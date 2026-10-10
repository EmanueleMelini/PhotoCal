package it.emanuelemelini.photocal.data.estimate

import it.emanuelemelini.photocal.data.crea.CreaFood
import it.emanuelemelini.photocal.data.crea.CreaTable
import it.emanuelemelini.photocal.data.ai.AiService
import it.emanuelemelini.photocal.data.ai.AnalyzedFood
import it.emanuelemelini.photocal.data.ai.FoodAnalysis
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first

/** Kcal, macros, fiber, sugars and salt per gram of food. */
data class PerGram(
    val kcal: Double,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val fiber: Double? = null,
    val sugars: Double? = null,
    val salt: Double? = null,
)

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
    val aiFiberG: Double?,
    val aiSugarsG: Double?,
    val aiSaltG: Double?,
    val aiPerGram: PerGram?,
    val crea: CreaFood?,
    /** Meal the AI put the food in, only for the description of a day. */
    val meal: MealType? = null,
) {
    val creaPerGram: PerGram?
        get() = crea?.let {
            PerGram(
                it.kcal / 100, it.proteinG?.div(100), it.carbsG?.div(100), it.fatG?.div(100),
                it.fiberG?.div(100), it.sugarsG?.div(100), it.saltG?.div(100),
            )
        }

    /** Values to use: CREA when there is a match, otherwise the AI estimate. */
    val perGram: PerGram? get() = creaPerGram ?: aiPerGram
}

data class FoodEstimate(val items: List<EstimatedFood>, val notes: String)

/**
 * Estimates with the AI chosen in Settings and, when enabled in Settings, replaces the values with the CREA
 * tables: the AI recognizes food and grams, the kcal come from the table.
 */
class FoodEstimator(
    private val aiService: AiService,
    private val creaTable: CreaTable,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun analyzePhoto(jpeg: ByteArray, userNotes: String): FoodEstimate =
        resolve(aiService.analyzePhoto(jpeg, userNotes, creaCatalog()))

    suspend fun estimateFromText(description: String, quantity: String?): FoodEstimate =
        resolve(aiService.estimateFromText(description, quantity, creaCatalog()))

    suspend fun describeDay(text: String, splitMeals: Boolean): FoodEstimate =
        resolve(aiService.describeDay(text, splitMeals, creaCatalog()))

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
            aiFiberG = fiberG,
            aiSugarsG = sugarsG,
            aiSaltG = saltG,
            aiPerGram = g?.let {
                PerGram(kcal / it, proteinG?.div(it), carbsG?.div(it), fatG?.div(it), fiberG?.div(it), sugarsG?.div(it), saltG?.div(it))
            },
            // The code picked by the AI counts only if it really exists in the table
            crea = creaCode?.takeIf { it.isNotBlank() }?.let { creaTable.get(it) },
            meal = when (meal) {
                "breakfast" -> MealType.BREAKFAST
                "lunch" -> MealType.LUNCH
                "dinner" -> MealType.DINNER
                "snack" -> MealType.SNACK
                else -> null
            },
        )
    }
}
