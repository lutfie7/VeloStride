package com.example

import com.example.data.model.SplitKm
import com.example.data.model.TrackPoint
import com.example.data.repository.ElevationRepository
import com.example.hardware.BandSyncHelper
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitElevationTest {

    @Test
    fun `recomputeSplitElevations preserves positive climbs and negative descents`() {
        // ~2 km track: km 1 climbs 100m -> 124m (+24m), km 2 descends 124m -> 95m (-29m)
        val pts = listOf(
            TrackPoint(lat = -6.2000, lng = 106.8000, ele = 100.0, hr = 140),
            TrackPoint(lat = -6.2045, lng = 106.8000, ele = 112.0, hr = 148),
            TrackPoint(lat = -6.2090, lng = 106.8000, ele = 124.0, hr = 155),
            TrackPoint(lat = -6.2135, lng = 106.8000, ele = 110.0, hr = 150),
            TrackPoint(lat = -6.2180, lng = 106.8000, ele = 95.0, hr = 142),
        )
        val initialSplits = listOf(
            SplitKm(km = 1, paceSec = 310, eleDiff = 0.0, avgHr = 148),
            SplitKm(km = 2, paceSec = 290, eleDiff = 0.0, avgHr = 146),
        )

        val updated = ElevationRepository().recomputeSplitElevations(pts, initialSplits)
        assertTrue("Expected positive climb on split 1 but got ${updated[0].eleDiff}", updated[0].eleDiff > 15.0)
        assertTrue("Expected negative descent on split 2 but got ${updated[1].eleDiff}", updated[1].eleDiff < -15.0)
    }

    @Test
    fun `BandSyncHelper buildKmSplitsFromPoints records negative elevation on downhill km`() {
        val pts = listOf(
            TrackPoint(lat = -6.2000, lng = 106.8000, ele = 200.0, hr = 145),
            TrackPoint(lat = -6.2050, lng = 106.8000, ele = 185.0, hr = 142),
            TrackPoint(lat = -6.2100, lng = 106.8000, ele = 168.0, hr = 138),
        )
        val splits = BandSyncHelper.buildKmSplitsFromPoints(
            points = pts,
            totalDistanceMeters = 1120.0,
            totalMovingSec = 300L,
            fallbackAvgHr = 140
        )
        assertTrue(splits.isNotEmpty())
        assertTrue("Expected negative eleDiff for downhill split but got ${splits.first().eleDiff}", splits.first().eleDiff < -20.0)
    }
}
