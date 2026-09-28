package it.emanuelemelini.photocal.ui.today

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.Totals
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.formatKcal
import it.emanuelemelini.photocal.ui.longLabel
import it.emanuelemelini.photocal.ui.quantityLabel
import it.emanuelemelini.photocal.ui.relativeLabel
import it.emanuelemelini.photocal.ui.shortLabel
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    requestedDay: LocalDate?,
    onRequestedDayHandled: () -> Unit,
    onOpenHistory: () -> Unit,
    onAddManual: (LocalDate) -> Unit,
    onEditEntry: (FoodEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onPhotoTaken: (photoPath: String, date: LocalDate) -> Unit,
    onScanBarcode: (LocalDate) -> Unit,
) {
    val container = appContainer()
    val photoStorage = container.photoStorage
    val viewModel: TodayViewModel = viewModel {
        TodayViewModel(createSavedStateHandle(), container.foodRepository, container.settingsRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = fabExpanded) { fabExpanded = false }

    // Day picked in History
    LaunchedEffect(requestedDay) {
        requestedDay?.let {
            viewModel.selectDate(it)
            onRequestedDayHandled()
        }
    }

    // File and day of the pending picture: they survive if the system kills the app
    // while the camera is open
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPhotoDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingPhotoPath
        val day = pendingPhotoDay
        pendingPhotoPath = null
        pendingPhotoDay = null
        if (path == null || day == null) return@rememberLauncherForActivityResult
        if (success) onPhotoTaken(path, LocalDate.ofEpochDay(day)) else photoStorage.delete(path)
    }

    val photoNeedsKey = stringResource(R.string.today_photo_needs_key)
    val settingsLabel = stringResource(R.string.action_settings)
    val noCamera = stringResource(R.string.today_no_camera)

    fun startPhoto() {
        fabExpanded = false
        if (!state.hasApiKey) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = photoNeedsKey,
                    actionLabel = settingsLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) onOpenSettings()
            }
            return
        }
        val file = photoStorage.newPhotoFile()
        pendingPhotoPath = file.absolutePath
        pendingPhotoDay = state.date.toEpochDay()
        try {
            takePicture.launch(photoStorage.uriFor(file))
        } catch (_: ActivityNotFoundException) {
            pendingPhotoPath = null
            pendingPhotoDay = null
            scope.launch { snackbarHostState.showSnackbar(noCamera) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(painterResource(R.drawable.ic_bar_chart), contentDescription = stringResource(R.string.today_history))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            AddFab(
                expanded = fabExpanded,
                onExpandedChange = { fabExpanded = it },
                onPhoto = ::startPhoto,
                onBarcode = {
                    fabExpanded = false
                    onScanBarcode(state.date)
                },
                onManual = {
                    fabExpanded = false
                    onAddManual(state.date)
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                // Extra space so the FAB doesn't cover the last entry
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            item {
                DateSelector(
                    date = state.date,
                    onPrevious = viewModel::previousDay,
                    onNext = viewModel::nextDay,
                    onPickDate = { showDatePicker = true },
                )
            }
            item {
                SummaryCard(totals = state.totals, kcalGoal = state.kcalGoal)
            }
            if (!state.isLoading && state.meals.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.today_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    )
                }
            }
            state.meals.forEach { group ->
                item(key = "meal-${group.mealType}") {
                    MealHeader(group)
                }
                items(group.entries, key = { it.id }) { entry ->
                    EntryRow(entry = entry, onClick = { onEditEntry(entry) })
                }
            }
        }
    }

    if (showDatePicker) {
        // The DatePicker works with UTC milliseconds at midnight
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.selectDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.selectDate(LocalDate.now())
                    showDatePicker = false
                }) { Text(stringResource(R.string.today_go_to_today)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DateSelector(
    date: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.today_previous_day))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPickDate)
                .padding(vertical = 4.dp),
        ) {
            val relative = date.relativeLabel()
            Text(
                text = relative ?: date.shortLabel(),
                style = MaterialTheme.typography.titleLarge,
            )
            if (relative != null) {
                Text(
                    text = date.longLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.today_next_day))
        }
    }
}

@Composable
private fun SummaryCard(totals: Totals, kcalGoal: Int) {
    val progress = if (kcalGoal > 0) (totals.kcal / kcalGoal).toFloat() else 0f
    val remaining = kcalGoal - totals.kcal
    val overGoal = remaining < 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = totals.kcal.formatKcal(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = " / $kcalGoal kcal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = if (overGoal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (overGoal) stringResource(R.string.today_over_goal, (-remaining).formatKcal())
                else stringResource(R.string.today_remaining, remaining.formatKcal()),
                style = MaterialTheme.typography.bodyMedium,
                color = if (overGoal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                MacroItem(stringResource(R.string.macro_protein), totals.proteinG)
                MacroItem(stringResource(R.string.macro_carbs), totals.carbsG)
                MacroItem(stringResource(R.string.macro_fat), totals.fatG)
            }
        }
    }
}

@Composable
private fun MacroItem(label: String, grams: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${grams.formatAmount()} g", style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MealHeader(group: MealGroup) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    ) {
        Text(
            text = stringResource(group.mealType.labelRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${group.kcal.formatKcal()} kcal",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun EntryRow(entry: FoodEntry, onClick: () -> Unit) {
    val details = listOfNotNull(
        entry.quantityLabel(),
        entry.proteinG?.let { stringResource(R.string.macro_short_protein, it.formatAmount()) },
        entry.carbsG?.let { stringResource(R.string.macro_short_carbs, it.formatAmount()) },
        entry.fatG?.let { stringResource(R.string.macro_short_fat, it.formatAmount()) },
    ).joinToString(" · ")

    ListItem(
        headlineContent = { Text(entry.name) },
        leadingContent = entry.photoPath?.let { path ->
            {
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MaterialTheme.shapes.small),
                )
            }
        },
        supportingContent = if (details.isNotEmpty()) {
            { Text(details) }
        } else null,
        trailingContent = {
            Text("${entry.kcal.formatKcal()} kcal", style = MaterialTheme.typography.bodyLarge)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** "+" FAB that expands into the three add actions. */
@Composable
private fun AddFab(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPhoto: () -> Unit,
    onBarcode: () -> Unit,
    onManual: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(end = 4.dp),
            ) {
                FabAction(stringResource(R.string.fab_photo), painterResource(R.drawable.ic_photo_camera), onClick = onPhoto)
                FabAction(stringResource(R.string.fab_barcode), painterResource(R.drawable.ic_barcode), onClick = onBarcode)
                FabAction(stringResource(R.string.fab_manual), rememberVectorPainter(Icons.Default.Edit), onClick = onManual)
            }
        }
        FloatingActionButton(onClick = { onExpandedChange(!expanded) }) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = stringResource(if (expanded) R.string.action_close else R.string.action_add),
            )
        }
    }
}

@Composable
private fun FabAction(label: String, icon: Painter, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The label is tappable too, not just the button
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.small,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        SmallFloatingActionButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
    }
}
