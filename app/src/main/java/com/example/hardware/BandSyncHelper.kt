package com.example.hardware

import com.example.core.Formatters
import com.example.core.GpxSerializer
import com.example.core.PerformanceEngine
import com.example.data.model.AthleteProfile
import com.example.data.model.DailyHealthLog
import com.example.data.model.HealthConnectSnapshot
import com.example.data.model.ImportedExerciseSession
import com.example.data.model.SplitKm
import com.example.data.model.TrackPoint
import com.example.data.model.WorkoutActivity
import com.example.data.repository.ElevationRepository
import com.example.data.repository.VeloStrideRepository
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class BandImportBatchResult(
    val newlyImportedCount: Int,
    val latestImportedTitle: String?,
    val latestImportedDistanceMeters: Double,
    val latestImportedElevationMeters: Double,
)

object BandSyncHelper {

    suspend fun persistImportedBandWorkouts(
        repository: VeloStrideRepository,
        elevationRepository: ElevationRepository,
        userId: String,
        profile: AthleteProfile,
        sessions: List<ImportedExerciseSession>,
        existingExternalIds: Set<String>,
    ): BandImportBatchResult {
        var newlyImported = 0
        var latestTitle: String? = null
        var latestDist = 0.0
        var latestEle = 0.0

        for (session in sessions) {
            if (session.externalId in existingExternalIds) continue

            val enrichedEle = elevationRepository.enrichTrackElevation(
                rawPoints = session.routePoints,
                existingGainMeters = session.elevationGainMeters
            )
            val movingSec = session.durationSeconds.coerceAtLeast(1L)
            val avgPace = if (session.distanceMeters >= 50.0) {
                (movingSec / (session.distanceMeters / 1000.0)).roundToInt().coerceIn(60, 3600)
            } else 0
            val effort = PerformanceEngine.computeRelativeEffort(
                movingSeconds = movingSec,
                avgHr = session.avgHeartRate,
                maxHr = profile.maxHr,
                restingHr = profile.restingHr,
                perceivedExertion = 6,
                hrZoneSeconds = session.hrZoneSeconds
            )
            val deviceLabel = if (session.sourcePackage.contains("huawei", true)) {
                "Wearable Device"
            } else {
                "Wearable / Health Connect"
            }
            val storedPoints = GpxSerializer.downsampleTrackPoints(enrichedEle.points, 250)
            val splits = buildKmSplitsFromPoints(
                points = enrichedEle.points,
                totalDistanceMeters = session.distanceMeters,
                totalMovingSec = movingSec,
                fallbackAvgHr = session.avgHeartRate
            )

            val sessionTimestamp = session.startTimeMs
                .takeIf { it > 0L && it <= System.currentTimeMillis() - 15_000L }
                ?.let { Timestamp(Date(it)) }

            val workout = WorkoutActivity(
                userId = userId,
                title = session.title,
                sportType = session.sportType.name,
                distanceMeters = session.distanceMeters,
                movingTimeSeconds = movingSec,
                elapsedTimeSeconds = movingSec,
                elevationGainMeters = enrichedEle.elevationGainMeters,
                avgPaceSecPerKm = avgPace,
                avgSpeedKmh = session.avgSpeedKmh,
                avgHeartRate = session.avgHeartRate,
                maxHeartRate = session.maxHeartRate,
                avgCadence = session.avgCadence,
                calories = session.calories,
                relativeEffort = effort,
                perceivedExertion = 6,
                hrZoneSeconds = session.hrZoneSeconds,
                routePoints = storedPoints.map { it.toMap() },
                splits = splits.take(100).map { it.toMap() },
                deviceName = deviceLabel,
                elevationSource = enrichedEle.sourceLabel,
                externalId = session.externalId,
                notes = "Synced from smart band via Health Connect (${session.sourcePackage.substringAfterLast('.').ifBlank { "wearable" }})",
                createdAt = sessionTimestamp
            )

            if (repository.saveWorkout(userId, workout).isSuccess) {
                newlyImported++
                if (latestTitle == null) {
                    latestTitle = workout.title
                    latestDist = workout.distanceMeters
                    latestEle = workout.elevationGainMeters
                }
            }
        }

        return BandImportBatchResult(
            newlyImportedCount = newlyImported,
            latestImportedTitle = latestTitle,
            latestImportedDistanceMeters = latestDist,
            latestImportedElevationMeters = latestEle
        )
    }

    fun buildKmSplitsFromPoints(
        points: List<TrackPoint>,
        totalDistanceMeters: Double,
        totalMovingSec: Long,
        fallbackAvgHr: Int,
    ): List<SplitKm> {
        if (totalDistanceMeters < 200.0) return emptyList()
        if (points.size < 2) {
            val wholeKm = (totalDistanceMeters / 1000.0).toInt().coerceIn(1, 50)
            val avgPace = (totalMovingSec / (totalDistanceMeters / 1000.0)).roundToInt().coerceIn(60, 3600)
            return (1..wholeKm).map { km ->
                SplitKm(km = km, paceSec = avgPace, eleDiff = 0.0, avgHr = fallbackAvgHr)
            }
        }
        val splits = mutableListOf<SplitKm>()
        var cumDistM = 0.0
        var splitNetEle = 0.0
        val hrBucket = mutableListOf<Int>()
        var nextKmThreshold = 1000.0
        var kmIdx = 1
        val avgPace = (totalMovingSec / (totalDistanceMeters / 1000.0).coerceAtLeast(0.2))
            .roundToInt()
            .coerceIn(60, 3600)

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            cumDistM += Formatters.haversineMeters(prev.lat, prev.lng, curr.lat, curr.lng)
            splitNetEle += (curr.ele - prev.ele)
            if (curr.hr > 0) hrBucket.add(curr.hr)

            if (cumDistM >= nextKmThreshold && splits.size < 99) {
                splits.add(
                    SplitKm(
                        km = kmIdx++,
                        paceSec = avgPace,
                        eleDiff = (splitNetEle * 10.0).roundToInt() / 10.0,
                        avgHr = if (hrBucket.isNotEmpty()) hrBucket.average().roundToInt() else fallbackAvgHr
                    )
                )
                splitNetEle = 0.0
                hrBucket.clear()
                nextKmThreshold += 1000.0
            }
        }
        if (splits.isEmpty() && cumDistM >= 200.0) {
            splits.add(
                SplitKm(
                    km = 1,
                    paceSec = avgPace,
                    eleDiff = (splitNetEle * 10.0).roundToInt() / 10.0,
                    avgHr = if (hrBucket.isNotEmpty()) hrBucket.average().roundToInt() else fallbackAvgHr
                )
            )
        }
        return splits
    }

    fun buildDailyHealthLog(
        userId: String,
        hc: HealthConnectSnapshot,
        restingHrFallback: Int,
        vo2MaxFallback: Double,
        recoveryScore: Int,
        bleSteps: Int = 0,
        bleCalories: Int = 0,
        bleHr: Int = 0,
        bleHrv: Double = 0.0,
        bleSpo2: Double = 0.0,
        bleDeviceName: String? = null,
    ): DailyHealthLog {
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val sleepTotal = hc.sleepTotalMinutes
        val sleepScore = if (sleepTotal > 0) {
            val deepPct = hc.sleepDeepMinutes.toDouble() / sleepTotal
            (((sleepTotal / 480.0) * 75.0) + (deepPct * 80.0)).roundToInt().coerceIn(35, 99)
        } else 0
        val source = when {
            hc.dataOriginPackage.contains("huawei", true) && bleDeviceName != null ->
                "$bleDeviceName + Huawei Health"
            bleDeviceName != null -> bleDeviceName
            hc.dataOriginPackage.contains("huawei", true) -> "Huawei Health / Wearable Device"
            else -> "Health Connect"
        }
        return DailyHealthLog(
            userId = userId,
            dateKey = todayKey,
            steps = hc.stepsToday + bleSteps,
            distanceMetersToday = hc.distanceMetersToday,
            activeCalories = hc.activeCaloriesToday + bleCalories,
            totalCalories = (hc.totalCaloriesToday + bleCalories).coerceAtLeast(hc.activeCaloriesToday + bleCalories),
            restingHr = hc.restingHeartRateBpm.takeIf { it > 0 } ?: restingHrFallback,
            latestHr = bleHr.takeIf { it > 0 } ?: hc.latestHeartRateBpm,
            hrvRmssdMs = bleHrv.takeIf { it > 0.0 } ?: hc.hrvRmssdMs,
            spo2Percent = hc.spo2Percent.takeIf { it > 0.0 } ?: bleSpo2,
            sleepTotalMinutes = sleepTotal,
            sleepDeepMinutes = hc.sleepDeepMinutes,
            sleepLightMinutes = hc.sleepLightMinutes,
            sleepRemMinutes = hc.sleepRemMinutes,
            sleepAwakeMinutes = hc.sleepAwakeMinutes,
            sleepScore = sleepScore,
            vo2MaxEstimate = hc.vo2MaxFromBand.takeIf { it > 10.0 } ?: vo2MaxFallback,
            recoveryScore = recoveryScore,
            sourceDevice = source
        )
    }
}
