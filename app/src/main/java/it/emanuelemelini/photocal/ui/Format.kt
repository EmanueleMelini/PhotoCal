package it.emanuelemelini.photocal.ui

import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.ServingUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val ITALIAN: Locale = Locale.ITALIAN
private val shortDateFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", ITALIAN)
private val longDateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", ITALIAN)

fun Double.formatKcal(): String = roundToInt().toString()

/** Grams and macros: whole numbers without decimals, otherwise one decimal with a comma. */
fun Double.formatAmount(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString()
    else String.format(ITALIAN, "%.1f", rounded)
}

/** Accepts both comma and dot as decimal separator. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** "Oggi", "Ieri", "Domani" (today, yesterday, tomorrow), or null for other dates. */
fun LocalDate.relativeLabel(today: LocalDate = LocalDate.now()): String? = when (this) {
    today -> "Oggi"
    today.minusDays(1) -> "Ieri"
    today.plusDays(1) -> "Domani"
    else -> null
}

fun LocalDate.shortLabel(): String = format(shortDateFormatter).capitalized()

fun LocalDate.longLabel(): String = format(longDateFormatter).capitalized()

private fun String.capitalized() = replaceFirstChar { it.titlecase(ITALIAN) }

/** Quantity shown in the diary, e.g. "120 g" or "2 calici (300 ml)". */
fun FoodEntry.quantityLabel(): String? {
    val unit = servingUnit
    val count = servings
    if (unit != null && unit != ServingUnit.GRAMMI && count != null) {
        return if (unit == ServingUnit.MILLILITRI) "${count.formatAmount()} ml"
        else "${count.formatAmount()} ${unit.labelFor(count)} (${(count * unit.gramsPerUnit).formatAmount()} ml)"
    }
    return grams?.let { "${it.formatAmount()} g" }
}
