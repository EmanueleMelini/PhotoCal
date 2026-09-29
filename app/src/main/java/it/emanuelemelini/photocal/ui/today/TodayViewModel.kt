package it.emanuelemelini.photocal.ui.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.photocal.data.FoodRepository
import it.emanuelemelini.photocal.data.WaterRepository
import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.data.db.Totals
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MealGroup(val mealType: MealType, val entries: List<FoodEntry>) {
    val kcal: Double = entries.sumOf { it.kcal }
}

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val meals: List<MealGroup> = emptyList(),
    val totals: Totals = Totals(0.0, 0.0, 0.0, 0.0),
    val kcalGoal: Int = SettingsRepository.DEFAULT_KCAL_GOAL,
    val proteinGoalG: Int? = null,
    val carbsGoalG: Int? = null,
    val fatGoalG: Int? = null,
    val waterMl: Int = 0,
    val waterGoalMl: Int = SettingsRepository.DEFAULT_WATER_GOAL_ML,
    val glassMl: Int = SettingsRepository.DEFAULT_GLASS_ML,
    val hasApiKey: Boolean = true,
    /** Sharing needs a name in the profile. */
    val canShare: Boolean = false,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val savedStateHandle: SavedStateHandle,
    foodRepository: FoodRepository,
    private val waterRepository: WaterRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    // The selected day also survives process recreation
    private val selectedDate = savedStateHandle
        .getStateFlow(KEY_DATE, LocalDate.now().toEpochDay())
        .map(LocalDate::ofEpochDay)

    val uiState: StateFlow<TodayUiState> = selectedDate
        .flatMapLatest { date ->
            combine(
                foodRepository.observeEntries(date),
                foodRepository.observeTotals(date),
                settingsRepository.settings,
                waterRepository.observeMl(date),
            ) { entries, totals, settings, waterMl ->
                TodayUiState(
                    date = date,
                    meals = MealType.entries.mapNotNull { meal ->
                        entries.filter { it.mealType == meal }
                            .takeIf { it.isNotEmpty() }
                            ?.let { MealGroup(meal, it) }
                    },
                    totals = totals,
                    kcalGoal = settings.dailyKcalGoal,
                    proteinGoalG = settings.proteinGoalG,
                    carbsGoalG = settings.carbsGoalG,
                    fatGoalG = settings.fatGoalG,
                    waterMl = waterMl,
                    waterGoalMl = settings.waterGoalMl,
                    glassMl = settings.glassMl,
                    hasApiKey = settings.geminiApiKey.isNotBlank(),
                    canShare = settings.profile.name.isNotBlank(),
                    isLoading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun addGlass() {
        val state = uiState.value
        viewModelScope.launch { waterRepository.addGlass(state.date, state.glassMl) }
    }

    fun removeGlass() {
        val state = uiState.value
        viewModelScope.launch { waterRepository.removeGlass(state.date, state.glassMl) }
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
