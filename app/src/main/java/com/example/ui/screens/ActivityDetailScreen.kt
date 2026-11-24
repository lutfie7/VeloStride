package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ActivityShareHelper
import com.example.core.Formatters
import com.example.core.GpxSerializer
import com.example.core.LocalUnitSystem
import com.example.core.ShareCardConfig
import com.example.core.rememberAppStrings
import com.example.data.model.UiState
import com.example.data.repository.ElevationRepository
import com.example.ui.MainViewModel
import com.example.ui.components.EditActivityDialog
import com.example.ui.components.ElevationAndHrChart
import com.example.ui.components.FlyoverMapDialog
import com.example.ui.components.HrZonesBarChart
import com.example.ui.components.MetricCell
import com.example.ui.components.RoutePolylineCanvas
import com.example.ui.components.ShareActivityDialog
import com.example.ui.components.SportBadge
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.EmeraldPeak
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    workoutId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val workoutsState by viewModel.workoutsState.collectAsStateWithLifecycle()
    val gearState by viewModel.gearState.collectAsStateWithLifecycle()
    val insightState by viewModel.workoutInsightState.collectAsStateWithLifecycle()

    DisposableEffect(workoutId) {
        onDispose { viewModel.clearWorkoutInsightPreview() }
    }

    val context = LocalContext.current
    val unitSystem = LocalUnitSystem.current
    val strings = rememberAppStrings()
    val scope = rememberCoroutineScope()
    val workout = (workoutsState as? UiState.Success)?.data?.firstOrNull { it.id == workoutId }
    val gearOptions = (gearState as? UiState.Success)?.data.orEmpty()
    var showShareDialog by remember { mutableStateOf(false) }
    var showFlyoverDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var previewMediaUri by remember { mutableStateOf<String?>(null) }

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
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.testTag("detail_edit_btn")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit activity")
                        }
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
                            onClick = { showDeleteConfirm = true },
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
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SportBadge(workout.parsedSport)
                            Text(
                                text = Formatters.formatDateTime(workout.createdAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RoutePolylineCanvas(
                            points = workout.decodedPoints,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(390.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Column(Modifier.padding(16.dp)) {
                        if (workout.decodedPoints.size >= 2) {
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
                            Spacer(Modifier.height(14.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricCell(strings.distanceLabel, Formatters.formatDistanceKm(workout.distanceMeters, unitSystem), valueColor = VeloOrange)
                            MetricCell(strings.movingTimeLabel, Formatters.formatDuration(workout.movingTimeSeconds))
                            MetricCell(strings.elevationLabel, Formatters.formatElevation(workout.elevationGainMeters, unitSystem))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricCell(
                                label = if (workout.parsedSport.isPaceBased) strings.avgPaceLabel else strings.avgSpeedLabel,
                                value = if (workout.parsedSport.isPaceBased) Formatters.formatPace(workout.avgPaceSecPerKm, unitSystem) else Formatters.formatSpeed(workout.avgSpeedKmh, unitSystem)
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
                        if (!workout.deviceName.isNullOrBlank() || !workout.gearName.isNullOrBlank() || workout.privateActivity) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (workout.privateActivity) {
                                    Surface(
                                        color = VeloOrange.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = VeloOrange, modifier = Modifier.size(12.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Private", style = MaterialTheme.typography.labelSmall, color = VeloOrange)
                                        }
                                    }
                                }
                                val metaText = listOfNotNull(
                                    workout.deviceName?.takeIf { it.isNotBlank() }?.let { "Device: $it" },
                                    workout.gearName?.takeIf { it.isNotBlank() }?.let { "Gear: $it" }
                                ).joinToString(" • ")
                                if (metaText.isNotBlank()) {
                                    Text(
                                        text = metaText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (!workout.notes.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = workout.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        }
                    }
                }
            }

            // Activity Media Gallery
            if (workout.mediaUris.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = SignalCyan)
                                Spacer(Modifier.width(8.dp))
                                Text("Activity Photos (${workout.mediaUris.size})", style = MaterialTheme.typography.titleMedium)
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(workout.mediaUris, key = { it }) { uriStr ->
                                    AsyncImage(
                                        model = uriStr,
                                        contentDescription = "Activity photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(132.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { previewMediaUri = uriStr }
                                    )
                                }
                            }
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
                                enabled = insightState !is UiState.Loading,
                                colors = ButtonDefaults.buttonColors(containerColor = SignalCyan, contentColor = MaterialTheme.colorScheme.onSecondary),
                                modifier = Modifier.testTag("generate_workout_insight_btn")
                            ) {
                                Text(
                                    when {
                                        insightState is UiState.Loading -> "Analyzing..."
                                        insightState is UiState.Error -> "Retry"
                                        workout.aiInsight.isNullOrBlank() -> "Analyze"
                                        else -> "Refresh"
                                    }
                                )
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
            val rawSplits = workout.decodedSplits
            if (rawSplits.isNotEmpty()) {
                item {
                    val splits = remember(workout.id, rawSplits, workout.routePoints) {
                        val pts = workout.decodedPoints
                        val hasNegativeInSaved = rawSplits.any { it.eleDiff < 0.0 }
                        val hasTrackDescent = pts.size >= 2 && (1 until pts.size).any { pts[it].ele < pts[it - 1].ele - 0.5 }
                        if (!hasNegativeInSaved && hasTrackDescent) {
                            ElevationRepository().recomputeSplitElevations(pts, rawSplits)
                        } else {
                            rawSplits
                        }
                    }
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
                                Text("ELEV", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
                                Text("AVG HR", style = MaterialTheme.typography.labelSmall)
                            }
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            splits.forEach { sp ->
                                val eleRounded = sp.eleDiff.roundToInt()
                                val eleText = Formatters.formatElevationSigned(sp.eleDiff, unitSystem)
                                val eleColor = when {
                                    eleRounded > 0 -> EmeraldPeak
                                    eleRounded < 0 -> SignalCyan
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${sp.km}", fontFamily = JetBrainsMonoFontFamily, modifier = Modifier.width(40.dp))
                                    Text(Formatters.formatPaceShort(sp.paceSec, unitSystem), fontFamily = JetBrainsMonoFontFamily, modifier = Modifier.width(72.dp))
                                    Text(
                                        text = eleText,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = eleColor,
                                        modifier = Modifier.width(64.dp)
                                    )
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
            onShareFrame = { progress, ratio ->
                scope.launch {
                    val cfg = ShareCardConfig(showMap = true, ratio = ratio)
                    val (boxW, boxH) = ActivityShareHelper.computeMapBoxSize(cfg)
                    val backdrop = ActivityShareHelper.fetchShareCardMapBackdrop(
                        workout = workout,
                        darkMap = true,
                        boxWidth = boxW,
                        boxHeight = boxH
                    )
                    ActivityShareHelper.shareAsImageCard(
                        context = context,
                        workout = workout,
                        config = cfg,
                        mapBackdrop = backdrop,
                        progressFraction = progress
                    )
                }
            },
            onDismiss = { showFlyoverDialog = false }
        )
    }

    if (showEditDialog && workout != null) {
        EditActivityDialog(
            workout = workout,
            gearOptions = gearOptions,
            onDismiss = { showEditDialog = false },
            onSave = { title, sportType, gear, rpe, isPrivate, notes, mediaUris ->
                showEditDialog = false
                viewModel.updateWorkout(
                    existing = workout,
                    title = title,
                    sportType = sportType,
                    gear = gear,
                    perceivedExertion = rpe,
                    privateActivity = isPrivate,
                    notes = notes,
                    mediaUris = mediaUris
                )
            },
            onExportGpx = {
                exportGpxLauncher.launch(GpxSerializer.suggestedGpxFileName(workout.title))
            },
            onDelete = {
                showEditDialog = false
                viewModel.deleteWorkout(workout.id, onBack)
            }
        )
    }

    if (showDeleteConfirm && workout != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Activity?") },
            text = { Text("Are you sure you want to permanently delete \"${workout.title}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteWorkout(workout.id, onBack)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonPulse,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("detail_confirm_delete_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    previewMediaUri?.let { uriStr ->
        Dialog(onDismissRequest = { previewMediaUri = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            ) {
                AsyncImage(
                    model = uriStr,
                    contentDescription = "Full size activity photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
                IconButton(
                    onClick = { previewMediaUri = null },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close photo", tint = Color.White)
                }
            }
        }
    }
}
