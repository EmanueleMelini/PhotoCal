package it.emanuelemelini.photocal.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.photocal.data.WeightRepository
import it.emanuelemelini.photocal.data.db.WeightEntry
import it.emanuelemelini.photocal.data.nutrition.ActivityLevel
import it.emanuelemelini.photocal.data.nutrition.DailyGoals
import it.emanuelemelini.photocal.data.nutrition.EnergyCalculator
import it.emanuelemelini.photocal.data.nutrition.EnergyEstimate
import it.emanuelemelini.photocal.data.nutrition.Profile
import it.emanuelemelini.photocal.data.nutrition.Sex
import it.emanuelemelini.photocal.data.nutrition.WeightGoal
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.parseDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class ProfileViewModel(
    private val settingsRepository: SettingsRepository,
    private val weightRepository: WeightRepository,
) : ViewModel() {

    private val currentYear = LocalDate.now().year

    /** Weight as first shown: only an edited value becomes a new weigh-in. */
    private var initialWeightText = ""

    // Compose state (not StateFlow) because TextFields must be updated synchronously
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
            sex = profile.sex
            birthYear = profile.birthYear?.toString().orEmpty()
            height = profile.heightCm?.toString().orEmpty()
            activity = profile.activity
            goal = profile.goal
            kcal = settings.dailyKcalGoal.toString()
            protein = settings.proteinGoalG?.toString().orEmpty()
            carbs = settings.carbsGoalG?.toString().orEmpty()
            fat = settings.fatGoalG?.toString().orEmpty()
            latestWeight = weightRepository.observeLatest().first()
            weight = latestWeight?.weightKg?.formatAmount().orEmpty()
            initialWeightText = weight
            isLoading = false
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

    fun onSexChange(value: Sex) { sex = value }
    fun onBirthYearChange(value: String) { birthYear = value.filter(Char::isDigit).take(4) }
    fun onHeightChange(value: String) { height = value.filter(Char::isDigit).take(3) }
    fun onWeightChange(value: String) { weight = value.filter { it.isDigit() || it == ',' || it == '.' }.take(6) }
    fun onActivityChange(value: ActivityLevel) { activity = value }
    fun onGoalChange(value: WeightGoal) { goal = value }
    fun onKcalChange(value: String) { kcal = value.filter(Char::isDigit).take(5) }
    fun onProteinChange(value: String) { protein = value.filter(Char::isDigit).take(4) }
    fun onCarbsChange(value: String) { carbs = value.filter(Char::isDigit).take(4) }
    fun onFatChange(value: String) { fat = value.filter(Char::isDigit).take(4) }

    /** Copies the suggestion into the goals; they stay editable. */
    fun applySuggestion() {
        val suggestion = estimate ?: return
        kcal = suggestion.suggestedKcal.toString()
        protein = suggestion.proteinG.toString()
        carbs = suggestion.carbsG.toString()
        fat = suggestion.fatG.toString()
    }

    /** Returns true if everything was valid and has been saved. */
    suspend fun save(): Boolean {
        if (!(birthYearValid && heightValid && weightValid && kcalValid && proteinValid && carbsValid && fatValid)) {
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
            )
        )
        // An edited weight here counts as today's weigh-in
        val typedWeight = parseDecimal(weight)
        if (typedWeight != null && weight != initialWeightText) {
            weightRepository.record(LocalDate.now(), typedWeight)
            initialWeightText = weight
        }
        return true
    }

    private fun profile() = Profile(
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
    }
}
