package it.emanuelemelini.photocal.ui.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.BuildConfig
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.update.Release
import it.emanuelemelini.photocal.data.update.UpdateError
import it.emanuelemelini.photocal.data.update.UpdateState
import it.emanuelemelini.photocal.ui.appContainer
import kotlin.math.roundToInt

private const val TAG = "UpdateDialogs"

/**
 * Over every screen: first the changelog of a just-installed version, then the update found
 * on GitHub with its download, permission and error dialogs.
 */
@Composable
fun UpdateDialogs() {
    val container = appContainer()
    val viewModel: UpdateViewModel = viewModel { UpdateViewModel(container.appUpdater, container.whatsNew) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // Back from the "Install unknown apps" settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    if (viewModel.changelog.isNotEmpty()) {
        ChangelogDialog(viewModel.changelog, onDismiss = viewModel::dismissChangelog)
        return
    }

    fun openInBrowser(release: Release) {
        viewModel.dismiss()
        uriHandler.openUri(release.apkUrl)
    }

    when (val current = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                // The notes of the new version, from GitHub, to show why it's worth updating
                val notes = current.release.notesFor(AppLocale.language.tag)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text(stringResource(R.string.update_available_text, current.release.version.toString(), BuildConfig.VERSION_NAME))
                    if (notes.isNotEmpty()) {
                        Text(stringResource(R.string.update_available_whats_new), style = MaterialTheme.typography.titleSmall)
                        notes.forEach { NoteRow(it) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.install(current.release) }) { Text(stringResource(R.string.update_action_install)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.update_action_later)) }
            },
        )

        is UpdateState.NeedsPermission -> if (current.denied) {
            AlertDialog(
                onDismissRequest = viewModel::dismiss,
                title = { Text(stringResource(R.string.update_permission_title)) },
                text = { Text(stringResource(R.string.update_permission_denied)) },
                confirmButton = {
                    TextButton(onClick = { openInBrowser(current.release) }) { Text(stringResource(R.string.update_action_browser)) }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.action_close)) }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = viewModel::dismiss,
                title = { Text(stringResource(R.string.update_permission_title)) },
                text = { Text(stringResource(R.string.update_permission_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onPermissionRequested(current.release)
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            // No such screen on this device: same as a refusal
                            Log.w(TAG, "No unknown sources settings", e)
                            viewModel.onResume()
                        }
                    }) { Text(stringResource(R.string.update_action_allow)) }
                },
                dismissButton = {
                    TextButton(onClick = { openInBrowser(current.release) }) { Text(stringResource(R.string.update_action_browser)) }
                },
            )
        }

        is UpdateState.Downloading -> AlertDialog(
            // Only the Cancel button stops the download
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.update_downloading_title, current.release.version.toString())) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val progress = current.progress
                    if (progress == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.update_downloading_progress, (progress * 100).roundToInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::cancelDownload) { Text(stringResource(R.string.action_cancel)) }
            },
        )

        // The system asks to confirm the install; launched from here, while the app is visible
        is UpdateState.ConfirmInstall -> LaunchedEffect(current) {
            try {
                context.startActivity(current.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No installer to confirm the update", e)
            }
            viewModel.onConfirmLaunched()
        }

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(R.string.update_failed_title)) },
            text = {
                Text(
                    stringResource(
                        when (current.error) {
                            UpdateError.DOWNLOAD -> R.string.update_error_download
                            UpdateError.SIGNATURE -> R.string.update_error_signature
                            UpdateError.STORAGE -> R.string.update_error_storage
                            UpdateError.INSTALL -> R.string.update_error_install
                        }
                    )
                )
            },
            confirmButton = {
                // A differently signed APK can't be installed from the browser either
                if (current.error == UpdateError.SIGNATURE) {
                    TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.action_close)) }
                } else {
                    TextButton(onClick = { openInBrowser(current.release) }) { Text(stringResource(R.string.update_action_browser)) }
                }
            },
            dismissButton = if (current.error == UpdateError.SIGNATURE) null else {
                { TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.action_close)) } }
            },
        )

        // Idle, or the result of a manual check, shown in Settings
        else -> Unit
    }
}
