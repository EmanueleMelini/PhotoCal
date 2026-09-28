package it.emanuelemelini.photocal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.data.db.ServingUnit
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.parseDecimal

/** Decimal number field: accepts only digits, comma and dot. */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    error: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label, maxLines = 1) },
        isError = isError,
        supportingText = errorText(isError, error) ?: helper?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = modifier,
    )
}

/** Quantity + unit (grams, ml, cups, glasses...), with the ml conversion below. */
@Composable
fun QuantityRow(
    quantity: String,
    onQuantityChange: (String) -> Unit,
    unit: ServingUnit,
    onUnitChange: (ServingUnit) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val ml = parseDecimal(quantity)?.times(unit.gramsPerUnit)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        NumberField(
            value = quantity,
            onValueChange = onQuantityChange,
            label = stringResource(R.string.label_quantity),
            isError = isError,
            error = stringResource(R.string.error_required_f),
            helper = ml
                ?.takeIf { unit != ServingUnit.GRAMS && unit != ServingUnit.MILLILITERS }
                ?.let { stringResource(R.string.quantity_ml_equivalent, it.formatAmount()) },
            modifier = Modifier.weight(1f),
        )
        UnitSelector(
            selected = unit,
            onSelect = onUnitChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitSelector(
    selected: ServingUnit,
    onSelect: (ServingUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = unitMenuLabel(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.label_unit)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ServingUnit.entries.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unitMenuLabel(unit)) },
                    onClick = {
                        onSelect(unit)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealSelector(
    selected: MealType,
    onSelect: (MealType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        Text(stringResource(R.string.label_meal), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealType.entries.forEach { meal ->
                FilterChip(
                    selected = selected == meal,
                    onClick = { onSelect(meal) },
                    label = { Text(stringResource(meal.labelRes)) },
                )
            }
        }
    }
}

/** Picker label of a unit, e.g. "calice (150 ml)". */
@Composable
private fun unitMenuLabel(unit: ServingUnit): String = stringResource(unit.menuRes, unit.gramsPerUnit.toInt())

fun errorText(show: Boolean, message: String): (@Composable () -> Unit)? =
    if (show) {
        { Text(message) }
    } else null
