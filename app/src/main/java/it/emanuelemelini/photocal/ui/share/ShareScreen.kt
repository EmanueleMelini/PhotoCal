package it.emanuelemelini.photocal.ui.share

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.shortLabel

/** Chooses what to show of a day (or of the week ending on it) and shares the link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(onBack: () -> Unit, onOpenProfile: () -> Unit) {
    val container = appContainer()
    val viewModel: ShareViewModel = viewModel {
        ShareViewModel(createSavedStateHandle(), container.shareBuilder, container.profilePhotoStorage)
    }
    val context = LocalContext.current
    val options = viewModel.options
    val card = viewModel.card
    val date = viewModel.date
    val shareText = if (options.week) {
        stringResource(R.string.share_text_week, date.minusDays(6).shortLabel(), date.shortLabel())
    } else {
        stringResource(R.string.share_text_day, date.shortLabel())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_title)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onBack)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    enabled = card != null && options.hasContent && card.name.isNotBlank(),
                    onClick = {
                        val link = viewModel.link() ?: return@Button
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "$shareText\n$link")
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text(stringResource(R.string.share_action), Modifier.padding(start = 8.dp))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Possible from a pinned "share" shortcut after the name was removed
            if (card != null && card.name.isBlank()) {
                Text(
                    stringResource(R.string.share_needs_name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onOpenProfile) { Text(stringResource(R.string.profile_title)) }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false, true).forEachIndexed { index, week ->
                    SegmentedButton(
                        selected = options.week == week,
                        onClick = { viewModel.update { it.copy(week = week) } },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(stringResource(if (week) R.string.share_period_week else R.string.share_period_day)) }
                }
            }

            Text(
                stringResource(R.string.share_what),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            if (viewModel.hasPhoto) {
                Toggle(stringResource(R.string.share_photo), options.photo) { on -> viewModel.update { it.copy(photo = on) } }
            }
            Toggle(stringResource(R.string.share_goals), options.goals) { on -> viewModel.update { it.copy(goals = on) } }
            Toggle(stringResource(R.string.shared_calories), options.kcal) { on -> viewModel.update { it.copy(kcal = on) } }
            Toggle(stringResource(R.string.shared_macros), options.macros) { on -> viewModel.update { it.copy(macros = on) } }
            Toggle(stringResource(R.string.water_title), options.water) { on -> viewModel.update { it.copy(water = on) } }
            if (!options.week) {
                Toggle(stringResource(R.string.shared_meals), options.meals) { on -> viewModel.update { it.copy(meals = on) } }
            }
            Toggle(stringResource(R.string.shared_weight), options.weight) { on -> viewModel.update { it.copy(weight = on) } }
            if (!options.hasContent) {
                Text(
                    stringResource(R.string.share_nothing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(stringResource(R.string.share_preview), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.share_preview_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            card?.let { SharedCardView(it, viewModel.avatar) }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
