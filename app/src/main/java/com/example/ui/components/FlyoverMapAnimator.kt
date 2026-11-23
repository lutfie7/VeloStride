package com.example.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.Formatters
import com.example.core.MapProjection
import com.example.data.model.TrackPoint
import com.example.data.model.WorkoutActivity
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.EmeraldPeak
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange
import kotlin.math.roundToInt

enum class FlyoverCameraMode(val label: String) {
    FOLLOW_3D("3D Follow"),
    FULL_ROUTE("Full Route"),
}

private data class FlyoverSample(
    val lat: Double,
    val lng: Double,
    val ele: Double,
    val hr: Int,
    val distanceMeters: Double,
    val segmentIndex: Int,
)

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun FlyoverMapAnimator(
    workout: WorkoutActivity,
    modifier: Modifier = Modifier,
    onShareSnapshot: ((Float) -> Unit)? = null,
) {
    val points = remember(workout.id, workout.routePoints) { workout.decodedPoints }
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val density = LocalDensity.current

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

    // Precompute cumulative distance per trackpoint for constant-speed interpolation
    val cumulativeDistances = remember(points) {
        val dists = DoubleArray(points.size)
        for (i in 1 until points.size) {
            dists[i] = dists[i - 1] + Formatters.haversineMeters(
                points[i - 1].lat,
                points[i - 1].lng,
                points[i].lat,
                points[i].lng
            )
        }
        dists
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

    LaunchedEffect(isPlaying, speedMultiplier, points) {
        if (!isPlaying) return@LaunchedEffect
        if (progress >= 1f) progress = 0f
        var lastNanos = 0L
        val baseDurationSec = 12f // 12s full route playback at 1x speed
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
        interpolateSample(points, cumulativeDistances, progress)
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

        // Animated Map Viewport
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (darkTheme) Color(0xFF0E131B) else Color(0xFFECEAE4))
        ) {
            val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(16f)
            val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(16f)
            val padPx = with(density) { 28.dp.toPx() }

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

                    // Faint upcoming full route
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

                    // Active revealed trail up to interpolated head
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

                    // Start & Finish markers
                    drawCircle(EmeraldPeak, radius = 8.5f, center = projected.first())
                    drawCircle(Color.White, radius = 3.5f, center = projected.first())
                    if (progress >= 0.99f) {
                        drawCircle(CrimsonPulse, radius = 9.5f, center = projected.last())
                        drawCircle(Color.White, radius = 4f, center = projected.last())
                    }

                    // Pulsing active runner/cyclist beacon
                    drawCircle(SignalCyan.copy(alpha = 0.28f), radius = 22f, center = headOffset)
                    drawCircle(VeloOrange, radius = 12f, center = headOffset)
                    drawCircle(Color.White, radius = 5f, center = headOffset)
                }
            }

            // Top-left Live Telemetry Pill HUD
            Surface(
                color = Color(0xCC0D121A),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Column {
                        Text("DIST", fontSize = 9.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                        Text(
                            text = String.format("%.2f km", coveredKm),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            color = VeloOrange
                        )
                    }
                    Column {
                        Text("TIME", fontSize = 9.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                        Text(
                            text = Formatters.formatDuration(elapsedSec),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                    Column {
                        Text("ELEV", fontSize = 9.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                        Text(
                            text = "${currentSample.ele.roundToInt()} m",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            color = SignalCyan
                        )
                    }
                    if (currentSample.hr > 0) {
                        Column {
                            Text("HR", fontSize = 9.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            Text(
                                text = "${currentSample.hr}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
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
                    .padding(8.dp)
            )
        }

        // Synced Elevation Sparkline
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
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

        if (onShareSnapshot != null) {
            OutlinedButton(
                onClick = {
                    isPlaying = false
                    onShareSnapshot(progress.coerceIn(0.05f, 1f))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("flyover_share_snapshot_btn")
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share Frame Saat Ini (${(progress * 100f).roundToInt()}%)")
            }
        }
    }
}

@Composable
fun FlyoverMapDialog(
    workout: WorkoutActivity,
    onShareFrame: ((Float) -> Unit)? = null,
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
                .padding(horizontal = 16.dp)
                .widthIn(max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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

private fun interpolateSample(
    points: List<TrackPoint>,
    cumulativeDistances: DoubleArray,
    progress: Float,
): FlyoverSample {
    val total = cumulativeDistances.lastOrNull() ?: 0.0
    if (total <= 0.0 || progress <= 0f) {
        val first = points.first()
        return FlyoverSample(first.lat, first.lng, first.ele, first.hr, 0.0, 0)
    }
    if (progress >= 1f) {
        val last = points.last()
        return FlyoverSample(last.lat, last.lng, last.ele, last.hr, total, points.lastIndex)
    }
    val targetDist = total * progress.toDouble()
    var segIdx = 0
    for (i in 1 until cumulativeDistances.size) {
        if (cumulativeDistances[i] >= targetDist) {
            segIdx = i - 1
            break
        }
        segIdx = i - 1
    }
    val a = points[segIdx]
    val b = points[(segIdx + 1).coerceAtMost(points.lastIndex)]
    val d0 = cumulativeDistances[segIdx]
    val d1 = cumulativeDistances[(segIdx + 1).coerceAtMost(cumulativeDistances.lastIndex)]
    val span = (d1 - d0).coerceAtLeast(1e-6)
    val t = ((targetDist - d0) / span).coerceIn(0.0, 1.0)

    return FlyoverSample(
        lat = a.lat + (b.lat - a.lat) * t,
        lng = a.lng + (b.lng - a.lng) * t,
        ele = a.ele + (b.ele - a.ele) * t,
        hr = (a.hr + (b.hr - a.hr) * t).roundToInt(),
        distanceMeters = targetDist,
        segmentIndex = segIdx
    )
}
