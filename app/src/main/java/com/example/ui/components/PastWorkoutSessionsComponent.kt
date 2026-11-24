package com.example.ui.components

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import coil.compose.AsyncImage
import com.example.core.ActivityShareHelper
import com.example.core.Formatters
import com.example.core.GpxSerializer
import com.example.core.LocalUnitSystem
import com.example.core.ShareCardConfig
import com.example.core.ShareCardRatio
import com.example.core.ShareCardStyle
import com.example.core.rememberAppStrings
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
    val unitSystem = LocalUnitSystem.current
    val strings = rememberAppStrings()
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
                        text = strings.pastWorkoutsTitle,
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
                    label = strings.totalDistLabel,
                    value = Formatters.formatDistanceKm(stats.totalDistanceMeters, unitSystem),
                    valueColor = VeloOrange
                )
                MetricCell(
                    label = strings.totalTimeLabel,
                    value = Formatters.formatDurationCompact(stats.totalDurationSeconds)
                )
                MetricCell(
                    label = strings.totalElevLabel,
                    value = "+${Formatters.formatElevation(stats.totalElevationGainMeters, unitSystem)}",
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
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val unitSystem = LocalUnitSystem.current
    val strings = rememberAppStrings()
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
                    label = strings.distanceLabel,
                    value = Formatters.formatDistanceKm(session.distanceMeters, unitSystem),
                    valueColor = VeloOrange
                )
                MetricCell(
                    label = strings.durationLabel,
                    value = Formatters.formatDuration(session.durationSeconds)
                )
                MetricCell(
                    label = strings.elevGainLabel,
                    value = "+${Formatters.formatElevation(session.elevationGainMeters, unitSystem)}",
                    valueColor = SignalCyan
                )
                MetricCell(
                    label = if (session.sportType.isPaceBased) strings.avgPaceLabel else strings.avgSpeedLabel,
                    value = if (session.sportType.isPaceBased) {
                        Formatters.formatPace(session.avgPaceSecPerKm, unitSystem)
                    } else {
                        Formatters.formatSpeed(session.avgSpeedKmh, unitSystem)
                    }
                )
            }

            Spacer(Modifier.height(12.dp))
            RoutePolylineCanvas(
                points = session.routePoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(236.dp)
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
                    if (session.mediaCount > 0) {
                        Surface(
                            color = SignalCyan.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoLibrary,
                                    contentDescription = "${session.mediaCount} photos",
                                    tint = SignalCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "${session.mediaCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SignalCyan
                                )
                            }
                        }
                    }
                    if (session.privateActivity) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Private activity",
                            tint = VeloOrange,
                            modifier = Modifier.size(16.dp)
                        )
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
                    if (onEdit != null) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.testTag("edit_activity_btn_${session.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit Activity",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
    val unitSystem = LocalUnitSystem.current
    val strings = rememberAppStrings()
    val hasGpsPoints = workout.decodedPoints.size >= 2
    var activeTab by remember { mutableIntStateOf(0) }
    var config by remember(workout.id) {
        mutableStateOf(
            ShareCardConfig(
                ratio = ShareCardRatio.PORTRAIT_9_16,
                showMap = hasGpsPoints,
                showHeartRate = workout.avgHeartRate > 0,
                showCalories = workout.calories > 0 && workout.avgHeartRate == 0
            )
        )
    }
    var mapBackdrop by remember(workout.id) { mutableStateOf<Bitmap?>(null) }
    var loadedBackdropKey by remember(workout.id) { mutableStateOf<String?>(null) }
    var backgroundBitmap by remember(workout.id) { mutableStateOf<Bitmap?>(null) }

    val bgMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            config = config.copy(backgroundMediaUri = uri.toString())
        }
    }

    LaunchedEffect(config.backgroundMediaUri, config.ratio) {
        val uri = config.backgroundMediaUri
        backgroundBitmap = if (uri.isNullOrBlank()) {
            null
        } else {
            ActivityShareHelper.loadShareBackgroundBitmap(
                context = context,
                uriString = uri,
                targetWidth = config.ratio.widthPx,
                targetHeight = config.ratio.heightPx
            )
        }
    }

    val backdropBoxSize = remember(config, activeTab) {
        val effective = if (activeTab == 1) config.copy(showMap = true) else config
        ActivityShareHelper.computeMapBoxSize(effective)
    }

    LaunchedEffect(workout.id, config.style, config.ratio, config.showMap, config.backgroundMediaUri, activeTab, backdropBoxSize) {
        val needMap = config.showMap || activeTab == 1
        val skipTileBackdrop = config.style.isTransparent || (!config.backgroundMediaUri.isNullOrBlank() && activeTab == 0)
        if (needMap && !skipTileBackdrop && hasGpsPoints) {
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
        mapBackdrop,
        backgroundBitmap,
        unitSystem
    ) {
        ActivityShareHelper.renderShareCardBitmap(
            workout = workout,
            config = config,
            mapBackdrop = if (config.style.isTransparent) null else mapBackdrop,
            backgroundBitmap = backgroundBitmap
        ).asImageBitmap()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(onBack = onDismiss)
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = strings.shareTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = workout.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("close_share_dialog_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.backLabel)
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (hasGpsPoints) {
                        PrimaryTabRow(selectedTabIndex = activeTab) {
                            Tab(
                                selected = activeTab == 0,
                                onClick = { activeTab = 0 },
                                text = { Text(strings.shareTabCard) },
                                icon = { Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("share_tab_card")
                            )
                            Tab(
                                selected = activeTab == 1,
                                onClick = { activeTab = 1 },
                                text = { Text(strings.shareTabFlyover) },
                                icon = { Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("share_tab_flyover")
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .weight(1f)
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
                                        backgroundBitmap = backgroundBitmap,
                                        progressFraction = progress
                                    )
                                    onDismiss()
                                }
                            )
                        } else {
                            // Strava-style large Card Preview sized to 9:16, 1:1, or 16:9
                            val previewStageHeight = when (config.ratio) {
                                ShareCardRatio.LANDSCAPE_16_9 -> 240.dp
                                ShareCardRatio.SQUARE_1_1 -> 380.dp
                                ShareCardRatio.PORTRAIT_9_16 -> 460.dp
                            }
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
                                    .height(previewStageHeight)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                            ) {
                                Box(
                                    modifier = previewStageModifier
                                        .clip(RoundedCornerShape(14.dp))
                                ) {
                                    if (config.style.isTransparent && backgroundBitmap == null) {
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

                            // Ratio Picker (9:16 default, 1:1, 16:9)
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
                                Text(strings.shareRatioLabel, style = MaterialTheme.typography.labelLarge)
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
                                Text(strings.shareStyleLabel, style = MaterialTheme.typography.labelLarge)
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

                            // Background Media Picker (Zero-permission Photo Picker + Activity Photos)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = SignalCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.shareBgMediaLabel, style = MaterialTheme.typography.labelLarge)
                                    }
                                    if (!config.backgroundMediaUri.isNullOrBlank()) {
                                        TextButton(
                                            onClick = { config = config.copy(backgroundMediaUri = null) },
                                            modifier = Modifier.testTag("share_clear_media_btn")
                                        ) {
                                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.shareClearMedia)
                                        }
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        bgMediaPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("share_pick_media_btn")
                                ) {
                                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (config.backgroundMediaUri.isNullOrBlank()) {
                                            strings.sharePickMedia
                                        } else {
                                            strings.shareChangeMedia
                                        }
                                    )
                                }
                                if (workout.mediaUris.isNotEmpty()) {
                                    Text(
                                        text = strings.shareMediaFromActivity,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(workout.mediaUris, key = { it }) { uriStr ->
                                            val isSelected = config.backgroundMediaUri == uriStr
                                            AsyncImage(
                                                model = uriStr,
                                                contentDescription = "Use activity photo as background",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .border(
                                                        width = if (isSelected) 2.dp else 1.dp,
                                                        color = if (isSelected) VeloOrange else MaterialTheme.colorScheme.outlineVariant,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .clickable {
                                                        config = config.copy(
                                                            backgroundMediaUri = if (isSelected) null else uriStr
                                                        )
                                                    }
                                            )
                                        }
                                    }
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
                                Text(strings.shareDataFieldsLabel, style = MaterialTheme.typography.labelLarge)
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilterChip(
                                    selected = config.showMap,
                                    onClick = { config = config.copy(showMap = !config.showMap) },
                                    label = { Text(strings.shareMapToggle) },
                                    modifier = Modifier.testTag("share_toggle_map")
                                )
                                FilterChip(
                                    selected = config.showDistance,
                                    onClick = { config = config.copy(showDistance = !config.showDistance) },
                                    label = { Text(strings.shareDistanceToggle) },
                                    modifier = Modifier.testTag("share_toggle_distance")
                                )
                                FilterChip(
                                    selected = config.showDuration,
                                    onClick = { config = config.copy(showDuration = !config.showDuration) },
                                    label = { Text(strings.shareDurationToggle) },
                                    modifier = Modifier.testTag("share_toggle_duration")
                                )
                                FilterChip(
                                    selected = config.showElevation,
                                    onClick = { config = config.copy(showElevation = !config.showElevation) },
                                    label = { Text(strings.shareElevationToggle) },
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
                                    label = { Text(strings.shareHeartRateToggle) },
                                    modifier = Modifier.testTag("share_toggle_hr")
                                )
                                FilterChip(
                                    selected = config.showCalories,
                                    onClick = { config = config.copy(showCalories = !config.showCalories) },
                                    label = { Text(strings.shareCaloriesToggle) },
                                    modifier = Modifier.testTag("share_toggle_calories")
                                )
                                FilterChip(
                                    selected = config.showEffort,
                                    onClick = { config = config.copy(showEffort = !config.showEffort) },
                                    label = { Text(strings.shareEffortToggle) },
                                    modifier = Modifier.testTag("share_toggle_effort")
                                )
                                FilterChip(
                                    selected = config.showDateAndDevice,
                                    onClick = { config = config.copy(showDateAndDevice = !config.showDateAndDevice) },
                                    label = { Text(strings.shareMetaToggle) },
                                    modifier = Modifier.testTag("share_toggle_meta")
                                )
                            }

                            Button(
                                onClick = {
                                    ActivityShareHelper.shareAsImageCard(
                                        context = context,
                                        workout = workout,
                                        config = config,
                                        mapBackdrop = if (config.style.isTransparent) null else mapBackdrop,
                                        backgroundBitmap = backgroundBitmap
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
                                Text("${strings.shareImageCardBtn} (${config.style.label} • ${config.ratio.label})")
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
                                Text(strings.shareTextSummaryBtn)
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
                                            Text(strings.saveGpxBtn, maxLines = 1)
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
                                        Text(strings.shareGpxBtn, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
