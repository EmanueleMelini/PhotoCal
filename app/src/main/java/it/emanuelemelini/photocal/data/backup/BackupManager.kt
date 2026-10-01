package it.emanuelemelini.photocal.data.backup

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import androidx.room.withTransaction
import it.emanuelemelini.photocal.AppLocale
import it.emanuelemelini.photocal.BuildConfig
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.backup.BackupCodec.toBackup
import it.emanuelemelini.photocal.data.backup.BackupCodec.toEntry
import it.emanuelemelini.photocal.data.backup.BackupCodec.toSettings
import it.emanuelemelini.photocal.data.db.AppDatabase
import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.ServingUnit
import it.emanuelemelini.photocal.data.db.Source
import it.emanuelemelini.photocal.data.photo.PhotoStorage
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

/** What was restored, for the confirmation message. */
data class RestoreResult(val entries: Int, val weights: Int, val water: Int)

/** Export and restore of the backup file and CSV export of the diary, through file URIs (SAF). */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val photoStorage: PhotoStorage,
    /** Called after a restore: reminders, widget... follow the new data. */
    private val onRestored: suspend () -> Unit,
) {

    /** Writes the backup to [uri]. */
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = Backup(
            appVersion = BuildConfig.VERSION_NAME,
            exportedAt = Instant.now().epochSecond,
            entries = database.foodDao().getAll().map { it.toBackup() },
            weights = database.weightDao().getAll().map { it.toBackup() },
            water = database.waterDao().getAll().map { it.toBackup() },
            savedFoods = database.savedFoodDao().getAll().map { it.toBackup() },
            settings = settingsRepository.settings.first().toBackup(),
        )
        write(uri, BackupCodec.encode(backup))
    }

    /** Reads and checks a backup, without changing anything. */
    suspend fun read(uri: Uri): BackupCodec.Result = withContext(Dispatchers.IO) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // A real backup is a few MB at most: bigger files are not ours
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (out.size() <= MAX_BYTES) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    out.write(buffer, 0, count)
                }
                if (out.size() > MAX_BYTES) null else out.toString(Charsets.UTF_8.name())
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
        text?.let(BackupCodec::decode) ?: BackupCodec.Result.Invalid
    }

    suspend fun restore(backup: Backup, mode: RestoreMode): RestoreResult = withContext(Dispatchers.IO) {
        val entries = backup.entries.map { it.toEntry(photoStorage::existingPath) }
        val weights = backup.weights.map { it.toEntry() }
        val water = backup.water.map { it.toEntry() }
        val foods = backup.savedFoods.map { it.toEntry() }
        val result = database.withTransaction {
            val foodDao = database.foodDao()
            val weightDao = database.weightDao()
            val waterDao = database.waterDao()
            val savedFoodDao = database.savedFoodDao()
            when (mode) {
                RestoreMode.REPLACE -> {
                    foodDao.deleteAll()
                    weightDao.deleteAll()
                    waterDao.deleteAll()
                    savedFoodDao.deleteAll()
                    foodDao.insertAll(entries)
                    weights.forEach { weightDao.upsert(it) }
                    water.forEach { waterDao.upsert(it) }
                    savedFoodDao.insertAll(foods)
                    RestoreResult(entries.size, weights.size, water.size)
                }
                RestoreMode.MERGE -> {
                    val newEntries = BackupCodec.newEntries(foodDao.getAll(), entries)
                    val newWeights = BackupCodec.newWeights(weightDao.getAll(), weights)
                    val newWater = BackupCodec.newWater(waterDao.getAll(), water)
                    foodDao.insertAll(newEntries)
                    newWeights.forEach { weightDao.upsert(it) }
                    newWater.forEach { waterDao.upsert(it) }
                    savedFoodDao.insertAll(BackupCodec.mergedFoods(savedFoodDao.getAll(), foods))
                    RestoreResult(newEntries.size, newWeights.size, newWater.size)
                }
            }
        }
        // Settings only when replacing: merging keeps the ones of this phone
        if (mode == RestoreMode.REPLACE) {
            backup.settings?.let { settingsRepository.restore(it.toSettings(settingsRepository.settings.first())) }
        }
        onRestored()
        result
    }

    /** Writes the whole diary to [uri] as CSV, in the app language. */
    suspend fun exportCsv(uri: Uri) = withContext(Dispatchers.IO) {
        val res = AppLocale.localizedContext(context).resources
        val csv = DiaryCsv(res.configuration.locales[0])
        val rows = database.foodDao().getAll().map { entry ->
            val (quantity, unit) = entry.csvQuantity(res)
            listOf(
                entry.date.toString(),
                res.getString(entry.mealType.labelRes),
                entry.name,
                csv.number(quantity),
                unit,
                csv.number(entry.grams),
                csv.number(entry.kcal),
                csv.number(entry.proteinG),
                csv.number(entry.carbsG),
                csv.number(entry.fatG),
                csv.number(entry.fiberG),
                csv.number(entry.sugarsG),
                csv.number(entry.saltG),
                res.getString(entry.source.labelRes()),
            )
        }
        write(uri, csv.write(res.getStringArray(R.array.csv_columns).toList(), rows))
    }

    /** Quantity as typed (e.g. 3 biscotti), or the grams. */
    private fun FoodEntry.csvQuantity(res: Resources): Pair<Double?, String> {
        val unit = servingUnit
        val count = servings
        if (unit == null || unit == ServingUnit.GRAMS || count == null) return grams to "g"
        val plural = if (count == 1.0) 1 else 2
        val name = if (unit == ServingUnit.PIECE) servingLabel?.takeIf { plural != 1 } ?: res.getQuantityString(unit.nameRes, plural)
        else res.getQuantityString(unit.nameRes, plural)
        return count to name
    }

    private fun Source.labelRes(): Int = when (this) {
        Source.PHOTO -> R.string.csv_source_photo
        Source.MANUAL -> R.string.csv_source_manual
        Source.BARCODE -> R.string.csv_source_barcode
    }

    private fun write(uri: Uri, text: String) {
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    companion object {
        private const val MAX_BYTES = 32 * 1024 * 1024

        fun backupFileName(today: LocalDate = LocalDate.now()) = "photocal-backup-$today.json"

        fun csvFileName(today: LocalDate = LocalDate.now()) = "photocal-diary-$today.csv"
    }
}
