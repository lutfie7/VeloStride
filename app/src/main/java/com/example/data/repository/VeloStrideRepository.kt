package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AthleteProfile
import com.example.data.model.ChallengeGoal
import com.example.data.model.DailyHealthLog
import com.example.data.model.GearItem
import com.example.data.model.PairedWearable
import com.example.data.model.SavedRoute
import com.example.data.model.SegmentRecord
import com.example.data.model.SportType
import com.example.data.model.WorkoutActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VeloStrideRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    fun observeProfile(userId: String): Flow<AthleteProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            userDoc(userId).snapshots()
                .map { it.toObject(AthleteProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> logAndRethrowStreamError(error, OperationType.GET, path, userId) }
        )
    }

    suspend fun ensureProfileExists(userId: String, displayName: String, photoUrl: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed." }
            val ref = userDoc(uid)
            val snap = ref.get().await()
            if (!snap.exists()) {
                val cleanName = displayName.trim().ifBlank { "VeloStride Athlete" }.take(80)
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "weeklyDistanceGoalKm" to 40.0,
                    "maxHr" to 192,
                    "restingHr" to 54,
                    "ftpWatts" to 230,
                    "totalDistanceMeters" to 0.0,
                    "totalWorkouts" to 0,
                    "preferredSport" to SportType.RUN.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (photoUrl.isNotBlank()) {
                    payload["photoUrl"] = photoUrl.take(500)
                }
                ref.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfileTargets(
        userId: String,
        displayName: String,
        weeklyGoalKm: Double,
        maxHr: Int,
        restingHr: Int,
        ftpWatts: Int,
        preferredSport: SportType,
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val payload = mapOf(
                "displayName" to displayName.trim().ifBlank { "Athlete" }.take(80),
                "weeklyDistanceGoalKm" to weeklyGoalKm.coerceIn(1.0, 1000.0),
                "maxHr" to maxHr.coerceIn(100, 230),
                "restingHr" to restingHr.coerceIn(30, 120),
                "ftpWatts" to ftpWatts.coerceIn(50, 600),
                "preferredSport" to preferredSport.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userDoc(uid).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeWorkouts(userId: String): Flow<List<WorkoutActivity>> = flow {
        val path = "users/$userId/workouts"
        emitAll(
            userDoc(userId).collection("workouts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(WorkoutActivity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> logAndRethrowStreamError(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun fetchExistingWorkoutExternalIds(userId: String): Set<String> {
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val snap = userDoc(uid).collection("workouts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(60)
                .get()
                .await()
            snap.documents.mapNotNull { it.getString("externalId") }.toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun saveWorkout(userId: String, workout: WorkoutActivity): Result<String> {
        var path = "users/$userId/workouts"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docRef = userDoc(uid).collection("workouts").document()
            path = docRef.path

            val safePoints = workout.routePoints.take(250)
            val safeSplits = workout.splits.take(100)
            val safeZones = if (workout.hrZoneSeconds.size == 5) {
                workout.hrZoneSeconds.map { it.coerceAtLeast(0L) }
            } else {
                listOf(0L, 0L, 0L, 0L, 0L)
            }

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "title" to workout.title.trim().ifBlank { "${workout.parsedSport.label} Session" }.take(120),
                "sportType" to workout.parsedSport.name,
                "distanceMeters" to workout.distanceMeters.coerceIn(0.0, 1_000_000.0),
                "movingTimeSeconds" to workout.movingTimeSeconds.coerceIn(0L, 604_800L),
                "elapsedTimeSeconds" to workout.elapsedTimeSeconds.coerceIn(0L, 604_800L),
                "elevationGainMeters" to workout.elevationGainMeters.coerceIn(0.0, 50_000.0),
                "avgPaceSecPerKm" to workout.avgPaceSecPerKm.coerceIn(0, 3600),
                "avgSpeedKmh" to workout.avgSpeedKmh.coerceIn(0.0, 200.0),
                "avgHeartRate" to workout.avgHeartRate.coerceIn(0, 240),
                "maxHeartRate" to workout.maxHeartRate.coerceIn(0, 240),
                "avgCadence" to workout.avgCadence.coerceIn(0, 300),
                "calories" to workout.calories.coerceIn(0, 25_000),
                "relativeEffort" to workout.relativeEffort.coerceIn(0, 1000),
                "perceivedExertion" to workout.perceivedExertion.coerceIn(1, 10),
                "kudosCount" to workout.kudosCount.coerceIn(0, 1_000_000),
                "kudosGiven" to workout.kudosGiven,
                "privateActivity" to workout.privateActivity,
                "prCount" to workout.prCount.coerceIn(0, 50),
                "hrZoneSeconds" to safeZones,
                "routePoints" to safePoints,
                "splits" to safeSplits,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            workout.gearId?.takeIf { it.isNotBlank() }?.let { payload["gearId"] = it.take(128) }
            workout.gearName?.takeIf { it.isNotBlank() }?.let { payload["gearName"] = it.take(80) }
            workout.deviceName?.takeIf { it.isNotBlank() }?.let { payload["deviceName"] = it.take(80) }
            workout.elevationSource?.takeIf { it.isNotBlank() }?.let { payload["elevationSource"] = it.take(60) }
            workout.externalId?.takeIf { it.isNotBlank() }?.let { payload["externalId"] = it.take(128) }
            workout.aiInsight?.takeIf { it.isNotBlank() }?.let { payload["aiInsight"] = it.take(2500) }
            workout.notes?.takeIf { it.isNotBlank() }?.let { payload["notes"] = it.take(500) }

            docRef.set(payload).await()

            // Increment user aggregate distance and workout count
            userDoc(uid).update(
                mapOf(
                    "totalDistanceMeters" to FieldValue.increment(workout.distanceMeters.coerceAtLeast(0.0)),
                    "totalWorkouts" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Increment selected gear mileage if present
            if (!workout.gearId.isNullOrBlank() && workout.distanceMeters > 0.0) {
                userDoc(uid).collection("gear").document(workout.gearId).update(
                    mapOf(
                        "distanceMeters" to FieldValue.increment(workout.distanceMeters),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleKudos(userId: String, workout: WorkoutActivity): Result<Unit> {
        val path = "users/$userId/workouts/${workout.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId && workout.id.isNotBlank())
            val nextGiven = !workout.kudosGiven
            val nextCount = if (nextGiven) workout.kudosCount + 1 else (workout.kudosCount - 1).coerceAtLeast(0)
            userDoc(uid).collection("workouts").document(workout.id).update(
                mapOf(
                    "kudosGiven" to nextGiven,
                    "kudosCount" to nextCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveWorkoutAiInsight(userId: String, workoutId: String, insight: String): Result<Unit> {
        val path = "users/$userId/workouts/$workoutId"
        return try {
            val uid = requireUserId()
            check(uid == userId && workoutId.isNotBlank())
            userDoc(uid).collection("workouts").document(workoutId).update(
                mapOf(
                    "aiInsight" to insight.trim().take(2500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateWorkoutElevation(
        userId: String,
        workoutId: String,
        elevationGainMeters: Double,
        routePoints: List<Map<String, Any>>,
        splits: List<Map<String, Any>>,
        elevationSource: String,
    ): Result<Unit> {
        val path = "users/$userId/workouts/$workoutId"
        return try {
            val uid = requireUserId()
            check(uid == userId && workoutId.isNotBlank())
            userDoc(uid).collection("workouts").document(workoutId).update(
                mapOf(
                    "elevationGainMeters" to elevationGainMeters.coerceIn(0.0, 50_000.0),
                    "routePoints" to routePoints.take(250),
                    "splits" to splits.take(100),
                    "elevationSource" to elevationSource.take(60),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteWorkout(userId: String, workoutId: String): Result<Unit> {
        val path = "users/$userId/workouts/$workoutId"
        return try {
            val uid = requireUserId()
            check(uid == userId && workoutId.isNotBlank())
            userDoc(uid).collection("workouts").document(workoutId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeRoutes(userId: String): Flow<List<SavedRoute>> = flow {
        val path = "users/$userId/routes"
        emitAll(
            userDoc(userId).collection("routes")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(SavedRoute::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> logAndRethrowStreamError(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun saveRoute(userId: String, route: SavedRoute): Result<Unit> {
        var path = "users/$userId/routes"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docRef = userDoc(uid).collection("routes").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "name" to route.name.trim().ifBlank { "Custom Route" }.take(100),
                    "sportType" to SportType.fromRaw(route.sportType).name,
                    "surfaceType" to route.surfaceType.trim().ifBlank { "Mixed" }.take(40),
                    "distanceMeters" to route.distanceMeters.coerceIn(0.0, 1_000_000.0),
                    "elevationGainMeters" to route.elevationGainMeters.coerceIn(0.0, 50_000.0),
                    "estimatedMinutes" to route.estimatedMinutes.coerceIn(0, 10_000),
                    "waypoints" to route.waypoints.take(100),
                    "starred" to route.starred,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleRouteStar(userId: String, route: SavedRoute): Result<Unit> {
        val path = "users/$userId/routes/${route.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId && route.id.isNotBlank())
            userDoc(uid).collection("routes").document(route.id).update(
                mapOf(
                    "starred" to !route.starred,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeGear(userId: String): Flow<List<GearItem>> = flow {
        val path = "users/$userId/gear"
        emitAll(
            userDoc(userId).collection("gear")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(GearItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> logAndRethrowStreamError(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun saveGear(userId: String, gear: GearItem): Result<Unit> {
        var path = "users/$userId/gear"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docRef = userDoc(uid).collection("gear").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "name" to gear.name.trim().ifBlank { "Running Shoes" }.take(80),
                    "brand" to gear.brand.trim().ifBlank { "Athletic" }.take(60),
                    "gearType" to if (gear.gearType == "BIKE") "BIKE" else "SHOES",
                    "distanceMeters" to gear.distanceMeters.coerceIn(0.0, 100_000_000.0),
                    "maxDistanceMeters" to gear.maxDistanceMeters.coerceIn(1000.0, 100_000_000.0),
                    "primaryGear" to gear.primaryGear,
                    "retired" to gear.retired,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleGearRetired(userId: String, gear: GearItem): Result<Unit> {
        val path = "users/$userId/gear/${gear.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId && gear.id.isNotBlank())
            userDoc(uid).collection("gear").document(gear.id).update(
                mapOf(
                    "retired" to !gear.retired,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeDevices(userId: String): Flow<List<PairedWearable>> = flow {
        val path = "users/$userId/devices"
        emitAll(
            userDoc(userId).collection("devices")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(PairedWearable::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> logAndRethrowStreamError(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun upsertPairedDevice(userId: String, device: PairedWearable): Result<Unit> {
        val safeDocId = device.macAddress.replace(":", "_").replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
            .ifBlank { "ble_device" }
        val path = "users/$userId/devices/$safeDocId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docRef = userDoc(uid).collection("devices").document(safeDocId)
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "batteryPercent" to device.batteryPercent.coerceIn(-1, 100),
                        "supportsHr" to device.supportsHr,
                        "supportsRsc" to device.supportsRsc,
                        "lastSyncedLabel" to device.lastSyncedLabel.take(60),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to device.name.trim().ifBlank { "BLE Wearable" }.take(80),
                        "macAddress" to device.macAddress.trim().ifBlank { "UNKNOWN" }.take(40),
                        "manufacturer" to device.manufacturer.trim().ifBlank { "Standard BLE" }.take(60),
                        "deviceCategory" to device.deviceCategory.trim().ifBlank { "Smartband" }.take(40),
                        "batteryPercent" to device.batteryPercent.coerceIn(-1, 100),
                        "supportsHr" to device.supportsHr,
                        "supportsRsc" to device.supportsRsc,
                        "huaweiNative" to device.huaweiNative,
                        "lastSyncedLabel" to device.lastSyncedLabel.take(60),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteDevice(userId: String, deviceId: String): Result<Unit> {
        val path = "users/$userId/devices/$deviceId"
        return try {
            val uid = requireUserId()
            check(uid == userId && deviceId.isNotBlank())
            userDoc(uid).collection("devices").document(deviceId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeSegments(userId: String): Flow<List<SegmentRecord>> = flow {
        val path = "users/$userId/segments"
        emitAll(
            userDoc(userId).collection("segments")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(SegmentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> logAndRethrowStreamError(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun saveSegment(userId: String, segment: SegmentRecord): Result<Unit> {
        var path = "users/$userId/segments"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docRef = userDoc(uid).collection("segments").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "name" to segment.name.trim().ifBlank { "Sprint Segment" }.take(100),
                    "sportType" to SportType.fromRaw(segment.sportType).name,
                    "distanceMeters" to segment.distanceMeters.coerceIn(50.0, 200_000.0),
                    "avgGradePercent" to segment.avgGradePercent.coerceIn(-30.0, 45.0),
                    "elevationGainMeters" to segment.elevationGainMeters.coerceIn(0.0, 10_000.0),
                    "prTimeSeconds" to segment.prTimeSeconds.coerceIn(1, 86_400),
                    "komTimeSeconds" to segment.komTimeSeconds.coerceIn(1, 86_400),
                    "attemptCount" to segment.attemptCount.coerceIn(1, 10_000),
                    "starred" to segment.starred,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleSegmentStar(userId: String, segment: SegmentRecord): Result<Unit> {
        val path = "users/$userId/segments/${segment.id}"
        return try {
            val uid = requireUserId()
            check(uid == userId && segment.id.isNotBlank())
            userDoc(uid).collection("segments").document(segment.id).update(
                mapOf(
                    "starred" to !segment.starred,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeChallenges(userId: String): Flow<List<ChallengeGoal>> = flow {
        val path = "users/$userId/challenges"
        emitAll(
            userDoc(userId).collection("challenges")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(ChallengeGoal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> logAndRethrowStreamError(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun saveChallenge(userId: String, challenge: ChallengeGoal): Result<Unit> {
        var path = "users/$userId/challenges"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docRef = userDoc(uid).collection("challenges").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "title" to challenge.title.trim().ifBlank { "Distance Goal" }.take(100),
                    "sportType" to SportType.fromRaw(challenge.sportType).name,
                    "targetValue" to challenge.targetValue.coerceIn(1.0, 100_000.0),
                    "currentValue" to challenge.currentValue.coerceIn(0.0, 100_000.0),
                    "unit" to challenge.unit.trim().ifBlank { "km" }.take(20),
                    "periodLabel" to challenge.periodLabel.trim().ifBlank { "Monthly" }.take(40),
                    "joined" to challenge.joined,
                    "completed" to challenge.completed,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeHealthLogs(userId: String): Flow<List<DailyHealthLog>> = flow {
        val path = "users/$userId/health_logs"
        emitAll(
            userDoc(userId).collection("health_logs")
                .orderBy("dateKey", Query.Direction.DESCENDING)
                .limit(14)
                .snapshots()
                .map { snap -> snap.toObjects(DailyHealthLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> logAndRethrowStreamError(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun upsertDailyHealthLog(userId: String, log: DailyHealthLog): Result<Unit> {
        val dateKey = log.dateKey.ifBlank {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        }.replace(Regex("[^a-zA-Z0-9_\\-]"), "-")
        val path = "users/$userId/health_logs/$dateKey"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docRef = userDoc(uid).collection("health_logs").document(dateKey)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "steps" to log.steps.coerceIn(0, 300_000),
                        "distanceMetersToday" to log.distanceMetersToday.coerceIn(0.0, 1_000_000.0),
                        "activeCalories" to log.activeCalories.coerceIn(0, 25_000),
                        "totalCalories" to log.totalCalories.coerceIn(0, 30_000),
                        "restingHr" to log.restingHr.coerceIn(0, 200),
                        "latestHr" to log.latestHr.coerceIn(0, 240),
                        "hrvRmssdMs" to log.hrvRmssdMs.coerceIn(0.0, 500.0),
                        "spo2Percent" to log.spo2Percent.coerceIn(0.0, 100.0),
                        "sleepTotalMinutes" to log.sleepTotalMinutes.coerceIn(0, 1440),
                        "sleepDeepMinutes" to log.sleepDeepMinutes.coerceIn(0, 1440),
                        "sleepLightMinutes" to log.sleepLightMinutes.coerceIn(0, 1440),
                        "sleepRemMinutes" to log.sleepRemMinutes.coerceIn(0, 1440),
                        "sleepAwakeMinutes" to log.sleepAwakeMinutes.coerceIn(0, 1440),
                        "sleepScore" to log.sleepScore.coerceIn(0, 100),
                        "vo2MaxEstimate" to log.vo2MaxEstimate.coerceIn(0.0, 100.0),
                        "recoveryScore" to log.recoveryScore.coerceIn(0, 100),
                        "sourceDevice" to log.sourceDevice.trim().ifBlank { "HUAWEI Band 11 Pro" }.take(80),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "dateKey" to dateKey,
                        "steps" to log.steps.coerceIn(0, 300_000),
                        "distanceMetersToday" to log.distanceMetersToday.coerceIn(0.0, 1_000_000.0),
                        "activeCalories" to log.activeCalories.coerceIn(0, 25_000),
                        "totalCalories" to log.totalCalories.coerceIn(0, 30_000),
                        "restingHr" to log.restingHr.coerceIn(0, 200),
                        "latestHr" to log.latestHr.coerceIn(0, 240),
                        "hrvRmssdMs" to log.hrvRmssdMs.coerceIn(0.0, 500.0),
                        "spo2Percent" to log.spo2Percent.coerceIn(0.0, 100.0),
                        "sleepTotalMinutes" to log.sleepTotalMinutes.coerceIn(0, 1440),
                        "sleepDeepMinutes" to log.sleepDeepMinutes.coerceIn(0, 1440),
                        "sleepLightMinutes" to log.sleepLightMinutes.coerceIn(0, 1440),
                        "sleepRemMinutes" to log.sleepRemMinutes.coerceIn(0, 1440),
                        "sleepAwakeMinutes" to log.sleepAwakeMinutes.coerceIn(0, 1440),
                        "sleepScore" to log.sleepScore.coerceIn(0, 100),
                        "vo2MaxEstimate" to log.vo2MaxEstimate.coerceIn(0.0, 100.0),
                        "recoveryScore" to log.recoveryScore.coerceIn(0, 100),
                        "sourceDevice" to log.sourceDevice.trim().ifBlank { "HUAWEI Band 11 Pro" }.take(80),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private fun logAndRethrowStreamError(
        error: Throwable,
        op: OperationType,
        path: String,
        uid: String,
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }
}
