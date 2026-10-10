package it.emanuelemelini.photocal.ui.entry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.FoodRepository
import it.emanuelemelini.photocal.data.SavedFoodRepository
import it.emanuelemelini.photocal.data.ai.AiException
import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.data.db.SavedFood
import it.emanuelemelini.photocal.data.db.ServingUnit
import it.emanuelemelini.photocal.data.db.Source
import it.emanuelemelini.photocal.data.estimate.FoodEstimator
import it.emanuelemelini.photocal.data.estimate.PerGram
import it.emanuelemelini.photocal.ui.EntryRoute
import it.emanuelemelini.photocal.ui.UiText
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.formatSalt
import it.emanuelemelini.photocal.ui.parseDecimal
import it.emanuelemelini.photocal.ui.toUiText
import it.emanuelemelini.photocal.ui.uiText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/** Form values exactly as typed by the user. */
data class EntryForm(
    val name: String = "",
    val mealType: MealType = MealType.suggestedFor(),
    val quantity: String = "",
    val unit: ServingUnit = ServingUnit.GRAMS,
    /** Grams of one piece, for [ServingUnit.PIECE]. */
    val pieceGrams: String = "",
    /** Name of the pieces from the package, kept from the barcode entry. */
    val pieceLabel: String? = null,
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val fiber: String = "",
    val sugars: String = "",
    val salt: String = "",
) {
    val nameValid get() = name.isNotBlank()
    val quantityValid get() = parseDecimal(quantity)?.let { it > 0 } == true
    val pieceGramsValid get() = unit != ServingUnit.PIECE || parseDecimal(pieceGrams)?.let { it > 0 } == true
    val kcalValid get() = parseDecimal(kcal)?.let { it >= 0 } == true
    val proteinValid get() = isValidOptional(protein)
    val carbsValid get() = isValidOptional(carbs)
    val fatValid get() = isValidOptional(fat)
    val fiberValid get() = isValidOptional(fiber)
    val sugarsValid get() = isValidOptional(sugars)
    val saltValid get() = isValidOptional(salt)
    val isValid get() = nameValid && quantityValid && pieceGramsValid && kcalValid && proteinValid && carbsValid && fatValid &&
        fiberValid && sugarsValid && saltValid

    /** Grams (= ml for liquids) matching the quantity in the chosen unit. */
    val grams: Double? get() = parseDecimal(quantity)?.let { unit.grams(it, parseDecimal(pieceGrams)) }

    fun sameQuantityAs(other: EntryForm?) =
        other != null && quantity == other.quantity && unit == other.unit && pieceGrams == other.pieceGrams

    /**
     * The quantity counts as the user's (and the AI estimate keeps it) when typed or changed
     * by them, or when the food is still the one of [source], the form it came from (entry
     * being edited, saved food, AI estimate; null if typed by the user). A different food
     * with an untouched quantity gets the grams estimated by the AI.
     */
    fun quantityIsTheUsers(source: EntryForm?): Boolean {
        if (source == null) return true
        return !sameQuantityAs(source) || name.trim().equals(source.name.trim(), ignoreCase = true)
    }

    private fun isValidOptional(text: String) =
        text.isBlank() || parseDecimal(text)?.let { it >= 0 } == true
}

sealed interface AiEstimate {
    data object Idle : AiEstimate
    data object Running : AiEstimate
    /** [notes] come from the AI (already in the app language); [creaNames] are the CREA foods used. */
    data class Done(val notes: String?, val creaNames: List<String>) : AiEstimate
    /** [canChangeModel]: the AI answered badly or not at all, another model may do better. */
    data class Failed(val message: UiText, val needsSettings: Boolean, val canChangeModel: Boolean = false) : AiEstimate
}

class EntryViewModel(
    savedStateHandle: SavedStateHandle,
    private val foodRepository: FoodRepository,
    private val savedFoods: SavedFoodRepository,
    private val foodEstimator: FoodEstimator,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<EntryRoute>()
    private val date = LocalDate.ofEpochDay(route.dateEpochDay)
    private var original: FoodEntry? = null
    /** Form of [original] as loaded, to know whether the quantity was changed. */
    private var loadedForm: EntryForm? = null

    val isEditing = route.entryId != 0L

    /** Saved food the form was filled from: its barcode goes with the new entry. */
    private var sourceFood: SavedFood? = null

    /** Photo of the entry (only for entries created from a photo), shown for reference. */
    var photoPath by mutableStateOf<String?>(null)
        private set

    // Compose state (not StateFlow) because TextFields must be updated synchronously
    var form by mutableStateOf(EntryForm())
        private set
    var showErrors by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(isEditing || route.savedFoodId != 0L)
        private set
    var isDone by mutableStateOf(false)
        private set
    var aiEstimate by mutableStateOf<AiEstimate>(AiEstimate.Idle)
        private set

    /** Pieces only for foods with the grams of a piece (from a barcode or a saved food). */
    var units by mutableStateOf(ServingUnit.fixedSize)
        private set

    /** Saved foods matching the name being typed, for a new entry. */
    var suggestions by mutableStateOf<List<SavedFood>>(emptyList())
        private set

    /**
     * Set after an AI estimate or when opening an existing entry: until the user edits kcal or
     * macros by hand, changing the quantity rescales the values proportionally.
     */
    private var perGram: PerGram? = null

    /**
     * Form whose quantity wasn't typed by the user: the entry being edited, the saved food or
     * the last AI estimate. null when the user typed the quantity themselves.
     */
    private var quantitySource: EntryForm? = null

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
                    loadedForm = form
                    quantitySource = form
                    if (it.servingUnit == ServingUnit.PIECE) units = ServingUnit.entries
                    photoPath = it.photoPath
                    perGram = it.grams?.takeIf { g -> g > 0 }?.let { g ->
                        PerGram(
                            it.kcal / g, it.proteinG?.div(g), it.carbsG?.div(g), it.fatG?.div(g),
                            it.fiberG?.div(g), it.sugarsG?.div(g), it.saltG?.div(g),
                        )
                    }
                }
                isLoading = false
            }
        } else {
            if (route.savedFoodId != 0L) {
                viewModelScope.launch {
                    savedFoods.get(route.savedFoodId)?.let(::useSavedFood)
                    isLoading = false
                }
            }
            viewModelScope.launch {
                snapshotFlow { form.name.trim() }
                    .distinctUntilChanged()
                    .collectLatest { name -> suggestions = suggestionsFor(name) }
            }
        }
    }

    private suspend fun suggestionsFor(name: String): List<SavedFood> {
        // Nothing to suggest once the form comes from that food
        if (name.length < SUGGESTION_MIN_CHARS || name.equals(sourceFood?.name, ignoreCase = true)) return emptyList()
        delay(SUGGESTION_DELAY_MS)
        return savedFoods.observeSearch(name, SUGGESTION_LIMIT).first()
    }

    /** Fills the form with a saved food, keeping the meal: values follow the quantity. */
    fun useSavedFood(food: SavedFood) {
        sourceFood = food
        suggestions = emptyList()
        form = food.toForm(form.mealType)
        quantitySource = form
        perGram = PerGram(
            food.kcalPer100 / 100, food.proteinPer100?.div(100), food.carbsPer100?.div(100), food.fatPer100?.div(100),
            food.fiberPer100?.div(100), food.sugarsPer100?.div(100), food.saltPer100?.div(100),
        )
        units = if (food.pieceGrams != null) ServingUnit.entries else ServingUnit.fixedSize
    }

    fun onFormChange(newForm: EntryForm) {
        val old = form
        val ratios = perGram
        form = when {
            ratios == null -> newForm
            // Values edited by hand: from now on the user is in control
            newForm.kcal != old.kcal || newForm.protein != old.protein || newForm.carbs != old.carbs ||
                newForm.fat != old.fat || newForm.fiber != old.fiber || newForm.sugars != old.sugars ||
                newForm.salt != old.salt -> {
                perGram = null
                newForm
            }
            newForm.quantity != old.quantity || newForm.unit != old.unit || newForm.pieceGrams != old.pieceGrams ->
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
        val userGrams = current.grams?.takeIf { current.quantityValid && current.quantityIsTheUsers(quantitySource) }
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
                        fiber = items.sumOfOrNull { (item, r) -> r.fiber?.times(item.grams) }?.div(totalGrams),
                        sugars = items.sumOfOrNull { (item, r) -> r.sugars?.times(item.grams) }?.div(totalGrams),
                        salt = items.sumOfOrNull { (item, r) -> r.salt?.times(item.grams) }?.div(totalGrams),
                    )
                    perGram = ratios
                    // The quantity typed by the user wins over the estimated one
                    form = if (userGrams != null) {
                        form.scaledTo(userGrams, ratios)
                    } else {
                        form.copy(quantity = totalGrams.formatAmount(), unit = ServingUnit.GRAMS, pieceGrams = "", pieceLabel = null)
                            .scaledTo(totalGrams, ratios).also { quantitySource = it }
                    }
                    AiEstimate.Done(
                        notes = result.notes.ifBlank { null },
                        creaNames = items.mapNotNull { (item, _) -> item.crea?.name },
                    )
                }
            } catch (e: AiException) {
                AiEstimate.Failed(e.toUiText(), e.needsSettings, e.anotherModelMayHelp)
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
        // The grams of a piece are shown rounded: an untouched quantity keeps the saved grams
        val grams = original?.grams?.takeIf { current.sameQuantityAs(loadedForm) } ?: current.grams
        // For grams the grams column is enough; for other units the choice is stored too
        val servingUnit = current.unit.takeIf { it != ServingUnit.GRAMS }
        val servings = if (servingUnit != null) parseDecimal(current.quantity) else null
        val servingLabel = current.pieceLabel.takeIf { servingUnit == ServingUnit.PIECE }
        val kcal = parseDecimal(current.kcal)!!
        val protein = parseDecimal(current.protein)
        val carbs = parseDecimal(current.carbs)
        val fat = parseDecimal(current.fat)
        val fiber = parseDecimal(current.fiber)
        val sugars = parseDecimal(current.sugars)
        val salt = parseDecimal(current.salt)

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
                        servingLabel = servingLabel,
                        fiberG = fiber,
                        sugarsG = sugars,
                        saltG = salt,
                    )
                )
            } else {
                // Same product from a barcode, unless it was renamed into another food
                val barcode = sourceFood?.barcode?.takeIf { sourceFood?.name.equals(name, ignoreCase = true) }
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
                        source = if (barcode != null) Source.BARCODE else Source.MANUAL,
                        photoPath = null,
                        createdAt = Instant.now(),
                        servingUnit = servingUnit,
                        servings = servings,
                        servingLabel = servingLabel,
                        fiberG = fiber,
                        sugarsG = sugars,
                        saltG = salt,
                    ),
                    barcode,
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
        fiber = ratios.fiber?.let { (it * grams).formatAmount() } ?: fiber,
        sugars = ratios.sugars?.let { (it * grams).formatAmount() } ?: sugars,
        salt = ratios.salt?.let { (it * grams).formatSalt() } ?: salt,
    )

    /** Total amount for the AI prompt: grams, or ml for liquid units. */
    private fun describeQuantity(form: EntryForm, grams: Double): String =
        "${grams.formatAmount()} ${if (form.unit.isLiquid) "ml" else "g"}"

    private fun <T> List<T>.sumOfOrNull(selector: (T) -> Double?): Double? =
        mapNotNull(selector).takeIf { it.isNotEmpty() }?.sum()

    private fun SavedFood.toForm(meal: MealType): EntryForm {
        // A unit without the number of servings can't be shown: back to grams
        val unit = servingUnit?.takeIf { servings != null } ?: ServingUnit.GRAMS
        val grams = grams
        return EntryForm(
            name = name,
            mealType = meal,
            quantity = (if (unit == ServingUnit.GRAMS) grams else servings)?.formatAmount().orEmpty(),
            unit = unit,
            pieceGrams = pieceGrams?.formatAmount().orEmpty(),
            pieceLabel = pieceLabel,
            kcal = (kcalPer100 * grams / 100).formatAmount(),
            protein = proteinPer100?.let { it * grams / 100 }?.formatAmount().orEmpty(),
            carbs = carbsPer100?.let { it * grams / 100 }?.formatAmount().orEmpty(),
            fat = fatPer100?.let { it * grams / 100 }?.formatAmount().orEmpty(),
            fiber = fiberPer100?.let { it * grams / 100 }?.formatAmount().orEmpty(),
            sugars = sugarsPer100?.let { it * grams / 100 }?.formatAmount().orEmpty(),
            salt = saltPer100?.let { it * grams / 100 }?.formatSalt().orEmpty(),
        )
    }

    private fun FoodEntry.toForm(): EntryForm {
        val unit = servingUnit ?: ServingUnit.GRAMS
        val quantity = if (unit != ServingUnit.GRAMS) servings else grams
        // The grams of a piece aren't stored: they come from the total and the count
        val gramsPerPiece = if (unit == ServingUnit.PIECE && grams != null && servings != null && servings > 0) grams / servings else null
        return EntryForm(
            name = name,
            mealType = mealType,
            quantity = quantity?.formatAmount().orEmpty(),
            unit = unit,
            pieceGrams = gramsPerPiece?.formatAmount().orEmpty(),
            pieceLabel = servingLabel,
            kcal = kcal.formatAmount(),
            protein = proteinG?.formatAmount().orEmpty(),
            carbs = carbsG?.formatAmount().orEmpty(),
            fat = fatG?.formatAmount().orEmpty(),
            fiber = fiberG?.formatAmount().orEmpty(),
            sugars = sugarsG?.formatAmount().orEmpty(),
            salt = saltG?.formatSalt().orEmpty(),
        )
    }

    private companion object {
        const val SUGGESTION_MIN_CHARS = 2
        const val SUGGESTION_LIMIT = 5
        const val SUGGESTION_DELAY_MS = 250L
    }
}
