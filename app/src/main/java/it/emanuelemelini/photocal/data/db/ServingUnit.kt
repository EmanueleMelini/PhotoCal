package it.emanuelemelini.photocal.data.db

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import it.emanuelemelini.photocal.R

/**
 * Unit used to enter the quantity. For liquids 1 ml ≈ 1 g, so the diary always stores
 * grams (= ml) together with the unit and the number of servings.
 * The constant names are stored in the database: renaming them needs a migration.
 */
enum class ServingUnit(
    @PluralsRes val nameRes: Int,
    /** Label in the picker menu, e.g. "calice (150 ml)"; receives the ml as argument. */
    @StringRes val menuRes: Int,
    val gramsPerUnit: Double,
    val isLiquid: Boolean,
) {
    GRAMS(R.plurals.unit_grams, R.string.unit_menu_grams, 1.0, false),
    MILLILITERS(R.plurals.unit_ml, R.string.unit_menu_ml, 1.0, true),
    ESPRESSO_CUP(R.plurals.unit_espresso_cup, R.string.unit_menu_espresso_cup, 30.0, true),
    CUP(R.plurals.unit_cup, R.string.unit_menu_cup, 250.0, true),
    GLASS(R.plurals.unit_glass, R.string.unit_menu_glass, 200.0, true),
    WINE_GLASS(R.plurals.unit_wine_glass, R.string.unit_menu_wine_glass, 150.0, true),
}
