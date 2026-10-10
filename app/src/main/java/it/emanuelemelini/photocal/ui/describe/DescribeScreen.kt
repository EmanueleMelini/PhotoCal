package it.emanuelemelini.photocal.ui.describe

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.crea.CreaTable
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.components.SaveBar
import it.emanuelemelini.photocal.ui.components.shimmer
import it.emanuelemelini.photocal.ui.components.staggeredEntrance
import it.emanuelemelini.photocal.ui.formatKcal
import it.emanuelemelini.photocal.ui.parseDecimal
import it.emanuelemelini.photocal.ui.photo.AnalyzeButtonContent
import it.emanuelemelini.photocal.ui.photo.ErrorCard
import it.emanuelemelini.photocal.ui.photo.ReviewItemCard
import it.emanuelemelini.photocal.ui.photo.ReviewStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescribeScreen(
    onDone: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val container = appContainer()
    val viewModel: DescribeViewModel = viewModel {
        DescribeViewModel(createSavedStateHandle(), container.foodRepository, container.foodEstimator)
    }
    val status = viewModel.status
    val isAnalyzing = status == ReviewStatus.Analyzing
    val fixedMeal = viewModel.fixedMeal
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel.isDone) {
        if (viewModel.isDone) onDone()
    }

    val hasUnsavedWork = viewModel.items.isNotEmpty()
    val requestExit = { if (hasUnsavedWork) confirmDiscard = true else onDone() }
    BackHandler(enabled = hasUnsavedWork) { confirmDiscard = true }

    // Voice typing with the speech recognizer of the system: the text lands in the field,
    // where it can be checked before the AI reads it
    val context = LocalContext.current
    val speechPrompt = stringResource(R.string.describe_speak_prompt)
    val speechIntent = remember(speechPrompt) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, AppLocale.current.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, speechPrompt)
    }
    var canDictate by remember { mutableStateOf(speechIntent.resolveActivity(context.packageManager) != null) }
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(viewModel::appendDictation)
        }
    }
    val startDictation = {
        try {
            dictate.launch(speechIntent)
        } catch (_: ActivityNotFoundException) {
            canDictate = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (fixedMeal != null) stringResource(R.string.describe_title_meal, stringResource(fixedMeal.labelRes))
                        else stringResource(R.string.describe_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = requestExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = viewModel.items.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                SaveBar(onClick = viewModel::save, enabled = !isAnalyzing, kcal = viewModel.totalKcal)
            }
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                stringResource(if (fixedMeal != null) R.string.describe_intro_meal else R.string.describe_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = viewModel.text,
                onValueChange = viewModel::onTextChange,
                label = { Text(stringResource(R.string.describe_label)) },
                placeholder = {
                    Text(stringResource(if (fixedMeal != null) R.string.describe_placeholder_meal else R.string.describe_placeholder))
                },
                enabled = !isAnalyzing,
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (canDictate) {
                    OutlinedButton(
                        onClick = startDictation,
                        enabled = !isAnalyzing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(painterResource(R.drawable.ic_mic), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.describe_dictate))
                    }
                }
                val analyzeLabel = stringResource(if (viewModel.hasAnalyzed) R.string.photo_analyze_again else R.string.photo_analyze)
                Button(
                    onClick = viewModel::analyze,
                    enabled = !isAnalyzing && viewModel.text.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { AnalyzeButtonContent(isAnalyzing, analyzeLabel) }
            }

            if (status is ReviewStatus.Error) {
                ErrorCard(
                    message = status.message,
                    needsSettings = status.needsSettings,
                    canRetry = status.canRetry,
                    canChangeModel = status.canChangeModel,
                    onRetry = viewModel::analyze,
                    onOpenAiSettings = {
                        viewModel.dismissError()
                        onOpenAiSettings()
                    },
                    onManual = {
                        viewModel.dismissError()
                        viewModel.addItem()
                    },
                )
            }

            // Placeholders of the foods to come while the AI reads the text
            AnimatedVisibility(visible = isAnalyzing && !viewModel.hasAnalyzed, enter = fadeIn(), exit = fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(SKELETON_ITEMS) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .shimmer(),
                        )
                    }
                }
            }

            viewModel.aiNotes?.let { notes ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                }
            }

            if (viewModel.hasAnalyzed) {
                Text(
                    stringResource(if (viewModel.items.isEmpty()) R.string.photo_nothing_found else R.string.photo_review_hint),
                    style = if (viewModel.items.isEmpty()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                var index = 0
                viewModel.groups.forEach { (meal, mealItems) ->
                    MealTitle(meal, mealItems.sumOf { parseDecimal(it.item.kcal) ?: 0.0 })
                    mealItems.forEach { described ->
                        val item = described.item
                        // Keyed: a food moved to another meal keeps its fields
                        key(item.key) {
                            ReviewItemCard(
                                item = item,
                                showErrors = viewModel.showErrors,
                                onChange = { transform -> viewModel.updateItem(item.key, transform) },
                                onRemove = { viewModel.removeItem(item.key) },
                                modifier = Modifier.staggeredEntrance(index++),
                                footer = if (fixedMeal == null) {
                                    { MealPicker(described.meal) { viewModel.changeMeal(item.key, it) } }
                                } else null,
                            )
                        }
                    }
                }

                TextButton(onClick = { viewModel.addItem() }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.photo_add_food))
                }

                if (viewModel.usesCrea) {
                    Text(
                        stringResource(R.string.photo_nutrition_source, CreaTable.SOURCE),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.photo_discard_title)) },
            text = { Text(stringResource(R.string.photo_discard_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onDone()
                }) { Text(stringResource(R.string.photo_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun MealTitle(meal: MealType, kcal: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(
            stringResource(meal.labelRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${kcal.formatKcal()} kcal",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Meal of one food, to move it when the AI put it in the wrong one. */
@Composable
private fun MealPicker(meal: MealType, onSelect: (MealType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(stringResource(R.string.describe_meal_of_food, stringResource(meal.labelRes))) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MealType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

private const val SKELETON_ITEMS = 3
