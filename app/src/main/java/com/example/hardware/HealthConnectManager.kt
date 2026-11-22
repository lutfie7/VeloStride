package com.example.hardware

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.core.Formatters
import com.example.data.model.HealthConnectSnapshot
import com.example.data.model.HealthConnectState
import com.example.data.model.ImportedExerciseSession
import com.example.data.model.SportType
import com.example.data.model.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

class HealthConnectManager(private val context: Context) {

    private val appContext = context.applicationContext

    private val _snapshot = MutableStateFlow(HealthConnectSnapshot())
    val snapshot: StateFlow<HealthConnectSnapshot> = _snapshot.asStateFlow()

    companion object {
        private const val TAG = "HealthConnectMgr"
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        val CORE_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        )

        val REQUIRED_PERMISSIONS: Set<String> = CORE_PERMISSIONS + setOf(
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(SpeedRecord::class),
            HealthPermission.getReadPermission(ElevationGainedRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(Vo2MaxRecord::class),
            "android.permission.health.READ_EXERCISE_ROUTES"
        )
    }

    suspend fun checkAvailabilityAndPermissions(): HealthConnectState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            _snapshot.update {
                it.copy(
                    state = HealthConnectState.UNAVAILABLE,
                    lastSyncMessage = "Health Connect requires Android 9 (API 28) or higher."
                )
            }
            return HealthConnectState.UNAVAILABLE
        }

        val status = HealthConnectClient.getSdkStatus(appContext, PROVIDER_PACKAGE)
        val mapped = when (status) {
            HealthConnectClient.SDK_UNAVAILABLE -> {
                _snapshot.update {
                    it.copy(
                        state = HealthConnectState.UNAVAILABLE,
                        lastSyncMessage = "Health Connect not installed on this device. Direct BLE GATT & Barometer remain active."
                    )
                }
                HealthConnectState.UNAVAILABLE
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                _snapshot.update {
                    it.copy(
                        state = HealthConnectState.UPDATE_REQUIRED,
                        lastSyncMessage = "Health Connect update required via Google Play."
                    )
                }
                HealthConnectState.UPDATE_REQUIRED
            }
            else -> {
                val client = HealthConnectClient.getOrCreate(appContext)
                val granted = try {
                    client.permissionController.getGrantedPermissions()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to query Health Connect permissions", e)
                    emptySet()
                }
                if (granted.containsAll(CORE_PERMISSIONS)) {
                    _snapshot.update { it.copy(state = HealthConnectState.AVAILABLE_GRANTED) }
                    HealthConnectState.AVAILABLE_GRANTED
                } else {
                    _snapshot.update {
                        it.copy(
                            state = HealthConnectState.AVAILABLE_NEEDS_PERMISSION,
                            lastSyncMessage = "Grant Health Connect access to import all HUAWEI Band 11 Pro metrics & workouts."
                        )
                    }
                    HealthConnectState.AVAILABLE_NEEDS_PERMISSION
                }
            }
        }
        return mapped
    }

    suspend fun syncHealthData(maxHrBaseline: Int = 192): Result<HealthConnectSnapshot> {
        val currentState = checkAvailabilityAndPermissions()
        if (currentState != HealthConnectState.AVAILABLE_GRANTED) {
            return Result.failure(IllegalStateException(_snapshot.value.lastSyncMessage))
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return Result.failure(IllegalStateException("Requires Android 8.0+"))
        }

        return try {
            val client = HealthConnectClient.getOrCreate(appContext)
            val granted = client.permissionController.getGrantedPermissions()
            val zone = ZoneId.systemDefault()
            val startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val now = Instant.now()
            val sleepStartWindow = startOfToday.minus(Duration.ofHours(16))
            val workoutLookback = now.minus(Duration.ofDays(14))

            // 1. Steps today
            val stepsResponse = client.readRecords(
                ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(startOfToday, now))
            )
            val totalSteps = stepsResponse.records.sumOf { it.count }.toInt()
            var originPkg = stepsResponse.records.firstOrNull()?.metadata?.dataOrigin?.packageName.orEmpty()

            // 2. Daily Distance & Elevation (if permitted)
            val distanceTodayMeters = if (granted.contains(HealthPermission.getReadPermission(DistanceRecord::class))) {
                runCatching {
                    client.readRecords(
                        ReadRecordsRequest(DistanceRecord::class, TimeRangeFilter.between(startOfToday, now))
                    ).records.sumOf { it.distance.inMeters }
                }.getOrDefault(0.0)
            } else 0.0

            val elevationTodayMeters = if (granted.contains(HealthPermission.getReadPermission(ElevationGainedRecord::class))) {
                runCatching {
                    client.readRecords(
                        ReadRecordsRequest(ElevationGainedRecord::class, TimeRangeFilter.between(startOfToday, now))
                    ).records.sumOf { it.elevation.inMeters }
                }.getOrDefault(0.0)
            } else 0.0

            // 3. Active & Total Calories today
            val activeKcal = runCatching {
                client.readRecords(
                    ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, TimeRangeFilter.between(startOfToday, now))
                ).records.sumOf { it.energy.inKilocalories }.toInt()
            }.getOrDefault(0)

            val totalKcal = runCatching {
                client.readRecords(
                    ReadRecordsRequest(TotalCaloriesBurnedRecord::class, TimeRangeFilter.between(startOfToday, now))
                ).records.sumOf { it.energy.inKilocalories }.toInt()
            }.getOrDefault(0).coerceAtLeast(activeKcal)

            // 4. Heart Rate & Resting HR
            val hrResp = client.readRecords(
                ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(startOfToday, now))
            )
            val allSamples = hrResp.records.flatMap { it.samples }
            val latestHr = allSamples.maxByOrNull { it.time }?.beatsPerMinute?.toInt() ?: 0
            val minHrSample = allSamples.minByOrNull { it.beatsPerMinute }?.beatsPerMinute?.toInt() ?: 0
            if (originPkg.isBlank()) {
                originPkg = hrResp.records.firstOrNull()?.metadata?.dataOrigin?.packageName.orEmpty()
            }

            val restingHrExplicit = if (granted.contains(HealthPermission.getReadPermission(RestingHeartRateRecord::class))) {
                runCatching {
                    client.readRecords(
                        ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.between(sleepStartWindow, now))
                    ).records.maxByOrNull { it.time }?.beatsPerMinute?.toInt() ?: 0
                }.getOrDefault(0)
            } else 0
            val restingHr = restingHrExplicit.takeIf { it > 0 } ?: minHrSample

            // 5. HRV RMSSD
            val hrvMs = if (granted.contains(HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class))) {
                runCatching {
                    client.readRecords(
                        ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, TimeRangeFilter.between(sleepStartWindow, now))
                    ).records.maxByOrNull { it.time }?.heartRateVariabilityMillis ?: 0.0
                }.getOrDefault(0.0)
            } else 0.0

            // 6. SpO2 Blood Oxygen Saturation (HUAWEI Band 11 Pro vital metric)
            val spo2Pct = if (granted.contains(HealthPermission.getReadPermission(OxygenSaturationRecord::class))) {
                runCatching {
                    client.readRecords(
                        ReadRecordsRequest(OxygenSaturationRecord::class, TimeRangeFilter.between(sleepStartWindow, now))
                    ).records.maxByOrNull { it.time }?.percentage?.value ?: 0.0
                }.getOrDefault(0.0)
            } else 0.0

            // 7. Native Band VO2 Max (if Huawei Health synced VO2 Max)
            val vo2FromBand = if (granted.contains(HealthPermission.getReadPermission(Vo2MaxRecord::class))) {
                runCatching {
                    client.readRecords(
                        ReadRecordsRequest(Vo2MaxRecord::class, TimeRangeFilter.between(workoutLookback, now))
                    ).records.maxByOrNull { it.time }?.vo2MillilitersPerMinuteKilogram ?: 0.0
                }.getOrDefault(0.0)
            } else 0.0

            // 8. Sleep Session & Stages (Awake / Light / Deep / REM)
            val sleepResp = client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(sleepStartWindow, now))
            )
            val latestSleep = sleepResp.records.maxByOrNull { it.endTime }
            var sleepTotalMin = 0
            var deepMin = 0
            var lightMin = 0
            var remMin = 0
            var awakeMin = 0

            if (latestSleep != null) {
                sleepTotalMin = Duration.between(latestSleep.startTime, latestSleep.endTime).toMinutes().toInt()
                    .coerceAtLeast(0)
                latestSleep.stages.forEach { stage ->
                    val mins = Duration.between(stage.startTime, stage.endTime).toMinutes().toInt().coerceAtLeast(0)
                    when (stage.stage) {
                        SleepSessionRecord.STAGE_TYPE_DEEP -> deepMin += mins
                        SleepSessionRecord.STAGE_TYPE_LIGHT,
                        SleepSessionRecord.STAGE_TYPE_SLEEPING -> lightMin += mins
                        SleepSessionRecord.STAGE_TYPE_REM -> remMin += mins
                        SleepSessionRecord.STAGE_TYPE_AWAKE,
                        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> awakeMin += mins
                    }
                }
                if (deepMin + lightMin + remMin == 0 && sleepTotalMin > 0) {
                    lightMin = sleepTotalMin
                }
            }

            // 9. Recent Exercise Sessions (Workouts recorded on HUAWEI Band 11 Pro!)
            val exerciseResp = runCatching {
                client.readRecords(
                    ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(workoutLookback, now))
                ).records
            }.getOrDefault(emptyList())

            val importedSessions = exerciseResp
                .sortedByDescending { it.startTime }
                .take(25)
                .map { session ->
                    extractExerciseSession(client, granted, session, maxHrBaseline)
                }

            val sourceLabel = if (originPkg.contains("huawei", true)) "Huawei Health / Band 11 Pro" else "Health Connect"
            val updated = HealthConnectSnapshot(
                state = HealthConnectState.AVAILABLE_GRANTED,
                stepsToday = totalSteps,
                distanceMetersToday = distanceTodayMeters,
                elevationGainedToday = elevationTodayMeters,
                activeCaloriesToday = activeKcal,
                totalCaloriesToday = totalKcal,
                latestHeartRateBpm = latestHr,
                restingHeartRateBpm = restingHr,
                hrvRmssdMs = (hrvMs * 10.0).roundToInt() / 10.0,
                spo2Percent = (spo2Pct * 10.0).roundToInt() / 10.0,
                vo2MaxFromBand = (vo2FromBand * 10.0).roundToInt() / 10.0,
                sleepTotalMinutes = sleepTotalMin,
                sleepDeepMinutes = deepMin,
                sleepLightMinutes = lightMin,
                sleepRemMinutes = remMin,
                sleepAwakeMinutes = awakeMin,
                importedExercises = importedSessions,
                dataOriginPackage = originPkg.ifBlank { "Health Connect" },
                lastSyncMessage = if (totalSteps > 0 || sleepTotalMin > 0 || latestHr > 0 || importedSessions.isNotEmpty()) {
                    "Synced $sourceLabel (${importedSessions.size} workouts, $totalSteps steps)"
                } else {
                    "Health Connect ready (no Huawei Health records in window yet)"
                }
            )
            _snapshot.value = updated
            Result.success(updated)
        } catch (e: Exception) {
            Log.e(TAG, "Health Connect sync failed", e)
            _snapshot.update { it.copy(lastSyncMessage = "Sync error: ${e.localizedMessage}") }
            Result.failure(e)
        }
    }

    private suspend fun extractExerciseSession(
        client: HealthConnectClient,
        granted: Set<String>,
        session: ExerciseSessionRecord,
        maxHrBaseline: Int,
    ): ImportedExerciseSession {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return ImportedExerciseSession(
                externalId = session.metadata.id,
                title = session.title ?: "Huawei Workout",
                sportType = SportType.WORKOUT,
                startTimeMs = 0L,
                durationSeconds = 0L,
                distanceMeters = 0.0,
                elevationGainMeters = 0.0,
                calories = 0,
                avgHeartRate = 0,
                maxHeartRate = 0,
                avgSpeedKmh = 0.0,
                avgCadence = 0,
                hrZoneSeconds = listOf(0L, 0L, 0L, 0L, 0L),
                routePoints = emptyList(),
                sourcePackage = session.metadata.dataOrigin.packageName
            )
        }

        val range = TimeRangeFilter.between(session.startTime, session.endTime)
        val durationSec = Duration.between(session.startTime, session.endTime).seconds.coerceAtLeast(1L)
        val sport = mapExerciseType(session.exerciseType)

        val distMeters = if (granted.contains(HealthPermission.getReadPermission(DistanceRecord::class))) {
            runCatching {
                client.readRecords(ReadRecordsRequest(DistanceRecord::class, range))
                    .records.sumOf { it.distance.inMeters }
            }.getOrDefault(0.0)
        } else 0.0

        val eleMeters = if (granted.contains(HealthPermission.getReadPermission(ElevationGainedRecord::class))) {
            runCatching {
                client.readRecords(ReadRecordsRequest(ElevationGainedRecord::class, range))
                    .records.sumOf { it.elevation.inMeters }
            }.getOrDefault(0.0)
        } else 0.0

        val kcal = runCatching {
            client.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, range))
                .records.sumOf { it.energy.inKilocalories }.toInt()
        }.getOrDefault(0).let { total ->
            if (total > 0) total else runCatching {
                client.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, range))
                    .records.sumOf { it.energy.inKilocalories }.toInt()
            }.getOrDefault(0)
        }

        val hrSamples = runCatching {
            client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range))
                .records.flatMap { it.samples }.sortedBy { it.time }
        }.getOrDefault(emptyList())

        val avgHr = if (hrSamples.isNotEmpty()) hrSamples.map { it.beatsPerMinute }.average().roundToInt() else 0
        val maxHr = hrSamples.maxOfOrNull { it.beatsPerMinute }?.toInt() ?: 0

        val zones = MutableList(5) { 0L }
        if (hrSamples.isNotEmpty()) {
            val secPerSample = (durationSec.toDouble() / hrSamples.size).coerceAtLeast(1.0)
            hrSamples.forEach { sample ->
                val zIdx = (Formatters.computeHrZone(sample.beatsPerMinute.toInt(), maxHrBaseline) - 1).coerceIn(0, 4)
                zones[zIdx] = zones[zIdx] + secPerSample.roundToInt()
            }
        }

        val speedKmh = if (distMeters > 10.0) {
            (distMeters / 1000.0) / (durationSec / 3600.0)
        } else if (granted.contains(HealthPermission.getReadPermission(SpeedRecord::class))) {
            val spSamples = runCatching {
                client.readRecords(ReadRecordsRequest(SpeedRecord::class, range))
                    .records.flatMap { it.samples }
            }.getOrDefault(emptyList())
            if (spSamples.isNotEmpty()) spSamples.map { it.speed.inMetersPerSecond * 3.6 }.average() else 0.0
        } else 0.0

        val routePoints: List<TrackPoint> = when (val routeRes = session.exerciseRouteResult) {
            is ExerciseRouteResult.Data -> {
                val locList = routeRes.exerciseRoute.route
                val step = (locList.size / 200.0).coerceAtLeast(1.0)
                val sampled = if (locList.size <= 200) locList else {
                    (0 until 200).map { i -> locList[(i * step).toInt().coerceAtMost(locList.lastIndex)] }
                }
                sampled.map { pt ->
                    // Pair nearest HR sample to GPS point
                    val nearestHr = hrSamples.minByOrNull {
                        kotlin.math.abs(Duration.between(it.time, pt.time).seconds)
                    }?.beatsPerMinute?.toInt() ?: avgHr
                    TrackPoint(
                        lat = pt.latitude,
                        lng = pt.longitude,
                        ele = pt.altitude?.inMeters ?: 0.0,
                        hr = nearestHr
                    )
                }
            }
            else -> emptyList()
        }

        val finalDist = if (distMeters > 0.0) distMeters else computeRouteDistance(routePoints)
        val pkg = session.metadata.dataOrigin.packageName
        val defaultTitle = if (pkg.contains("huawei", true)) {
            "Huawei Band ${sport.label}"
        } else {
            "${sport.label} (Health Connect)"
        }

        return ImportedExerciseSession(
            externalId = session.metadata.id.ifBlank { "hc_${session.startTime.epochSecond}" },
            title = session.title?.takeIf { it.isNotBlank() } ?: defaultTitle,
            sportType = sport,
            startTimeMs = session.startTime.toEpochMilli(),
            durationSeconds = durationSec,
            distanceMeters = finalDist,
            elevationGainMeters = eleMeters,
            calories = kcal,
            avgHeartRate = avgHr,
            maxHeartRate = maxHr,
            avgSpeedKmh = (speedKmh * 10.0).roundToInt() / 10.0,
            avgCadence = 0,
            hrZoneSeconds = zones,
            routePoints = routePoints,
            sourcePackage = pkg
        )
    }

    private fun computeRouteDistance(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        return (1 until points.size).sumOf { idx ->
            Formatters.haversineMeters(
                points[idx - 1].lat,
                points[idx - 1].lng,
                points[idx].lat,
                points[idx].lng
            )
        }
    }

    private fun mapExerciseType(type: Int): SportType = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> SportType.RUN
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> SportType.HIKE
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> SportType.WALK
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> SportType.CYCLE
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> SportType.SWIM
        ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> SportType.BADMINTON
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
        ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING -> SportType.YOGA
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> SportType.ROWING
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> SportType.ELLIPTICAL
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP,
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> SportType.WORKOUT
        else -> SportType.WORKOUT
    }
}
