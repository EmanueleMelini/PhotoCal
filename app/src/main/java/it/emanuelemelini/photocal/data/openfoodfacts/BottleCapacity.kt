package it.emanuelemelini.photocal.data.openfoodfacts

import it.emanuelemelini.photocal.data.nutrition.Bottle
import kotlin.math.roundToInt

/** Capacity of a bottle scanned on Open Food Facts, in ml. */
object BottleCapacity {

    /** "500 ml", "0,5 l", "75 cl", "6 x 50 cl" (one bottle of the pack), "1.5L". */
    private val VOLUME = Regex("""(\d+(?:[.,]\d+)?)\s*(ml|cl|dl|l|lt|litr[oi]|litres?|liters?)\b""", RegexOption.IGNORE_CASE)

    /** null when the product says nothing usable or the volume isn't a bottle's. */
    fun of(product: Product): Int? {
        val fromText = product.quantityText?.let(::parse)
        val ml = fromText ?: product.packageQuantity?.takeIf { product.isLiquid }?.roundToInt()
        return ml?.takeIf { it in Bottle.ML_RANGE }
    }

    /** First volume in the text, in ml. */
    fun parse(text: String): Int? {
        val match = VOLUME.find(text) ?: return null
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val factor = when (match.groupValues[2].lowercase()) {
            "ml" -> 1
            "cl" -> 10
            "dl" -> 100
            else -> 1_000
        }
        return (number * factor).roundToInt().takeIf { it > 0 }
    }
}
