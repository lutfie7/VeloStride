package com.example.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.model.SavedRoute
import com.example.data.model.SportType
import com.example.data.model.TrackPoint
import com.example.data.model.WorkoutActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory
import kotlin.math.roundToInt

data class ParsedGpxData(
    val name: String,
    val sportType: SportType,
    val points: List<TrackPoint>,
    val timestampsMs: List<Long>,
    val avgCadence: Int,
    val startTimeMs: Long?,
    val endTimeMs: Long?,
)

object GpxSerializer {

    fun suggestedGpxFileName(rawTitle: String): String {
        val clean = rawTitle.trim()
            .replace(Regex("[^a-zA-Z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "velostride_track" }
            .take(48)
        return if (clean.endsWith(".gpx", ignoreCase = true)) clean else "$clean.gpx"
    }

    fun downsampleTrackPoints(points: List<TrackPoint>, maxCount: Int): List<TrackPoint> {
        if (points.size <= maxCount || maxCount < 2) return points
        val result = ArrayList<TrackPoint>(maxCount)
        val step = (points.size - 1).toDouble() / (maxCount - 1)
        var lastIndex = -1
        for (i in 0 until maxCount) {
            val idx = (i * step).roundToInt().coerceIn(0, points.lastIndex)
            if (idx != lastIndex) {
                result.add(points[idx])
                lastIndex = idx
            }
        }
        if (result.lastOrNull() != points.last()) {
            if (result.size >= maxCount) {
                result[result.lastIndex] = points.last()
            } else {
                result.add(points.last())
            }
        }
        return result
    }

    fun buildWorkoutGpxXml(workout: WorkoutActivity): String {
        val points = workout.decodedPoints
        val startMs = workout.createdAt?.toDate()?.time ?: (System.currentTimeMillis() - workout.movingTimeSeconds * 1000L)
        val durationMs = workout.movingTimeSeconds.coerceAtLeast(1L) * 1000L
        val safeTitle = escapeXml(workout.title.ifBlank { "${workout.parsedSport.label} Activity" })
        val gpxType = mapSportToGpxType(workout.parsedSport)
        val desc = escapeXml(
            "VeloStride • ${Formatters.formatDistanceKm(workout.distanceMeters)} • " +
                "Elev +${workout.elevationGainMeters.roundToInt()}m • ${workout.elevationSource ?: "GPS"}"
        )

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                """<gpx version="1.1" creator="VeloStride" """ +
                    """xmlns="http://www.topografix.com/GPX/1/1" """ +
                    """xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1" """ +
                    """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """ +
                    """xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">"""
            )
            appendLine("  <metadata>")
            appendLine("    <name>$safeTitle</name>")
            appendLine("    <desc>$desc</desc>")
            appendLine("    <time>${formatIsoUtc(startMs)}</time>")
            appendLine("  </metadata>")
            appendLine("  <trk>")
            appendLine("    <name>$safeTitle</name>")
            appendLine("    <type>$gpxType</type>")
            appendLine("    <trkseg>")
            if (points.isNotEmpty()) {
                val denom = (points.size - 1).coerceAtLeast(1)
                points.forEachIndexed { idx, pt ->
                    val ptTimeMs = startMs + ((durationMs * idx) / denom)
                    appendLine(
                        String.format(
                            Locale.US,
                            "      <trkpt lat=\"%.6f\" lon=\"%.6f\">",
                            pt.lat,
                            pt.lng
                        )
                    )
                    appendLine(String.format(Locale.US, "        <ele>%.1f</ele>", pt.ele))
                    appendLine("        <time>${formatIsoUtc(ptTimeMs)}</time>")
                    if (pt.hr > 0) {
                        appendLine("        <extensions>")
                        appendLine("          <gpxtpx:TrackPointExtension>")
                        appendLine("            <gpxtpx:hr>${pt.hr}</gpxtpx:hr>")
                        appendLine("          </gpxtpx:TrackPointExtension>")
                        appendLine("        </extensions>")
                    }
                    appendLine("      </trkpt>")
                }
            }
            appendLine("    </trkseg>")
            appendLine("  </trk>")
            appendLine("</gpx>")
        }
    }

    fun buildRouteGpxXml(route: SavedRoute): String {
        val points = route.decodedWaypoints
        val sport = SportType.fromRaw(route.sportType)
        val safeTitle = escapeXml(route.name.ifBlank { "VeloStride Route" })
        val gpxType = mapSportToGpxType(sport)
        val createdMs = route.createdAt?.toDate()?.time ?: System.currentTimeMillis()
        val desc = escapeXml(
            "${route.surfaceType} • ${Formatters.formatDistanceKm(route.distanceMeters)} • " +
                "Elev +${route.elevationGainMeters.roundToInt()}m"
        )

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                """<gpx version="1.1" creator="VeloStride" """ +
                    """xmlns="http://www.topografix.com/GPX/1/1" """ +
                    """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """ +
                    """xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">"""
            )
            appendLine("  <metadata>")
            appendLine("    <name>$safeTitle</name>")
            appendLine("    <desc>$desc</desc>")
            appendLine("    <time>${formatIsoUtc(createdMs)}</time>")
            appendLine("  </metadata>")
            appendLine("  <rte>")
            appendLine("    <name>$safeTitle</name>")
            appendLine("    <type>$gpxType</type>")
            points.forEachIndexed { idx, pt ->
                appendLine(
                    String.format(
                        Locale.US,
                        "    <rtept lat=\"%.6f\" lon=\"%.6f\">",
                        pt.lat,
                        pt.lng
                    )
                )
                appendLine(String.format(Locale.US, "      <ele>%.1f</ele>", pt.ele))
                appendLine("      <name>WP ${idx + 1}</name>")
                appendLine("    </rtept>")
            }
            appendLine("  </rte>")
            appendLine("  <trk>")
            appendLine("    <name>$safeTitle</name>")
            appendLine("    <type>$gpxType</type>")
            appendLine("    <trkseg>")
            points.forEach { pt ->
                appendLine(
                    String.format(
                        Locale.US,
                        "      <trkpt lat=\"%.6f\" lon=\"%.6f\">",
                        pt.lat,
                        pt.lng
                    )
                )
                appendLine(String.format(Locale.US, "        <ele>%.1f</ele>", pt.ele))
                appendLine("      </trkpt>")
            }
            appendLine("    </trkseg>")
            appendLine("  </trk>")
            appendLine("</gpx>")
        }
    }

    suspend fun exportGpxToUri(context: Context, uri: Uri, gpxXml: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val os = context.contentResolver.openOutputStream(uri)
                    ?: error("Unable to open destination file for GPX export.")
                os.use { stream ->
                    stream.write(gpxXml.toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
            }
        }

    fun shareGpxFile(context: Context, rawTitle: String, gpxXml: String): Result<Unit> =
        runCatching {
            val cacheFolder = File(context.cacheDir, "shared_gpx").apply { mkdirs() }
            val fileName = suggestedGpxFileName(rawTitle)
            val outFile = File(cacheFolder, fileName)
            FileOutputStream(outFile).use { fos ->
                fos.write(gpxXml.toByteArray(Charsets.UTF_8))
                fos.flush()
            }
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share GPX File"))
        }

    suspend fun readGpxFromUri(context: Context, uri: Uri): Result<ParsedGpxData> =
        withContext(Dispatchers.IO) {
            runCatching {
                val displayName = resolveDisplayName(context, uri)
                    ?.removeSuffix(".gpx")
                    ?.removeSuffix(".GPX")
                    ?.ifBlank { null }
                    ?: "Imported GPX Track"
                val stream = context.contentResolver.openInputStream(uri)
                    ?: error("Unable to open selected GPX file.")
                stream.use { input ->
                    parseGpxStream(input, fallbackName = displayName)
                }
            }
        }

    fun parseGpxStream(inputStream: InputStream, fallbackName: String = "Imported GPX Track"): ParsedGpxData {
        val trkPoints = mutableListOf<TrackPoint>()
        val trkTimestamps = mutableListOf<Long>()
        val rtePoints = mutableListOf<TrackPoint>()
        val rteTimestamps = mutableListOf<Long>()
        val wptPoints = mutableListOf<TrackPoint>()
        val wptTimestamps = mutableListOf<Long>()
        val cadenceSamples = mutableListOf<Int>()

        var metadataName: String? = null
        var trkName: String? = null
        var rteName: String? = null
        var rawType: String? = null

        var inMetadata = false
        var inTrk = false
        var inRte = false
        var activePointTag: String? = null
        var curLat: Double? = null
        var curLng: Double? = null
        var curEle = 0.0
        var curHr = 0
        var curTimeMs = 0L
        val textBuffer = StringBuilder()

        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                val tag = (qName ?: localName).orEmpty().substringAfter(':').lowercase(Locale.US)
                textBuffer.setLength(0)
                when (tag) {
                    "metadata" -> inMetadata = true
                    "trk" -> inTrk = true
                    "rte" -> inRte = true
                    "trkpt", "rtept", "wpt" -> {
                        activePointTag = tag
                        curLat = attributes?.getValue("lat")?.toDoubleOrNull()
                        curLng = attributes?.getValue("lon")?.toDoubleOrNull()
                        curEle = 0.0
                        curHr = 0
                        curTimeMs = 0L
                    }
                }
            }

            override fun characters(ch: CharArray?, start: Int, length: Int) {
                if (ch != null && length > 0) {
                    textBuffer.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val tag = (qName ?: localName).orEmpty().substringAfter(':').lowercase(Locale.US)
                val text = textBuffer.toString().trim()
                textBuffer.setLength(0)
                when (tag) {
                    "metadata" -> inMetadata = false
                    "trk" -> inTrk = false
                    "rte" -> inRte = false
                    "name" -> if (text.isNotBlank()) {
                        when {
                            activePointTag == null && inTrk && trkName == null -> trkName = text
                            activePointTag == null && inRte && rteName == null -> rteName = text
                            activePointTag == null && inMetadata && metadataName == null -> metadataName = text
                        }
                    }
                    "type" -> if (activePointTag == null && rawType == null && text.isNotBlank()) {
                        rawType = text
                    }
                    "ele" -> if (activePointTag != null) {
                        curEle = text.toDoubleOrNull() ?: 0.0
                    }
                    "time" -> if (activePointTag != null) {
                        curTimeMs = parseIsoTimestampMs(text) ?: 0L
                    }
                    "hr", "heartrate" -> if (activePointTag != null) {
                        curHr = text.toIntOrNull()?.coerceIn(0, 240) ?: 0
                    }
                    "cad", "cadence" -> if (activePointTag != null) {
                        text.toIntOrNull()?.coerceIn(1, 300)?.let { cadenceSamples.add(it) }
                    }
                    "trkpt", "rtept", "wpt" -> {
                        val lat = curLat
                        val lng = curLng
                        if (lat != null && lng != null &&
                            !lat.isNaN() && !lng.isNaN() &&
                            lat in -90.0..90.0 && lng in -180.0..180.0
                        ) {
                            val pt = TrackPoint(lat = lat, lng = lng, ele = curEle, hr = curHr)
                            when (activePointTag) {
                                "trkpt" -> {
                                    trkPoints.add(pt)
                                    trkTimestamps.add(curTimeMs)
                                }
                                "rtept" -> {
                                    rtePoints.add(pt)
                                    rteTimestamps.add(curTimeMs)
                                }
                                "wpt" -> {
                                    wptPoints.add(pt)
                                    wptTimestamps.add(curTimeMs)
                                }
                            }
                        }
                        activePointTag = null
                        curLat = null
                        curLng = null
                    }
                }
            }
        }

        val saxFactory = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
        saxFactory.newSAXParser().parse(inputStream, handler)

        val (selectedPoints, selectedTimestamps) = when {
            trkPoints.size >= 2 -> trkPoints to trkTimestamps
            rtePoints.size >= 2 -> rtePoints to rteTimestamps
            trkPoints.isNotEmpty() -> trkPoints to trkTimestamps
            rtePoints.isNotEmpty() -> rtePoints to rteTimestamps
            else -> wptPoints to wptTimestamps
        }

        if (selectedPoints.size < 2) {
            throw IllegalArgumentException("File GPX tidak memiliki minimal 2 titik koordinat GPS (<trkpt> / <rtept>).")
        }

        val resolvedName = (trkName ?: rteName ?: metadataName ?: fallbackName).trim().take(100)
            .ifBlank { fallbackName }
        val resolvedSport = mapGpxTypeToSport(rawType, resolvedName)
        val validTimes = selectedTimestamps.filter { it > 0L }
        val avgCad = if (cadenceSamples.isNotEmpty()) cadenceSamples.average().roundToInt() else 0

        return ParsedGpxData(
            name = resolvedName,
            sportType = resolvedSport,
            points = selectedPoints,
            timestampsMs = selectedTimestamps,
            avgCadence = avgCad,
            startTimeMs = validTimes.minOrNull(),
            endTimeMs = validTimes.maxOrNull()
        )
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }

    private fun mapSportToGpxType(sport: SportType): String = when (sport) {
        SportType.RUN -> "running"
        SportType.TRAIL_RUN -> "trail_running"
        SportType.HIKE -> "hiking"
        SportType.WALK -> "walking"
        SportType.CYCLE -> "cycling"
        SportType.GRAVEL_RIDE -> "gravel_cycling"
        SportType.MTB -> "mountain_biking"
        SportType.SWIM -> "swimming"
        SportType.ROWING -> "rowing"
        else -> sport.name.lowercase(Locale.US)
    }

    private fun mapGpxTypeToSport(rawType: String?, titleHint: String): SportType {
        val normalized = (rawType ?: titleHint).lowercase(Locale.US).trim()
        return when {
            normalized.contains("trail") -> SportType.TRAIL_RUN
            normalized.contains("hike") || normalized.contains("hiking") || normalized.contains("trek") -> SportType.HIKE
            normalized.contains("walk") -> SportType.WALK
            normalized.contains("gravel") -> SportType.GRAVEL_RIDE
            normalized.contains("mtb") || normalized.contains("mountain") -> SportType.MTB
            normalized.contains("cycl") || normalized.contains("bike") || normalized.contains("ride") -> SportType.CYCLE
            normalized.contains("swim") -> SportType.SWIM
            normalized.contains("row") -> SportType.ROWING
            normalized.contains("badminton") -> SportType.BADMINTON
            normalized.contains("yoga") -> SportType.YOGA
            normalized.contains("elliptical") -> SportType.ELLIPTICAL
            normalized.contains("workout") || normalized.contains("gym") -> SportType.WORKOUT
            normalized.contains("run") -> SportType.RUN
            else -> SportType.fromRaw(rawType?.uppercase(Locale.US).orEmpty())
        }
    }

    private fun formatIsoUtc(timeMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(timeMs))
    }

    private fun parseIsoTimestampMs(raw: String): Long? {
        if (raw.isBlank()) return null
        val cleaned = raw.trim()
            .replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    if (!pattern.endsWith("Z")) {
                        timeZone = TimeZone.getTimeZone("UTC")
                    } else if (pattern.contains("'Z'")) {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                }
                val parsed = sdf.parse(cleaned)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }

    private fun escapeXml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
