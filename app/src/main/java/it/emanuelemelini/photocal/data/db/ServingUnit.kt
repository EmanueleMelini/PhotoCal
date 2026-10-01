package it.emanuelemelini.photocal.data.db

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import it.emanuelemelini.photocal.R

/**
 * Unit used to enter the quantity. For liquids 1 ml ≈ 1 g, so the diary always stores
 * grams (= ml) together with the unit and the number of servings.
 * The constant names are stored in the database: renaming them needs a migration.
 * [PIECE] has no fixed size: the grams of one piece depend on the product.
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
    PIECE(R.plurals.unit_piece, R.string.unit_menu_piece, 0.0, false),
    ESPRESSO_CUP(R.plurals.unit_espresso_cup, R.string.unit_menu_espresso_cup, 30.0, true),
    CUP(R.plurals.unit_cup, R.string.unit_menu_cup, 250.0, true),
    GLASS(R.plurals.unit_glass, R.string.unit_menu_glass, 200.0, true),
    WINE_GLASS(R.plurals.unit_wine_glass, R.string.unit_menu_wine_glass, 150.0, true),
    ;

    /** Grams (= ml) of [count] units; for [PIECE] null until the grams of one piece are known. */
    fun grams(count: Double, pieceGrams: Double?): Double? =
        if (this == PIECE) pieceGrams?.takeIf { it > 0 }?.times(count) else count * gramsPerUnit

    companion object {
        /** Units with a fixed size, for foods without the grams of a piece. */
        val fixedSize: List<ServingUnit> get() = entries.filter { it != PIECE }
    }
}
