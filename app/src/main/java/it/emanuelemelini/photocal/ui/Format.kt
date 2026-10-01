package it.emanuelemelini.photocal.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.ServingUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun Double.formatKcal(): String = roundToInt().toString()

/** Grams and macros: whole numbers without decimals, otherwise one decimal (locale separator). */
fun Double.formatAmount(locale: Locale = AppLocale.current): String {
    val rounded = (this * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString()
    else String.format(locale, "%.1f", rounded)
}

/** Accepts both comma and dot as decimal separator, whatever the language. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** Today / Yesterday / Tomorrow, or null for other dates. */
@Composable
fun LocalDate.relativeLabel(today: LocalDate = LocalDate.now()): String? = when (this) {
    today -> stringResource(R.string.day_today)
    today.minusDays(1) -> stringResource(R.string.day_yesterday)
    today.plusDays(1) -> stringResource(R.string.day_tomorrow)
    else -> null
}

/** e.g. "Lun 28 set 2026" / "Mon, Sep 28, 2026": field order follows the language. */
fun LocalDate.shortLabel(locale: Locale = AppLocale.current): String = formatted("EEEdMMMyyyy", locale)

/** e.g. "Lunedì 28 settembre 2026" / "Monday, September 28, 2026". */
fun LocalDate.longLabel(locale: Locale = AppLocale.current): String = formatted("EEEEdMMMMyyyy", locale)

private fun LocalDate.formatted(skeleton: String, locale: Locale): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return format(DateTimeFormatter.ofPattern(pattern, locale)).replaceFirstChar { it.titlecase(locale) }
}

/** Plural quantity for a decimal count: "1 calice", "1,5 calici". */
fun pluralCount(count: Double): Int = if (count == 1.0) 1 else 2

/** Quantity shown in the diary, e.g. "120 g", "2 calici (300 ml)" or "3 biscotti (25 g)". */
@Composable
fun FoodEntry.quantityLabel(): String? = quantityLabel(grams, servingUnit, servings, servingLabel)

/** Same label from the raw values, e.g. for the entries of a shared day. */
@Composable
fun quantityLabel(grams: Double?, unit: ServingUnit?, count: Double?, pieceLabel: String? = null): String? {
    if (unit != null && unit != ServingUnit.GRAMS && count != null) {
        if (unit == ServingUnit.MILLILITERS) return "${count.formatAmount()} ml"
        if (unit == ServingUnit.PIECE) {
            val name = pieceName(count, pieceLabel)
            return grams?.let { "${count.formatAmount()} $name (${it.formatAmount()} g)" } ?: "${count.formatAmount()} $name"
        }
        val name = pluralStringResource(unit.nameRes, pluralCount(count))
        return "${count.formatAmount()} $name (${(count * unit.gramsPerUnit).formatAmount()} ml)"
    }
    return grams?.let { "${it.formatAmount()} g" }
}

/**
 * Name of [count] pieces: the one from the package (a plural, so not for a single piece),
 * otherwise the translated "pezzo"/"pezzi".
 */
@Composable
fun pieceName(count: Double, label: String?): String =
    label?.takeIf { pluralCount(count) != 1 } ?: pluralStringResource(R.plurals.unit_piece, pluralCount(count))

/** Fiber, sugars and salt, e.g. "Fibre 2 g · Zuccheri 10 g · Sale 0,3 g"; null when none is known. */
@Composable
fun extrasLabel(fiberG: Double?, sugarsG: Double?, saltG: Double?): String? =
    listOfNotNull(
        fiberG?.let { stringResource(R.string.extra_fiber_g, it.formatAmount()) },
        sugarsG?.let { stringResource(R.string.extra_sugars_g, it.formatAmount()) },
        saltG?.let { stringResource(R.string.extra_salt_g, it.formatSalt()) },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** Salt keeps two decimals under 1 g: 0,25 g matters on a daily total of about 5 g. */
fun Double.formatSalt(locale: Locale = AppLocale.current): String =
    if (this in 0.0..<1.0) String.format(locale, "%.2f", this) else formatAmount(locale)

/** Signed change with a real minus sign, e.g. "+0,4" or "−1,6". */
fun Double.formatSignedAmount(): String = when {
    this > 0 -> "+" + formatAmount()
    this < 0 -> "\u2212" + (-this).formatAmount()
    else -> formatAmount()
}

/** Water in liters with one decimal, e.g. "1,6 L". */
fun formatLiters(ml: Int, locale: Locale = AppLocale.current): String =
    String.format(locale, "%.1f L", ml / 1000.0)
