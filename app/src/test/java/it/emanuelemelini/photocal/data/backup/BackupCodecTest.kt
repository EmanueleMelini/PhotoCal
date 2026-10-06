package it.emanuelemelini.photocal.data.backup

import it.emanuelemelini.photocal.data.ai.AiProvider
import it.emanuelemelini.photocal.data.backup.BackupCodec.Result
import it.emanuelemelini.photocal.data.backup.BackupCodec.toBackup
import it.emanuelemelini.photocal.data.backup.BackupCodec.toEntry
import it.emanuelemelini.photocal.data.backup.BackupCodec.toSettings
import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.MealType
import it.emanuelemelini.photocal.data.db.SavedFood
import it.emanuelemelini.photocal.data.db.ServingUnit
import it.emanuelemelini.photocal.data.db.Source
import it.emanuelemelini.photocal.data.db.WeightEntry
import it.emanuelemelini.photocal.data.nutrition.ActivityLevel
import it.emanuelemelini.photocal.data.nutrition.Bottle
import it.emanuelemelini.photocal.data.nutrition.Profile
import it.emanuelemelini.photocal.data.prefs.Settings
import it.emanuelemelini.photocal.data.prefs.ThemeMode
import it.emanuelemelini.photocal.data.reminders.ReminderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class BackupCodecTest {

    private val day = LocalDate.of(2026, 9, 30)

    private val biscuits = FoodEntry(
        date = day, mealType = MealType.SNACK, name = "Biscotti", grams = 22.5, kcal = 113.0,
        proteinG = 1.5, carbsG = 15.8, fatG = 4.5, source = Source.BARCODE,
        photoPath = "/data/user/0/it.emanuelemelini.photocal/files/photos/IMG_1.jpg",
        createdAt = Instant.ofEpochMilli(1_790_000_000_000), servingUnit = ServingUnit.PIECE, servings = 3.0,
        servingLabel = "biscotti", fiberG = 0.8, sugarsG = 7.0, saltG = 0.12,
    )

    private val backup = Backup(
        appVersion = "1.4.0",
        exportedAt = 1_790_000_000,
        entries = listOf(biscuits.toBackup()),
        weights = listOf(WeightEntry(date = day, weightKg = 78.4, createdAt = Instant.EPOCH).toBackup()),
        savedFoods = listOf(SavedFood.from(biscuits, "0076808002461", null, Instant.EPOCH)!!.toBackup()),
        settings = Settings(
            dailyKcalGoal = 2200,
            profile = Profile(name = "Mario", activity = ActivityLevel.SEDENTARY),
            themeMode = ThemeMode.PURPLE,
            aiProvider = AiProvider.ANTHROPIC,
            aiApiKeys = mapOf(AiProvider.GEMINI to "secret", AiProvider.ANTHROPIC to "secret-too"),
            aiModels = mapOf(AiProvider.ANTHROPIC to "claude-haiku-4-5"),
            healthAddBurned = true,
        ).toBackup(),
    )

    @Test
    fun roundTrip() {
        val decoded = BackupCodec.decode(BackupCodec.encode(backup))
        assertEquals(Result.Valid(backup), decoded)
    }

    @Test
    fun entriesComeBackWithThePhotoOnlyIfItIsStillThere() {
        val entry = backup.entries.single()
        assertEquals("IMG_1.jpg", entry.photo)
        val restored = entry.toEntry { name -> "/new/photos/$name" }
        assertEquals(biscuits.copy(photoPath = "/new/photos/IMG_1.jpg"), restored)
        assertEquals(null, entry.toEntry { null }.photoPath)
    }

    @Test
    fun theApiKeyIsNeverExported() {
        assertFalse(BackupCodec.encode(backup).contains("secret"))
        // Restored settings keep the key of this phone
        val restored = backup.settings!!.toSettings(Settings(aiApiKeys = mapOf(AiProvider.GEMINI to "mine")))
        assertEquals("mine", restored.aiApiKey(AiProvider.GEMINI))
        assertEquals("", restored.aiApiKey(AiProvider.ANTHROPIC))
        assertEquals(2200, restored.dailyKcalGoal)
        assertEquals(ThemeMode.PURPLE, restored.themeMode)
        assertTrue(restored.healthAddBurned)
    }

    @Test
    fun theChosenAiAndItsModelsAreRestored() {
        val restored = backup.settings!!.toSettings(Settings())
        assertEquals(AiProvider.ANTHROPIC, restored.aiProvider)
        assertEquals("claude-haiku-4-5", restored.aiModel(AiProvider.ANTHROPIC))
        assertEquals(AiProvider.OPENAI.defaultModel, restored.aiModel(AiProvider.OPENAI))
    }

    @Test
    fun backupsBefore150KeepTheGeminiModelAndTheCurrentAi() {
        val old = BackupSettings(kcalGoal = 2000, waterGoalMl = 1600, glassMl = 200, geminiModel = "gemini-3.5-flash-lite")
        val restored = old.toSettings(Settings(aiProvider = AiProvider.OPENAI))
        assertEquals(AiProvider.OPENAI, restored.aiProvider)
        assertEquals("gemini-3.5-flash-lite", restored.aiModel(AiProvider.GEMINI))
    }

    @Test
    fun remindersAreRestored() {
        val settings = Settings().let { it.copy(reminders = it.reminders + (ReminderType.LUNCH to it.reminders.getValue(ReminderType.LUNCH).copy(enabled = true, time = LocalTime.of(12, 45)))) }
        val restored = settings.toBackup().toSettings(Settings())
        assertEquals(LocalTime.of(12, 45), restored.reminders.getValue(ReminderType.LUNCH).time)
        assertTrue(restored.reminders.getValue(ReminderType.LUNCH).enabled)
    }

    @Test
    fun bottleIsRestored() {
        val settings = Settings(bottle = Bottle("Borraccia Stanley", 750))
        assertEquals(Bottle("Borraccia Stanley", 750), settings.toBackup().toSettings(Settings()).bottle)
    }

    @Test
    fun backupWithoutBottleKeepsTheCurrentOne() {
        val current = Settings(bottle = Bottle("", 500))
        assertEquals(current.bottle, Settings().toBackup().toSettings(current).bottle)
    }

    @Test
    fun otherFilesAreRejected() {
        for (text in listOf("", "{}", "[]", "not json", """{"format":"other","version":1}""", """{"format":"photocal-backup"}""")) {
            assertEquals(text, Result.Invalid, BackupCodec.decode(text))
        }
    }

    @Test
    fun newerVersionIsReported() {
        assertEquals(Result.NewerVersion, BackupCodec.decode("""{"format":"photocal-backup","version":2,"future":[1]}"""))
    }

    @Test
    fun implausibleValuesAreRejected() {
        val entry = backup.entries.single()
        val bad = listOf(
            backup.copy(entries = listOf(entry.copy(meal = "BRUNCH"))),
            backup.copy(entries = listOf(entry.copy(kcal = -1.0))),
            backup.copy(entries = listOf(entry.copy(unit = "BUCKET"))),
            backup.copy(entries = listOf(entry.copy(date = 1))),
            backup.copy(entries = listOf(entry.copy(name = " "))),
            backup.copy(weights = listOf(backup.weights.single().copy(weightKg = 5.0))),
            backup.copy(settings = backup.settings!!.copy(glassMl = 5)),
            backup.copy(settings = backup.settings!!.copy(bottleMl = 20_000)),
        )
        for (item in bad) assertEquals(Result.Invalid, BackupCodec.decode(BackupCodec.encode(item)))
        assertEquals(Result.Invalid, BackupCodec.decode(BackupCodec.encode(backup).replace("113.0", "NaN")))
    }

    @Test
    fun mergeSkipsEntriesAlreadyInTheDiary() {
        val sameAgain = biscuits.copy(id = 7, name = " biscotti ", kcal = 113.4, createdAt = Instant.EPOCH)
        val otherMeal = biscuits.copy(mealType = MealType.BREAKFAST)
        val result = BackupCodec.newEntries(existing = listOf(sameAgain), incoming = listOf(biscuits, otherMeal, otherMeal))
        assertEquals(listOf(otherMeal), result)
    }

    @Test
    fun mergeKeepsTheWeightOfDaysAlreadyLogged() {
        val existing = WeightEntry(date = day, weightKg = 80.0, createdAt = Instant.EPOCH)
        val sameDay = WeightEntry(date = day, weightKg = 78.0, createdAt = Instant.EPOCH)
        val nextDay = WeightEntry(date = day.plusDays(1), weightKg = 78.0, createdAt = Instant.EPOCH)
        assertEquals(listOf(nextDay), BackupCodec.newWeights(listOf(existing), listOf(sameDay, nextDay)))
    }

    @Test
    fun mergeAddsNewFoodsAndFavorites() {
        val saved = SavedFood.from(biscuits, null, null, Instant.EPOCH)!!.copy(id = 3)
        val favorite = saved.copy(id = 0, favorite = true)
        val other = SavedFood.from(biscuits.copy(name = "Crackers"), null, null, Instant.EPOCH)!!
        assertEquals(listOf(saved.copy(favorite = true), other), BackupCodec.mergedFoods(listOf(saved), listOf(favorite, other)))
    }
}
