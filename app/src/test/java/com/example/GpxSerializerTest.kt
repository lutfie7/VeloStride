package com.example

import com.example.core.GpxSerializer
import com.example.data.model.SavedRoute
import com.example.data.model.SportType
import com.example.data.model.TrackPoint
import com.example.data.model.WorkoutActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxSerializerTest {

    @Test
    fun `workout GPX export and import round-trip preserves trackpoints elevation and heart rate`() {
        val points = listOf(
            TrackPoint(lat = -6.218500, lng = 106.802000, ele = 18.5, hr = 142),
            TrackPoint(lat = -6.219200, lng = 106.803100, ele = 24.0, hr = 156),
            TrackPoint(lat = -6.220100, lng = 106.804400, ele = 31.2, hr = 165)
        )
        val workout = WorkoutActivity(
            id = "wk_1",
            title = "Senayan Morning Trail",
            sportType = SportType.TRAIL_RUN.name,
            distanceMeters = 5_420.0,
            movingTimeSeconds = 1_800L,
            elevationGainMeters = 45.0,
            routePoints = points.map { it.toMap() },
            elevationSource = "Copernicus DEM 90m"
        )

        val xml = GpxSerializer.buildWorkoutGpxXml(workout)
        assertTrue(xml.contains("<gpx version=\"1.1\""))
        assertTrue(xml.contains("<type>trail_running</type>"))
        assertTrue(xml.contains("<gpxtpx:hr>156</gpxtpx:hr>"))

        val parsed = GpxSerializer.parseGpxStream(xml.byteInputStream(), fallbackName = "Fallback")
        assertEquals("Senayan Morning Trail", parsed.name)
        assertEquals(SportType.TRAIL_RUN, parsed.sportType)
        assertEquals(3, parsed.points.size)
        assertEquals(-6.2185, parsed.points[0].lat, 0.0001)
        assertEquals(31.2, parsed.points[2].ele, 0.2)
        assertEquals(165, parsed.points[2].hr)
        assertNotNull(parsed.startTimeMs)
        assertNotNull(parsed.endTimeMs)
        assertEquals(1_800_000L, parsed.endTimeMs!! - parsed.startTimeMs!!)
    }

    @Test
    fun `route GPX export and import parses waypoints and downsamples uniformly`() {
        val waypoints = (0..300).map { i ->
            TrackPoint(
                lat = -6.20 + i * 0.0002,
                lng = 106.80 + i * 0.0002,
                ele = 20.0 + (i % 15),
                hr = 0
            )
        }
        val downsampled = GpxSerializer.downsampleTrackPoints(waypoints, 100)
        assertEquals(100, downsampled.size)
        assertEquals(waypoints.first(), downsampled.first())
        assertEquals(waypoints.last(), downsampled.last())

        val route = SavedRoute(
            id = "rt_1",
            name = "Sentul Gravel Climb",
            sportType = SportType.GRAVEL_RIDE.name,
            surfaceType = "Gravel",
            distanceMeters = 18_400.0,
            elevationGainMeters = 420.0,
            waypoints = downsampled.map { it.toMap() }
        )
        val xml = GpxSerializer.buildRouteGpxXml(route)
        val parsed = GpxSerializer.parseGpxStream(xml.byteInputStream())
        assertEquals("Sentul Gravel Climb", parsed.name)
        assertEquals(SportType.GRAVEL_RIDE, parsed.sportType)
        assertEquals(100, parsed.points.size)
    }
}
