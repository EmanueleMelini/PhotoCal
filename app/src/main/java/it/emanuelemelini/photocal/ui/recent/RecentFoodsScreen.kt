package it.emanuelemelini.photocal.ui.recent

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.db.SavedFood
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.formatKcal
import it.emanuelemelini.photocal.ui.quantityLabel

/** Favorites and recent foods: tapping one opens the entry form already filled in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentFoodsScreen(
    onBack: () -> Unit,
    onPick: (SavedFood) -> Unit,
) {
    val container = appContainer()
    val viewModel: RecentFoodsViewModel = viewModel {
        RecentFoodsViewModel(createSavedStateHandle(), container.savedFoodRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recent_title)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onBack)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            item {
                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text(stringResource(R.string.recent_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val results = state.results
            when {
                results != null -> {
                    if (results.isEmpty()) message(R.string.recent_no_results)
                    foods("results", results, viewModel, onPick)
                }
                state.favorites.isEmpty() && state.recent.isEmpty() -> message(R.string.recent_empty)
                else -> {
                    if (state.favorites.isNotEmpty()) {
                        header(R.string.recent_favorites)
                        foods("favorites", state.favorites, viewModel, onPick)
                    }
                    if (state.recent.isNotEmpty()) {
                        header(R.string.recent_recent)
                        foods("recent", state.recent, viewModel, onPick)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.header(textRes: Int) {
    item(key = "header-$textRes") {
        Text(
            stringResource(textRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

private fun LazyListScope.message(textRes: Int) {
    item(key = "message") {
        Text(
            stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

private fun LazyListScope.foods(
    section: String,
    foods: List<SavedFood>,
    viewModel: RecentFoodsViewModel,
    onPick: (SavedFood) -> Unit,
) {
    items(foods, key = { "$section-${it.id}" }) { food ->
        FoodRow(
            food = food,
            onClick = { onPick(food) },
            onToggleFavorite = { viewModel.toggleFavorite(food) },
            onRemove = { viewModel.remove(food) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FoodRow(food: SavedFood, onClick: () -> Unit, onToggleFavorite: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val kcal = food.kcalPer100 * food.grams / 100
    Box {
        ListItem(
            headlineContent = { Text(food.name) },
            supportingContent = {
                val quantity = quantityLabel(food.grams, food.servingUnit, food.servings, food.pieceLabel)
                Text(listOfNotNull(quantity, "${kcal.formatKcal()} kcal").joinToString(" · "))
            },
            trailingContent = {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (food.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(
                            if (food.favorite) R.string.recent_remove_favorite else R.string.recent_add_favorite,
                        ),
                        tint = if (food.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { menuOpen = true },
                onLongClickLabel = stringResource(R.string.recent_remove),
            ),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.recent_remove)) },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}
