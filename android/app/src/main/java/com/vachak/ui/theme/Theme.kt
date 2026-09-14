package com.vachak.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val VachakShape = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val LightScheme = lightColorScheme(
    primary = VachakColors.DeepLavender,
    onPrimary = Color.White,
    primaryContainer = VachakColors.Lavender100,
    onPrimaryContainer = VachakColors.ForestDark,
    secondary = VachakColors.Lavender600,
    onSecondary = Color.White,
    secondaryContainer = VachakColors.SoftLavender,
    onSecondaryContainer = VachakColors.Lavender700,
    tertiary = VachakColors.AccentDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE9C2),
    onTertiaryContainer = Color(0xFF5C3D00),
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = VachakColors.Background, // editorial #FAF9FF (vs #FEF7FF)
    onBackground = VachakColors.TextPrimary,
    surface = VachakColors.Surface,
    onSurface = VachakColors.TextPrimary,
    surfaceVariant = VachakColors.SoftLavender,
    onSurfaceVariant = VachakColors.TextSecondary,
    outline = VachakColors.Border,
    outlineVariant = VachakColors.Lavender200,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = Color(0xFFDDD8CB),
    surfaceBright = Color(0xFFFAF6EE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F0E6),
    surfaceContainer = Color(0xFFEFEADF),
    surfaceContainerHigh = Color(0xFFE9E3D5),
    surfaceContainerHighest = Color(0xFFE3DCCC),
)

private val DarkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

@Composable
fun VachakTheme(
    // Light-only: dark scheme is half-wired (screens hardcode light surfaces),
    // so never follow the system — always render the light classroom theme.
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = scheme,
        typography = VachakTypography,
        shapes = VachakShape,
        content = content
    )
}
