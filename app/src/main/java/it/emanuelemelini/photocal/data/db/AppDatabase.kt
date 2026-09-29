package it.emanuelemelini.photocal.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schemas are exported to app/schemas. From 1.0.0 the app is installed with real data:
 * every schema change needs a migration.
 */
@Database(entities = [FoodEntry::class, WeightEntry::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao

    abstract fun weightDao(): WeightDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "photocal.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2 (app 1.1.0): weight log. SQL copied from the exported schema 2.json. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `weight_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_weight_entries_date` ON `weight_entries` (`date`)")
            }
        }
    }
}
