package com.example.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.example.data.model.WorkoutActivity
import java.io.File
import java.io.FileOutputStream

object ActivityShareHelper {

    fun buildShareSummaryText(workout: WorkoutActivity): String {
        val sport = workout.parsedSport
        val paceOrSpeed = if (sport.isPaceBased) {
            "Pace: ${Formatters.formatPace(workout.avgPaceSecPerKm)}"
        } else {
            "Speed: ${Formatters.formatSpeed(workout.avgSpeedKmh)}"
        }
        return buildString {
            appendLine("${workout.title} • ${sport.label}")
            appendLine("Date: ${Formatters.formatDateTime(workout.createdAt)}")
            appendLine("Distance: ${Formatters.formatDistanceKm(workout.distanceMeters)}")
            appendLine("Duration: ${Formatters.formatDuration(workout.movingTimeSeconds)}")
            appendLine("Elevation Gain: ${Formatters.formatElevation(workout.elevationGainMeters)} (${workout.elevationSource ?: "GPS"})")
            appendLine(paceOrSpeed)
            if (workout.avgHeartRate > 0) {
                appendLine("Heart Rate: Avg ${workout.avgHeartRate} bpm / Max ${workout.maxHeartRate} bpm")
            }
            if (workout.calories > 0) {
                appendLine("Calories: ${workout.calories} kcal • Relative Effort: ${workout.relativeEffort}")
            }
            if (!workout.deviceName.isNullOrBlank()) {
                appendLine("Tracked with: ${workout.deviceName}")
            }
            append("Tracked via VeloStride")
        }
    }

    fun shareAsText(context: Context, workout: WorkoutActivity) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${workout.title} — VeloStride")
            putExtra(Intent.EXTRA_TEXT, buildShareSummaryText(workout))
        }
        context.startActivity(Intent.createChooser(intent, "Share Activity"))
    }

    fun renderShareCardBitmap(workout: WorkoutActivity): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background obsidian gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(0xFF0E131B.toInt(), 0xFF151C28.toInt(), 0xFF0A0D12.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle orange accent top bar
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFC4C02.toInt() }
        canvas.drawRoundRect(RectF(64f, 64f, 180f, 74f), 5f, 5f, accentPaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFC4C02.toInt()
            textSize = 34f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.14f
        }
        canvas.drawText("VELOSTRIDE", 64f, 128f, brandPaint)

        val sportPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x29FC4C02 }
        canvas.drawRoundRect(RectF(64f, 154f, 340f, 206f), 26f, 26f, sportPillPaint)
        val sportTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFC4C02.toInt()
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(workout.parsedSport.label.uppercase(), 90f, 190f, sportTextPaint)

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt()
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(Formatters.formatDateTime(workout.createdAt), 364f, 190f, datePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 54f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val clippedTitle = if (workout.title.length > 28) workout.title.take(27) + "…" else workout.title
        canvas.drawText(clippedTitle, 64f, 286f, titlePaint)

        // Route Map Box
        val mapRect = RectF(64f, 336f, width - 64f, 820f)
        val mapBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF101621.toInt() }
        canvas.drawRoundRect(mapRect, 32f, 32f, mapBgPaint)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x2294A3B8
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

        val pts = workout.decodedPoints
        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFC4C02.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FC4C02
            style = Paint.Style.STROKE
            strokeWidth = 24f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val path = Path()
        if (pts.size >= 2) {
            val minLat = pts.minOf { it.lat }
            val maxLat = pts.maxOf { it.lat }
            val minLng = pts.minOf { it.lng }
            val maxLng = pts.maxOf { it.lng }
            val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
            val lngSpan = (maxLng - minLng).coerceAtLeast(0.0005)
            val pad = 56f
            val dw = mapRect.width() - pad * 2
            val dh = mapRect.height() - pad * 2
            var startX = 0f
            var startY = 0f
            var endX = 0f
            var endY = 0f
            pts.forEachIndexed { idx, p ->
                val x = mapRect.left + pad + ((p.lng - minLng) / lngSpan).toFloat() * dw
                val y = mapRect.bottom - pad - ((p.lat - minLat) / latSpan).toFloat() * dh
                if (idx == 0) {
                    path.moveTo(x, y)
                    startX = x
                    startY = y
                } else {
                    path.lineTo(x, y)
                }
                endX = x
                endY = y
            }
            canvas.drawPath(path, glowPaint)
            canvas.drawPath(path, routePaint)
            val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF22C55E.toInt() }
            val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEF4444.toInt() }
            canvas.drawCircle(startX, startY, 13f, startPaint)
            canvas.drawCircle(endX, endY, 13f, endPaint)
        } else {
            path.moveTo(mapRect.left + 90f, mapRect.bottom - 120f)
            path.cubicTo(
                mapRect.left + mapRect.width() * 0.35f, mapRect.top + 80f,
                mapRect.left + mapRect.width() * 0.65f, mapRect.bottom - 60f,
                mapRect.right - 90f, mapRect.top + 120f
            )
            canvas.drawPath(path, glowPaint)
            canvas.drawPath(path, routePaint)
        }

        // Metrics Grid
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF94A3B8.toInt()
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 52f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val orangeValuePaint = Paint(valuePaint).apply { color = 0xFFFC4C02.toInt() }
        val cyanValuePaint = Paint(valuePaint).apply { color = 0xFF00D4FF.toInt() }

        val col1X = 64f
        val col2X = 416f
        val col3X = 752f

        // Row 1: Distance | Duration | Elevation Gain
        canvas.drawText("DISTANCE", col1X, 896f, labelPaint)
        canvas.drawText(Formatters.formatDistanceKm(workout.distanceMeters), col1X, 956f, orangeValuePaint)

        canvas.drawText("DURATION", col2X, 896f, labelPaint)
        canvas.drawText(Formatters.formatDuration(workout.movingTimeSeconds), col2X, 956f, valuePaint)

        canvas.drawText("ELEV GAIN", col3X, 896f, labelPaint)
        canvas.drawText("+${workout.elevationGainMeters.toInt()} m", col3X, 956f, cyanValuePaint)

        // Row 2: Pace/Speed | Avg HR | Calories
        val paceLabel = if (workout.parsedSport.isPaceBased) "AVG PACE" else "AVG SPEED"
        val paceVal = if (workout.parsedSport.isPaceBased) {
            Formatters.formatPace(workout.avgPaceSecPerKm)
        } else {
            Formatters.formatSpeed(workout.avgSpeedKmh)
        }
        canvas.drawText(paceLabel, col1X, 1054f, labelPaint)
        canvas.drawText(paceVal, col1X, 1114f, valuePaint)

        canvas.drawText("AVG HR", col2X, 1054f, labelPaint)
        canvas.drawText(
            if (workout.avgHeartRate > 0) "${workout.avgHeartRate} bpm" else "--",
            col2X,
            1114f,
            valuePaint
        )

        canvas.drawText("EFFORT", col3X, 1054f, labelPaint)
        canvas.drawText("${workout.relativeEffort}", col3X, 1114f, valuePaint)

        // Footer
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF232C3B.toInt() }
        canvas.drawRect(64f, 1196f, width - 64f, 1198f, dividerPaint)

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCBD5E1.toInt()
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val devicePart = workout.deviceName?.let { "Recorded on $it" } ?: "Recorded with VeloStride"
        val elePart = workout.elevationSource?.let { " • Elev: $it" }.orEmpty()
        canvas.drawText("$devicePart$elePart", 64f, 1262f, footerPaint)

        return bitmap
    }

    fun shareAsImageCard(context: Context, workout: WorkoutActivity) {
        val bitmap = renderShareCardBitmap(workout)
        val cacheFolder = File(context.cacheDir, "shared_cards").apply { mkdirs() }
        val safeId = workout.id.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "session" }
        val outFile = File(cacheFolder, "velostride_$safeId.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
        }
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, buildShareSummaryText(workout))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Activity Card"))
    }
}
