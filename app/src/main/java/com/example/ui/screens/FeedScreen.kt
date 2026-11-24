package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.Formatters
import com.example.core.GpxSerializer
import com.example.data.model.AthleteProfile
import com.example.data.model.BleConnectionStatus
import com.example.data.model.SportType
import com.example.data.model.UiState
import com.example.data.model.WorkoutActivity
import com.example.data.model.WorkoutSortOrder
import com.example.data.model.computeHistoryStats
import com.example.data.model.toSessionSummary
import com.example.ui.MainViewModel
import com.example.ui.components.EditActivityDialog
import com.example.ui.components.PastWorkoutHistorySummaryCard
import com.example.ui.components.PastWorkoutSessionCard
import com.example.ui.components.RecoveryGaugeRing
import com.example.ui.components.ShareActivityDialog
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.EmeraldPeak
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: MainViewModel,
    onOpenWorkoutDetail: (String) -> Unit,
    onNavigateToRecord: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onOpenManualEntry: () -> Unit,
) {
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val workoutsState by viewModel.workoutsState.collectAsStateWithLifecycle()
    val gearState by viewModel.gearState.collectAsStateWithLifecycle()
    val challengesState by viewModel.challengesState.collectAsStateWithLifecycle()
    val analytics by viewModel.analyticsState.collectAsStateWithLifecycle()
    val bleState by viewModel.bleState.collectAsStateWithLifecycle()
    val healthLogsState by viewModel.healthLogsState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf<SportType?>(null) }
    var sortOrder by remember { mutableStateOf(WorkoutSortOrder.NEWEST) }
    var workoutToShare by remember { mutableStateOf<WorkoutActivity?>(null) }
    var workoutToEdit by remember { mutableStateOf<WorkoutActivity?>(null) }
    var pendingExportWorkout by remember { mutableStateOf<WorkoutActivity?>(null) }
    val gearOptions = (gearState as? UiState.Success)?.data.orEmpty()

    val importGpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importWorkoutFromGpx(context, uri)
        }
    }

    val exportGpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        val target = pendingExportWorkout
        pendingExportWorkout = null
        if (uri != null && target != null) {
            viewModel.exportWorkoutGpxToUri(context, uri, target)
        }
    }

    val launchGpxImport = {
        importGpxLauncher.launch(
            arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream", "*/*")
        )
    }

    val profile = (profileState as? UiState.Success)?.data ?: AthleteProfile()
    val latestHealth = (healthLogsState as? UiState.Success)?.data?.firstOrNull()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshAllData() },
        modifier = Modifier
            .fillMaxSize()
            .testTag("feed_pull_refresh")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        // 1. Weekly Athlete Snapshot & Recovery Readiness Hero
        item {
            val workouts = (workoutsState as? UiState.Success)?.data ?: emptyList()
            val sevenDaysAgo = System.currentTimeMillis() - 7L * 86_400_000L
            val weekWorkouts = workouts.filter {
                (it.createdAt?.toDate()?.time ?: System.currentTimeMillis()) >= sevenDaysAgo
            }
            val weekKm = weekWorkouts.sumOf { it.distanceMeters } / 1000.0
            val weekMoving = weekWorkouts.sumOf { it.movingTimeSeconds }
            val goalKm = profile.weeklyDistanceGoalKm.coerceAtLeast(1.0)
            val goalPct = (weekKm / goalKm).toFloat().coerceIn(0f, 1f)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WEEKLY SNAPSHOT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${String.format("%.1f", weekKm)} / ${goalKm.toInt()} km",
                                style = MaterialTheme.typography.headlineMedium,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${weekWorkouts.size} activities • ${Formatters.formatDurationCompact(weekMoving)} • Effort ${analytics.weeklyRelativeEffort}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RecoveryGaugeRing(
                            score = analytics.recoveryScore,
                            label = "Ready",
                            accentColor = if (analytics.recoveryScore >= 70) EmeraldPeak else VeloOrange,
                            sizeDp = 78.dp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { goalPct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = VeloOrange,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("VO2 MAX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${analytics.vo2Max}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = SignalCyan
                                )
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("STEPS TODAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val steps = (latestHealth?.steps ?: 0) + bleState.sessionBleSteps
                                Text(
                                    text = "$steps",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Surface(
                            color = if (bleState.status == BleConnectionStatus.CONNECTED) {
                                EmeraldPeak.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .clickable { onNavigateToDevices() }
                                .testTag("feed_band_status_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Watch,
                                    contentDescription = "Wearable status",
                                    tint = if (bleState.status == BleConnectionStatus.CONNECTED) EmeraldPeak else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = if (bleState.status == BleConnectionStatus.CONNECTED) "WEARABLE LIVE" else "WEARABLE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (bleState.status == BleConnectionStatus.CONNECTED) EmeraldPeak else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (bleState.liveHeartRateBpm > 0) "${bleState.liveHeartRateBpm} bpm" else "Pair / Sync",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Sport Filter Chips (All + Primary 6 + others)
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("All Sports") },
                        modifier = Modifier.testTag("filter_sport_all")
                    )
                }
                items(SportType.entries) { sport ->
                    FilterChip(
                        selected = selectedFilter == sport,
                        onClick = { selectedFilter = if (selectedFilter == sport) null else sport },
                        label = { Text(sport.label) },
                        modifier = Modifier.testTag("filter_sport_${sport.name.lowercase()}")
                    )
                }
            }
        }

        // 3. Active Challenges Strip (if any)
        val challenges = (challengesState as? UiState.Success)?.data.orEmpty()
        if (challenges.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(challenges, key = { it.id }) { ch ->
                        val pct = (ch.currentValue / ch.targetValue.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.width(230.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(ch.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                Text(
                                    "${String.format("%.1f", ch.currentValue)} / ${ch.targetValue.toInt()} ${ch.unit} • ${ch.periodLabel}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { pct },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = EmeraldPeak
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Activity Feed List
        when (val st = workoutsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CrimsonPulse.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = st.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                val filtered = if (selectedFilter == null) {
                    st.data
                } else {
                    st.data.filter { it.parsedSport == selectedFilter }
                }
                val sorted = when (sortOrder) {
                    WorkoutSortOrder.NEWEST -> filtered
                    WorkoutSortOrder.LONGEST_DISTANCE -> filtered.sortedByDescending { it.distanceMeters }
                    WorkoutSortOrder.LONGEST_DURATION -> filtered.sortedByDescending { it.movingTimeSeconds }
                    WorkoutSortOrder.MOST_ELEVATION -> filtered.sortedByDescending { it.elevationGainMeters }
                }

                if (filtered.isNotEmpty()) {
                    item {
                        PastWorkoutHistorySummaryCard(
                            stats = filtered.computeHistoryStats(),
                            sortOrder = sortOrder,
                            onSortOrderChange = { sortOrder = it },
                            onImportGpx = launchGpxImport
                        )
                    }
                }

                if (sorted.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = VeloOrange,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (selectedFilter == null) "No Activities Logged Yet" else "No ${selectedFilter!!.label} Sessions",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Start live GPS tracking with your Wearable Device, log an indoor workout manually, or load a sample training block to preview all analytics.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = onNavigateToRecord,
                                        modifier = Modifier.testTag("empty_record_btn")
                                    ) {
                                        Text("Record")
                                    }
                                    OutlinedButton(
                                        onClick = onOpenManualEntry,
                                        modifier = Modifier.testTag("empty_manual_log_btn")
                                    ) {
                                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Log")
                                    }
                                    OutlinedButton(
                                        onClick = launchGpxImport,
                                        modifier = Modifier.testTag("empty_import_gpx_btn")
                                    ) {
                                        Icon(Icons.Rounded.UploadFile, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(".GPX")
                                    }
                                }
                                if (st.data.isEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.loadSampleTrainingBlock() },
                                        modifier = Modifier.testTag("load_sample_block_btn")
                                    ) {
                                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Load Sample Training Block (Demo)")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(sorted, key = { it.id }) { workout ->
                        val summary = remember(workout) { workout.toSessionSummary() }
                        PastWorkoutSessionCard(
                            session = summary,
                            onClick = { onOpenWorkoutDetail(workout.id) },
                            onToggleKudos = { viewModel.toggleKudos(workout) },
                            onShare = { workoutToShare = workout },
                            onEdit = { workoutToEdit = workout }
                        )
                    }
                }
            }
        }
        }
    }

    workoutToShare?.let { w ->
        ShareActivityDialog(
            workout = w,
            onExportGpx = {
                pendingExportWorkout = w
                exportGpxLauncher.launch(GpxSerializer.suggestedGpxFileName(w.title))
            },
            onDismiss = { workoutToShare = null }
        )
    }

    workoutToEdit?.let { w ->
        EditActivityDialog(
            workout = w,
            gearOptions = gearOptions,
            onDismiss = { workoutToEdit = null },
            onSave = { title, sportType, gear, rpe, isPrivate, notes, mediaUris ->
                workoutToEdit = null
                viewModel.updateWorkout(
                    existing = w,
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
                pendingExportWorkout = w
                exportGpxLauncher.launch(GpxSerializer.suggestedGpxFileName(w.title))
            },
            onDelete = {
                workoutToEdit = null
                viewModel.deleteWorkout(w.id) {}
            }
        )
    }
}
