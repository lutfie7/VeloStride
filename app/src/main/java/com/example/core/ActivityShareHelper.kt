package com.example.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
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

enum class ShareCardRatio(
    val label: String,
    val widthPx: Int,
    val heightPx: Int,
    val fileTag: String,
) {
    SQUARE_1_1("1:1", 1080, 1080, "1x1"),
    LANDSCAPE_16_9("16:9", 1600, 900, "16x9"),
    PORTRAIT_9_16("9:16", 1080, 1920, "9x16");

    val aspectFloat: Float get() = widthPx.toFloat() / heightPx.toFloat()
}

data class ShareCardConfig(
    val style: ShareCardStyle = ShareCardStyle.OBSIDIAN,
    val ratio: ShareCardRatio = ShareCardRatio.PORTRAIT_9_16,
    val backgroundMediaUri: String? = null,
    val showMap: Boolean = true,
    val showDistance: Boolean = true,
    val showDuration: Boolean = true,
    val showElevation: Boolean = true,
    val showPaceOrSpeed: Boolean = true,
    val showHeartRate: Boolean = true,
    val showCalories: Boolean = false,
    val showEffort: Boolean = true,
    val showDateAndDevice: Boolean = true,
) {
    fun enabledMetricCount(): Int = listOf(
        showDistance,
        showDuration,
        showElevation,
        showPaceOrSpeed,
        showHeartRate,
        showCalories,
        showEffort
    ).count { it }
}

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

    fun computeMapBoxSize(config: ShareCardConfig): Pair<Int, Int> {
        val (l, t, r, b) = computeMapBounds(config.copy(showMap = true), config.enabledMetricCount())
        return (r - l).roundToInt().coerceAtLeast(64) to (b - t).roundToInt().coerceAtLeast(64)
    }

    private fun computeMapRect(config: ShareCardConfig, metricsCount: Int): RectF {
        val (l, t, r, b) = computeMapBounds(config, metricsCount)
        return RectF(l, t, r, b)
    }

    private fun computeMapBounds(config: ShareCardConfig, metricsCount: Int): FloatArray {
        val w = config.ratio.widthPx.toFloat()
        return when (config.ratio) {
            ShareCardRatio.SQUARE_1_1 -> {
                val bottom = when {
                    metricsCount == 0 -> 960f
                    metricsCount <= 3 -> 788f
                    metricsCount <= 6 -> 678f
                    else -> 604f
                }
                floatArrayOf(48f, 282f, w - 48f, bottom)
            }
            ShareCardRatio.LANDSCAPE_16_9 -> {
                floatArrayOf(730f, 44f, w - 44f, 856f)
            }
            ShareCardRatio.PORTRAIT_9_16 -> {
                val bottom = when {
                    metricsCount == 0 -> 1720f
                    metricsCount <= 3 -> 1480f
                    metricsCount <= 6 -> 1270f
                    else -> 1120f
                }
                floatArrayOf(52f, 360f, w - 52f, bottom)
            }
        }
    }

    /**
     * Fetches OpenStreetMap tiles onto an offscreen Bitmap matching the Share Card map box so both
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
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            if (darkMap) {
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
        for (tile in tiles) {
            val tileBmp = runCatching {
                val conn = (URL(tile.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "VeloStride/1.0 (Android; com.aistudio.velostride.kxqrmz)")
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

    suspend fun loadShareBackgroundBitmap(
        context: Context,
        uriString: String,
        targetWidth: Int = 1080,
        targetHeight: Int = 1920,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (uriString.isBlank()) return@withContext null
        runCatching {
            val uri = Uri.parse(uriString)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val srcW = info.size.width.coerceAtLeast(1)
                    val srcH = info.size.height.coerceAtLeast(1)
                    val ratioW = srcW / targetWidth.coerceAtLeast(1)
                    val ratioH = srcH / targetHeight.coerceAtLeast(1)
                    val sample = minOf(ratioW, ratioH).coerceAtLeast(1)
                    decoder.setTargetSampleSize(sample)
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                var sample = 1
                val w = bounds.outWidth
                val h = bounds.outHeight
                if (w > targetWidth && h > targetHeight) {
                    while ((w / (sample * 2)) >= targetWidth && (h / (sample * 2)) >= targetHeight) {
                        sample *= 2
                    }
                }
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            }
        }.getOrNull()
    }

    private fun computeCenterCropRect(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Rect {
        val srcAspect = srcW.toFloat() / srcH.coerceAtLeast(1).toFloat()
        val dstAspect = dstW.toFloat() / dstH.coerceAtLeast(1).toFloat()
        return if (srcAspect > dstAspect) {
            val cropW = (srcH * dstAspect).roundToInt().coerceIn(1, srcW)
            val left = ((srcW - cropW) / 2).coerceAtLeast(0)
            Rect(left, 0, left + cropW, srcH)
        } else {
            val cropH = (srcW / dstAspect).roundToInt().coerceIn(1, srcH)
            val top = ((srcH - cropH) / 2).coerceAtLeast(0)
            Rect(0, top, srcW, top + cropH)
        }
    }

    fun renderShareCardBitmap(
        workout: WorkoutActivity,
        config: ShareCardConfig = ShareCardConfig(),
        mapBackdrop: Bitmap? = null,
        backgroundBitmap: Bitmap? = null,
        progressFraction: Float = 1f,
    ): Bitmap {
        val width = config.ratio.widthPx
        val height = config.ratio.heightPx
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cardStyle = config.style
        val hasMediaBg = backgroundBitmap != null && !backgroundBitmap.isRecycled
        val primaryText: Int
        val secondaryText: Int
        val accentColor: Int
        val secondaryAccent: Int
        val mapBgColor: Int
        val dividerColor: Int
        val pillBgColor: Int

        if (hasMediaBg && backgroundBitmap != null) {
            val srcRect = computeCenterCropRect(backgroundBitmap.width, backgroundBitmap.height, width, height)
            val dstRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(backgroundBitmap, srcRect, dstRect, photoPaint)

            val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(0x96060A12.toInt(), 0x52060A12, 0xCE060A12.toInt()),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

            primaryText = Color.WHITE
            secondaryText = 0xFFE2E8F0.toInt()
            accentColor = if (cardStyle == ShareCardStyle.NEON_HUD) 0xFF00D4FF.toInt() else 0xFFFC4C02.toInt()
            secondaryAccent = if (cardStyle == ShareCardStyle.SUNSET_PULSE) 0xFFFFD166.toInt() else 0xFF38BDF8.toInt()
            mapBgColor = 0x780A101A
            dividerColor = 0x55FFFFFF
            pillBgColor = 0x70000000
        } else {
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
                canvas.drawRoundRect(RectF(26f, 26f, width - 26f, height - 26f), 34f, 34f, hudFramePaint)
                primaryText = 0xFFF0FDFA.toInt()
                secondaryText = 0xFF7DD3FC.toInt()
                accentColor = 0xFF00D4FF.toInt()
                secondaryAccent = 0xFF34D399.toInt()
                mapBgColor = 0xFF071A24.toInt()
                dividerColor = 0x3800D4FF
                pillBgColor = 0x2600D4FF
            }
            ShareCardStyle.TRANSPARENT -> {
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
        }

        val useTextShadow = cardStyle.isTransparent || hasMediaBg
        val shadowRadius = if (useTextShadow) 12f else 0f
        val shadowColor = if (useTextShadow) 0xBB000000.toInt() else Color.TRANSPARENT

        val metrics = buildList {
            if (config.showDistance) {
                add(ShareMetricSpec("DISTANCE", Formatters.formatDistanceKm(workout.distanceMeters), accentColor))
            }
            if (config.showDuration) {
                add(ShareMetricSpec("DURATION", Formatters.formatDuration(workout.movingTimeSeconds), primaryText))
            }
            if (config.showElevation) {
                add(ShareMetricSpec("ELEV GAIN", "+${Formatters.formatElevation(workout.elevationGainMeters)}", secondaryAccent))
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

        val isLandscapeSplit = config.ratio == ShareCardRatio.LANDSCAPE_16_9 && config.showMap
        val contentLeft = when (config.ratio) {
            ShareCardRatio.SQUARE_1_1 -> 60f
            ShareCardRatio.LANDSCAPE_16_9 -> 56f
            ShareCardRatio.PORTRAIT_9_16 -> 64f
        }
        val contentRight = if (isLandscapeSplit) 700f else width - contentLeft
        val usableWidth = contentRight - contentLeft

        val barTop = when (config.ratio) {
            ShareCardRatio.SQUARE_1_1 -> 46f
            ShareCardRatio.LANDSCAPE_16_9 -> 50f
            ShareCardRatio.PORTRAIT_9_16 -> 84f
        }
        val brandY = barTop + 56f
        val pillTop = brandY + 22f
        val pillBottom = pillTop + 48f
        val titleY = pillBottom + 62f

        // Top accent bar & two-tone VeloStride wordmark matching brand logo
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        canvas.drawRoundRect(RectF(contentLeft, barTop, contentLeft + 112f, barTop + 9f), 5f, 5f, accentPaint)

        val brandSize = if (config.ratio == ShareCardRatio.LANDSCAPE_16_9) 34f else 38f
        val veloColor = if (!hasMediaBg && cardStyle == ShareCardStyle.SUMMIT_LIGHT) 0xFF252A2E.toInt() else 0xFFF8FAFC.toInt()
        val strideColor = if (cardStyle == ShareCardStyle.NEON_HUD) accentColor else 0xFFF26A21.toInt()
        val veloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = veloColor
            textSize = brandSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = -0.04f
            if (useTextShadow) setShadowLayer(shadowRadius, 0f, 3f, shadowColor)
        }
        val stridePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strideColor
            textSize = brandSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = -0.04f
            if (useTextShadow) setShadowLayer(shadowRadius, 0f, 3f, shadowColor)
        }
        canvas.drawText("Velo", contentLeft, brandY, veloPaint)
        val veloWidth = veloPaint.measureText("Velo")
        canvas.drawText("Stride", contentLeft + veloWidth, brandY, stridePaint)

        // Sport pill & optional date
        val sportText = workout.parsedSport.label.uppercase()
        val sportTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = if (isLandscapeSplit) 24f else 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val pillWidth = (sportTextPaint.measureText(sportText) + 52f).coerceAtLeast(180f)
        val sportPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillBgColor }
        canvas.drawRoundRect(RectF(contentLeft, pillTop, contentLeft + pillWidth, pillBottom), 24f, 24f, sportPillPaint)
        canvas.drawText(sportText, contentLeft + 26f, pillTop + 33f, sportTextPaint)

        if (config.showDateAndDevice) {
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryText
                textSize = if (isLandscapeSplit) 22f else 26f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                if (cardStyle.isTransparent) setShadowLayer(shadowRadius, 0f, 2f, shadowColor)
            }
            val dateX = contentLeft + pillWidth + 22f
            val dateStr = ellipsizeToWidth(
                Formatters.formatDateTime(workout.createdAt),
                datePaint,
                (contentRight - dateX).coerceAtLeast(80f)
            )
            canvas.drawText(dateStr, dateX, pillTop + 33f, datePaint)
        }

        // Title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryText
            textSize = when (config.ratio) {
                ShareCardRatio.LANDSCAPE_16_9 -> if (isLandscapeSplit) 42f else 54f
                ShareCardRatio.SQUARE_1_1 -> 48f
                ShareCardRatio.PORTRAIT_9_16 -> 56f
            }
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            if (useTextShadow) setShadowLayer(shadowRadius, 0f, 2f, shadowColor)
        }
        val clippedTitle = ellipsizeToWidth(workout.title, titlePaint, usableWidth)
        canvas.drawText(clippedTitle, contentLeft, titleY, titlePaint)

        // Optional Map Section
        val mapRect = if (config.showMap) {
            computeMapRect(config, metrics.size).also { rect ->
                drawMapSection(
                    canvas = canvas,
                    workout = workout,
                    mapRect = rect,
                    cardStyle = cardStyle,
                    accentColor = accentColor,
                    mapBgColor = mapBgColor,
                    mapBackdrop = mapBackdrop,
                    hasMediaBg = hasMediaBg,
                    progressFraction = progressFraction
                )
            }
        } else {
            null
        }

        // Metrics Grid
        if (metrics.isNotEmpty()) {
            val cols = when {
                isLandscapeSplit -> if (metrics.size == 1) 1 else 2
                config.ratio == ShareCardRatio.LANDSCAPE_16_9 && !config.showMap -> metrics.size.coerceIn(1, 4)
                metrics.size == 1 -> 1
                metrics.size == 2 || metrics.size == 4 -> 2
                else -> 3
            }

            val metricsTopY = when {
                isLandscapeSplit -> titleY + 60f
                mapRect != null -> when (config.ratio) {
                    ShareCardRatio.SQUARE_1_1 -> mapRect.bottom + if (metrics.size > 6) 48f else 56f
                    ShareCardRatio.PORTRAIT_9_16 -> mapRect.bottom + 78f
                    ShareCardRatio.LANDSCAPE_16_9 -> titleY + 60f
                }
                else -> when (config.ratio) {
                    ShareCardRatio.SQUARE_1_1 -> titleY + 96f
                    ShareCardRatio.LANDSCAPE_16_9 -> titleY + 110f
                    ShareCardRatio.PORTRAIT_9_16 -> titleY + 140f
                }
            }

            val rowSpacing = when {
                isLandscapeSplit -> if (metrics.size > 6) 114f else 132f
                config.ratio == ShareCardRatio.SQUARE_1_1 -> when {
                    !config.showMap -> 195f
                    metrics.size > 6 -> 112f
                    else -> 132f
                }
                config.ratio == ShareCardRatio.LANDSCAPE_16_9 -> 200f
                else -> when {
                    !config.showMap -> 240f
                    metrics.size > 6 -> 176f
                    else -> 192f
                }
            }

            val labelSize = when {
                isLandscapeSplit -> 22f
                config.ratio == ShareCardRatio.SQUARE_1_1 -> if (config.showMap) 23f else 28f
                config.ratio == ShareCardRatio.LANDSCAPE_16_9 -> 28f
                else -> if (config.showMap) 26f else 30f
            }
            val valueSize = when {
                isLandscapeSplit -> if (metrics.size <= 4) 48f else 42f
                !config.showMap -> if (metrics.size <= 4) 68f else 58f
                config.ratio == ShareCardRatio.SQUARE_1_1 -> if (cols == 2) 52f else 44f
                else -> if (cols == 2) 60f else 50f
            }
            val valueOffset = when {
                isLandscapeSplit -> 48f
                !config.showMap -> 72f
                config.ratio == ShareCardRatio.SQUARE_1_1 -> 50f
                else -> 58f
            }

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryText
                textSize = labelSize
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                letterSpacing = 0.08f
                if (useTextShadow) setShadowLayer(shadowRadius, 0f, 2f, shadowColor)
            }
            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryText
                textSize = valueSize
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                if (useTextShadow) setShadowLayer(shadowRadius, 0f, 3f, shadowColor)
            }

            val colWidth = usableWidth / cols
            metrics.forEachIndexed { idx, spec ->
                val row = idx / cols
                val col = idx % cols
                val x = contentLeft + col * colWidth
                val yLabel = metricsTopY + row * rowSpacing
                val yVal = yLabel + valueOffset

                canvas.drawText(spec.label, x, yLabel, labelPaint)
                valuePaint.color = spec.valueColor
                val fitVal = ellipsizeToWidth(spec.value, valuePaint, colWidth - 16f)
                canvas.drawText(fitVal, x, yVal, valuePaint)
            }
        }

        // Footer
        if (config.showDateAndDevice) {
            val dividerY = height - when (config.ratio) {
                ShareCardRatio.SQUARE_1_1 -> 104f
                ShareCardRatio.LANDSCAPE_16_9 -> 106f
                ShareCardRatio.PORTRAIT_9_16 -> 148f
            }
            val footerTextY = dividerY + 54f
            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dividerColor }
            canvas.drawRect(contentLeft, dividerY, contentRight, dividerY + 2f, dividerPaint)

            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryText
                textSize = if (isLandscapeSplit) 22f else 26f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                if (useTextShadow) setShadowLayer(shadowRadius, 0f, 2f, shadowColor)
            }
            val devicePart = workout.deviceName?.let { "Recorded on $it" } ?: "Recorded with VeloStride"
            val elePart = if (config.showElevation) {
                workout.elevationSource?.let { " • Elev: $it" }.orEmpty()
            } else {
                ""
            }
            val footerStr = ellipsizeToWidth("$devicePart$elePart", footerPaint, usableWidth)
            canvas.drawText(footerStr, contentLeft, footerTextY, footerPaint)
        }

        return bitmap
    }

    private fun ellipsizeToWidth(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val avail = (maxWidth - paint.measureText(ellipsis)).coerceAtLeast(10f)
        val count = paint.breakText(text, true, avail, null)
        return text.take(count.coerceAtLeast(1)).trimEnd() + ellipsis
    }

    private fun drawMapSection(
        canvas: Canvas,
        workout: WorkoutActivity,
        mapRect: RectF,
        cardStyle: ShareCardStyle,
        accentColor: Int,
        mapBgColor: Int,
        mapBackdrop: Bitmap?,
        hasMediaBg: Boolean = false,
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

            if (!hasMediaBg && mapBackdrop != null && !mapBackdrop.isRecycled) {
                val src = Rect(0, 0, mapBackdrop.width, mapBackdrop.height)
                val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(mapBackdrop, src, mapRect, imgPaint)
            } else {
                val gridColor = when {
                    hasMediaBg -> 0x26FFFFFF
                    cardStyle == ShareCardStyle.SUMMIT_LIGHT -> 0x22334155
                    else -> 0x2294A3B8
                }
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

        if (!hasMediaBg && !cardStyle.isTransparent && mapBackdrop != null && !mapBackdrop.isRecycled) {
            val attrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (cardStyle.isDarkMap) 0xB3FFFFFF.toInt() else 0xB31E293B.toInt()
                textSize = 18f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            canvas.drawText("© OpenStreetMap", mapRect.right - 165f, mapRect.bottom - 18f, attrPaint)
        }

        canvas.restore()

        if (hasMediaBg && !cardStyle.isTransparent) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x4DFFFFFF
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRoundRect(mapRect, 32f, 32f, borderPaint)
        }
    }

    fun shareAsImageCard(
        context: Context,
        workout: WorkoutActivity,
        config: ShareCardConfig = ShareCardConfig(),
        mapBackdrop: Bitmap? = null,
        backgroundBitmap: Bitmap? = null,
        progressFraction: Float = 1f,
    ) {
        val bitmap = renderShareCardBitmap(
            workout = workout,
            config = config,
            mapBackdrop = mapBackdrop,
            backgroundBitmap = backgroundBitmap,
            progressFraction = progressFraction
        )
        val cacheFolder = File(context.cacheDir, "shared_cards").apply { mkdirs() }
        val safeId = workout.id.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "session" }
        val styleTag = config.style.name.lowercase()
        val outFile = File(cacheFolder, "velostride_${safeId}_${styleTag}_${config.ratio.fileTag}.png")
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
