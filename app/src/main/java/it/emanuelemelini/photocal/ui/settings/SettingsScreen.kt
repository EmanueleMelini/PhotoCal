package it.emanuelemelini.photocal.ui.settings

import it.emanuelemelini.photocal.data.prefs.Settings
import androidx.compose.material3.ListItem
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.clickable
import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.emanuelemelini.photocal.data.prefs.ThemeMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.photocal.AppLanguage
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.BuildConfig
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.crea.CreaTable
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import it.emanuelemelini.photocal.data.update.Changelog
import it.emanuelemelini.photocal.data.update.UpdateState
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.update.ChangelogDialog
import kotlinx.coroutines.launch

private const val AI_STUDIO_URL = "https://aistudio.google.com/apikey"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenHealthPrivacy: () -> Unit,
) {
    val container = appContainer()
    val viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(container.settingsRepository, container.geminiClient, container.reminderScheduler, container.appUpdater)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var showApiKey by rememberSaveable { mutableStateOf(false) }
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val savedMessage = stringResource(R.string.settings_saved)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onBack)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.settings_goal_section), style = MaterialTheme.typography.titleMedium)
            // Goals are set in their own screen, with the profile-based suggestion
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_title)) },
                supportingContent = { Text(goalsSummary(appearance)) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable(onClick = onOpenProfile),
            )

            HorizontalDivider()

            Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)

            // null = follow the device language; changing it recreates the activity
            val chosenLanguage = remember { AppLocale.chosen() }
            val languageOptions = listOf(null) + AppLanguage.entries
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                languageOptions.forEachIndexed { index, language ->
                    SegmentedButton(
                        selected = chosenLanguage == language,
                        onClick = { if (chosenLanguage != language) AppLocale.choose(language) },
                        shape = SegmentedButtonDefaults.itemShape(index, languageOptions.size),
                    ) {
                        // Each language is written in itself, as is customary
                        Text(
                            when (language) {
                                null -> stringResource(R.string.settings_language_system)
                                AppLanguage.ITALIAN -> "Italiano"
                                AppLanguage.ENGLISH -> "English"
                            }
                        )
                    }
                }
            }

            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelLarge)
            // Chips instead of a segmented row: five options don't fit a phone width
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = appearance.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(stringResource(mode.labelRes)) },
                    )
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // The purple theme always uses its own palette
                val dynamicAvailable = !appearance.themeMode.isPurple
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_dynamic_colors), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(
                                if (dynamicAvailable) R.string.settings_dynamic_colors_hint
                                else R.string.settings_dynamic_colors_purple
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = appearance.dynamicColor && dynamicAvailable,
                        onCheckedChange = viewModel::setDynamicColor,
                        enabled = dynamicAvailable,
                    )
                }
            }

            AppIconPicker()

            HorizontalDivider()

            RemindersSection(
                reminders = appearance.reminders,
                onEnabledChange = viewModel::setReminderEnabled,
                onTimeChange = viewModel::setReminderTime,
            )

            HorizontalDivider()

            Text("Gemini", style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_gemini_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = viewModel.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                label = { Text(stringResource(R.string.settings_api_key)) },
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showApiKey = !showApiKey }) {
                        Text(stringResource(if (showApiKey) R.string.settings_hide else R.string.settings_show))
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { uriHandler.openUri(AI_STUDIO_URL) }) { Text(stringResource(R.string.settings_get_api_key)) }

            OutlinedTextField(
                value = viewModel.model,
                onValueChange = viewModel::onModelChange,
                label = { Text(stringResource(R.string.settings_model)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsRepository.SUGGESTED_GEMINI_MODELS.forEach { suggestion ->
                    FilterChip(
                        selected = viewModel.model == suggestion,
                        onClick = { viewModel.onModelChange(suggestion) },
                        label = { Text(suggestion) },
                    )
                }
            }

            OutlinedButton(
                onClick = viewModel::testConnection,
                enabled = viewModel.connectionTest != ConnectionTest.Running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (viewModel.connectionTest == ConnectionTest.Running) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Text(stringResource(R.string.settings_test_connection))
            }
            when (val test = viewModel.connectionTest) {
                is ConnectionTest.Success -> Text(
                    test.message.asString(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is ConnectionTest.Failure -> Text(
                    test.message.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> Unit
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_use_crea), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_use_crea_hint, CreaTable.SOURCE),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = appearance.useCrea, onCheckedChange = viewModel::setUseCrea)
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        viewModel.save()
                        snackbarHostState.showSnackbar(savedMessage)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }

            HorizontalDivider()

            HealthSection(onOpenPrivacy = onOpenHealthPrivacy)

            HorizontalDivider()

            DataSection(onMessage = { text -> scope.launch { snackbarHostState.showSnackbar(text) } })

            HorizontalDivider()

            AboutSection(
                updateState = updateState,
                onCheckForUpdates = viewModel::checkForUpdates,
            )
        }
    }
}

/** Installed version, changelog and manual update check. */
@Composable
private fun AboutSection(updateState: UpdateState, onCheckForUpdates: () -> Unit) {
    var showChangelog by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
        supportingContent = { Text(stringResource(R.string.settings_whats_new)) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        modifier = Modifier.clickable { showChangelog = true },
    )
    val checking = updateState == UpdateState.Checking
    OutlinedButton(
        onClick = onCheckForUpdates,
        enabled = !checking,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (checking) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(stringResource(R.string.settings_check_updates))
    }
    when (updateState) {
        UpdateState.UpToDate -> Text(
            stringResource(R.string.settings_up_to_date),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
        UpdateState.CheckFailed -> Text(
            stringResource(R.string.settings_check_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        else -> Unit
    }
    if (showChangelog) {
        // Every version installed so far, newest first
        val entries = remember { Changelog.entries.filter { it.versionCode <= BuildConfig.VERSION_CODE } }
        ChangelogDialog(entries, onDismiss = { showChangelog = false })
    }
}

/** e.g. "1850 kcal · P 130 g · C 210 g · G 55 g". */
@Composable
private fun goalsSummary(settings: Settings): String = listOfNotNull(
    "${settings.dailyKcalGoal} kcal",
    settings.proteinGoalG?.let { stringResource(R.string.macro_short_protein, "$it g") },
    settings.carbsGoalG?.let { stringResource(R.string.macro_short_carbs, "$it g") },
    settings.fatGoalG?.let { stringResource(R.string.macro_short_fat, "$it g") },
).joinToString(" · ")
