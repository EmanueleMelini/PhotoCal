package it.emanuelemelini.photocal.ui.photo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.crea.CreaTable
import it.emanuelemelini.photocal.ui.UiText
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.components.MealSelector
import it.emanuelemelini.photocal.ui.formatKcal
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoReviewScreen(
    onDone: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val container = appContainer()
    val viewModel: PhotoReviewViewModel = viewModel {
        PhotoReviewViewModel(
            createSavedStateHandle(),
            container.foodRepository,
            container.photoStorage,
            container.foodEstimator,
        )
    }
    val status = viewModel.status
    val isAnalyzing = status == ReviewStatus.Analyzing
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel.isDone) {
        if (viewModel.isDone) onDone()
    }

    // Asks for confirmation before leaving if there are unsaved foods
    val hasUnsavedWork = viewModel.items.isNotEmpty()
    val requestExit = { if (hasUnsavedWork) confirmDiscard = true else onDone() }
    BackHandler(enabled = hasUnsavedWork) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.photo_title)) },
                navigationIcon = {
                    IconButton(onClick = requestExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            AsyncImage(
                model = File(viewModel.photoPath),
                contentDescription = stringResource(R.string.photo_content_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(MaterialTheme.shapes.medium),
            )

            OutlinedTextField(
                value = viewModel.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text(stringResource(R.string.photo_notes)) },
                placeholder = { Text(stringResource(R.string.photo_notes_placeholder)) },
                enabled = !isAnalyzing,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            val analyzeLabel = stringResource(if (viewModel.hasAnalyzed) R.string.photo_analyze_again else R.string.photo_analyze)
            if (viewModel.hasAnalyzed) {
                OutlinedButton(
                    onClick = viewModel::analyze,
                    enabled = viewModel.canAnalyze && !isAnalyzing,
                    modifier = Modifier.fillMaxWidth(),
                ) { AnalyzeButtonContent(isAnalyzing, analyzeLabel) }
            } else {
                Button(
                    onClick = viewModel::analyze,
                    enabled = viewModel.canAnalyze && !isAnalyzing,
                    modifier = Modifier.fillMaxWidth(),
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

            viewModel.aiNotes?.let { notes ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (viewModel.hasAnalyzed) {
                if (viewModel.items.isEmpty()) {
                    Text(
                        stringResource(R.string.photo_nothing_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.photo_review_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                viewModel.items.forEach { item ->
                    ReviewItemCard(
                        item = item,
                        showErrors = viewModel.showErrors,
                        onChange = { transform -> viewModel.updateItem(item.key, transform) },
                        onRemove = { viewModel.removeItem(item.key) },
                    )
                }

                TextButton(onClick = viewModel::addItem) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.photo_add_food))
                }

                if (viewModel.items.isNotEmpty()) {
                    MealSelector(selected = viewModel.mealType, onSelect = viewModel::onMealTypeChange)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.label_total), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            "${viewModel.totalKcal.formatKcal()} kcal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    if (viewModel.usesCrea) {
                        Text(
                            stringResource(R.string.photo_nutrition_source, CreaTable.SOURCE),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Button(
                        onClick = viewModel::save,
                        enabled = !isAnalyzing,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.action_save)) }
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
private fun AnalyzeButtonContent(isAnalyzing: Boolean, label: String) {
    if (isAnalyzing) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.photo_analyzing))
    } else {
        Text(label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ErrorCard(
    message: UiText,
    needsSettings: Boolean,
    canRetry: Boolean,
    canChangeModel: Boolean,
    onRetry: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onManual: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                message.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            // Three buttons may not fit on one line of a narrow phone
            FlowRow(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (needsSettings) {
                    TextButton(onClick = onOpenAiSettings) { Text(stringResource(R.string.action_settings)) }
                } else if (canRetry) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
                if (canChangeModel) {
                    TextButton(onClick = onOpenAiSettings) { Text(stringResource(R.string.action_change_model)) }
                }
                TextButton(onClick = onManual) { Text(stringResource(R.string.action_manual_entry)) }
            }
        }
    }
}

@Composable
private fun ReviewItemCard(
    item: ReviewItem,
    showErrors: Boolean,
    onChange: ((ReviewItem) -> ReviewItem) -> Unit,
    onRemove: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = item.name,
                    onValueChange = { text -> onChange { it.copy(name = text) } },
                    label = { Text(stringResource(R.string.photo_food)) },
                    isError = showErrors && !item.nameValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.photo_remove_food))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    item.confidence?.let { ConfidenceLabel(it) }
                    Text(
                        text = if (item.usingCrea) stringResource(R.string.photo_values_crea, item.creaName.orEmpty())
                        else stringResource(R.string.photo_values_ai),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.creaPerGram != null && item.aiPerGram != null) {
                    TextButton(onClick = { onChange { it.withCrea(!item.usingCrea) } }) {
                        Text(stringResource(if (item.usingCrea) R.string.photo_use_ai else R.string.photo_use_crea))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallNumberField(item.grams, { t -> onChange { it.withGrams(t) } }, stringResource(R.string.label_grams),
                    showErrors && !item.gramsValid, Modifier.weight(1f))
                SmallNumberField(item.kcal, { t -> onChange { it.withKcal(t) } }, stringResource(R.string.label_kcal),
                    showErrors && !item.kcalValid, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallNumberField(item.protein, { t -> onChange { it.withProtein(t) } }, stringResource(R.string.photo_protein_short),
                    showErrors && !item.proteinValid, Modifier.weight(1f))
                SmallNumberField(item.carbs, { t -> onChange { it.withCarbs(t) } }, stringResource(R.string.photo_carbs_short),
                    showErrors && !item.carbsValid, Modifier.weight(1f))
                SmallNumberField(item.fat, { t -> onChange { it.withFat(t) } }, stringResource(R.string.entry_fat_g),
                    showErrors && !item.fatValid, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ConfidenceLabel(confidence: String) {
    // The schema uses language-neutral values: high / medium / low
    val (color, label) = when (confidence) {
        "high" -> MaterialTheme.colorScheme.primary to R.string.confidence_high
        "medium" -> MaterialTheme.colorScheme.tertiary to R.string.confidence_medium
        else -> MaterialTheme.colorScheme.error to R.string.confidence_low
    }
    Text(
        text = stringResource(R.string.confidence_label, stringResource(label)),
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

@Composable
private fun SmallNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label, maxLines = 1) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
