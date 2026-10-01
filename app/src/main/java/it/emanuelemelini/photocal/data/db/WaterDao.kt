package it.emanuelemelini.photocal.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import kotlin.math.max

@Dao
interface WaterDao {

    @Query("SELECT ml FROM water_intake WHERE date = :date")
    fun observeMl(date: LocalDate): Flow<Int?>

    @Query("SELECT ml FROM water_intake WHERE date = :date")
    suspend fun getMl(date: LocalDate): Int?

    @Query("SELECT * FROM water_intake WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun getBetween(from: LocalDate, to: LocalDate): List<WaterIntake>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(intake: WaterIntake)

    @Query("SELECT * FROM water_intake ORDER BY date")
    suspend fun getAll(): List<WaterIntake>

    @Query("DELETE FROM water_intake")
    suspend fun deleteAll()

    /** Adds [deltaMl] (negative to remove) to the day, never going below zero. */
    @Transaction
    suspend fun add(date: LocalDate, deltaMl: Int, now: Instant) {
        val current = getMl(date) ?: 0
        upsert(WaterIntake(date = date, ml = max(0, current + deltaMl), updatedAt = now))
    }
}
