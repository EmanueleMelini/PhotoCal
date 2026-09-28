package it.emanuelemelini.photocal.ui.entry

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.components.MealSelector
import it.emanuelemelini.photocal.ui.components.NumberField
import it.emanuelemelini.photocal.ui.components.QuantityRow
import it.emanuelemelini.photocal.ui.components.errorText
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    onDone: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = appContainer()
    val viewModel: EntryViewModel = viewModel {
        EntryViewModel(createSavedStateHandle(), container.foodRepository, container.foodEstimator)
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
                title = { Text(if (viewModel.isEditing) "Modifica voce" else "Nuova voce") },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onDone)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (viewModel.isEditing) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Elimina")
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
                    contentDescription = "Foto del pasto",
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
                label = { Text("Nome") },
                placeholder = { Text("es. 2 fette di pane integrale") },
                isError = showErrors && !form.nameValid,
                supportingText = errorText(showErrors && !form.nameValid, "Inserisci un nome")
                    ?: { Text("La stima AI usa il nome e, se c'è, la quantità") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            AiEstimateSection(
                estimate = viewModel.aiEstimate,
                onEstimate = viewModel::estimateWithAi,
                onOpenSettings = onOpenSettings,
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
            )

            NumberField(
                value = form.kcal,
                onValueChange = { viewModel.onFormChange(form.copy(kcal = it)) },
                label = "Kcal",
                isError = showErrors && !form.kcalValid,
                error = "Obbligatorie",
                helper = if (viewModel.autoScales) "Si ricalcolano cambiando la quantità" else null,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Macronutrienti (facoltativi)", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = form.protein,
                    onValueChange = { viewModel.onFormChange(form.copy(protein = it)) },
                    label = "Proteine g",
                    isError = showErrors && !form.proteinValid,
                    error = "Non valido",
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = form.carbs,
                    onValueChange = { viewModel.onFormChange(form.copy(carbs = it)) },
                    label = "Carboidr. g",
                    isError = showErrors && !form.carbsValid,
                    error = "Non valido",
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = form.fat,
                    onValueChange = { viewModel.onFormChange(form.copy(fat = it)) },
                    label = "Grassi g",
                    isError = showErrors && !form.fatValid,
                    error = "Non valido",
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) { Text("Salva") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare la voce?") },
            text = { Text("\"${form.name}\" verrà rimossa dal diario.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun AiEstimateSection(
    estimate: AiEstimate,
    onEstimate: () -> Unit,
    onOpenSettings: () -> Unit,
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
            Text("Stima in corso…")
        } else {
            Text("Stima con AI")
        }
    }
    when (estimate) {
        is AiEstimate.Done -> estimate.notes?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is AiEstimate.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                estimate.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            if (estimate.needsSettings) {
                TextButton(onClick = onOpenSettings) { Text("Impostazioni") }
            }
        }
        else -> Unit
    }
}
