package it.emanuelemelini.photocal.data.openfoodfacts

/**
 * Pieces in the manufacturer's serving, read from the Open Food Facts `serving_size` text,
 * e.g. "3 biscotti (25 g)" -> 3 pieces of 8.33 g called "biscotti".
 */
data class ServingPieces(
    /** Grams (= ml for liquids) of one piece. */
    val pieceGrams: Double,
    /**
     * Name of the pieces as written on the package, only when it is a plural (more than one
     * piece in the serving): null means the generic "pieces".
     */
    val label: String?,
)

object ServingSize {

    private const val NUMBER = """(\d+(?:[.,]\d+)?)"""
    private const val WORD = """([^\d()=]+?)"""
    private const val UNIT = """(?:g|gr|ml)\.?"""

    /** "3 biscotti (25 g)", "3 biscotti = 25 g". */
    private val COUNT_FIRST = Regex("""^$NUMBER\s*$WORD\s*(?:\(\s*$NUMBER\s*$UNIT\s*\)|=\s*$NUMBER\s*$UNIT)$""", RegexOption.IGNORE_CASE)

    /** "25 g (3 biscotti)". */
    private val GRAMS_FIRST = Regex("""^$NUMBER\s*$UNIT\s*\(\s*$NUMBER\s*$WORD\s*\)$""", RegexOption.IGNORE_CASE)

    /** "3 biscotti", with the grams in `serving_quantity`. */
    private val COUNT_ONLY = Regex("""^$NUMBER\s+$WORD$""", RegexOption.IGNORE_CASE)

    /** A serving ("1 serving (100 g)") or a quantity ("100 g"), not a number of pieces. */
    private val NOT_PIECES = setOf(
        "serving", "servings", "portion", "portions", "porzione", "porzioni", "porción", "porciones",
        "g", "gr", "gramm", "grams", "grammi", "ml", "cl", "l", "kg", "oz",
    )

    /** Generic pieces: they get the translated "pieces" of the app. */
    private val GENERIC_PIECES = setOf(
        "piece", "pieces", "pezzo", "pezzi", "pz", "pc", "pcs", "unit", "units", "unità", "stück",
    )

    private const val MAX_LABEL = 30

    /** null when the text doesn't say how many pieces the serving has. */
    fun parse(text: String?, servingGrams: Double?): ServingPieces? {
        val value = text?.trim()?.replace(Regex("""\s+"""), " ")?.takeIf { it.isNotEmpty() } ?: return null
        var count: Double? = null
        var word: String? = null
        var grams: Double? = null
        COUNT_FIRST.matchEntire(value)?.let {
            count = it.groupValues[1].toNumber()
            word = it.groupValues[2]
            grams = (it.groupValues[3].ifEmpty { it.groupValues[4] }).toNumber()
        } ?: GRAMS_FIRST.matchEntire(value)?.let {
            grams = it.groupValues[1].toNumber()
            count = it.groupValues[2].toNumber()
            word = it.groupValues[3]
        } ?: COUNT_ONLY.matchEntire(value)?.let {
            count = it.groupValues[1].toNumber()
            word = it.groupValues[2]
            grams = servingGrams
        }
        val pieces = count?.takeIf { it > 0 } ?: return null
        val total = grams?.takeIf { it > 0 } ?: return null
        val name = word?.trim()?.trimEnd('.')?.trim().orEmpty()
            // "5 BISCUITS (30 g)": all caps is how the label is printed, not the name
            .let { if (it == it.uppercase()) it.lowercase() else it }
        if (name.isEmpty() || name.lowercase() in NOT_PIECES) return null
        val label = name.takeIf { pieces > 1 && it.length <= MAX_LABEL && it.lowercase() !in GENERIC_PIECES }
        return ServingPieces(pieceGrams = total / pieces, label = label)
    }

    private fun String.toNumber(): Double? = replace(',', '.').toDoubleOrNull()
}
