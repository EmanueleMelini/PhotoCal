package it.emanuelemelini.photocal.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.ui.formatKcal

/**
 * Save button pinned to the bottom of a form, for the bottomBar of a Scaffold: always in
 * reach, and above the keyboard when it is open. With [kcal] it also shows the total.
 *
 * The Scaffold content should use `padding(padding).consumeWindowInsets(padding)` before
 * imePadding, or the keyboard height is counted twice.
 */
@Composable
fun SaveBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kcal: Double? = null,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, modifier = modifier.fillMaxWidth()) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
        ) {
            Text(
                if (kcal != null) stringResource(R.string.action_save_kcal, kcal.formatKcal())
                else stringResource(R.string.action_save)
            )
        }
    }
}
