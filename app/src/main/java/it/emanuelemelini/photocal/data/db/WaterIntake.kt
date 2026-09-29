package it.emanuelemelini.photocal.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Water drunk on a day, in ml: stored as ml (not glasses) so the counts keep their meaning
 * if the glass size is changed later.
 */
@Entity(tableName = "water_intake")
data class WaterIntake(
    @PrimaryKey val date: LocalDate,
    val ml: Int,
    val updatedAt: Instant,
)
