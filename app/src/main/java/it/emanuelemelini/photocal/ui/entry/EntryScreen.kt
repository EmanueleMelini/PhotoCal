package it.emanuelemelini.photocal.ui.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.db.SavedFood
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.components.MealSelector
import it.emanuelemelini.photocal.ui.components.NumberField
import it.emanuelemelini.photocal.ui.components.QuantityRow
import it.emanuelemelini.photocal.ui.components.errorText
import it.emanuelemelini.photocal.ui.formatKcal
import it.emanuelemelini.photocal.ui.quantityLabel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    onDone: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val container = appContainer()
    val viewModel: EntryViewModel = viewModel {
        EntryViewModel(createSavedStateHandle(), container.foodRepository, container.savedFoodRepository, container.foodEstimator)
    }
    val form = viewModel.form
    val showErrors = viewModel.showErrors
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel.isDone) {
        if (viewModel.isDone) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isEditing) R.string.entry_title_edit else R.string.entry_title_new)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onDone)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (viewModel.isEditing) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (viewModel.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            viewModel.photoPath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = stringResource(R.string.photo_content_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = { viewModel.onFormChange(form.copy(name = it)) },
                label = { Text(stringResource(R.string.entry_name)) },
                placeholder = { Text(stringResource(R.string.entry_name_placeholder)) },
                isError = showErrors && !form.nameValid,
                supportingText = errorText(showErrors && !form.nameValid, stringResource(R.string.entry_name_required))
                    ?: { Text(stringResource(R.string.entry_name_ai_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (viewModel.suggestions.isNotEmpty()) {
                Suggestions(viewModel.suggestions, onPick = viewModel::useSavedFood)
            }

            AiEstimateSection(
                estimate = viewModel.aiEstimate,
                onEstimate = viewModel::estimateWithAi,
                onOpenAiSettings = onOpenAiSettings,
            )

            MealSelector(
                selected = form.mealType,
                onSelect = { viewModel.onFormChange(form.copy(mealType = it)) },
            )

            QuantityRow(
                quantity = form.quantity,
                onQuantityChange = { viewModel.onFormChange(form.copy(quantity = it)) },
                unit = form.unit,
                onUnitChange = { viewModel.onFormChange(form.copy(unit = it)) },
                isError = showErrors && !form.quantityValid,
                units = viewModel.units,
                pieceGrams = form.pieceGrams,
                onPieceGramsChange = { viewModel.onFormChange(form.copy(pieceGrams = it)) },
                pieceGramsError = showErrors && !form.pieceGramsValid,
                pieceLabel = form.pieceLabel,
            )

            NumberField(
                value = form.kcal,
                onValueChange = { viewModel.onFormChange(form.copy(kcal = it)) },
                label = stringResource(R.string.label_kcal),
                isError = showErrors && !form.kcalValid,
                error = stringResource(R.string.error_required_pl),
                helper = if (viewModel.autoScales) stringResource(R.string.entry_kcal_auto_scale) else null,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.entry_macros_optional), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = form.protein,
                    onValueChange = { viewModel.onFormChange(form.copy(protein = it)) },
                    label = stringResource(R.string.entry_protein_g),
                    isError = showErrors && !form.proteinValid,
                    error = stringResource(R.string.error_invalid),
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = form.carbs,
                    onValueChange = { viewModel.onFormChange(form.copy(carbs = it)) },
                    label = stringResource(R.string.entry_carbs_g),
                    isError = showErrors && !form.carbsValid,
                    error = stringResource(R.string.error_invalid),
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = form.fat,
                    onValueChange = { viewModel.onFormChange(form.copy(fat = it)) },
                    label = stringResource(R.string.entry_fat_g),
                    isError = showErrors && !form.fatValid,
                    error = stringResource(R.string.error_invalid),
                    modifier = Modifier.weight(1f),
                )
            }

            Text(stringResource(R.string.entry_extras_optional), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = form.fiber,
                    onValueChange = { viewModel.onFormChange(form.copy(fiber = it)) },
                    label = stringResource(R.string.label_fiber_g),
                    isError = showErrors && !form.fiberValid,
                    error = stringResource(R.string.error_invalid),
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = form.sugars,
                    onValueChange = { viewModel.onFormChange(form.copy(sugars = it)) },
                    label = stringResource(R.string.label_sugars_g),
                    isError = showErrors && !form.sugarsValid,
                    error = stringResource(R.string.error_invalid),
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = form.salt,
                    onValueChange = { viewModel.onFormChange(form.copy(salt = it)) },
                    label = stringResource(R.string.label_salt_g),
                    isError = showErrors && !form.saltValid,
                    error = stringResource(R.string.error_invalid),
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.entry_delete_title)) },
            text = { Text(stringResource(R.string.entry_delete_text, form.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Saved foods matching the typed name: one tap fills the form. */
@Composable
private fun Suggestions(foods: List<SavedFood>, onPick: (SavedFood) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.entry_suggestions),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        foods.forEach { food ->
            val quantity = quantityLabel(food.grams, food.servingUnit, food.servings, food.pieceLabel)
            val kcal = food.kcalPer100 * food.grams / 100
            ListItem(
                headlineContent = { Text(food.name) },
                supportingContent = { Text(listOfNotNull(quantity, "${kcal.formatKcal()} kcal").joinToString(" · ")) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onPick(food) },
            )
        }
    }
}

@Composable
private fun AiEstimateSection(
    estimate: AiEstimate,
    onEstimate: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val running = estimate == AiEstimate.Running
    OutlinedButton(
        onClick = onEstimate,
        enabled = !running,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (running) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.entry_ai_running))
        } else {
            Text(stringResource(R.string.entry_ai_button))
        }
    }
    when (estimate) {
        is AiEstimate.Done -> {
            val text = listOfNotNull(
                estimate.notes,
                estimate.creaNames.takeIf { it.isNotEmpty() }
                    ?.let { stringResource(R.string.entry_ai_crea_values, it.joinToString()) },
            ).joinToString("\n")
            if (text.isNotEmpty()) {
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is AiEstimate.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                estimate.message.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            if (estimate.needsSettings) {
                TextButton(onClick = onOpenAiSettings) { Text(stringResource(R.string.action_settings)) }
            } else if (estimate.canChangeModel) {
                TextButton(onClick = onOpenAiSettings) { Text(stringResource(R.string.action_change_model)) }
            }
        }
        else -> Unit
    }
}
