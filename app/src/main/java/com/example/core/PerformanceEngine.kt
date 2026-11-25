package com.example.core

import com.example.data.model.AthleteProfile
import com.example.data.model.DailyHealthLog
import com.example.data.model.FitnessLoadPoint
import com.example.data.model.PerformanceAnalytics
import com.example.data.model.PersonalRecordItem
import com.example.data.model.SportType
import com.example.data.model.WorkoutActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

object PerformanceEngine {

    /**
     * Computes VO2 Max using two validated athletic physiology methods:
     * 1. Running velocity/VDOT estimation (Daniels-Gilbert oxygen cost equation) when running splits/pace exist.
     * 2. Uth-Sørensen-Overgaard-Pedersen HR-ratio model: 15.3 * (HRmax / HRrest) blended with training intensity.
     */
    fun estimateVo2Max(
        workouts: List<WorkoutActivity>,
        profile: AthleteProfile,
        latestHealthLog: DailyHealthLog?,
    ): Double {
        val restingHr = (latestHealthLog?.restingHr?.takeIf { it in 35..110 } ?: profile.restingHr).coerceIn(35, 110)
        val maxHr = profile.maxHr.coerceIn(130, 225)
        val hrRatioVo2 = 15.3 * (maxHr.toDouble() / restingHr.toDouble())

        val recentRuns = workouts.filter {
            (it.parsedSport == SportType.RUN || it.parsedSport == SportType.TRAIL_RUN) &&
                it.distanceMeters >= 2000.0 &&
                it.movingTimeSeconds >= 480L
        }.take(6)

        if (recentRuns.isEmpty()) {
            val watchVo2 = latestHealthLog?.vo2MaxEstimate?.takeIf { it in 25.0..85.0 }
            val est = if (watchVo2 != null) (watchVo2 * 0.65 + hrRatioVo2 * 0.35) else hrRatioVo2
            return (est.coerceIn(28.0, 82.0) * 10.0).roundToInt() / 10.0
        }

        val runVo2Estimates = recentRuns.map { run ->
            val timeMinutes = run.movingTimeSeconds / 60.0
            val velocityMetersPerMin = run.distanceMeters / timeMinutes
            // Daniels-Gilbert VO2 at velocity: -4.60 + 0.182258 * v + 0.000104 * v^2
            val vo2Cost = -4.60 + 0.182258 * velocityMetersPerMin + 0.000104 * velocityMetersPerMin.pow(2)
            // Fraction of VO2max sustained based on duration
            val pctMax = (0.8 + 0.1894393 * exp(-0.012778 * timeMinutes) + 0.2989558 * exp(-0.1932605 * timeMinutes))
                .coerceIn(0.65, 1.02)
            val rawVdot = (vo2Cost / pctMax).coerceIn(28.0, 82.0)
            // Adjust slightly if HR efficiency is known
            if (run.avgHeartRate in 100..215) {
                val hrReservePct = ((run.avgHeartRate - restingHr).toDouble() / (maxHr - restingHr).coerceAtLeast(30)).coerceIn(0.45, 1.0)
                val efficiencyVo2 = (vo2Cost / hrReservePct).coerceIn(28.0, 82.0)
                (rawVdot * 0.65 + efficiencyVo2 * 0.35)
            } else {
                rawVdot
            }
        }

        val avgRunVo2 = runVo2Estimates.average()
        val watchVo2 = latestHealthLog?.vo2MaxEstimate?.takeIf { it in 25.0..85.0 }
        val blended = if (watchVo2 != null) {
            avgRunVo2 * 0.60 + watchVo2 * 0.25 + hrRatioVo2 * 0.15
        } else {
            avgRunVo2 * 0.78 + hrRatioVo2 * 0.22
        }
        return (blended.coerceIn(28.0, 82.0) * 10.0).roundToInt() / 10.0
    }

    fun classifyVo2Max(vo2Max: Double): String = when {
        vo2Max >= 56.0 -> "Superior"
        vo2Max >= 49.0 -> "Excellent"
        vo2Max >= 42.0 -> "Good"
        vo2Max >= 35.0 -> "Fair"
        else -> "Building"
    }

    /**
     * Calculates Banister TRIMP / Relative Effort for a workout using HR zones or RPE.
     */
    fun computeRelativeEffort(
        movingSeconds: Long,
        avgHr: Int,
        maxHr: Int,
        restingHr: Int,
        perceivedExertion: Int,
        hrZoneSeconds: List<Long>,
    ): Int {
        val zoneWeights = listOf(0.8, 1.4, 2.4, 4.0, 6.2)
        val zoneSum = hrZoneSeconds.take(5).mapIndexed { idx, secs ->
            (secs / 60.0) * zoneWeights[idx]
        }.sum()
        if (zoneSum > 1.0) {
            return zoneSum.roundToInt().coerceIn(5, 800)
        }
        val mins = (movingSeconds / 60.0).coerceAtLeast(1.0)
        if (avgHr in 70..220 && maxHr > restingHr) {
            val hrr = ((avgHr - restingHr).toDouble() / (maxHr - restingHr)).coerceIn(0.2, 1.05)
            val trimp = mins * hrr * 0.64 * exp(1.92 * hrr)
            return trimp.roundToInt().coerceIn(5, 800)
        }
        return (mins * (perceivedExertion * 0.32)).roundToInt().coerceIn(5, 600)
    }

    /**
     * Full performance analysis combining Fitness (CTL - 42d EWMA), Fatigue (ATL - 7d EWMA),
     * Form (TSB = CTL - ATL), ACWR injury-risk ratio, VO2 Max, and Recovery Readiness.
     */
    fun computePerformanceAnalytics(
        workouts: List<WorkoutActivity>,
        profile: AthleteProfile,
        healthLogs: List<DailyHealthLog>,
        liveHrvMs: Double = 0.0,
    ): PerformanceAnalytics {
        val nowMs = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val latestHealth = healthLogs.firstOrNull()
        val baseVo2 = estimateVo2Max(workouts, profile, latestHealth)

        // Build 14-day daily training load buckets (oldest -> today)
        val cal = Calendar.getInstance()
        val dayLabelFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dailyLoads = MutableList(28) { 0 }
        workouts.forEach { workout ->
            val createdMs = workout.createdAt?.toDate()?.time ?: nowMs
            val daysAgo = ((nowMs - createdMs) / dayMs).toInt()
            if (daysAgo in 0 until 28) {
                val idx = 27 - daysAgo
                dailyLoads[idx] += workout.relativeEffort.coerceAtLeast(10)
            }
        }

        var ctl = 24.0
        var atl = 24.0
        val ctlDecay = exp(-1.0 / 42.0)
        val atlDecay = exp(-1.0 / 7.0)
        val fullSeries = mutableListOf<FitnessLoadPoint>()

        for (i in 0 until 28) {
            val load = dailyLoads[i].toDouble()
            ctl = ctl * ctlDecay + load * (1.0 - ctlDecay) * 1.4
            atl = atl * atlDecay + load * (1.0 - atlDecay) * 1.6
            val form = ctl - atl
            cal.timeInMillis = nowMs - ((27 - i) * dayMs)
            val vo2Day = (baseVo2 - (27 - i) * 0.03 + if (load > 0) 0.08 else 0.0)
                .coerceIn(28.0, 82.0)
            fullSeries += FitnessLoadPoint(
                dayLabel = dayLabelFormat.format(cal.time),
                fitnessCtl = (ctl * 10.0).roundToInt() / 10.0,
                fatigueAtl = (atl * 10.0).roundToInt() / 10.0,
                formTsb = (form * 10.0).roundToInt() / 10.0,
                relativeEffort = dailyLoads[i],
                vo2Max = (vo2Day * 10.0).roundToInt() / 10.0
            )
        }

        val last14 = fullSeries.takeLast(14)
        val currentPoint = fullSeries.lastOrNull() ?: FitnessLoadPoint("Today", 24.0, 24.0, 0.0, 0, baseVo2)
        val weeklyEffort = dailyLoads.takeLast(7).sum()
        val acwr = if (currentPoint.fitnessCtl > 5.0) {
            (currentPoint.fatigueAtl / currentPoint.fitnessCtl).let { (it * 100.0).roundToInt() / 100.0 }
        } else {
            1.0
        }

        val loadStatus = when {
            acwr > 1.45 -> "Overreaching"
            acwr >= 1.05 -> "Productive Peak"
            acwr >= 0.8 -> "Optimal Balance"
            else -> "Detraining / Fresh"
        }

        val effectiveHrv = when {
            liveHrvMs > 5.0 -> liveHrvMs
            latestHealth != null && latestHealth.hrvRmssdMs > 5.0 -> latestHealth.hrvRmssdMs
            else -> 54.0
        }
        val sleepMins = latestHealth?.sleepTotalMinutes ?: 420
        val sleepScore = latestHealth?.sleepScore ?: ((sleepMins / 480.0) * 90.0).roundToInt().coerceIn(45, 98)

        val effectiveSpo2 = latestHealth?.spo2Percent?.takeIf { it in 70.0..100.0 } ?: 0.0
        val spo2Adjustment = when {
            effectiveSpo2 in 70.0..93.9 -> -8.0
            effectiveSpo2 >= 96.0 -> 2.0
            else -> 0.0
        }

        // Recovery Score (0..100): Sleep (35%) + HRV (30%) + TSB Form (35%) + SpO2 modifier
        val sleepComponent = ((sleepScore / 100.0) * 35.0).coerceIn(10.0, 35.0)
        val hrvComponent = (((effectiveHrv - 25.0) / 65.0) * 30.0).coerceIn(8.0, 30.0)
        val tsbComponent = (((currentPoint.formTsb + 25.0) / 45.0) * 35.0).coerceIn(5.0, 35.0)
        val recoveryScore = (sleepComponent + hrvComponent + tsbComponent + spo2Adjustment).roundToInt().coerceIn(12, 99)

        val recoveryStatus = when {
            recoveryScore >= 75 -> "Primed for Intensity"
            recoveryScore >= 50 -> "Steady Aerobic Ready"
            else -> "Active Recovery Advised"
        }
        val recoveryHours = when {
            recoveryScore >= 85 -> 0
            recoveryScore >= 70 -> 8
            recoveryScore >= 50 -> 18
            else -> 32
        }

        val vo2Delta = if (last14.size >= 2) {
            ((last14.last().vo2Max - last14.first().vo2Max) * 10.0).roundToInt() / 10.0
        } else {
            0.0
        }

        val raiResult = computeRaiScore(
            vo2Max = baseVo2,
            fitnessCtl = currentPoint.fitnessCtl,
            formTsb = currentPoint.formTsb,
            acwr = acwr,
            recoveryScore = recoveryScore,
            workoutCount28d = dailyLoads.count { it > 0 },
            profile = profile
        )

        val prs = buildPersonalRecords(workouts)

        return PerformanceAnalytics(
            vo2Max = baseVo2,
            vo2MaxTrendDelta = vo2Delta,
            vo2MaxClassification = classifyVo2Max(baseVo2),
            raiScore = raiResult.score,
            raiClassification = raiResult.classification,
            raiAerobicComponent = raiResult.aerobicComponent,
            raiLoadComponent = raiResult.loadComponent,
            raiRecoveryComponent = raiResult.recoveryComponent,
            raiProfileCalibrated = raiResult.calibrated,
            fitnessCtl = currentPoint.fitnessCtl,
            fatigueAtl = currentPoint.fatigueAtl,
            formTsb = currentPoint.formTsb,
            weeklyRelativeEffort = weeklyEffort,
            acuteChronicRatio = acwr,
            loadStatusLabel = loadStatus,
            recoveryScore = recoveryScore,
            recoveryStatusLabel = recoveryStatus,
            recommendedRecoveryHours = recoveryHours,
            hrvRmssdMs = (effectiveHrv * 10.0).roundToInt() / 10.0,
            sleepMinutes = sleepMins,
            sleepScore = sleepScore,
            spo2Percent = effectiveSpo2,
            stepsToday = latestHealth?.steps ?: 0,
            restingHr = latestHealth?.restingHr?.takeIf { it > 0 } ?: profile.restingHr,
            activeCalories = latestHealth?.activeCalories ?: 0,
            trainingSeries = last14,
            personalRecords = prs
        )
    }

    data class RaiBreakdown(
        val score: Int,
        val classification: String,
        val aerobicComponent: Int,
        val loadComponent: Int,
        val recoveryComponent: Int,
        val calibrated: Boolean,
    )

    fun computeRaiScore(
        vo2Max: Double,
        fitnessCtl: Double,
        formTsb: Double,
        acwr: Double,
        recoveryScore: Int,
        workoutCount28d: Int,
        profile: AthleteProfile,
    ): RaiBreakdown {
        val age = profile.ageYears()
        val genderOffset = when (profile.gender.uppercase()) {
            "FEMALE" -> -4.0
            "MALE" -> 1.0
            else -> 0.0
        }
        val ageOffset = if (age != null && age > 25) ((age - 25) * 0.22).coerceAtMost(9.0) else 0.0
        val vo2Benchmark = (46.0 + genderOffset - ageOffset).coerceIn(32.0, 52.0)

        var aerobic = (((vo2Max / vo2Benchmark) * 74.0)).roundToInt().coerceIn(20, 96)
        val bmi = profile.bmi()
        if (bmi != null) {
            aerobic = when {
                bmi in 18.5..24.5 -> (aerobic + 4).coerceAtMost(99)
                bmi > 29.0 -> (aerobic - 3).coerceAtLeast(18)
                else -> aerobic
            }
        }
        if (profile.weightKg in 35.0..180.0 && profile.ftpWatts > 50) {
            val wKg = profile.ftpWatts / profile.weightKg
            if (wKg >= 3.2) {
                aerobic = (aerobic + 3).coerceAtMost(99)
            }
        }

        val ctlBase = (((fitnessCtl - 15.0) / 65.0) * 65.0 + 30.0).coerceIn(24.0, 92.0)
        val consistencyBonus = (workoutCount28d.coerceAtMost(16) * 0.5)
        val acwrModifier = when {
            acwr in 0.80..1.30 -> 6.0
            acwr in 1.31..1.45 -> 0.0
            acwr > 1.45 -> -10.0
            else -> -3.0
        }
        val load = (ctlBase + consistencyBonus + acwrModifier).roundToInt().coerceIn(18, 99)

        val formMod = if (formTsb in -10.0..12.0) 4 else if (formTsb < -22.0) -6 else 0
        val recovery = (recoveryScore + formMod).coerceIn(15, 99)

        val composite = (aerobic * 0.45 + load * 0.30 + recovery * 0.25).roundToInt().coerceIn(15, 99)
        val classification = when {
            composite >= 85 -> "Elite"
            composite >= 72 -> "Strong"
            composite >= 58 -> "Steady"
            composite >= 42 -> "Developing"
            else -> "Building"
        }
        return RaiBreakdown(
            score = composite,
            classification = classification,
            aerobicComponent = aerobic,
            loadComponent = load,
            recoveryComponent = recovery,
            calibrated = profile.hasBiometrics()
        )
    }

    private fun buildPersonalRecords(workouts: List<WorkoutActivity>): List<PersonalRecordItem> {
        val runs = workouts.filter { it.parsedSport == SportType.RUN || it.parsedSport == SportType.TRAIL_RUN }
        val rides = workouts.filter {
            it.parsedSport == SportType.CYCLE || it.parsedSport == SportType.GRAVEL_RIDE || it.parsedSport == SportType.MTB
        }
        val items = mutableListOf<PersonalRecordItem>()

        runs.filter { it.distanceMeters >= 5000 && it.avgPaceSecPerKm > 0 }
            .minByOrNull { it.avgPaceSecPerKm }?.let { best ->
                items += PersonalRecordItem(
                    category = "Fastest 5K Est.",
                    valueLabel = Formatters.formatDuration((best.avgPaceSecPerKm * 5).toLong()),
                    dateLabel = Formatters.formatShortDate(best.createdAt),
                    workoutTitle = best.title
                )
            }
        runs.filter { it.distanceMeters >= 10000 && it.avgPaceSecPerKm > 0 }
            .minByOrNull { it.avgPaceSecPerKm }?.let { best ->
                items += PersonalRecordItem(
                    category = "Fastest 10K Est.",
                    valueLabel = Formatters.formatDuration((best.avgPaceSecPerKm * 10).toLong()),
                    dateLabel = Formatters.formatShortDate(best.createdAt),
                    workoutTitle = best.title
                )
            }
        workouts.maxByOrNull { it.distanceMeters }?.takeIf { it.distanceMeters > 0 }?.let { longest ->
            items += PersonalRecordItem(
                category = "Longest Distance",
                valueLabel = Formatters.formatDistanceKm(longest.distanceMeters),
                dateLabel = Formatters.formatShortDate(longest.createdAt),
                workoutTitle = longest.title
            )
        }
        workouts.maxByOrNull { it.elevationGainMeters }?.takeIf { it.elevationGainMeters > 0 }?.let { climb ->
            items += PersonalRecordItem(
                category = "Biggest Climb",
                valueLabel = Formatters.formatElevation(climb.elevationGainMeters),
                dateLabel = Formatters.formatShortDate(climb.createdAt),
                workoutTitle = climb.title
            )
        }
        rides.maxByOrNull { it.avgSpeedKmh }?.takeIf { it.avgSpeedKmh > 0 }?.let { fastRide ->
            items += PersonalRecordItem(
                category = "Fastest Ride Avg",
                valueLabel = Formatters.formatSpeed(fastRide.avgSpeedKmh),
                dateLabel = Formatters.formatShortDate(fastRide.createdAt),
                workoutTitle = fastRide.title
            )
        }
        return items
    }
}
