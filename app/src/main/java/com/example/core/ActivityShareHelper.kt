package com.example.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.example.data.model.WorkoutActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

enum class ShareCardStyle(
    val label: String,
    val isDarkMap: Boolean,
    val isTransparent: Boolean = false,
) {
    OBSIDIAN("Obsidian", isDarkMap = true),
    SUMMIT_LIGHT("Summit", isDarkMap = false),
    SUNSET_PULSE("Sunset", isDarkMap = true),
    NEON_HUD("Neon HUD", isDarkMap = true),
    TRANSPARENT("Transparent", isDarkMap = true, isTransparent = true),
}

data class ShareCardConfig(
    val style: ShareCardStyle = ShareCardStyle.OBSIDIAN,
    val showMap: Boolean = true,
    val showDistance: Boolean = true,
    val showDuration: Boolean = true,
    val showElevation: Boolean = true,
    val showPaceOrSpeed: Boolean = true,
    val showHeartRate: Boolean = true,
    val showCalories: Boolean = false,
    val showEffort: Boolean = true,
    val showDateAndDevice: Boolean = true,
)

private data class ShareMetricSpec(
    val label: String,
    val value: String,
    val valueColor: Int,
)

object ActivityShareHelper {

    fun buildShareSummaryText(
        workout: WorkoutActivity,
        config: ShareCardConfig = ShareCardConfig(),
    ): String {
        val sport = workout.parsedSport
        val paceOrSpeed = if (sport.isPaceBased) {
            "Pace: ${Formatters.formatPace(workout.avgPaceSecPerKm)}"
        } else {
            "Speed: ${Formatters.formatSpeed(workout.avgSpeedKmh)}"
        }
        return buildString {
            appendLine("${workout.title} • ${sport.label}")
            if (config.showDateAndDevice) {
                appendLine("Date: ${Formatters.formatDateTime(workout.createdAt)}")
            }
            if (config.showDistance) {
                appendLine("Distance: ${Formatters.formatDistanceKm(workout.distanceMeters)}")
            }
            if (config.showDuration) {
                appendLine("Duration: ${Formatters.formatDuration(workout.movingTimeSeconds)}")
            }
            if (config.showElevation) {
                appendLine("Elevation Gain: ${Formatters.formatElevation(workout.elevationGainMeters)} (${workout.elevationSource ?: "GPS"})")
            }
            if (config.showPaceOrSpeed) {
                appendLine(paceOrSpeed)
            }
            if (config.showHeartRate && workout.avgHeartRate > 0) {
                appendLine("Heart Rate: Avg ${workout.avgHeartRate} bpm / Max ${workout.maxHeartRate} bpm")
            }
            if (config.showCalories && workout.calories > 0) {
                appendLine("Calories: ${workout.calories} kcal")
            }
            if (config.showEffort) {
                appendLine("Relative Effort: ${workout.relativeEffort}")
            }
            if (config.showDateAndDevice && !workout.deviceName.isNullOrBlank()) {
                appendLine("Tracked with: ${workout.deviceName}")
            }
            append("Tracked via VeloStride")
        }
    }

    fun shareAsText(
        context: Context,
        workout: WorkoutActivity,
        config: ShareCardConfig = ShareCardConfig(),
    ) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${workout.title} — VeloStride")
            putExtra(Intent.EXTRA_TEXT, buildShareSummaryText(workout, config))
        }
        context.startActivity(Intent.createChooser(intent, "Share Activity"))
    }

    /**
     * Fetches CARTO/OSM map tiles onto an offscreen Bitmap matching the Share Card map box so both
     * the live preview and exported PNG show real streets/terrain beneath the un-stretched route.
     */
    suspend fun fetchShareCardMapBackdrop(
        workout: WorkoutActivity,
        darkMap: Boolean,
        boxWidth: Int = 952,
        boxHeight: Int = 484,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val pts = workout.decodedPoints
        if (pts.size < 2) return@withContext null

        val viewport = MapProjection.computeViewportForPoints(
            points = pts,
            widthPx = boxWidth.toFloat(),
            heightPx = boxHeight.toFloat(),
            paddingPx = 56f
        )
        val tiles = MapProjection.visibleTiles(
            viewport = viewport,
            widthPx = boxWidth.toFloat(),
            heightPx = boxHeight.toFloat(),
            darkTheme = darkMap
        )
        if (tiles.isEmpty()) return@withContext null

        val backdrop = Bitmap.createBitmap(boxWidth, boxHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(backdrop)
        canvas.drawColor(if (darkMap) 0xFF10151E.toInt() else 0xFFECEAE4.toInt())

        var loadedCount = 0
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (tile in tiles) {
            val tileBmp = runCatching {
                val conn = (URL(tile.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "VeloStride/1.0 (Android Fitness Tracker)")
                }
                conn.inputStream.use { stream -> BitmapFactory.decodeStream(stream) }
            }.getOrNull()

            if (tileBmp != null) {
                val dst = RectF(
                    tile.leftPx,
                    tile.topPx,
                    tile.leftPx + tile.sizePx + 1.5f,
                    tile.topPx + tile.sizePx + 1.5f
                )
                canvas.drawBitmap(tileBmp, null, dst, tilePaint)
                tileBmp.recycle()
                loadedCount++
            }
        }

        if (loadedCount == 0) {
            backdrop.recycle()
            null
        } else {
            // Subtle contrast scrim
            canvas.drawColor(if (darkMap) 0x24000000 else 0x14FFFFFF)
            backdrop
        }
    }

    fun renderShareCardBitmap(
        workout: WorkoutActivity,
        config: ShareCardConfig = ShareCardConfig(),
        mapBackdrop: Bitmap? = null,
        progressFraction: Float = 1f,
    ): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cardStyle = config.style
        val primaryText: Int
        val secondaryText: Int
        val accentColor: Int
        val secondaryAccent: Int
        val mapBgColor: Int
        val dividerColor: Int
        val pillBgColor: Int

        when (cardStyle) {
            ShareCardStyle.OBSIDIAN -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(0xFF0E131B.toInt(), 0xFF151C28.toInt(), 0xFF0A0D12.toInt()),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
                primaryText = Color.WHITE
                secondaryText = 0xFF94A3B8.toInt()
                accentColor = 0xFFFC4C02.toInt()
                secondaryAccent = 0xFF00D4FF.toInt()
                mapBgColor = 0xFF101621.toInt()
                dividerColor = 0xFF232C3B.toInt()
                pillBgColor = 0x29FC4C02
            }
            ShareCardStyle.SUMMIT_LIGHT -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(0xFFF8FAFC.toInt(), 0xFFF1F5F9.toInt(), 0xFFE2E8F0.toInt()),
                        floatArrayOf(0f, 0.6f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
                primaryText = 0xFF0F172A.toInt()
                secondaryText = 0xFF475569.toInt()
                accentColor = 0xFFEA580C.toInt()
                secondaryAccent = 0xFF0284C7.toInt()
                mapBgColor = 0xFFE2E8F0.toInt()
                dividerColor = 0xFFCBD5E1.toInt()
                pillBgColor = 0x22EA580C
            }
            ShareCardStyle.SUNSET_PULSE -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(0xFF240B2D.toInt(), 0xFF6B1D3A.toInt(), 0xFF160E1F.toInt()),
                        floatArrayOf(0f, 0.48f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
                primaryText = Color.WHITE
                secondaryText = 0xFFE2B6C9.toInt()
                accentColor = 0xFFFF8C42.toInt()
                secondaryAccent = 0xFFFFD166.toInt()
                mapBgColor = 0x44100816
                dividerColor = 0x44FFFFFF
                pillBgColor = 0x33FF8C42
            }
            ShareCardStyle.NEON_HUD -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(0xFF06131A.toInt(), 0xFF0A1D29.toInt(), 0xFF040B10.toInt()),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
                val hudFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x3800D4FF
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                }
                canvas.drawRoundRect(RectF(28f, 28f, width - 28f, height - 28f), 36f, 36f, hudFramePaint)
                primaryText = 0xFFF0FDFA.toInt()
                secondaryText = 0xFF7DD3FC.toInt()
                accentColor = 0xFF00D4FF.toInt()
                secondaryAccent = 0xFF34D399.toInt()
                mapBgColor = 0xFF071A24.toInt()
                dividerColor = 0x3800D4FF
                pillBgColor = 0x2600D4FF
            }
            ShareCardStyle.TRANSPARENT -> {
                // Keep transparent background so user can layer PNG over photos/Stories
                canvas.drawColor(Color.TRANSPARENT)
                primaryText = Color.WHITE
                secondaryText = 0xFFE2E8F0.toInt()
                accentColor = 0xFFFC4C02.toInt()
                secondaryAccent = 0xFF38BDF8.toInt()
                mapBgColor = Color.TRANSPARENT
                dividerColor = 0x66FFFFFF
                pillBgColor = 0x55000000
            }
        }

        val shadowRadius = if (cardStyle.isTransparent) 12f else 0f
        val shadowColor = if (cardStyle.isTransparent) 0xBB000000.toInt() else Color.TRANSPARENT

        // Top accent bar & brand
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        canvas.drawRoundRect(RectF(64f, 64f, 180f, 74f), 5f, 5f, accentPaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 34f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.14f
            if (cardStyle.isTransparent) setShadowLayer(shadowRadius, 0f, 3f, shadowColor)
        }
        canvas.drawText("VELOSTRIDE", 64f, 128f, brandPaint)

        // Sport pill & optional date
        val sportPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillBgColor }
        canvas.drawRoundRect(RectF(64f, 154f, 340f, 206f), 26f, 26f, sportPillPaint)
        val sportTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(workout.parsedSport.label.uppercase(), 90f, 190f, sportTextPaint)

        if (config.showDateAndDevice) {
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryText
                textSize = 28f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                if (cardStyle.isTransparent) setShadowLayer(shadowRadius, 0f, 2f, shadowColor)
            }
            canvas.drawText(Formatters.formatDateTime(workout.createdAt), 364f, 190f, datePaint)
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryText
            textSize = 54f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            if (cardStyle.isTransparent) setShadowLayer(shadowRadius, 0f, 3f, shadowColor)
        }
        val clippedTitle = if (workout.title.length > 28) workout.title.take(27) + "…" else workout.title
        canvas.drawText(clippedTitle, 64f, 286f, titlePaint)

        // Build selected metrics dynamically
        val metrics = buildList {
            if (config.showDistance) {
                add(ShareMetricSpec("DISTANCE", Formatters.formatDistanceKm(workout.distanceMeters), accentColor))
            }
            if (config.showDuration) {
                add(ShareMetricSpec("DURATION", Formatters.formatDuration(workout.movingTimeSeconds), primaryText))
            }
            if (config.showElevation) {
                add(ShareMetricSpec("ELEV GAIN", "+${workout.elevationGainMeters.toInt()} m", secondaryAccent))
            }
            if (config.showPaceOrSpeed) {
                val paceLabel = if (workout.parsedSport.isPaceBased) "AVG PACE" else "AVG SPEED"
                val paceVal = if (workout.parsedSport.isPaceBased) {
                    Formatters.formatPace(workout.avgPaceSecPerKm)
                } else {
                    Formatters.formatSpeed(workout.avgSpeedKmh)
                }
                add(ShareMetricSpec(paceLabel, paceVal, primaryText))
            }
            if (config.showHeartRate) {
                val hrVal = if (workout.avgHeartRate > 0) "${workout.avgHeartRate} bpm" else "--"
                add(ShareMetricSpec("AVG HR", hrVal, primaryText))
            }
            if (config.showCalories) {
                val calVal = if (workout.calories > 0) "${workout.calories} kcal" else "--"
                add(ShareMetricSpec("CALORIES", calVal, primaryText))
            }
            if (config.showEffort) {
                add(ShareMetricSpec("EFFORT", "${workout.relativeEffort}", primaryText))
            }
        }

        val metricsTopY = if (config.showMap) {
            val mapBottom = if (metrics.size > 6) 760f else 820f
            val mapRect = RectF(64f, 336f, width - 64f, mapBottom)
            drawMapSection(
                canvas = canvas,
                workout = workout,
                mapRect = mapRect,
                cardStyle = cardStyle,
                accentColor = accentColor,
                mapBgColor = mapBgColor,
                mapBackdrop = mapBackdrop,
                progressFraction = progressFraction
            )
            mapBottom + 74f
        } else {
            390f
        }

        // Draw dynamic metrics grid
        if (metrics.isNotEmpty()) {
            val cols = when {
                metrics.size == 1 -> 1
                metrics.size == 2 || metrics.size == 4 -> 2
                else -> 3
            }
            val rowSpacing = if (config.showMap) 144f else 190f
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryText
                textSize = if (config.showMap) 25f else 30f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                letterSpacing = 0.08f
                if (cardStyle.isTransparent) setShadowLayer(shadowRadius, 0f, 2f, shadowColor)
            }
            val valueSize = when {
                !config.showMap && metrics.size <= 4 -> 66f
                cols == 2 -> 58f
                else -> 48f
            }
            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryText
                textSize = valueSize
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                if (cardStyle.isTransparent) setShadowLayer(shadowRadius, 0f, 3f, shadowColor)
            }

            val usableWidth = width - 128f
            val colWidth = usableWidth / cols

            metrics.forEachIndexed { idx, spec ->
                val row = idx / cols
                val col = idx % cols
                val x = 64f + col * colWidth
                val yLabel = metricsTopY + row * rowSpacing
                val yVal = yLabel + (if (config.showMap) 56f else 72f)

                canvas.drawText(spec.label, x, yLabel, labelPaint)
                valuePaint.color = spec.valueColor
                canvas.drawText(spec.value, x, yVal, valuePaint)
            }
        }

        // Footer
        if (config.showDateAndDevice) {
            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dividerColor }
            canvas.drawRect(64f, 1212f, width - 64f, 1214f, dividerPaint)

            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryText
                textSize = 27f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                if (cardStyle.isTransparent) setShadowLayer(shadowRadius, 0f, 2f, shadowColor)
            }
            val devicePart = workout.deviceName?.let { "Recorded on $it" } ?: "Recorded with VeloStride"
            val elePart = if (config.showElevation) {
                workout.elevationSource?.let { " • Elev: $it" }.orEmpty()
            } else {
                ""
            }
            canvas.drawText("$devicePart$elePart", 64f, 1272f, footerPaint)
        }

        return bitmap
    }

    private fun drawMapSection(
        canvas: Canvas,
        workout: WorkoutActivity,
        mapRect: RectF,
        cardStyle: ShareCardStyle,
        accentColor: Int,
        mapBgColor: Int,
        mapBackdrop: Bitmap?,
        progressFraction: Float,
    ) {
        val clipPath = Path().apply {
            addRoundRect(mapRect, 32f, 32f, Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(clipPath)

        if (!cardStyle.isTransparent) {
            val mapBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mapBgColor }
            canvas.drawRect(mapRect, mapBgPaint)

            if (mapBackdrop != null && !mapBackdrop.isRecycled) {
                val src = Rect(0, 0, mapBackdrop.width, mapBackdrop.height)
                val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(mapBackdrop, src, mapRect, imgPaint)
            } else {
                val gridColor = if (cardStyle == ShareCardStyle.SUMMIT_LIGHT) 0x22334155 else 0x2294A3B8
                val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = gridColor
                    strokeWidth = 2f
                }
                for (i in 1..5) {
                    val gx = mapRect.left + mapRect.width() * (i / 6f)
                    canvas.drawLine(gx, mapRect.top, gx, mapRect.bottom, gridPaint)
                }
                for (j in 1..3) {
                    val gy = mapRect.top + mapRect.height() * (j / 4f)
                    canvas.drawLine(mapRect.left, gy, mapRect.right, gy, gridPaint)
                }
            }
        }

        val pts = workout.decodedPoints
        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (cardStyle.isTransparent) 0xAA000000.toInt() else (accentColor and 0x00FFFFFF) or 0x44000000
            style = Paint.Style.STROKE
            strokeWidth = if (cardStyle.isTransparent) 20f else 24f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        if (pts.size >= 2) {
            val mapW = mapRect.width()
            val mapH = mapRect.height()
            val viewport = MapProjection.computeViewportForPoints(
                points = pts,
                widthPx = mapW,
                heightPx = mapH,
                paddingPx = 56f
            )
            val clampedFraction = progressFraction.coerceIn(0.05f, 1f)
            val maxIdx = ((pts.lastIndex) * clampedFraction).roundToInt().coerceIn(1, pts.lastIndex)

            val fullPath = Path()
            val activePath = Path()
            var startX = 0f
            var startY = 0f
            var activeEndX = 0f
            var activeEndY = 0f

            pts.forEachIndexed { idx, p ->
                val (localX, localY) = MapProjection.project(p.lat, p.lng, viewport, mapW, mapH)
                val x = mapRect.left + localX
                val y = mapRect.top + localY
                if (idx == 0) {
                    fullPath.moveTo(x, y)
                    activePath.moveTo(x, y)
                    startX = x
                    startY = y
                } else {
                    fullPath.lineTo(x, y)
                    if (idx <= maxIdx) {
                        activePath.lineTo(x, y)
                    }
                }
                if (idx == maxIdx) {
                    activeEndX = x
                    activeEndY = y
                }
            }

            if (clampedFraction < 0.99f) {
                val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = (accentColor and 0x00FFFFFF) or 0x40000000
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                canvas.drawPath(fullPath, ghostPaint)
            }

            canvas.drawPath(activePath, glowPaint)
            canvas.drawPath(activePath, routePaint)

            val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF22C55E.toInt() }
            val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEF4444.toInt() }
            val whiteCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

            canvas.drawCircle(startX, startY, 13f, startPaint)
            canvas.drawCircle(startX, startY, 5.5f, whiteCenterPaint)
            canvas.drawCircle(activeEndX, activeEndY, 14f, endPaint)
            canvas.drawCircle(activeEndX, activeEndY, 6f, whiteCenterPaint)
        } else {
            val path = Path()
            path.moveTo(mapRect.left + 90f, mapRect.bottom - 120f)
            path.cubicTo(
                mapRect.left + mapRect.width() * 0.35f, mapRect.top + 80f,
                mapRect.left + mapRect.width() * 0.65f, mapRect.bottom - 60f,
                mapRect.right - 90f, mapRect.top + 120f
            )
            canvas.drawPath(path, glowPaint)
            canvas.drawPath(path, routePaint)
        }

        if (!cardStyle.isTransparent && mapBackdrop != null && !mapBackdrop.isRecycled) {
            val attrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (cardStyle.isDarkMap) 0xB3FFFFFF.toInt() else 0xB31E293B.toInt()
                textSize = 18f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            canvas.drawText("© CARTO • OSM", mapRect.right - 160f, mapRect.bottom - 18f, attrPaint)
        }

        canvas.restore()
    }

    fun shareAsImageCard(
        context: Context,
        workout: WorkoutActivity,
        config: ShareCardConfig = ShareCardConfig(),
        mapBackdrop: Bitmap? = null,
        progressFraction: Float = 1f,
    ) {
        val bitmap = renderShareCardBitmap(
            workout = workout,
            config = config,
            mapBackdrop = mapBackdrop,
            progressFraction = progressFraction
        )
        val cacheFolder = File(context.cacheDir, "shared_cards").apply { mkdirs() }
        val safeId = workout.id.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "session" }
        val styleTag = config.style.name.lowercase()
        val outFile = File(cacheFolder, "velostride_${safeId}_$styleTag.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 96, fos)
        }
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, buildShareSummaryText(workout, config))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Activity Card"))
    }
}
