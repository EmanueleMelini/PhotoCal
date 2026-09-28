package it.emanuelemelini.photocal.ui.barcode

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import it.emanuelemelini.photocal.data.FoodRepository
import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.data.db.ServingUnit
import it.emanuelemelini.photocal.data.db.Source
import it.emanuelemelini.photocal.data.openfoodfacts.OpenFoodFactsClient
import it.emanuelemelini.photocal.data.openfoodfacts.Product
import it.emanuelemelini.photocal.data.openfoodfacts.ProductLookupException
import it.emanuelemelini.photocal.ui.BarcodeRoute
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.parseDecimal
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

sealed interface BarcodeStatus {
    /** Waiting for a scan (scanner closed or cancelled). */
    data object Idle : BarcodeStatus
    data class Loading(val barcode: String) : BarcodeStatus
    data class Found(val product: Product) : BarcodeStatus
    data class NotFound(val barcode: String) : BarcodeStatus
    data class Error(val message: String, val barcode: String?) : BarcodeStatus
}

/** Nutrition values computed for the chosen quantity. */
data class Nutrition(val grams: Double, val kcal: Double, val proteinG: Double?, val carbsG: Double?, val fatG: Double?)

class BarcodeViewModel(
    savedStateHandle: SavedStateHandle,
    private val foodRepository: FoodRepository,
    private val openFoodFactsClient: OpenFoodFactsClient,
) : ViewModel() {

    private val date = LocalDate.ofEpochDay(savedStateHandle.toRoute<BarcodeRoute>().dateEpochDay)
    private var lookupJob: Job? = null

    /** The scanner opens by itself only once, when entering the screen. */
    var autoScanDone = false

    // Compose state (not StateFlow) because TextFields must be updated synchronously
    var status by mutableStateOf<BarcodeStatus>(BarcodeStatus.Idle)
        private set
    var manualCode by mutableStateOf("")
        private set
    var name by mutableStateOf("")
        private set
    var quantity by mutableStateOf("")
        private set
    var unit by mutableStateOf(ServingUnit.GRAMMI)
        private set
    var mealType by mutableStateOf(MealType.suggestedFor())
        private set
    var showErrors by mutableStateOf(false)
        private set
    var isDone by mutableStateOf(false)
        private set

    val nameValid get() = name.isNotBlank()
    val quantityValid get() = parseDecimal(quantity)?.let { it > 0 } == true

    /** Kcal and macros for the entered quantity, null if the quantity is invalid. */
    val nutrition: Nutrition?
        get() {
            val product = (status as? BarcodeStatus.Found)?.product ?: return null
            val kcalPer100 = product.kcalPer100 ?: return null
            val grams = parseDecimal(quantity)?.takeIf { it > 0 }?.times(unit.gramsPerUnit) ?: return null
            val factor = grams / 100
            return Nutrition(
                grams = grams,
                kcal = kcalPer100 * factor,
                proteinG = product.proteinPer100?.times(factor),
                carbsG = product.carbsPer100?.times(factor),
                fatG = product.fatPer100?.times(factor),
            )
        }

    fun onManualCodeChange(value: String) {
        manualCode = value.filter(Char::isDigit).take(14)
    }

    fun onNameChange(value: String) {
        name = value
    }

    fun onQuantityChange(value: String) {
        quantity = value
    }

    fun onUnitChange(value: ServingUnit) {
        unit = value
    }

    fun onMealTypeChange(value: MealType) {
        mealType = value
    }

    /** Serving or whole package: sets the quantity in g/ml. */
    fun useAmount(amount: Double, isLiquid: Boolean) {
        quantity = amount.formatAmount()
        unit = if (isLiquid) ServingUnit.MILLILITRI else ServingUnit.GRAMMI
    }

    fun onScanFailed(message: String) {
        status = BarcodeStatus.Error(message, barcode = null)
    }

    fun lookup(barcode: String) {
        val code = barcode.trim()
        if (code.isEmpty()) return
        lookupJob?.cancel()
        status = BarcodeStatus.Loading(code)
        lookupJob = viewModelScope.launch {
            status = try {
                val product = openFoodFactsClient.getProduct(code)
                name = product.displayName
                unit = if (product.isLiquid) ServingUnit.MILLILITRI else ServingUnit.GRAMMI
                quantity = product.servingQuantity?.formatAmount().orEmpty()
                showErrors = false
                if (product.hasNutrition) BarcodeStatus.Found(product)
                else BarcodeStatus.Error(
                    "\"${product.displayName}\" è su Open Food Facts ma senza valori nutrizionali.",
                    barcode = code,
                )
            } catch (_: ProductLookupException.NotFound) {
                BarcodeStatus.NotFound(code)
            } catch (e: ProductLookupException) {
                BarcodeStatus.Error(e.message.orEmpty(), barcode = code)
            }
        }
    }

    fun save() {
        if (status !is BarcodeStatus.Found) return
        val values = nutrition
        if (!nameValid || values == null) {
            showErrors = true
            return
        }
        val servingUnit = unit.takeIf { it != ServingUnit.GRAMMI }
        viewModelScope.launch {
            foodRepository.add(
                FoodEntry(
                    date = date,
                    mealType = mealType,
                    name = name.trim(),
                    grams = values.grams,
                    kcal = values.kcal,
                    proteinG = values.proteinG,
                    carbsG = values.carbsG,
                    fatG = values.fatG,
                    source = Source.BARCODE,
                    photoPath = null,
                    createdAt = Instant.now(),
                    servingUnit = servingUnit,
                    servings = if (servingUnit != null) parseDecimal(quantity) else null,
                )
            )
            isDone = true
        }
    }
}
