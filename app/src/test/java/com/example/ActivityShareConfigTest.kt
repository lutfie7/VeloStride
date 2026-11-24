package com.example

import com.example.core.ActivityShareHelper
import com.example.core.AppLanguage
import com.example.core.FlyoverDisplayConfig
import com.example.core.FlyoverVideoExporter
import com.example.core.Formatters
import com.example.core.ShareCardConfig
import com.example.core.ShareCardRatio
import com.example.core.ShareCardStyle
import com.example.core.UnitSystem
import com.example.core.appStringsFor
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
            deviceName = "Wearable Device"
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
        assertFalse(summary.contains("Wearable Device"))
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

    @Test
    fun `cinematic 3D camera pose aligns with travel bearing and keeps finite pitch and zoom across ratios`() {
        // Straight eastward track along -6.200 deg latitude
        val eastPts = listOf(
            TrackPoint(lat = -6.200, lng = 106.800, ele = 100.0),
            TrackPoint(lat = -6.200, lng = 106.810, ele = 140.0),
            TrackPoint(lat = -6.200, lng = 106.820, ele = 180.0),
        )
        val dists = FlyoverVideoExporter.computeCumulativeDistances(eastPts)

        ShareCardRatio.entries.forEach { ratio ->
            val midPose = FlyoverVideoExporter.computeCinematicCameraPose(
                points = eastPts,
                cumulativeDistances = dists,
                progress = 0.5f,
                ratio = ratio
            )
            // Moving East -> heading ~ +90 deg, and at progress=0.5 sin(pi)=0 so rotationZ ~ -90 deg
            assertEquals(90f, midPose.headingDeg, 2.5f)
            assertEquals(-90f, midPose.rotationZDeg, 2.5f)
            assertTrue(midPose.pitchDeg in 28f..41f)
            assertTrue(midPose.zoomScale in 1.1f..1.45f)
            assertTrue(midPose.lookAtLng > 106.810)
        }
    }

    @Test
    fun `flyover display config defaults and NaN or empty track pose remain finite and safe`() {
        val defaults = FlyoverDisplayConfig()
        assertTrue(defaults.showDistance)
        assertTrue(defaults.showDuration)
        assertTrue(defaults.showElevation)
        assertTrue(defaults.showPaceOrSpeed)
        assertTrue(defaults.showHeartRate)
        assertTrue(defaults.showElevationProfile)
        assertTrue(defaults.showBrandBadge)

        val custom = defaults.copy(showHeartRate = false, showElevationProfile = false)
        assertFalse(custom.showHeartRate)
        assertFalse(custom.showElevationProfile)
        assertTrue(custom.showDistance)

        val fallbackPose = FlyoverVideoExporter.computeCinematicCameraPose(
            points = emptyList(),
            cumulativeDistances = DoubleArray(0),
            progress = Float.NaN,
            ratio = ShareCardRatio.SQUARE_1_1
        )
        assertTrue(fallbackPose.pitchDeg.isFinite() && fallbackPose.pitchDeg in 28f..41f)
        assertTrue(fallbackPose.zoomScale.isFinite() && fallbackPose.zoomScale in 1.1f..1.45f)
        assertTrue(fallbackPose.rotationZDeg.isFinite())
    }

    @Test
    fun `share card config defaults to 9-16 ratio and supports background media uri`() {
        val defaultConfig = ShareCardConfig()
        assertEquals(ShareCardRatio.PORTRAIT_9_16, defaultConfig.ratio)
        assertEquals(null, defaultConfig.backgroundMediaUri)

        val withMedia = defaultConfig.copy(backgroundMediaUri = "content://media/external/images/media/42")
        assertEquals("content://media/external/images/media/42", withMedia.backgroundMediaUri)
    }

    @Test
    fun `settings language and metric imperial unit conversions format accurately`() {
        assertEquals(AppLanguage.ENGLISH_US, AppLanguage.fromTag("en-US"))
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.fromTag("id-ID"))
        assertEquals(AppLanguage.ENGLISH_US, AppLanguage.fromTag("unknown"))

        assertEquals(UnitSystem.METRIC, UnitSystem.fromCode("METRIC"))
        assertEquals(UnitSystem.IMPERIAL, UnitSystem.fromCode("imperial"))
        assertEquals(UnitSystem.METRIC, UnitSystem.fromCode(null))

        assertEquals("Settings", appStringsFor(AppLanguage.ENGLISH_US).settingsTitle)
        assertEquals("Pengaturan", appStringsFor(AppLanguage.INDONESIAN).settingsTitle)

        // 10 km -> 10.00 km (Metric) vs 6.21 mi (Imperial)
        assertEquals("10.00 km", Formatters.formatDistanceKm(10_000.0, UnitSystem.METRIC))
        assertEquals("6.21 mi", Formatters.formatDistanceKm(10_000.0, UnitSystem.IMPERIAL))

        // 300 sec/km (5:00 /km) -> 8:03 /mi
        assertEquals("5:00 /km", Formatters.formatPace(300, UnitSystem.METRIC))
        assertEquals("8:03 /mi", Formatters.formatPace(300, UnitSystem.IMPERIAL))

        // 16.09344 km/h -> 10.0 mph
        assertEquals("10.0 mph", Formatters.formatSpeed(16.09344, UnitSystem.IMPERIAL))

        // 100 m -> 328 ft
        assertEquals("100 m", Formatters.formatElevation(100.0, UnitSystem.METRIC))
        assertEquals("328 ft", Formatters.formatElevation(100.0, UnitSystem.IMPERIAL))
        assertEquals("-33ft", Formatters.formatElevationSigned(-10.0, UnitSystem.IMPERIAL))
    }
}
