package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = VeloOrange,
    onPrimary = Color.White,
    primaryContainer = VeloOrangeContainer,
    onPrimaryContainer = VeloOrangeLight,
    secondary = SignalCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = Color(0xFF0A2E3A),
    onSecondaryContainer = SignalCyan,
    tertiary = EmeraldPeak,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = ChalkText,
    surface = GraphiteSurface,
    onSurface = ChalkText,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = MistText,
    outline = SlateOutline,
    error = CrimsonPulse,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = VeloOrange,
    onPrimary = Color.White,
    primaryContainer = LightOrangeContainer,
    onPrimaryContainer = VeloOrangeDark,
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = Color(0xFF16A34A),
    onTertiary = Color.White,
    background = AlabasterBg,
    onBackground = ObsidianInk,
    surface = PureCardSurface,
    onSurface = ObsidianInk,
    surfaceVariant = LightMutedSurface,
    onSurfaceVariant = SlateInk,
    outline = LightOutline,
    error = CrimsonPulse,
    onError = Color.White
)

val VeloShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VeloStrideTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = VeloShapes,
        content = content
    )
}
