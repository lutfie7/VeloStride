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

enum class AppLanguage(val tag: String, val displayName: String) {
    ENGLISH_US("en-US", "English (US)"),
    INDONESIAN("id-ID", "Bahasa Indonesia");

    val locale: Locale
        get() = when (this) {
            ENGLISH_US -> Locale.US
            INDONESIAN -> Locale.forLanguageTag("id-ID")
        }

    companion object {
        fun fromTag(raw: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(raw, ignoreCase = true) } ?: ENGLISH_US
    }
}

enum class UnitSystem(val code: String) {
    METRIC("METRIC"),
    IMPERIAL("IMPERIAL");

    companion object {
        fun fromCode(raw: String?): UnitSystem =
            entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: METRIC
    }
}

object Formatters {
    const val METERS_PER_MILE = 1609.344
    const val KM_PER_MILE = 1.609344
    const val FEET_PER_METER = 3.28084
    const val KG_PER_LB = 0.45359237
    const val CM_PER_INCH = 2.54

    @Volatile
    var unitSystem: UnitSystem = UnitSystem.METRIC

    @Volatile
    var appLanguage: AppLanguage = AppLanguage.ENGLISH_US

    fun distanceUnitLabel(system: UnitSystem = unitSystem): String =
        if (system == UnitSystem.IMPERIAL) "mi" else "km"

    fun elevationUnitLabel(system: UnitSystem = unitSystem): String =
        if (system == UnitSystem.IMPERIAL) "ft" else "m"

    fun speedUnitLabel(system: UnitSystem = unitSystem): String =
        if (system == UnitSystem.IMPERIAL) "mph" else "km/h"

    fun weightUnitLabel(system: UnitSystem = unitSystem): String =
        if (system == UnitSystem.IMPERIAL) "lbs" else "kg"

    fun kgToDisplayWeight(weightKg: Double, system: UnitSystem = unitSystem): Double =
        if (system == UnitSystem.IMPERIAL) weightKg / KG_PER_LB else weightKg

    fun displayWeightToKg(value: Double, system: UnitSystem = unitSystem): Double =
        if (system == UnitSystem.IMPERIAL) value * KG_PER_LB else value

    fun formatWeight(weightKg: Double, system: UnitSystem = unitSystem): String {
        if (weightKg <= 0.0) return "--"
        val display = kgToDisplayWeight(weightKg, system)
        return String.format(Locale.US, "%.1f %s", display, weightUnitLabel(system))
    }

    fun formatHeight(heightCm: Double, system: UnitSystem = unitSystem): String {
        if (heightCm <= 0.0) return "--"
        return if (system == UnitSystem.IMPERIAL) {
            val totalInches = (heightCm / CM_PER_INCH).roundToInt()
            val feet = totalInches / 12
            val inches = totalInches % 12
            "${feet}'${inches}\""
        } else {
            "${heightCm.roundToInt()} cm"
        }
    }

    fun metersToDisplayDistance(meters: Double, system: UnitSystem = unitSystem): Double =
        if (system == UnitSystem.IMPERIAL) meters / METERS_PER_MILE else meters / 1000.0

    fun kmToDisplayDistance(km: Double, system: UnitSystem = unitSystem): Double =
        if (system == UnitSystem.IMPERIAL) km / KM_PER_MILE else km

    fun displayDistanceToKm(value: Double, system: UnitSystem = unitSystem): Double =
        if (system == UnitSystem.IMPERIAL) value * KM_PER_MILE else value

    fun formatDistanceKm(meters: Double, system: UnitSystem = unitSystem): String {
        val value = metersToDisplayDistance(meters, system).coerceAtLeast(0.0)
        val unit = distanceUnitLabel(system)
        return if (value >= 100.0) {
            String.format(Locale.US, "%.0f %s", value, unit)
        } else {
            String.format(Locale.US, "%.2f %s", value, unit)
        }
    }

    fun formatDistanceNumber(meters: Double, system: UnitSystem = unitSystem): String {
        val value = metersToDisplayDistance(meters, system).coerceAtLeast(0.0)
        return String.format(Locale.US, "%.2f", value)
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

    fun formatPace(secPerKm: Int, system: UnitSystem = unitSystem): String {
        val unit = if (system == UnitSystem.IMPERIAL) "/mi" else "/km"
        if (secPerKm <= 0 || secPerKm > 3600) return "--:-- $unit"
        val convertedSec = if (system == UnitSystem.IMPERIAL) {
            (secPerKm * KM_PER_MILE).roundToInt()
        } else {
            secPerKm
        }
        val mins = convertedSec / 60
        val secs = convertedSec % 60
        return String.format(Locale.US, "%d:%02d %s", mins, secs, unit)
    }

    fun formatPaceShort(secPerKm: Int, system: UnitSystem = unitSystem): String {
        if (secPerKm <= 0 || secPerKm > 3600) return "--:--"
        val convertedSec = if (system == UnitSystem.IMPERIAL) {
            (secPerKm * KM_PER_MILE).roundToInt()
        } else {
            secPerKm
        }
        val mins = convertedSec / 60
        val secs = convertedSec % 60
        return String.format(Locale.US, "%d:%02d", mins, secs)
    }

    fun formatSpeed(kmh: Double, system: UnitSystem = unitSystem): String {
        val speed = if (system == UnitSystem.IMPERIAL) kmh / KM_PER_MILE else kmh
        return String.format(Locale.US, "%.1f %s", speed.coerceAtLeast(0.0), speedUnitLabel(system))
    }

    fun formatElevation(meters: Double, system: UnitSystem = unitSystem): String {
        val value = if (system == UnitSystem.IMPERIAL) (meters * FEET_PER_METER).roundToInt() else meters.roundToInt()
        return "$value ${elevationUnitLabel(system)}"
    }

    fun formatElevationSigned(meters: Double, system: UnitSystem = unitSystem): String {
        val value = if (system == UnitSystem.IMPERIAL) (meters * FEET_PER_METER).roundToInt() else meters.roundToInt()
        val unit = elevationUnitLabel(system)
        return when {
            value > 0 -> "+$value$unit"
            value < 0 -> "$value$unit"
            else -> "0$unit"
        }
    }

    fun formatDateTime(timestamp: Timestamp?, language: AppLanguage = appLanguage): String {
        val date = timestamp?.toDate() ?: Date()
        val sdf = SimpleDateFormat("EEE, MMM d • HH:mm", language.locale)
        return sdf.format(date)
    }

    fun formatShortDate(timestamp: Timestamp?, language: AppLanguage = appLanguage): String {
        val date = timestamp?.toDate() ?: Date()
        val sdf = SimpleDateFormat("MMM d", language.locale)
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
