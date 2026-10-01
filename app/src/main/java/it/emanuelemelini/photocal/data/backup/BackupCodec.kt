package it.emanuelemelini.photocal.data.backup

import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.data.db.SavedFood
import it.emanuelemelini.photocal.data.db.ServingUnit
import it.emanuelemelini.photocal.data.db.Source
import it.emanuelemelini.photocal.data.db.WaterIntake
import it.emanuelemelini.photocal.data.db.WeightEntry
import it.emanuelemelini.photocal.data.nutrition.ActivityLevel
import it.emanuelemelini.photocal.data.nutrition.Profile
import it.emanuelemelini.photocal.data.nutrition.Sex
import it.emanuelemelini.photocal.data.nutrition.WeightGoal
import it.emanuelemelini.photocal.data.prefs.Settings
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import it.emanuelemelini.photocal.data.prefs.ThemeMode
import it.emanuelemelini.photocal.data.reminders.ReminderConfig
import it.emanuelemelini.photocal.data.reminders.ReminderType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToLong

/** Backup file as JSON, and the conversions to and from the database rows. */
object BackupCodec {

    const val FORMAT = "photocal-backup"
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    sealed interface Result {
        data class Valid(val backup: Backup) : Result

        /** Not a PhotoCal backup, corrupted or with implausible values. */
        data object Invalid : Result

        /** Made by a newer app version. */
        data object NewerVersion : Result
    }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    fun decode(text: String): Result = try {
        // Format and version first: a newer format may not parse as this one
        val root = json.parseToJsonElement(text).jsonObject
        val format = root["format"]?.jsonPrimitive?.content
        val version = root["version"]?.jsonPrimitive?.int
        when {
            format != FORMAT || version == null -> Result.Invalid
            version > VERSION -> Result.NewerVersion
            else -> json.decodeFromString(Backup.serializer(), text).let { if (BackupValidation.isValid(it)) Result.Valid(it) else Result.Invalid }
        }
    } catch (_: IllegalArgumentException) {
        // Also SerializationException, NumberFormatException and a non-object root
        Result.Invalid
    }

    // Database rows -> backup

    fun FoodEntry.toBackup() = BackupEntry(
        date = date.toEpochDay(),
        meal = mealType.name,
        name = name,
        grams = grams,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        fiberG = fiberG,
        sugarsG = sugarsG,
        saltG = saltG,
        source = source.name,
        photo = photoPath?.let { File(it).name },
        createdAt = createdAt.toEpochMilli(),
        unit = servingUnit?.name,
        servings = servings,
        pieceLabel = servingLabel,
    )

    fun WeightEntry.toBackup() = BackupWeight(date.toEpochDay(), weightKg, createdAt.toEpochMilli())

    fun WaterIntake.toBackup() = BackupWater(date.toEpochDay(), ml, updatedAt.toEpochMilli())

    fun SavedFood.toBackup() = BackupSavedFood(
        name = name,
        barcode = barcode,
        source = source.name,
        kcalPer100 = kcalPer100,
        proteinPer100 = proteinPer100,
        carbsPer100 = carbsPer100,
        fatPer100 = fatPer100,
        fiberPer100 = fiberPer100,
        sugarsPer100 = sugarsPer100,
        saltPer100 = saltPer100,
        grams = grams,
        unit = servingUnit?.name,
        servings = servings,
        pieceGrams = pieceGrams,
        pieceLabel = pieceLabel,
        favorite = favorite,
        useCount = useCount,
        lastUsedAt = lastUsedAt.toEpochMilli(),
    )

    fun Settings.toBackup() = BackupSettings(
        kcalGoal = dailyKcalGoal,
        proteinGoalG = proteinGoalG,
        carbsGoalG = carbsGoalG,
        fatGoalG = fatGoalG,
        waterGoalMl = waterGoalMl,
        glassMl = glassMl,
        name = profile.name,
        sex = profile.sex?.name,
        birthYear = profile.birthYear,
        heightCm = profile.heightCm,
        activity = profile.activity?.name,
        weightGoal = profile.goal.name,
        geminiModel = geminiModel,
        themeMode = themeMode.name,
        dynamicColor = dynamicColor,
        useCrea = useCrea,
        reminders = reminders.values.map { BackupReminder(it.type.name, it.enabled, it.time.hour * 60 + it.time.minute) },
        healthWrite = healthWrite,
        healthAddBurned = healthAddBurned,
    )

    // Backup -> database rows (the values are already validated)

    /** [photoPath] finds the photo on this phone from its file name. */
    fun BackupEntry.toEntry(photoPath: (String) -> String?) = FoodEntry(
        date = LocalDate.ofEpochDay(date),
        mealType = MealType.valueOf(meal),
        name = name,
        grams = grams,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        source = Source.valueOf(source),
        photoPath = photo?.let(photoPath),
        createdAt = Instant.ofEpochMilli(createdAt),
        servingUnit = unit?.let(ServingUnit::valueOf),
        servings = servings,
        servingLabel = pieceLabel,
        fiberG = fiberG,
        sugarsG = sugarsG,
        saltG = saltG,
    )

    fun BackupWeight.toEntry() = WeightEntry(date = LocalDate.ofEpochDay(date), weightKg = weightKg, createdAt = Instant.ofEpochMilli(createdAt))

    fun BackupWater.toEntry() = WaterIntake(date = LocalDate.ofEpochDay(date), ml = ml, updatedAt = Instant.ofEpochMilli(updatedAt))

    fun BackupSavedFood.toEntry() = SavedFood(
        foodKey = SavedFood.keyFor(name, barcode),
        name = name,
        barcode = barcode,
        source = Source.valueOf(source),
        kcalPer100 = kcalPer100,
        proteinPer100 = proteinPer100,
        carbsPer100 = carbsPer100,
        fatPer100 = fatPer100,
        fiberPer100 = fiberPer100,
        sugarsPer100 = sugarsPer100,
        saltPer100 = saltPer100,
        grams = grams,
        servingUnit = unit?.let(ServingUnit::valueOf),
        servings = servings,
        pieceGrams = pieceGrams,
        pieceLabel = pieceLabel,
        favorite = favorite,
        useCount = useCount,
        lastUsedAt = Instant.ofEpochMilli(lastUsedAt),
    )

    /** Settings of the backup over [current]: what the backup doesn't have stays as it is. */
    fun BackupSettings.toSettings(current: Settings) = current.copy(
        dailyKcalGoal = kcalGoal,
        proteinGoalG = proteinGoalG,
        carbsGoalG = carbsGoalG,
        fatGoalG = fatGoalG,
        waterGoalMl = waterGoalMl,
        glassMl = glassMl,
        profile = Profile(
            sex = sex?.let { value -> Sex.entries.find { it.name == value } },
            birthYear = birthYear,
            heightCm = heightCm,
            activity = activity?.let { value -> ActivityLevel.entries.find { it.name == value } },
            goal = weightGoal?.let { value -> WeightGoal.entries.find { it.name == value } } ?: WeightGoal.MAINTAIN,
            name = name,
        ),
        geminiModel = geminiModel?.takeIf { it.isNotBlank() } ?: SettingsRepository.DEFAULT_GEMINI_MODEL,
        themeMode = themeMode?.let { value -> ThemeMode.entries.find { it.name == value } } ?: ThemeMode.SYSTEM,
        dynamicColor = dynamicColor,
        useCrea = useCrea,
        reminders = current.reminders + reminders.mapNotNull { reminder ->
            ReminderType.entries.find { it.name == reminder.type }?.let { type ->
                type to ReminderConfig(type, reminder.enabled, LocalTime.of(reminder.minutes / 60, reminder.minutes % 60))
            }
        },
        healthWrite = healthWrite,
        healthAddBurned = healthAddBurned,
    )

    // Merge

    /** Same food on the same day and meal, with the same kcal (rounded): already in the diary. */
    private fun FoodEntry.mergeKey() = listOf(date, mealType, name.trim().lowercase(), kcal.roundToLong())

    /** Entries of [incoming] not already in [existing], nor twice in [incoming]. */
    fun newEntries(existing: List<FoodEntry>, incoming: List<FoodEntry>): List<FoodEntry> {
        val keys = existing.mapTo(HashSet()) { it.mergeKey() }
        return incoming.filter { keys.add(it.mergeKey()) }
    }

    /** Weigh-ins and water only for the days that have none yet. */
    fun newWeights(existing: List<WeightEntry>, incoming: List<WeightEntry>): List<WeightEntry> {
        val days = existing.mapTo(HashSet()) { it.date }
        return incoming.filter { days.add(it.date) }
    }

    fun newWater(existing: List<WaterIntake>, incoming: List<WaterIntake>): List<WaterIntake> {
        val days = existing.mapTo(HashSet()) { it.date }
        return incoming.filter { days.add(it.date) }
    }

    /** Saved foods not already saved; the existing ones stay, becoming favorites if they were in the backup. */
    fun mergedFoods(existing: List<SavedFood>, incoming: List<SavedFood>): List<SavedFood> {
        val byKey = existing.associateBy { it.foodKey }
        return incoming.distinctBy { it.foodKey }.mapNotNull { food ->
            val current = byKey[food.foodKey]
            when {
                current == null -> food
                food.favorite && !current.favorite -> current.copy(favorite = true)
                else -> null
            }
        }
    }
}

/** Limits of a valid backup: generous for real data, tight enough to restore safely. */
internal object BackupValidation {
    const val MAX_NAME = 200
    const val MAX_ROWS = 200_000

    fun isValid(backup: Backup): Boolean {
        if (backup.entries.size > MAX_ROWS || backup.weights.size > MAX_ROWS || backup.water.size > MAX_ROWS ||
            backup.savedFoods.size > MAX_ROWS
        ) return false
        return backup.entries.all(::isValid) && backup.weights.all(::isValid) && backup.water.all(::isValid) &&
            backup.savedFoods.all(::isValid) && (backup.settings?.let(::isValid) ?: true)
    }

    private fun isValid(entry: BackupEntry): Boolean =
        entry.date in EPOCH_DAY_RANGE && MealType.entries.any { it.name == entry.meal } &&
            Source.entries.any { it.name == entry.source } && validName(entry.name) &&
            entry.kcal in 0.0..20_000.0 && inRange(entry.grams, 0.0..20_000.0) &&
            listOf(entry.proteinG, entry.carbsG, entry.fatG, entry.fiberG, entry.sugarsG, entry.saltG).all { inRange(it, 0.0..5_000.0) } &&
            (entry.unit == null || ServingUnit.entries.any { it.name == entry.unit }) &&
            inRange(entry.servings, 0.0..1_000.0) && (entry.pieceLabel?.length ?: 0) <= MAX_NAME &&
            (entry.photo?.length ?: 0) <= MAX_NAME

    private fun isValid(weight: BackupWeight): Boolean = weight.date in EPOCH_DAY_RANGE && weight.weightKg in 20.0..400.0

    private fun isValid(water: BackupWater): Boolean = water.date in EPOCH_DAY_RANGE && water.ml in 0..50_000

    private fun isValid(food: BackupSavedFood): Boolean =
        validName(food.name) && Source.entries.any { it.name == food.source } &&
            food.kcalPer100 in 0.0..10_000.0 && food.grams in 0.0..20_000.0 &&
            listOf(food.proteinPer100, food.carbsPer100, food.fatPer100, food.fiberPer100, food.sugarsPer100, food.saltPer100)
                .all { inRange(it, 0.0..1_000.0) } &&
            (food.unit == null || ServingUnit.entries.any { it.name == food.unit }) &&
            inRange(food.servings, 0.0..1_000.0) && inRange(food.pieceGrams, 0.0..20_000.0) &&
            (food.barcode?.length ?: 0) <= 20 && (food.pieceLabel?.length ?: 0) <= MAX_NAME && food.useCount >= 0

    private fun isValid(settings: BackupSettings): Boolean =
        settings.kcalGoal in 500..10_000 && listOf(settings.proteinGoalG, settings.carbsGoalG, settings.fatGoalG).all { it == null || it in 0..1_000 } &&
            settings.waterGoalMl in 0..10_000 && settings.glassMl in 50..1_000 && settings.name.length <= MAX_NAME &&
            (settings.birthYear == null || settings.birthYear in 1900..2100) && (settings.heightCm == null || settings.heightCm in 50..300) &&
            settings.reminders.all { it.minutes in 0..<24 * 60 }

    private fun validName(name: String) = name.isNotBlank() && name.length <= MAX_NAME

    /** Also rejects NaN and infinities. */
    private fun inRange(value: Double?, range: ClosedFloatingPointRange<Double>) = value == null || value in range

    /** 2000-01-01 .. 2100-12-31. */
    private val EPOCH_DAY_RANGE = 10_957L..47_846L
}
