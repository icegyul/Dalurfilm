package com.dalur.film.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Brand palette: cinema-dark + champagne accent ----
val DalurBackground = Color(0xFF0B0B0D)
val DalurSurface = Color(0xFF141417)
val DalurSurfaceHigh = Color(0xFF1E1E24)
val DalurAccent = Color(0xFFE8DCC8)
val DalurOnAccent = Color(0xFF1A1712)
val DalurText = Color(0xFFF5F2EA)
val DalurMuted = Color(0xFF9A958A)
val DalurRec = Color(0xFFE5484D)
val DalurOutline = Color(0xFF2C2C33)

private val Scheme = darkColorScheme(
    background = DalurBackground,
    onBackground = DalurText,
    surface = DalurSurface,
    onSurface = DalurText,
    surfaceVariant = DalurSurfaceHigh,
    onSurfaceVariant = DalurMuted,
    surfaceContainer = DalurSurface,
    surfaceContainerHigh = DalurSurfaceHigh,
    primary = DalurAccent,
    onPrimary = DalurOnAccent,
    primaryContainer = DalurSurfaceHigh,
    onPrimaryContainer = DalurAccent,
    secondary = DalurMuted,
    onSecondary = DalurBackground,
    tertiary = DalurAccent,
    onTertiary = DalurOnAccent,
    outline = DalurOutline,
    outlineVariant = DalurOutline,
    error = DalurRec,
    onError = Color.White,
    surfaceTint = DalurAccent
)

private val Type = Typography(
    headlineMedium = TextStyle(
        fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp
    )
)

private val DalurShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun DalurTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, shapes = DalurShapes, content = content)
}
