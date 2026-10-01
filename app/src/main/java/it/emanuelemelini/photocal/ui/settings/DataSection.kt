package it.emanuelemelini.photocal.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.backup.BackupManager
import it.emanuelemelini.photocal.data.backup.RestoreMode
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.shortLabel

/** Backup, restore and CSV export, through the system file picker (no permissions needed). */
@Composable
fun DataSection(onMessage: (String) -> Unit) {
    val container = appContainer()
    val viewModel: DataViewModel = viewModel { DataViewModel(container.backupManager) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON)) { uri ->
        uri?.let(viewModel::export)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV)) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::open)
    }

    viewModel.message?.let { message ->
        val text = message.asString()
        LaunchedEffect(message) {
            viewModel.onMessageShown()
            onMessage(text)
        }
    }

    Text(stringResource(R.string.settings_data), style = MaterialTheme.typography.titleMedium)
    val enabled = !viewModel.busy
    DataRow(R.string.data_export, R.string.data_export_hint, enabled) { exportLauncher.launch(BackupManager.backupFileName()) }
    // Some file managers don't know the JSON type: other types are accepted and checked on reading
    DataRow(R.string.data_restore, R.string.data_restore_hint, enabled) { openLauncher.launch(arrayOf(JSON, "application/octet-stream", "text/*")) }
    DataRow(R.string.data_csv, R.string.data_csv_hint, enabled) { csvLauncher.launch(BackupManager.csvFileName()) }

    viewModel.pendingRestore?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRestore,
            title = { Text(stringResource(R.string.data_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val first = pending.firstDay
                    val last = pending.lastDay
                    Text(
                        if (first != null && last != null) {
                            pluralStringResource(
                                R.plurals.data_restore_summary, pending.entries,
                                pending.entries, pending.exportedOn.shortLabel(), first.shortLabel(), last.shortLabel(),
                            )
                        } else {
                            stringResource(R.string.data_restore_empty, pending.exportedOn.shortLabel())
                        }
                    )
                    Text(stringResource(R.string.data_restore_explain), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.restore(RestoreMode.REPLACE) }) {
                    Text(stringResource(R.string.data_restore_replace), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::dismissRestore) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = { viewModel.restore(RestoreMode.MERGE) }) { Text(stringResource(R.string.data_restore_merge)) }
                }
            },
        )
    }
}

@Composable
private fun DataRow(titleRes: Int, hintRes: Int, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(titleRes)) },
        supportingContent = { Text(stringResource(hintRes)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

private const val JSON = "application/json"
private const val CSV = "text/csv"
