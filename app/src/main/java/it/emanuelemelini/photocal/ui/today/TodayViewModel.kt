package it.emanuelemelini.photocal.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.photocal.data.FoodRepository
import it.emanuelemelini.photocal.data.WaterRepository
import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.data.db.Totals
import it.emanuelemelini.photocal.data.health.DayActivity
import it.emanuelemelini.photocal.data.health.HealthConnect
import it.emanuelemelini.photocal.data.nutrition.Bottle
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

data class MealGroup(val mealType: MealType, val entries: List<FoodEntry>) {
    val kcal: Double = entries.sumOf { it.kcal }
}

/** A bottle added to [date], kept for the undo. */
data class BottleDrink(val date: LocalDate, val ml: Int)

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val meals: List<MealGroup> = emptyList(),
    val totals: Totals = Totals(0.0, 0.0, 0.0, 0.0),
    /** Goal of the day: the base one plus the active calories, when the user chose so. */
    val kcalGoal: Int = SettingsRepository.DEFAULT_KCAL_GOAL,
    /** Goal from the profile, shown next to the burned calories when they are added. */
    val baseKcalGoal: Int = SettingsRepository.DEFAULT_KCAL_GOAL,
    /** Steps and active calories from Health Connect; null when not connected. */
    val activity: DayActivity? = null,
    /** kcal added to the goal (0 when the option is off). */
    val burnedKcalAdded: Int = 0,
    val proteinGoalG: Int? = null,
    val carbsGoalG: Int? = null,
    val fatGoalG: Int? = null,
    val waterMl: Int = 0,
    val waterGoalMl: Int = SettingsRepository.DEFAULT_WATER_GOAL_ML,
    val glassMl: Int = SettingsRepository.DEFAULT_GLASS_ML,
    /** Set in the profile: a whole bottle is one tap away. */
    val bottle: Bottle? = null,
    val aiConfigured: Boolean = true,
    /** Sharing needs a name in the profile. */
    val canShare: Boolean = false,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val foodRepository: FoodRepository,
    private val waterRepository: WaterRepository,
    settingsRepository: SettingsRepository,
    private val healthConnect: HealthConnect,
) : ViewModel() {

    /** Changed when the screen comes back: steps and calories grow during the day. */
    private val activityRefresh = MutableStateFlow(0)

    // The selected day also survives process recreation
    private val selectedDate = savedStateHandle
        .getStateFlow(KEY_DATE, LocalDate.now().toEpochDay())
        .map(LocalDate::ofEpochDay)

    private val connected = settingsRepository.settings.map { it.healthConnected }.distinctUntilChanged()

    private val activity = combine(selectedDate, connected, activityRefresh) { date, isConnected, _ -> date to isConnected }
        .flatMapLatest { (date, isConnected) ->
            flow { emit(if (isConnected) healthConnect.activityOf(date) else null) }
        }

    val uiState: StateFlow<TodayUiState> = selectedDate
        .flatMapLatest { date ->
            combine(
                foodRepository.observeEntries(date),
                foodRepository.observeTotals(date),
                settingsRepository.settings,
                waterRepository.observeMl(date),
                activity.onStart { emit(null) },
            ) { entries, totals, settings, waterMl, activity ->
                val burned = if (settings.healthAddBurned) activity?.activeKcal?.roundToInt() ?: 0 else 0
                TodayUiState(
                    date = date,
                    meals = MealType.entries.mapNotNull { meal ->
                        entries.filter { it.mealType == meal }
                            .takeIf { it.isNotEmpty() }
                            ?.let { MealGroup(meal, it) }
                    },
                    totals = totals,
                    kcalGoal = settings.dailyKcalGoal + burned,
                    baseKcalGoal = settings.dailyKcalGoal,
                    activity = activity,
                    burnedKcalAdded = burned,
                    proteinGoalG = settings.proteinGoalG,
                    carbsGoalG = settings.carbsGoalG,
                    fatGoalG = settings.fatGoalG,
                    waterMl = waterMl,
                    waterGoalMl = settings.waterGoalMl,
                    glassMl = settings.glassMl,
                    bottle = settings.bottle,
                    aiConfigured = settings.aiConfigured,
                    canShare = settings.profile.name.isNotBlank(),
                    isLoading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun refreshActivity() {
        activityRefresh.value++
    }

    fun addGlass() {
        val state = uiState.value
        viewModelScope.launch { waterRepository.addGlass(state.date, state.glassMl) }
    }

    fun removeGlass() {
        val state = uiState.value
        viewModelScope.launch { waterRepository.removeGlass(state.date, state.glassMl) }
    }

    /** Adds the whole bottle to the day shown; returns what [undoBottle] needs, or null without a bottle. */
    fun addBottle(): BottleDrink? {
        val state = uiState.value
        val bottle = state.bottle ?: return null
        val drink = BottleDrink(state.date, bottle.ml)
        viewModelScope.launch { waterRepository.addBottle(drink.date, drink.ml) }
        return drink
    }

    fun undoBottle(drink: BottleDrink) {
        viewModelScope.launch { waterRepository.removeBottle(drink.date, drink.ml) }
    }

    /** Same food again, in the same day and meal. */
    fun duplicate(entry: FoodEntry) {
        viewModelScope.launch { foodRepository.copy(listOf(entry), entry.date, entry.mealType) }
    }

    /** Copies [entries] to [date] ([meal] null: each one keeps its meal), then [onCopied]. */
    fun copy(entries: List<FoodEntry>, date: LocalDate, meal: MealType?, onCopied: (Int) -> Unit) {
        viewModelScope.launch { onCopied(foodRepository.copy(entries, date, meal)) }
    }

    fun previousDay() = shiftDays(-1)

    fun nextDay() = shiftDays(1)

    fun selectDate(date: LocalDate) {
        savedStateHandle[KEY_DATE] = date.toEpochDay()
    }

    private fun shiftDays(days: Long) {
        val current = savedStateHandle.get<Long>(KEY_DATE) ?: LocalDate.now().toEpochDay()
        savedStateHandle[KEY_DATE] = current + days
    }

    private companion object {
        const val KEY_DATE = "selected_date"
    }
}
