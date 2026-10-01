package it.emanuelemelini.photocal.data

import it.emanuelemelini.photocal.data.db.FoodEntry
import it.emanuelemelini.photocal.data.db.SavedFood
import it.emanuelemelini.photocal.data.db.SavedFoodDao
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Recent and favorite foods, saved from the diary entries. */
class SavedFoodRepository(private val dao: SavedFoodDao) {

    fun observeFavorites(): Flow<List<SavedFood>> = dao.observeFavorites()

    fun observeRecent(limit: Int = RECENT_LIMIT): Flow<List<SavedFood>> = dao.observeRecent(limit)

    /** Foods whose name contains [query], favorites first. */
    fun observeSearch(query: String, limit: Int): Flow<List<SavedFood>> =
        dao.observeSearch("%" + query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%", limit)

    suspend fun get(id: Long): SavedFood? = dao.getById(id)

    suspend fun getByBarcode(barcode: String): SavedFood? = dao.getByKey(SavedFood.keyFor("", barcode))

    /** Saves the food of a new entry (or updates it), for the recent foods. */
    suspend fun recordUse(entry: FoodEntry, barcode: String? = null) {
        val existing = dao.getByKey(SavedFood.keyFor(entry.name, barcode))
        SavedFood.from(entry, barcode, existing, Instant.now())?.let { dao.upsert(it) }
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun delete(id: Long) = dao.delete(id)

    companion object {
        const val RECENT_LIMIT = 50
    }
}
