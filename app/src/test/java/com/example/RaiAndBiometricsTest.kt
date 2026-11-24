package com.example

import com.example.core.Formatters
import com.example.core.PerformanceEngine
import com.example.core.UnitSystem
import com.example.data.model.AthleteProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RaiAndBiometricsTest {

    @Test
    fun `athlete profile computes age, bmi, and Tanaka max HR accurately`() {
        val refCal = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 24) }
        val profile = AthleteProfile(
            displayName = "Andi",
            birthDateIso = "1996-05-10",
            gender = "MALE",
            heightCm = 175.0,
            weightKg = 70.0
        )

        assertEquals(30, profile.ageYears(refCal.timeInMillis))
        assertEquals(22.9, profile.bmi() ?: 0.0, 0.05)
        assertEquals("Optimal", profile.bmiCategory())
        assertEquals(187, profile.estimatedMaxHrByAge())
        assertTrue(profile.hasBiometrics())
    }

    @Test
    fun `empty profile biometrics return nulls cleanly`() {
        val empty = AthleteProfile()
        assertNull(empty.ageYears())
        assertNull(empty.bmi())
        assertNull(empty.estimatedMaxHrByAge())
        assertFalse(empty.hasBiometrics())
    }

    @Test
    fun `computeRaiScore returns bounded 0-100 score and reflects biometrics calibration`() {
        val uncalibrated = PerformanceEngine.computeRaiScore(
            vo2Max = 50.0,
            fitnessCtl = 45.0,
            formTsb = 2.0,
            acwr = 1.05,
            recoveryScore = 82,
            workoutCount28d = 10,
            profile = AthleteProfile()
        )
        val calibrated = PerformanceEngine.computeRaiScore(
            vo2Max = 50.0,
            fitnessCtl = 45.0,
            formTsb = 2.0,
            acwr = 1.05,
            recoveryScore = 82,
            workoutCount28d = 10,
            profile = AthleteProfile(
                birthDateIso = "1992-03-14",
                gender = "MALE",
                heightCm = 172.0,
                weightKg = 64.0,
                ftpWatts = 245
            )
        )

        assertFalse(uncalibrated.calibrated)
        assertTrue(calibrated.calibrated)
        assertTrue(calibrated.score in 15..99)
        assertTrue(calibrated.score >= uncalibrated.score)
        assertNotNull(calibrated.classification)
    }

    @Test
    fun `formatters convert weight and height between metric and imperial`() {
        assertEquals("70.0 kg", Formatters.formatWeight(70.0, UnitSystem.METRIC))
        assertTrue(Formatters.formatWeight(70.0, UnitSystem.IMPERIAL).endsWith("lbs"))
        assertEquals("175 cm", Formatters.formatHeight(175.0, UnitSystem.METRIC))
        assertEquals("5'9\"", Formatters.formatHeight(175.0, UnitSystem.IMPERIAL))
    }
}
