package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BrandStrideOrange = Color(0xFFF26A21)
val BrandVeloDarkText = Color(0xFF252A2E)
val BrandVeloLightText = Color(0xFFE6EAEE)

@Composable
fun VeloStrideCrestIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val sx = this.size.width / 211f
        val sy = this.size.height / 139f
        val scale = minOf(sx, sy)
        val ox = (this.size.width - 211f * scale) / 2f
        val oy = (this.size.height - 139f * scale) / 2f

        fun poly(vararg pts: Pair<Float, Float>): Path = Path().apply {
            pts.forEachIndexed { idx, (px, py) ->
                val x = ox + px * scale
                val y = oy + (py - 20f) * scale
                if (idx == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        drawPath(poly(12f to 20f, 80f to 20f, 64f to 47f, 0f to 47f), BrandStrideOrange)
        drawPath(poly(41f to 76f, 123f to 76f, 107f to 103f, 25f to 103f), BrandStrideOrange)
        drawPath(poly(70f to 132f, 166f to 132f, 150f to 159f, 54f to 159f), BrandStrideOrange)
        drawPath(poly(127f to 20f, 211f to 20f, 195f to 47f, 111f to 47f), Color(0xFFAEB4BA))
        drawPath(poly(156f to 76f, 211f to 76f, 195f to 103f, 140f to 103f), Color(0xFFD9DDE1))
    }
}

@Composable
fun VeloStrideWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    onDarkSurface: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
    showCrest: Boolean = false,
    crestSize: Dp = 24.dp,
) {
    val veloColor = if (onDarkSurface) BrandVeloLightText else BrandVeloDarkText
    val letterSpacing = (-0.04f * fontSize.value).sp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        if (showCrest) {
            VeloStrideCrestIcon(size = crestSize)
        }
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = veloColor,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = letterSpacing
                    )
                ) {
                    append("Velo")
                }
                withStyle(
                    SpanStyle(
                        color = BrandStrideOrange,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = letterSpacing
                    )
                ) {
                    append("Stride")
                }
            },
            fontSize = fontSize,
            maxLines = 1
        )
    }
}
