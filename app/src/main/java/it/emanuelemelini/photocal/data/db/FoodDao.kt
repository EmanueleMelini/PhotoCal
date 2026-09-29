package it.emanuelemelini.photocal.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface FoodDao {

    @Insert
    suspend fun insert(entry: FoodEntry): Long

    @Insert
    suspend fun insertAll(entries: List<FoodEntry>)

    @Update
    suspend fun update(entry: FoodEntry)

    @Delete
    suspend fun delete(entry: FoodEntry)

    @Query("SELECT * FROM food_entries WHERE id = :id")
    suspend fun getById(id: Long): FoodEntry?

    @Query("SELECT COUNT(*) FROM food_entries WHERE photoPath = :path")
    suspend fun countByPhotoPath(path: String): Int

    @Query("SELECT DISTINCT photoPath FROM food_entries WHERE photoPath IS NOT NULL")
    suspend fun getPhotoPaths(): List<String>

    @Query("SELECT COUNT(*) FROM food_entries WHERE date = :date AND mealType = :meal")
    suspend fun countByDateAndMeal(date: LocalDate, meal: MealType): Int

    @Query("SELECT * FROM food_entries WHERE date = :date ORDER BY createdAt")
    fun observeByDate(date: LocalDate): Flow<List<FoodEntry>>

    @Query(
        """
        SELECT COALESCE(SUM(kcal), 0) AS kcal,
               COALESCE(SUM(proteinG), 0) AS proteinG,
               COALESCE(SUM(carbsG), 0) AS carbsG,
               COALESCE(SUM(fatG), 0) AS fatG
        FROM food_entries WHERE date = :date
        """
    )
    fun observeTotalsByDate(date: LocalDate): Flow<Totals>

    @Query(
        """
        SELECT COALESCE(SUM(kcal), 0) AS kcal,
               COALESCE(SUM(proteinG), 0) AS proteinG,
               COALESCE(SUM(carbsG), 0) AS carbsG,
               COALESCE(SUM(fatG), 0) AS fatG
        FROM food_entries WHERE date = :date
        """
    )
    suspend fun getTotalsByDate(date: LocalDate): Totals

    /** Only days with at least one entry; the chart fills in the empty days. */
    @Query(
        """
        SELECT date,
               SUM(kcal) AS kcal,
               COALESCE(SUM(proteinG), 0) AS proteinG,
               COALESCE(SUM(carbsG), 0) AS carbsG,
               COALESCE(SUM(fatG), 0) AS fatG
        FROM food_entries WHERE date BETWEEN :from AND :to
        GROUP BY date ORDER BY date
        """
    )
    fun observeTotalsBetween(from: LocalDate, to: LocalDate): Flow<List<DayTotals>>

    @Query(
        """
        SELECT date,
               SUM(kcal) AS kcal,
               COALESCE(SUM(proteinG), 0) AS proteinG,
               COALESCE(SUM(carbsG), 0) AS carbsG,
               COALESCE(SUM(fatG), 0) AS fatG
        FROM food_entries WHERE date BETWEEN :from AND :to
        GROUP BY date ORDER BY date
        """
    )
    suspend fun getTotalsBetween(from: LocalDate, to: LocalDate): List<DayTotals>
}
