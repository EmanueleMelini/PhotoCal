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
@Database(entities = [FoodEntry::class, WeightEntry::class, WaterIntake::class, SavedFood::class], version = 4, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao

    abstract fun weightDao(): WeightDao

    abstract fun waterDao(): WaterDao

    abstract fun savedFoodDao(): SavedFoodDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "photocal.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

        /**
         * v4 (app 1.4.0): name of the pieces, fiber, sugars and salt of the entries; saved foods
         * (recent and favorites). SQL copied from the exported schema 4.json.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("`servingLabel` TEXT", "`fiberG` REAL", "`sugarsG` REAL", "`saltG` REAL").forEach {
                    db.execSQL("ALTER TABLE `food_entries` ADD COLUMN $it")
                }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_foods` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`foodKey` TEXT NOT NULL, `name` TEXT NOT NULL, `barcode` TEXT, `source` TEXT NOT NULL, " +
                        "`kcalPer100` REAL NOT NULL, `proteinPer100` REAL, `carbsPer100` REAL, `fatPer100` REAL, " +
                        "`fiberPer100` REAL, `sugarsPer100` REAL, `saltPer100` REAL, `grams` REAL NOT NULL, " +
                        "`servingUnit` TEXT, `servings` REAL, `pieceGrams` REAL, `pieceLabel` TEXT, " +
                        "`favorite` INTEGER NOT NULL, `useCount` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_foods_foodKey` ON `saved_foods` (`foodKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_foods_lastUsedAt` ON `saved_foods` (`lastUsedAt`)")
            }
        }
    }
}
