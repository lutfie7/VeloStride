package com.example

import com.example.core.PerformanceEngine
import com.example.data.model.AthleteProfile
import com.example.data.model.DailyHealthLog
import com.example.data.model.HealthConnectSnapshot
import com.example.data.repository.AthleteIntelligenceRepository
import com.example.hardware.BandSyncHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearableSyncAndAiTest {

    @Test
    fun `buildDailyHealthLog merges huawei health connect and ble fallback telemetry`() {
        val snapshot = HealthConnectSnapshot(
            stepsToday = 8420,
            distanceMetersToday = 6150.0,
            activeCaloriesToday = 420,
            totalCaloriesToday = 1950,
            restingHeartRateBpm = 52,
            latestHeartRateBpm = 68,
            hrvRmssdMs = 58.5,
            spo2Percent = 0.0, // simulate missing HC SpO2 -> BLE fallback should win
            vo2MaxFromBand = 51.2,
            sleepTotalMinutes = 435,
            sleepDeepMinutes = 95,
            sleepLightMinutes = 230,
            sleepRemMinutes = 90,
            sleepAwakeMinutes = 20,
            dataOriginPackage = "com.huawei.health"
        )

        val log = BandSyncHelper.buildDailyHealthLog(
            userId = "user1",
            hc = snapshot,
            restingHrFallback = 58,
            vo2MaxFallback = 46.0,
            recoveryScore = 84,
            bleSteps = 120,
            bleCalories = 25,
            bleHr = 132,
            bleHrv = 62.0,
            bleSpo2 = 97.5,
            bleDeviceName = "HUAWEI Band 11 Pro-C37"
        )

        assertEquals(8540, log.steps)
        assertEquals(6150.0, log.distanceMetersToday, 0.01)
        assertEquals(445, log.activeCalories)
        assertEquals(1975, log.totalCalories)
        assertEquals(52, log.restingHr)
        assertEquals(132, log.latestHr)
        assertEquals(62.0, log.hrvRmssdMs, 0.01)
        assertEquals(97.5, log.spo2Percent, 0.01)
        assertEquals(51.2, log.vo2MaxEstimate, 0.01)
        assertEquals("HUAWEI Band 11 Pro-C37 + Huawei Health", log.sourceDevice)
    }

    @Test
    fun `buildWearableHealthSummary exposes steps, sleep stages, spo2, hrv and watch vo2`() {
        val log = DailyHealthLog(
            steps = 9850,
            distanceMetersToday = 7400.0,
            activeCalories = 510,
            totalCalories = 2200,
            restingHr = 50,
            latestHr = 64,
            hrvRmssdMs = 64.2,
            spo2Percent = 98.0,
            sleepTotalMinutes = 450,
            sleepDeepMinutes = 110,
            sleepLightMinutes = 220,
            sleepRemMinutes = 100,
            sleepAwakeMinutes = 20,
            sleepScore = 88,
            vo2MaxEstimate = 52.4,
            sourceDevice = "HUAWEI Band 11 Pro + Huawei Health"
        )

        val analytics = PerformanceEngine.computePerformanceAnalytics(
            workouts = emptyList(),
            profile = AthleteProfile(maxHr = 190, restingHr = 52),
            healthLogs = listOf(log)
        )

        val repo = AthleteIntelligenceRepository()
        val summary = repo.buildWearableHealthSummary(log, analytics)

        assertTrue(summary.contains("Source=HUAWEI Band 11 Pro + Huawei Health"))
        assertTrue(summary.contains("Steps=9850"))
        assertTrue(summary.contains("DailyDist=7.40km"))
        assertTrue(summary.contains("ActiveKcal=510"))
        assertTrue(summary.contains("RestingHR=50bpm"))
        assertTrue(summary.contains("SpO2=98.0%"))
        assertTrue(summary.contains("HRV=64.2ms"))
        assertTrue(summary.contains("Sleep=7h30m (Deep 110m, Light 220m, REM 100m, Awake 20m, Score 88/100)"))
        assertTrue(summary.contains("WatchVO2Max=52.4"))
    }

    @Test
    fun `performance engine reflects watch vo2max, spo2 recovery adjustment and daily wearable fields`() {
        val profile = AthleteProfile(maxHr = 188, restingHr = 56)
        val healthyLog = DailyHealthLog(
            steps = 11200,
            activeCalories = 600,
            restingHr = 49,
            hrvRmssdMs = 68.0,
            spo2Percent = 98.0,
            sleepTotalMinutes = 460,
            sleepScore = 90,
            vo2MaxEstimate = 55.0
        )
        val lowSpo2Log = healthyLog.copy(spo2Percent = 91.5)

        val healthyAnalytics = PerformanceEngine.computePerformanceAnalytics(
            workouts = emptyList(),
            profile = profile,
            healthLogs = listOf(healthyLog)
        )
        val hypoxicAnalytics = PerformanceEngine.computePerformanceAnalytics(
            workouts = emptyList(),
            profile = profile,
            healthLogs = listOf(lowSpo2Log)
        )

        assertEquals(11200, healthyAnalytics.stepsToday)
        assertEquals(49, healthyAnalytics.restingHr)
        assertEquals(600, healthyAnalytics.activeCalories)
        assertEquals(98.0, healthyAnalytics.spo2Percent, 0.01)
        assertTrue(healthyAnalytics.recoveryScore > hypoxicAnalytics.recoveryScore)
        assertTrue(healthyAnalytics.vo2Max >= 50.0)
    }
}
