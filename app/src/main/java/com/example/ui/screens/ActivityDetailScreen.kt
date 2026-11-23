package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ActivityShareHelper
import com.example.core.Formatters
import com.example.core.GpxSerializer
import com.example.core.ShareCardConfig
import com.example.data.model.UiState
import com.example.ui.MainViewModel
import com.example.ui.components.ElevationAndHrChart
import com.example.ui.components.FlyoverMapDialog
import com.example.ui.components.HrZonesBarChart
import com.example.ui.components.MetricCell
import com.example.ui.components.RoutePolylineCanvas
import com.example.ui.components.ShareActivityDialog
import com.example.ui.components.SportBadge
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    workoutId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val workoutsState by viewModel.workoutsState.collectAsStateWithLifecycle()
    val insightState by viewModel.workoutInsightState.collectAsStateWithLifecycle()

    DisposableEffect(workoutId) {
        onDispose { viewModel.clearWorkoutInsightPreview() }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workout = (workoutsState as? UiState.Success)?.data?.firstOrNull { it.id == workoutId }
    var showShareDialog by remember { mutableStateOf(false) }
    var showFlyoverDialog by remember { mutableStateOf(false) }

    val exportGpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        if (uri != null && workout != null) {
            viewModel.exportWorkoutGpxToUri(context, uri, workout)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workout?.title ?: "Activity Detail", maxLines = 1) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (workout != null) {
                        if (workout.decodedPoints.size >= 2) {
                            IconButton(
                                onClick = {
                                    exportGpxLauncher.launch(GpxSerializer.suggestedGpxFileName(workout.title))
                                },
                                modifier = Modifier.testTag("detail_export_gpx_btn")
                            ) {
                                Icon(Icons.Rounded.FileDownload, contentDescription = "Export GPX")
                            }
                        }
                        IconButton(
                            onClick = { showShareDialog = true },
                            modifier = Modifier.testTag("detail_share_btn")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share activity")
                        }
                        IconButton(
                            onClick = { viewModel.deleteWorkout(workout.id, onBack) },
                            modifier = Modifier.testTag("detail_delete_btn")
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete activity")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (workout == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Workout not found or loading...", style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Map & Header
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SportBadge(workout.parsedSport)
                            Text(
                                text = Formatters.formatDateTime(workout.createdAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        RoutePolylineCanvas(
                            points = workout.decodedPoints,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        )
                        if (workout.decodedPoints.size >= 2) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { showFlyoverDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("detail_flyover_btn")
                            ) {
                                Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Play 3D Flyover Map Animator")
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricCell("Distance", Formatters.formatDistanceKm(workout.distanceMeters), valueColor = VeloOrange)
                            MetricCell("Moving Time", Formatters.formatDuration(workout.movingTimeSeconds))
                            MetricCell("Elevation", Formatters.formatElevation(workout.elevationGainMeters))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricCell(
                                label = if (workout.parsedSport.isPaceBased) "Avg Pace" else "Avg Speed",
                                value = if (workout.parsedSport.isPaceBased) Formatters.formatPace(workout.avgPaceSecPerKm) else Formatters.formatSpeed(workout.avgSpeedKmh)
                            )
                            MetricCell(
                                label = "Avg / Max HR",
                                value = if (workout.avgHeartRate > 0) "${workout.avgHeartRate}/${workout.maxHeartRate}" else "--",
                                valueColor = CrimsonPulse
                            )
                            MetricCell("Calories", "${workout.calories} kcal")
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricCell("Rel. Effort", "${workout.relativeEffort}")
                            MetricCell("Cadence", if (workout.avgCadence > 0) "${workout.avgCadence} spm" else "--")
                            MetricCell("Elev. Source", workout.elevationSource ?: "GPS")
                        }
                        if (!workout.deviceName.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Device: ${workout.deviceName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Athlete Intelligence (Gemini AI)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = SignalCyan)
                                Spacer(Modifier.width(8.dp))
                                Text("Athlete Intelligence", style = MaterialTheme.typography.titleLarge)
                            }
                            Button(
                                onClick = { viewModel.generateWorkoutAiInsight(workout) },
                                colors = ButtonDefaults.buttonColors(containerColor = SignalCyan, contentColor = MaterialTheme.colorScheme.onSecondary),
                                modifier = Modifier.testTag("generate_workout_insight_btn")
                            ) {
                                Text(if (workout.aiInsight.isNullOrBlank()) "Analyze" else "Refresh")
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        when (val ai = insightState) {
                            is UiState.Loading -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Analyzing heart rate drift, pace & recovery impact...", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            is UiState.Error -> {
                                Text(ai.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                            is UiState.Success -> {
                                Text(ai.data, style = MaterialTheme.typography.bodyLarge)
                            }
                            null -> {
                                if (!workout.aiInsight.isNullOrBlank()) {
                                    Text(workout.aiInsight, style = MaterialTheme.typography.bodyLarge)
                                } else {
                                    Text(
                                        "Tap Analyze for AI-powered pacing verdict, cardiac efficiency analysis, and recovery guidance.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Elevation & HR Chart
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Elevation & Heart Rate Profile", style = MaterialTheme.typography.titleMedium)
                            if (workout.decodedPoints.size >= 2) {
                                OutlinedButton(
                                    onClick = { viewModel.enrichWorkoutElevation(workout) },
                                    modifier = Modifier.testTag("enrich_dem_elevation_btn")
                                ) {
                                    Icon(Icons.Rounded.Terrain, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("DEM Elev")
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        ElevationAndHrChart(points = workout.decodedPoints)
                    }
                }
            }

            // HR Zones
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Heart Rate Zones", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        HrZonesBarChart(zoneSeconds = workout.hrZoneSeconds)
                    }
                }
            }

            // Splits
            val splits = workout.decodedSplits
            if (splits.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Splits", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("KM", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
                                Text("PACE", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(72.dp))
                                Text("ELEV", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
                                Text("AVG HR", style = MaterialTheme.typography.labelSmall)
                            }
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            splits.forEach { sp ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${sp.km}", fontFamily = JetBrainsMonoFontFamily, modifier = Modifier.width(40.dp))
                                    Text(Formatters.formatPaceShort(sp.paceSec), fontFamily = JetBrainsMonoFontFamily, modifier = Modifier.width(72.dp))
                                    Text("${sp.eleDiff.toInt()}m", fontFamily = JetBrainsMonoFontFamily, modifier = Modifier.width(56.dp))
                                    Text(if (sp.avgHr > 0) "${sp.avgHr} bpm" else "--", fontFamily = JetBrainsMonoFontFamily, color = CrimsonPulse)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showShareDialog && workout != null) {
        ShareActivityDialog(
            workout = workout,
            onExportGpx = {
                exportGpxLauncher.launch(GpxSerializer.suggestedGpxFileName(workout.title))
            },
            onDismiss = { showShareDialog = false }
        )
    }

    if (showFlyoverDialog && workout != null) {
        FlyoverMapDialog(
            workout = workout,
            onShareFrame = { progress ->
                scope.launch {
                    val backdrop = ActivityShareHelper.fetchShareCardMapBackdrop(workout, darkMap = true)
                    ActivityShareHelper.shareAsImageCard(
                        context = context,
                        workout = workout,
                        config = ShareCardConfig(showMap = true),
                        mapBackdrop = backdrop,
                        progressFraction = progress
                    )
                }
            },
            onDismiss = { showFlyoverDialog = false }
        )
    }
}
