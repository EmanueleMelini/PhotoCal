package it.emanuelemelini.photocal.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface WeightDao {

    /** Replaces the weigh-in of the same day, if any (unique index on date). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntry)

    @Delete
    suspend fun delete(entry: WeightEntry)

    @Query("SELECT * FROM weight_entries ORDER BY date DESC")
    fun observeAll(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entries WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entries ORDER BY date DESC LIMIT 1")
    fun observeLatest(): Flow<WeightEntry?>

    @Query("SELECT * FROM weight_entries ORDER BY date")
    suspend fun getAll(): List<WeightEntry>

    @Query("DELETE FROM weight_entries")
    suspend fun deleteAll()
}
