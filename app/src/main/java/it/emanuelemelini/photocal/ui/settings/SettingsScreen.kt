package it.emanuelemelini.photocal.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.photocal.AppLanguage
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.BuildConfig
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.prefs.Settings
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.components.staggeredEntrance

/** A page of Settings; the constant names are part of the navigation route. */
enum class SettingsPage(@StringRes val titleRes: Int) {
    APPEARANCE(R.string.settings_appearance),
    REMINDERS(R.string.settings_reminders),
    AI(R.string.settings_ai_title),
    HEALTH(R.string.health_title),
    DATA(R.string.settings_data),
    PRIVACY(R.string.privacy_title),
    ABOUT(R.string.settings_about),
}

/**
 * Settings menu: one row per page with what is set now, e.g. "Tema: Scuro · Lingua: Italiano". The rows
 * enter one after the other the first time the menu is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
) {
    val container = appContainer()
    val viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(
            container.settingsRepository,
            container.aiService,
            container.reminderScheduler,
            container.appUpdater,
            container.newsTopics,
        )
    }
    val settings by viewModel.appearance.collectAsStateWithLifecycle()

    // Only on the way in: coming back from a page the menu is already there
    val firstVisit = rememberSaveable { mutableStateOf(true) }
    val animateEntrance = remember { firstVisit.value }
    LaunchedEffect(Unit) { firstVisit.value = false }

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
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            val rows = listOf(
                MenuRow(rememberVectorPainter(Icons.Default.Person), stringResource(R.string.profile_title), profileSummary(settings), onOpenProfile),
                pageRow(painterResource(R.drawable.ic_palette), SettingsPage.APPEARANCE, appearanceSummary(settings), onOpenPage),
                pageRow(rememberVectorPainter(Icons.Default.Notifications), SettingsPage.REMINDERS, remindersSummary(settings), onOpenPage),
                pageRow(painterResource(R.drawable.ic_auto_awesome), SettingsPage.AI, aiSummary(settings), onOpenPage),
                pageRow(rememberVectorPainter(Icons.Default.Favorite), SettingsPage.HEALTH, healthSummary(settings), onOpenPage),
                pageRow(painterResource(R.drawable.ic_save), SettingsPage.DATA, stringResource(R.string.settings_summary_data), onOpenPage),
                pageRow(rememberVectorPainter(Icons.Default.Lock), SettingsPage.PRIVACY, stringResource(R.string.settings_summary_privacy), onOpenPage),
                pageRow(
                    rememberVectorPainter(Icons.Default.Info), SettingsPage.ABOUT,
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), onOpenPage,
                ),
            )
            rows.forEachIndexed { index, row ->
                ListItem(
                    headlineContent = { Text(row.title) },
                    supportingContent = { Text(row.summary, maxLines = 2) },
                    leadingContent = { Icon(row.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier
                        .then(if (animateEntrance) Modifier.staggeredEntrance(index) else Modifier)
                        .clickable(onClick = dropUnlessResumed(block = row.onClick)),
                )
            }
        }
    }
}

private class MenuRow(val icon: Painter, val title: String, val summary: String, val onClick: () -> Unit)

@Composable
private fun pageRow(icon: Painter, page: SettingsPage, summary: String, onOpenPage: (SettingsPage) -> Unit) =
    MenuRow(icon, stringResource(page.titleRes), summary) { onOpenPage(page) }

/** e.g. "1850 kcal · P 130 g · C 210 g · G 55 g · Borraccia 750 ml". */
@Composable
private fun profileSummary(settings: Settings): String = listOfNotNull(
    "${settings.dailyKcalGoal} kcal",
    settings.proteinGoalG?.let { stringResource(R.string.macro_short_protein, "$it g") },
    settings.carbsGoalG?.let { stringResource(R.string.macro_short_carbs, "$it g") },
    settings.fatGoalG?.let { stringResource(R.string.macro_short_fat, "$it g") },
    settings.bottle?.let { stringResource(R.string.settings_summary_bottle, it.ml) },
).joinToString(" · ")

/** e.g. "Tema: Scuro · Lingua: Italiano". */
@Composable
private fun appearanceSummary(settings: Settings): String {
    val language = when (remember { AppLocale.chosen() }) {
        null -> stringResource(R.string.settings_language_system)
        AppLanguage.ITALIAN -> "Italiano"
        AppLanguage.ENGLISH -> "English"
    }
    return stringResource(R.string.settings_summary_appearance, stringResource(settings.themeMode.labelRes), language)
}

@Composable
private fun remindersSummary(settings: Settings): String {
    val enabled = settings.reminders.values.count { it.enabled }
    return if (enabled == 0) stringResource(R.string.settings_summary_reminders_none)
    else pluralStringResource(R.plurals.settings_summary_reminders, enabled, enabled)
}

/** e.g. "Gemini · gemini-3.8-flash", or what is still missing. */
@Composable
private fun aiSummary(settings: Settings): String {
    val provider = stringResource(settings.aiProvider.labelRes)
    return if (settings.aiConfigured) "$provider · ${settings.aiModel(settings.aiProvider)}"
    else stringResource(R.string.settings_summary_ai_missing, provider)
}

@Composable
private fun healthSummary(settings: Settings): String =
    stringResource(if (settings.healthConnected) R.string.health_connected else R.string.settings_summary_health_off)
