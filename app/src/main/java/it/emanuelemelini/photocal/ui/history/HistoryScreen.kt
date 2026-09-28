package it.emanuelemelini.photocal.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.formatKcal
import it.emanuelemelini.photocal.ui.relativeLabel
import it.emanuelemelini.photocal.ui.shortLabel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val container = appContainer()
    val viewModel: HistoryViewModel = viewModel {
        HistoryViewModel(createSavedStateHandle(), container.foodRepository, container.settingsRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Selection reset when the range changes
    var selectedIndex by rememberSaveable(state.range) { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storico") },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onBack)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    HistoryRange.entries.forEachIndexed { index, range ->
                        SegmentedButton(
                            selected = state.range == range,
                            onClick = { viewModel.setRange(range) },
                            shape = SegmentedButtonDefaults.itemShape(index, HistoryRange.entries.size),
                        ) { Text(range.label) }
                    }
                }
            }

            item {
                SelectionReadout(
                    day = selectedIndex?.let { state.days.getOrNull(it) },
                    kcalGoal = state.kcalGoal,
                    onOpenDay = onOpenDay,
                )
            }

            item {
                CalorieChart(
                    days = state.days,
                    kcalGoal = state.kcalGoal,
                    selectedIndex = selectedIndex,
                    onSelect = { index -> selectedIndex = if (selectedIndex == index) null else index },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                ) {
                    StatTile(
                        label = "Media giornaliera",
                        value = state.averageKcal?.let { "${it.formatKcal()} kcal" } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Giorni registrati",
                        value = "${state.loggedDays.size} su ${state.completedDays.size}",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Entro l'obiettivo",
                        value = "${state.daysWithinGoal} su ${state.loggedDays.size}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                Text(
                    "Statistiche sui giorni conclusi, oggi escluso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item {
                Text(
                    "Giorni",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
            // List from the most recent day: it is also the chart's "table" view
            items(state.days.asReversed(), key = { it.date.toEpochDay() }) { day ->
                ListItem(
                    headlineContent = { Text(day.date.relativeLabel() ?: day.date.shortLabel()) },
                    trailingContent = {
                        Text(
                            if (day.kcal > 0) "${day.kcal.formatKcal()} kcal" else "nessuna voce",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (day.kcal > 0) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    supportingContent = if (day.kcal > state.kcalGoal) {
                        { Text("Oltre l'obiettivo di ${(day.kcal - state.kcalGoal).formatKcal()} kcal") }
                    } else null,
                    modifier = Modifier.clickable { onOpenDay(day.date) },
                )
            }
        }
    }
}

/** Value of the day tapped in the chart (text tooltip above the chart). */
@Composable
private fun SelectionReadout(day: DayKcal?, kcalGoal: Int, onOpenDay: (LocalDate) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            if (day == null) {
                Text(
                    "Linea tratteggiata: obiettivo di $kcalGoal kcal. Tocca una colonna per i dettagli.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(day.date.relativeLabel() ?: day.date.shortLabel(), style = MaterialTheme.typography.labelLarge)
                val detail = when {
                    day.kcal <= 0 -> "nessuna voce"
                    day.kcal > kcalGoal -> "${day.kcal.formatKcal()} kcal · oltre l'obiettivo di ${(day.kcal - kcalGoal).formatKcal()}"
                    else -> "${day.kcal.formatKcal()} kcal · ${(kcalGoal - day.kcal).formatKcal()} sotto l'obiettivo"
                }
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (day != null) {
            TextButton(onClick = { onOpenDay(day.date) }) { Text("Apri giorno") }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
