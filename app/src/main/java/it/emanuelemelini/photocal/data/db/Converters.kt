package it.emanuelemelini.photocal.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

class Converters {
    // LocalDate stored as epoch day: sortable and handy for range queries
    @TypeConverter fun localDateToLong(date: LocalDate): Long = date.toEpochDay()
    @TypeConverter fun longToLocalDate(value: Long): LocalDate = LocalDate.ofEpochDay(value)

    @TypeConverter fun instantToLong(instant: Instant): Long = instant.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter fun mealTypeToString(mealType: MealType): String = mealType.name
    @TypeConverter fun stringToMealType(value: String): MealType = MealType.valueOf(value)

    @TypeConverter fun sourceToString(source: Source): String = source.name
    @TypeConverter fun stringToSource(value: String): Source = Source.valueOf(value)

    @TypeConverter fun servingUnitToString(unit: ServingUnit?): String? = unit?.name
    @TypeConverter fun stringToServingUnit(value: String?): ServingUnit? = value?.let(ServingUnit::valueOf)
}
