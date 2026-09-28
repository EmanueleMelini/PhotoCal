package it.emanuelemelini.photocal.data.openfoodfacts

/** Packaged product from Open Food Facts. Values are per 100 g (or 100 ml for liquids). */
data class Product(
    val barcode: String,
    val name: String,
    val brand: String?,
    val kcalPer100: Double?,
    val proteinPer100: Double?,
    val carbsPer100: Double?,
    val fatPer100: Double?,
    /** Serving suggested by the manufacturer, in g or ml. */
    val servingQuantity: Double?,
    /** Package content, in g or ml. */
    val packageQuantity: Double?,
    val isLiquid: Boolean,
    val imageUrl: String?,
) {
    val hasNutrition: Boolean get() = kcalPer100 != null

    /**
     * Brand, only when it adds information: null if either one contains the other
     * (e.g. "Nutella"/"Nutella", "Coca-Cola"/"COCA-COLA SERVICES SA/NV").
     */
    val distinctBrand: String?
        get() = brand?.takeUnless { it.contains(name, ignoreCase = true) || name.contains(it, ignoreCase = true) }

    /** Name saved in the diary, with the brand when it adds information. */
    val displayName: String
        get() = distinctBrand?.let { "$name ($it)" } ?: name
}
