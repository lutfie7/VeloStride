package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class SportType(
    val label: String,
    val isPaceBased: Boolean,
    val defaultSpeedKmh: Double,
    val isPrimary: Boolean = false,
) {
    RUN("Run", true, 10.5, true),
    HIKE("Hike", true, 4.5, true),
    WALK("Walk", true, 5.2, true),
    TRAIL_RUN("Trail Run", true, 8.2, true),
    CYCLE("Cycle", false, 26.0, true),
    WORKOUT("Workout", true, 0.0, true),
    GRAVEL_RIDE("Gravel Ride", false, 21.0),
    MTB("Mountain Bike", false, 18.0),
    SWIM("Swim", true, 3.0),
    BADMINTON("Badminton", true, 0.0),
    YOGA("Yoga", true, 0.0),
    ROWING("Rowing", false, 12.0),
    ELLIPTICAL("Elliptical", true, 8.5);

    companion object {
        fun fromRaw(raw: String): SportType = when (raw) {
            "RIDE" -> CYCLE
            "INDOOR_CARDIO" -> WORKOUT
            else -> entries.firstOrNull { it.name == raw } ?: RUN
        }

        val primarySports: List<SportType>
            get() = entries.filter { it.isPrimary }
    }
}

@Keep
@IgnoreExtraProperties
data class AthleteProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Athlete",
    val photoUrl: String = "",
    val birthDateIso: String = "",
    val gender: String = "",
    val heightCm: Double = 0.0,
    val weightKg: Double = 0.0,
    val weeklyDistanceGoalKm: Double = 40.0,
    val maxHr: Int = 192,
    val restingHr: Int = 54,
    val ftpWatts: Int = 230,
    val totalDistanceMeters: Double = 0.0,
    val totalWorkouts: Int = 0,
    val preferredSport: String = SportType.RUN.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun ageYears(referenceTimeMs: Long = System.currentTimeMillis()): Int? {
        val parts = birthDateIso.trim().split("-")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (year !in 1920..2025 || month !in 1..12 || day !in 1..31) return null

        val nowCal = java.util.Calendar.getInstance().apply { timeInMillis = referenceTimeMs }
        var age = nowCal.get(java.util.Calendar.YEAR) - year
        val currentMonth = nowCal.get(java.util.Calendar.MONTH) + 1
        val currentDay = nowCal.get(java.util.Calendar.DAY_OF_MONTH)
        if (currentMonth < month || (currentMonth == month && currentDay < day)) {
            age--
        }
        return age.takeIf { it in 5..110 }
    }

    fun bmi(): Double? {
        if (heightCm !in 80.0..280.0 || weightKg !in 20.0..400.0) return null
        val heightM = heightCm / 100.0
        val raw = weightKg / (heightM * heightM)
        return kotlin.math.round(raw * 10.0) / 10.0
    }

    fun bmiCategory(): String? = bmi()?.let { value ->
        when {
            value < 18.5 -> "Underweight"
            value < 25.0 -> "Optimal"
            value < 30.0 -> "Overweight"
            else -> "High"
        }
    }

    fun estimatedMaxHrByAge(): Int? =
        ageYears()?.let { age -> kotlin.math.round(208.0 - 0.7 * age).toInt().coerceIn(130, 225) }

    fun hasBiometrics(): Boolean =
        ageYears() != null || gender.isNotBlank() || heightCm > 0.0 || weightKg > 0.0
}

@Keep
@IgnoreExtraProperties
data class TrackPoint(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val ele: Double = 0.0,
    val hr: Int = 0,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "lat" to lat,
        "lng" to lng,
        "ele" to ele,
        "hr" to hr
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): TrackPoint = TrackPoint(
            lat = (map["lat"] as? Number)?.toDouble() ?: 0.0,
            lng = (map["lng"] as? Number)?.toDouble() ?: 0.0,
            ele = (map["ele"] as? Number)?.toDouble() ?: 0.0,
            hr = (map["hr"] as? Number)?.toInt() ?: 0
        )
    }
}

@Keep
@IgnoreExtraProperties
data class SplitKm(
    val km: Int = 1,
    val paceSec: Int = 0,
    val eleDiff: Double = 0.0,
    val avgHr: Int = 0,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "km" to km,
        "paceSec" to paceSec,
        "eleDiff" to eleDiff,
        "avgHr" to avgHr
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): SplitKm = SplitKm(
            km = (map["km"] as? Number)?.toInt() ?: 1,
            paceSec = (map["paceSec"] as? Number)?.toInt() ?: 0,
            eleDiff = (map["eleDiff"] as? Number)?.toDouble() ?: 0.0,
            avgHr = (map["avgHr"] as? Number)?.toInt() ?: 0
        )
    }
}

@Keep
@IgnoreExtraProperties
data class WorkoutActivity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val sportType: String = SportType.RUN.name,
    val distanceMeters: Double = 0.0,
    val movingTimeSeconds: Long = 0L,
    val elapsedTimeSeconds: Long = 0L,
    val elevationGainMeters: Double = 0.0,
    val avgPaceSecPerKm: Int = 0,
    val avgSpeedKmh: Double = 0.0,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val avgCadence: Int = 0,
    val calories: Int = 0,
    val relativeEffort: Int = 0,
    val perceivedExertion: Int = 5,
    val kudosCount: Int = 0,
    val kudosGiven: Boolean = false,
    val privateActivity: Boolean = false,
    val prCount: Int = 0,
    val hrZoneSeconds: List<Long> = listOf(0L, 0L, 0L, 0L, 0L),
    val routePoints: List<Map<String, Any>> = emptyList(),
    val splits: List<Map<String, Any>> = emptyList(),
    val gearId: String? = null,
    val gearName: String? = null,
    val deviceName: String? = null,
    val elevationSource: String? = null,
    val externalId: String? = null,
    val aiInsight: String? = null,
    val notes: String? = null,
    val mediaUris: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val parsedSport: SportType
        get() = SportType.fromRaw(sportType)

    val decodedPoints: List<TrackPoint>
        get() = routePoints.map { TrackPoint.fromMap(it) }

    val decodedSplits: List<SplitKm>
        get() = splits.map { SplitKm.fromMap(it) }
}

@Keep
@IgnoreExtraProperties
data class SavedRoute(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val sportType: String = SportType.RUN.name,
    val surfaceType: String = "Paved",
    val distanceMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val estimatedMinutes: Int = 0,
    val waypoints: List<Map<String, Any>> = emptyList(),
    val starred: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val decodedWaypoints: List<TrackPoint>
        get() = waypoints.map { TrackPoint.fromMap(it) }
}

enum class GearCategory(
    val label: String,
    val defaultMaxKm: Double,
    val defaultModelName: String,
    val isShoes: Boolean,
    val isBike: Boolean = false,
) {
    RUNNING_SHOES("Running Shoes", 650.0, "Daily Trainer", true),
    TRAIL_RUN_SHOES("Trail Run Shoes", 600.0, "Trail Runner", true),
    HIKING_SHOES("Hiking Shoes", 800.0, "Trail Hiker", true),
    WALKING_SHOES("Walking Shoes", 700.0, "Walking Sneaker", true),
    TRACK_SPIKES("Track Spikes", 300.0, "Track Spike", true),
    ROAD_BIKE("Road Bike", 5000.0, "Road Bike", false, isBike = true),
    GRAVEL_BIKE("Gravel Bike", 4500.0, "Gravel Bike", false, isBike = true),
    MTB_BIKE("Mountain Bike", 3500.0, "Mountain Bike", false, isBike = true),
    WEARABLE_SENSOR("Wearable / Sensor", 10000.0, "Wearable Sensor", false);

    companion object {
        fun fromRaw(raw: String): GearCategory = when (raw.trim().uppercase()) {
            "SHOES" -> RUNNING_SHOES
            "BIKE" -> ROAD_BIKE
            else -> entries.firstOrNull { it.name == raw.trim().uppercase() } ?: RUNNING_SHOES
        }
    }
}

@Keep
@IgnoreExtraProperties
data class GearItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val brand: String = "",
    val gearType: String = GearCategory.RUNNING_SHOES.name,
    val distanceMeters: Double = 0.0,
    val maxDistanceMeters: Double = 650000.0,
    val primaryGear: Boolean = false,
    val retired: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val parsedCategory: GearCategory
        get() = GearCategory.fromRaw(gearType)
}

@Keep
@IgnoreExtraProperties
data class PairedWearable(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val macAddress: String = "",
    val manufacturer: String = "",
    val deviceCategory: String = "Smartband",
    val batteryPercent: Int = -1,
    val supportsHr: Boolean = false,
    val supportsRsc: Boolean = false,
    val huaweiNative: Boolean = false,
    val lastSyncedLabel: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class SegmentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val sportType: String = SportType.RUN.name,
    val distanceMeters: Double = 0.0,
    val avgGradePercent: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val prTimeSeconds: Int = 0,
    val komTimeSeconds: Int = 0,
    val attemptCount: Int = 1,
    val starred: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ChallengeGoal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val sportType: String = SportType.RUN.name,
    val targetValue: Double = 100.0,
    val currentValue: Double = 0.0,
    val unit: String = "km",
    val periodLabel: String = "Monthly",
    val joined: Boolean = true,
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class DailyHealthLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val steps: Int = 0,
    val distanceMetersToday: Double = 0.0,
    val activeCalories: Int = 0,
    val totalCalories: Int = 0,
    val restingHr: Int = 0,
    val latestHr: Int = 0,
    val hrvRmssdMs: Double = 0.0,
    val spo2Percent: Double = 0.0,
    val sleepTotalMinutes: Int = 0,
    val sleepDeepMinutes: Int = 0,
    val sleepLightMinutes: Int = 0,
    val sleepRemMinutes: Int = 0,
    val sleepAwakeMinutes: Int = 0,
    val sleepScore: Int = 0,
    val vo2MaxEstimate: Double = 0.0,
    val recoveryScore: Int = 0,
    val sourceDevice: String = "Wearable Device",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

// Analytics & Hardware Live State Models (UI / Domain)

data class FitnessLoadPoint(
    val dayLabel: String,
    val fitnessCtl: Double,
    val fatigueAtl: Double,
    val formTsb: Double,
    val relativeEffort: Int,
    val vo2Max: Double,
)

data class PersonalRecordItem(
    val category: String,
    val valueLabel: String,
    val dateLabel: String,
    val workoutTitle: String,
)

data class PerformanceAnalytics(
    val vo2Max: Double,
    val vo2MaxTrendDelta: Double,
    val vo2MaxClassification: String,
    val raiScore: Int = 0,
    val raiClassification: String = "Building",
    val raiAerobicComponent: Int = 0,
    val raiLoadComponent: Int = 0,
    val raiRecoveryComponent: Int = 0,
    val raiProfileCalibrated: Boolean = false,
    val fitnessCtl: Double,
    val fatigueAtl: Double,
    val formTsb: Double,
    val weeklyRelativeEffort: Int,
    val acuteChronicRatio: Double,
    val loadStatusLabel: String,
    val recoveryScore: Int,
    val recoveryStatusLabel: String,
    val recommendedRecoveryHours: Int,
    val hrvRmssdMs: Double,
    val sleepMinutes: Int,
    val sleepScore: Int,
    val spo2Percent: Double = 0.0,
    val stepsToday: Int = 0,
    val restingHr: Int = 0,
    val activeCalories: Int = 0,
    val trainingSeries: List<FitnessLoadPoint>,
    val personalRecords: List<PersonalRecordItem>,
)

enum class BleConnectionStatus {
    UNAVAILABLE,
    BLUETOOTH_OFF,
    PERMISSION_REQUIRED,
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

data class BleDeviceCandidate(
    val name: String,
    val macAddress: String,
    val rssi: Int,
    val isHuaweiDevice: Boolean,
    val supportsHeartRate: Boolean,
    val supportsRsc: Boolean,
    val manufacturerName: String,
    val advertisedServices: List<String>,
    val isBonded: Boolean = false,
    val isLiveScan: Boolean = false,
    val deviceTypeLabel: String = "",
)

data class BleTelemetryState(
    val status: BleConnectionStatus = BleConnectionStatus.DISCONNECTED,
    val statusMessage: String = "Ready to scan for Wearable Device or BLE sensors",
    val isScanning: Boolean = false,
    val autoReconnectEnabled: Boolean = true,
    val showAllBleDevices: Boolean = false,
    val discoveredDevices: List<BleDeviceCandidate> = emptyList(),
    val connectedDeviceName: String? = null,
    val connectedMacAddress: String? = null,
    val isHuaweiNative: Boolean = false,
    val liveHeartRateBpm: Int = 0,
    val hrvRmssdMs: Double = 0.0,
    val liveSpo2Percent: Double = 0.0,
    val sensorContactDetected: Boolean? = null,
    val liveCadenceSpm: Int = 0,
    val liveSpeedKmh: Double = 0.0,
    val sessionBleSteps: Int = 0,
    val sessionBleCalories: Int = 0,
    val batteryPercent: Int = -1,
    val deviceManufacturer: String = "",
    val deviceModel: String = "",
    val firmwareVersion: String = "",
    val discoveredServices: List<String> = emptyList(),
    val lastPacketTimestampMs: Long = 0L,
)

enum class HealthConnectState {
    CHECKING,
    AVAILABLE_GRANTED,
    AVAILABLE_NEEDS_PERMISSION,
    UPDATE_REQUIRED,
    UNAVAILABLE
}

data class ImportedExerciseSession(
    val externalId: String,
    val title: String,
    val sportType: SportType,
    val startTimeMs: Long,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val calories: Int,
    val avgHeartRate: Int,
    val maxHeartRate: Int,
    val avgSpeedKmh: Double,
    val avgCadence: Int,
    val hrZoneSeconds: List<Long>,
    val routePoints: List<TrackPoint>,
    val sourcePackage: String,
)

data class HealthConnectSnapshot(
    val state: HealthConnectState = HealthConnectState.CHECKING,
    val stepsToday: Int = 0,
    val distanceMetersToday: Double = 0.0,
    val elevationGainedToday: Double = 0.0,
    val activeCaloriesToday: Int = 0,
    val totalCaloriesToday: Int = 0,
    val latestHeartRateBpm: Int = 0,
    val restingHeartRateBpm: Int = 0,
    val hrvRmssdMs: Double = 0.0,
    val spo2Percent: Double = 0.0,
    val vo2MaxFromBand: Double = 0.0,
    val sleepTotalMinutes: Int = 0,
    val sleepDeepMinutes: Int = 0,
    val sleepLightMinutes: Int = 0,
    val sleepRemMinutes: Int = 0,
    val sleepAwakeMinutes: Int = 0,
    val importedExercises: List<ImportedExerciseSession> = emptyList(),
    val dataOriginPackage: String = "",
    val missingPermissionsCount: Int = 0,
    val huaweiHealthInstalled: Boolean = false,
    val lastSyncMessage: String = "Tap Sync Health Connect to pull Wearable Device & Health Connect records",
)

enum class WorkoutSortOrder(val label: String) {
    NEWEST("Newest"),
    LONGEST_DISTANCE("Distance"),
    LONGEST_DURATION("Duration"),
    MOST_ELEVATION("Elevation")
}

data class WorkoutSessionSummary(
    val id: String,
    val title: String,
    val sportType: SportType,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val elevationGainMeters: Double,
    val elevationSource: String,
    val avgPaceSecPerKm: Int,
    val avgSpeedKmh: Double,
    val avgHeartRate: Int,
    val relativeEffort: Int,
    val prCount: Int,
    val kudosCount: Int,
    val kudosGiven: Boolean,
    val deviceName: String?,
    val gearName: String?,
    val hasAiInsight: Boolean,
    val mediaCount: Int = 0,
    val privateActivity: Boolean = false,
    val routePoints: List<TrackPoint>,
    val timestampMs: Long,
    val sourceActivity: WorkoutActivity,
) {
    val distanceKm: Double get() = distanceMeters / 1000.0
}

fun WorkoutActivity.toSessionSummary(): WorkoutSessionSummary = WorkoutSessionSummary(
    id = id,
    title = title,
    sportType = parsedSport,
    distanceMeters = distanceMeters,
    durationSeconds = movingTimeSeconds.coerceAtLeast(elapsedTimeSeconds.coerceAtLeast(0L)).let {
        if (movingTimeSeconds > 0L) movingTimeSeconds else it
    },
    elevationGainMeters = elevationGainMeters,
    elevationSource = elevationSource ?: if (decodedPoints.size >= 2) "GPS" else "Manual",
    avgPaceSecPerKm = avgPaceSecPerKm,
    avgSpeedKmh = avgSpeedKmh,
    avgHeartRate = avgHeartRate,
    relativeEffort = relativeEffort,
    prCount = prCount,
    kudosCount = kudosCount,
    kudosGiven = kudosGiven,
    deviceName = deviceName,
    gearName = gearName,
    hasAiInsight = !aiInsight.isNullOrBlank(),
    mediaCount = mediaUris.size,
    privateActivity = privateActivity,
    routePoints = decodedPoints,
    timestampMs = createdAt?.toDate()?.time ?: 0L,
    sourceActivity = this
)

data class WorkoutHistoryStats(
    val sessionCount: Int = 0,
    val totalDistanceMeters: Double = 0.0,
    val totalDurationSeconds: Long = 0L,
    val totalElevationGainMeters: Double = 0.0,
)

fun List<WorkoutActivity>.computeHistoryStats(): WorkoutHistoryStats = WorkoutHistoryStats(
    sessionCount = size,
    totalDistanceMeters = sumOf { it.distanceMeters },
    totalDurationSeconds = sumOf { if (it.movingTimeSeconds > 0L) it.movingTimeSeconds else it.elapsedTimeSeconds },
    totalElevationGainMeters = sumOf { it.elevationGainMeters }
)

data class MapsGroundedPlace(
    val title: String,
    val uri: String,
    val placeId: String = "",
)

data class MapsGroundingResult(
    val query: String,
    val answerText: String,
    val places: List<MapsGroundedPlace>,
    val latitude: Double,
    val longitude: Double,
)

enum class VoiceCoachStatus {
    IDLE,
    CONNECTING,
    ACTIVE,
    STOPPED,
    ERROR
}

data class VoiceTranscriptEntry(
    val isUser: Boolean,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
)

