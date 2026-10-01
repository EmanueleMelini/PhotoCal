package it.emanuelemelini.photocal.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedFoodDao {

    /** Replaces the food with the same key (unique index). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: SavedFood)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<SavedFood>)

    @Query("SELECT * FROM saved_foods WHERE id = :id")
    suspend fun getById(id: Long): SavedFood?

    @Query("SELECT * FROM saved_foods WHERE foodKey = :key")
    suspend fun getByKey(key: String): SavedFood?

    @Query("SELECT * FROM saved_foods WHERE favorite = 1 ORDER BY name COLLATE NOCASE")
    fun observeFavorites(): Flow<List<SavedFood>>

    @Query("SELECT * FROM saved_foods WHERE favorite = 0 ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SavedFood>>

    /** [pattern] is a LIKE pattern with '\' as escape character. */
    @Query(
        """
        SELECT * FROM saved_foods WHERE name LIKE :pattern ESCAPE '\'
        ORDER BY favorite DESC, useCount DESC, lastUsedAt DESC LIMIT :limit
        """
    )
    fun observeSearch(pattern: String, limit: Int): Flow<List<SavedFood>>

    @Query("UPDATE saved_foods SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM saved_foods WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM saved_foods ORDER BY id")
    suspend fun getAll(): List<SavedFood>

    @Query("DELETE FROM saved_foods")
    suspend fun deleteAll()
}
