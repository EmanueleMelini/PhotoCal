package it.emanuelemelini.photocal.ui.entry

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
import it.emanuelemelini.photocal.data.db.ServingUnit
import it.emanuelemelini.photocal.data.db.Source
import it.emanuelemelini.photocal.data.estimate.FoodEstimator
import it.emanuelemelini.photocal.data.estimate.PerGram
import it.emanuelemelini.photocal.data.gemini.GeminiException
import it.emanuelemelini.photocal.ui.EntryRoute
import it.emanuelemelini.photocal.ui.UiText
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.parseDecimal
import it.emanuelemelini.photocal.ui.toUiText
import it.emanuelemelini.photocal.ui.uiText
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/** Form values exactly as typed by the user. */
data class EntryForm(
    val name: String = "",
    val mealType: MealType = MealType.suggestedFor(),
    val quantity: String = "",
    val unit: ServingUnit = ServingUnit.GRAMS,
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
) {
    val nameValid get() = name.isNotBlank()
    val quantityValid get() = parseDecimal(quantity)?.let { it > 0 } == true
    val kcalValid get() = parseDecimal(kcal)?.let { it >= 0 } == true
    val proteinValid get() = isValidOptional(protein)
    val carbsValid get() = isValidOptional(carbs)
    val fatValid get() = isValidOptional(fat)
    val isValid get() = nameValid && quantityValid && kcalValid && proteinValid && carbsValid && fatValid

    /** Grams (= ml for liquids) matching the quantity in the chosen unit. */
    val grams: Double? get() = parseDecimal(quantity)?.let { it * unit.gramsPerUnit }

    private fun isValidOptional(text: String) =
        text.isBlank() || parseDecimal(text)?.let { it >= 0 } == true
}

sealed interface AiEstimate {
    data object Idle : AiEstimate
    data object Running : AiEstimate
    /** [notes] come from the AI (already in the app language); [creaNames] are the CREA foods used. */
    data class Done(val notes: String?, val creaNames: List<String>) : AiEstimate
    data class Failed(val message: UiText, val needsSettings: Boolean) : AiEstimate
}

class EntryViewModel(
    savedStateHandle: SavedStateHandle,
    private val foodRepository: FoodRepository,
    private val foodEstimator: FoodEstimator,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<EntryRoute>()
    private val date = LocalDate.ofEpochDay(route.dateEpochDay)
    private var original: FoodEntry? = null

    val isEditing = route.entryId != 0L

    /** Photo of the entry (only for entries created from a photo), shown for reference. */
    var photoPath by mutableStateOf<String?>(null)
        private set

    // Compose state (not StateFlow) because TextFields must be updated synchronously
    var form by mutableStateOf(EntryForm())
        private set
    var showErrors by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(isEditing)
        private set
    var isDone by mutableStateOf(false)
        private set
    var aiEstimate by mutableStateOf<AiEstimate>(AiEstimate.Idle)
        private set

    /**
     * Set after an AI estimate or when opening an existing entry: until the user edits kcal or
     * macros by hand, changing the quantity rescales the values proportionally.
     */
    private var perGram: PerGram? = null

    /** true if kcal and macros update by themselves when the quantity changes. */
    val autoScales: Boolean get() = perGram != null

    init {
        route.prefillName?.let { form = form.copy(name = it) }
        MealType.entries.find { it.name == route.meal }?.let { form = form.copy(mealType = it) }
        if (isEditing) {
            viewModelScope.launch {
                original = foodRepository.get(route.entryId)
                original?.let {
                    form = it.toForm()
                    photoPath = it.photoPath
                    perGram = it.grams?.takeIf { g -> g > 0 }?.let { g ->
                        PerGram(it.kcal / g, it.proteinG?.div(g), it.carbsG?.div(g), it.fatG?.div(g))
                    }
                }
                isLoading = false
            }
        }
    }

    fun onFormChange(newForm: EntryForm) {
        val old = form
        val ratios = perGram
        form = when {
            ratios == null -> newForm
            // Values edited by hand: from now on the user is in control
            newForm.kcal != old.kcal || newForm.protein != old.protein ||
                newForm.carbs != old.carbs || newForm.fat != old.fat -> {
                perGram = null
                newForm
            }
            newForm.quantity != old.quantity || newForm.unit != old.unit ->
                newForm.grams?.takeIf { it > 0 }?.let { newForm.scaledTo(it, ratios) } ?: newForm
            else -> newForm
        }
    }

    /** Estimates kcal and macros with Gemini from the name (and the quantity, if any). */
    fun estimateWithAi() {
        val current = form
        if (!current.nameValid) {
            aiEstimate = AiEstimate.Failed(uiText(R.string.entry_ai_name_first), false)
            return
        }
        if (aiEstimate == AiEstimate.Running) return
        aiEstimate = AiEstimate.Running
        val userGrams = current.grams?.takeIf { current.quantityValid }
        val quantityText = userGrams?.let { describeQuantity(current, it) }

        viewModelScope.launch {
            aiEstimate = try {
                val result = foodEstimator.estimateFromText(current.name, quantityText)
                // CREA values when the AI found the food, otherwise its estimate
                val items = result.items.mapNotNull { item ->
                    item.perGram?.takeIf { item.grams > 0 }?.let { ratios -> item to ratios }
                }
                val totalGrams = items.sumOf { (item, _) -> item.grams }
                if (items.isEmpty()) {
                    AiEstimate.Failed(
                        result.notes.takeIf { it.isNotBlank() }?.let(UiText::Raw) ?: uiText(R.string.entry_ai_nothing_found),
                        false,
                    )
                } else {
                    // Several foods in a single entry: they are summed up
                    val ratios = PerGram(
                        kcal = items.sumOf { (item, r) -> r.kcal * item.grams } / totalGrams,
                        protein = items.sumOfOrNull { (item, r) -> r.protein?.times(item.grams) }?.div(totalGrams),
                        carbs = items.sumOfOrNull { (item, r) -> r.carbs?.times(item.grams) }?.div(totalGrams),
                        fat = items.sumOfOrNull { (item, r) -> r.fat?.times(item.grams) }?.div(totalGrams),
                    )
                    perGram = ratios
                    // The quantity typed by the user wins over the estimated one
                    form = if (userGrams != null) {
                        form.scaledTo(userGrams, ratios)
                    } else {
                        form.copy(quantity = totalGrams.formatAmount(), unit = ServingUnit.GRAMS)
                            .scaledTo(totalGrams, ratios)
                    }
                    AiEstimate.Done(
                        notes = result.notes.ifBlank { null },
                        creaNames = items.mapNotNull { (item, _) -> item.crea?.name },
                    )
                }
            } catch (e: GeminiException) {
                AiEstimate.Failed(e.toUiText(), e.needsSettings)
            }
        }
    }

    fun save() {
        val current = form
        if (!current.isValid) {
            showErrors = true
            return
        }
        val name = current.name.trim()
        val grams = current.grams
        // For grams the grams column is enough; for other units the choice is stored too
        val servingUnit = current.unit.takeIf { it != ServingUnit.GRAMS }
        val servings = if (servingUnit != null) parseDecimal(current.quantity) else null
        val kcal = parseDecimal(current.kcal)!!
        val protein = parseDecimal(current.protein)
        val carbs = parseDecimal(current.carbs)
        val fat = parseDecimal(current.fat)

        viewModelScope.launch {
            val existing = original
            if (existing != null) {
                foodRepository.update(
                    existing.copy(
                        name = name,
                        mealType = current.mealType,
                        grams = grams,
                        kcal = kcal,
                        proteinG = protein,
                        carbsG = carbs,
                        fatG = fat,
                        servingUnit = servingUnit,
                        servings = servings,
                    )
                )
            } else {
                foodRepository.add(
                    FoodEntry(
                        date = date,
                        mealType = current.mealType,
                        name = name,
                        grams = grams,
                        kcal = kcal,
                        proteinG = protein,
                        carbsG = carbs,
                        fatG = fat,
                        source = Source.MANUAL,
                        photoPath = null,
                        createdAt = Instant.now(),
                        servingUnit = servingUnit,
                        servings = servings,
                    )
                )
            }
            isDone = true
        }
    }

    fun delete() {
        val existing = original ?: return
        viewModelScope.launch {
            foodRepository.delete(existing)
            isDone = true
        }
    }

    private fun EntryForm.scaledTo(grams: Double, ratios: PerGram) = copy(
        kcal = (ratios.kcal * grams).formatAmount(),
        protein = ratios.protein?.let { (it * grams).formatAmount() } ?: protein,
        carbs = ratios.carbs?.let { (it * grams).formatAmount() } ?: carbs,
        fat = ratios.fat?.let { (it * grams).formatAmount() } ?: fat,
    )

    /** Total amount for the AI prompt: grams, or ml for liquid units. */
    private fun describeQuantity(form: EntryForm, grams: Double): String =
        "${grams.formatAmount()} ${if (form.unit.isLiquid) "ml" else "g"}"

    private fun <T> List<T>.sumOfOrNull(selector: (T) -> Double?): Double? =
        mapNotNull(selector).takeIf { it.isNotEmpty() }?.sum()

    private fun FoodEntry.toForm(): EntryForm {
        val unit = servingUnit ?: ServingUnit.GRAMS
        val quantity = if (unit != ServingUnit.GRAMS) servings else grams
        return EntryForm(
            name = name,
            mealType = mealType,
            quantity = quantity?.formatAmount().orEmpty(),
            unit = unit,
            kcal = kcal.formatAmount(),
            protein = proteinG?.formatAmount().orEmpty(),
            carbs = carbsG?.formatAmount().orEmpty(),
            fat = fatG?.formatAmount().orEmpty(),
        )
    }
}
