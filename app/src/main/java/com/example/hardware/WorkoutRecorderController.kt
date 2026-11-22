package com.example.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.core.Formatters
import com.example.core.PerformanceEngine
import com.example.data.model.SportType
import com.example.data.model.SplitKm
import com.example.data.model.TrackPoint
import com.example.data.model.WorkoutActivity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class RecordingPhase {
    IDLE,
    RECORDING,
    PAUSED
}

data class LiveRecordingState(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val sportType: SportType = SportType.RUN,
    val elapsedSeconds: Long = 0L,
    val movingSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val currentAltitudeMeters: Double? = null,
    val elevationSource: String = "Barometer + GPS",
    val currentSpeedKmh: Double = 0.0,
    val currentPaceSecPerKm: Int = 0,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val liveHeartRate: Int = 0,
    val liveCadence: Int = 0,
    val estimatedCalories: Int = 0,
    val autoPauseEnabled: Boolean = true,
    val autoPausedNow: Boolean = false,
    val gpsAccuracyMeters: Float? = null,
    val trackPoints: List<TrackPoint> = emptyList(),
    val splits: List<SplitKm> = emptyList(),
    val hrZoneSeconds: List<Long> = listOf(0L, 0L, 0L, 0L, 0L),
    val beaconActive: Boolean = false,
    val lastLat: Double? = null,
    val lastLng: Double? = null,
)

class WorkoutRecorderController(
    private val context: Context,
    private val bleManager: HuaweiBleManager,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val pressureSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private val _state = MutableStateFlow(
        LiveRecordingState(
            elevationSource = if (pressureSensor != null) "Phone Barometer" else "GPS + Copernicus DEM"
        )
    )
    val state: StateFlow<LiveRecordingState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var maxHrBaseline = 192
    private var restingHrBaseline = 54
    private var lastLocation: Location? = null
    private var lastAltitude: Double? = null
    private var smoothedBaroAltitude: Double? = null
    private var baroAnchorAltitude: Double? = null
    private var hasActiveBarometer = false
    private var hrSampleSum = 0L
    private var hrSampleCount = 0L
    private var cadenceSampleSum = 0L
    private var cadenceSampleCount = 0L
    private var nextSplitTargetMeters = 1000.0
    private var splitStartSeconds = 0L
    private var splitStartElevation = 0.0
    private var splitHrSum = 0L
    private var splitHrCount = 0L

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun selectSport(sport: SportType) {
        if (_state.value.phase == RecordingPhase.IDLE) {
            _state.update { it.copy(sportType = sport) }
        }
    }

    fun setAutoPause(enabled: Boolean) {
        _state.update { it.copy(autoPauseEnabled = enabled) }
    }

    fun toggleBeacon() {
        _state.update { it.copy(beaconActive = !it.beaconActive) }
    }

    fun updateHrBaselines(maxHr: Int, restingHr: Int) {
        maxHrBaseline = maxHr.coerceIn(120, 225)
        restingHrBaseline = restingHr.coerceIn(35, 110)
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        resetCounters()
        bleManager.resetSessionCounters()
        _state.update {
            it.copy(
                phase = RecordingPhase.RECORDING,
                autoPausedNow = false,
                elevationSource = if (pressureSensor != null) "Phone Barometer" else "GPS + Copernicus DEM"
            )
        }
        startForegroundServiceNotification()
        pressureSensor?.let { sensor ->
            sensorManager?.registerListener(barometerListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (hasLocationPermission()) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .build()
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        }
        startTicker()
    }

    fun pauseRecording() {
        if (_state.value.phase == RecordingPhase.RECORDING) {
            _state.update { it.copy(phase = RecordingPhase.PAUSED, autoPausedNow = false) }
        }
    }

    fun resumeRecording() {
        if (_state.value.phase == RecordingPhase.PAUSED) {
            _state.update { it.copy(phase = RecordingPhase.RECORDING, autoPausedNow = false) }
        }
    }

    fun markManualLap() {
        val current = _state.value
        if (current.phase == RecordingPhase.IDLE) return
        val splitTime = (current.movingSeconds - splitStartSeconds).coerceAtLeast(1L).toInt()
        val splitAvgHr = if (splitHrCount > 0) (splitHrSum / splitHrCount).toInt() else current.liveHeartRate
        val newSplit = SplitKm(
            km = current.splits.size + 1,
            paceSec = splitTime,
            eleDiff = (current.elevationGainMeters - splitStartElevation),
            avgHr = splitAvgHr
        )
        splitStartSeconds = current.movingSeconds
        splitStartElevation = current.elevationGainMeters
        splitHrSum = 0L
        splitHrCount = 0L
        _state.update { it.copy(splits = it.splits + newSplit) }
    }

    fun stopAndBuildWorkout(
        userId: String,
        title: String,
        perceivedExertion: Int,
        gearId: String?,
        gearName: String?,
        privateActivity: Boolean,
        notes: String?,
    ): WorkoutActivity {
        stopLocationAndService()
        val s = _state.value
        val moving = s.movingSeconds.coerceAtLeast(1L)
        val avgSpeed = if (s.distanceMeters > 0) (s.distanceMeters / 1000.0) / (moving / 3600.0) else 0.0
        val avgPace = if (s.distanceMeters >= 50.0) ((moving / (s.distanceMeters / 1000.0)).roundToInt()) else 0
        val effort = PerformanceEngine.computeRelativeEffort(
            movingSeconds = moving,
            avgHr = s.avgHeartRate,
            maxHr = maxHrBaseline,
            restingHr = restingHrBaseline,
            perceivedExertion = perceivedExertion,
            hrZoneSeconds = s.hrZoneSeconds
        )
        val downsampled = downsampleTrackPoints(s.trackPoints, 250).map { it.toMap() }
        val splitMaps = s.splits.take(100).map { it.toMap() }
        val deviceLabel = bleManager.state.value.connectedDeviceName ?: "Phone GPS"

        val built = WorkoutActivity(
            userId = userId,
            title = title.ifBlank { "${s.sportType.label} Workout" },
            sportType = s.sportType.name,
            distanceMeters = s.distanceMeters,
            movingTimeSeconds = s.movingSeconds,
            elapsedTimeSeconds = s.elapsedSeconds,
            elevationGainMeters = (s.elevationGainMeters * 10.0).roundToInt() / 10.0,
            avgPaceSecPerKm = avgPace,
            avgSpeedKmh = (avgSpeed * 10.0).roundToInt() / 10.0,
            avgHeartRate = s.avgHeartRate,
            maxHeartRate = s.maxHeartRate,
            avgCadence = if (cadenceSampleCount > 0) (cadenceSampleSum / cadenceSampleCount).toInt() else 0,
            calories = s.estimatedCalories,
            relativeEffort = effort,
            perceivedExertion = perceivedExertion.coerceIn(1, 10),
            kudosCount = 0,
            kudosGiven = false,
            privateActivity = privateActivity,
            prCount = 0,
            hrZoneSeconds = s.hrZoneSeconds,
            routePoints = downsampled,
            splits = splitMaps,
            gearId = gearId,
            gearName = gearName,
            deviceName = deviceLabel,
            elevationSource = s.elevationSource,
            notes = notes?.takeIf { it.isNotBlank() }
        )

        resetCounters()
        _state.value = LiveRecordingState(
            sportType = s.sportType,
            elevationSource = if (pressureSensor != null) "Phone Barometer" else "GPS + Copernicus DEM"
        )
        return built
    }

    fun discardRecording() {
        stopLocationAndService()
        val prevSport = _state.value.sportType
        resetCounters()
        _state.value = LiveRecordingState(
            sportType = prevSport,
            elevationSource = if (pressureSensor != null) "Phone Barometer" else "GPS + Copernicus DEM"
        )
    }

    private fun stopLocationAndService() {
        timerJob?.cancel()
        timerJob = null
        try {
            sensorManager?.unregisterListener(barometerListener)
        } catch (_: Exception) {}
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
        val stopIntent = Intent(appContext, GpsTrackingService::class.java).apply {
            action = GpsTrackingService.ACTION_STOP
        }
        try {
            appContext.startService(stopIntent)
        } catch (_: Exception) {}
    }

    private fun startForegroundServiceNotification() {
        if (!hasLocationPermission()) return
        val intent = Intent(appContext, GpsTrackingService::class.java).apply {
            action = GpsTrackingService.ACTION_START
            putExtra(GpsTrackingService.EXTRA_TITLE, "VeloStride • ${_state.value.sportType.label}")
            putExtra(GpsTrackingService.EXTRA_SUMMARY, "Recording GPS, Barometer & Wearable Telemetry")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (_: Exception) {}
    }

    private val barometerListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_PRESSURE) return
            if (_state.value.phase != RecordingPhase.RECORDING) return
            val hPa = event.values.firstOrNull() ?: return
            val rawAlt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hPa).toDouble()
            if (rawAlt.isNaN() || rawAlt.isInfinite()) return

            hasActiveBarometer = true
            val smoothed = smoothedBaroAltitude?.let { prev -> prev * 0.78 + rawAlt * 0.22 } ?: rawAlt
            smoothedBaroAltitude = smoothed

            var gainDelta = 0.0
            val anchor = baroAnchorAltitude
            if (anchor == null) {
                baroAnchorAltitude = smoothed
            } else if (!_state.value.autoPausedNow) {
                val diff = smoothed - anchor
                if (diff >= 1.2) {
                    gainDelta = diff
                    baroAnchorAltitude = smoothed
                } else if (smoothed < anchor - 1.2) {
                    baroAnchorAltitude = smoothed
                }
            }

            _state.update { current ->
                current.copy(
                    currentAltitudeMeters = (smoothed * 10.0).roundToInt() / 10.0,
                    elevationGainMeters = current.elevationGainMeters + gainDelta,
                    elevationSource = "Phone Barometer"
                )
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun startTicker() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(1000L)
                val ble = bleManager.state.value
                val liveBpm = ble.liveHeartRateBpm
                val liveCad = ble.liveCadenceSpm
                _state.update { current ->
                    if (current.phase != RecordingPhase.RECORDING) {
                        current.copy(liveHeartRate = liveBpm, liveCadence = liveCad)
                    } else {
                        val nextElapsed = current.elapsedSeconds + 1L
                        val isMoving = !current.autoPausedNow
                        val nextMoving = if (isMoving) current.movingSeconds + 1L else current.movingSeconds

                        val zones = current.hrZoneSeconds.toMutableList()
                        var nextMaxHr = current.maxHeartRate
                        if (liveBpm > 0 && isMoving) {
                            hrSampleSum += liveBpm
                            hrSampleCount++
                            splitHrSum += liveBpm
                            splitHrCount++
                            if (liveBpm > nextMaxHr) nextMaxHr = liveBpm
                            val zoneIdx = (Formatters.computeHrZone(liveBpm, maxHrBaseline) - 1).coerceIn(0, 4)
                            zones[zoneIdx] = zones[zoneIdx] + 1L
                        }
                        if (liveCad > 0 && isMoving) {
                            cadenceSampleSum += liveCad
                            cadenceSampleCount++
                        }

                        val avgHr = if (hrSampleCount > 0) (hrSampleSum / hrSampleCount).toInt() else 0
                        val kcalPerMin = if (avgHr > 70) {
                            ((avgHr * 0.145) - 5.2).coerceIn(4.0, 18.0)
                        } else {
                            when (current.sportType) {
                                SportType.RUN, SportType.TRAIL_RUN -> 10.5
                                SportType.CYCLE, SportType.GRAVEL_RIDE, SportType.MTB -> 8.8
                                SportType.WORKOUT, SportType.ROWING, SportType.ELLIPTICAL -> 7.8
                                SportType.SWIM -> 9.2
                                else -> 5.8
                            }
                        }
                        val kcal = if (ble.sessionBleCalories > 0) {
                            ble.sessionBleCalories
                        } else {
                            ((nextMoving / 60.0) * kcalPerMin).roundToInt()
                        }

                        current.copy(
                            elapsedSeconds = nextElapsed,
                            movingSeconds = nextMoving,
                            liveHeartRate = liveBpm,
                            liveCadence = liveCad,
                            avgHeartRate = avgHr,
                            maxHeartRate = nextMaxHr,
                            hrZoneSeconds = zones,
                            estimatedCalories = kcal
                        )
                    }
                }
            }
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            if (_state.value.phase != RecordingPhase.RECORDING) return
            if (loc.hasAccuracy() && loc.accuracy > 35f) return

            val speedKmh = if (loc.hasSpeed()) (loc.speed * 3.6) else 0.0
            val shouldAutoPause = _state.value.autoPauseEnabled && speedKmh < 0.7 && lastLocation != null

            var distDelta = 0.0
            var eleDelta = 0.0
            val prev = lastLocation
            if (prev != null && !shouldAutoPause) {
                val d = Formatters.haversineMeters(prev.latitude, prev.longitude, loc.latitude, loc.longitude)
                if (d in 1.5..250.0) {
                    distDelta = d
                }
            }
            // Only integrate GPS altitude delta if phone does NOT have a live barometric sensor
            val pointAlt = smoothedBaroAltitude ?: if (loc.hasAltitude()) loc.altitude else 0.0
            if (!hasActiveBarometer && loc.hasAltitude() && !shouldAutoPause) {
                val prevAlt = lastAltitude
                if (prevAlt != null) {
                    val diff = loc.altitude - prevAlt
                    if (diff in 0.8..35.0) {
                        eleDelta = diff
                    }
                }
                lastAltitude = loc.altitude
            }
            lastLocation = loc

            _state.update { current ->
                val newDist = current.distanceMeters + distDelta
                val newEle = current.elevationGainMeters + eleDelta
                val paceSec = if (speedKmh > 0.8) ((3600.0 / speedKmh).roundToInt()) else 0
                val newPoint = TrackPoint(
                    lat = loc.latitude,
                    lng = loc.longitude,
                    ele = pointAlt,
                    hr = bleManager.state.value.liveHeartRateBpm
                )
                val updatedPoints = if (distDelta >= 3.0 || current.trackPoints.isEmpty()) {
                    current.trackPoints + newPoint
                } else {
                    current.trackPoints
                }

                val updatedSplits = current.splits.toMutableList()
                if (newDist >= nextSplitTargetMeters) {
                    val splitTime = (current.movingSeconds - splitStartSeconds).coerceAtLeast(1L).toInt()
                    val splitHr = if (splitHrCount > 0) (splitHrSum / splitHrCount).toInt() else current.liveHeartRate
                    updatedSplits += SplitKm(
                        km = updatedSplits.size + 1,
                        paceSec = splitTime,
                        eleDiff = newEle - splitStartElevation,
                        avgHr = splitHr
                    )
                    nextSplitTargetMeters += 1000.0
                    splitStartSeconds = current.movingSeconds
                    splitStartElevation = newEle
                    splitHrSum = 0L
                    splitHrCount = 0L
                }

                current.copy(
                    distanceMeters = newDist,
                    elevationGainMeters = newEle,
                    currentAltitudeMeters = if (pointAlt != 0.0) (pointAlt * 10.0).roundToInt() / 10.0 else current.currentAltitudeMeters,
                    currentSpeedKmh = (speedKmh * 10.0).roundToInt() / 10.0,
                    currentPaceSecPerKm = paceSec,
                    autoPausedNow = shouldAutoPause,
                    gpsAccuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
                    trackPoints = updatedPoints,
                    splits = updatedSplits,
                    lastLat = loc.latitude,
                    lastLng = loc.longitude
                )
            }
        }
    }

    private fun resetCounters() {
        lastLocation = null
        lastAltitude = null
        smoothedBaroAltitude = null
        baroAnchorAltitude = null
        hasActiveBarometer = false
        hrSampleSum = 0L
        hrSampleCount = 0L
        cadenceSampleSum = 0L
        cadenceSampleCount = 0L
        nextSplitTargetMeters = 1000.0
        splitStartSeconds = 0L
        splitStartElevation = 0.0
        splitHrSum = 0L
        splitHrCount = 0L
    }

    private fun downsampleTrackPoints(points: List<TrackPoint>, maxCount: Int): List<TrackPoint> {
        if (points.size <= maxCount) return points
        val step = points.size.toDouble() / maxCount
        return (0 until maxCount).map { i ->
            points[(i * step).toInt().coerceAtMost(points.lastIndex)]
        }
    }
}
