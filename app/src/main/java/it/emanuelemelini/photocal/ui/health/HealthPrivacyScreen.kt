package it.emanuelemelini.photocal.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import it.emanuelemelini.photocal.R

/**
 * What PhotoCal reads from and writes to Health Connect. Health Connect opens it from the
 * permission screen ("privacy policy" link), as it requires.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthPrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.health_privacy_title)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onBack)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            listOf(R.string.health_privacy_read, R.string.health_privacy_write, R.string.health_privacy_storage).forEach {
                Text(stringResource(it), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
