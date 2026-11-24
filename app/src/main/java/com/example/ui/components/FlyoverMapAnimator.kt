package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.FlyoverCameraMode
import com.example.core.FlyoverDisplayConfig
import com.example.core.FlyoverVideoExporter
import com.example.core.Formatters
import com.example.core.MapLayerType
import com.example.core.MapProjection
import com.example.core.ShareCardRatio
import com.example.data.model.WorkoutActivity
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.EmeraldPeak
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

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

    var internalRatio by remember { mutableStateOf(ShareCardRatio.PORTRAIT_9_16) }
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
    var layerType by remember(darkTheme) {
        mutableStateOf(if (darkTheme) MapLayerType.DARK else MapLayerType.STANDARD)
    }
    var displayConfig by remember(workout.id) { mutableStateOf(FlyoverDisplayConfig()) }

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
        val key = "${workout.id}_${activeRatio.name}_${cameraMode.name}_${speedMultiplier}_${layerType.name}_${displayConfig.hashCode()}_$darkTheme"
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
            displayConfig = displayConfig,
            layerType = layerType,
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
        // Camera Mode Bar (3D Follow, Cinematic 3D, Full Route)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlyoverCameraMode.entries.forEach { mode ->
                val modeIcon = when (mode) {
                    FlyoverCameraMode.FOLLOW_3D -> Icons.Rounded.Explore
                    FlyoverCameraMode.CINEMATIC_3D -> Icons.Rounded.Videocam
                    FlyoverCameraMode.FULL_ROUTE -> Icons.Rounded.ZoomOutMap
                }
                FilterChip(
                    selected = cameraMode == mode,
                    onClick = { cameraMode = mode },
                    leadingIcon = {
                        Icon(
                            imageVector = modeIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(mode.label, maxLines = 1) },
                    modifier = Modifier.testTag("flyover_mode_${mode.name.lowercase()}")
                )
            }
        }

        // Aspect Ratio (1:1, 16:9, 9:16) & Speed Selector Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AspectRatio,
                contentDescription = null,
                tint = SignalCyan,
                modifier = Modifier.size(16.dp)
            )
            ShareCardRatio.entries.forEach { ratio ->
                FilterChip(
                    selected = activeRatio == ratio,
                    onClick = { updateRatio(ratio) },
                    label = { Text(ratio.label, maxLines = 1) },
                    modifier = Modifier.testTag("flyover_ratio_${ratio.fileTag}")
                )
            }
            Spacer(Modifier.width(4.dp))
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

        // Map Layer Bar (Standard, Dark, Terrain, Cycling)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Layers,
                contentDescription = null,
                tint = SignalCyan,
                modifier = Modifier.size(16.dp)
            )
            MapLayerType.entries.forEach { layer ->
                FilterChip(
                    selected = layerType == layer,
                    onClick = { layerType = layer },
                    label = { Text(layer.label, maxLines = 1) },
                    modifier = Modifier.testTag("flyover_layer_${layer.name.lowercase()}")
                )
            }
        }

        // Data Display Toggles Bar (show/hide telemetry overlays on Flyover & exported MP4)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = VeloOrange,
                modifier = Modifier.size(16.dp)
            )
            FilterChip(
                selected = displayConfig.showDistance,
                onClick = { displayConfig = displayConfig.copy(showDistance = !displayConfig.showDistance) },
                label = { Text("Jarak", maxLines = 1) },
                modifier = Modifier.testTag("flyover_toggle_distance")
            )
            FilterChip(
                selected = displayConfig.showDuration,
                onClick = { displayConfig = displayConfig.copy(showDuration = !displayConfig.showDuration) },
                label = { Text("Waktu", maxLines = 1) },
                modifier = Modifier.testTag("flyover_toggle_duration")
            )
            FilterChip(
                selected = displayConfig.showElevation,
                onClick = { displayConfig = displayConfig.copy(showElevation = !displayConfig.showElevation) },
                label = { Text("Elevasi", maxLines = 1) },
                modifier = Modifier.testTag("flyover_toggle_elevation")
            )
            FilterChip(
                selected = displayConfig.showPaceOrSpeed,
                onClick = { displayConfig = displayConfig.copy(showPaceOrSpeed = !displayConfig.showPaceOrSpeed) },
                label = { Text(if (workout.parsedSport.isPaceBased) "Pace" else "Speed", maxLines = 1) },
                modifier = Modifier.testTag("flyover_toggle_pace")
            )
            FilterChip(
                selected = displayConfig.showHeartRate,
                onClick = { displayConfig = displayConfig.copy(showHeartRate = !displayConfig.showHeartRate) },
                label = { Text("HR", maxLines = 1) },
                modifier = Modifier.testTag("flyover_toggle_hr")
            )
            FilterChip(
                selected = displayConfig.showElevationProfile,
                onClick = { displayConfig = displayConfig.copy(showElevationProfile = !displayConfig.showElevationProfile) },
                label = { Text("Grafik", maxLines = 1) },
                modifier = Modifier.testTag("flyover_toggle_profile")
            )
            FilterChip(
                selected = displayConfig.showBrandBadge,
                onClick = { displayConfig = displayConfig.copy(showBrandBadge = !displayConfig.showBrandBadge) },
                label = { Text("Badge", maxLines = 1) },
                modifier = Modifier.testTag("flyover_toggle_badge")
            )
        }

        // Animated Map Stage sized generously to the chosen aspect ratio (1:1, 16:9, 9:16)
        val stageHeight = when (activeRatio) {
            ShareCardRatio.LANDSCAPE_16_9 -> 240.dp
            ShareCardRatio.SQUARE_1_1 -> 372.dp
            ShareCardRatio.PORTRAIT_9_16 -> 450.dp
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

        val tileBitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }
        val flyoverDarkTileFilter = remember {
            ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.505f, -1.258f, -0.127f, 0f, 232f,
                        -0.375f, -0.378f, -0.127f, 0f, 232f,
                        -0.375f, -1.258f, 0.753f, 0f, 232f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
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
                    .clipToBounds()
                    .background(if (layerType == MapLayerType.DARK || darkTheme) Color(0xFF0E131B) else Color(0xFFECEAE4))
            ) {
                val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(16f)
                val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(16f)
                val padPx = with(density) { 24.dp.toPx() }
                val isCinematic = cameraMode == FlyoverCameraMode.CINEMATIC_3D

                val cinematicPose = if (isCinematic) {
                    FlyoverVideoExporter.computeCinematicCameraPose(
                        points = points,
                        cumulativeDistances = cumulativeDistances,
                        progress = progress,
                        ratio = activeRatio
                    )
                } else {
                    null
                }

                val overviewViewport = remember(points, widthPx, heightPx, padPx) {
                    MapProjection.computeViewportForPoints(
                        points = points,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        paddingPx = padPx
                    )
                }
                val activeViewport = when (cameraMode) {
                    FlyoverCameraMode.FOLLOW_3D -> MapProjection.computeViewportForCenter(
                        centerLat = currentSample.lat,
                        centerLng = currentSample.lng,
                        zoom = followZoom,
                        tileSizePx = 280.0
                    )
                    FlyoverCameraMode.CINEMATIC_3D -> {
                        val pose = cinematicPose!!
                        MapProjection.computeViewportForCenter(
                            centerLat = pose.lookAtLat,
                            centerLng = pose.lookAtLng,
                            zoom = followZoom,
                            tileSizePx = 280.0
                        )
                    }
                    FlyoverCameraMode.FULL_ROUTE -> overviewViewport
                }

                val tileAreaW = if (isCinematic) {
                    (hypot(widthPx, heightPx) * 1.05f).coerceIn(64f, 1400f)
                } else {
                    widthPx
                }
                val tileAreaH = if (isCinematic) tileAreaW else heightPx
                val visibleTiles = remember(activeViewport, tileAreaW, tileAreaH, layerType) {
                    MapProjection.visibleTiles(activeViewport, tileAreaW, tileAreaH, darkTheme, layerType)
                }
                val neededTileKeys = remember(visibleTiles, layerType) {
                    visibleTiles.map { "${layerType.name}/${it.zoom}/${it.x}/${it.y}" to it.url }
                }

                LaunchedEffect(neededTileKeys) {
                    val missing = neededTileKeys.filter { !tileBitmaps.containsKey(it.first) }
                    if (missing.isEmpty()) return@LaunchedEffect
                    val fetched = withContext(Dispatchers.IO) {
                        val decodeOpts = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.RGB_565
                        }
                        missing.mapNotNull { (key, url) ->
                            val bmp = runCatching {
                                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                                    connectTimeout = 5000
                                    readTimeout = 5000
                                    setRequestProperty("User-Agent", "VeloStride/1.0 (Android; com.aistudio.velostride.kxqrmz)")
                                }
                                conn.inputStream.use { stream -> BitmapFactory.decodeStream(stream, null, decodeOpts) }
                            }.getOrNull()
                            bmp?.asImageBitmap()?.let { key to it }
                        }
                    }
                    fetched.forEach { (k, img) ->
                        if (tileBitmaps.size >= 36) {
                            val oldest = tileBitmaps.keys.firstOrNull()
                            if (oldest != null) tileBitmaps.remove(oldest)
                        }
                        tileBitmaps[k] = img
                    }
                }

                if (isCinematic) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        if (darkTheme) Color(0xFF060A13) else Color(0xFF1E293B),
                                        if (darkTheme) Color(0xFF101928) else Color(0xFF334155)
                                    )
                                )
                            )
                    )
                }

                // Single bounded Canvas with safe single-layer 3D tilt (avoids nested RenderNode + requiredSize crashes)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .graphicsLayer {
                            when (cameraMode) {
                                FlyoverCameraMode.FOLLOW_3D -> {
                                    rotationX = 24f
                                    cameraDistance = 16f
                                    scaleX = 1.20f
                                    scaleY = 1.20f
                                }
                                FlyoverCameraMode.CINEMATIC_3D -> {
                                    val pose = cinematicPose!!
                                    val safePitch = pose.pitchDeg.takeIf { it.isFinite() }?.coerceIn(20f, 38f) ?: 33f
                                    val safeScale = pose.zoomScale.takeIf { it.isFinite() }?.coerceIn(1.05f, 1.45f) ?: 1.22f
                                    val safeTy = (heightPx * pose.translationYFraction).takeIf { it.isFinite() } ?: 0f
                                    rotationX = safePitch
                                    cameraDistance = 16f
                                    scaleX = safeScale
                                    scaleY = safeScale
                                    translationY = safeTy
                                }
                                FlyoverCameraMode.FULL_ROUTE -> Unit
                            }
                        }
                ) {
                    if (points.isEmpty() || size.width <= 1f || size.height <= 1f) return@Canvas
                    val rotDeg = if (isCinematic) {
                        cinematicPose?.rotationZDeg?.takeIf { it.isFinite() } ?: 0f
                    } else 0f

                    withTransform({
                        if (isCinematic && rotDeg != 0f) {
                            rotate(degrees = rotDeg, pivot = center)
                        }
                    }) {
                        // 1. Map tiles (drawn safely from RGB_565 ImageBitmaps)
                        val offsetX = (size.width - tileAreaW) / 2f
                        val offsetY = (size.height - tileAreaH) / 2f
                        for (tile in visibleTiles) {
                            val key = "${layerType.name}/${tile.zoom}/${tile.x}/${tile.y}"
                            val img = tileBitmaps[key] ?: continue
                            val left = (tile.leftPx + offsetX).roundToInt()
                            val top = (tile.topPx + offsetY).roundToInt()
                            val tileDim = ceil(tile.sizePx + 1.5f).toInt().coerceAtLeast(1)
                            drawImage(
                                image = img,
                                dstOffset = IntOffset(left, top),
                                dstSize = IntSize(tileDim, tileDim),
                                colorFilter = if (layerType == MapLayerType.DARK) flyoverDarkTileFilter else null
                            )
                        }
                        drawRect(
                            color = if (layerType == MapLayerType.DARK || darkTheme) {
                                Color.Black.copy(alpha = 0.14f)
                            } else {
                                Color.White.copy(alpha = 0.06f)
                            },
                            topLeft = Offset(-size.width, -size.height),
                            size = androidx.compose.ui.geometry.Size(size.width * 3f, size.height * 3f)
                        )

                        // 2. Route geometry + 3D elevation relief
                        val finiteEles = points.mapNotNull { it.ele.takeIf { e -> e.isFinite() } }
                        val minEle = finiteEles.minOrNull() ?: 0.0
                        val maxEle = finiteEles.maxOrNull() ?: 0.0
                        val eleSpan = (maxEle - minEle).coerceAtLeast(1.0)
                        val hasRelief = isCinematic && (maxEle - minEle) >= 3.0
                        val maxLiftPx = if (hasRelief) 24f else 0f
                        val rotRad = if (isCinematic) Math.toRadians(rotDeg.toDouble()) else 0.0
                        val upX = (-sin(rotRad)).toFloat().takeIf { it.isFinite() } ?: 0f
                        val upY = (-cos(rotRad)).toFloat().takeIf { it.isFinite() } ?: -1f

                        val groundPts = ArrayList<Offset>(points.size)
                        val liftedPts = ArrayList<Offset>(points.size)
                        for (pt in points) {
                            val (pxRaw, pyRaw) = MapProjection.project(pt.lat, pt.lng, activeViewport, size.width, size.height)
                            val px = pxRaw.takeIf { it.isFinite() } ?: (size.width / 2f)
                            val py = pyRaw.takeIf { it.isFinite() } ?: (size.height / 2f)
                            groundPts.add(Offset(px, py))
                            val ptEle = pt.ele.takeIf { it.isFinite() } ?: minEle
                            val lift = if (hasRelief) {
                                (((ptEle - minEle) / eleSpan).toFloat().coerceIn(0f, 1f)) * maxLiftPx
                            } else 0f
                            liftedPts.add(Offset(px + upX * lift, py + upY * lift))
                        }
                        if (liftedPts.isEmpty()) return@withTransform

                        if (isCinematic) {
                            val shadowPath = Path().apply {
                                moveTo(groundPts.first().x, groundPts.first().y)
                                for (i in 1 until groundPts.size) {
                                    lineTo(groundPts[i].x, groundPts[i].y)
                                }
                            }
                            drawPath(
                                shadowPath,
                                Color.Black.copy(alpha = 0.42f),
                                style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                            if (hasRelief) {
                                val step = (points.size / 36).coerceAtLeast(1)
                                for (i in points.indices step step) {
                                    drawLine(
                                        color = SignalCyan.copy(alpha = 0.28f),
                                        start = groundPts[i],
                                        end = liftedPts[i],
                                        strokeWidth = 2f
                                    )
                                }
                            }
                        }

                        val fullPath = Path().apply {
                            moveTo(liftedPts.first().x, liftedPts.first().y)
                            for (i in 1 until liftedPts.size) {
                                lineTo(liftedPts[i].x, liftedPts[i].y)
                            }
                        }
                        drawPath(
                            fullPath,
                            VeloOrange.copy(alpha = 0.24f),
                            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        val (headGXRaw, headGYRaw) = MapProjection.project(
                            currentSample.lat,
                            currentSample.lng,
                            activeViewport,
                            size.width,
                            size.height
                        )
                        val headGX = headGXRaw.takeIf { it.isFinite() } ?: liftedPts.first().x
                        val headGY = headGYRaw.takeIf { it.isFinite() } ?: liftedPts.first().y
                        val sampleEle = currentSample.ele.takeIf { it.isFinite() } ?: minEle
                        val headLift = if (hasRelief) {
                            (((sampleEle - minEle) / eleSpan).toFloat().coerceIn(0f, 1f)) * maxLiftPx
                        } else 0f
                        val headGround = Offset(headGX, headGY)
                        val headOffset = Offset(headGX + upX * headLift, headGY + upY * headLift)

                        val activePath = Path().apply {
                            moveTo(liftedPts.first().x, liftedPts.first().y)
                            val maxSeg = currentSample.segmentIndex.coerceIn(0, liftedPts.lastIndex)
                            for (i in 1..maxSeg) {
                                lineTo(liftedPts[i].x, liftedPts[i].y)
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
                            style = Stroke(width = if (isCinematic) 8f else 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        if (isCinematic) {
                            drawPath(
                                activePath,
                                Color(0xAAFFF3E0),
                                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                            val aheadSample = FlyoverVideoExporter.interpolateSample(
                                points,
                                cumulativeDistances,
                                (progress + 0.025f).coerceIn(0f, 1f)
                            )
                            val (aGXRaw, aGYRaw) = MapProjection.project(
                                aheadSample.lat,
                                aheadSample.lng,
                                activeViewport,
                                size.width,
                                size.height
                            )
                            val aGX = aGXRaw.takeIf { it.isFinite() } ?: headGX
                            val aGY = aGYRaw.takeIf { it.isFinite() } ?: headGY
                            val aEle = aheadSample.ele.takeIf { it.isFinite() } ?: minEle
                            val aLift = if (hasRelief) {
                                (((aEle - minEle) / eleSpan).toFloat().coerceIn(0f, 1f)) * maxLiftPx
                            } else 0f
                            val dx = (aGX + upX * aLift) - headOffset.x
                            val dy = (aGY + upY * aLift) - headOffset.y
                            val len = hypot(dx, dy)
                            if (len.isFinite() && len > 2f) {
                                drawLine(
                                    color = SignalCyan.copy(alpha = 0.65f),
                                    start = headOffset,
                                    end = Offset(headOffset.x + (dx / len) * 34f, headOffset.y + (dy / len) * 34f),
                                    strokeWidth = 3.5f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        drawCircle(EmeraldPeak, radius = 8.5f, center = liftedPts.first())
                        drawCircle(Color.White, radius = 3.5f, center = liftedPts.first())
                        if (progress >= 0.99f) {
                            drawCircle(CrimsonPulse, radius = 9.5f, center = liftedPts.last())
                            drawCircle(Color.White, radius = 4f, center = liftedPts.last())
                        }

                        if (isCinematic && headLift > 2f) {
                            drawCircle(Color.Black.copy(alpha = 0.45f), radius = 8f, center = headGround)
                            drawLine(SignalCyan.copy(alpha = 0.65f), headGround, headOffset, strokeWidth = 2.5f)
                        }

                        drawCircle(SignalCyan.copy(alpha = 0.28f), radius = 22f, center = headOffset)
                        drawCircle(VeloOrange, radius = 12f, center = headOffset)
                        drawCircle(Color.White, radius = 5f, center = headOffset)
                    }
                }

                if (isCinematic) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.28f)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xEB070C16), Color.Transparent)
                                )
                            )
                    )
                }

                if (displayConfig.showBrandBadge) {
                    Surface(
                        color = Color(0xB30D121A),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            VeloStrideWordmark(
                                fontSize = 10.sp,
                                onDarkSurface = true
                            )
                            if (isCinematic) {
                                Text(
                                    text = "• 3D",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SignalCyan
                                )
                            }
                        }
                    }
                }

                // Compact Live Telemetry Pill HUD (controlled by FlyoverDisplayConfig toggles)
                val compactHud = activeRatio == ShareCardRatio.PORTRAIT_9_16
                val hasAnyHudMetric = displayConfig.showDistance ||
                    displayConfig.showDuration ||
                    displayConfig.showElevation ||
                    displayConfig.showPaceOrSpeed ||
                    (displayConfig.showHeartRate && currentSample.hr > 0)

                if (hasAnyHudMetric) {
                    Surface(
                        color = Color(0xCC0D121A),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .testTag("flyover_hud_overlay")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(if (compactHud) 7.dp else 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = if (compactHud) 8.dp else 11.dp, vertical = 6.dp)
                        ) {
                            if (displayConfig.showDistance) {
                                val distVal = Formatters.metersToDisplayDistance(coveredKm * 1000.0)
                                val distUnit = Formatters.distanceUnitLabel()
                                Column {
                                    Text("DIST", fontSize = 8.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                                    Text(
                                        text = String.format("%.2f", distVal) + if (compactHud) "" else " $distUnit",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (compactHud) 11.sp else 13.sp,
                                        color = VeloOrange
                                    )
                                }
                            }
                            if (displayConfig.showDuration) {
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
                            }
                            if (displayConfig.showElevation) {
                                Column {
                                    Text("ELEV", fontSize = 8.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                                    Text(
                                        text = Formatters.formatElevation(currentSample.ele),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (compactHud) 11.sp else 13.sp,
                                        color = SignalCyan
                                    )
                                }
                            }
                            if (displayConfig.showPaceOrSpeed && !compactHud) {
                                Column {
                                    Text(
                                        text = if (workout.parsedSport.isPaceBased) "PACE" else "SPD",
                                        fontSize = 8.sp,
                                        color = Color(0xFF94A3B8),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (workout.parsedSport.isPaceBased) {
                                            Formatters.formatPaceShort(workout.avgPaceSecPerKm)
                                        } else {
                                            val spd = if (Formatters.unitSystem == com.example.core.UnitSystem.IMPERIAL) {
                                                workout.avgSpeedKmh / Formatters.KM_PER_MILE
                                            } else {
                                                workout.avgSpeedKmh
                                            }
                                            String.format("%.1f", spd)
                                        },
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = EmeraldPeak
                                    )
                                }
                            }
                            if (displayConfig.showHeartRate && currentSample.hr > 0 && !compactHud) {
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
                }

                MapAttributionBadge(
                    darkTheme = darkTheme,
                    layerType = layerType,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                )
            }
        }

        // Synced Elevation Sparkline (controlled by FlyoverDisplayConfig.showElevationProfile)
        if (displayConfig.showElevationProfile) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .testTag("flyover_elevation_sparkline")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlyoverMapDialog(
    workout: WorkoutActivity,
    onShareFrame: ((Float, ShareCardRatio) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
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
                                    text = "Flyover Map Animator",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = workout.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("close_flyover_dialog_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close Flyover")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    FlyoverMapAnimator(
                        workout = workout,
                        onShareSnapshot = onShareFrame,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}
