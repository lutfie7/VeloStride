package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.ActivityShareHelper
import com.example.core.Formatters
import com.example.core.GpxSerializer
import com.example.core.ShareCardConfig
import com.example.core.ShareCardRatio
import com.example.core.ShareCardStyle
import com.example.data.model.WorkoutActivity
import com.example.data.model.WorkoutHistoryStats
import com.example.data.model.WorkoutSessionSummary
import com.example.data.model.WorkoutSortOrder
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.EmeraldPeak
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange

@Composable
fun PastWorkoutHistorySummaryCard(
    stats: WorkoutHistoryStats,
    sortOrder: WorkoutSortOrder,
    onSortOrderChange: (WorkoutSortOrder) -> Unit,
    onImportGpx: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = VeloOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Past Workout Sessions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onImportGpx != null) {
                        OutlinedButton(
                            onClick = onImportGpx,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("import_gpx_workout_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.UploadFile,
                                contentDescription = "Import GPX file",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(".GPX", maxLines = 1)
                        }
                    }
                    Surface(
                        color = VeloOrange.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${stats.sessionCount} logged",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = VeloOrange,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell(
                    label = "Total Dist",
                    value = Formatters.formatDistanceKm(stats.totalDistanceMeters),
                    valueColor = VeloOrange
                )
                MetricCell(
                    label = "Total Time",
                    value = Formatters.formatDurationCompact(stats.totalDurationSeconds)
                )
                MetricCell(
                    label = "Total Elev",
                    value = "+${stats.totalElevationGainMeters.toInt()} m",
                    valueColor = SignalCyan
                )
            }

            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WorkoutSortOrder.entries) { order ->
                    FilterChip(
                        selected = sortOrder == order,
                        onClick = { onSortOrderChange(order) },
                        label = { Text("Sort: ${order.label}") },
                        modifier = Modifier.testTag("workout_sort_${order.name.lowercase()}")
                    )
                }
            }
        }
    }
}

@Composable
fun PastWorkoutSessionCard(
    session: WorkoutSessionSummary,
    onClick: () -> Unit,
    onToggleKudos: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("workout_card_${session.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SportBadge(session.sportType)
                Text(
                    text = Formatters.formatDateTime(session.sourceActivity.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!session.deviceName.isNullOrBlank() || !session.gearName.isNullOrBlank()) {
                val deviceText = session.deviceName?.let { "via $it" }.orEmpty()
                val gearText = session.gearName?.let {
                    if (deviceText.isNotBlank()) " • $it" else it
                }.orEmpty()
                Text(
                    text = "$deviceText$gearText",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell(
                    label = "Distance",
                    value = Formatters.formatDistanceKm(session.distanceMeters),
                    valueColor = VeloOrange
                )
                MetricCell(
                    label = "Duration",
                    value = Formatters.formatDuration(session.durationSeconds)
                )
                MetricCell(
                    label = "Elev Gain",
                    value = "+${session.elevationGainMeters.toInt()} m",
                    valueColor = SignalCyan
                )
                MetricCell(
                    label = if (session.sportType.isPaceBased) "Avg Pace" else "Avg Speed",
                    value = if (session.sportType.isPaceBased) {
                        Formatters.formatPace(session.avgPaceSecPerKm)
                    } else {
                        Formatters.formatSpeed(session.avgSpeedKmh)
                    }
                )
            }

            Spacer(Modifier.height(12.dp))
            RoutePolylineCanvas(
                points = session.routePoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Effort ${session.relativeEffort}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = SignalCyan.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terrain,
                                contentDescription = null,
                                tint = SignalCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = session.elevationSource,
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalCyan
                            )
                        }
                    }
                    if (session.avgHeartRate > 0) {
                        Text(
                            text = "${session.avgHeartRate} bpm",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = CrimsonPulse
                        )
                    }
                    if (session.prCount > 0) {
                        Surface(
                            color = EmeraldPeak.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${session.prCount} PR",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldPeak,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (session.hasAiInsight) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "AI insight saved",
                            tint = SignalCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.testTag("share_activity_btn_${session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share Activity",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onToggleKudos,
                        modifier = Modifier.testTag("kudos_btn_${session.id}")
                    ) {
                        Icon(
                            imageVector = if (session.kudosGiven) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Give Kudos",
                            tint = if (session.kudosGiven) VeloOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${session.kudosCount}",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = JetBrainsMonoFontFamily
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ShareActivityDialog(
    workout: WorkoutActivity,
    onExportGpx: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val hasGpsPoints = workout.decodedPoints.size >= 2
    var activeTab by remember { mutableIntStateOf(0) }
    var config by remember(workout.id) {
        mutableStateOf(
            ShareCardConfig(
                showMap = hasGpsPoints,
                showHeartRate = workout.avgHeartRate > 0,
                showCalories = workout.calories > 0 && workout.avgHeartRate == 0
            )
        )
    }
    var mapBackdrop by remember(workout.id) { mutableStateOf<Bitmap?>(null) }
    var loadedBackdropKey by remember(workout.id) { mutableStateOf<String?>(null) }

    val backdropBoxSize = remember(config, activeTab) {
        val effective = if (activeTab == 1) config.copy(showMap = true) else config
        ActivityShareHelper.computeMapBoxSize(effective)
    }

    LaunchedEffect(workout.id, config.style, config.ratio, config.showMap, activeTab, backdropBoxSize) {
        val needMap = config.showMap || activeTab == 1
        if (needMap && !config.style.isTransparent && hasGpsPoints) {
            val needDark = config.style.isDarkMap
            val (boxW, boxH) = backdropBoxSize
            val key = "${needDark}_${boxW}x${boxH}"
            if (loadedBackdropKey != key || mapBackdrop == null) {
                val bmp = ActivityShareHelper.fetchShareCardMapBackdrop(
                    workout = workout,
                    darkMap = needDark,
                    boxWidth = boxW,
                    boxHeight = boxH
                )
                if (bmp != null) {
                    mapBackdrop = bmp
                    loadedBackdropKey = key
                }
            }
        }
    }

    val previewBitmap = remember(
        workout.id,
        workout.elevationGainMeters,
        workout.distanceMeters,
        config,
        mapBackdrop
    ) {
        ActivityShareHelper.renderShareCardBitmap(
            workout = workout,
            config = config,
            mapBackdrop = if (config.style.isTransparent) null else mapBackdrop
        ).asImageBitmap()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .widthIn(max = 560.dp)
                .heightIn(max = 760.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Share & Export Activity",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_share_dialog_btn")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close Share dialog")
                    }
                }

                if (hasGpsPoints) {
                    PrimaryTabRow(selectedTabIndex = activeTab) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = { Text("Share Card") },
                            icon = { Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("share_tab_card")
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("Flyover Map") },
                            icon = { Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("share_tab_flyover")
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (activeTab == 1 && hasGpsPoints) {
                        FlyoverMapAnimator(
                            workout = workout,
                            selectedRatio = config.ratio,
                            onRatioChange = { nextRatio -> config = config.copy(ratio = nextRatio) },
                            onShareSnapshot = { progress, snapRatio ->
                                ActivityShareHelper.shareAsImageCard(
                                    context = context,
                                    workout = workout,
                                    config = config.copy(showMap = true, ratio = snapRatio),
                                    mapBackdrop = if (config.style.isTransparent) null else mapBackdrop,
                                    progressFraction = progress
                                )
                                onDismiss()
                            }
                        )
                    } else {
                        // Card Preview sized to 1:1, 16:9, or 9:16 (with checkerboard when Transparent)
                        val previewStageModifier = if (config.ratio == ShareCardRatio.LANDSCAPE_16_9) {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(config.ratio.aspectFloat, matchHeightConstraintsFirst = false)
                        } else {
                            Modifier
                                .fillMaxHeight()
                                .aspectRatio(config.ratio.aspectFloat, matchHeightConstraintsFirst = true)
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(256.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                        ) {
                            Box(
                                modifier = previewStageModifier
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                if (config.style.isTransparent) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val cell = 20f
                                        val cols = (size.width / cell).toInt() + 1
                                        val rows = (size.height / cell).toInt() + 1
                                        for (r in 0..rows) {
                                            for (c in 0..cols) {
                                                val dark = (r + c) % 2 == 0
                                                drawRect(
                                                    color = if (dark) Color(0xFF1E293B) else Color(0xFF334155),
                                                    topLeft = Offset(c * cell, r * cell),
                                                    size = Size(cell, cell)
                                                )
                                            }
                                        }
                                    }
                                }
                                Image(
                                    bitmap = previewBitmap,
                                    contentDescription = "Shareable activity card preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Ratio Picker (1:1, 16:9, 9:16)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.AspectRatio,
                                contentDescription = null,
                                tint = SignalCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Rasio", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(2.dp))
                            ShareCardRatio.entries.forEach { ratio ->
                                FilterChip(
                                    selected = config.ratio == ratio,
                                    onClick = { config = config.copy(ratio = ratio) },
                                    label = { Text(ratio.label, maxLines = 1) },
                                    modifier = Modifier.testTag("share_ratio_${ratio.fileTag}")
                                )
                            }
                        }

                        // Style Picker
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = VeloOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Style Kartu", style = MaterialTheme.typography.labelLarge)
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ShareCardStyle.entries) { style ->
                                FilterChip(
                                    selected = config.style == style,
                                    onClick = { config = config.copy(style = style) },
                                    label = { Text(style.label, maxLines = 1) },
                                    modifier = Modifier.testTag("share_style_${style.name.lowercase()}")
                                )
                            }
                        }

                        // Data Field Toggles
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = SignalCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Data Ditampilkan", style = MaterialTheme.typography.labelLarge)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = config.showMap,
                                onClick = { config = config.copy(showMap = !config.showMap) },
                                label = { Text("Peta Rute") },
                                modifier = Modifier.testTag("share_toggle_map")
                            )
                            FilterChip(
                                selected = config.showDistance,
                                onClick = { config = config.copy(showDistance = !config.showDistance) },
                                label = { Text("Jarak") },
                                modifier = Modifier.testTag("share_toggle_distance")
                            )
                            FilterChip(
                                selected = config.showDuration,
                                onClick = { config = config.copy(showDuration = !config.showDuration) },
                                label = { Text("Durasi") },
                                modifier = Modifier.testTag("share_toggle_duration")
                            )
                            FilterChip(
                                selected = config.showElevation,
                                onClick = { config = config.copy(showElevation = !config.showElevation) },
                                label = { Text("Elevasi") },
                                modifier = Modifier.testTag("share_toggle_elevation")
                            )
                            FilterChip(
                                selected = config.showPaceOrSpeed,
                                onClick = { config = config.copy(showPaceOrSpeed = !config.showPaceOrSpeed) },
                                label = { Text(if (workout.parsedSport.isPaceBased) "Pace" else "Speed") },
                                modifier = Modifier.testTag("share_toggle_pace")
                            )
                            FilterChip(
                                selected = config.showHeartRate,
                                onClick = { config = config.copy(showHeartRate = !config.showHeartRate) },
                                label = { Text("Heart Rate") },
                                modifier = Modifier.testTag("share_toggle_hr")
                            )
                            FilterChip(
                                selected = config.showCalories,
                                onClick = { config = config.copy(showCalories = !config.showCalories) },
                                label = { Text("Kalori") },
                                modifier = Modifier.testTag("share_toggle_calories")
                            )
                            FilterChip(
                                selected = config.showEffort,
                                onClick = { config = config.copy(showEffort = !config.showEffort) },
                                label = { Text("Effort") },
                                modifier = Modifier.testTag("share_toggle_effort")
                            )
                            FilterChip(
                                selected = config.showDateAndDevice,
                                onClick = { config = config.copy(showDateAndDevice = !config.showDateAndDevice) },
                                label = { Text("Tanggal & Info") },
                                modifier = Modifier.testTag("share_toggle_meta")
                            )
                        }

                        Button(
                            onClick = {
                                ActivityShareHelper.shareAsImageCard(
                                    context = context,
                                    workout = workout,
                                    config = config,
                                    mapBackdrop = if (config.style.isTransparent) null else mapBackdrop
                                )
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("share_image_card_btn")
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Share Image Card (${config.style.label} • ${config.ratio.label})")
                        }

                        OutlinedButton(
                            onClick = {
                                ActivityShareHelper.shareAsText(context, workout, config)
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("share_text_summary_btn")
                        ) {
                            Icon(Icons.Rounded.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Share Text Summary")
                        }

                        if (hasGpsPoints) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (onExportGpx != null) {
                                    OutlinedButton(
                                        onClick = {
                                            onDismiss()
                                            onExportGpx()
                                        },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("export_gpx_file_btn")
                                    ) {
                                        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Save .GPX", maxLines = 1)
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        val xml = GpxSerializer.buildWorkoutGpxXml(workout)
                                        GpxSerializer.shareGpxFile(context, workout.title, xml)
                                        onDismiss()
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("share_gpx_file_btn")
                                ) {
                                    Icon(Icons.Rounded.Route, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share .GPX", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
