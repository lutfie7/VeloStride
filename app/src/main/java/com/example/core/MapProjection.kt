package com.example.core

import com.example.data.model.TrackPoint
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan

enum class MapLayerType(val label: String) {
    STANDARD("Standard"),
    DARK("Dark"),
    TOPO("Terrain"),
    CYCLING("Cycling");

    val attributionText: String
        get() = when (this) {
            TOPO -> "© OpenTopoMap / OSM"
            CYCLING -> "© CyclOSM / OSM"
            else -> "© OpenStreetMap"
        }
}

data class MapViewport(
    val centerWX: Double,
    val centerWY: Double,
    val scalePx: Double,
    val zoom: Int,
)

data class MapTileSpec(
    val x: Int,
    val y: Int,
    val zoom: Int,
    val leftPx: Float,
    val topPx: Float,
    val sizePx: Float,
    val url: String,
)

object MapProjection {
    private const val MAX_LAT = 85.05112878
    private const val TILE_BASE_PX = 256.0

    fun lngToWorldX(lng: Double): Double =
        ((lng.coerceIn(-180.0, 180.0)) + 180.0) / 360.0

    fun latToWorldY(lat: Double): Double {
        val rad = Math.toRadians(lat.coerceIn(-MAX_LAT, MAX_LAT))
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0
    }

    fun worldXToLng(wx: Double): Double =
        (wx * 360.0 - 180.0).coerceIn(-180.0, 180.0)

    fun worldYToLat(wy: Double): Double {
        val n = PI - 2.0 * PI * wy.coerceIn(0.0, 1.0)
        return Math.toDegrees(atan(sinh(n))).coerceIn(-MAX_LAT, MAX_LAT)
    }

    fun computeViewportForPoints(
        points: List<TrackPoint>,
        widthPx: Float,
        heightPx: Float,
        paddingPx: Float,
        minZoom: Int = 2,
        maxZoom: Int = 17,
    ): MapViewport {
        if (points.isEmpty()) {
            return computeViewportForCenter(-6.2185, 106.8020, zoom = 14)
        }
        var minWX = Double.POSITIVE_INFINITY
        var maxWX = Double.NEGATIVE_INFINITY
        var minWY = Double.POSITIVE_INFINITY
        var maxWY = Double.NEGATIVE_INFINITY

        for (pt in points) {
            val wx = lngToWorldX(pt.lng)
            val wy = latToWorldY(pt.lat)
            if (wx < minWX) minWX = wx
            if (wx > maxWX) maxWX = wx
            if (wy < minWY) minWY = wy
            if (wy > maxWY) maxWY = wy
        }

        val spanWX = (maxWX - minWX).coerceAtLeast(1e-6)
        val spanWY = (maxWY - minWY).coerceAtLeast(1e-6)
        val drawW = (widthPx - paddingPx * 2f).toDouble().coerceAtLeast(16.0)
        val drawH = (heightPx - paddingPx * 2f).toDouble().coerceAtLeast(16.0)

        val minScale = (1 shl minZoom) * TILE_BASE_PX
        val maxScale = (1 shl maxZoom) * TILE_BASE_PX
        // Single isotropic scale preserves true geographic aspect ratio (never stretched)
        val scalePx = minOf(drawW / spanWX, drawH / spanWY).coerceIn(minScale, maxScale)
        val zoom = floor(log2(scalePx / TILE_BASE_PX)).toInt().coerceIn(minZoom, maxZoom)

        return MapViewport(
            centerWX = (minWX + maxWX) / 2.0,
            centerWY = (minWY + maxWY) / 2.0,
            scalePx = scalePx,
            zoom = zoom
        )
    }

    fun computeViewportForCenter(
        centerLat: Double,
        centerLng: Double,
        zoom: Int = 14,
        tileSizePx: Double = 300.0,
    ): MapViewport {
        val safeZoom = zoom.coerceIn(2, 18)
        val scalePx = (1 shl safeZoom) * tileSizePx
        return MapViewport(
            centerWX = lngToWorldX(centerLng),
            centerWY = latToWorldY(centerLat),
            scalePx = scalePx,
            zoom = safeZoom
        )
    }

    fun panViewport(
        viewport: MapViewport,
        deltaXPx: Float,
        deltaYPx: Float,
        bearingDeg: Float = 0f,
    ): MapViewport {
        if (!deltaXPx.isFinite() || !deltaYPx.isFinite() || viewport.scalePx <= 0.0) return viewport
        val rad = Math.toRadians(-bearingDeg.toDouble())
        val cosR = cos(rad)
        val sinR = sin(rad)
        val worldDx = deltaXPx * cosR - deltaYPx * sinR
        val worldDy = deltaXPx * sinR + deltaYPx * cosR
        return viewport.copy(
            centerWX = (viewport.centerWX - worldDx / viewport.scalePx).coerceIn(0.0, 1.0),
            centerWY = (viewport.centerWY - worldDy / viewport.scalePx).coerceIn(0.0, 1.0)
        )
    }

    fun zoomViewport(
        viewport: MapViewport,
        zoomFactor: Float,
        minZoom: Int = 2,
        maxZoom: Int = 18,
    ): MapViewport {
        if (!zoomFactor.isFinite() || zoomFactor <= 0f) return viewport
        val minScale = (1 shl minZoom) * TILE_BASE_PX
        val maxScale = (1 shl maxZoom) * TILE_BASE_PX * 1.5
        val newScale = (viewport.scalePx * zoomFactor.toDouble()).coerceIn(minScale, maxScale)
        val newZoom = floor(log2(newScale / TILE_BASE_PX)).toInt().coerceIn(minZoom, maxZoom)
        return viewport.copy(scalePx = newScale, zoom = newZoom)
    }

    fun zoomAroundFocus(
        viewport: MapViewport,
        focusX: Float,
        focusY: Float,
        widthPx: Float,
        heightPx: Float,
        zoomFactor: Float,
        bearingDeg: Float = 0f,
        minZoom: Int = 2,
        maxZoom: Int = 18,
    ): MapViewport {
        if (!zoomFactor.isFinite() || zoomFactor <= 0f || widthPx <= 0f || heightPx <= 0f) return viewport
        val dxScreen = focusX - widthPx / 2f
        val dyScreen = focusY - heightPx / 2f
        val rad = Math.toRadians(-bearingDeg.toDouble())
        val cosR = cos(rad)
        val sinR = sin(rad)
        val dxWorldPx = dxScreen * cosR - dyScreen * sinR
        val dyWorldPx = dxScreen * sinR + dyScreen * cosR

        val focusWX = viewport.centerWX + dxWorldPx / viewport.scalePx
        val focusWY = viewport.centerWY + dyWorldPx / viewport.scalePx

        val zoomed = zoomViewport(viewport, zoomFactor, minZoom, maxZoom)
        val newCenterWX = (focusWX - dxWorldPx / zoomed.scalePx).coerceIn(0.0, 1.0)
        val newCenterWY = (focusWY - dyWorldPx / zoomed.scalePx).coerceIn(0.0, 1.0)
        return zoomed.copy(centerWX = newCenterWX, centerWY = newCenterWY)
    }

    fun project(
        lat: Double,
        lng: Double,
        viewport: MapViewport,
        widthPx: Float,
        heightPx: Float,
    ): Pair<Float, Float> {
        val wx = lngToWorldX(lng)
        val wy = latToWorldY(lat)
        val x = widthPx / 2f + ((wx - viewport.centerWX) * viewport.scalePx).toFloat()
        val y = heightPx / 2f + ((wy - viewport.centerWY) * viewport.scalePx).toFloat()
        return x to y
    }

    fun unproject(
        xPx: Float,
        yPx: Float,
        viewport: MapViewport,
        widthPx: Float,
        heightPx: Float,
        bearingDeg: Float = 0f,
    ): Pair<Double, Double> {
        val dxScreen = xPx - widthPx / 2f
        val dyScreen = yPx - heightPx / 2f
        val rad = Math.toRadians(-bearingDeg.toDouble())
        val cosR = cos(rad)
        val sinR = sin(rad)
        val dxWorld = dxScreen * cosR - dyScreen * sinR
        val dyWorld = dxScreen * sinR + dyScreen * cosR
        val wx = viewport.centerWX + dxWorld / viewport.scalePx
        val wy = viewport.centerWY + dyWorld / viewport.scalePx
        return worldYToLat(wy) to worldXToLng(wx)
    }

    @Suppress("UNUSED_PARAMETER")
    fun tileUrl(x: Int, y: Int, zoom: Int, darkTheme: Boolean = false): String =
        "https://tile.openstreetmap.org/$zoom/$x/$y.png"

    fun tileUrl(x: Int, y: Int, zoom: Int, layerType: MapLayerType): String = when (layerType) {
        MapLayerType.STANDARD, MapLayerType.DARK -> "https://tile.openstreetmap.org/$zoom/$x/$y.png"
        MapLayerType.TOPO -> "https://tile.opentopomap.org/$zoom/$x/$y.png"
        MapLayerType.CYCLING -> "https://a.tile-cyclosm.openstreetmap.fr/cyclosm/$zoom/$x/$y.png"
    }

    fun visibleTiles(
        viewport: MapViewport,
        widthPx: Float,
        heightPx: Float,
        darkTheme: Boolean,
        layerType: MapLayerType = if (darkTheme) MapLayerType.DARK else MapLayerType.STANDARD,
    ): List<MapTileSpec> {
        if (widthPx <= 0f || heightPx <= 0f || !viewport.scalePx.isFinite() || viewport.scalePx <= 0.0) return emptyList()
        val z = viewport.zoom.coerceIn(2, 18)
        val n = 1 shl z
        val tileSizePx = (viewport.scalePx / n).toFloat()
        if (tileSizePx <= 4f || !tileSizePx.isFinite()) return emptyList()

        val halfW = (widthPx / 2.0) / viewport.scalePx
        val halfH = (heightPx / 2.0) / viewport.scalePx
        val leftWX = viewport.centerWX - halfW
        val rightWX = viewport.centerWX + halfW
        val topWY = viewport.centerWY - halfH
        val bottomWY = viewport.centerWY + halfH

        val minTX = floor(leftWX * n).toInt()
        val maxTX = floor(rightWX * n).toInt()
        val minTY = floor(topWY * n).toInt().coerceIn(0, n - 1)
        val maxTY = floor(bottomWY * n).toInt().coerceIn(0, n - 1)

        val countX = (maxTX - minTX + 1).coerceAtLeast(0)
        val countY = (maxTY - minTY + 1).coerceAtLeast(0)
        if (countX * countY > 64) return emptyList()

        val tiles = ArrayList<MapTileSpec>(countX * countY)
        for (ty in minTY..maxTY) {
            for (txRaw in minTX..maxTX) {
                val tx = ((txRaw % n) + n) % n
                val leftPx = widthPx / 2f + ((txRaw.toDouble() / n - viewport.centerWX) * viewport.scalePx).toFloat()
                val topPx = heightPx / 2f + ((ty.toDouble() / n - viewport.centerWY) * viewport.scalePx).toFloat()
                tiles.add(
                    MapTileSpec(
                        x = tx,
                        y = ty,
                        zoom = z,
                        leftPx = leftPx,
                        topPx = topPx,
                        sizePx = tileSizePx,
                        url = tileUrl(tx, ty, z, layerType)
                    )
                )
            }
        }
        return tiles
    }
}
