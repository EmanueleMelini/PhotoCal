package it.emanuelemelini.photocal.ui.today

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.components.animatedProgress
import it.emanuelemelini.photocal.ui.components.countUp
import it.emanuelemelini.photocal.ui.extrasLabel
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.formatKcal
import it.emanuelemelini.photocal.ui.longLabel
import it.emanuelemelini.photocal.ui.quantityLabel
import it.emanuelemelini.photocal.ui.relativeLabel
import it.emanuelemelini.photocal.ui.shortLabel
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    requestedDay: LocalDate?,
    onRequestedDayHandled: () -> Unit,
    requestedPhotoMeal: PhotoRequest?,
    onRequestedPhotoHandled: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenGoals: () -> Unit,
    onAddManual: (LocalDate) -> Unit,
    onEditEntry: (FoodEntry) -> Unit,
    onOpenSettings: () -> Unit,
    /** Settings scrolled to the AI section. */
    onOpenAiSettings: () -> Unit,
    onPhotoTaken: (photoPath: String, date: LocalDate, meal: MealType?) -> Unit,
    onScanBarcode: (LocalDate) -> Unit,
    onOpenRecent: (LocalDate) -> Unit,
    onShare: (LocalDate) -> Unit,
) {
    val container = appContainer()
    val photoStorage = container.photoStorage
    val viewModel: TodayViewModel = viewModel {
        TodayViewModel(
            createSavedStateHandle(),
            container.foodRepository,
            container.waterRepository,
            container.settingsRepository,
            container.healthConnect,
        )
    }
    // Steps and calories change during the day: read them again when coming back
    LifecycleResumeEffect(Unit) {
        viewModel.refreshActivity()
        onPauseOrDispose {}
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var copyRequest by remember { mutableStateOf<CopyRequest?>(null) }
    var copied by remember { mutableStateOf<Pair<LocalDate, Int>?>(null) }
    var bottleAdded by remember { mutableStateOf<BottleDrink?>(null) }

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
    var pendingPhotoMeal by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingPhotoPath
        val day = pendingPhotoDay
        val meal = MealType.entries.find { it.name == pendingPhotoMeal }
        pendingPhotoPath = null
        pendingPhotoDay = null
        pendingPhotoMeal = null
        if (path == null || day == null) return@rememberLauncherForActivityResult
        if (success) onPhotoTaken(path, LocalDate.ofEpochDay(day), meal) else photoStorage.delete(path)
    }

    val photoNeedsKey = stringResource(R.string.today_photo_needs_key)
    val settingsLabel = stringResource(R.string.action_settings)
    val noCamera = stringResource(R.string.today_no_camera)

    fun startPhoto(meal: MealType? = null, date: LocalDate = state.date) {
        fabExpanded = false
        if (!state.aiConfigured) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = photoNeedsKey,
                    actionLabel = settingsLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) onOpenAiSettings()
            }
            return
        }
        val file = photoStorage.newPhotoFile()
        pendingPhotoPath = file.absolutePath
        pendingPhotoDay = date.toEpochDay()
        pendingPhotoMeal = meal?.name
        try {
            takePicture.launch(photoStorage.uriFor(file))
        } catch (_: ActivityNotFoundException) {
            pendingPhotoPath = null
            pendingPhotoDay = null
            pendingPhotoMeal = null
            scope.launch { snackbarHostState.showSnackbar(noCamera) }
        }
    }

    // Photo requested from a reminder notification
    LaunchedEffect(requestedPhotoMeal) {
        requestedPhotoMeal?.let {
            onRequestedPhotoHandled()
            // Reminders are always about today, whatever day is being shown
            startPhoto(it.meal, LocalDate.now())
        }
    }

    // The new day slides in from the side it comes from
    val dayShift = remember { Animatable(0f) }
    var shownDate by remember { mutableStateOf(state.date) }
    LaunchedEffect(state.date) {
        val previous = shownDate
        shownDate = state.date
        if (previous == state.date) return@LaunchedEffect
        dayShift.snapTo(if (state.date > previous) 1f else -1f)
        dayShift.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
    }
    val dayModifier = Modifier.graphicsLayer {
        translationX = dayShift.value * size.width * DAY_SHIFT
        alpha = 1f - abs(dayShift.value)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (state.meals.isNotEmpty()) {
                        IconButton(onClick = {
                            copyRequest = CopyRequest(state.meals.flatMap { it.entries }, state.date, null, R.string.copy_day_title)
                        }) {
                            Icon(painterResource(R.drawable.ic_content_copy), contentDescription = stringResource(R.string.copy_day))
                        }
                    }
                    if (state.canShare) {
                        IconButton(onClick = { onShare(state.date) }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_day))
                        }
                    }
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
                onPhoto = { startPhoto() },
                onBarcode = {
                    fabExpanded = false
                    onScanBarcode(state.date)
                },
                onManual = {
                    fabExpanded = false
                    onAddManual(state.date)
                },
                onRecent = {
                    fabExpanded = false
                    onOpenRecent(state.date)
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
            item(key = "summary") {
                SummaryCard(state = state, onClick = onOpenGoals, modifier = dayModifier)
            }
            item(key = "water") {
                WaterCard(
                    state = state,
                    onAdd = viewModel::addGlass,
                    onRemove = viewModel::removeGlass,
                    onAddBottle = { viewModel.addBottle()?.let { bottleAdded = it } },
                    onClick = onOpenGoals,
                    modifier = dayModifier,
                )
            }
            if (!state.isLoading && state.meals.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.today_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .animateItem()
                            .then(dayModifier)
                            .fillMaxWidth()
                            .padding(32.dp),
                    )
                }
            }
            state.meals.forEach { group ->
                item(key = "meal-${group.mealType}") {
                    MealHeader(
                        group,
                        onCopy = {
                            copyRequest = CopyRequest(group.entries, state.date, group.mealType, R.string.copy_meal_title)
                        },
                        modifier = Modifier.animateItem().then(dayModifier),
                    )
                }
                items(group.entries, key = { it.id }) { entry ->
                    EntryRow(
                        entry = entry,
                        onClick = { onEditEntry(entry) },
                        onDuplicate = { viewModel.duplicate(entry) },
                        onCopy = {
                            copyRequest = CopyRequest(listOf(entry), entry.date, entry.mealType, R.string.copy_entry_title)
                        },
                        // New entries slide in, deleted ones close up
                        modifier = Modifier.animateItem().then(dayModifier),
                    )
                }
            }
        }
    }

    copyRequest?.let { request ->
        CopyDialog(
            request = request,
            onDismiss = { copyRequest = null },
            onCopy = { date, meal ->
                copyRequest = null
                viewModel.copy(request.entries, date, meal) { count -> copied = date to count }
            },
        )
    }

    // Snackbar after a copy, with a shortcut to the day the entries went to
    copied?.let { (date, count) ->
        val message = pluralStringResource(R.plurals.copy_done, count, count, date.relativeLabel() ?: date.shortLabel())
        val go = stringResource(R.string.copy_go)
        LaunchedEffect(date, count) {
            copied = null
            // In the screen scope: clearing [copied] ends this effect, not the snackbar
            scope.launch {
                val result = snackbarHostState.showSnackbar(message, actionLabel = go, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) viewModel.selectDate(date)
            }
        }
    }

    // Snackbar after a whole bottle: a big amount, so it can be undone
    bottleAdded?.let { drink ->
        val message = stringResource(R.string.water_bottle_added, drink.ml)
        val undo = stringResource(R.string.action_undo)
        LaunchedEffect(drink) {
            bottleAdded = null
            scope.launch {
                val result = snackbarHostState.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) viewModel.undoBottle(drink)
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
private fun SummaryCard(state: TodayUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val totals = state.totals
    val kcalGoal = state.kcalGoal
    val progress = animatedProgress(if (kcalGoal > 0) (totals.kcal / kcalGoal).toFloat() else 0f, label = "kcal_progress")
    val kcal = countUp(totals.kcal, label = "kcal")
    val remaining = kcalGoal - totals.kcal
    val overGoal = remaining < 0
    val barColor by animateColorAsState(
        if (overGoal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "kcal_color",
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = kcal.formatKcal(),
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
                progress = { progress },
                color = barColor,
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
            activityLabel(state)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                MacroItem(stringResource(R.string.macro_protein), totals.proteinG, state.proteinGoalG)
                MacroItem(stringResource(R.string.macro_carbs), totals.carbsG, state.carbsGoalG)
                MacroItem(stringResource(R.string.macro_fat), totals.fatG, state.fatGoalG)
            }
            extrasLabel(totals.fiberG, totals.sugarsG, totals.saltG)?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Steps and burned calories from Health Connect, e.g. "6.540 passi · 320 kcal bruciate", and
 * the base goal when they are added to it.
 */
@Composable
private fun activityLabel(state: TodayUiState): String? {
    val activity = state.activity ?: return null
    val parts = listOfNotNull(
        activity.steps?.let { steps ->
            val count = steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            pluralStringResource(R.plurals.today_steps, count, NumberFormat.getIntegerInstance(AppLocale.current).format(steps))
        },
        activity.activeKcal?.let { stringResource(R.string.today_burned, it.formatKcal()) },
    )
    if (parts.isEmpty()) return null
    val line = parts.joinToString(" · ")
    return if (state.burnedKcalAdded > 0) stringResource(R.string.today_goal_with_burned, line, state.baseKcalGoal, state.burnedKcalAdded) else line
}

@Composable
private fun MacroItem(label: String, target: Double, goal: Int?) {
    val grams = countUp(target, label = label)
    val progress = animatedProgress(if (goal != null && goal > 0) (target / goal).toFloat() else 0f, label = label)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // With a goal: "45 / 120 g" and a small progress bar
        Text(
            if (goal != null) "${grams.formatAmount()} / $goal g" else "${grams.formatAmount()} g",
            style = MaterialTheme.typography.titleMedium,
        )
        if (goal != null && goal > 0) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .width(72.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MealHeader(group: MealGroup, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp),
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
        IconButton(onClick = onCopy) {
            Icon(
                painterResource(R.drawable.ic_content_copy),
                contentDescription = stringResource(R.string.copy_meal),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Tap to edit; long press for duplicate and copy. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: FoodEntry,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val details = listOfNotNull(
        entry.quantityLabel(),
        entry.proteinG?.let { stringResource(R.string.macro_short_protein, it.formatAmount()) },
        entry.carbsG?.let { stringResource(R.string.macro_short_carbs, it.formatAmount()) },
        entry.fatG?.let { stringResource(R.string.macro_short_fat, it.formatAmount()) },
    ).joinToString(" · ")

    Box(modifier) {
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
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { menuOpen = true },
                onLongClickLabel = stringResource(R.string.entry_actions),
            ),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_duplicate)) },
                onClick = {
                    menuOpen = false
                    onDuplicate()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_entry)) },
                onClick = {
                    menuOpen = false
                    onCopy()
                },
            )
        }
    }
}

/**
 * "+" FAB that expands into the add actions: the + turns into a ×, the actions rise one after
 * the other starting from the closest one.
 */
@Composable
private fun AddFab(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPhoto: () -> Unit,
    onBarcode: () -> Unit,
    onManual: () -> Unit,
    onRecent: () -> Unit,
) {
    val actions = listOf(
        Triple(stringResource(R.string.fab_photo), painterResource(R.drawable.ic_photo_camera), onPhoto),
        Triple(stringResource(R.string.fab_barcode), painterResource(R.drawable.ic_barcode), onBarcode),
        Triple(stringResource(R.string.fab_recent), painterResource(R.drawable.ic_history), onRecent),
        Triple(stringResource(R.string.fab_manual), rememberVectorPainter(Icons.Default.Edit), onManual),
    )
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 135f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "fab_rotation",
    )
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        actions.forEachIndexed { index, (label, icon, onClick) ->
            // Steps from the bottom: the action next to the FAB comes first
            val step = actions.lastIndex - index
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(160, delayMillis = step * FAB_STAGGER_MILLIS)) +
                    slideInVertically(tween(220, delayMillis = step * FAB_STAGGER_MILLIS)) { it / 2 } +
                    scaleIn(tween(220, delayMillis = step * FAB_STAGGER_MILLIS), initialScale = 0.8f),
                exit = fadeOut(tween(120, delayMillis = index * FAB_STAGGER_MILLIS / 2)) +
                    slideOutVertically(tween(160, delayMillis = index * FAB_STAGGER_MILLIS / 2)) { it / 2 },
            ) {
                FabAction(label, icon, onClick = onClick, modifier = Modifier.padding(end = 4.dp))
            }
        }
        FloatingActionButton(onClick = { onExpandedChange(!expanded) }) {
            // One + that turns into a × while rotating
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(if (expanded) R.string.action_close else R.string.action_add),
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
            )
        }
    }
}

@Composable
private fun FabAction(label: String, icon: Painter, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
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

/** Photo requested from outside (a reminder), for [meal] if known. */
data class PhotoRequest(val meal: MealType?)

private const val FAB_STAGGER_MILLIS = 40

/** Share of the width the new day slides in from. */
private const val DAY_SHIFT = 0.25f
