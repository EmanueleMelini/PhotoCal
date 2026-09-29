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
@Database(entities = [FoodEntry::class, WeightEntry::class, WaterIntake::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao

    abstract fun weightDao(): WeightDao

    abstract fun waterDao(): WaterDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "photocal.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

        /** v3 (app 1.2.0): water counter. SQL copied from the exported schema 3.json. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `water_intake` (`date` INTEGER NOT NULL, `ml` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))"
                )
            }
        }
    }
}
