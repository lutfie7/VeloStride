package com.example

import com.example.core.ActivityShareHelper
import com.example.core.ShareCardConfig
import com.example.core.ShareCardStyle
import com.example.data.model.SportType
import com.example.data.model.WorkoutActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityShareConfigTest {

    @Test
    fun `buildShareSummaryText respects ShareCardConfig field toggles`() {
        val workout = WorkoutActivity(
            id = "w_share",
            title = "Senayan Sunset Run",
            sportType = SportType.RUN.name,
            distanceMeters = 10_000.0,
            movingTimeSeconds = 2_700L,
            elevationGainMeters = 65.0,
            avgPaceSecPerKm = 270,
            avgHeartRate = 162,
            maxHeartRate = 180,
            calories = 680,
            relativeEffort = 112,
            deviceName = "HUAWEI Band 11 Pro"
        )

        val minimalConfig = ShareCardConfig(
            style = ShareCardStyle.TRANSPARENT,
            showMap = false,
            showDistance = true,
            showDuration = false,
            showElevation = false,
            showPaceOrSpeed = true,
            showHeartRate = false,
            showCalories = false,
            showEffort = false,
            showDateAndDevice = false
        )

        val summary = ActivityShareHelper.buildShareSummaryText(workout, minimalConfig)
        assertTrue(summary.contains("Distance:"))
        assertTrue(summary.contains("Pace:"))
        assertFalse(summary.contains("Duration:"))
        assertFalse(summary.contains("Elevation Gain:"))
        assertFalse(summary.contains("Heart Rate:"))
        assertFalse(summary.contains("Calories:"))
        assertFalse(summary.contains("Relative Effort:"))
        assertFalse(summary.contains("HUAWEI Band 11 Pro"))
    }
}
