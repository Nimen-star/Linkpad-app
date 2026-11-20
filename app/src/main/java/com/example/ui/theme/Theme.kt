package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LinkPadColorScheme = darkColorScheme(
    primary = CobaltBlue,
    onPrimary = ObsidianBg,
    primaryContainer = CobaltDim,
    onPrimaryContainer = CobaltBlue,
    secondary = BrushedSilver,
    onSecondary = ObsidianBg,
    secondaryContainer = KeycapSurface,
    onSecondaryContainer = TitaniumText,
    tertiary = SignalEmerald,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = TitaniumText,
    surface = GraphiteSurface,
    onSurface = TitaniumText,
    surfaceVariant = KeycapSurface,
    onSurfaceVariant = PewterMuted,
    outline = HairlineBorder,
    outlineVariant = HairlineSubtle,
    error = PrecisionCrimson,
    onError = ObsidianBg,
    errorContainer = CrimsonDim,
    onErrorContainer = PrecisionCrimson
)

private val LinkPadShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun LinkPadTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LinkPadColorScheme,
        typography = LinkPadTypography,
        shapes = LinkPadShapes,
        content = content
    )
}
