package it.emanuelemelini.photocal.ui.quality

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.emanuelemelini.photocal.AppLanguage
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.quality.AdditiveRisk
import it.emanuelemelini.photocal.data.quality.NutrientKind
import it.emanuelemelini.photocal.data.quality.NutrientRating
import it.emanuelemelini.photocal.data.quality.QualityBand
import it.emanuelemelini.photocal.data.quality.QualityRating
import it.emanuelemelini.photocal.data.quality.RatedAdditive
import it.emanuelemelini.photocal.data.quality.UnratedReason
import it.emanuelemelini.photocal.ui.formatAmount
import it.emanuelemelini.photocal.ui.formatKcal
import it.emanuelemelini.photocal.ui.formatSalt
import it.emanuelemelini.photocal.ui.theme.QualityColors

/** Product whose quality sheet is open. */
data class QualitySheetData(val name: String, val barcode: String, val rating: QualityRating)

val QualityBand.color: Color
    get() = when (this) {
        QualityBand.POOR -> QualityColors.poor
        QualityBand.MEDIOCRE -> QualityColors.mediocre
        QualityBand.GOOD -> QualityColors.good
        QualityBand.EXCELLENT -> QualityColors.excellent
    }

private val QualityBand.textColor: Color
    get() = when (this) {
        QualityBand.POOR, QualityBand.EXCELLENT -> QualityColors.onDeepFill
        QualityBand.MEDIOCRE, QualityBand.GOOD -> QualityColors.onBrightFill
    }

@get:StringRes
val QualityBand.labelRes: Int
    get() = when (this) {
        QualityBand.POOR -> R.string.quality_band_poor
        QualityBand.MEDIOCRE -> R.string.quality_band_mediocre
        QualityBand.GOOD -> R.string.quality_band_good
        QualityBand.EXCELLENT -> R.string.quality_band_excellent
    }

/** Score in a circle of its band's color; [onClick] opens the details. */
@Composable
fun QualityBadge(score: Int, modifier: Modifier = Modifier, size: Dp = 40.dp, onClick: (() -> Unit)? = null) {
    val band = QualityBand.of(score)
    val description = pluralStringResource(R.plurals.quality_badge_description, score, score, stringResource(band.labelRes))
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(band.color, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clearAndSetSemantics {
                contentDescription = description
                if (onClick != null) role = Role.Button
            },
    ) {
        Text(
            score.toString(),
            color = band.textColor,
            fontWeight = FontWeight.Bold,
            fontSize = if (size >= 48.dp) 20.sp else 16.sp,
        )
    }
}

@StringRes
fun UnratedReason.messageRes(): Int = when (this) {
    UnratedReason.NOT_APPLICABLE -> R.string.quality_unrated_not_applicable
    UnratedReason.MISSING_NUTRITION -> R.string.quality_unrated_missing_nutrition
    UnratedReason.MISSING_INGREDIENTS -> R.string.quality_unrated_missing_ingredients
}

/**
 * Score of a product explained: nutrition, additives, organic, processing, then how it is
 * computed, the sources and the limits of the rating. The Nutri-Score letter is never shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(data: QualitySheetData, onDismiss: () -> Unit) {
    val rating = data.rating
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Header(data.name, rating)

            Section(R.string.quality_section_nutrition, rating.nutritionPoints, QualityRating.NUTRITION_MAX) {
                when {
                    rating.isWater -> Note(stringResource(R.string.quality_water))
                    rating.nutritionPoints == null -> Note(stringResource(R.string.quality_no_nutrition))
                    else -> {
                        Note(stringResource(if (rating.perMl) R.string.quality_per_100ml else R.string.quality_per_100g))
                        rating.nutrients.forEach { NutrientRow(it) }
                    }
                }
            }

            Section(R.string.quality_section_additives, rating.additivePoints, QualityRating.ADDITIVES_MAX) {
                val additives = rating.additives
                when {
                    additives == null -> Note(stringResource(R.string.quality_no_ingredients))
                    additives.isEmpty() -> Note(stringResource(R.string.quality_no_additives))
                    else -> {
                        additives.forEach { AdditiveRow(it) }
                        if (additives.any { it.risk != AdditiveRisk.NONE }) Note(stringResource(R.string.quality_risk_explanation))
                    }
                }
            }

            Section(R.string.quality_section_organic, rating.organicPoints, QualityRating.ORGANIC_MAX) {
                Text(stringResource(if (rating.organic) R.string.quality_organic_yes else R.string.quality_organic_no))
            }

            rating.novaGroup?.let { nova ->
                Section(R.string.quality_section_processing, points = null, max = null) {
                    Text(stringResource(R.string.quality_nova, stringArrayResource(R.array.quality_nova)[nova - 1], nova))
                    Note(stringResource(R.string.quality_nova_note))
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.quality_method_title), style = MaterialTheme.typography.titleSmall)
            Note(stringResource(R.string.quality_method))
            Note(stringResource(R.string.quality_disclaimer))
            Note(stringResource(R.string.quality_source))
            TextButton(onClick = { uriHandler.openUri(OFF_PRODUCT_URL + data.barcode) }) {
                Text(stringResource(R.string.quality_open_off))
            }
        }
    }
}

@Composable
private fun Header(name: String, rating: QualityRating) {
    val score = rating.score
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (score != null) {
            QualityBadge(score, size = 56.dp)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            val band = rating.band
            if (score != null && band != null) {
                Text(
                    stringResource(R.string.quality_header, stringResource(band.labelRes), score),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    rating.unrated?.let { Text(stringResource(it.messageRes()), style = MaterialTheme.typography.bodyMedium) }
    if (rating.capped) {
        Text(
            stringResource(R.string.quality_capped, QualityRating.CAP_WITH_HIGH_RISK),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Title with the points earned out of [max] ([points] null: not computed or not counted). */
@Composable
private fun Section(@StringRes titleRes: Int, points: Int?, max: Int?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (points != null && max != null) {
                Text(
                    stringResource(R.string.quality_points, points, max),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Colored dot next to a text that already says the same: color is never the only cue. */
@Composable
private fun Dot(color: Color?) {
    val shape = CircleShape
    Box(
        Modifier
            .padding(top = 6.dp)
            .size(10.dp)
            .then(
                if (color != null) Modifier.background(color, shape)
                else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, shape),
            ),
    )
}

@Composable
private fun NutrientRow(nutrient: NutrientRating) {
    val color = when (nutrient.isGood) {
        true -> QualityColors.excellent
        false -> QualityColors.poor
        null -> QualityColors.mediocre
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Dot(color)
        Text(nutrientLabel(nutrient), modifier = Modifier.weight(1f))
        nutrientValue(nutrient)?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun nutrientLabel(nutrient: NutrientRating): String {
    val arrayRes = when (nutrient.kind) {
        NutrientKind.ENERGY -> R.array.quality_energy
        NutrientKind.SUGARS -> R.array.quality_sugars
        NutrientKind.SATURATED_FAT -> R.array.quality_saturated_fat
        NutrientKind.SALT -> R.array.quality_salt
        NutrientKind.PROTEINS -> R.array.quality_proteins
        NutrientKind.FIBER -> R.array.quality_fiber
        NutrientKind.FRUITS_VEGETABLES -> R.array.quality_fruits_vegetables
        NutrientKind.SWEETENERS -> return stringResource(R.string.quality_sweeteners)
    }
    return stringArrayResource(arrayRes)[nutrient.level.ordinal]
}

/** Amount per 100 g/ml: energy in kcal, salt with its decimals. */
private fun nutrientValue(nutrient: NutrientRating): String? {
    val value = nutrient.value ?: return null
    return when {
        nutrient.kind == NutrientKind.SWEETENERS -> null
        nutrient.unit.equals("kJ", ignoreCase = true) -> "${(value / KJ_PER_KCAL).formatKcal()} kcal"
        nutrient.unit == "%" -> "${value.formatAmount()} %"
        nutrient.kind == NutrientKind.SALT -> "${value.formatSalt()} g"
        else -> "${value.formatAmount()} ${nutrient.unit.orEmpty()}".trim()
    }
}

@Composable
private fun AdditiveRow(additive: RatedAdditive) {
    val (color, riskRes) = when (additive.risk) {
        AdditiveRisk.HIGH -> QualityColors.poor to R.string.quality_risk_high
        AdditiveRisk.MODERATE -> QualityColors.mediocre to R.string.quality_risk_moderate
        AdditiveRisk.NONE -> null to R.string.quality_risk_none
    }
    val name = additive.additive?.let { if (AppLocale.language == AppLanguage.ITALIAN) it.nameIt else it.nameEn }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Dot(color)
        Column(Modifier.weight(1f)) {
            Text(listOfNotNull(additive.label, name?.replaceFirstChar(Char::uppercaseChar)).joinToString(" · "))
            Text(stringResource(riskRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Day summary: average score of the packaged products, weighted on their kcal. */
@Composable
fun DayQualityRow(score: Int, count: Int, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        QualityBadge(score)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.quality_day_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(QualityBand.of(score).labelRes) + " · " + pluralStringResource(R.plurals.quality_day_products, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Line under a scanned product: score and band, or why there is none. */
@Composable
fun ProductQualityRow(rating: QualityRating, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val score = rating.score
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        if (score != null) {
            QualityBadge(score)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.quality_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(rating.band!!.labelRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                stringResource(rating.unrated!!.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            stringResource(R.string.quality_details),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private const val KJ_PER_KCAL = 4.184
private const val OFF_PRODUCT_URL = "https://world.openfoodfacts.org/product/"
