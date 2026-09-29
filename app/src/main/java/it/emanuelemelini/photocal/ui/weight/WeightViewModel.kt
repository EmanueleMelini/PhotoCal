package it.emanuelemelini.photocal.ui.weight

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.WeightRepository
import it.emanuelemelini.photocal.data.db.WeightEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class WeightRange(val days: Int, @StringRes val labelRes: Int) {
    MONTH(30, R.string.weight_range_month),
    QUARTER(90, R.string.weight_range_quarter),
    YEAR(365, R.string.weight_range_year),
}

data class WeightUiState(
    val range: WeightRange = WeightRange.QUARTER,
    val from: LocalDate = LocalDate.now().minusDays(WeightRange.QUARTER.days - 1L),
    /** Weigh-ins in the range, oldest first (for the chart). */
    val inRange: List<WeightEntry> = emptyList(),
    /** Every weigh-in, most recent first (for the list). */
    val all: List<WeightEntry> = emptyList(),
) {
    /** Change from the first to the last weigh-in of the range, if there are at least two. */
    val change: Double? get() = if (inRange.size >= 2) inRange.last().weightKg - inRange.first().weightKg else null
}

@OptIn(ExperimentalCoroutinesApi::class)
class WeightViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val weightRepository: WeightRepository,
) : ViewModel() {

    val uiState: StateFlow<WeightUiState> = savedStateHandle
        .getStateFlow(KEY_RANGE, WeightRange.QUARTER)
        .flatMapLatest { range ->
            val today = LocalDate.now()
            val from = today.minusDays(range.days - 1L)
            combine(weightRepository.observeBetween(from, today), weightRepository.observeAll()) { inRange, all ->
                WeightUiState(range = range, from = from, inRange = inRange, all = all)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUiState())

    fun setRange(range: WeightRange) {
        savedStateHandle[KEY_RANGE] = range
    }

    fun record(date: LocalDate, weightKg: Double) {
        viewModelScope.launch { weightRepository.record(date, weightKg) }
    }

    fun delete(entry: WeightEntry) {
        viewModelScope.launch { weightRepository.delete(entry) }
    }

    private companion object {
        const val KEY_RANGE = "range"
    }
}
