package it.emanuelemelini.photocal.data

import it.emanuelemelini.photocal.data.db.WeightDao
import it.emanuelemelini.photocal.data.db.WeightEntry
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

class WeightRepository(
    private val dao: WeightDao,
    /** Called after the weight of a day is recorded (or deleted: null), e.g. for Health Connect. */
    private val onChanged: suspend (LocalDate, Double?) -> Unit = { _, _ -> },
) {

    fun observeAll(): Flow<List<WeightEntry>> = dao.observeAll()

    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<WeightEntry>> = dao.observeBetween(from, to)

    fun observeLatest(): Flow<WeightEntry?> = dao.observeLatest()

    /** Records the weight of [date], replacing the one already recorded that day. */
    suspend fun record(date: LocalDate, weightKg: Double) {
        dao.upsert(WeightEntry(date = date, weightKg = weightKg, createdAt = Instant.now()))
        onChanged(date, weightKg)
    }

    suspend fun delete(entry: WeightEntry) {
        dao.delete(entry)
        onChanged(entry.date, null)
    }
}
