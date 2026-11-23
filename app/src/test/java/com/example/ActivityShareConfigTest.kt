package com.example

import com.example.core.ActivityShareHelper
import com.example.core.FlyoverVideoExporter
import com.example.core.ShareCardConfig
import com.example.core.ShareCardRatio
import com.example.core.ShareCardStyle
import com.example.data.model.SportType
import com.example.data.model.TrackPoint
import com.example.data.model.WorkoutActivity
import org.junit.Assert.assertEquals
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
            ratio = ShareCardRatio.PORTRAIT_9_16,
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

    @Test
    fun `share card and flyover video sizes preserve 1-1, 16-9, and 9-16 aspect ratios`() {
        val (w1, h1) = FlyoverVideoExporter.computeFlyoverVideoSize(ShareCardRatio.SQUARE_1_1)
        assertEquals(720, w1)
        assertEquals(720, h1)

        val (w16, h16) = FlyoverVideoExporter.computeFlyoverVideoSize(ShareCardRatio.LANDSCAPE_16_9)
        assertEquals(1280, w16)
        assertEquals(720, h16)

        val (w9, h9) = FlyoverVideoExporter.computeFlyoverVideoSize(ShareCardRatio.PORTRAIT_9_16)
        assertEquals(720, w9)
        assertEquals(1280, h9)

        // All video dimensions must be multiples of 16 for H.264 hardware encoders
        ShareCardRatio.entries.forEach { ratio ->
            val (vw, vh) = FlyoverVideoExporter.computeFlyoverVideoSize(ratio)
            assertEquals(0, vw % 16)
            assertEquals(0, vh % 16)

            val (mw, mh) = ActivityShareHelper.computeMapBoxSize(ShareCardConfig(ratio = ratio))
            assertTrue(mw > 200 && mh > 200)
        }
    }

    @Test
    fun `flyover interpolation stays bounded and monotonically tracks distance`() {
        val pts = listOf(
            TrackPoint(lat = -6.200, lng = 106.800, ele = 10.0, hr = 130),
            TrackPoint(lat = -6.205, lng = 106.805, ele = 20.0, hr = 150),
            TrackPoint(lat = -6.210, lng = 106.810, ele = 30.0, hr = 170),
        )
        val dists = FlyoverVideoExporter.computeCumulativeDistances(pts)
        val start = FlyoverVideoExporter.interpolateSample(pts, dists, 0f)
        val mid = FlyoverVideoExporter.interpolateSample(pts, dists, 0.5f)
        val end = FlyoverVideoExporter.interpolateSample(pts, dists, 1f)

        assertEquals(10.0, start.ele, 0.5)
        assertEquals(20.0, mid.ele, 1.5)
        assertEquals(30.0, end.ele, 0.5)
        assertTrue(mid.distanceMeters > start.distanceMeters && mid.distanceMeters < end.distanceMeters)
    }
}
