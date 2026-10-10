package it.emanuelemelini.photocal.ui.barcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.google.mlkit.common.MlKitException
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.db.ServingUnit
import it.emanuelemelini.photocal.data.openfoodfacts.Product
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.components.MealSelector
import it.emanuelemelini.photocal.ui.components.QuantityRow
import it.emanuelemelini.photocal.ui.components.SaveBar
import it.emanuelemelini.photocal.ui.components.errorText
import it.emanuelemelini.photocal.ui.extrasLabel
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.formatKcal
import it.emanuelemelini.photocal.ui.pieceName
import it.emanuelemelini.photocal.ui.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(
    onDone: () -> Unit,
    onManualEntry: (prefillName: String?) -> Unit,
) {
    val container = appContainer()
    val viewModel: BarcodeViewModel = viewModel {
        BarcodeViewModel(createSavedStateHandle(), container.foodRepository, container.savedFoodRepository, container.openFoodFactsClient)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status = viewModel.status

    fun startScan() {
        scope.launch {
            try {
                scanBarcode(context)?.let(viewModel::lookup)
            } catch (e: CancellationException) {
                throw e
            } catch (e: MlKitException) {
                viewModel.onScanFailed(
                    if (e.errorCode == MlKitException.UNAVAILABLE) {
                        // The Play services module is downloaded on first use
                        uiText(R.string.barcode_scanner_downloading)
                    } else {
                        uiText(R.string.barcode_scanner_unavailable)
                    }
                )
            } catch (_: Exception) {
                viewModel.onScanFailed(uiText(R.string.barcode_scanner_unavailable))
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!viewModel.autoScanDone) {
            viewModel.autoScanDone = true
            startScan()
        }
    }
    LaunchedEffect(viewModel.isDone) {
        if (viewModel.isDone) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.barcode_title)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onDone)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            if (status is BarcodeStatus.Found) SaveBar(onClick = viewModel::save, kcal = viewModel.nutrition?.kcal)
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            when (status) {
                BarcodeStatus.Idle -> ScanPrompt(
                    manualCode = viewModel.manualCode,
                    onManualCodeChange = viewModel::onManualCodeChange,
                    onScan = ::startScan,
                    onSearch = { viewModel.lookup(viewModel.manualCode) },
                )

                is BarcodeStatus.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.barcode_searching, status.barcode))
                }

                is BarcodeStatus.NotFound -> MessageCard(
                    message = stringResource(R.string.barcode_not_found, status.barcode),
                    onScanAgain = ::startScan,
                    onManualEntry = { onManualEntry(null) },
                )

                is BarcodeStatus.Error -> MessageCard(
                    message = status.message.asString(),
                    onRetry = status.barcode?.let { code -> { viewModel.lookup(code) } },
                    onScanAgain = ::startScan,
                    onManualEntry = { onManualEntry(viewModel.name.ifBlank { null }) },
                )

                is BarcodeStatus.Found -> ProductForm(
                    product = status.product,
                    offline = status.offline,
                    viewModel = viewModel,
                    onScanAgain = ::startScan,
                )
            }

            // After an error or a product not found the code can also be typed
            if (status is BarcodeStatus.NotFound || status is BarcodeStatus.Error) {
                ManualCodeField(
                    manualCode = viewModel.manualCode,
                    onManualCodeChange = viewModel::onManualCodeChange,
                    onSearch = { viewModel.lookup(viewModel.manualCode) },
                )
            }
        }
    }
}

@Composable
private fun ScanPrompt(
    manualCode: String,
    onManualCodeChange: (String) -> Unit,
    onScan: () -> Unit,
    onSearch: () -> Unit,
) {
    Text(
        stringResource(R.string.barcode_intro),
        style = MaterialTheme.typography.bodyMedium,
    )
    Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
        Icon(painterResource(R.drawable.ic_barcode), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.barcode_scan))
    }
    ManualCodeField(manualCode, onManualCodeChange, onSearch)
}

@Composable
private fun ManualCodeField(
    manualCode: String,
    onManualCodeChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = manualCode,
            onValueChange = onManualCodeChange,
            label = { Text(stringResource(R.string.barcode_type_code)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (manualCode.length >= 8) onSearch() }),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onSearch, enabled = manualCode.length >= 8) { Text(stringResource(R.string.barcode_search)) }
    }
}

@Composable
private fun MessageCard(
    message: String,
    onScanAgain: () -> Unit,
    onManualEntry: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                onRetry?.let { TextButton(onClick = it) { Text(stringResource(R.string.action_retry)) } }
                TextButton(onClick = onScanAgain) { Text(stringResource(R.string.barcode_scan)) }
                TextButton(onClick = onManualEntry) { Text(stringResource(R.string.action_manual_entry)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductForm(
    product: Product,
    offline: Boolean,
    viewModel: BarcodeViewModel,
    onScanAgain: () -> Unit,
) {
    val unitLabel = if (product.isLiquid) "ml" else "g"

    if (offline) {
        Text(
            stringResource(R.string.barcode_offline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            product.imageUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.small),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                product.distinctBrand?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = stringResource(R.string.barcode_per_100, unitLabel, per100Summary(product)),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    OutlinedTextField(
        value = viewModel.name,
        onValueChange = viewModel::onNameChange,
        label = { Text(stringResource(R.string.barcode_diary_name)) },
        isError = viewModel.showErrors && !viewModel.nameValid,
        supportingText = errorText(viewModel.showErrors && !viewModel.nameValid, stringResource(R.string.entry_name_required)),
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )

    QuantityRow(
        quantity = viewModel.quantity,
        onQuantityChange = viewModel::onQuantityChange,
        unit = viewModel.unit,
        onUnitChange = viewModel::onUnitChange,
        isError = viewModel.showErrors && !viewModel.quantityValid,
        units = ServingUnit.entries,
        pieceGrams = viewModel.pieceGrams,
        onPieceGramsChange = viewModel::onPieceGramsChange,
        pieceGramsError = viewModel.showErrors && !viewModel.pieceGramsValid,
        pieceLabel = product.servingPieces?.label,
    )

    val pieces = product.servingPieces
    if (product.servingQuantity != null || product.packageQuantity != null || pieces != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            product.servingQuantity?.let { serving ->
                val label = if (pieces != null) {
                    val count = serving / pieces.pieceGrams
                    stringResource(
                        R.string.barcode_one_serving_pieces,
                        "${count.formatAmount()} ${pieceName(count, pieces.label)}",
                        serving.formatAmount(),
                        unitLabel,
                    )
                } else {
                    stringResource(R.string.barcode_one_serving, serving.formatAmount(), unitLabel)
                }
                AssistChip(
                    onClick = { viewModel.useAmount(serving, product.isLiquid) },
                    label = { Text(label) },
                )
            }
            pieces?.let {
                AssistChip(
                    onClick = { viewModel.useOnePiece(it.pieceGrams) },
                    label = { Text(stringResource(R.string.barcode_one_piece, pieceName(1.0, null), it.pieceGrams.formatAmount())) },
                )
            }
            product.packageQuantity?.takeIf { it != product.servingQuantity }?.let { pack ->
                AssistChip(
                    onClick = { viewModel.useAmount(pack, product.isLiquid) },
                    label = { Text(stringResource(R.string.barcode_package, pack.formatAmount(), unitLabel)) },
                )
            }
        }
    }

    viewModel.nutrition?.let { values ->
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.label_total), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${values.kcal.formatKcal()} kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            listOfNotNull(
                values.proteinG?.let { stringResource(R.string.macro_protein_g, it.formatAmount()) },
                values.carbsG?.let { stringResource(R.string.macro_carbs_g, it.formatAmount()) },
                values.fatG?.let { stringResource(R.string.macro_fat_g, it.formatAmount()) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        extrasLabel(values.fiberG, values.sugarsG, values.saltG)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    MealSelector(selected = viewModel.mealType, onSelect = viewModel::onMealTypeChange)

    TextButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.barcode_scan_another)) }
}

@Composable
private fun per100Summary(product: Product): String = listOfNotNull(
    product.kcalPer100?.let { "${it.formatKcal()} kcal" },
    product.proteinPer100?.let { stringResource(R.string.macro_short_protein, it.formatAmount()) },
    product.carbsPer100?.let { stringResource(R.string.macro_short_carbs, it.formatAmount()) },
    product.fatPer100?.let { stringResource(R.string.macro_short_fat, it.formatAmount()) },
).joinToString(" · ")
