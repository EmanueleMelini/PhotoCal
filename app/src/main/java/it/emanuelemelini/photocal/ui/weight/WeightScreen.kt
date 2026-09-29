package it.emanuelemelini.photocal.ui.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.db.WeightEntry
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.formatSignedAmount
import it.emanuelemelini.photocal.ui.parseDecimal
import it.emanuelemelini.photocal.ui.relativeLabel
import it.emanuelemelini.photocal.ui.shortLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(onBack: () -> Unit) {
    val container = appContainer()
    val viewModel: WeightViewModel = viewModel {
        WeightViewModel(createSavedStateHandle(), container.weightRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selected by remember(state.range) { mutableStateOf<WeightEntry?>(null) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<WeightEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weight_log_title)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onBack)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.weight_add))
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    WeightRange.entries.forEachIndexed { index, range ->
                        SegmentedButton(
                            selected = state.range == range,
                            onClick = { viewModel.setRange(range) },
                            shape = SegmentedButtonDefaults.itemShape(index, WeightRange.entries.size),
                        ) { Text(stringResource(range.labelRes)) }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    val shown = selected ?: state.inRange.lastOrNull()
                    if (shown == null) {
                        Text(
                            stringResource(R.string.weight_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "${shown.weightKg.formatAmount()} kg · ${shown.date.relativeLabel() ?: shown.date.shortLabel()}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        state.change?.let { change ->
                            Text(
                                stringResource(R.string.weight_change_in_range, change.formatSignedAmount()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                WeightChart(
                    entries = state.inRange,
                    from = state.from,
                    selected = selected,
                    onSelect = { selected = if (selected == it) null else it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                Text(
                    stringResource(R.string.weight_all_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
            items(state.all, key = { it.id }) { entry ->
                ListItem(
                    headlineContent = { Text("${entry.weightKg.formatAmount()} kg") },
                    supportingContent = { Text(entry.date.relativeLabel() ?: entry.date.shortLabel()) },
                    trailingContent = {
                        IconButton(onClick = { toDelete = entry }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    },
                )
            }
        }
    }

    if (showAddDialog) {
        AddWeightDialog(
            onConfirm = { date, kg ->
                viewModel.record(date, kg)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
    toDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.weight_delete_title)) },
            text = { Text("${entry.weightKg.formatAmount()} kg · ${entry.date.shortLabel()}") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(entry)
                    toDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** New weigh-in: today by default; a weigh-in on a day that already has one replaces it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWeightDialog(onConfirm: (LocalDate, Double) -> Unit, onDismiss: () -> Unit) {
    var weight by rememberSaveable { mutableStateOf("") }
    var dayEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(dayEpoch)
    val kg = parseDecimal(weight)?.takeIf { it in 25.0..350.0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { text -> weight = text.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                    label = { Text(stringResource(R.string.profile_weight_kg)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Row {
                    TextButton(onClick = { pickingDate = true }) {
                        Text(date.relativeLabel() ?: date.shortLabel())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { kg?.let { onConfirm(date, it) } }, enabled = kg != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (pickingDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        // No weigh-ins in the future
                        val picked = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        dayEpoch = minOf(picked, LocalDate.now()).toEpochDay()
                    }
                    pickingDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}
