package it.emanuelemelini.photocal.data.quality

import it.emanuelemelini.photocal.data.db.NutrientComponent
import it.emanuelemelini.photocal.data.db.NutritionCategory
import it.emanuelemelini.photocal.data.db.ProductQuality
import kotlin.math.roundToInt

/** Score bands, as in the other product scanners. */
enum class QualityBand {
    POOR, MEDIOCRE, GOOD, EXCELLENT;

    companion object {
        fun of(score: Int): QualityBand = when {
            score < 25 -> POOR
            score < 50 -> MEDIOCRE
            score < 75 -> GOOD
            else -> EXCELLENT
        }
    }
}

enum class UnratedReason {
    /** No nutrition score for this kind of product (coffee, spices, alcohol...). */
    NOT_APPLICABLE,
    MISSING_NUTRITION,
    /** Without the ingredients the additives are unknown. */
    MISSING_INGREDIENTS,
}

/** Nutrients of the Nutri-Score computation; [negative] ones lower the score. */
enum class NutrientKind(val id: String, val negative: Boolean) {
    ENERGY("energy", true),
    SUGARS("sugars", true),
    SATURATED_FAT("saturated_fat", true),
    SALT("salt", true),
    SWEETENERS("non_nutritive_sweeteners", true),
    PROTEINS("proteins", false),
    FIBER("fiber", false),
    FRUITS_VEGETABLES("fruits_vegetables_legumes", false),
}

enum class NutrientLevel { LOW, MEDIUM, HIGH }

/** A nutrient worth showing: [value] per 100 g (or 100 ml) in [unit] ("kJ" for energy). */
data class NutrientRating(val kind: NutrientKind, val level: NutrientLevel, val value: Double?, val unit: String?) {
    /** Strength or weakness of the product: null when it's in the middle. */
    val isGood: Boolean?
        get() = when {
            !kind.negative -> true
            level == NutrientLevel.LOW -> true
            level == NutrientLevel.HIGH -> false
            else -> null
        }
}

/** An additive of the product; [additive] is null for codes missing from the table. */
data class RatedAdditive(val code: String, val additive: Additive?) {
    val risk: AdditiveRisk get() = additive?.risk ?: AdditiveRisk.NONE

    /** "E322i". */
    val label: String get() = code.replaceFirstChar(Char::uppercaseChar)
}

/**
 * Quality score of a product, out of 100: nutrition 60 (from the Nutri-Score 2023 points),
 * additives 30 (EFSA overexposure risk), organic 10. A high-risk additive caps it at 49.
 * [score] is null when the product can't be rated: [unrated] says why, and the parts that
 * could be computed are still there.
 */
data class QualityRating(
    val score: Int?,
    val unrated: UnratedReason?,
    /** 0..[NUTRITION_MAX], null without nutrition data. */
    val nutritionPoints: Int?,
    /** 0..[ADDITIVES_MAX], null without ingredients. */
    val additivePoints: Int?,
    /** 0 or [ORGANIC_MAX]. */
    val organicPoints: Int,
    /** The score was lowered to [CAP_WITH_HIGH_RISK] by a high-risk additive. */
    val capped: Boolean,
    val nutrients: List<NutrientRating>,
    /** null when the ingredients are unknown. */
    val additives: List<RatedAdditive>?,
    val organic: Boolean,
    val novaGroup: Int?,
    /** Values per 100 ml (beverages) instead of per 100 g. */
    val perMl: Boolean,
    val isWater: Boolean,
) {
    val band: QualityBand? get() = score?.let(QualityBand::of)

    companion object {
        const val NUTRITION_MAX = 60
        const val ADDITIVES_MAX = 30
        const val ORGANIC_MAX = 10
        const val CAP_WITH_HIGH_RISK = 49
        const val HIGH_RISK_PENALTY = 15
        const val MODERATE_RISK_PENALTY = 8

        /**
         * Average score of the rated products of a day, weighted on their kcal: a spoon of oil
         * counts less than a plate of pasta. Without kcal (e.g. only water), a plain average.
         */
        fun average(items: List<Pair<Int, Double>>): Int? {
            if (items.isEmpty()) return null
            val kcal = items.sumOf { it.second.coerceAtLeast(0.0) }
            return if (kcal > 0) (items.sumOf { it.first * it.second.coerceAtLeast(0.0) } / kcal).roundToInt()
            else items.map { it.first }.average().roundToInt()
        }
    }
}

/** Computes [QualityRating] from the stored data; [additives] is the EFSA risk table. */
class QualityRater(private val additives: Map<String, Additive>) {

    fun rate(quality: ProductQuality): QualityRating {
        val isWater = quality.nutritionCategory == NutritionCategory.WATER
        val grade = quality.nutritionGrade?.singleOrNull()?.takeIf { it in 'a'..'e' }
        val points = quality.nutritionPoints
        val nutritionPercent = when {
            isWater -> 100.0
            grade != null && points != null -> nutritionPercent(grade, points, quality.nutritionCategory)
            else -> null
        }
        val nutritionPoints = nutritionPercent?.let { (it * QualityRating.NUTRITION_MAX / 100).roundToInt() }

        // Water often has no ingredient list, and needs none
        val codes = quality.additives ?: emptyList<String>().takeIf { isWater }
        val rated = codes?.let { mostSpecific(it).map { code -> RatedAdditive(code, additives[code]) } }
        val highRisk = rated.orEmpty().count { it.risk == AdditiveRisk.HIGH }
        val moderateRisk = rated.orEmpty().count { it.risk == AdditiveRisk.MODERATE }
        val additivePoints = rated?.let {
            (QualityRating.ADDITIVES_MAX - highRisk * QualityRating.HIGH_RISK_PENALTY - moderateRisk * QualityRating.MODERATE_RISK_PENALTY)
                .coerceAtLeast(0)
        }
        val organicPoints = if (quality.organic) QualityRating.ORGANIC_MAX else 0

        val unrated = when {
            quality.nutritionGrade == NOT_APPLICABLE && !isWater -> UnratedReason.NOT_APPLICABLE
            nutritionPoints == null -> UnratedReason.MISSING_NUTRITION
            additivePoints == null -> UnratedReason.MISSING_INGREDIENTS
            else -> null
        }
        val total = if (unrated == null) nutritionPoints!! + additivePoints!! + organicPoints else null
        val capped = total != null && highRisk > 0 && total > QualityRating.CAP_WITH_HIGH_RISK
        return QualityRating(
            score = if (capped) QualityRating.CAP_WITH_HIGH_RISK else total,
            unrated = unrated,
            nutritionPoints = nutritionPoints,
            additivePoints = additivePoints,
            organicPoints = organicPoints,
            capped = capped,
            nutrients = if (isWater) emptyList() else quality.nutrients.mapNotNull(::nutrientRating).sortedBy { it.kind },
            additives = rated,
            organic = quality.organic,
            novaGroup = quality.novaGroup,
            perMl = isWater || quality.nutritionCategory == NutritionCategory.BEVERAGE,
            isWater = isWater,
        )
    }

    /**
     * Open Food Facts lists both the family and the additive ("e322", "e322i"): only the most
     * specific one is kept, so a family doesn't count twice.
     */
    private fun mostSpecific(codes: List<String>): List<String> {
        val ancestors = codes.flatMapTo(HashSet()) { ancestorsOf(it) }
        return codes.distinct().filterNot { it in ancestors }
    }

    private fun ancestorsOf(code: String): List<String> =
        generateSequence(additives[code]?.parent) { additives[it]?.parent }.take(MAX_DEPTH).toList()

    private companion object {
        const val NOT_APPLICABLE = "not-applicable"
        const val MAX_DEPTH = 5
    }
}

/**
 * Nutrition part as a percentage: the grade sets the band (A 80-100 ... E 0-20), the points
 * place the product inside it. So two products with close values get close percentages even
 * across a grade boundary. Bands in points follow the Nutri-Score 2023 thresholds, with the
 * edges halfway between whole points; the open ends of A and E are cut where few products go.
 */
internal fun nutritionPercent(grade: Char, points: Int, category: NutritionCategory): Double {
    val index = grade - 'a'
    val (low, high) = (BANDS[category] ?: BANDS.getValue(NutritionCategory.GENERAL))[index]
    val position = (points.toDouble().coerceIn(low, high) - low) / (high - low)
    return BAND_SIZE * (GRADE_COUNT - index) - BAND_SIZE * position
}

private const val GRADE_COUNT = 5
private const val BAND_SIZE = 100.0 / GRADE_COUNT

/** Points range of grades A..E: general foods A <= 0, B 1-2, C 3-10, D 11-18, E >= 19. */
private val BANDS: Map<NutritionCategory, List<Pair<Double, Double>>> = mapOf(
    NutritionCategory.GENERAL to listOf(-6.0 to 0.5, 0.5 to 2.5, 2.5 to 10.5, 10.5 to 18.5, 18.5 to 30.5),
    // Fats, oils, nuts and seeds: A <= -6, B -5..2, then as the general foods
    NutritionCategory.FAT to listOf(-12.0 to -5.5, -5.5 to 2.5, 2.5 to 10.5, 10.5 to 18.5, 18.5 to 30.5),
    // Beverages: A is only water, B <= 2, C 3-6, D 7-9, E >= 10
    NutritionCategory.BEVERAGE to listOf(-10.5 to -4.5, -4.5 to 2.5, 2.5 to 6.5, 6.5 to 9.5, 9.5 to 19.5),
)

/**
 * Level of a nutrient from its share of the Nutri-Score points: few points of a negative
 * nutrient (e.g. sugars up to about 7 g) is a strength, many (sugars from about 20 g) a
 * weakness; positive nutrients are shown only when they earn points.
 */
private fun nutrientRating(component: NutrientComponent): NutrientRating? {
    val kind = NutrientKind.entries.find { it.id == component.id } ?: return null
    val share = component.points.toDouble() / component.maxPoints
    val level = if (kind.negative) {
        when {
            share <= 0.1 -> NutrientLevel.LOW
            share < 0.4 -> NutrientLevel.MEDIUM
            else -> NutrientLevel.HIGH
        }
    } else {
        when {
            share >= 0.6 -> NutrientLevel.HIGH
            share >= 0.2 -> NutrientLevel.MEDIUM
            else -> NutrientLevel.LOW
        }
    }
    val shown = when {
        // Only when present: it's a weakness of some beverages
        kind == NutrientKind.SWEETENERS -> component.points > 0
        !kind.negative -> level != NutrientLevel.LOW
        else -> true
    }
    return NutrientRating(kind, level, component.value, component.unit).takeIf { shown }
}
