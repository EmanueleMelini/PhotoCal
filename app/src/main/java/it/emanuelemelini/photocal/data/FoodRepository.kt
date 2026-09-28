package it.emanuelemelini.photocal.data

import it.emanuelemelini.photocal.data.db.DayTotals
import it.emanuelemelini.photocal.data.db.FoodDao
import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.Totals
import it.emanuelemelini.photocal.data.photo.PhotoStorage
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import kotlin.time.Duration.Companion.hours

class FoodRepository(
    private val dao: FoodDao,
    private val photoStorage: PhotoStorage,
) {

    fun observeEntries(date: LocalDate): Flow<List<FoodEntry>> = dao.observeByDate(date)

    fun observeTotals(date: LocalDate): Flow<Totals> = dao.observeTotalsByDate(date)

    fun observeTotalsBetween(from: LocalDate, to: LocalDate): Flow<List<DayTotals>> =
        dao.observeTotalsBetween(from, to)

    suspend fun get(id: Long): FoodEntry? = dao.getById(id)

    suspend fun add(entry: FoodEntry): Long = dao.insert(entry)

    suspend fun addAll(entries: List<FoodEntry>) = dao.insertAll(entries)

    suspend fun update(entry: FoodEntry) = dao.update(entry)

    /** Deletes the entry and, if it was the last one using it, its photo too. */
    suspend fun delete(entry: FoodEntry) {
        dao.delete(entry)
        entry.photoPath?.let { path ->
            if (dao.countByPhotoPath(path) == 0) photoStorage.delete(path)
        }
    }

    /** The one-hour margin avoids deleting the photo of an analysis still in progress. */
    suspend fun deleteOrphanPhotos() {
        photoStorage.deleteOrphans(dao.getPhotoPaths().toSet(), olderThanMillis = 1.hours.inWholeMilliseconds)
    }
}
