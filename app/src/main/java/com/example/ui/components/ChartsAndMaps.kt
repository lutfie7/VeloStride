package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.Formatters
import com.example.data.model.FitnessLoadPoint
import com.example.data.model.TrackPoint
import com.example.ui.theme.AmberZone
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.EmeraldPeak
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange
import com.example.ui.theme.VioletZone
import com.example.ui.theme.Zone1Color
import com.example.ui.theme.Zone2Color
import com.example.ui.theme.Zone3Color
import com.example.ui.theme.Zone4Color
import com.example.ui.theme.Zone5Color
import kotlin.math.roundToInt

@Composable
fun RoutePolylineCanvas(
    points: List<TrackPoint>,
    modifier: Modifier = Modifier,
    strokeColor: Color = VeloOrange,
    showGrid: Boolean = true,
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val bgTop = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val bgBottom = MaterialTheme.colorScheme.surface

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
    ) {
        if (showGrid) {
            val cols = 6
            val rows = 4
            for (c in 1 until cols) {
                val x = size.width * (c / cols.toFloat())
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }
            for (r in 1 until rows) {
                val y = size.height * (r / rows.toFloat())
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
        }

        if (points.size < 2) {
            // Subtle contour placeholder arc when workout was logged manually without GPS points
            val path = Path().apply {
                moveTo(size.width * 0.12f, size.height * 0.72f)
                cubicTo(
                    size.width * 0.35f, size.height * 0.22f,
                    size.width * 0.65f, size.height * 0.80f,
                    size.width * 0.88f, size.height * 0.30f
                )
            }
            drawPath(
                path = path,
                color = strokeColor.copy(alpha = 0.45f),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
            return@Canvas
        }

        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }
        val minLng = points.minOf { it.lng }
        val maxLng = points.maxOf { it.lng }
        val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
        val lngSpan = (maxLng - minLng).coerceAtLeast(0.0005)
        val pad = 24f
        val drawW = (size.width - pad * 2).coerceAtLeast(10f)
        val drawH = (size.height - pad * 2).coerceAtLeast(10f)

        val mapped = points.map { pt ->
            val x = pad + ((pt.lng - minLng) / lngSpan).toFloat() * drawW
            val y = size.height - pad - ((pt.lat - minLat) / latSpan).toFloat() * drawH
            Offset(x, y)
        }

        val path = Path().apply {
            moveTo(mapped.first().x, mapped.first().y)
            for (i in 1 until mapped.size) {
                lineTo(mapped[i].x, mapped[i].y)
            }
        }

        // Glow under-stroke
        drawPath(
            path = path,
            color = strokeColor.copy(alpha = 0.25f),
            style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Start & End markers
        drawCircle(color = EmeraldPeak, radius = 9f, center = mapped.first())
        drawCircle(color = Color.White, radius = 4f, center = mapped.first())
        drawCircle(color = CrimsonPulse, radius = 9f, center = mapped.last())
        drawCircle(color = Color.White, radius = 4f, center = mapped.last())
    }
}

@Composable
fun ElevationAndHrChart(
    points: List<TrackPoint>,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) {
        Text(
            text = "No GPS elevation/HR sample track recorded for this manual session.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        return
    }
    var selectedIdx by remember(points) { mutableIntStateOf(-1) }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val selected = points.getOrNull(selectedIdx)

    Column(modifier = modifier) {
        if (selected != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Ele: ${Formatters.formatElevation(selected.ele)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = SignalCyan
                )
                Text(
                    text = if (selected.hr > 0) "HR: ${selected.hr} bpm" else "HR: --",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = CrimsonPulse
                )
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val idx = ((offset.x / size.width) * points.lastIndex)
                            .roundToInt()
                            .coerceIn(0, points.lastIndex)
                        selectedIdx = idx
                    }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures { change, _ ->
                        val idx = ((change.position.x / size.width) * points.lastIndex)
                            .roundToInt()
                            .coerceIn(0, points.lastIndex)
                        selectedIdx = idx
                    }
                }
        ) {
            val minEle = points.minOf { it.ele }
            val maxEle = points.maxOf { it.ele }
            val eleSpan = (maxEle - minEle).coerceAtLeast(10.0)
            val hrs = points.map { it.hr }.filter { it > 0 }
            val minHr = hrs.minOrNull() ?: 90
            val maxHr = hrs.maxOrNull() ?: 190
            val hrSpan = (maxHr - minHr).coerceAtLeast(20)

            for (r in 1..3) {
                val y = size.height * (r / 4f)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
            }

            val elePath = Path()
            val fillPath = Path()
            val hrPath = Path()
            var hasHrStarted = false

            points.forEachIndexed { idx, pt ->
                val x = (idx / points.lastIndex.toFloat()) * size.width
                val eleY = size.height - 16f - ((pt.ele - minEle) / eleSpan).toFloat() * (size.height - 32f)
                if (idx == 0) {
                    elePath.moveTo(x, eleY)
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, eleY)
                } else {
                    elePath.lineTo(x, eleY)
                    fillPath.lineTo(x, eleY)
                }
                if (pt.hr > 0) {
                    val hrY = size.height - 16f - ((pt.hr - minHr) / hrSpan.toFloat()) * (size.height - 32f)
                    if (!hasHrStarted) {
                        hrPath.moveTo(x, hrY)
                        hasHrStarted = true
                    } else {
                        hrPath.lineTo(x, hrY)
                    }
                }
            }
            fillPath.lineTo(size.width, size.height)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(SignalCyan.copy(alpha = 0.30f), Color.Transparent)
                )
            )
            drawPath(elePath, SignalCyan, style = Stroke(width = 4f, cap = StrokeCap.Round))
            if (hasHrStarted) {
                drawPath(hrPath, CrimsonPulse, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
            }
            if (selectedIdx in points.indices) {
                val sx = (selectedIdx / points.lastIndex.toFloat()) * size.width
                drawLine(Color.White.copy(alpha = 0.6f), Offset(sx, 0f), Offset(sx, size.height), 2f)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot("Elevation", SignalCyan)
            LegendDot("Heart Rate", CrimsonPulse)
            Text(
                text = "Drag chart to scrub",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FitnessFreshnessChart(
    series: List<FitnessLoadPoint>,
    modifier: Modifier = Modifier,
) {
    if (series.isEmpty()) return
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            val allVals = series.flatMap { listOf(it.fitnessCtl, it.fatigueAtl, it.formTsb) }
            val minVal = (allVals.minOrNull() ?: -20.0).coerceAtMost(-10.0)
            val maxVal = (allVals.maxOrNull() ?: 60.0).coerceAtLeast(40.0)
            val span = (maxVal - minVal).coerceAtLeast(20.0)
            val padY = 18f
            val h = size.height - padY * 2

            fun mapY(v: Double): Float = size.height - padY - ((v - minVal) / span).toFloat() * h

            // Zero line for Form (TSB)
            val zeroY = mapY(0.0)
            drawLine(gridColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 2f)
            drawLine(gridColor.copy(alpha = 0.25f), Offset(0f, size.height * 0.25f), Offset(size.width, size.height * 0.25f), 1f)
            drawLine(gridColor.copy(alpha = 0.25f), Offset(0f, size.height * 0.75f), Offset(size.width, size.height * 0.75f), 1f)

            val ctlPath = Path()
            val atlPath = Path()
            val tsbPath = Path()
            series.forEachIndexed { idx, pt ->
                val x = if (series.size == 1) size.width / 2f else (idx / series.lastIndex.toFloat()) * size.width
                val yCtl = mapY(pt.fitnessCtl)
                val yAtl = mapY(pt.fatigueAtl)
                val yTsb = mapY(pt.formTsb)
                if (idx == 0) {
                    ctlPath.moveTo(x, yCtl)
                    atlPath.moveTo(x, yAtl)
                    tsbPath.moveTo(x, yTsb)
                } else {
                    ctlPath.lineTo(x, yCtl)
                    atlPath.lineTo(x, yAtl)
                    tsbPath.lineTo(x, yTsb)
                }
            }
            drawPath(tsbPath, EmeraldPeak, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
            drawPath(atlPath, VioletZone, style = Stroke(width = 4f, cap = StrokeCap.Round))
            drawPath(ctlPath, VeloOrange, style = Stroke(width = 5.5f, cap = StrokeCap.Round))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LegendDot("Fitness (CTL)", VeloOrange)
            LegendDot("Fatigue (ATL)", VioletZone)
            LegendDot("Form (TSB)", EmeraldPeak)
        }
    }
}

@Composable
fun Vo2MaxTrendChart(
    series: List<FitnessLoadPoint>,
    modifier: Modifier = Modifier,
) {
    if (series.isEmpty()) return
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        val minV = (series.minOf { it.vo2Max } - 2.0).coerceAtLeast(20.0)
        val maxV = (series.maxOf { it.vo2Max } + 2.0).coerceAtLeast(minV + 6.0)
        val span = maxV - minV
        drawLine(gridColor, Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), 1f)

        val line = Path()
        val fill = Path()
        series.forEachIndexed { idx, pt ->
            val x = if (series.size == 1) size.width / 2f else (idx / series.lastIndex.toFloat()) * size.width
            val y = size.height - 16f - ((pt.vo2Max - minV) / span).toFloat() * (size.height - 32f)
            if (idx == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
            if (idx == series.lastIndex) {
                drawCircle(SignalCyan, 7f, Offset(x, y))
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(SignalCyan.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(line, SignalCyan, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
    }
}

@Composable
fun RecoveryGaugeRing(
    score: Int,
    label: String,
    accentColor: Color,
    sizeDp: Dp = 96.dp,
) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(modifier = Modifier.size(sizeDp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = accentColor,
                startAngle = 135f,
                sweepAngle = 270f * (score.coerceIn(0, 100) / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score%",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SleepStagesBar(
    deepMin: Int,
    lightMin: Int,
    remMin: Int,
    awakeMin: Int,
    modifier: Modifier = Modifier,
) {
    val total = (deepMin + lightMin + remMin + awakeMin).coerceAtLeast(1)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (deepMin > 0) {
                Box(
                    Modifier
                        .weight(deepMin / total.toFloat())
                        .fillMaxSize()
                        .background(VioletZone)
                )
            }
            if (lightMin > 0) {
                Box(
                    Modifier
                        .weight(lightMin / total.toFloat())
                        .fillMaxSize()
                        .background(SignalCyan)
                )
            }
            if (remMin > 0) {
                Box(
                    Modifier
                        .weight(remMin / total.toFloat())
                        .fillMaxSize()
                        .background(EmeraldPeak)
                )
            }
            if (awakeMin > 0) {
                Box(
                    Modifier
                        .weight(awakeMin / total.toFloat())
                        .fillMaxSize()
                        .background(AmberZone)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LegendDot("Deep ${deepMin}m", VioletZone)
            LegendDot("Light ${lightMin}m", SignalCyan)
            LegendDot("REM ${remMin}m", EmeraldPeak)
            LegendDot("Awake ${awakeMin}m", AmberZone)
        }
    }
}

@Composable
fun HrZonesBarChart(zoneSeconds: List<Long>, modifier: Modifier = Modifier) {
    val safeZones = if (zoneSeconds.size == 5) zoneSeconds else listOf(0L, 0L, 0L, 0L, 0L)
    val total = safeZones.sum().coerceAtLeast(1L)
    val labels = listOf("Z1 Recovery", "Z2 Endurance", "Z3 Tempo", "Z4 Threshold", "Z5 Peak")
    val colors = listOf(Zone1Color, Zone2Color, Zone3Color, Zone4Color, Zone5Color)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        safeZones.forEachIndexed { idx, secs ->
            val pct = (secs * 100.0 / total).roundToInt()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = labels[idx],
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(104.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (pct > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((pct / 100f).coerceIn(0.04f, 1f))
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(colors[idx])
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${Formatters.formatDurationCompact(secs)} ($pct%)",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LegendDot(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
