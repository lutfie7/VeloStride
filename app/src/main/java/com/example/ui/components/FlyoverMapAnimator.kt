package com.example.ui.components

import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.FlyoverCameraMode
import com.example.core.FlyoverVideoExporter
import com.example.core.Formatters
import com.example.core.MapProjection
import com.example.core.ShareCardRatio
import com.example.data.model.WorkoutActivity
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.EmeraldPeak
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun FlyoverMapAnimator(
    workout: WorkoutActivity,
    modifier: Modifier = Modifier,
    selectedRatio: ShareCardRatio? = null,
    onRatioChange: ((ShareCardRatio) -> Unit)? = null,
    onShareSnapshot: ((Float, ShareCardRatio) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val points = remember(workout.id, workout.routePoints) { workout.decodedPoints }
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val density = LocalDensity.current

    var internalRatio by remember { mutableStateOf(ShareCardRatio.SQUARE_1_1) }
    val activeRatio = selectedRatio ?: internalRatio
    fun updateRatio(next: ShareCardRatio) {
        internalRatio = next
        onRatioChange?.invoke(next)
    }

    if (points.size < 2) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(16.dp),
            modifier = modifier.fillMaxWidth()
        ) {
            Text(
                text = "Flyover Map Animator membutuhkan minimal 2 titik GPS pada aktivitas ini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
        }
        return
    }

    val cumulativeDistances = remember(points) {
        FlyoverVideoExporter.computeCumulativeDistances(points)
    }
    val totalTrackMeters = cumulativeDistances.lastOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val displayTotalMeters = workout.distanceMeters.takeIf { it > 10.0 } ?: totalTrackMeters

    val followZoom = remember(displayTotalMeters) {
        when {
            displayTotalMeters > 35_000.0 -> 13
            displayTotalMeters > 14_000.0 -> 14
            displayTotalMeters > 4_000.0 -> 15
            else -> 16
        }
    }

    var progress by remember(workout.id) { mutableFloatStateOf(0f) }
    var isPlaying by remember(workout.id) { mutableStateOf(true) }
    var speedMultiplier by remember { mutableFloatStateOf(2f) }
    var cameraMode by remember { mutableStateOf(FlyoverCameraMode.FOLLOW_3D) }

    var isExportingVideo by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var cachedVideoKey by remember { mutableStateOf<String?>(null) }
    var cachedVideoFile by remember { mutableStateOf<File?>(null) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { destUri ->
        val src = pendingSaveFile
        pendingSaveFile = null
        if (destUri != null && src != null && src.exists()) {
            scope.launch {
                runCatching {
                    FlyoverVideoExporter.copyVideoToUri(context, src, destUri)
                }.onSuccess {
                    exportStatus = "Video berhasil disimpan!"
                }.onFailure { err ->
                    exportStatus = err.localizedMessage ?: "Gagal menyimpan video."
                }
            }
        }
    }

    suspend fun ensureExportedMp4(): File {
        val key = "${workout.id}_${activeRatio.name}_${cameraMode.name}_${speedMultiplier}_$darkTheme"
        val existing = cachedVideoFile
        if (cachedVideoKey == key && existing != null && existing.exists() && existing.length() > 0L) {
            return existing
        }
        val rendered = FlyoverVideoExporter.exportFlyoverVideoToCache(
            context = context,
            workout = workout,
            ratio = activeRatio,
            cameraMode = cameraMode,
            speedMultiplier = speedMultiplier,
            darkTheme = darkTheme,
            onProgress = { p, msg ->
                exportProgress = p
                exportStatus = msg
            }
        )
        cachedVideoKey = key
        cachedVideoFile = rendered
        return rendered
    }

    LaunchedEffect(isPlaying, speedMultiplier, points) {
        if (!isPlaying) return@LaunchedEffect
        if (progress >= 1f) progress = 0f
        var lastNanos = 0L
        val baseDurationSec = 12f
        while (isPlaying) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val deltaSec = (frameNanos - lastNanos) / 1_000_000_000f
                    val next = (progress + (deltaSec * speedMultiplier) / baseDurationSec).coerceAtMost(1f)
                    progress = next
                    if (next >= 1f) {
                        isPlaying = false
                    }
                }
                lastNanos = frameNanos
            }
        }
    }

    val currentSample = remember(points, cumulativeDistances, progress) {
        FlyoverVideoExporter.interpolateSample(points, cumulativeDistances, progress)
    }

    val coveredKm = (displayTotalMeters * progress) / 1000.0
    val elapsedSec = (workout.movingTimeSeconds * progress).roundToInt().toLong()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Camera Mode & Speed Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FlyoverCameraMode.entries.forEach { mode ->
                    FilterChip(
                        selected = cameraMode == mode,
                        onClick = { cameraMode = mode },
                        leadingIcon = {
                            Icon(
                                imageVector = if (mode == FlyoverCameraMode.FOLLOW_3D) {
                                    Icons.Rounded.Explore
                                } else {
                                    Icons.Rounded.ZoomOutMap
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(mode.label, maxLines = 1) },
                        modifier = Modifier.testTag("flyover_mode_${mode.name.lowercase()}")
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1f, 2f, 4f, 8f).forEach { spd ->
                    val active = speedMultiplier == spd
                    Surface(
                        color = if (active) VeloOrange else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        onClick = { speedMultiplier = spd },
                        modifier = Modifier.testTag("flyover_speed_${spd.toInt()}x")
                    ) {
                        Text(
                            text = "${spd.toInt()}x",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Aspect Ratio Bar (1:1, 16:9, 9:16)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AspectRatio,
                contentDescription = null,
                tint = SignalCyan,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Rasio",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(2.dp))
            ShareCardRatio.entries.forEach { ratio ->
                FilterChip(
                    selected = activeRatio == ratio,
                    onClick = { updateRatio(ratio) },
                    label = { Text(ratio.label, maxLines = 1) },
                    modifier = Modifier.testTag("flyover_ratio_${ratio.fileTag}")
                )
            }
        }

        // Animated Map Stage sized to the chosen aspect ratio (1:1, 16:9, 9:16)
        val stageHeight = when (activeRatio) {
            ShareCardRatio.LANDSCAPE_16_9 -> 200.dp
            ShareCardRatio.SQUARE_1_1 -> 265.dp
            ShareCardRatio.PORTRAIT_9_16 -> 320.dp
        }
        val frameSizeModifier = if (activeRatio == ShareCardRatio.LANDSCAPE_16_9) {
            Modifier
                .fillMaxWidth()
                .aspectRatio(activeRatio.aspectFloat, matchHeightConstraintsFirst = false)
        } else {
            Modifier
                .fillMaxHeight()
                .aspectRatio(activeRatio.aspectFloat, matchHeightConstraintsFirst = true)
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(stageHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
        ) {
            BoxWithConstraints(
                modifier = frameSizeModifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (darkTheme) Color(0xFF0E131B) else Color(0xFFECEAE4))
            ) {
                val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(16f)
                val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(16f)
                val padPx = with(density) { 24.dp.toPx() }

                val overviewViewport = remember(points, widthPx, heightPx, padPx) {
                    MapProjection.computeViewportForPoints(
                        points = points,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        paddingPx = padPx
                    )
                }
                val activeViewport = if (cameraMode == FlyoverCameraMode.FOLLOW_3D) {
                    MapProjection.computeViewportForCenter(
                        centerLat = currentSample.lat,
                        centerLng = currentSample.lng,
                        zoom = followZoom,
                        tileSizePx = 320.0
                    )
                } else {
                    overviewViewport
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (cameraMode == FlyoverCameraMode.FOLLOW_3D) {
                                rotationX = 24f
                                cameraDistance = 14f * density.density
                                scaleX = 1.22f
                                scaleY = 1.22f
                            }
                        }
                ) {
                    MapTileBackground(
                        viewport = activeViewport,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        darkTheme = darkTheme
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val projected = points.map { pt ->
                            val (px, py) = MapProjection.project(pt.lat, pt.lng, activeViewport, size.width, size.height)
                            Offset(px, py)
                        }

                        val fullPath = Path().apply {
                            moveTo(projected.first().x, projected.first().y)
                            for (i in 1 until projected.size) {
                                lineTo(projected[i].x, projected[i].y)
                            }
                        }
                        drawPath(
                            fullPath,
                            VeloOrange.copy(alpha = 0.24f),
                            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        val (headX, headY) = MapProjection.project(
                            currentSample.lat,
                            currentSample.lng,
                            activeViewport,
                            size.width,
                            size.height
                        )
                        val headOffset = Offset(headX, headY)
                        val activePath = Path().apply {
                            moveTo(projected.first().x, projected.first().y)
                            val maxSeg = currentSample.segmentIndex.coerceIn(0, projected.lastIndex)
                            for (i in 1..maxSeg) {
                                lineTo(projected[i].x, projected[i].y)
                            }
                            lineTo(headOffset.x, headOffset.y)
                        }

                        drawPath(
                            activePath,
                            Color.Black.copy(alpha = if (darkTheme) 0.50f else 0.25f),
                            style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            activePath,
                            VeloOrange.copy(alpha = 0.34f),
                            style = Stroke(width = 18f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            activePath,
                            VeloOrange,
                            style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        drawCircle(EmeraldPeak, radius = 8.5f, center = projected.first())
                        drawCircle(Color.White, radius = 3.5f, center = projected.first())
                        if (progress >= 0.99f) {
                            drawCircle(CrimsonPulse, radius = 9.5f, center = projected.last())
                            drawCircle(Color.White, radius = 4f, center = projected.last())
                        }

                        drawCircle(SignalCyan.copy(alpha = 0.28f), radius = 22f, center = headOffset)
                        drawCircle(VeloOrange, radius = 12f, center = headOffset)
                        drawCircle(Color.White, radius = 5f, center = headOffset)
                    }
                }

                // Compact Live Telemetry Pill HUD (stays legible even inside 9:16 frame)
                val compactHud = activeRatio == ShareCardRatio.PORTRAIT_9_16
                Surface(
                    color = Color(0xCC0D121A),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (compactHud) 7.dp else 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = if (compactHud) 8.dp else 11.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Text("DIST", fontSize = 8.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            Text(
                                text = String.format("%.2f", coveredKm) + if (compactHud) "" else " km",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (compactHud) 11.sp else 13.sp,
                                color = VeloOrange
                            )
                        }
                        Column {
                            Text("TIME", fontSize = 8.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            Text(
                                text = Formatters.formatDuration(elapsedSec),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (compactHud) 11.sp else 13.sp,
                                color = Color.White
                            )
                        }
                        Column {
                            Text("ELEV", fontSize = 8.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            Text(
                                text = "${currentSample.ele.roundToInt()}m",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (compactHud) 11.sp else 13.sp,
                                color = SignalCyan
                            )
                        }
                        if (currentSample.hr > 0 && !compactHud) {
                            Column {
                                Text("HR", fontSize = 8.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${currentSample.hr}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = CrimsonPulse
                                )
                            }
                        }
                    }
                }

                MapAttributionBadge(
                    darkTheme = darkTheme,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                )
            }
        }

        // Synced Elevation Sparkline
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
        ) {
            val minEle = points.minOf { it.ele }
            val maxEle = points.maxOf { it.ele }
            val span = (maxEle - minEle).coerceAtLeast(8.0)

            val elePath = Path()
            val fillPath = Path()
            points.forEachIndexed { idx, pt ->
                val x = (cumulativeDistances[idx] / totalTrackMeters).toFloat() * size.width
                val y = size.height - 6f - ((pt.ele - minEle) / span).toFloat() * (size.height - 12f)
                if (idx == 0) {
                    elePath.moveTo(x, y)
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, y)
                } else {
                    elePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(size.width, size.height)
            fillPath.close()

            drawPath(
                fillPath,
                Brush.verticalGradient(listOf(SignalCyan.copy(alpha = 0.30f), Color.Transparent))
            )
            drawPath(elePath, SignalCyan, style = Stroke(width = 3f, cap = StrokeCap.Round))

            val cursorX = progress * size.width
            drawLine(VeloOrange, Offset(cursorX, 0f), Offset(cursorX, size.height), strokeWidth = 3f)
            val cursorY = size.height - 6f - ((currentSample.ele - minEle) / span).toFloat() * (size.height - 12f)
            drawCircle(VeloOrange, radius = 6f, center = Offset(cursorX, cursorY))
            drawCircle(Color.White, radius = 2.5f, center = Offset(cursorX, cursorY))
        }

        // Scrubber + Playback Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledIconButton(
                onClick = {
                    if (progress >= 1f) progress = 0f
                    isPlaying = !isPlaying
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("flyover_play_pause_btn")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause flyover" else "Play flyover"
                )
            }

            IconButton(
                onClick = {
                    progress = 0f
                    isPlaying = true
                },
                modifier = Modifier.testTag("flyover_replay_btn")
            ) {
                Icon(Icons.Rounded.Replay, contentDescription = "Restart flyover")
            }

            Slider(
                value = progress,
                onValueChange = { next ->
                    isPlaying = false
                    progress = next
                },
                colors = SliderDefaults.colors(
                    thumbColor = VeloOrange,
                    activeTrackColor = VeloOrange
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("flyover_scrubber")
            )

            Text(
                text = "${(progress * 100f).roundToInt()}%",
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(38.dp)
            )
        }

        // Video Export Progress / Status
        if (isExportingVideo || !exportStatus.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isExportingVideo) {
                        LinearProgressIndicator(
                            progress = { exportProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    exportStatus?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Save Video MP4 & Share Video Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                enabled = !isExportingVideo,
                onClick = {
                    isPlaying = false
                    isExportingVideo = true
                    exportProgress = 0.02f
                    exportStatus = "Memulai render video ${activeRatio.label}..."
                    scope.launch {
                        runCatching {
                            val mp4 = ensureExportedMp4()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val savedUri = FlyoverVideoExporter.saveVideoToMediaStore(
                                    context = context,
                                    sourceMp4 = mp4,
                                    displayName = mp4.name
                                )
                                checkNotNull(savedUri) { "Gagal menyimpan ke Galeri." }
                                exportStatus = "Tersimpan ke Galeri: Movies/VeloStride/${mp4.name}"
                            } else {
                                pendingSaveFile = mp4
                                saveDocumentLauncher.launch(mp4.name)
                            }
                        }.onFailure { err ->
                            exportStatus = err.localizedMessage ?: "Gagal mengekspor video MP4."
                        }
                        isExportingVideo = false
                    }
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("flyover_save_video_btn")
            ) {
                Icon(Icons.Rounded.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Video (${activeRatio.label})", maxLines = 1)
            }

            OutlinedButton(
                enabled = !isExportingVideo,
                onClick = {
                    isPlaying = false
                    isExportingVideo = true
                    exportProgress = 0.02f
                    exportStatus = "Menyiapkan video ${activeRatio.label}..."
                    scope.launch {
                        runCatching {
                            val mp4 = ensureExportedMp4()
                            FlyoverVideoExporter.shareVideoFile(context, mp4, workout.title)
                            exportStatus = "Video siap dibagikan (${mp4.name})"
                        }.onFailure { err ->
                            exportStatus = err.localizedMessage ?: "Gagal membagikan video MP4."
                        }
                        isExportingVideo = false
                    }
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                modifier = Modifier
                    .weight(0.85f)
                    .height(48.dp)
                    .testTag("flyover_share_video_btn")
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share MP4", maxLines = 1)
            }
        }

        if (onShareSnapshot != null) {
            OutlinedButton(
                enabled = !isExportingVideo,
                onClick = {
                    isPlaying = false
                    onShareSnapshot(progress.coerceIn(0.05f, 1f), activeRatio)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("flyover_share_snapshot_btn")
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share Frame (${(progress * 100f).roundToInt()}% • ${activeRatio.label})")
            }
        }
    }
}

@Composable
fun FlyoverMapDialog(
    workout: WorkoutActivity,
    onShareFrame: ((Float, ShareCardRatio) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
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
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .widthIn(max = 560.dp)
                .heightIn(max = 780.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Flyover Map Animator",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = workout.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_flyover_dialog_btn")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close Flyover")
                    }
                }
                Spacer(Modifier.height(10.dp))
                FlyoverMapAnimator(
                    workout = workout,
                    onShareSnapshot = onShareFrame
                )
            }
        }
    }
}
