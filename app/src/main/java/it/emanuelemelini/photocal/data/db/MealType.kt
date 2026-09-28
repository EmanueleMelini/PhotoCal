package it.emanuelemelini.photocal.data.db

import java.time.LocalTime

enum class MealType(val label: String) {
    COLAZIONE("Colazione"),
    PRANZO("Pranzo"),
    CENA("Cena"),
    SPUNTINO("Spuntino");

    companion object {
        /** Default meal suggested from the current time. */
        fun suggestedFor(time: LocalTime = LocalTime.now()): MealType = when (time.hour) {
            in 5..10 -> COLAZIONE
            in 11..14 -> PRANZO
            in 18..22 -> CENA
            else -> SPUNTINO
        }
    }
}
