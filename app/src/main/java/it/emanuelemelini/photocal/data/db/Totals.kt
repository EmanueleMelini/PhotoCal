package it.emanuelemelini.photocal.data.db

import java.time.LocalDate

/** Totals of a single day; fiber, sugars and salt are null when no entry has them. */
data class Totals(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double? = null,
    val sugarsG: Double? = null,
    val saltG: Double? = null,
)

/** Totals of one day within a range (for the history). */
data class DayTotals(
    val date: LocalDate,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)
