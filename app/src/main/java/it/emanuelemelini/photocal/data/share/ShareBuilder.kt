package it.emanuelemelini.photocal.data.share

import it.emanuelemelini.photocal.BuildConfig
import it.emanuelemelini.photocal.data.FoodRepository
import it.emanuelemelini.photocal.data.WaterRepository
import it.emanuelemelini.photocal.data.WeightRepository
import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.ServingUnit
import it.emanuelemelini.photocal.data.nutrition.WaterCalculator
import it.emanuelemelini.photocal.data.photo.ProfilePhotoStorage
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import kotlin.math.roundToLong

/** What the sender shows; everything is shown by default. */
data class ShareOptions(
    /** The 7 days ending on the chosen one, instead of the day alone. */
    val week: Boolean = false,
    val photo: Boolean = true,
    val goals: Boolean = true,
    val kcal: Boolean = true,
    val macros: Boolean = true,
    val water: Boolean = true,
    /** Day only: a week never carries the meals. */
    val meals: Boolean = true,
    val weight: Boolean = true,
) {
    val hasContent: Boolean get() = kcal || macros || water || weight || (meals && !week)
}

/** Builds the share card of a day or a week from the diary, and its link. */
class ShareBuilder(
    private val foodRepository: FoodRepository,
    private val waterRepository: WaterRepository,
    private val weightRepository: WeightRepository,
    private val settingsRepository: SettingsRepository,
    private val profilePhotoStorage: ProfilePhotoStorage,
) {
    private val key: ByteArray = Base64.getDecoder().decode(BuildConfig.SHARE_KEY)

    suspend fun build(date: LocalDate, options: ShareOptions): SharedCard {
        val settings = settingsRepository.settings.first()
        val from = if (options.week) date.minusDays(6) else date
        val totals = foodRepository.totalsBetween(from, date).associateBy { it.date }
        val water = if (options.water) waterRepository.mlBetween(from, date) else emptyMap()
        val weights = if (options.weight) {
            weightRepository.observeBetween(from, date).first().associate { it.date to it.weightKg }
        } else {
            emptyMap()
        }

        val dayCount = if (options.week) 7L else 1L
        // List.map is inline: the entries of the day can be read inside it
        val days = (0 until dayCount).map { from.plusDays(it) }.map { day ->
            val total = totals[day]
            SharedDay(
                epochDay = day.toEpochDay(),
                kcal = if (options.kcal) round1(total?.kcal ?: 0.0) else null,
                proteinG = if (options.macros) round1(total?.proteinG ?: 0.0) else null,
                carbsG = if (options.macros) round1(total?.carbsG ?: 0.0) else null,
                fatG = if (options.macros) round1(total?.fatG ?: 0.0) else null,
                waterMl = if (options.water) water[day] ?: 0 else null,
                weightKg = weights[day]?.let(::round1),
                entries = if (options.meals && !options.week) {
                    foodRepository.observeEntries(day).first().map { it.toSharedEntry() }.take(ShareValidation.MAX_ENTRIES)
                } else {
                    null
                },
            )
        }

        val goals = if (options.goals) {
            SharedGoals(
                kcal = settings.dailyKcalGoal.takeIf { options.kcal },
                proteinG = settings.proteinGoalG.takeIf { options.macros },
                carbsG = settings.carbsGoalG.takeIf { options.macros },
                fatG = settings.fatGoalG.takeIf { options.macros },
                waterMl = WaterCalculator.goalMlInGlasses(settings.waterGoalMl, settings.glassMl).takeIf { options.water },
            )
        } else {
            null
        }

        return SharedCard(
            name = settings.profile.name.trim().take(ShareValidation.MAX_NAME),
            avatar = if (options.photo) profilePhotoStorage.thumbnailBase64() else null,
            sharedAt = Instant.now().epochSecond,
            week = options.week,
            goals = goals,
            glassMl = settings.glassMl.takeIf { options.water },
            days = days,
        )
    }

    fun link(card: SharedCard): String = linkPrefix() + ShareCodec.encode(card, key)

    /** Payload of a link opened from outside (the part after '#'). */
    fun decode(payload: String): ShareCodec.Result = ShareCodec.decode(payload, key)

    companion object {
        /** The data travel after '#': browsers never send that part to the server. */
        fun linkPrefix() = "https://${BuildConfig.SHARE_HOST}$PATH#"

        const val PATH = "/d"
    }
}

/** Entry of a shared day. */
internal fun FoodEntry.toSharedEntry(): SharedEntry {
    val inPieces = servingUnit == ServingUnit.PIECE
    return SharedEntry(
        meal = mealType.name,
        name = name.trim().take(ShareValidation.MAX_ENTRY_NAME),
        kcal = round1(kcal),
        grams = grams?.let(::round1),
        // Pieces go in their own fields, so older versions still read the grams
        unit = servingUnit?.takeUnless { inPieces }?.name,
        servings = servings?.takeUnless { inPieces }?.let(::round1),
        pieces = servings?.takeIf { inPieces }?.let(::round1),
        pieceLabel = servingLabel?.trim()?.take(ShareValidation.MAX_PIECE_LABEL)?.takeIf { inPieces && it.isNotEmpty() },
    )
}

private fun round1(value: Double): Double = (value * 10).roundToLong() / 10.0
