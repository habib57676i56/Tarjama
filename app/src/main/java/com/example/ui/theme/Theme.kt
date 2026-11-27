package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val DarkLiquidGlassScheme = darkColorScheme(
    primary = LiquidCyan,
    onPrimary = MidnightObsidian,
    primaryContainer = Color(0xFF123458),
    onPrimaryContainer = Color(0xFFD1F2FF),
    secondary = AuroraViolet,
    onSecondary = MidnightObsidian,
    secondaryContainer = Color(0xFF1F2248),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = EmeraldMint,
    onTertiary = MidnightObsidian,
    background = MidnightObsidian,
    onBackground = TextPrimaryDark,
    surface = DeepSapphire,
    onSurface = TextPrimaryDark,
    surfaceVariant = ElevatedGlassDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color.White.copy(alpha = 0.22f),
    outlineVariant = Color.White.copy(alpha = 0.10f),
    error = CoralAlert,
    onError = MidnightObsidian
)

private val LightCrystalGlassScheme = lightColorScheme(
    primary = DeepOceanBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F0FF),
    onPrimaryContainer = Color(0xFF062C43),
    secondary = DeepIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E6FF),
    onSecondaryContainer = Color(0xFF1B1B54),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = CrystalFrostBg,
    onBackground = TextPrimaryLight,
    surface = CrystalSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = CrystalContainer,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFF0B132B).copy(alpha = 0.18f),
    outlineVariant = Color(0xFF0B132B).copy(alpha = 0.08f),
    error = Color(0xFFDC2626),
    onError = Color.White
)

val LiquidGlassShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

val LocalIsLiquidDark = staticCompositionLocalOf { true }

@Composable
fun TarjumanTheme(
    darkTheme: Boolean = true,
    rtlLayout: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkLiquidGlassScheme else LightCrystalGlassScheme
    val layoutDirection = if (rtlLayout) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalIsLiquidDark provides darkTheme,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = LiquidGlassShapes,
            content = content
        )
    }
}
