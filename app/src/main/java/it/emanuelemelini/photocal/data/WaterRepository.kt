package it.emanuelemelini.photocal.data

import it.emanuelemelini.photocal.data.db.WaterDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

class WaterRepository(
    private val dao: WaterDao,
    /** Called after every change, e.g. to refresh the home screen widget. */
    private val onChanged: suspend () -> Unit = {},
) {

    fun observeMl(date: LocalDate): Flow<Int> = dao.observeMl(date).map { it ?: 0 }

    suspend fun mlFor(date: LocalDate): Int = dao.getMl(date) ?: 0

    /** ml of each day in the range that has water logged. */
    suspend fun mlBetween(from: LocalDate, to: LocalDate): Map<LocalDate, Int> =
        dao.getBetween(from, to).associate { it.date to it.ml }

    suspend fun addGlass(date: LocalDate, glassMl: Int) = change(date, glassMl)

    suspend fun removeGlass(date: LocalDate, glassMl: Int) = change(date, -glassMl)

    private suspend fun change(date: LocalDate, deltaMl: Int) {
        dao.add(date, deltaMl, Instant.now())
        onChanged()
    }
}
