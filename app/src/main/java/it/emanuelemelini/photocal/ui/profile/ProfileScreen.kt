package it.emanuelemelini.photocal.ui.profile

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.nutrition.ActivityLevel
import it.emanuelemelini.photocal.data.nutrition.EnergyEstimate
import it.emanuelemelini.photocal.data.nutrition.Sex
import it.emanuelemelini.photocal.data.nutrition.WeightGoal
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.components.Avatar
import it.emanuelemelini.photocal.ui.components.errorText
import it.emanuelemelini.photocal.ui.formatLiters
import it.emanuelemelini.photocal.ui.shortLabel
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenWeightLog: () -> Unit,
) {
    val container = appContainer()
    val viewModel: ProfileViewModel = viewModel {
        ProfileViewModel(container.settingsRepository, container.weightRepository, container.profilePhotoStorage)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.settings_saved)
    val photoError = stringResource(R.string.profile_photo_error)
    val showErrors = viewModel.showErrors

    val context = LocalContext.current.applicationContext
    val photoStorage = container.photoStorage
    // System photo picker: no storage permission needed
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setPhoto({ context.contentResolver.openInputStream(uri) ?: throw IOException("No photo") })
    }
    // Temporary file for the camera; it survives if the system kills the app meanwhile
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingCameraPath ?: return@rememberLauncherForActivityResult
        pendingCameraPath = null
        if (success) viewModel.setPhoto({ File(path).inputStream() }, onDone = { photoStorage.delete(path) }) else photoStorage.delete(path)
    }
    LaunchedEffect(Unit) {
        for (error in viewModel.photoErrors) snackbarHostState.showSnackbar(photoError)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onBack)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (viewModel.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
            Text(stringResource(R.string.profile_section), style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(image = viewModel.photo, name = viewModel.name, size = 80.dp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 16.dp),
                ) {
                    TextButton(onClick = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(stringResource(R.string.profile_photo_choose)) }
                    TextButton(onClick = {
                        val file = photoStorage.newPhotoFile()
                        pendingCameraPath = file.absolutePath
                        try {
                            takePhoto.launch(photoStorage.uriFor(file))
                        } catch (_: ActivityNotFoundException) {
                            pendingCameraPath = null
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.today_no_camera)) }
                        }
                    }) { Text(stringResource(R.string.profile_photo_take)) }
                    if (viewModel.photo != null) {
                        TextButton(onClick = viewModel::removePhoto) { Text(stringResource(R.string.profile_photo_remove)) }
                    }
                }
            }
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.profile_name)) },
                supportingText = { Text(stringResource(R.string.profile_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.profile_sex), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sex.entries.forEach { sex ->
                    FilterChip(
                        selected = viewModel.sex == sex,
                        onClick = { viewModel.onSexChange(sex) },
                        label = { Text(stringResource(sex.labelRes)) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val range = viewModel.birthYearRange
                IntField(
                    value = viewModel.birthYear,
                    onValueChange = viewModel::onBirthYearChange,
                    label = stringResource(R.string.profile_birth_year),
                    isError = showErrors && !viewModel.birthYearValid,
                    error = stringResource(R.string.profile_range_error, range.first, range.last),
                    modifier = Modifier.weight(1f),
                )
                IntField(
                    value = viewModel.height,
                    onValueChange = viewModel::onHeightChange,
                    label = stringResource(R.string.profile_height_cm),
                    isError = showErrors && !viewModel.heightValid,
                    error = stringResource(
                        R.string.profile_range_error,
                        ProfileViewModel.HEIGHT_RANGE.first,
                        ProfileViewModel.HEIGHT_RANGE.last,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IntField(
                    value = viewModel.weight,
                    onValueChange = viewModel::onWeightChange,
                    label = stringResource(R.string.profile_weight_kg),
                    isError = showErrors && !viewModel.weightValid,
                    error = stringResource(R.string.error_invalid),
                    decimal = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = viewModel.latestWeight?.let { stringResource(R.string.profile_last_weigh_in, it.date.shortLabel()) }
                        ?: stringResource(R.string.profile_weight_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenWeightLog) { Text(stringResource(R.string.weight_log_title)) }
            }
            if (viewModel.isUnderAge) {
                Text(
                    stringResource(R.string.profile_under_age),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(stringResource(R.string.profile_activity), style = MaterialTheme.typography.labelLarge)
            Column(Modifier.selectableGroup()) {
                ActivityLevel.entries.forEach { level ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = viewModel.activity == level,
                                role = Role.RadioButton,
                                onClick = { viewModel.onActivityChange(level) },
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = viewModel.activity == level, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(stringResource(level.labelRes), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(level.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Text(stringResource(R.string.profile_goal), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightGoal.entries.forEach { goal ->
                    FilterChip(
                        selected = viewModel.goal == goal,
                        onClick = { viewModel.onGoalChange(goal) },
                        label = { Text(stringResource(goal.labelRes)) },
                    )
                }
            }

            EstimateCard(
                estimate = viewModel.estimate,
                waterMl = viewModel.waterSuggestionMl,
                waterGlasses = viewModel.waterSuggestionGlasses,
                onApply = viewModel::applySuggestion,
            )

            HorizontalDivider()

            Text(stringResource(R.string.profile_goals_section), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.profile_goals_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IntField(
                value = viewModel.kcal,
                onValueChange = viewModel::onKcalChange,
                label = stringResource(R.string.profile_kcal_goal),
                isError = showErrors && !viewModel.kcalValid,
                error = stringResource(
                    R.string.profile_range_error,
                    ProfileViewModel.KCAL_RANGE.first,
                    ProfileViewModel.KCAL_RANGE.last,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IntField(viewModel.protein, viewModel::onProteinChange, stringResource(R.string.entry_protein_g),
                    showErrors && !viewModel.proteinValid, stringResource(R.string.error_invalid), Modifier.weight(1f))
                IntField(viewModel.carbs, viewModel::onCarbsChange, stringResource(R.string.entry_carbs_g),
                    showErrors && !viewModel.carbsValid, stringResource(R.string.error_invalid), Modifier.weight(1f))
                IntField(viewModel.fat, viewModel::onFatChange, stringResource(R.string.entry_fat_g),
                    showErrors && !viewModel.fatValid, stringResource(R.string.error_invalid), Modifier.weight(1f))
            }
            MacroConsistency(kcalGoal = viewModel.kcal.toIntOrNull(), kcalFromMacros = viewModel.kcalFromMacros)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IntField(
                    value = viewModel.waterGlasses,
                    onValueChange = viewModel::onWaterGlassesChange,
                    label = stringResource(R.string.profile_water_goal),
                    isError = showErrors && !viewModel.waterValid,
                    error = stringResource(R.string.error_invalid),
                    modifier = Modifier.weight(1f),
                )
                IntField(
                    value = viewModel.glassSize,
                    onValueChange = viewModel::onGlassSizeChange,
                    label = stringResource(R.string.profile_glass_ml),
                    isError = showErrors && !viewModel.glassSizeValid,
                    error = stringResource(
                        R.string.profile_range_error,
                        ProfileViewModel.GLASS_ML_RANGE.first,
                        ProfileViewModel.GLASS_ML_RANGE.last,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            viewModel.waterGoalTypedMl?.let { ml ->
                Text(
                    stringResource(R.string.profile_water_goal_liters, formatLiters(ml)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = { scope.launch { if (viewModel.save()) snackbarHostState.showSnackbar(savedMessage) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }

            Text(
                stringResource(R.string.profile_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EstimateCard(estimate: EnergyEstimate?, waterMl: Int?, waterGlasses: Int?, onApply: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.profile_estimate_title), style = MaterialTheme.typography.titleSmall)
            if (estimate == null) {
                Text(stringResource(R.string.profile_estimate_missing), style = MaterialTheme.typography.bodyMedium)
            } else {
                EnergyRows(estimate)
            }
            if (waterMl != null) {
                val glasses = waterGlasses?.let { pluralStringResource(R.plurals.profile_water_glasses, it, it) }
                EstimateRow(
                    stringResource(R.string.profile_water_suggested),
                    listOfNotNull(formatLiters(waterMl), glasses).joinToString(" · "),
                    bold = true,
                )
                Text(
                    stringResource(R.string.profile_water_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (estimate != null || waterMl != null) {
                FilledTonalButton(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.profile_use_suggestion))
                }
            }
        }
    }
}

/** Energy part of the estimate; emitted into the card column. */
@Composable
private fun EnergyRows(estimate: EnergyEstimate) {
    EstimateRow(stringResource(R.string.profile_bmr), "${estimate.bmr} kcal")
    EstimateRow(stringResource(R.string.profile_tdee), "${estimate.tdee} kcal")
    EstimateRow(stringResource(R.string.profile_suggested), "${estimate.suggestedKcal} kcal", bold = true)
    Text(
        listOf(
            stringResource(R.string.macro_protein_g, estimate.proteinG.toString()),
            stringResource(R.string.macro_carbs_g, estimate.carbsG.toString()),
            stringResource(R.string.macro_fat_g, estimate.fatG.toString()),
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (estimate.limited) {
        Text(
            stringResource(R.string.profile_estimate_limited),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun EstimateRow(label: String, value: String, bold: Boolean = false) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** Shows the kcal of the typed macros; warns when they are far (>10%) from the kcal goal. */
@Composable
private fun MacroConsistency(kcalGoal: Int?, kcalFromMacros: Int?) {
    if (kcalGoal == null || kcalFromMacros == null) return
    val far = abs(kcalFromMacros - kcalGoal) > kcalGoal * 0.1
    Text(
        stringResource(if (far) R.string.profile_macros_kcal_far else R.string.profile_macros_kcal, kcalFromMacros),
        style = MaterialTheme.typography.bodySmall,
        color = if (far) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun IntField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    error: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1) },
        isError = isError,
        supportingText = errorText(isError, error),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier,
    )
}
