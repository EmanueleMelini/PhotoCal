package it.emanuelemelini.photocal.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.MealType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Entries to copy: [meal] null for a whole day, whose entries keep their meals. */
data class CopyRequest(val entries: List<FoodEntry>, val from: LocalDate, val meal: MealType?, val titleRes: Int)

/** Day (and meal) to copy to: tomorrow when copying from today, otherwise today. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CopyDialog(
    request: CopyRequest,
    onDismiss: () -> Unit,
    onCopy: (date: LocalDate, meal: MealType?) -> Unit,
) {
    val today = LocalDate.now()
    val initial = if (request.from == today) today.plusDays(1) else today
    // The DatePicker works with UTC milliseconds at midnight
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    var meal by remember { mutableStateOf(request.meal) }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onCopy(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(), meal)
                    }
                },
                enabled = pickerState.selectedDateMillis != null,
            ) { Text(stringResource(R.string.copy_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            DatePicker(
                state = pickerState,
                title = {
                    Text(
                        stringResource(request.titleRes),
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    )
                },
            )
            // A whole day keeps the meal of each entry
            if (request.meal != null) {
                Text(
                    stringResource(R.string.label_meal),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                ) {
                    MealType.entries.forEach { type ->
                        FilterChip(
                            selected = meal == type,
                            onClick = { meal = type },
                            label = { Text(stringResource(type.labelRes)) },
                        )
                    }
                }
            }
        }
    }
}
