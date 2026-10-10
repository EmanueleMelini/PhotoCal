package it.emanuelemelini.photocal.ui.describe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.FoodRepository
import it.emanuelemelini.photocal.data.ai.AiException
import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.data.db.Source
import it.emanuelemelini.photocal.data.estimate.FoodEstimator
import it.emanuelemelini.photocal.ui.DescribeRoute
import it.emanuelemelini.photocal.ui.parseDecimal
import it.emanuelemelini.photocal.ui.photo.ReviewItem
import it.emanuelemelini.photocal.ui.photo.ReviewStatus
import it.emanuelemelini.photocal.ui.photo.toReviewItem
import it.emanuelemelini.photocal.ui.toUiText
import it.emanuelemelini.photocal.ui.uiText
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/** A food of the description, with the meal it goes to. */
data class DescribedItem(val item: ReviewItem, val meal: MealType)

/**
 * Several foods at once from a text, typed or dictated: the AI estimates them and, without
 * a [fixedMeal], also splits them into meals. The user reviews them before saving.
 */
class DescribeViewModel(
    savedStateHandle: SavedStateHandle,
    private val foodRepository: FoodRepository,
    private val foodEstimator: FoodEstimator,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<DescribeRoute>()
    private val date = LocalDate.ofEpochDay(route.dateEpochDay)
    private var nextKey = 0L

    /** Meal chosen from its + in Today: every food goes there. */
    val fixedMeal: MealType? = MealType.entries.find { it.name == route.meal }

    // Compose state (not StateFlow) because TextFields must be updated synchronously
    var text by mutableStateOf("")
        private set
    var items by mutableStateOf<List<DescribedItem>>(emptyList())
        private set
    var aiNotes by mutableStateOf<String?>(null)
        private set
    var hasAnalyzed by mutableStateOf(false)
        private set
    var status by mutableStateOf<ReviewStatus>(ReviewStatus.Ready)
        private set
    var showErrors by mutableStateOf(false)
        private set
    var isDone by mutableStateOf(false)
        private set

    val totalKcal: Double get() = items.sumOf { parseDecimal(it.item.kcal) ?: 0.0 }

    val usesCrea: Boolean get() = items.any { it.item.usingCrea }

    /** Foods grouped by meal, in the order of the day. */
    val groups: List<Pair<MealType, List<DescribedItem>>>
        get() = MealType.entries.mapNotNull { meal ->
            items.filter { it.meal == meal }.takeIf { it.isNotEmpty() }?.let { meal to it }
        }

    fun onTextChange(value: String) {
        text = value
    }

    /** Dictated text goes after what is already there, so the user can speak in parts. */
    fun appendDictation(spoken: String) {
        val words = spoken.trim()
        if (words.isEmpty()) return
        text = if (text.isBlank()) words else text.trimEnd() + " " + words
    }

    fun analyze() {
        if (text.isBlank()) {
            status = ReviewStatus.Error(uiText(R.string.describe_text_first), needsSettings = false, canRetry = false)
            return
        }
        if (status == ReviewStatus.Analyzing) return
        status = ReviewStatus.Analyzing
        viewModelScope.launch {
            status = try {
                val result = foodEstimator.describeDay(text, splitMeals = fixedMeal == null)
                // Foods without a meal in the text go where the time of day suggests
                val fallback = MealType.suggestedFor()
                items = result.items.map { DescribedItem(it.toReviewItem(nextKey++), fixedMeal ?: it.meal ?: fallback) }
                aiNotes = result.notes.ifBlank { null }
                hasAnalyzed = true
                showErrors = false
                ReviewStatus.Ready
            } catch (e: AiException) {
                ReviewStatus.Error(e.toUiText(), e.needsSettings, canChangeModel = e.anotherModelMayHelp)
            }
        }
    }

    fun updateItem(key: Long, transform: (ReviewItem) -> ReviewItem) {
        items = items.map { if (it.item.key == key) it.copy(item = transform(it.item)) else it }
    }

    fun changeMeal(key: Long, meal: MealType) {
        items = items.map { if (it.item.key == key) it.copy(meal = meal) else it }
    }

    fun removeItem(key: Long) {
        items = items.filterNot { it.item.key == key }
    }

    /** Empty row, in [meal]: also used for manual entry when the AI fails. */
    fun addItem(meal: MealType = fixedMeal ?: MealType.suggestedFor()) {
        items = items + DescribedItem(ReviewItem(key = nextKey++), meal)
        hasAnalyzed = true
    }

    fun dismissError() {
        if (status is ReviewStatus.Error) status = ReviewStatus.Ready
    }

    fun save() {
        val current = items
        if (current.isEmpty()) return
        if (current.any { !it.item.isValid }) {
            showErrors = true
            return
        }
        viewModelScope.launch {
            val now = Instant.now()
            foodRepository.addAll(
                current.mapIndexed { index, (item, meal) ->
                    val (fiber, sugars, salt) = item.extras()
                    FoodEntry(
                        date = date,
                        mealType = meal,
                        name = item.name.trim(),
                        grams = parseDecimal(item.grams),
                        kcal = parseDecimal(item.kcal)!!,
                        proteinG = parseDecimal(item.protein),
                        carbsG = parseDecimal(item.carbs),
                        fatG = parseDecimal(item.fat),
                        // Like a manual entry estimated with the AI
                        source = Source.MANUAL,
                        photoPath = null,
                        // Staggered milliseconds to keep the row order in the diary
                        createdAt = now.plusMillis(index.toLong()),
                        fiberG = fiber,
                        sugarsG = sugars,
                        saltG = salt,
                    )
                }
            )
            isDone = true
        }
    }
}
