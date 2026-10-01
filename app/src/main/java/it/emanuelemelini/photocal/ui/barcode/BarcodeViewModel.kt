package it.emanuelemelini.photocal.ui.barcode

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.FoodRepository
import it.emanuelemelini.photocal.data.SavedFoodRepository
import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.data.db.SavedFood
import it.emanuelemelini.photocal.data.db.ServingUnit
import it.emanuelemelini.photocal.data.db.Source
import it.emanuelemelini.photocal.data.openfoodfacts.OpenFoodFactsClient
import it.emanuelemelini.photocal.data.openfoodfacts.Product
import it.emanuelemelini.photocal.data.openfoodfacts.ProductLookupException
import it.emanuelemelini.photocal.data.openfoodfacts.ServingPieces
import it.emanuelemelini.photocal.ui.BarcodeRoute
import it.emanuelemelini.photocal.ui.UiText
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.parseDecimal
import it.emanuelemelini.photocal.ui.toUiText
import it.emanuelemelini.photocal.ui.uiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

sealed interface BarcodeStatus {
    /** Waiting for a scan (scanner closed or cancelled). */
    data object Idle : BarcodeStatus
    data class Loading(val barcode: String) : BarcodeStatus
    /** [offline]: Open Food Facts unreachable, the product comes from the saved foods. */
    data class Found(val product: Product, val offline: Boolean = false) : BarcodeStatus
    data class NotFound(val barcode: String) : BarcodeStatus
    data class Error(val message: UiText, val barcode: String?) : BarcodeStatus
}

/** Nutrition values computed for the chosen quantity. */
data class Nutrition(
    val grams: Double,
    val kcal: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fiberG: Double? = null,
    val sugarsG: Double? = null,
    val saltG: Double? = null,
)

class BarcodeViewModel(
    savedStateHandle: SavedStateHandle,
    private val foodRepository: FoodRepository,
    private val savedFoods: SavedFoodRepository,
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
    var unit by mutableStateOf(ServingUnit.GRAMS)
        private set
    /** Grams of one piece, for [ServingUnit.PIECE]. */
    var pieceGrams by mutableStateOf("")
        private set
    var mealType by mutableStateOf(MealType.suggestedFor())
        private set
    var showErrors by mutableStateOf(false)
        private set
    var isDone by mutableStateOf(false)
        private set

    val nameValid get() = name.isNotBlank()
    val quantityValid get() = parseDecimal(quantity)?.let { it > 0 } == true
    val pieceGramsValid get() = unit != ServingUnit.PIECE || parseDecimal(pieceGrams)?.let { it > 0 } == true

    /** Kcal and macros for the entered quantity, null if the quantity is invalid. */
    val nutrition: Nutrition?
        get() {
            val product = (status as? BarcodeStatus.Found)?.product ?: return null
            val kcalPer100 = product.kcalPer100 ?: return null
            val count = parseDecimal(quantity)?.takeIf { it > 0 } ?: return null
            val grams = unit.grams(count, pieceGramsValue(product)) ?: return null
            val factor = grams / 100
            return Nutrition(
                grams = grams,
                kcal = kcalPer100 * factor,
                proteinG = product.proteinPer100?.times(factor),
                carbsG = product.carbsPer100?.times(factor),
                fatG = product.fatPer100?.times(factor),
                fiberG = product.fiberPer100?.times(factor),
                sugarsG = product.sugarsPer100?.times(factor),
                saltG = product.saltPer100?.times(factor),
            )
        }

    /** Grams of one piece: the exact value from the package while its rounded text is untouched. */
    private fun pieceGramsValue(product: Product): Double? =
        product.servingPieces?.pieceGrams?.takeIf { it.formatAmount() == pieceGrams } ?: parseDecimal(pieceGrams)

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

    fun onPieceGramsChange(value: String) {
        pieceGrams = value
    }

    fun onMealTypeChange(value: MealType) {
        mealType = value
    }

    /** Serving or whole package: sets the quantity in g/ml. */
    fun useAmount(amount: Double, isLiquid: Boolean) {
        quantity = amount.formatAmount()
        unit = if (isLiquid) ServingUnit.MILLILITERS else ServingUnit.GRAMS
    }

    /** One piece, with the grams of a piece from the package. */
    fun useOnePiece(gramsPerPiece: Double) {
        quantity = 1.0.formatAmount()
        unit = ServingUnit.PIECE
        pieceGrams = gramsPerPiece.formatAmount()
    }

    fun onScanFailed(message: UiText) {
        status = BarcodeStatus.Error(message, barcode = null)
    }

    fun lookup(barcode: String) {
        val code = barcode.trim()
        if (code.isEmpty()) return
        lookupJob?.cancel()
        status = BarcodeStatus.Loading(code)
        lookupJob = viewModelScope.launch {
            val saved = savedFoods.getByBarcode(code)
            status = try {
                val product = openFoodFactsClient.getProduct(code)
                // The name and the grams of a piece chosen last time win over the package ones
                name = saved?.name ?: product.displayName
                unit = if (product.isLiquid) ServingUnit.MILLILITERS else ServingUnit.GRAMS
                quantity = product.servingQuantity?.formatAmount().orEmpty()
                pieceGrams = (saved?.pieceGrams ?: product.servingPieces?.pieceGrams)?.formatAmount().orEmpty()
                showErrors = false
                if (product.hasNutrition) BarcodeStatus.Found(product)
                else BarcodeStatus.Error(
                    uiText(R.string.barcode_no_nutrition, product.displayName),
                    barcode = code,
                )
            } catch (_: ProductLookupException.NotFound) {
                BarcodeStatus.NotFound(code)
            } catch (e: ProductLookupException) {
                if (e is ProductLookupException.Network && saved != null) useOffline(saved, code)
                else BarcodeStatus.Error(e.toUiText(), barcode = code)
            }
        }
    }

    /** Product already logged: its saved values and last quantity, without Open Food Facts. */
    private fun useOffline(saved: SavedFood, code: String): BarcodeStatus {
        name = saved.name
        unit = saved.servingUnit?.takeIf { saved.servings != null } ?: ServingUnit.GRAMS
        quantity = (if (unit == ServingUnit.GRAMS) saved.grams else saved.servings)?.formatAmount().orEmpty()
        pieceGrams = saved.pieceGrams?.formatAmount().orEmpty()
        showErrors = false
        val product = Product(
            barcode = code,
            name = saved.name,
            brand = null,
            kcalPer100 = saved.kcalPer100,
            proteinPer100 = saved.proteinPer100,
            carbsPer100 = saved.carbsPer100,
            fatPer100 = saved.fatPer100,
            fiberPer100 = saved.fiberPer100,
            sugarsPer100 = saved.sugarsPer100,
            saltPer100 = saved.saltPer100,
            servingQuantity = null,
            packageQuantity = null,
            servingPieces = saved.pieceGrams?.let { ServingPieces(it, saved.pieceLabel) },
            isLiquid = saved.servingUnit?.isLiquid == true,
            imageUrl = null,
        )
        return BarcodeStatus.Found(product, offline = true)
    }

    fun save() {
        val product = (status as? BarcodeStatus.Found)?.product ?: return
        val values = nutrition
        if (!nameValid || !pieceGramsValid || values == null) {
            showErrors = true
            return
        }
        val servingUnit = unit.takeIf { it != ServingUnit.GRAMS }
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
                    fiberG = values.fiberG,
                    sugarsG = values.sugarsG,
                    saltG = values.saltG,
                    source = Source.BARCODE,
                    photoPath = null,
                    createdAt = Instant.now(),
                    servingUnit = servingUnit,
                    servings = if (servingUnit != null) parseDecimal(quantity) else null,
                    servingLabel = product.servingPieces?.label?.takeIf { servingUnit == ServingUnit.PIECE },
                ),
                barcode = product.barcode,
            )
            isDone = true
        }
    }
}
