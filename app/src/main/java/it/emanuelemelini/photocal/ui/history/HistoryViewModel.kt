package it.emanuelemelini.photocal.ui.history

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.FoodRepository
import it.emanuelemelini.photocal.data.WeightRepository
import it.emanuelemelini.photocal.data.db.WeightEntry
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class HistoryRange(val days: Int, @StringRes val labelRes: Int) {
    WEEK(7, R.string.history_range_week),
    MONTH(30, R.string.history_range_month),
}

data class DayKcal(val date: LocalDate, val kcal: Double)

data class HistoryUiState(
    val range: HistoryRange = HistoryRange.WEEK,
    /** One element per day in the range, from the oldest to today (0 if empty). */
    val days: List<DayKcal> = emptyList(),
    val kcalGoal: Int = SettingsRepository.DEFAULT_KCAL_GOAL,
    val isLoading: Boolean = true,
    /** Weigh-ins in the range, oldest first. */
    val weights: List<WeightEntry> = emptyList(),
) {
    val weightChange: Double? get() = if (weights.size >= 2) weights.last().weightKg - weights.first().weightKg else null

    /** Stats exclude today: the day isn't over and would lower the average. */
    val completedDays: List<DayKcal> get() = days.dropLast(1)
    val loggedDays: List<DayKcal> get() = completedDays.filter { it.kcal > 0 }
    val averageKcal: Double? get() = loggedDays.takeIf { it.isNotEmpty() }?.map { it.kcal }?.average()
    val daysWithinGoal: Int get() = loggedDays.count { it.kcal <= kcalGoal }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val savedStateHandle: SavedStateHandle,
    foodRepository: FoodRepository,
    settingsRepository: SettingsRepository,
    weightRepository: WeightRepository,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = savedStateHandle
        .getStateFlow(KEY_RANGE, HistoryRange.WEEK)
        .flatMapLatest { range ->
            val today = LocalDate.now()
            val from = today.minusDays(range.days - 1L)
            combine(
                foodRepository.observeTotalsBetween(from, today),
                settingsRepository.settings,
                weightRepository.observeBetween(from, today),
            ) { totals, settings, weights ->
                // The DAO returns only days with entries: the gaps are filled here
                val byDate = totals.associate { it.date to it.kcal }
                HistoryUiState(
                    range = range,
                    days = (0 until range.days).map { offset ->
                        val date = from.plusDays(offset.toLong())
                        DayKcal(date, byDate[date] ?: 0.0)
                    },
                    kcalGoal = settings.dailyKcalGoal,
                    isLoading = false,
                    weights = weights,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setRange(range: HistoryRange) {
        savedStateHandle[KEY_RANGE] = range
    }

    private companion object {
        const val KEY_RANGE = "range"
    }
}
