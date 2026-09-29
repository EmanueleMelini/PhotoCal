package it.emanuelemelini.photocal.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** A weigh-in. At most one per day: a new one on the same day replaces it. */
@Entity(tableName = "weight_entries", indices = [Index(value = ["date"], unique = true)])
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val weightKg: Double,
    val createdAt: Instant,
)
