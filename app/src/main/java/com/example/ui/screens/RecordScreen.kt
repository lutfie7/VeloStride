package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.Formatters
import com.example.core.LocalUnitSystem
import com.example.core.rememberAppStrings
import com.example.data.model.BleConnectionStatus
import com.example.data.model.GearItem
import com.example.data.model.SportType
import com.example.data.model.UiState
import com.example.hardware.RecordingPhase
import com.example.ui.MainViewModel
import com.example.ui.components.MetricCell
import com.example.ui.components.RoutePolylineCanvas
import com.example.ui.components.SportBadge
import com.example.ui.components.SportSelectorGrid
import com.example.ui.theme.AmberZone
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.EmeraldPeak
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange
import kotlin.math.roundToInt

@Composable
fun RecordScreen(
    viewModel: MainViewModel,
    onWorkoutSaved: (String) -> Unit,
    onNavigateToDevices: () -> Unit,
    onOpenManualEntry: () -> Unit,
) {
    val context = LocalContext.current
    val unitSystem = LocalUnitSystem.current
    val strings = rememberAppStrings()
    val rec by viewModel.recordingState.collectAsStateWithLifecycle()
    val ble by viewModel.bleState.collectAsStateWithLifecycle()
    val gearState by viewModel.gearState.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()

    val maxHr = (profileState as? UiState.Success)?.data?.maxHr ?: 192
    val activeGear = (gearState as? UiState.Success)?.data?.filter { !it.retired }.orEmpty()

    var showSaveDialog by remember { mutableStateOf(false) }

    val requiredRecordPerms = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.recorderController.startRecording()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Live GPS & Wearable Status Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val hasLoc = viewModel.recorderController.hasLocationPermission()
                        Icon(
                            imageVector = if (hasLoc) Icons.Rounded.GpsFixed else Icons.Rounded.GpsNotFixed,
                            contentDescription = "GPS status",
                            tint = if (hasLoc) EmeraldPeak else AmberZone,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = when {
                                    rec.gpsAccuracyMeters != null -> "GPS Locked (±${rec.gpsAccuracyMeters!!.toInt()}m)"
                                    hasLoc -> "GPS Ready"
                                    else -> "Location Access Required"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = if (rec.autoPausedNow) {
                                    "Auto-Paused (stationary)"
                                } else {
                                    "Elev: ${rec.elevationSource}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (rec.autoPausedNow) AmberZone else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val isConnectedBle = ble.status == BleConnectionStatus.CONNECTED
                    val batteryDisplay = rec.wearableBatteryPercent.takeIf { it >= 0 } ?: ble.batteryPercent
                    Surface(
                        color = if (isConnectedBle) {
                            EmeraldPeak.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable { onNavigateToDevices() }
                            .testTag("record_wearable_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Watch,
                                contentDescription = "Wearable Device telemetry",
                                tint = if (isConnectedBle) EmeraldPeak else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            val deviceTitle = (rec.telemetryDeviceName ?: ble.connectedDeviceName).orEmpty()
                            Text(
                                text = when {
                                    isConnectedBle && batteryDisplay >= 0 -> "${deviceTitle.ifBlank { "Wearable" }} • $batteryDisplay%"
                                    isConnectedBle -> deviceTitle.ifBlank { "Wearable Live" }
                                    else -> "Pair Wearable"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 2. Sport Selector (only editable when IDLE)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (rec.phase == RecordingPhase.IDLE) {
                        SportSelectorGrid(
                            selectedSport = rec.sportType,
                            onSelect = { viewModel.recorderController.selectSport(it) }
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SportBadge(rec.sportType)
                            Surface(
                                color = if (rec.phase == RecordingPhase.RECORDING) VeloOrange.copy(alpha = 0.2f) else AmberZone.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (rec.phase == RecordingPhase.RECORDING) "LIVE RECORDING" else "PAUSED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (rec.phase == RecordingPhase.RECORDING) VeloOrange else AmberZone,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Telemetry Cockpit HUD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MOVING TIME",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = Formatters.formatDuration(rec.movingSeconds),
                        fontSize = 48.sp,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        MetricCell(
                            label = strings.distanceLabel,
                            value = Formatters.formatDistanceKm(rec.distanceMeters, unitSystem),
                            valueColor = VeloOrange
                        )
                        MetricCell(
                            label = if (rec.sportType.isPaceBased) strings.avgPaceLabel else strings.avgSpeedLabel,
                            value = if (rec.sportType.isPaceBased) {
                                Formatters.formatPace(rec.currentPaceSecPerKm, unitSystem)
                            } else {
                                Formatters.formatSpeed(rec.currentSpeedKmh, unitSystem)
                            }
                        )
                        MetricCell(
                            label = if (rec.currentAltitudeMeters != null) {
                                "Gain (${Formatters.formatElevation(rec.currentAltitudeMeters!!, unitSystem)} alt)"
                            } else {
                                strings.elevGainLabel
                            },
                            value = Formatters.formatElevation(rec.elevationGainMeters, unitSystem),
                            valueColor = SignalCyan
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    val liveHr = rec.liveHeartRate.takeIf { it > 0 } ?: ble.liveHeartRateBpm
                    val liveHrv = rec.liveHrvMs.takeIf { it > 0.0 } ?: ble.hrvRmssdMs
                    val liveSpo2 = rec.liveSpo2Percent.takeIf { it > 0.0 } ?: ble.liveSpo2Percent
                    val liveCad = rec.liveCadence.takeIf { it > 0 } ?: ble.liveCadenceSpm
                    val liveSteps = rec.sessionSteps.takeIf { it > 0 } ?: ble.sessionBleSteps
                    val zone = Formatters.computeHrZone(liveHr, maxHr)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        MetricCell(
                            label = if (zone > 0) "HR (${Formatters.hrZoneLabel(zone)})" else "Heart Rate",
                            value = if (liveHr > 0) "$liveHr bpm" else "-- bpm",
                            valueColor = if (liveHr > 0) CrimsonPulse else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MetricCell(
                            label = "Cadence",
                            value = if (liveCad > 0) "$liveCad spm" else "--"
                        )
                        MetricCell(
                            label = "Calories",
                            value = "${rec.estimatedCalories} kcal"
                        )
                    }

                    if (ble.status == BleConnectionStatus.CONNECTED || liveHrv > 0.0 || liveSpo2 > 0.0 || liveSteps > 0) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            MetricCell(
                                label = "HRV RMSSD",
                                value = if (liveHrv > 0.0) "$liveHrv ms" else "--",
                                valueColor = SignalCyan
                            )
                            MetricCell(
                                label = "SpO2",
                                value = if (liveSpo2 > 0.0) "$liveSpo2%" else "--",
                                valueColor = EmeraldPeak
                            )
                            MetricCell(
                                label = "Steps",
                                value = if (liveSteps > 0) "$liveSteps" else "--"
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        val isStreaming = rec.isWearableStreaming || liveHr > 0 || liveCad > 0 || liveSpo2 > 0.0
                        Surface(
                            color = if (isStreaming) {
                                EmeraldPeak.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("record_wearable_telemetry_banner")
                        ) {
                            Text(
                                text = if (isStreaming) {
                                    "Wearable telemetry streaming directly from ${ble.connectedDeviceName ?: "device"}"
                                } else {
                                    "${ble.connectedDeviceName ?: "Wearable"} connected — enable Heart Rate Broadcast on watch for live BPM (Health Connect HR auto-fills on save)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isStreaming) EmeraldPeak else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Live Polyline Preview + Controls
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoutePolylineCanvas(
                        points = rec.trackPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(172.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Auto-Pause", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = rec.autoPauseEnabled,
                                onCheckedChange = { viewModel.recorderController.setAutoPause(it) },
                                modifier = Modifier.testTag("auto_pause_switch")
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.recorderController.toggleBeacon()
                                val locText = if (rec.lastLat != null && rec.lastLng != null) {
                                    "https://maps.google.com/?q=${rec.lastLat},${rec.lastLng}"
                                } else {
                                    "GPS lock pending"
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "VeloStride Live Beacon: Tracking ${rec.sportType.label} • ${Formatters.formatDistanceKm(rec.distanceMeters)} in ${Formatters.formatDuration(rec.movingSeconds)}. Location: $locText"
                                    )
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Live Beacon"))
                            },
                            modifier = Modifier.testTag("beacon_share_btn")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = "Live Beacon Share", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (rec.beaconActive) "Beacon ON" else "Beacon")
                        }
                    }

                    // Primary Recording Actions
                    when (rec.phase) {
                        RecordingPhase.IDLE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (viewModel.recorderController.hasLocationPermission()) {
                                            viewModel.recorderController.startRecording()
                                        } else {
                                            permLauncher.launch(requiredRecordPerms)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .testTag("record_start_btn")
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Start ${rec.sportType.label}")
                                }
                                OutlinedButton(
                                    onClick = onOpenManualEntry,
                                    modifier = Modifier
                                        .height(54.dp)
                                        .testTag("record_manual_log_btn")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Manual")
                                }
                            }
                        }
                        RecordingPhase.RECORDING -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.recorderController.pauseRecording() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberZone, contentColor = MaterialTheme.colorScheme.onPrimary),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .testTag("record_pause_btn")
                                ) {
                                    Icon(Icons.Rounded.Pause, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Pause")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.recorderController.markManualLap() },
                                    modifier = Modifier
                                        .height(54.dp)
                                        .testTag("record_lap_btn")
                                ) {
                                    Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Lap (${rec.splits.size})")
                                }
                            }
                        }
                        RecordingPhase.PAUSED -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.recorderController.resumeRecording() },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPeak),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("record_resume_btn")
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Resume")
                                }
                                Button(
                                    onClick = { showSaveDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("record_finish_btn")
                                ) {
                                    Icon(Icons.Rounded.Stop, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Finish")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.recorderController.discardRecording() },
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("record_discard_btn")
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Discard workout", tint = CrimsonPulse)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveRecordedWorkoutDialog(
            defaultTitle = "${rec.sportType.label} Session",
            gearOptions = activeGear,
            onDismiss = { showSaveDialog = false },
            onSave = { title, rpe, gear, isPrivate, notes ->
                showSaveDialog = false
                viewModel.finishAndSaveRecordedWorkout(
                    title = title,
                    perceivedExertion = rpe,
                    gear = gear,
                    privateActivity = isPrivate,
                    notes = notes,
                    onSaved = onWorkoutSaved
                )
            }
        )
    }
}

@Composable
private fun SaveRecordedWorkoutDialog(
    defaultTitle: String,
    gearOptions: List<GearItem>,
    onDismiss: () -> Unit,
    onSave: (String, Int, GearItem?, Boolean, String) -> Unit,
) {
    var title by remember { mutableStateOf(defaultTitle) }
    var rpe by remember { mutableFloatStateOf(6f) }
    var selectedGear by remember { mutableStateOf(gearOptions.firstOrNull { it.primaryGear } ?: gearOptions.firstOrNull()) }
    var isPrivate by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Activity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Activity Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Perceived Exertion (RPE): ${rpe.roundToInt()} / 10", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = rpe,
                    onValueChange = { rpe = it },
                    valueRange = 1f..10f,
                    steps = 8
                )
                if (gearOptions.isNotEmpty()) {
                    Text("Gear Used", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(gearOptions, key = { it.id }) { gear ->
                            FilterChip(
                                selected = selectedGear?.id == gear.id,
                                onClick = { selectedGear = if (selectedGear?.id == gear.id) null else gear },
                                label = { Text("${gear.name} (${gear.parsedCategory.label})", maxLines = 1) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("How did it feel? (Optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Private Activity", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, rpe.roundToInt(), selectedGear, isPrivate, notes) },
                modifier = Modifier.testTag("confirm_save_recorded_btn")
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep Editing") }
        }
    )
}

@Composable
fun ManualWorkoutDialog(
    gearOptions: List<GearItem>,
    defaultSport: SportType = SportType.RUN,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        sportType: SportType,
        distanceKm: Double,
        durationMinutes: Int,
        elevationMeters: Double,
        avgHr: Int,
        rpe: Int,
        gear: GearItem?,
    ) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var sport by remember { mutableStateOf(defaultSport) }
    var distKm by remember { mutableStateOf("5.0") }
    var durationMin by remember { mutableStateOf("35") }
    var eleMeters by remember { mutableStateOf("45") }
    var avgHr by remember { mutableStateOf("152") }
    var rpe by remember { mutableFloatStateOf(6f) }
    var selectedGear by remember { mutableStateOf(gearOptions.firstOrNull { it.primaryGear }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Activity Manually") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Workout Title") },
                        placeholder = { Text("Morning ${sport.label}") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_title_input")
                    )
                }
                item {
                    SportSelectorGrid(selectedSport = sport, onSelect = { sport = it })
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = distKm,
                            onValueChange = { distKm = it },
                            label = { Text("Dist (km)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("manual_distance_input")
                        )
                        OutlinedTextField(
                            value = durationMin,
                            onValueChange = { durationMin = it },
                            label = { Text("Mins") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("manual_duration_input")
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = eleMeters,
                            onValueChange = { eleMeters = it },
                            label = { Text("Elev (m)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = avgHr,
                            onValueChange = { avgHr = it },
                            label = { Text("Avg HR") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Text("Perceived Effort (RPE): ${rpe.roundToInt()}/10", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = rpe,
                        onValueChange = { rpe = it },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                }
                if (gearOptions.isNotEmpty()) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(gearOptions, key = { it.id }) { gear ->
                                FilterChip(
                                    selected = selectedGear?.id == gear.id,
                                    onClick = { selectedGear = if (selectedGear?.id == gear.id) null else gear },
                                    label = { Text("${gear.name} (${gear.parsedCategory.label})", maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        title.ifBlank { "Morning ${sport.label}" },
                        sport,
                        distKm.toDoubleOrNull() ?: 5.0,
                        durationMin.toIntOrNull() ?: 35,
                        eleMeters.toDoubleOrNull() ?: 20.0,
                        avgHr.toIntOrNull() ?: 150,
                        rpe.roundToInt(),
                        selectedGear
                    )
                },
                modifier = Modifier.testTag("confirm_manual_workout_btn")
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Log Activity")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
