package it.emanuelemelini.photocal.ui.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.health.HealthAvailability
import it.emanuelemelini.photocal.ui.appContainer

/** Health Connect: link, weight and water writing, burned calories added to the goal. */
@Composable
fun HealthSection(onOpenPrivacy: () -> Unit) {
    val container = appContainer()
    val viewModel: HealthViewModel = viewModel { HealthViewModel(container.healthConnect, container.settingsRepository) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(viewModel.healthConnect.permissionContract()) { granted ->
        viewModel.onPermissionsResult(granted)
    }

    // The user may change the permissions from Health Connect itself
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Text(stringResource(R.string.health_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.health_intro),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    when (viewModel.availability) {
        HealthAvailability.NOT_SUPPORTED -> Text(stringResource(R.string.health_not_supported), style = MaterialTheme.typography.bodyMedium)
        HealthAvailability.NEEDS_INSTALL -> OutlinedButton(onClick = {
            try {
                context.startActivity(viewModel.healthConnect.installIntent())
            } catch (_: ActivityNotFoundException) {
                // No Play Store: nothing to open
            }
        }) { Text(stringResource(R.string.health_install)) }
        HealthAvailability.AVAILABLE -> {
            val connected = settings.healthConnected && viewModel.granted.isNotEmpty()
            if (connected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.health_connected),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::disconnect) { Text(stringResource(R.string.health_disconnect)) }
                }
                if (viewModel.granted.size < viewModel.healthConnect.permissions.size) {
                    Text(
                        stringResource(R.string.health_partial),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { permissionLauncher.launch(viewModel.healthConnect.permissions) }) {
                        Text(stringResource(R.string.health_permissions))
                    }
                }
                SwitchRow(R.string.health_write, R.string.health_write_hint, settings.healthWrite, viewModel::setWrite)
                SwitchRow(R.string.health_add_burned, R.string.health_add_burned_hint, settings.healthAddBurned, viewModel::setAddBurned)
            } else {
                OutlinedButton(onClick = { permissionLauncher.launch(viewModel.healthConnect.permissions) }) {
                    Text(stringResource(R.string.health_connect))
                }
            }
            TextButton(onClick = onOpenPrivacy) { Text(stringResource(R.string.health_privacy_title)) }
        }
    }
}

@Composable
private fun SwitchRow(titleRes: Int, hintRes: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
