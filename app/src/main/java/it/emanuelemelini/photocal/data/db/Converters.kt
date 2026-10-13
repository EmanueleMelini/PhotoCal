package it.emanuelemelini.photocal.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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

    @TypeConverter fun nutritionCategoryToString(category: NutritionCategory): String = category.name
    @TypeConverter fun stringToNutritionCategory(value: String): NutritionCategory = NutritionCategory.valueOf(value)

    @TypeConverter fun nutrientsToString(nutrients: List<NutrientComponent>): String = json.encodeToString(nutrientsSerializer, nutrients)
    @TypeConverter fun stringToNutrients(value: String): List<NutrientComponent> = json.decodeFromString(nutrientsSerializer, value)

    // Additive codes contain no commas; null (ingredients unknown) differs from an empty list
    @TypeConverter fun codesToString(codes: List<String>?): String? = codes?.joinToString(",")
    @TypeConverter fun stringToCodes(value: String?): List<String>? = value?.split(',')?.filter { it.isNotEmpty() }

    // Explicit types: KSP (Room) runs before the serialization plugin generates serializer()
    private companion object {
        val json: Json = Json { ignoreUnknownKeys = true }
        val nutrientsSerializer: KSerializer<List<NutrientComponent>> = ListSerializer(NutrientComponent.serializer())
    }
}
