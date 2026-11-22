package com.example.core

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object Formatters {
    fun formatDistanceKm(meters: Double): String {
        val km = meters / 1000.0
        return if (km >= 100.0) {
            String.format(Locale.US, "%.0f km", km)
        } else {
            String.format(Locale.US, "%.2f km", km)
        }
    }

    fun formatDistanceNumber(meters: Double): String {
        val km = meters / 1000.0
        return String.format(Locale.US, "%.2f", km)
    }

    fun formatDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val hrs = safe / 3600
        val mins = (safe % 3600) / 60
        val secs = safe % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun formatDurationCompact(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val hrs = safe / 3600
        val mins = (safe % 3600) / 60
        return when {
            hrs > 0 -> "${hrs}h ${mins}m"
            mins > 0 -> "${mins}m"
            else -> "${safe}s"
        }
    }

    fun formatPace(secPerKm: Int): String {
        if (secPerKm <= 0 || secPerKm > 3600) return "--:-- /km"
        val mins = secPerKm / 60
        val secs = secPerKm % 60
        return String.format(Locale.US, "%d:%02d /km", mins, secs)
    }

    fun formatPaceShort(secPerKm: Int): String {
        if (secPerKm <= 0 || secPerKm > 3600) return "--:--"
        val mins = secPerKm / 60
        val secs = secPerKm % 60
        return String.format(Locale.US, "%d:%02d", mins, secs)
    }

    fun formatSpeed(kmh: Double): String {
        return String.format(Locale.US, "%.1f km/h", kmh.coerceAtLeast(0.0))
    }

    fun formatElevation(meters: Double): String {
        return "${meters.roundToInt()} m"
    }

    fun formatDateTime(timestamp: Timestamp?): String {
        val date = timestamp?.toDate() ?: Date()
        val sdf = SimpleDateFormat("EEE, MMM d • HH:mm", Locale.getDefault())
        return sdf.format(date)
    }

    fun formatShortDate(timestamp: Timestamp?): String {
        val date = timestamp?.toDate() ?: Date()
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        return sdf.format(date)
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun computeHrZone(bpm: Int, maxHr: Int = 192): Int {
        if (bpm <= 0) return 0
        val pct = bpm.toDouble() / maxHr.coerceAtLeast(120)
        return when {
            pct < 0.60 -> 1
            pct < 0.70 -> 2
            pct < 0.80 -> 3
            pct < 0.90 -> 4
            else -> 5
        }
    }

    fun hrZoneLabel(zone: Int): String = when (zone) {
        1 -> "Z1 Recovery"
        2 -> "Z2 Endurance"
        3 -> "Z3 Tempo"
        4 -> "Z4 Threshold"
        5 -> "Z5 Peak"
        else -> "No HR"
    }
}
