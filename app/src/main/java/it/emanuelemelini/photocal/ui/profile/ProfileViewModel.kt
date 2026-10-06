package it.emanuelemelini.photocal.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.WeightRepository
import it.emanuelemelini.photocal.data.ai.AiException
import it.emanuelemelini.photocal.data.ai.AiService
import it.emanuelemelini.photocal.data.db.WeightEntry
import it.emanuelemelini.photocal.data.nutrition.ActivityLevel
import it.emanuelemelini.photocal.data.nutrition.Bottle
import it.emanuelemelini.photocal.data.nutrition.DailyGoals
import it.emanuelemelini.photocal.data.nutrition.EnergyCalculator
import it.emanuelemelini.photocal.data.nutrition.EnergyEstimate
import it.emanuelemelini.photocal.data.nutrition.Profile
import it.emanuelemelini.photocal.data.nutrition.Sex
import it.emanuelemelini.photocal.data.nutrition.WaterCalculator
import it.emanuelemelini.photocal.data.nutrition.WeightGoal
import it.emanuelemelini.photocal.data.openfoodfacts.BottleCapacity
import it.emanuelemelini.photocal.data.openfoodfacts.OpenFoodFactsClient
import it.emanuelemelini.photocal.data.openfoodfacts.ProductLookupException
import it.emanuelemelini.photocal.data.photo.PhotoStorage
import it.emanuelemelini.photocal.data.photo.ProfilePhotoStorage
import it.emanuelemelini.photocal.data.share.ShareValidation
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import it.emanuelemelini.photocal.ui.UiText
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.parseDecimal
import it.emanuelemelini.photocal.ui.toUiText
import it.emanuelemelini.photocal.ui.uiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate

class ProfileViewModel(
    private val settingsRepository: SettingsRepository,
    private val weightRepository: WeightRepository,
    private val profilePhotoStorage: ProfilePhotoStorage,
    private val photoStorage: PhotoStorage,
    private val aiService: AiService,
    private val openFoodFactsClient: OpenFoodFactsClient,
) : ViewModel() {

    private val currentYear = LocalDate.now().year

    /** Weight as first shown: only an edited value becomes a new weigh-in. */
    private var initialWeightText = ""

    /**
     * Water goal in ml, changed only by typing the glasses or applying the suggestion: a new
     * glass size recomputes the glasses from it, without drifting while the size is typed.
     */
    private var waterGoalMl = SettingsRepository.DEFAULT_WATER_GOAL_ML

    // Compose state (not StateFlow) because TextFields must be updated synchronously
    var name by mutableStateOf("")
        private set
    var photo by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Emits when a chosen picture can't be read. */
    val photoErrors = Channel<Unit>(Channel.CONFLATED)

    var sex by mutableStateOf<Sex?>(null)
        private set
    var birthYear by mutableStateOf("")
        private set
    var height by mutableStateOf("")
        private set
    var weight by mutableStateOf("")
        private set
    var activity by mutableStateOf<ActivityLevel?>(null)
        private set
    var goal by mutableStateOf(WeightGoal.MAINTAIN)
        private set

    var kcal by mutableStateOf("")
        private set
    var protein by mutableStateOf("")
        private set
    var carbs by mutableStateOf("")
        private set
    var fat by mutableStateOf("")
        private set
    var glassSize by mutableStateOf("")
        private set
    var waterGlasses by mutableStateOf("")
        private set

    var bottleName by mutableStateOf("")
        private set

    /** Empty: no bottle. */
    var bottleMl by mutableStateOf("")
        private set

    /** A barcode lookup or AI photo of the bottle is running. */
    var bottleBusy by mutableStateOf(false)
        private set

    /** Outcome of a bottle lookup, shown in a snackbar. */
    val bottleMessages = Channel<UiText>(Channel.BUFFERED)

    /** The bottle photo needs the AI chosen in Settings. */
    var aiConfigured by mutableStateOf(true)
        private set

    var latestWeight by mutableStateOf<WeightEntry?>(null)
        private set
    var showErrors by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(true)
        private set

    val birthYearValid get() = birthYear.isBlank() || birthYear.toIntOrNull()?.let { it in birthYearRange } == true
    val heightValid get() = height.isBlank() || height.toIntOrNull()?.let { it in HEIGHT_RANGE } == true
    val weightValid get() = weight.isBlank() || parseDecimal(weight)?.let { it in WEIGHT_RANGE } == true
    val kcalValid get() = kcal.toIntOrNull()?.let { it in KCAL_RANGE } == true
    val proteinValid get() = isValidMacro(protein)
    val carbsValid get() = isValidMacro(carbs)
    val fatValid get() = isValidMacro(fat)
    val glassSizeValid get() = validGlassMl != null
    val bottleMlValid get() = bottleMl.isBlank() || bottleMl.toIntOrNull()?.let { it in Bottle.ML_RANGE } == true
    val waterValid: Boolean
        get() {
            val glasses = waterGlasses.toIntOrNull() ?: return false
            return glasses in WATER_GLASSES_RANGE && glasses * (validGlassMl ?: 0) <= MAX_WATER_ML
        }

    private val validGlassMl: Int? get() = glassSize.toIntOrNull()?.takeIf { it in GLASS_ML_RANGE }

    /** Water goal of the typed glasses, in ml (null while glasses or glass size are invalid). */
    val waterGoalTypedMl: Int?
        get() {
            val glass = validGlassMl ?: return null
            return waterGlasses.toIntOrNull()?.takeIf { it in WATER_GLASSES_RANGE }?.let { it * glass }
        }

    val birthYearRange get() = (currentYear - 100)..(currentYear - MIN_AGE)

    /** The formula is meant for adults: shown as a note for younger users. */
    val isUnderAge: Boolean get() = birthYear.toIntOrNull()?.let { currentYear - it < 18 } == true

    /** Live estimate from the fields as they are typed; null while some data is missing. */
    val estimate: EnergyEstimate?
        get() = EnergyCalculator.estimate(
            profile(),
            weightKg = parseDecimal(weight)?.takeIf { weightValid },
            currentYear = currentYear,
        )

    /** Suggested water to drink, in ml; needs only sex and activity (weight is optional). */
    val waterSuggestionMl: Int?
        get() = WaterCalculator.suggestedMl(sex, activity, parseDecimal(weight)?.takeIf { weightValid })

    /** The suggestion in glasses of the typed size (null while the size is invalid). */
    val waterSuggestionGlasses: Int?
        get() {
            val ml = waterSuggestionMl ?: return null
            return validGlassMl?.let { WaterCalculator.glassesToReach(ml, it) }
        }

    /** kcal of the typed macros, to compare with the kcal goal (null unless all three are set). */
    val kcalFromMacros: Int?
        get() {
            val p = protein.toIntOrNull() ?: return null
            val c = carbs.toIntOrNull() ?: return null
            val f = fat.toIntOrNull() ?: return null
            return EnergyCalculator.kcalFromMacros(p, c, f)
        }

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val profile = settings.profile
            name = profile.name
            sex = profile.sex
            birthYear = profile.birthYear?.toString().orEmpty()
            height = profile.heightCm?.toString().orEmpty()
            activity = profile.activity
            goal = profile.goal
            kcal = settings.dailyKcalGoal.toString()
            protein = settings.proteinGoalG?.toString().orEmpty()
            carbs = settings.carbsGoalG?.toString().orEmpty()
            fat = settings.fatGoalG?.toString().orEmpty()
            waterGoalMl = settings.waterGoalMl
            glassSize = settings.glassMl.toString()
            waterGlasses = WaterCalculator.goalGlasses(settings.waterGoalMl, settings.glassMl).toString()
            bottleName = settings.bottle?.name.orEmpty()
            bottleMl = settings.bottle?.ml?.toString().orEmpty()
            aiConfigured = settings.aiConfigured
            latestWeight = weightRepository.observeLatest().first()
            weight = latestWeight?.weightKg?.formatAmount().orEmpty()
            initialWeightText = weight
            isLoading = false
        }
        viewModelScope.launch {
            // Saved or removed photos are applied at once, without the Save button
            profilePhotoStorage.version.collect { photo = profilePhotoStorage.load()?.asImageBitmap() }
        }
        viewModelScope.launch {
            // Follows the weight log (e.g. a weigh-in added there), unless the field is being edited
            weightRepository.observeLatest().collect { latest ->
                latestWeight = latest
                if (!isLoading && weight == initialWeightText) {
                    weight = latest?.weightKg?.formatAmount().orEmpty()
                    initialWeightText = weight
                }
            }
        }
    }

    fun onNameChange(value: String) { name = value.take(ShareValidation.MAX_NAME) }
    fun onSexChange(value: Sex) { sex = value }

    /** [open] reads the chosen picture (it may be called more than once). */
    fun setPhoto(open: () -> InputStream, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                profilePhotoStorage.save(open)
            } catch (_: IOException) {
                photoErrors.trySend(Unit)
            } finally {
                onDone()
            }
        }
    }

    fun removePhoto() {
        viewModelScope.launch { profilePhotoStorage.delete() }
    }
    fun onBirthYearChange(value: String) { birthYear = value.filter(Char::isDigit).take(4) }
    fun onHeightChange(value: String) { height = value.filter(Char::isDigit).take(3) }
    fun onWeightChange(value: String) { weight = value.filter { it.isDigit() || it == ',' || it == '.' }.take(6) }
    fun onActivityChange(value: ActivityLevel) { activity = value }
    fun onGoalChange(value: WeightGoal) { goal = value }
    fun onKcalChange(value: String) { kcal = value.filter(Char::isDigit).take(5) }
    fun onProteinChange(value: String) { protein = value.filter(Char::isDigit).take(4) }
    fun onCarbsChange(value: String) { carbs = value.filter(Char::isDigit).take(4) }
    fun onFatChange(value: String) { fat = value.filter(Char::isDigit).take(4) }

    fun onGlassSizeChange(value: String) {
        glassSize = value.filter(Char::isDigit).take(4)
        validGlassMl?.let { waterGlasses = WaterCalculator.goalGlasses(waterGoalMl, it).toString() }
    }

    fun onWaterGlassesChange(value: String) {
        waterGlasses = value.filter(Char::isDigit).take(2)
        waterGoalTypedMl?.let { waterGoalMl = it }
    }

    fun onBottleNameChange(value: String) { bottleName = value.take(ShareValidation.MAX_NAME) }
    fun onBottleMlChange(value: String) { bottleMl = value.filter(Char::isDigit).take(4) }

    /** Name and capacity of the scanned bottle from Open Food Facts; saved with Save. */
    fun lookupBottle(barcode: String) = bottleLookup {
        try {
            val product = openFoodFactsClient.getProduct(barcode)
            val ml = BottleCapacity.of(product)
            if (ml == null) uiText(R.string.profile_bottle_no_capacity, product.displayName)
            else found(Bottle(product.displayName, ml))
        } catch (e: ProductLookupException) {
            e.toUiText()
        }
    }

    /** The bottle in the photo at [path], recognized by the AI; the photo is then deleted. */
    fun recognizeBottle(path: String) = bottleLookup {
        try {
            aiService.recognizeBottle(photoStorage.shrink(path))?.let(::found)
                ?: uiText(R.string.profile_bottle_not_recognized)
        } catch (e: AiException) {
            e.toUiText()
        } catch (_: IOException) {
            uiText(R.string.profile_bottle_not_recognized)
        } finally {
            photoStorage.delete(path)
        }
    }

    fun onBottleScanFailed(message: UiText) {
        bottleMessages.trySend(message)
    }

    private fun bottleLookup(block: suspend () -> UiText) {
        if (bottleBusy) return
        bottleBusy = true
        viewModelScope.launch {
            try {
                bottleMessages.send(block())
            } finally {
                bottleBusy = false
            }
        }
    }

    private fun found(bottle: Bottle): UiText {
        bottleMl = bottle.ml.toString()
        if (bottle.name.isNotBlank()) bottleName = bottle.name.take(ShareValidation.MAX_NAME)
        return if (bottle.name.isBlank()) uiText(R.string.profile_bottle_found_unnamed, bottle.ml)
        else uiText(R.string.profile_bottle_found, bottle.name, bottle.ml)
    }

    /** Copies the available suggestions into the goals; they stay editable. */
    fun applySuggestion() {
        estimate?.let { suggestion ->
            kcal = suggestion.suggestedKcal.toString()
            protein = suggestion.proteinG.toString()
            carbs = suggestion.carbsG.toString()
            fat = suggestion.fatG.toString()
        }
        val waterMl = waterSuggestionMl
        val glasses = waterSuggestionGlasses
        if (waterMl != null && glasses != null) {
            waterGoalMl = waterMl
            waterGlasses = glasses.toString()
        }
    }

    /** Returns true if everything was valid and has been saved. */
    suspend fun save(): Boolean {
        val valid = birthYearValid && heightValid && weightValid && kcalValid &&
            proteinValid && carbsValid && fatValid && glassSizeValid && waterValid && bottleMlValid
        val waterMl = waterGoalTypedMl
        if (!valid || waterMl == null) {
            showErrors = true
            return false
        }
        showErrors = false
        settingsRepository.saveProfile(profile())
        settingsRepository.saveGoals(
            DailyGoals(
                kcal = kcal.toInt(),
                proteinG = protein.toIntOrNull(),
                carbsG = carbs.toIntOrNull(),
                fatG = fat.toIntOrNull(),
                waterMl = waterMl,
                glassMl = glassSize.toInt(),
            )
        )
        settingsRepository.saveBottle(bottleMl.toIntOrNull()?.let { Bottle(bottleName.trim(), it) })
        // An edited weight here counts as today's weigh-in
        val typedWeight = parseDecimal(weight)
        if (typedWeight != null && weight != initialWeightText) {
            weightRepository.record(LocalDate.now(), typedWeight)
            initialWeightText = weight
        }
        return true
    }

    private fun profile() = Profile(
        name = name.trim(),
        sex = sex,
        birthYear = birthYear.toIntOrNull()?.takeIf { birthYearValid },
        heightCm = height.toIntOrNull()?.takeIf { heightValid },
        activity = activity,
        goal = goal,
    )

    private fun isValidMacro(text: String) = text.isBlank() || text.toIntOrNull()?.let { it in MACRO_RANGE } == true

    companion object {
        const val MIN_AGE = 14
        val HEIGHT_RANGE = 100..250
        val WEIGHT_RANGE = 25.0..350.0
        val KCAL_RANGE = 500..10_000
        val MACRO_RANGE = 0..1_000
        val GLASS_ML_RANGE = 50..1_000
        val WATER_GLASSES_RANGE = 1..40
        const val MAX_WATER_ML = 10_000
    }
}
