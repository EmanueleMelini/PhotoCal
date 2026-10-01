package it.emanuelemelini.photocal.ui.recent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import it.emanuelemelini.photocal.data.SavedFoodRepository
import it.emanuelemelini.photocal.data.db.SavedFood
import it.emanuelemelini.photocal.ui.RecentFoodsRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [results] is non-null while searching; otherwise the screen shows favorites and recent foods. */
data class RecentFoodsUiState(
    val favorites: List<SavedFood> = emptyList(),
    val recent: List<SavedFood> = emptyList(),
    val results: List<SavedFood>? = null,
    val isLoading: Boolean = true,
)

class RecentFoodsViewModel(
    savedStateHandle: SavedStateHandle,
    private val savedFoods: SavedFoodRepository,
) : ViewModel() {

    val route = savedStateHandle.toRoute<RecentFoodsRoute>()

    // Compose state (not StateFlow) because TextFields must be updated synchronously
    var query by mutableStateOf("")
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<RecentFoodsUiState> = combine(
        savedFoods.observeFavorites(),
        savedFoods.observeRecent(),
        snapshotFlow { query.trim() }.flatMapLatest { text ->
            if (text.isEmpty()) flowOf(null) else savedFoods.observeSearch(text, SEARCH_LIMIT)
        },
    ) { favorites, recent, results ->
        RecentFoodsUiState(favorites = favorites, recent = recent, results = results, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecentFoodsUiState())

    fun onQueryChange(value: String) {
        query = value
    }

    fun toggleFavorite(food: SavedFood) {
        viewModelScope.launch { savedFoods.setFavorite(food.id, !food.favorite) }
    }

    fun remove(food: SavedFood) {
        viewModelScope.launch { savedFoods.delete(food.id) }
    }

    private companion object {
        const val SEARCH_LIMIT = 50
    }
}
