package it.emanuelemelini.photocal.ui.barcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import it.emanuelemelini.photocal.data.openfoodfacts.Product
import it.emanuelemelini.photocal.ui.appContainer
import it.emanuelemelini.photocal.ui.components.MealSelector
import it.emanuelemelini.photocal.ui.components.QuantityRow
import it.emanuelemelini.photocal.ui.components.errorText
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.formatKcal
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
        BarcodeViewModel(createSavedStateHandle(), container.foodRepository, container.openFoodFactsClient)
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
                        "Lo scanner si sta scaricando da Google Play services: riprova tra qualche istante."
                    } else {
                        "Scanner non disponibile. Riprova o inserisci il codice a mano."
                    }
                )
            } catch (_: Exception) {
                viewModel.onScanFailed("Scanner non disponibile. Riprova o inserisci il codice a mano.")
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
                title = { Text("Barcode") },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onDone)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
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
                    Text("Cerco il prodotto ${status.barcode}…")
                }

                is BarcodeStatus.NotFound -> MessageCard(
                    message = "Nessun prodotto con codice ${status.barcode} su Open Food Facts.",
                    onScanAgain = ::startScan,
                    onManualEntry = { onManualEntry(null) },
                )

                is BarcodeStatus.Error -> MessageCard(
                    message = status.message,
                    onRetry = status.barcode?.let { code -> { viewModel.lookup(code) } },
                    onScanAgain = ::startScan,
                    onManualEntry = { onManualEntry(viewModel.name.ifBlank { null }) },
                )

                is BarcodeStatus.Found -> ProductForm(
                    product = status.product,
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
        "Inquadra il codice a barre di un prodotto confezionato: i valori nutrizionali arrivano da Open Food Facts.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
        Icon(painterResource(R.drawable.ic_barcode), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Scansiona")
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
            label = { Text("Oppure digita il codice") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (manualCode.length >= 8) onSearch() }),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onSearch, enabled = manualCode.length >= 8) { Text("Cerca") }
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
                onRetry?.let { TextButton(onClick = it) { Text("Riprova") } }
                TextButton(onClick = onScanAgain) { Text("Scansiona") }
                TextButton(onClick = onManualEntry) { Text("Inserisci a mano") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductForm(
    product: Product,
    viewModel: BarcodeViewModel,
    onScanAgain: () -> Unit,
) {
    val unitLabel = if (product.isLiquid) "ml" else "g"

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
                    text = "Per 100 $unitLabel: " + per100Summary(product),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    OutlinedTextField(
        value = viewModel.name,
        onValueChange = viewModel::onNameChange,
        label = { Text("Nome nel diario") },
        isError = viewModel.showErrors && !viewModel.nameValid,
        supportingText = errorText(viewModel.showErrors && !viewModel.nameValid, "Inserisci un nome"),
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
    )

    if (product.servingQuantity != null || product.packageQuantity != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            product.servingQuantity?.let { serving ->
                AssistChip(
                    onClick = { viewModel.useAmount(serving, product.isLiquid) },
                    label = { Text("1 porzione (${serving.formatAmount()} $unitLabel)") },
                )
            }
            product.packageQuantity?.takeIf { it != product.servingQuantity }?.let { pack ->
                AssistChip(
                    onClick = { viewModel.useAmount(pack, product.isLiquid) },
                    label = { Text("Confezione (${pack.formatAmount()} $unitLabel)") },
                )
            }
        }
    }

    viewModel.nutrition?.let { values ->
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Totale", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${values.kcal.formatKcal()} kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            listOfNotNull(
                values.proteinG?.let { "Proteine ${it.formatAmount()} g" },
                values.carbsG?.let { "Carboidrati ${it.formatAmount()} g" },
                values.fatG?.let { "Grassi ${it.formatAmount()} g" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    MealSelector(selected = viewModel.mealType, onSelect = viewModel::onMealTypeChange)

    Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text("Salva") }
    TextButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) { Text("Scansiona un altro prodotto") }
}

private fun per100Summary(product: Product): String = listOfNotNull(
    product.kcalPer100?.let { "${it.formatKcal()} kcal" },
    product.proteinPer100?.let { "P ${it.formatAmount()}" },
    product.carbsPer100?.let { "C ${it.formatAmount()}" },
    product.fatPer100?.let { "G ${it.formatAmount()}" },
).joinToString(" · ")
