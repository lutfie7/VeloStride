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

    @Test
    fun `parseGpxStream preserves historical workout time with offsets, microseconds, and metadata fallback`() {
        val gpxWithOffsetsAndMicros = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="StravaGPX">
              <metadata><time>2025-05-10T06:00:00Z</time></metadata>
              <trk>
                <name>Sunday Morning Loop</name>
                <type>running</type>
                <trkseg>
                  <trkpt lat="-6.2185" lon="106.8020"><ele>15.0</ele><time>2025-05-10T06:05:12.123456+07:00</time></trkpt>
                  <trkpt lat="-6.2195" lon="106.8030"><ele>17.0</ele><time>2025-05-10T06:15:12.123456+07:00</time></trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val parsed = GpxSerializer.parseGpxStream(gpxWithOffsetsAndMicros.byteInputStream())
        // 2025-05-10T06:05:12+07:00 == 2025-05-09T23:05:12.123Z
        val expectedStart = GpxSerializer.parseIsoTimestampMs("2025-05-09T23:05:12.123Z")
        assertNotNull(expectedStart)
        assertEquals(expectedStart, parsed.startTimeMs)
        assertEquals(600_000L, parsed.endTimeMs!! - parsed.startTimeMs!!)

        val gpxMetadataTimeOnly = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="BandExport">
              <metadata><time>2025-03-15T01:30:00Z</time></metadata>
              <trk>
                <name>Band Walk</name>
                <trkseg>
                  <trkpt lat="-6.21" lon="106.80"><ele>10.0</ele></trkpt>
                  <trkpt lat="-6.22" lon="106.81"><ele>12.0</ele></trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()
        val parsedMeta = GpxSerializer.parseGpxStream(gpxMetadataTimeOnly.byteInputStream())
        assertEquals(GpxSerializer.parseIsoTimestampMs("2025-03-15T01:30:00Z"), parsedMeta.startTimeMs)
    }
}
