package com.example.data.repository

import android.util.Log
import com.example.core.Formatters
import com.example.data.model.SplitKm
import com.example.data.model.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

data class EnrichedElevationResult(
    val points: List<TrackPoint>,
    val elevationGainMeters: Double,
    val minElevationMeters: Double,
    val maxElevationMeters: Double,
    val sourceLabel: String,
)

/**
 * Provides real topographic elevation (Copernicus / SRTM 90m Digital Elevation Model)
 * via Open-Meteo Elevation API so devices without a hardware barometric altimeter
 * (such as HUAWEI Band 11 Pro) still get accurate elevation profiles and gain metrics.
 */
class ElevationRepository {

    suspend fun enrichTrackElevation(
        rawPoints: List<TrackPoint>,
        existingGainMeters: Double = 0.0,
    ): EnrichedElevationResult = withContext(Dispatchers.IO) {
        if (rawPoints.isEmpty()) {
            return@withContext EnrichedElevationResult(
                points = emptyList(),
                elevationGainMeters = existingGainMeters,
                minElevationMeters = 0.0,
                maxElevationMeters = 0.0,
                sourceLabel = if (existingGainMeters > 0.0) "Recorded Gain" else "No GPS Track"
            )
        }

        // Check if points already carry valid non-flat elevation variance
        val nonZero = rawPoints.count { kotlin.math.abs(it.ele) > 0.1 }
        val minExisting = rawPoints.minOf { it.ele }
        val maxExisting = rawPoints.maxOf { it.ele }
        val hasRealTrackEle = nonZero >= (rawPoints.size * 0.6) && (maxExisting - minExisting) >= 1.5

        if (hasRealTrackEle) {
            val gain = if (existingGainMeters > 0.0) existingGainMeters else computeElevationGain(rawPoints)
            return@withContext EnrichedElevationResult(
                points = rawPoints,
                elevationGainMeters = (gain * 10.0).roundToInt() / 10.0,
                minElevationMeters = (minExisting * 10.0).roundToInt() / 10.0,
                maxElevationMeters = (maxExisting * 10.0).roundToInt() / 10.0,
                sourceLabel = "Barometer / GPS"
            )
        }

        // Query Open-Meteo Copernicus DEM 90m for sampled points (max 80 per HTTP GET)
        val sampleIndices = sampleIndices(rawPoints.size, 80)
        val sampledCoords = sampleIndices.map { rawPoints[it] }
        val demElevations = fetchOpenMeteoElevations(sampledCoords)

        if (demElevations == null || demElevations.size != sampledCoords.size) {
            val fallbackGain = if (existingGainMeters > 0.0) existingGainMeters else computeElevationGain(rawPoints)
            return@withContext EnrichedElevationResult(
                points = rawPoints,
                elevationGainMeters = (fallbackGain * 10.0).roundToInt() / 10.0,
                minElevationMeters = (minExisting * 10.0).roundToInt() / 10.0,
                maxElevationMeters = (maxExisting * 10.0).roundToInt() / 10.0,
                sourceLabel = "GPS Fallback"
            )
        }

        // Linearly interpolate DEM elevations back onto every trackpoint
        val enriched = MutableList(rawPoints.size) { rawPoints[it] }
        for (k in sampleIndices.indices) {
            val startIdx = sampleIndices[k]
            val startEle = demElevations[k]
            if (k == sampleIndices.lastIndex) {
                enriched[startIdx] = enriched[startIdx].copy(ele = startEle)
            } else {
                val endIdx = sampleIndices[k + 1]
                val endEle = demElevations[k + 1]
                val span = (endIdx - startIdx).coerceAtLeast(1)
                for (i in startIdx..endIdx) {
                    val t = (i - startIdx).toDouble() / span
                    val interpEle = ((startEle + (endEle - startEle) * t) * 10.0).roundToInt() / 10.0
                    enriched[i] = enriched[i].copy(ele = interpEle)
                }
            }
        }

        val gain = computeElevationGain(enriched)
        val minE = enriched.minOf { it.ele }
        val maxE = enriched.maxOf { it.ele }

        EnrichedElevationResult(
            points = enriched,
            elevationGainMeters = (gain * 10.0).roundToInt() / 10.0,
            minElevationMeters = (minE * 10.0).roundToInt() / 10.0,
            maxElevationMeters = (maxE * 10.0).roundToInt() / 10.0,
            sourceLabel = "Copernicus DEM 90m"
        )
    }

    fun recomputeSplitElevations(
        points: List<TrackPoint>,
        existingSplits: List<SplitKm>,
    ): List<SplitKm> {
        if (points.size < 2 || existingSplits.isEmpty()) return existingSplits

        val cumDist = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cumDist[i] = cumDist[i - 1] + Formatters.haversineMeters(
                points[i - 1].lat,
                points[i - 1].lng,
                points[i].lat,
                points[i].lng
            )
        }
        val totalDistM = cumDist.lastOrNull() ?: 0.0
        val useDistanceSlicing = totalDistM >= 200.0
        val chunkSize = (points.size / existingSplits.size.toDouble()).coerceAtLeast(1.0)
        val distStep = totalDistM / existingSplits.size.toDouble()

        return existingSplits.mapIndexed { idx, split ->
            val netDiff = if (useDistanceSlicing) {
                val startDist = idx * distStep
                val endDist = (idx + 1) * distStep
                interpolateEleAtDist(points, cumDist, endDist) -
                    interpolateEleAtDist(points, cumDist, startDist)
            } else {
                val start = (idx * chunkSize).toInt().coerceIn(0, points.lastIndex)
                val end = ((idx + 1) * chunkSize).toInt().coerceIn(start, points.lastIndex)
                val slice = points.subList(start, end + 1)
                if (slice.size >= 2) slice.last().ele - slice.first().ele else split.eleDiff
            }
            split.copy(eleDiff = (netDiff * 10.0).roundToInt() / 10.0)
        }
    }

    private fun interpolateEleAtDist(
        points: List<TrackPoint>,
        cumDist: DoubleArray,
        targetDist: Double,
    ): Double {
        if (targetDist <= cumDist.first()) return points.first().ele
        if (targetDist >= cumDist.last()) return points.last().ele
        for (i in 0 until points.lastIndex) {
            val d0 = cumDist[i]
            val d1 = cumDist[i + 1]
            if (targetDist in d0..d1) {
                val span = d1 - d0
                if (span <= 1e-6) return points[i].ele
                val t = (targetDist - d0) / span
                return points[i].ele + (points[i + 1].ele - points[i].ele) * t
            }
        }
        return points.last().ele
    }

    fun computeElevationGain(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var gain = 0.0
        var anchor = points.first().ele
        for (i in 1 until points.size) {
            val current = points[i].ele
            val diff = current - anchor
            if (diff >= 1.2) {
                gain += diff
                anchor = current
            } else if (current < anchor - 1.2) {
                anchor = current
            }
        }
        return gain
    }

    private fun sampleIndices(totalSize: Int, maxSamples: Int): List<Int> {
        if (totalSize <= maxSamples) return (0 until totalSize).toList()
        val result = LinkedHashSet<Int>()
        val step = (totalSize - 1).toDouble() / (maxSamples - 1)
        for (i in 0 until maxSamples) {
            result.add((i * step).roundToInt().coerceIn(0, totalSize - 1))
        }
        result.add(totalSize - 1)
        return result.toList().sorted()
    }

    private fun fetchOpenMeteoElevations(coords: List<TrackPoint>): List<Double>? {
        if (coords.isEmpty()) return emptyList()
        return try {
            val latList = coords.joinToString(",") { String.format(Locale.US, "%.5f", it.lat) }
            val lngList = coords.joinToString(",") { String.format(Locale.US, "%.5f", it.lng) }
            val url = URL("https://api.open-meteo.com/v1/elevation?latitude=$latList&longitude=$lngList")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "Open-Meteo DEM returned HTTP $code")
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val arr = JSONObject(body).optJSONArray("elevation") ?: return null
            (0 until arr.length()).map { idx -> arr.optDouble(idx, 0.0) }
        } catch (e: Exception) {
            Log.w(TAG, "DEM elevation fetch unavailable: ${e.message}")
            null
        }
    }

    private companion object {
        const val TAG = "ElevationRepo"
    }
}
