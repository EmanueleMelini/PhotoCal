package it.emanuelemelini.photocal.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.ui.privacy.PRIVACY_POLICY_URL

/** Usage statistics and crash reports (Firebase), as answered at the first start. */
@Composable
fun PrivacySection(
    usageStats: Boolean,
    crashReports: Boolean,
    onUsageStatsChange: (Boolean) -> Unit,
    onCrashReportsChange: (Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Text(
        stringResource(R.string.privacy_never_sent),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PrivacySwitch(R.string.privacy_usage_stats, R.string.privacy_usage_stats_hint, usageStats, onUsageStatsChange)
    PrivacySwitch(R.string.privacy_crash_reports, R.string.privacy_crash_reports_hint, crashReports, onCrashReportsChange)
    TextButton(onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }) { Text(stringResource(R.string.privacy_policy)) }
}

@Composable
private fun PrivacySwitch(titleRes: Int, hintRes: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
