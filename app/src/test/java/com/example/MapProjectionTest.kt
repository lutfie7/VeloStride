package com.example

import com.example.core.MapLayerType
import com.example.core.MapProjection
import com.example.data.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MapProjectionTest {

    @Test
    fun conformalProjectionPreservesSquareAspectRatioInWideContainer() {
        // Near equator (-6.2 deg lat), cos(lat) ~ 0.994, so equal dLat and dLng form a ~1:1 ground square
        val d = 0.01
        val points = listOf(
            TrackPoint(lat = -6.20, lng = 106.80),
            TrackPoint(lat = -6.20 + d, lng = 106.80 + d),
        )
        val widthPx = 1000f
        val heightPx = 300f
        val viewport = MapProjection.computeViewportForPoints(
            points = points,
            widthPx = widthPx,
            heightPx = heightPx,
            paddingPx = 20f
        )

        val (x0, y0) = MapProjection.project(points[0].lat, points[0].lng, viewport, widthPx, heightPx)
        val (x1, y1) = MapProjection.project(points[1].lat, points[1].lng, viewport, widthPx, heightPx)

        val projectedWidth = abs(x1 - x0)
        val projectedHeight = abs(y0 - y1)
        val ratio = projectedWidth / projectedHeight

        // Aspect ratio must remain ~1.0 rather than stretching to 1000/300 (3.33)
        assertTrue("Expected un-stretched ratio near 1.0 but was $ratio", ratio in 0.95f..1.05f)
    }

    @Test
    fun projectAndUnprojectRoundTripsCoordinates() {
        val viewport = MapProjection.computeViewportForCenter(
            centerLat = -6.2185,
            centerLng = 106.8020,
            zoom = 15
        )
        val targetLat = -6.2140
        val targetLng = 106.8075
        val (px, py) = MapProjection.project(targetLat, targetLng, viewport, 800f, 600f)
        val (latOut, lngOut) = MapProjection.unproject(px, py, viewport, 800f, 600f)

        assertEquals(targetLat, latOut, 1e-5)
        assertEquals(targetLng, lngOut, 1e-5)
    }

    @Test
    fun visibleTilesGeneratesValidUrlsForDarkAndLightThemes() {
        val viewport = MapProjection.computeViewportForCenter(-6.2185, 106.8020, zoom = 14)
        val darkTiles = MapProjection.visibleTiles(viewport, 800f, 500f, darkTheme = true)
        val lightTiles = MapProjection.visibleTiles(viewport, 800f, 500f, darkTheme = false)

        assertTrue(darkTiles.isNotEmpty())
        assertTrue(darkTiles.all { it.url.startsWith("https://tile.openstreetmap.org/14/") && it.url.endsWith(".png") })
        assertTrue(lightTiles.isNotEmpty())
        assertTrue(lightTiles.all { it.url.startsWith("https://tile.openstreetmap.org/14/") && it.url.endsWith(".png") })
    }

    @Test
    fun mapLayerTypesProduceDistinctValidTileUrls() {
        val osm = MapProjection.tileUrl(10, 20, 14, MapLayerType.STANDARD)
        val dark = MapProjection.tileUrl(10, 20, 14, MapLayerType.DARK)
        val topo = MapProjection.tileUrl(10, 20, 14, MapLayerType.TOPO)
        val cycling = MapProjection.tileUrl(10, 20, 14, MapLayerType.CYCLING)

        assertTrue(osm.contains("tile.openstreetmap.org/14/10/20.png"))
        assertTrue(dark.contains("tile.openstreetmap.org/14/10/20.png"))
        assertTrue(topo.contains("tile.opentopomap.org/14/10/20.png"))
        assertTrue(cycling.contains("tile-cyclosm.openstreetmap.fr/cyclosm/14/10/20.png"))
    }

    @Test
    fun zoomAroundFocusKeepsGeographicFocusCoordinateAnchored() {
        val vp = MapProjection.computeViewportForCenter(-6.2185, 106.8020, zoom = 14)
        val width = 800f
        val height = 600f
        val focusX = 560f
        val focusY = 220f
        val (latBefore, lngBefore) = MapProjection.unproject(focusX, focusY, vp, width, height, bearingDeg = 25f)

        val zoomed = MapProjection.zoomAroundFocus(
            viewport = vp,
            focusX = focusX,
            focusY = focusY,
            widthPx = width,
            heightPx = height,
            zoomFactor = 1.6f,
            bearingDeg = 25f
        )
        val (latAfter, lngAfter) = MapProjection.unproject(focusX, focusY, zoomed, width, height, bearingDeg = 25f)

        assertTrue(zoomed.scalePx > vp.scalePx)
        assertEquals(latBefore, latAfter, 1e-4)
        assertEquals(lngBefore, lngAfter, 1e-4)
    }

    @Test
    fun panViewportMovesCenterInOppositeWorldDirection() {
        val vp = MapProjection.computeViewportForCenter(-6.2185, 106.8020, zoom = 14)
        val panned = MapProjection.panViewport(vp, deltaXPx = 120f, deltaYPx = -80f, bearingDeg = 0f)

        assertTrue(panned.centerWX < vp.centerWX)
        assertTrue(panned.centerWY > vp.centerWY)
    }
}
