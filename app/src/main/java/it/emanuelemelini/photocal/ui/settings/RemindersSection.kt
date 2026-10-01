package it.emanuelemelini.photocal.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.reminders.ReminderConfig
import it.emanuelemelini.photocal.data.reminders.ReminderType
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Reminder switches and times, then the PhotoCal news (push); asks for the notification
 * permission when one of them is turned on.
 */
@Composable
fun RemindersSection(
    reminders: Map<ReminderType, ReminderConfig>,
    onEnabledChange: (ReminderType, Boolean) -> Unit,
    onTimeChange: (ReminderType, LocalTime) -> Unit,
    newsEnabled: Boolean,
    onNewsChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var editing by remember { mutableStateOf<ReminderConfig?>(null) }

    // Re-checked when coming back from the system settings
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = notificationsAllowed(context)
        onPauseOrDispose {}
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = notificationsAllowed(context)
    }

    Text(stringResource(R.string.settings_reminders), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.settings_reminders_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (!notificationsAllowed && (newsEnabled || reminders.values.any { it.enabled })) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_notifications_off),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { openNotificationSettings(context) }) {
                Text(stringResource(R.string.settings_notifications_enable))
            }
        }
    }

    ReminderType.entries.forEach { type ->
        val config = reminders.getValue(type)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(type.labelRes), style = MaterialTheme.typography.bodyLarge)
                type.hintRes?.let { hint ->
                    Text(
                        stringResource(hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = { editing = config }, enabled = config.enabled) {
                Text(config.time.format(timeFormatter()))
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = { enabled ->
                    onEnabledChange(type, enabled)
                    if (enabled && needsNotificationPermission(context)) permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
        }
    }

    // Not a reminder: sent from Firebase when there is something new, at no set time
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_news), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_news_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = newsEnabled,
            onCheckedChange = { enabled ->
                onNewsChange(enabled)
                if (enabled && needsNotificationPermission(context)) permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )
    }

    editing?.let { config ->
        TimePickerDialog(
            initial = config.time,
            onConfirm = { time ->
                onTimeChange(config.type, time)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reminder_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun timeFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(AppLocale.current)

/** Notification permission still to be asked (Android 13+). */
fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

private fun notificationsAllowed(context: Context): Boolean =
    !needsNotificationPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )
}
