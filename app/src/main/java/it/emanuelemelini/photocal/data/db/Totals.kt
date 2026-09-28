package it.emanuelemelini.photocal.data.db

import java.time.LocalDate

/** Totals of a single day. */
data class Totals(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

/** Totals of one day within a range (for the history). */
data class DayTotals(
    val date: LocalDate,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)
