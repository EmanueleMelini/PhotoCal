package it.emanuelemelini.photocal.data.health

import android.content.Context
import android.content.Intent
import android.os.RemoteException
import android.util.Log
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import it.emanuelemelini.photocal.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Steps and active calories of a day, null when Health Connect has none. */
data class DayActivity(val steps: Long?, val activeKcal: Double?)

enum class HealthAvailability {
    AVAILABLE,

    /** Android 13 or lower without the Health Connect app (or with an old one). */
    NEEDS_INSTALL,

    NOT_SUPPORTED,
}

/**
 * Health Connect ("Salute di Android"): reads steps and active calories, writes weight and
 * water. Every record written by PhotoCal has a stable client id (one per day and type), so
 * writing again updates it instead of adding a duplicate.
 */
class HealthConnect(private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class),
    )

    private val client: HealthConnectClient? by lazy {
        if (availability() == HealthAvailability.AVAILABLE) HealthConnectClient.getOrCreate(context) else null
    }

    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_INSTALL
        else -> HealthAvailability.NOT_SUPPORTED
    }

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    /** Play Store page of the Health Connect app (Android 13 and lower). */
    fun installIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        "market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun grantedPermissions(): Set<String> =
        safely(emptySet()) { client?.permissionController?.getGrantedPermissions().orEmpty() }

    suspend fun revokeAll() {
        safely(Unit) { client?.permissionController?.revokeAllPermissions() }
    }

    /** Steps and active calories of [date] (local day); null without permission or on errors. */
    suspend fun activityOf(date: LocalDate): DayActivity? {
        val client = client ?: return null
        val granted = grantedPermissions()
        val readSteps = HealthPermission.getReadPermission(StepsRecord::class) in granted
        val readKcal = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted
        if (!readSteps && !readKcal) return null
        val zone = ZoneId.systemDefault()
        val range = TimeRangeFilter.between(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant())
        val metrics = buildSet {
            if (readSteps) add(StepsRecord.COUNT_TOTAL)
            if (readKcal) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        }
        return safely(null) {
            val result = client.aggregate(AggregateRequest(metrics, range))
            DayActivity(
                steps = result[StepsRecord.COUNT_TOTAL],
                activeKcal = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
            )
        }
    }

    /** Weight of [date]; null deletes the one PhotoCal wrote. */
    suspend fun writeWeight(date: LocalDate, weightKg: Double?) {
        val client = client ?: return
        if (HealthPermission.getWritePermission(WeightRecord::class) !in grantedPermissions()) return
        val id = "photocal-weight-$date"
        safely(Unit) {
            if (weightKg == null) {
                client.deleteRecords(WeightRecord::class, emptyList(), listOf(id))
            } else {
                // One weigh-in per day in PhotoCal: at noon, as it has no time
                val time = date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault())
                client.insertRecords(
                    listOf(WeightRecord(time.toInstant(), time.offset, Mass.kilograms(weightKg), Metadata.manualEntry(id, System.currentTimeMillis())))
                )
            }
        }
    }

    /** Water of [date] as one record for the whole day; 0 deletes it. */
    suspend fun writeWater(date: LocalDate, ml: Int) {
        val client = client ?: return
        if (HealthPermission.getWritePermission(HydrationRecord::class) !in grantedPermissions()) return
        val id = "photocal-water-$date"
        safely(Unit) {
            if (ml <= 0) {
                client.deleteRecords(HydrationRecord::class, emptyList(), listOf(id))
            } else {
                val zone = ZoneId.systemDefault()
                val start = date.atStartOfDay(zone)
                // Today the record ends now: Health Connect doesn't accept records in the future
                val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1), Instant.now())
                    .coerceAtLeast(start.toInstant().plusSeconds(60))
                client.insertRecords(
                    listOf(
                        HydrationRecord(
                            start.toInstant(), start.offset, end, zone.rules.getOffset(end),
                            Volume.milliliters(ml.toDouble()), Metadata.manualEntry(id, System.currentTimeMillis()),
                        )
                    )
                )
            }
        }
    }

    /** Health Connect errors (revoked permission, service unavailable, rate limit) never reach the UI. */
    private suspend fun <T> safely(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: SecurityException) {
        Log.w(TAG, "Health Connect permission missing", e)
        fallback
    } catch (e: IllegalStateException) {
        Log.w(TAG, "Health Connect unavailable", e)
        fallback
    } catch (e: IOException) {
        Log.w(TAG, "Health Connect error", e)
        fallback
    } catch (e: RemoteException) {
        Log.w(TAG, "Health Connect error", e)
        fallback
    }

    private companion object {
        const val TAG = "HealthConnect"
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    }
}

/** Writes weight and water to Health Connect when the user asked for it, in the background. */
class HealthSync(
    private val healthConnect: HealthConnect,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    fun water(date: LocalDate, ml: Int) = whenEnabled { healthConnect.writeWater(date, ml) }

    fun weight(date: LocalDate, weightKg: Double?) = whenEnabled { healthConnect.writeWeight(date, weightKg) }

    private fun whenEnabled(block: suspend () -> Unit) {
        scope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.healthConnected && settings.healthWrite) block()
        }
    }
}
