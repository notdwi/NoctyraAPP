package com.noctyra.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Purple = Color(0xFF9B7DFF)
val PurpleLight = Color(0xFFB8A1FF)
val PurpleDark = Color(0xFF7C5CE0)
val SurfaceDark = Color(0xFF1A1625)
val SurfaceVariantDark = Color(0xFF252033)
val BackgroundDark = Color(0xFF0F0B18)
val CardDark = Color(0xFF1E192A)
val TextPrimary = Color(0xFFF2F0F7)
val TextSecondary = Color(0xFF9E99A7)
val ErrorRed = Color(0xFFFF6B6B)

private val NoctyraColorScheme = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = PurpleDark,
    secondary = PurpleLight,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    outline = Color(0xFF3D3650)
)

private val NoctyraTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, color = TextPrimary),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = TextPrimary),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = TextPrimary),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = TextPrimary),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, color = TextPrimary),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPrimary),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, color = TextPrimary),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, color = TextSecondary),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, color = TextSecondary),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPrimary),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, color = TextSecondary),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, color = TextSecondary)
)

private val NoctyraShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun NoctyraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NoctyraColorScheme,
        typography = NoctyraTypography,
        shapes = NoctyraShapes,
        content = content
    )
}
