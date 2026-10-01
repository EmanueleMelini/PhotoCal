package it.emanuelemelini.photocal.ui.photo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.FoodRepository
import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.data.db.Source
import it.emanuelemelini.photocal.data.estimate.EstimatedFood
import it.emanuelemelini.photocal.data.estimate.FoodEstimator
import it.emanuelemelini.photocal.data.estimate.PerGram
import it.emanuelemelini.photocal.data.gemini.GeminiException
import it.emanuelemelini.photocal.data.photo.PhotoStorage
import it.emanuelemelini.photocal.ui.PhotoReviewRoute
import it.emanuelemelini.photocal.ui.UiText
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.parseDecimal
import it.emanuelemelini.photocal.ui.toUiText
import it.emanuelemelini.photocal.ui.uiText
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

/**
 * Editable row of the food list. [perGram] (from the chosen source or the last manual edit)
 * is used to recompute kcal and macros when grams change.
 */
data class ReviewItem(
    val key: Long,
    val name: String = "",
    val grams: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val confidence: String? = null,
    val perGram: PerGram? = null,
    /** AI estimate and CREA values, to switch between them. */
    val aiPerGram: PerGram? = null,
    val creaPerGram: PerGram? = null,
    val creaName: String? = null,
    val usingCrea: Boolean = false,
) {
    val nameValid get() = name.isNotBlank()
    val gramsValid get() = grams.isBlank() || parseDecimal(grams)?.let { it > 0 } == true
    val kcalValid get() = parseDecimal(kcal)?.let { it >= 0 } == true
    val proteinValid get() = isValidOptional(protein)
    val carbsValid get() = isValidOptional(carbs)
    val fatValid get() = isValidOptional(fat)
    val isValid get() = nameValid && gramsValid && kcalValid && proteinValid && carbsValid && fatValid

    fun withGrams(text: String): ReviewItem {
        val g = parseDecimal(text)?.takeIf { it > 0 } ?: return copy(grams = text)
        return copy(grams = text).applying(perGram, g)
    }

    fun withKcal(text: String) = copy(kcal = text).updatingRatios { it.copy(kcal = perGram(text) ?: it.kcal) }
    fun withProtein(text: String) = copy(protein = text).updatingRatios { it.copy(protein = perGram(text) ?: it.protein) }
    fun withCarbs(text: String) = copy(carbs = text).updatingRatios { it.copy(carbs = perGram(text) ?: it.carbs) }
    fun withFat(text: String) = copy(fat = text).updatingRatios { it.copy(fat = perGram(text) ?: it.fat) }

    /** Updates the edited value in the ratios; for a manual row creates them from the fields. */
    private fun updatingRatios(update: (PerGram) -> PerGram): ReviewItem =
        copy(perGram = perGram?.let(update) ?: ratiosFromFields())

    private fun ratiosFromFields(): PerGram? =
        perGram(kcal)?.let { PerGram(it, perGram(protein), perGram(carbs), perGram(fat)) }

    /** Switches from CREA values to the AI estimate (or vice versa) keeping the grams. */
    fun withCrea(enabled: Boolean): ReviewItem {
        val source = (if (enabled) creaPerGram else aiPerGram) ?: return this
        val g = parseDecimal(grams)?.takeIf { it > 0 } ?: return copy(usingCrea = enabled, perGram = source)
        return copy(usingCrea = enabled).applying(source, g)
    }

    private fun applying(ratios: PerGram?, g: Double): ReviewItem {
        if (ratios == null) return this
        return copy(
            perGram = ratios,
            kcal = (ratios.kcal * g).formatAmount(),
            protein = ratios.protein?.let { (it * g).formatAmount() } ?: protein,
            carbs = ratios.carbs?.let { (it * g).formatAmount() } ?: carbs,
            fat = ratios.fat?.let { (it * g).formatAmount() } ?: fat,
        )
    }

    /**
     * Fiber, sugars and salt for the grams of the row, from the ratios of the chosen source
     * (not editable here: they can be changed later from the diary).
     */
    fun extras(): Triple<Double?, Double?, Double?> {
        val g = parseDecimal(grams)?.takeIf { it > 0 } ?: return Triple(null, null, null)
        return Triple(perGram?.fiber?.times(g), perGram?.sugars?.times(g), perGram?.salt?.times(g))
    }

    private fun perGram(valueText: String): Double? {
        val value = parseDecimal(valueText) ?: return null
        val g = parseDecimal(grams)?.takeIf { it > 0 } ?: return null
        return value / g
    }

    private fun isValidOptional(text: String) =
        text.isBlank() || parseDecimal(text)?.let { it >= 0 } == true
}

sealed interface ReviewStatus {
    data object Preparing : ReviewStatus
    data object Ready : ReviewStatus
    data object Analyzing : ReviewStatus
    data class Error(
        val message: UiText,
        val needsSettings: Boolean,
        val canRetry: Boolean = true,
    ) : ReviewStatus
}

class PhotoReviewViewModel(
    savedStateHandle: SavedStateHandle,
    private val foodRepository: FoodRepository,
    private val photoStorage: PhotoStorage,
    private val foodEstimator: FoodEstimator,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<PhotoReviewRoute>()
    private val date = LocalDate.ofEpochDay(route.dateEpochDay)
    val photoPath: String = route.photoPath

    private var jpeg: ByteArray? = null
    private var nextKey = 0L
    private var saved = false

    // Compose state (not StateFlow) because TextFields must be updated synchronously
    var notes by mutableStateOf("")
        private set
    var mealType by mutableStateOf(MealType.entries.find { it.name == route.meal } ?: MealType.suggestedFor())
        private set
    var items by mutableStateOf<List<ReviewItem>>(emptyList())
        private set
    var aiNotes by mutableStateOf<String?>(null)
        private set
    var hasAnalyzed by mutableStateOf(false)
        private set
    var status by mutableStateOf<ReviewStatus>(ReviewStatus.Preparing)
        private set
    var showErrors by mutableStateOf(false)
        private set
    var isDone by mutableStateOf(false)
        private set

    /** false if the photo can't be read: only manual entry is left. */
    var canAnalyze by mutableStateOf(false)
        private set

    /** true if at least one row uses CREA values (to show the source). */
    val usesCrea: Boolean get() = items.any { it.usingCrea }

    val totalKcal: Double get() = items.sumOf { parseDecimal(it.kcal) ?: 0.0 }

    init {
        viewModelScope.launch {
            status = try {
                jpeg = photoStorage.shrink(photoPath)
                canAnalyze = true
                ReviewStatus.Ready
            } catch (_: IOException) {
                ReviewStatus.Error(
                    uiText(R.string.photo_unreadable),
                    needsSettings = false,
                    canRetry = false,
                )
            }
        }
    }

    fun onNotesChange(value: String) {
        notes = value
    }

    fun onMealTypeChange(value: MealType) {
        mealType = value
    }

    fun analyze() {
        val image = jpeg ?: return
        if (status == ReviewStatus.Analyzing) return
        status = ReviewStatus.Analyzing
        viewModelScope.launch {
            status = try {
                val result = foodEstimator.analyzePhoto(image, notes)
                items = result.items.map { it.toReviewItem() }
                aiNotes = result.notes.ifBlank { null }
                hasAnalyzed = true
                showErrors = false
                ReviewStatus.Ready
            } catch (e: GeminiException) {
                ReviewStatus.Error(e.toUiText(), e.needsSettings)
            }
        }
    }

    fun updateItem(key: Long, transform: (ReviewItem) -> ReviewItem) {
        items = items.map { if (it.key == key) transform(it) else it }
    }

    fun removeItem(key: Long) {
        items = items.filterNot { it.key == key }
    }

    /** Adds an empty row: also used for manual entry when the AI fails. */
    fun addItem() {
        items = items + ReviewItem(key = nextKey++)
        hasAnalyzed = true
    }

    fun dismissError() {
        if (status is ReviewStatus.Error) status = ReviewStatus.Ready
    }

    fun save() {
        val current = items
        if (current.isEmpty()) return
        if (current.any { !it.isValid }) {
            showErrors = true
            return
        }
        viewModelScope.launch {
            val now = Instant.now()
            foodRepository.addAll(
                current.mapIndexed { index, item ->
                    val (fiber, sugars, salt) = item.extras()
                    FoodEntry(
                        date = date,
                        mealType = mealType,
                        name = item.name.trim(),
                        grams = parseDecimal(item.grams),
                        kcal = parseDecimal(item.kcal)!!,
                        proteinG = parseDecimal(item.protein),
                        carbsG = parseDecimal(item.carbs),
                        fatG = parseDecimal(item.fat),
                        source = Source.PHOTO,
                        photoPath = photoPath,
                        // Staggered milliseconds to keep the row order in the diary
                        createdAt = now.plusMillis(index.toLong()),
                        fiberG = fiber,
                        sugarsG = sugars,
                        saltG = salt,
                    )
                }
            )
            saved = true
            isDone = true
        }
    }

    override fun onCleared() {
        // Analysis abandoned: the photo is no longer needed
        if (!saved) photoStorage.delete(photoPath)
    }

    private fun EstimatedFood.toReviewItem(): ReviewItem {
        val g = grams.takeIf { it > 0 }
        val base = ReviewItem(
            key = nextKey++,
            name = name,
            grams = g?.formatAmount().orEmpty(),
            kcal = aiKcal.formatAmount(),
            protein = aiProteinG?.formatAmount().orEmpty(),
            carbs = aiCarbsG?.formatAmount().orEmpty(),
            fat = aiFatG?.formatAmount().orEmpty(),
            confidence = confidence,
            perGram = aiPerGram,
            aiPerGram = aiPerGram,
            creaPerGram = creaPerGram,
            creaName = crea?.name,
        )
        // If there is a CREA food, start from its values
        return if (creaPerGram != null) base.withCrea(true) else base
    }
}
