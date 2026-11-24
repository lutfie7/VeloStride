package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Formatters
import com.example.core.GpxSerializer
import com.example.core.ParsedGpxData
import com.example.core.PerformanceEngine
import com.example.core.ThemePreferences
import com.example.data.model.AthleteProfile
import com.example.data.model.BleConnectionStatus
import com.example.data.model.BleTelemetryState
import com.example.data.model.ChallengeGoal
import com.example.data.model.DailyHealthLog
import com.example.data.model.GearItem
import com.example.data.model.HealthConnectSnapshot
import com.example.data.model.HealthConnectState
import com.example.data.model.MapsGroundingResult
import com.example.data.model.PairedWearable
import com.example.data.model.PerformanceAnalytics
import com.example.data.model.SavedRoute
import com.example.data.model.SegmentRecord
import com.example.data.model.SplitKm
import com.example.data.model.SportType
import com.example.data.model.TrackPoint
import com.example.data.model.UiState
import com.example.data.model.WorkoutActivity
import com.example.data.repository.AthleteIntelligenceRepository
import com.example.data.repository.ElevationRepository
import com.example.data.repository.LiveVoiceCoachManager
import com.example.data.repository.LiveVoiceCoachUiState
import com.example.data.repository.VeloStrideRepository
import com.example.hardware.BandSyncHelper
import com.example.hardware.HealthConnectManager
import com.example.hardware.HuaweiBleManager
import com.example.hardware.LiveRecordingState
import com.example.hardware.WorkoutRecorderController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class MainViewModel(
    private val appContext: Context,
    private val themePreferences: ThemePreferences,
    private val repository: VeloStrideRepository,
    private val aiRepository: AthleteIntelligenceRepository,
    val bleManager: HuaweiBleManager,
    val healthConnectManager: HealthConnectManager,
    val recorderController: WorkoutRecorderController,
    private val currentUserId: String,
    initialDisplayName: String,
    initialPhotoUrl: String,
    private val elevationRepository: ElevationRepository = ElevationRepository(),
    val liveVoiceCoach: LiveVoiceCoachManager,
) : ViewModel() {

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    val bleState: StateFlow<BleTelemetryState> = bleManager.state
    val healthConnectState: StateFlow<HealthConnectSnapshot> = healthConnectManager.snapshot
    val recordingState: StateFlow<LiveRecordingState> = recorderController.state
    val liveVoiceState: StateFlow<LiveVoiceCoachUiState> = liveVoiceCoach.state

    private val _isBandSyncing = MutableStateFlow(false)
    val isBandSyncing: StateFlow<Boolean> = _isBandSyncing.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val profileState: StateFlow<UiState<AthleteProfile>> = repository.observeProfile(currentUserId)
        .map<AthleteProfile?, UiState<AthleteProfile>> { profile ->
            UiState.Success(profile ?: AthleteProfile(userId = currentUserId, displayName = initialDisplayName))
        }
        .unwrapFirestoreErrors("profile")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val workoutsState: StateFlow<UiState<List<WorkoutActivity>>> = repository.observeWorkouts(currentUserId)
        .map<List<WorkoutActivity>, UiState<List<WorkoutActivity>>> { UiState.Success(it) }
        .unwrapFirestoreErrors("workouts")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val routesState: StateFlow<UiState<List<SavedRoute>>> = repository.observeRoutes(currentUserId)
        .map<List<SavedRoute>, UiState<List<SavedRoute>>> { UiState.Success(it) }
        .unwrapFirestoreErrors("routes")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val gearState: StateFlow<UiState<List<GearItem>>> = repository.observeGear(currentUserId)
        .map<List<GearItem>, UiState<List<GearItem>>> { UiState.Success(it) }
        .unwrapFirestoreErrors("gear")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val devicesState: StateFlow<UiState<List<PairedWearable>>> = repository.observeDevices(currentUserId)
        .map<List<PairedWearable>, UiState<List<PairedWearable>>> { UiState.Success(it) }
        .unwrapFirestoreErrors("devices")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val segmentsState: StateFlow<UiState<List<SegmentRecord>>> = repository.observeSegments(currentUserId)
        .map<List<SegmentRecord>, UiState<List<SegmentRecord>>> { UiState.Success(it) }
        .unwrapFirestoreErrors("segments")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val challengesState: StateFlow<UiState<List<ChallengeGoal>>> = repository.observeChallenges(currentUserId)
        .map<List<ChallengeGoal>, UiState<List<ChallengeGoal>>> { UiState.Success(it) }
        .unwrapFirestoreErrors("challenges")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val healthLogsState: StateFlow<UiState<List<DailyHealthLog>>> = repository.observeHealthLogs(currentUserId)
        .map<List<DailyHealthLog>, UiState<List<DailyHealthLog>>> { UiState.Success(it) }
        .unwrapFirestoreErrors("health_logs")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val analyticsState: StateFlow<PerformanceAnalytics> = combine(
        workoutsState,
        profileState,
        healthLogsState,
        bleState
    ) { wState, pState, hState, ble ->
        val workouts = (wState as? UiState.Success)?.data ?: emptyList()
        val profile = (pState as? UiState.Success)?.data ?: AthleteProfile(userId = currentUserId)
        val logs = (hState as? UiState.Success)?.data ?: emptyList()
        PerformanceEngine.computePerformanceAnalytics(workouts, profile, logs, ble.hrvRmssdMs)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        PerformanceEngine.computePerformanceAnalytics(emptyList(), AthleteProfile(), emptyList())
    )

    private val _workoutInsightState = MutableStateFlow<UiState<String>?>(null)
    val workoutInsightState: StateFlow<UiState<String>?> = _workoutInsightState.asStateFlow()

    private val _coachResponseState = MutableStateFlow<UiState<String>?>(null)
    val coachResponseState: StateFlow<UiState<String>?> = _coachResponseState.asStateFlow()

    private val _mapsGroundingState = MutableStateFlow<UiState<MapsGroundingResult>?>(null)
    val mapsGroundingState: StateFlow<UiState<MapsGroundingResult>?> = _mapsGroundingState.asStateFlow()

    private var lastSavedBleMac: String? = null

    init {
        viewModelScope.launch {
            repository.ensureProfileExists(currentUserId, initialDisplayName, initialPhotoUrl)
            bleManager.refreshAdapterState()
            healthConnectManager.checkAvailabilityAndPermissions()
        }
        viewModelScope.launch {
            profileState.collect { st ->
                if (st is UiState.Success) {
                    recorderController.updateHrBaselines(st.data.maxHr, st.data.restingHr)
                }
            }
        }
        viewModelScope.launch {
            bleState.collect { ble ->
                val mac = ble.connectedMacAddress
                if (ble.status == BleConnectionStatus.CONNECTED && !mac.isNullOrBlank() && mac != lastSavedBleMac) {
                    lastSavedBleMac = mac
                    repository.upsertPairedDevice(
                        currentUserId,
                        PairedWearable(
                            userId = currentUserId,
                            name = ble.connectedDeviceName ?: "Wearable Device",
                            macAddress = mac,
                            manufacturer = ble.deviceManufacturer.ifBlank {
                                if (ble.isHuaweiNative) "Wearable Device BLE" else "Standard BLE"
                            },
                            deviceCategory = if (ble.isHuaweiNative) "Wearable Device" else "BLE Sensor",
                            batteryPercent = ble.batteryPercent,
                            supportsHr = ble.liveHeartRateBpm > 0 || ble.discoveredServices.any { it.contains("Heart Rate") },
                            supportsRsc = ble.discoveredServices.any { it.contains("Running Speed") },
                            huaweiNative = ble.isHuaweiNative,
                            lastSyncedLabel = "Live BLE Connected"
                        )
                    )
                }
            }
        }
    }

    fun refreshAllData() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                bleManager.refreshAdapterState()
                val hcState = healthConnectManager.checkAvailabilityAndPermissions()
                if (hcState == HealthConnectState.AVAILABLE_GRANTED) {
                    syncHealthConnectAndPersist(silentIfNoNewWorkouts = true)
                }
                delay(450L)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun syncHealthConnectAndPersist(silentIfNoNewWorkouts: Boolean = false) {
        if (_isBandSyncing.value) return
        viewModelScope.launch {
            _isBandSyncing.value = true
            try {
                val profile = (profileState.value as? UiState.Success)?.data ?: AthleteProfile()
                val result = healthConnectManager.syncHealthData(profile.maxHr)
                result.onSuccess { hc ->
                    val ble = bleState.value
                    val analytics = analyticsState.value

                    repository.upsertDailyHealthLog(
                        currentUserId,
                        BandSyncHelper.buildDailyHealthLog(
                            userId = currentUserId,
                            hc = hc,
                            restingHrFallback = profile.restingHr,
                            vo2MaxFallback = analytics.vo2Max,
                            recoveryScore = analytics.recoveryScore,
                            bleSteps = ble.sessionBleSteps,
                            bleCalories = ble.sessionBleCalories,
                            bleHr = ble.liveHeartRateBpm,
                            bleHrv = ble.hrvRmssdMs,
                            bleDeviceName = ble.connectedDeviceName
                        )
                    )

                    val observedIds = (workoutsState.value as? UiState.Success)?.data
                        ?.mapNotNull { it.externalId }
                        ?.toSet()
                        ?: emptySet()
                    val remoteIds = repository.fetchExistingWorkoutExternalIds(currentUserId)
                    val existingExternalIds = observedIds + remoteIds

                    val batch = BandSyncHelper.persistImportedBandWorkouts(
                        repository = repository,
                        elevationRepository = elevationRepository,
                        userId = currentUserId,
                        profile = profile,
                        sessions = hc.importedExercises,
                        existingExternalIds = existingExternalIds
                    )

                    val msg = when {
                        batch.newlyImportedCount > 0 ->
                            "${hc.lastSyncMessage} • Imported ${batch.newlyImportedCount} Wearable workout(s) with DEM elevation!"
                        !silentIfNoNewWorkouts -> hc.lastSyncMessage
                        else -> null
                    }
                    if (msg != null) {
                        _events.emit(msg)
                    }
                }.onFailure { err ->
                    if (!silentIfNoNewWorkouts) {
                        _events.emit(err.localizedMessage ?: "Health Connect sync unavailable")
                    }
                }
            } finally {
                _isBandSyncing.value = false
            }
        }
    }

    fun syncLiveBleHealthSnapshot() {
        viewModelScope.launch {
            val ble = bleState.value
            val analytics = analyticsState.value
            val profile = (profileState.value as? UiState.Success)?.data ?: AthleteProfile()
            val hc = healthConnectState.value
            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            val res = repository.upsertDailyHealthLog(
                currentUserId,
                DailyHealthLog(
                    userId = currentUserId,
                    dateKey = todayKey,
                    steps = hc.stepsToday + ble.sessionBleSteps,
                    distanceMetersToday = hc.distanceMetersToday,
                    activeCalories = hc.activeCaloriesToday + ble.sessionBleCalories,
                    totalCalories = (hc.totalCaloriesToday + ble.sessionBleCalories).coerceAtLeast(ble.sessionBleCalories),
                    restingHr = hc.restingHeartRateBpm.takeIf { it > 0 } ?: profile.restingHr,
                    latestHr = ble.liveHeartRateBpm.takeIf { it > 0 } ?: hc.latestHeartRateBpm,
                    hrvRmssdMs = ble.hrvRmssdMs.takeIf { it > 0.0 } ?: hc.hrvRmssdMs,
                    spo2Percent = hc.spo2Percent,
                    sleepTotalMinutes = hc.sleepTotalMinutes,
                    sleepDeepMinutes = hc.sleepDeepMinutes,
                    sleepLightMinutes = hc.sleepLightMinutes,
                    sleepRemMinutes = hc.sleepRemMinutes,
                    sleepAwakeMinutes = hc.sleepAwakeMinutes,
                    sleepScore = analytics.sleepScore,
                    vo2MaxEstimate = hc.vo2MaxFromBand.takeIf { it > 10.0 } ?: analytics.vo2Max,
                    recoveryScore = analytics.recoveryScore,
                    sourceDevice = ble.connectedDeviceName ?: "Wearable Device"
                )
            )
            if (res.isSuccess) {
                _events.emit("Telemetry snapshot synced to cloud")
            } else {
                _events.emit("Failed to save telemetry snapshot")
            }
        }
    }

    fun finishAndSaveRecordedWorkout(
        title: String,
        perceivedExertion: Int,
        gear: GearItem?,
        privateActivity: Boolean,
        notes: String?,
        onSaved: (String) -> Unit,
    ) {
        viewModelScope.launch {
            var built = recorderController.stopAndBuildWorkout(
                userId = currentUserId,
                title = title,
                perceivedExertion = perceivedExertion,
                gearId = gear?.id,
                gearName = gear?.name,
                privateActivity = privateActivity,
                notes = notes
            )
            // If Band 11 Pro or phone lacked barometer/altitude data, enrich route elevation via Copernicus DEM 90m
            val pts = built.decodedPoints
            if (pts.size >= 2 && (built.elevationGainMeters < 1.0 || pts.all { kotlin.math.abs(it.ele) < 0.1 })) {
                val enriched = elevationRepository.enrichTrackElevation(pts, built.elevationGainMeters)
                val updatedSplits = elevationRepository.recomputeSplitElevations(enriched.points, built.decodedSplits)
                built = built.copy(
                    elevationGainMeters = enriched.elevationGainMeters,
                    routePoints = enriched.points.map { it.toMap() },
                    splits = updatedSplits.map { it.toMap() },
                    elevationSource = enriched.sourceLabel
                )
            }
            val result = repository.saveWorkout(currentUserId, built)
            result.onSuccess { newId ->
                _events.emit("Activity saved with ${built.elevationSource ?: "GPS"} elevation!")
                onSaved(newId)
            }.onFailure { err ->
                _events.emit("Error saving workout: ${err.localizedMessage}")
            }
        }
    }

    fun enrichWorkoutElevation(workout: WorkoutActivity) {
        viewModelScope.launch {
            val pts = workout.decodedPoints
            if (pts.size < 2) {
                _events.emit("This manual activity has no GPS trackpoints for DEM elevation lookup.")
                return@launch
            }
            val enriched = elevationRepository.enrichTrackElevation(pts, 0.0)
            val updatedSplits = elevationRepository.recomputeSplitElevations(enriched.points, workout.decodedSplits)
            repository.updateWorkoutElevation(
                userId = currentUserId,
                workoutId = workout.id,
                elevationGainMeters = enriched.elevationGainMeters,
                routePoints = enriched.points.map { it.toMap() },
                splits = updatedSplits.map { it.toMap() },
                elevationSource = enriched.sourceLabel
            ).onSuccess {
                _events.emit("Elevation enriched via ${enriched.sourceLabel}: +${enriched.elevationGainMeters.roundToInt()}m")
            }.onFailure {
                _events.emit("Failed to update elevation: ${it.localizedMessage}")
            }
        }
    }

    fun saveManualWorkout(
        title: String,
        sportType: SportType,
        distanceKm: Double,
        durationMinutes: Int,
        elevationMeters: Double,
        avgHr: Int,
        perceivedExertion: Int,
        gear: GearItem?,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            val profile = (profileState.value as? UiState.Success)?.data ?: AthleteProfile()
            val distM = (distanceKm * 1000.0).coerceIn(100.0, 500_000.0)
            val secs = (durationMinutes * 60L).coerceIn(60L, 172_800L)
            val avgSpeed = (distanceKm / (secs / 3600.0)).coerceIn(0.5, 120.0)
            val avgPace = ((secs / distanceKm).roundToInt()).coerceIn(60, 3600)
            val effHr = avgHr.coerceIn(70, 220)
            val effort = PerformanceEngine.computeRelativeEffort(
                movingSeconds = secs,
                avgHr = effHr,
                maxHr = profile.maxHr,
                restingHr = profile.restingHr,
                perceivedExertion = perceivedExertion,
                hrZoneSeconds = listOf(0L, 0L, 0L, 0L, 0L)
            )
            val zoneIdx = (Formatters.computeHrZone(effHr, profile.maxHr) - 1).coerceIn(0, 4)
            val zones = MutableList(5) { 0L }.apply { this[zoneIdx] = secs }
            val splitCount = distanceKm.roundToInt().coerceIn(1, 30)
            val splits = (1..splitCount).map { km ->
                SplitKm(km = km, paceSec = avgPace, eleDiff = elevationMeters / splitCount, avgHr = effHr).toMap()
            }

            val workout = WorkoutActivity(
                userId = currentUserId,
                title = title,
                sportType = sportType.name,
                distanceMeters = distM,
                movingTimeSeconds = secs,
                elapsedTimeSeconds = secs,
                elevationGainMeters = elevationMeters,
                avgPaceSecPerKm = avgPace,
                avgSpeedKmh = (avgSpeed * 10.0).roundToInt() / 10.0,
                avgHeartRate = effHr,
                maxHeartRate = (effHr + 16).coerceAtMost(profile.maxHr),
                avgCadence = if (sportType.isPaceBased) 172 else 86,
                calories = ((durationMinutes * (effHr * 0.085))).roundToInt().coerceIn(20, 9000),
                relativeEffort = effort,
                perceivedExertion = perceivedExertion.coerceIn(1, 10),
                hrZoneSeconds = zones,
                splits = splits,
                gearId = gear?.id,
                gearName = gear?.name,
                deviceName = bleState.value.connectedDeviceName ?: "Manual Log"
            )
            repository.saveWorkout(currentUserId, workout).onSuccess {
                _events.emit("Logged $title")
                onSaved()
            }.onFailure {
                _events.emit("Failed to save activity: ${it.localizedMessage}")
            }
        }
    }

    fun toggleKudos(workout: WorkoutActivity) {
        viewModelScope.launch {
            repository.toggleKudos(currentUserId, workout)
        }
    }

    fun updateWorkout(
        existing: WorkoutActivity,
        title: String,
        sportType: SportType,
        gear: GearItem?,
        perceivedExertion: Int,
        privateActivity: Boolean,
        notes: String?,
        mediaUris: List<String>,
        onUpdated: () -> Unit = {},
    ) {
        viewModelScope.launch {
            repository.updateWorkout(
                userId = currentUserId,
                existing = existing,
                title = title,
                sportType = sportType,
                gear = gear,
                perceivedExertion = perceivedExertion,
                privateActivity = privateActivity,
                notes = notes,
                mediaUris = mediaUris
            ).onSuccess {
                _events.emit("Activity updated")
                onUpdated()
            }.onFailure { err ->
                _events.emit("Failed to update activity: ${err.localizedMessage}")
            }
        }
    }

    fun deleteWorkout(workoutId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteWorkout(currentUserId, workoutId).onSuccess {
                _events.emit("Activity deleted")
                onDeleted()
            }
        }
    }

    fun generateWorkoutAiInsight(workout: WorkoutActivity) {
        viewModelScope.launch {
            _workoutInsightState.value = UiState.Loading
            val profile = (profileState.value as? UiState.Success)?.data ?: AthleteProfile()
            val analytics = analyticsState.value
            try {
                var finalInsight = ""
                aiRepository.analyzeWorkoutStream(workout, profile, analytics).collect { streamed ->
                    finalInsight = streamed
                    _workoutInsightState.value = UiState.Success(streamed)
                }
                if (finalInsight.isNotBlank() && workout.id.isNotBlank()) {
                    repository.saveWorkoutAiInsight(currentUserId, workout.id, finalInsight)
                } else if (finalInsight.isBlank()) {
                    _workoutInsightState.value = UiState.Error("Gemini returned an empty analysis.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Workout AI analysis timed out", e)
                _workoutInsightState.value = UiState.Error("Analysis timed out after 60s. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Workout AI analysis failed", e)
                _workoutInsightState.value = UiState.Error(formatAiErrorMessage(e, "AI analysis failed."))
            }
        }
    }

    fun clearWorkoutInsightPreview() {
        _workoutInsightState.value = null
    }

    fun askAiCoach(question: String) {
        viewModelScope.launch {
            _coachResponseState.value = UiState.Loading
            val profile = (profileState.value as? UiState.Success)?.data ?: AthleteProfile()
            val workouts = (workoutsState.value as? UiState.Success)?.data ?: emptyList()
            val latestLog = (healthLogsState.value as? UiState.Success)?.data?.firstOrNull()
            val analytics = analyticsState.value
            try {
                var finalText = ""
                aiRepository.generateTrainingRecommendationStream(
                    userQuestion = question,
                    profile = profile,
                    analytics = analytics,
                    recentWorkouts = workouts,
                    latestHealth = latestLog
                ).collect { chunk ->
                    finalText = chunk
                    _coachResponseState.value = UiState.Success(chunk)
                }
                if (finalText.isBlank()) {
                    _coachResponseState.value = UiState.Error("AI Coach returned an empty recommendation.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI Coach timed out", e)
                _coachResponseState.value = UiState.Error("AI Coach timed out. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Coach failed", e)
                _coachResponseState.value = UiState.Error(formatAiErrorMessage(e, "AI Coach failed."))
            }
        }
    }

    fun startLiveVoiceCoach() {
        val profile = (profileState.value as? UiState.Success)?.data ?: AthleteProfile(userId = currentUserId)
        val workouts = (workoutsState.value as? UiState.Success)?.data ?: emptyList()
        liveVoiceCoach.startConversation(
            profile = profile,
            analytics = analyticsState.value,
            recentWorkouts = workouts
        )
    }

    fun stopLiveVoiceCoach() {
        liveVoiceCoach.stopConversation()
    }

    fun sendLiveVoiceText(message: String) {
        liveVoiceCoach.sendTextDuringLiveSession(message)
    }

    fun searchNearbyWithMapsGrounding(query: String, lat: Double?, lng: Double?) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return
        viewModelScope.launch {
            _mapsGroundingState.value = UiState.Loading
            val fallbackPoint = (workoutsState.value as? UiState.Success)?.data
                ?.firstOrNull { it.decodedPoints.isNotEmpty() }
                ?.decodedPoints?.firstOrNull()
            val effLat = lat ?: fallbackPoint?.lat ?: -6.2185
            val effLng = lng ?: fallbackPoint?.lng ?: 106.8020
            try {
                val result = aiRepository.searchNearbyPlacesWithMapsGrounding(
                    query = cleanQuery,
                    latitude = effLat,
                    longitude = effLng
                )
                _mapsGroundingState.value = UiState.Success(result)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Maps Grounding timed out", e)
                _mapsGroundingState.value = UiState.Error("Google Maps grounding timed out after 60s. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Maps Grounding failed", e)
                _mapsGroundingState.value = UiState.Error(
                    formatAiErrorMessage(e, "Failed to query Google Maps grounding.")
                )
            }
        }
    }

    private fun formatAiErrorMessage(e: Exception, fallback: String): String {
        val raw = e.localizedMessage ?: fallback
        return when {
            raw.contains("App Check", ignoreCase = true) || raw.contains("403") -> {
                "$raw\n\nTip: Untuk APK hasil build GitHub, buat Debug Token di Firebase Console (App Check -> Manage debug tokens -> Add debug token) lalu masukkan UUID token ke GitHub Secret FIREBASE_APPCHECK_DEBUG_TOKEN dan build ulang."
            }
            AthleteIntelligenceRepository.isQuotaExceededError(e) -> {
                "Kuota permintaan AI sementara tercapai (sudah mencoba fallback ke gemini-3.5-flash-lite). Tunggu beberapa menit lalu ketuk Analyze lagi.\n\nDetail: $raw"
            }
            AthleteIntelligenceRepository.isTimeoutError(e) -> {
                "Koneksi ke server AI timeout setelah mencoba model utama & cadangan. Periksa koneksi internet Anda lalu coba lagi."
            }
            AthleteIntelligenceRepository.isTransientDemandError(e) -> {
                "Server AI sedang mengalami lonjakan trafik sementara (sudah mencoba retry & model cadangan). Tunggu beberapa detik lalu ketuk Analyze lagi.\n\nDetail: $raw"
            }
            else -> raw
        }
    }

    override fun onCleared() {
        liveVoiceCoach.stopConversation()
        bleManager.release()
        super.onCleared()
    }

    fun saveCustomRoute(
        name: String,
        sportType: SportType,
        surfaceType: String,
        waypoints: List<TrackPoint>,
        distanceMeters: Double,
        elevationMeters: Double,
    ) {
        viewModelScope.launch {
            val enriched = elevationRepository.enrichTrackElevation(waypoints, elevationMeters)
            val estMin = ((distanceMeters / 1000.0) / sportType.defaultSpeedKmh.coerceAtLeast(4.0) * 60.0).roundToInt().coerceAtLeast(5)
            repository.saveRoute(
                currentUserId,
                SavedRoute(
                    userId = currentUserId,
                    name = name,
                    sportType = sportType.name,
                    surfaceType = surfaceType,
                    distanceMeters = distanceMeters,
                    elevationGainMeters = enriched.elevationGainMeters,
                    estimatedMinutes = estMin,
                    waypoints = GpxSerializer.downsampleTrackPoints(enriched.points, 100).map { it.toMap() },
                    starred = true
                )
            ).onSuccess { _events.emit("Route saved with ${enriched.sourceLabel}: $name") }
        }
    }

    fun importWorkoutFromGpx(
        context: Context,
        uri: Uri,
        onImported: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            val parsedResult = GpxSerializer.readGpxFromUri(context.applicationContext, uri)
            parsedResult.onFailure { err ->
                _events.emit("Failed to import GPX: ${err.localizedMessage ?: "Invalid GPX file"}")
                return@launch
            }
            val parsed = parsedResult.getOrThrow()
            val fullPoints = parsed.points
            val profile = (profileState.value as? UiState.Success)?.data ?: AthleteProfile()

            val totalDistanceM = (1 until fullPoints.size).sumOf { idx ->
                Formatters.haversineMeters(
                    fullPoints[idx - 1].lat,
                    fullPoints[idx - 1].lng,
                    fullPoints[idx].lat,
                    fullPoints[idx].lng
                )
            }.coerceIn(10.0, 1_000_000.0)

            val movingSec = if (parsed.startTimeMs != null && parsed.endTimeMs != null && parsed.endTimeMs > parsed.startTimeMs) {
                ((parsed.endTimeMs - parsed.startTimeMs) / 1000L).coerceIn(10L, 604_800L)
            } else {
                val estSpeedMps = (parsed.sportType.defaultSpeedKmh.coerceAtLeast(5.0) / 3.6)
                (totalDistanceM / estSpeedMps).roundToLongSafe().coerceIn(30L, 604_800L)
            }

            val enriched = elevationRepository.enrichTrackElevation(fullPoints)
            val enrichedPoints = enriched.points

            val hrSamples = enrichedPoints.map { it.hr }.filter { it > 0 }
            val avgHr = if (hrSamples.isNotEmpty()) hrSamples.average().roundToInt().coerceIn(40, 230) else 0
            val maxHr = (hrSamples.maxOrNull() ?: 0).coerceIn(0, 240)

            val zoneSeconds = MutableList(5) { 0L }
            if (hrSamples.isNotEmpty()) {
                val secPerSample = (movingSec.toDouble() / hrSamples.size).coerceAtLeast(1.0)
                hrSamples.forEach { bpm ->
                    val z = (Formatters.computeHrZone(bpm, profile.maxHr) - 1).coerceIn(0, 4)
                    zoneSeconds[z] += secPerSample.roundToInt().toLong()
                }
            }

            val splits = buildGpxKmSplits(enrichedPoints, parsed.timestampsMs, movingSec, totalDistanceM)
            val storedPoints = GpxSerializer.downsampleTrackPoints(enrichedPoints, 250)
            val avgSpeed = ((totalDistanceM / 1000.0) / (movingSec / 3600.0)).coerceIn(0.0, 200.0)
            val avgPace = if (totalDistanceM >= 50.0) {
                (movingSec / (totalDistanceM / 1000.0)).roundToInt().coerceIn(60, 3600)
            } else 0
            val effort = PerformanceEngine.computeRelativeEffort(
                movingSeconds = movingSec,
                avgHr = avgHr,
                maxHr = profile.maxHr,
                restingHr = profile.restingHr,
                perceivedExertion = 6,
                hrZoneSeconds = zoneSeconds
            )
            val estCalories = if (avgHr > 0) {
                ((movingSec / 60.0) * (avgHr * 0.085)).roundToInt().coerceIn(10, 20_000)
            } else {
                ((totalDistanceM / 1000.0) * 65.0).roundToInt().coerceIn(10, 20_000)
            }

            val activityTimestamp = parsed.startTimeMs
                ?.takeIf { it > 0L && it <= System.currentTimeMillis() - 15_000L }
                ?.let { Timestamp(Date(it)) }

            val workout = WorkoutActivity(
                userId = currentUserId,
                title = parsed.name.take(120),
                sportType = parsed.sportType.name,
                distanceMeters = (totalDistanceM * 10.0).roundToInt() / 10.0,
                movingTimeSeconds = movingSec,
                elapsedTimeSeconds = movingSec,
                elevationGainMeters = enriched.elevationGainMeters,
                avgPaceSecPerKm = avgPace,
                avgSpeedKmh = (avgSpeed * 10.0).roundToInt() / 10.0,
                avgHeartRate = avgHr,
                maxHeartRate = maxHr,
                avgCadence = parsed.avgCadence,
                calories = estCalories,
                relativeEffort = effort,
                perceivedExertion = 6,
                hrZoneSeconds = zoneSeconds,
                routePoints = storedPoints.map { it.toMap() },
                splits = splits.take(100).map { it.toMap() },
                deviceName = "GPX Import",
                elevationSource = enriched.sourceLabel,
                notes = "Imported from GPX (${fullPoints.size} pts)",
                createdAt = activityTimestamp
            )

            repository.saveWorkout(currentUserId, workout).onSuccess { newId ->
                _events.emit(
                    "Imported GPX: ${workout.title} (${Formatters.formatDistanceKm(totalDistanceM)}, " +
                        "+${enriched.elevationGainMeters.roundToInt()}m • ${enriched.sourceLabel})"
                )
                onImported(newId)
            }.onFailure { err ->
                _events.emit("Failed to save GPX workout: ${err.localizedMessage}")
            }
        }
    }

    fun importRouteFromGpx(
        context: Context,
        uri: Uri,
        onImported: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val parsedResult = GpxSerializer.readGpxFromUri(context.applicationContext, uri)
            parsedResult.onFailure { err ->
                _events.emit("Failed to import GPX route: ${err.localizedMessage ?: "Invalid GPX file"}")
                return@launch
            }
            val parsed = parsedResult.getOrThrow()
            val fullPoints = parsed.points
            val totalDistanceM = (1 until fullPoints.size).sumOf { idx ->
                Formatters.haversineMeters(
                    fullPoints[idx - 1].lat,
                    fullPoints[idx - 1].lng,
                    fullPoints[idx].lat,
                    fullPoints[idx].lng
                )
            }.coerceIn(10.0, 1_000_000.0)

            val enriched = elevationRepository.enrichTrackElevation(fullPoints)
            val estMin = ((totalDistanceM / 1000.0) / parsed.sportType.defaultSpeedKmh.coerceAtLeast(4.0) * 60.0)
                .roundToInt()
                .coerceIn(5, 10_000)

            val route = SavedRoute(
                userId = currentUserId,
                name = parsed.name.take(100),
                sportType = parsed.sportType.name,
                surfaceType = if (parsed.sportType == SportType.TRAIL_RUN || parsed.sportType == SportType.HIKE) "Trail" else "Mixed",
                distanceMeters = (totalDistanceM * 10.0).roundToInt() / 10.0,
                elevationGainMeters = enriched.elevationGainMeters,
                estimatedMinutes = estMin,
                waypoints = GpxSerializer.downsampleTrackPoints(enriched.points, 100).map { it.toMap() },
                starred = true
            )

            repository.saveRoute(currentUserId, route).onSuccess {
                _events.emit(
                    "GPX route saved: ${route.name} (${Formatters.formatDistanceKm(totalDistanceM)}, " +
                        "+${enriched.elevationGainMeters.roundToInt()}m)"
                )
                onImported()
            }.onFailure { err ->
                _events.emit("Failed to save GPX route: ${err.localizedMessage}")
            }
        }
    }

    fun exportWorkoutGpxToUri(context: Context, uri: Uri, workout: WorkoutActivity) {
        viewModelScope.launch {
            val xml = GpxSerializer.buildWorkoutGpxXml(workout)
            GpxSerializer.exportGpxToUri(context.applicationContext, uri, xml).onSuccess {
                _events.emit("Exported GPX: ${GpxSerializer.suggestedGpxFileName(workout.title)}")
            }.onFailure { err ->
                _events.emit("GPX export failed: ${err.localizedMessage}")
            }
        }
    }

    fun exportRouteGpxToUri(context: Context, uri: Uri, route: SavedRoute) {
        viewModelScope.launch {
            val xml = GpxSerializer.buildRouteGpxXml(route)
            GpxSerializer.exportGpxToUri(context.applicationContext, uri, xml).onSuccess {
                _events.emit("Exported Route GPX: ${GpxSerializer.suggestedGpxFileName(route.name)}")
            }.onFailure { err ->
                _events.emit("Route GPX export failed: ${err.localizedMessage}")
            }
        }
    }

    fun emitNotice(message: String) {
        viewModelScope.launch { _events.emit(message) }
    }

    private fun Double.roundToLongSafe(): Long = if (isNaN() || isInfinite()) 0L else kotlin.math.round(this).toLong()

    private fun buildGpxKmSplits(
        points: List<TrackPoint>,
        timestampsMs: List<Long>,
        totalMovingSec: Long,
        totalDistanceM: Double,
    ): List<SplitKm> {
        if (points.size < 2 || totalDistanceM < 200.0) return emptyList()
        val splits = mutableListOf<SplitKm>()
        var cumDistM = 0.0
        var splitStartDistM = 0.0
        var splitStartTimeMs = timestampsMs.firstOrNull()?.takeIf { it > 0L }
        var splitNetEle = 0.0
        val hrBucket = mutableListOf<Int>()
        var nextKmThreshold = 1000.0
        var kmIdx = 1

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            cumDistM += Formatters.haversineMeters(prev.lat, prev.lng, curr.lat, curr.lng)
            splitNetEle += (curr.ele - prev.ele)
            if (curr.hr > 0) hrBucket.add(curr.hr)

            if (cumDistM >= nextKmThreshold && splits.size < 99) {
                val segDistKm = ((cumDistM - splitStartDistM) / 1000.0).coerceAtLeast(0.2)
                val endTimeMs = timestampsMs.getOrNull(i)?.takeIf { it > 0L }
                val segSec = if (splitStartTimeMs != null && endTimeMs != null && endTimeMs > splitStartTimeMs) {
                    (endTimeMs - splitStartTimeMs) / 1000.0
                } else {
                    totalMovingSec * (segDistKm / (totalDistanceM / 1000.0).coerceAtLeast(0.2))
                }
                splits.add(
                    SplitKm(
                        km = kmIdx++,
                        paceSec = (segSec / segDistKm).roundToInt().coerceIn(60, 3600),
                        eleDiff = (splitNetEle * 10.0).roundToInt() / 10.0,
                        avgHr = if (hrBucket.isNotEmpty()) hrBucket.average().roundToInt() else 0
                    )
                )
                splitStartDistM = cumDistM
                splitStartTimeMs = endTimeMs
                splitNetEle = 0.0
                hrBucket.clear()
                nextKmThreshold += 1000.0
            }
        }
        return splits
    }

    fun toggleRouteStar(route: SavedRoute) {
        viewModelScope.launch { repository.toggleRouteStar(currentUserId, route) }
    }

    fun toggleSegmentStar(segment: SegmentRecord) {
        viewModelScope.launch { repository.toggleSegmentStar(currentUserId, segment) }
    }

    fun saveCustomSegment(
        name: String,
        sportType: SportType,
        distanceMeters: Double,
        elevationGainMeters: Double,
        prTimeSeconds: Int,
        komTimeSeconds: Int,
    ) {
        viewModelScope.launch {
            val dist = distanceMeters.coerceIn(50.0, 200_000.0)
            val avgGrade = ((elevationGainMeters / dist) * 100.0).coerceIn(-30.0, 45.0)
            repository.saveSegment(
                currentUserId,
                SegmentRecord(
                    userId = currentUserId,
                    name = name,
                    sportType = sportType.name,
                    distanceMeters = dist,
                    avgGradePercent = (avgGrade * 10.0).roundToInt() / 10.0,
                    elevationGainMeters = elevationGainMeters.coerceIn(0.0, 10_000.0),
                    prTimeSeconds = prTimeSeconds.coerceIn(1, 86_400),
                    komTimeSeconds = komTimeSeconds.coerceIn(1, 86_400),
                    attemptCount = 1,
                    starred = true
                )
            ).onSuccess { _events.emit("Segment created: $name") }
        }
    }

    fun addGear(name: String, brand: String, gearType: String, maxKm: Double) {
        viewModelScope.launch {
            repository.saveGear(
                currentUserId,
                GearItem(
                    userId = currentUserId,
                    name = name,
                    brand = brand,
                    gearType = gearType,
                    distanceMeters = 0.0,
                    maxDistanceMeters = maxKm * 1000.0,
                    primaryGear = false,
                    retired = false
                )
            ).onSuccess { _events.emit("Added $name to Gear Locker") }
        }
    }

    fun toggleGearRetired(gear: GearItem) {
        viewModelScope.launch { repository.toggleGearRetired(currentUserId, gear) }
    }

    fun addChallenge(title: String, sportType: SportType, targetValue: Double, unit: String) {
        viewModelScope.launch {
            repository.saveChallenge(
                currentUserId,
                ChallengeGoal(
                    userId = currentUserId,
                    title = title,
                    sportType = sportType.name,
                    targetValue = targetValue,
                    currentValue = 0.0,
                    unit = unit,
                    periodLabel = "Current Cycle",
                    joined = true,
                    completed = false
                )
            ).onSuccess { _events.emit("Joined challenge: $title") }
        }
    }

    fun updateProfileTargets(
        displayName: String,
        photoUrl: String,
        birthDateIso: String,
        gender: String,
        heightCm: Double,
        weightKg: Double,
        weeklyGoalKm: Double,
        maxHr: Int,
        restingHr: Int,
        ftpWatts: Int,
        preferredSport: SportType,
    ) {
        viewModelScope.launch {
            repository.updateProfileTargets(
                userId = currentUserId,
                displayName = displayName,
                photoUrl = photoUrl,
                birthDateIso = birthDateIso,
                gender = gender,
                heightCm = heightCm,
                weightKg = weightKg,
                weeklyGoalKm = weeklyGoalKm,
                maxHr = maxHr,
                restingHr = restingHr,
                ftpWatts = ftpWatts,
                preferredSport = preferredSport
            ).onSuccess {
                _events.emit("Athlete profile & biometrics updated")
            }.onFailure { err ->
                _events.emit("Failed to update profile: ${err.localizedMessage}")
            }
        }
    }

    fun deletePairedDevice(deviceId: String) {
        viewModelScope.launch {
            repository.deleteDevice(currentUserId, deviceId)
        }
    }

    /**
     * Explicit user-triggered demo training block so users on an emulator without GPS/BLE hardware
     * can inspect Fitness & Freshness charts, VO2 Max, Segments, Gear, and AI Athlete Intelligence.
     */
    fun loadSampleTrainingBlock() {
        viewModelScope.launch {
            val gearItem = GearItem(
                userId = currentUserId,
                name = "Vaporfly 3 Carbon",
                brand = "Nike",
                gearType = "SHOES",
                distanceMeters = 148_000.0,
                maxDistanceMeters = 650_000.0,
                primaryGear = true,
                retired = false
            )
            repository.saveGear(currentUserId, gearItem)

            val loopPoints = (0..36).map { idx ->
                val angle = (idx / 36.0) * 2.0 * Math.PI
                val lat = -6.2185 + 0.0085 * sin(angle) + 0.0015 * sin(angle * 3)
                val lng = 106.8020 + 0.0095 * cos(angle)
                val ele = 14.0 + 18.0 * (0.5 + 0.5 * sin(angle * 2))
                val hr = (146 + 24 * sin(angle - 0.5)).roundToInt()
                TrackPoint(lat, lng, ele, hr).toMap()
            }

            val sampleWorkouts = listOf(
                WorkoutActivity(
                    userId = currentUserId,
                    title = "Senayan Threshold 10K",
                    sportType = SportType.RUN.name,
                    distanceMeters = 10_240.0,
                    movingTimeSeconds = 2790L,
                    elapsedTimeSeconds = 2830L,
                    elevationGainMeters = 68.0,
                    avgPaceSecPerKm = 272,
                    avgSpeedKmh = 13.2,
                    avgHeartRate = 166,
                    maxHeartRate = 183,
                    avgCadence = 178,
                    calories = 710,
                    relativeEffort = 128,
                    perceivedExertion = 8,
                    kudosCount = 14,
                    prCount = 2,
                    hrZoneSeconds = listOf(120L, 410L, 840L, 1180L, 240L),
                    routePoints = loopPoints,
                    splits = (1..10).map { km ->
                        SplitKm(km, 276 - (km % 3) * 4, 6.8, 155 + km * 2).toMap()
                    },
                    gearName = "Vaporfly 3 Carbon",
                    deviceName = "Wearable Device"
                ),
                WorkoutActivity(
                    userId = currentUserId,
                    title = "Dawn Gravel Loop 42K",
                    sportType = SportType.GRAVEL_RIDE.name,
                    distanceMeters = 42_500.0,
                    movingTimeSeconds = 5120L,
                    elapsedTimeSeconds = 5340L,
                    elevationGainMeters = 485.0,
                    avgPaceSecPerKm = 120,
                    avgSpeedKmh = 29.9,
                    avgHeartRate = 152,
                    maxHeartRate = 176,
                    avgCadence = 88,
                    calories = 1140,
                    relativeEffort = 156,
                    perceivedExertion = 7,
                    kudosCount = 22,
                    prCount = 3,
                    hrZoneSeconds = listOf(380L, 1450L, 2020L, 1090L, 180L),
                    routePoints = loopPoints,
                    splits = (1..8).map { km ->
                        SplitKm(km * 5, 118 + (km % 2) * 5, 60.0, 148 + km * 2).toMap()
                    },
                    deviceName = "Wearable Device"
                ),
                WorkoutActivity(
                    userId = currentUserId,
                    title = "Zone 2 Aerobic Recovery 6K",
                    sportType = SportType.RUN.name,
                    distanceMeters = 6_150.0,
                    movingTimeSeconds = 2040L,
                    elapsedTimeSeconds = 2080L,
                    elevationGainMeters = 28.0,
                    avgPaceSecPerKm = 332,
                    avgSpeedKmh = 10.8,
                    avgHeartRate = 139,
                    maxHeartRate = 150,
                    avgCadence = 169,
                    calories = 395,
                    relativeEffort = 48,
                    perceivedExertion = 4,
                    kudosCount = 9,
                    prCount = 0,
                    hrZoneSeconds = listOf(310L, 1420L, 310L, 0L, 0L),
                    routePoints = loopPoints,
                    splits = (1..6).map { km -> SplitKm(km, 330 + km, 4.5, 138 + km).toMap() },
                    gearName = "Vaporfly 3 Carbon",
                    deviceName = "Wearable Device"
                )
            )
            sampleWorkouts.forEach { repository.saveWorkout(currentUserId, it) }

            repository.saveSegment(
                currentUserId,
                SegmentRecord(
                    userId = currentUserId,
                    name = "GBK Outer Sprint 1.2K",
                    sportType = SportType.RUN.name,
                    distanceMeters = 1200.0,
                    avgGradePercent = 0.6,
                    elevationGainMeters = 8.0,
                    prTimeSeconds = 248,
                    komTimeSeconds = 214,
                    attemptCount = 7,
                    starred = true
                )
            )
            repository.saveSegment(
                currentUserId,
                SegmentRecord(
                    userId = currentUserId,
                    name = "Sentul Ridge Climb",
                    sportType = SportType.CYCLE.name,
                    distanceMeters = 4800.0,
                    avgGradePercent = 6.2,
                    elevationGainMeters = 298.0,
                    prTimeSeconds = 815,
                    komTimeSeconds = 692,
                    attemptCount = 4,
                    starred = true
                )
            )
            repository.saveRoute(
                currentUserId,
                SavedRoute(
                    userId = currentUserId,
                    name = "Alpine Ridge Tempo 15K",
                    sportType = SportType.TRAIL_RUN.name,
                    surfaceType = "Singletrack & Paved",
                    distanceMeters = 15_200.0,
                    elevationGainMeters = 340.0,
                    estimatedMinutes = 86,
                    waypoints = loopPoints.take(12),
                    starred = true
                )
            )
            repository.saveChallenge(
                currentUserId,
                ChallengeGoal(
                    userId = currentUserId,
                    title = "Monthly Endurance 150K",
                    sportType = SportType.RUN.name,
                    targetValue = 150.0,
                    currentValue = 58.9,
                    unit = "km",
                    periodLabel = "Current Month",
                    joined = true,
                    completed = false
                )
            )
            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            repository.upsertDailyHealthLog(
                currentUserId,
                DailyHealthLog(
                    userId = currentUserId,
                    dateKey = todayKey,
                    steps = 11_480,
                    activeCalories = 640,
                    totalCalories = 2380,
                    restingHr = 52,
                    latestHr = 64,
                    hrvRmssdMs = 66.5,
                    sleepTotalMinutes = 458,
                    sleepDeepMinutes = 104,
                    sleepLightMinutes = 236,
                    sleepRemMinutes = 98,
                    sleepAwakeMinutes = 20,
                    sleepScore = 88,
                    vo2MaxEstimate = 52.8,
                    recoveryScore = 86,
                    sourceDevice = "Wearable Device (Sample Sync)"
                )
            )
            _events.emit("Sample training block & Wearable Device sleep/VO2 data loaded!")
        }
    }

    private fun <T> Flow<UiState<T>>.unwrapFirestoreErrors(label: String): Flow<UiState<T>> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing $label", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load $label"))
        }

    private companion object {
        const val TAG = "MainVM"
    }
}
