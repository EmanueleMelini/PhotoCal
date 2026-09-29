package it.emanuelemelini.photocal.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.update.ChangelogEntry

/** Release notes, newest first; with several versions each one has its own heading. */
@Composable
fun ChangelogDialog(entries: List<ChangelogEntry>, onDismiss: () -> Unit) {
    val single = entries.singleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (single != null) stringResource(R.string.changelog_title_version, single.versionName)
                else stringResource(R.string.changelog_title)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                entries.forEach { entry ->
                    if (single == null) {
                        Text(
                            stringResource(R.string.changelog_version, entry.versionName),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    stringArrayResource(entry.notesRes).forEach { NoteRow(it) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}

/** One bullet point of release notes. */
@Composable
internal fun NoteRow(note: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Text(note, style = MaterialTheme.typography.bodyMedium)
    }
}
