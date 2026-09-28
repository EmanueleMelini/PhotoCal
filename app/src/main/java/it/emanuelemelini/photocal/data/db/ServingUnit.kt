package it.emanuelemelini.photocal.data.db

/**
 * Unit used to enter the quantity. For liquids 1 ml ≈ 1 g, so the diary always stores
 * grams (= ml) together with the unit and the number of servings.
 */
enum class ServingUnit(
    val singular: String,
    val plural: String,
    val gramsPerUnit: Double,
    val isLiquid: Boolean,
) {
    GRAMMI("g", "g", 1.0, false),
    MILLILITRI("ml", "ml", 1.0, true),
    TAZZINA("tazzina", "tazzine", 30.0, true),
    TAZZA("tazza", "tazze", 250.0, true),
    BICCHIERE("bicchiere", "bicchieri", 200.0, true),
    CALICE("calice", "calici", 150.0, true);

    fun labelFor(count: Double): String = if (count == 1.0) singular else plural

    /** Label in the picker menu, e.g. "calice (150 ml)". */
    val menuLabel: String
        get() = when (this) {
            GRAMMI -> "grammi"
            MILLILITRI -> "ml"
            else -> "$singular (${gramsPerUnit.toInt()} ml)"
        }
}
