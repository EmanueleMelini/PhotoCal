package it.emanuelemelini.photocal.ui.privacy

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import it.emanuelemelini.photocal.BuildConfig
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.settings.needsNotificationPermission
import kotlinx.coroutines.launch

/** Privacy policy on the site: what is sent to Firebase and what never is. */
val PRIVACY_POLICY_URL = "https://${BuildConfig.SHARE_HOST}/privacy"

/**
 * First-start question about usage statistics and crash reports, both off until the user
 * agrees; then, on Android 13+, the notification permission for reminders and news.
 * Composed all the time (not only while [pending]), so the permission request outlives the dialog.
 */
@Composable
fun PrivacyConsent(pending: Boolean, newsNotifications: Boolean) {
    val container = appContainer()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    // The answer doesn't matter here: Settings shows when notifications are off
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    if (!pending) return
    val askNotifications = newsNotifications && needsNotificationPermission(context)

    fun answer(granted: Boolean) {
        // Application scope: the dialog goes away as soon as the choice is saved
        container.applicationScope.launch { container.settingsRepository.setPrivacyConsent(granted) }
        if (askNotifications) permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    AlertDialog(
        // An explicit answer is needed: no choice by tapping outside
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.privacy_consent_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.privacy_consent_text))
                Text(stringResource(R.string.privacy_never_sent))
                Text(stringResource(R.string.privacy_consent_change), style = MaterialTheme.typography.bodySmall)
                if (askNotifications) {
                    Text(stringResource(R.string.privacy_consent_notifications), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }) {
                    Text(stringResource(R.string.privacy_policy))
                }
            }
        },
        // Same weight for both answers
        confirmButton = {
            TextButton(onClick = { answer(true) }) { Text(stringResource(R.string.privacy_consent_allow)) }
        },
        dismissButton = {
            TextButton(onClick = { answer(false) }) { Text(stringResource(R.string.privacy_consent_deny)) }
        },
    )
}
