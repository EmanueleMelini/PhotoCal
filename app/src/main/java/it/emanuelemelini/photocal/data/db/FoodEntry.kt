package it.emanuelemelini.photocal.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "food_entries", indices = [Index("date")])
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val mealType: MealType,
    val name: String,
    val grams: Double?,
    val kcal: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val source: Source,
    val photoPath: String?,
    val createdAt: Instant,
    /** Unit chosen when entering the food (null = grams entered directly). */
    val servingUnit: ServingUnit? = null,
    /** Number of servings in [servingUnit], e.g. 2 wine glasses. */
    val servings: Double? = null,
)
