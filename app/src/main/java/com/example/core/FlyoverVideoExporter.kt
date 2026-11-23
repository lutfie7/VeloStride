package com.example.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.TrackPoint
import com.example.data.model.WorkoutActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.roundToInt

enum class FlyoverCameraMode(val label: String) {
    FOLLOW_3D("3D Follow"),
    FULL_ROUTE("Full Route"),
}

data class FlyoverSample(
    val lat: Double,
    val lng: Double,
    val ele: Double,
    val hr: Int,
    val distanceMeters: Double,
    val segmentIndex: Int,
)

object FlyoverVideoExporter {

    private const val FPS = 30
    private const val BIT_RATE = 4_000_000
    private const val EGL_RECORDABLE_ANDROID = 0x3142

    fun computeCumulativeDistances(points: List<TrackPoint>): DoubleArray {
        val dists = DoubleArray(points.size)
        for (i in 1 until points.size) {
            dists[i] = dists[i - 1] + Formatters.haversineMeters(
                points[i - 1].lat,
                points[i - 1].lng,
                points[i].lat,
                points[i].lng
            )
        }
        return dists
    }

    fun interpolateSample(
        points: List<TrackPoint>,
        cumulativeDistances: DoubleArray,
        progress: Float,
    ): FlyoverSample {
        if (points.isEmpty()) return FlyoverSample(0.0, 0.0, 0.0, 0, 0.0, 0)
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

    fun computeFlyoverVideoSize(ratio: ShareCardRatio): Pair<Int, Int> = when (ratio) {
        ShareCardRatio.SQUARE_1_1 -> 720 to 720
        ShareCardRatio.LANDSCAPE_16_9 -> 1280 to 720
        ShareCardRatio.PORTRAIT_9_16 -> 720 to 1280
    }

    fun suggestedVideoFileName(title: String, ratio: ShareCardRatio): String {
        val slug = title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(28)
            .ifBlank { "activity" }
        return "velostride_flyover_${slug}_${ratio.fileTag}.mp4"
    }

    suspend fun exportFlyoverVideoToCache(
        context: Context,
        workout: WorkoutActivity,
        ratio: ShareCardRatio,
        cameraMode: FlyoverCameraMode,
        speedMultiplier: Float,
        darkTheme: Boolean,
        onProgress: (Float, String) -> Unit,
    ): File = withContext(Dispatchers.Default) {
        val points = workout.decodedPoints
        require(points.size >= 2) { "Minimal 2 titik GPS diperlukan untuk membuat video flyover." }

        val (vidW, vidH) = computeFlyoverVideoSize(ratio)
        val cumulativeDistances = computeCumulativeDistances(points)
        val totalTrackMeters = cumulativeDistances.lastOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val displayTotalMeters = workout.distanceMeters.takeIf { it > 10.0 } ?: totalTrackMeters
        val followZoom = when {
            displayTotalMeters > 35_000.0 -> 13
            displayTotalMeters > 14_000.0 -> 14
            displayTotalMeters > 4_000.0 -> 15
            else -> 16
        }

        val durationSec = (12f / speedMultiplier.coerceIn(1f, 8f)).coerceIn(4f, 12f)
        val totalFrames = (durationSec * FPS).roundToInt().coerceIn(90, 360)

        onProgress(0.04f, "Memuat tile peta OpenStreetMap...")
        val tileCache = withContext(Dispatchers.IO) {
            prefetchFlyoverTiles(
                points = points,
                cumulativeDistances = cumulativeDistances,
                widthPx = vidW.toFloat(),
                heightPx = vidH.toFloat(),
                cameraMode = cameraMode,
                followZoom = followZoom,
                darkTheme = darkTheme
            )
        }

        val outDir = File(context.cacheDir, "shared_videos").apply { mkdirs() }
        val outFile = File(outDir, suggestedVideoFileName(workout.title, ratio))
        if (outFile.exists()) outFile.delete()

        val frameBitmap = Bitmap.createBitmap(vidW, vidH, Bitmap.Config.ARGB_8888)
        val frameCanvas = Canvas(frameBitmap)
        val overviewViewport = MapProjection.computeViewportForPoints(
            points = points,
            widthPx = vidW.toFloat(),
            heightPx = vidH.toFloat(),
            paddingPx = 64f
        )

        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            if (darkTheme) {
                colorFilter = ColorMatrixColorFilter(
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
        }

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1
        var eglState: EglSurfaceRenderer? = null

        try {
            onProgress(0.15f, "Menyiapkan encoder video H.264...")
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, vidW, vidH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec = videoCodec
            videoCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = videoCodec.createInputSurface()
            videoCodec.start()

            val videoMuxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = videoMuxer

            val renderer = EglSurfaceRenderer(inputSurface, vidW, vidH)
            eglState = renderer

            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationNs = 1_000_000_000L / FPS
            val minEle = points.minOf { it.ele }
            val maxEle = points.maxOf { it.ele }

            for (frameIdx in 0 until totalFrames) {
                // Hold briefly at the finish so viewers see the final route
                val animFrac = (frameIdx.toFloat() / (totalFrames - 18).coerceAtLeast(1)).coerceIn(0f, 1f)
                val sample = interpolateSample(points, cumulativeDistances, animFrac)
                val activeViewport = if (cameraMode == FlyoverCameraMode.FOLLOW_3D) {
                    MapProjection.computeViewportForCenter(
                        centerLat = sample.lat,
                        centerLng = sample.lng,
                        zoom = followZoom,
                        tileSizePx = 320.0
                    )
                } else {
                    overviewViewport
                }

                drawFlyoverFrame(
                    canvas = frameCanvas,
                    width = vidW,
                    height = vidH,
                    workout = workout,
                    points = points,
                    cumulativeDistances = cumulativeDistances,
                    totalTrackMeters = totalTrackMeters,
                    displayTotalMeters = displayTotalMeters,
                    minEle = minEle,
                    maxEle = maxEle,
                    sample = sample,
                    progress = animFrac,
                    cameraMode = cameraMode,
                    viewport = activeViewport,
                    tileCache = tileCache,
                    tilePaint = tilePaint,
                    darkTheme = darkTheme
                )

                renderer.renderBitmap(frameBitmap, frameIdx * frameDurationNs)
                val drainResult = drainEncoder(
                    codec = videoCodec,
                    bufferInfo = bufferInfo,
                    muxer = videoMuxer,
                    currentTrackIndex = trackIndex,
                    muxerStarted = muxerStarted,
                    endOfStream = false
                )
                trackIndex = drainResult.first
                muxerStarted = drainResult.second

                if (frameIdx % 6 == 0 || frameIdx == totalFrames - 1) {
                    val pct = 0.15f + 0.80f * ((frameIdx + 1).toFloat() / totalFrames.toFloat())
                    onProgress(pct, "Merender video ${(pct * 100f).roundToInt()}%...")
                }
            }

            onProgress(0.96f, "Menyelesaikan file MP4...")
            videoCodec.signalEndOfInputStream()
            val finalDrain = drainEncoder(
                codec = videoCodec,
                bufferInfo = bufferInfo,
                muxer = videoMuxer,
                currentTrackIndex = trackIndex,
                muxerStarted = muxerStarted,
                endOfStream = true
            )
            muxerStarted = finalDrain.second
            onProgress(1f, "Video siap!")
            outFile
        } finally {
            runCatching { eglState?.release() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching {
                if (muxerStarted) muxer?.stop()
                muxer?.release()
            }
            frameBitmap.recycle()
            tileCache.values.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    suspend fun saveVideoToMediaStore(
        context: Context,
        sourceMp4: File,
        displayName: String,
    ): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VeloStride")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        try {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceMp4).use { input -> input.copyTo(out) }
            }
            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    suspend fun copyVideoToUri(
        context: Context,
        sourceMp4: File,
        destinationUri: Uri,
    ) = withContext(Dispatchers.IO) {
        val out: OutputStream = context.contentResolver.openOutputStream(destinationUri)
            ?: error("Tidak dapat membuka file tujuan.")
        out.use { stream ->
            FileInputStream(sourceMp4).use { input -> input.copyTo(stream) }
        }
    }

    fun shareVideoFile(
        context: Context,
        mp4File: File,
        workoutTitle: String,
    ) {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            mp4File
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "$workoutTitle — VeloStride Flyover")
            putExtra(Intent.EXTRA_TEXT, "$workoutTitle • Flyover Route Animation via VeloStride")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Flyover Video"))
    }

    private fun prefetchFlyoverTiles(
        points: List<TrackPoint>,
        cumulativeDistances: DoubleArray,
        widthPx: Float,
        heightPx: Float,
        cameraMode: FlyoverCameraMode,
        followZoom: Int,
        darkTheme: Boolean,
    ): Map<String, Bitmap> {
        val neededUrls = LinkedHashMap<String, String>()
        if (cameraMode == FlyoverCameraMode.FULL_ROUTE) {
            val vp = MapProjection.computeViewportForPoints(points, widthPx, heightPx, 64f)
            MapProjection.visibleTiles(vp, widthPx, heightPx, darkTheme).forEach { tile ->
                neededUrls["${tile.zoom}/${tile.x}/${tile.y}"] = tile.url
            }
        } else {
            val steps = 16
            for (s in 0..steps) {
                val sample = interpolateSample(points, cumulativeDistances, s / steps.toFloat())
                val vp = MapProjection.computeViewportForCenter(
                    centerLat = sample.lat,
                    centerLng = sample.lng,
                    zoom = followZoom,
                    tileSizePx = 320.0
                )
                MapProjection.visibleTiles(vp, widthPx, heightPx, darkTheme).forEach { tile ->
                    val key = "${tile.zoom}/${tile.x}/${tile.y}"
                    if (neededUrls.size < 48) {
                        neededUrls.putIfAbsent(key, tile.url)
                    }
                }
            }
        }

        val cache = HashMap<String, Bitmap>(neededUrls.size)
        for ((key, url) in neededUrls) {
            val bmp = runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "VeloStride/1.0 (Android; com.aistudio.velostride.kxqrmz)")
                }
                conn.inputStream.use { stream -> BitmapFactory.decodeStream(stream) }
            }.getOrNull()
            if (bmp != null) {
                cache[key] = bmp
            }
        }
        return cache
    }

    private fun drawFlyoverFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        workout: WorkoutActivity,
        points: List<TrackPoint>,
        cumulativeDistances: DoubleArray,
        totalTrackMeters: Double,
        displayTotalMeters: Double,
        minEle: Double,
        maxEle: Double,
        sample: FlyoverSample,
        progress: Float,
        cameraMode: FlyoverCameraMode,
        viewport: MapViewport,
        tileCache: Map<String, Bitmap>,
        tilePaint: Paint,
        darkTheme: Boolean,
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(if (darkTheme) 0xFF0E131B.toInt() else 0xFFECEAE4.toInt())

        canvas.save()
        if (cameraMode == FlyoverCameraMode.FOLLOW_3D) {
            val camera = Camera()
            val matrix = Matrix()
            camera.save()
            camera.rotateX(24f)
            camera.getMatrix(matrix)
            camera.restore()
            matrix.preTranslate(-w / 2f, -h / 2f)
            matrix.postScale(1.22f, 1.22f)
            matrix.postTranslate(w / 2f, h / 2f)
            canvas.concat(matrix)
        }

        val tiles = MapProjection.visibleTiles(viewport, w, h, darkTheme)
        for (tile in tiles) {
            val bmp = tileCache["${tile.zoom}/${tile.x}/${tile.y}"]
            if (bmp != null && !bmp.isRecycled) {
                val dst = RectF(
                    tile.leftPx,
                    tile.topPx,
                    tile.leftPx + tile.sizePx + 1.5f,
                    tile.topPx + tile.sizePx + 1.5f
                )
                canvas.drawBitmap(bmp, null, dst, tilePaint)
            }
        }
        canvas.drawColor(if (darkTheme) 0x22000000 else 0x12FFFFFF)

        // Projected route
        val projectedX = FloatArray(points.size)
        val projectedY = FloatArray(points.size)
        val fullPath = Path()
        for (i in points.indices) {
            val (px, py) = MapProjection.project(points[i].lat, points[i].lng, viewport, w, h)
            projectedX[i] = px
            projectedY[i] = py
            if (i == 0) fullPath.moveTo(px, py) else fullPath.lineTo(px, py)
        }

        val upcomingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FC4C02
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(fullPath, upcomingPaint)

        val (headX, headY) = MapProjection.project(sample.lat, sample.lng, viewport, w, h)
        val activePath = Path().apply {
            moveTo(projectedX[0], projectedY[0])
            val maxSeg = sample.segmentIndex.coerceIn(0, points.lastIndex)
            for (i in 1..maxSeg) {
                lineTo(projectedX[i], projectedY[i])
            }
            lineTo(headX, headY)
        }

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (darkTheme) 0x80000000.toInt() else 0x40000000
            style = Paint.Style.STROKE
            strokeWidth = 18f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55FC4C02
            style = Paint.Style.STROKE
            strokeWidth = 24f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFC4C02.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(activePath, haloPaint)
        canvas.drawPath(activePath, glowPaint)
        canvas.drawPath(activePath, trailPaint)

        // Start, Finish & Moving Head
        val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF10B981.toInt() }
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(projectedX[0], projectedY[0], 11f, startPaint)
        canvas.drawCircle(projectedX[0], projectedY[0], 4.5f, whitePaint)

        if (progress >= 0.99f) {
            val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEF4444.toInt() }
            canvas.drawCircle(projectedX.last(), projectedY.last(), 12f, endPaint)
            canvas.drawCircle(projectedX.last(), projectedY.last(), 5f, whitePaint)
        }

        val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x4800D4FF }
        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFC4C02.toInt() }
        canvas.drawCircle(headX, headY, 28f, pulsePaint)
        canvas.drawCircle(headX, headY, 15f, headPaint)
        canvas.drawCircle(headX, headY, 6f, whitePaint)

        canvas.restore()

        // Flat HUD Overlay
        val pad = 28f
        val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD00D121A.toInt() }
        val hudLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt()
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val hudValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val coveredKm = (displayTotalMeters * progress) / 1000.0
        val elapsedSec = (workout.movingTimeSeconds * progress).roundToInt().toLong()
        val hasHr = sample.hr > 0
        val hudWidth = (if (hasHr) 540f else 420f).coerceAtMost(w - pad * 2f)
        val hudRect = RectF(pad, pad, pad + hudWidth, pad + 96f)
        canvas.drawRoundRect(hudRect, 20f, 20f, hudBgPaint)

        val colCount = if (hasHr) 4 else 3
        val colW = (hudWidth - 32f) / colCount
        val metricEntries = buildList {
            add(Triple("DIST", String.format("%.2f km", coveredKm), 0xFFFC4C02.toInt()))
            add(Triple("TIME", Formatters.formatDuration(elapsedSec), Color.WHITE))
            add(Triple("ELEV", "${sample.ele.roundToInt()} m", 0xFF00D4FF.toInt()))
            if (hasHr) add(Triple("HR", "${sample.hr}", 0xFFEF4444.toInt()))
        }
        metricEntries.forEachIndexed { idx, (lbl, v, col) ->
            val x = pad + 18f + idx * colW
            canvas.drawText(lbl, x, pad + 34f, hudLabelPaint)
            hudValPaint.color = col
            canvas.drawText(v, x, pad + 74f, hudValPaint)
        }

        // Top-right Brand / Activity badge (shown cleanly when space allows)
        val brandText = "VELOSTRIDE"
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFC4C02.toInt()
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.12f
        }
        val brandW = brandPaint.measureText(brandText) + 34f
        if (w - hudRect.right - pad > brandW + 14f) {
            val badgeRect = RectF(w - pad - brandW, pad, w - pad, pad + 50f)
            canvas.drawRoundRect(badgeRect, 14f, 14f, hudBgPaint)
            canvas.drawText(brandText, badgeRect.left + 17f, badgeRect.top + 33f, brandPaint)
        }

        // Bottom Elevation Sparkline Card
        val sparkTop = h - 108f
        val sparkBottom = h - 28f
        val sparkRect = RectF(pad, sparkTop, w - pad, sparkBottom)
        canvas.drawRoundRect(sparkRect, 16f, 16f, hudBgPaint)

        val span = (maxEle - minEle).coerceAtLeast(8.0)
        val innerL = sparkRect.left + 18f
        val innerR = sparkRect.right - 18f
        val innerW = innerR - innerL
        val innerT = sparkRect.top + 14f
        val innerB = sparkRect.bottom - 14f
        val innerH = innerB - innerT

        val elePath = Path()
        val fillPath = Path()
        for (i in points.indices) {
            val x = innerL + (cumulativeDistances[i] / totalTrackMeters).toFloat() * innerW
            val y = innerB - ((points[i].ele - minEle) / span).toFloat() * innerH
            if (i == 0) {
                elePath.moveTo(x, y)
                fillPath.moveTo(x, innerB)
                fillPath.lineTo(x, y)
            } else {
                elePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(innerR, innerB)
        fillPath.close()

        val sparkFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, innerT, 0f, innerB,
                0x5500D4FF, 0x0000D4FF,
                Shader.TileMode.CLAMP
            )
        }
        val sparkLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00D4FF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(fillPath, sparkFillPaint)
        canvas.drawPath(elePath, sparkLinePaint)

        val cursorX = innerL + progress * innerW
        val cursorY = innerB - ((sample.ele - minEle) / span).toFloat() * innerH
        val cursorLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFC4C02.toInt()
            strokeWidth = 3f
        }
        canvas.drawLine(cursorX, innerT, cursorX, innerB, cursorLinePaint)
        canvas.drawCircle(cursorX, cursorY, 7.5f, headPaint)
        canvas.drawCircle(cursorX, cursorY, 3f, whitePaint)

        val attrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB3FFFFFF.toInt()
            textSize = 15f
        }
        canvas.drawText("© OpenStreetMap", sparkRect.right - 134f, sparkRect.top - 10f, attrPaint)
    }

    private fun drainEncoder(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        currentTrackIndex: Int,
        muxerStarted: Boolean,
        endOfStream: Boolean,
    ): Pair<Int, Boolean> {
        var trackIdx = currentTrackIndex
        var started = muxerStarted
        val timeoutUs = if (endOfStream) 10_000L else 0L

        while (true) {
            val outIdx = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!started) { "MediaMuxer format changed twice." }
                    trackIdx = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                }
                outIdx >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outIdx)
                        ?: error("MediaCodec output buffer $outIdx was null.")
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0) {
                        check(started) { "MediaMuxer has not started." }
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIdx, encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
        return trackIdx to started
    }

    private class EglSurfaceRenderer(
        surface: android.view.Surface,
        private val width: Int,
        private val height: Int,
    ) {
        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var programId = 0
        private var textureId = 0
        private val vertexBuffer: FloatBuffer
        private val texBuffer: FloatBuffer

        init {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL14 display." }
            val version = IntArray(2)
            check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "Unable to initialize EGL14." }

            val configAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            check(EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)) {
                "Unable to find RGB888+recordable EGL config."
            }
            val chosenConfig = configs[0] ?: error("No EGLConfig returned.")

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, chosenConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "Failed creating EGLContext." }

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, chosenConfig, surface, surfaceAttribs, 0)
            check(eglSurface != EGL14.EGL_NO_SURFACE) { "Failed creating EGLSurface." }

            check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                "eglMakeCurrent failed."
            }

            val vertices = floatArrayOf(
                -1f, -1f,
                1f, -1f,
                -1f, 1f,
                1f, 1f
            )
            val texCoords = floatArrayOf(
                0f, 1f,
                1f, 1f,
                0f, 0f,
                1f, 0f
            )
            vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                    put(vertices)
                    position(0)
                }
            texBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                    put(texCoords)
                    position(0)
                }

            val vertexShader = compileShader(
                GLES20.GL_VERTEX_SHADER,
                """
                attribute vec4 aPos;
                attribute vec2 aTex;
                varying vec2 vTex;
                void main() {
                    gl_Position = aPos;
                    vTex = aTex;
                }
                """.trimIndent()
            )
            val fragmentShader = compileShader(
                GLES20.GL_FRAGMENT_SHADER,
                """
                precision mediump float;
                varying vec2 vTex;
                uniform sampler2D uTex;
                void main() {
                    gl_FragColor = texture2D(uTex, vTex);
                }
                """.trimIndent()
            )
            programId = GLES20.glCreateProgram().also { prog ->
                GLES20.glAttachShader(prog, vertexShader)
                GLES20.glAttachShader(prog, fragmentShader)
                GLES20.glLinkProgram(prog)
                GLES20.glDeleteShader(vertexShader)
                GLES20.glDeleteShader(fragmentShader)
            }

            val texIds = IntArray(1)
            GLES20.glGenTextures(1, texIds, 0)
            textureId = texIds[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        fun renderBitmap(bitmap: Bitmap, presentationTimeNs: Long) {
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(programId)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

            val posLoc = GLES20.glGetAttribLocation(programId, "aPos")
            val texLoc = GLES20.glGetAttribLocation(programId, "aTex")
            val uTexLoc = GLES20.glGetUniformLocation(programId, "uTex")
            GLES20.glUniform1i(uTexLoc, 0)

            GLES20.glEnableVertexAttribArray(posLoc)
            GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glEnableVertexAttribArray(texLoc)
            GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(posLoc)
            GLES20.glDisableVertexAttribArray(texLoc)

            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        fun release() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                if (programId != 0) GLES20.glDeleteProgram(programId)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            return shader
        }
    }
}
