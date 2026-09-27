package com.noctyra.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Noctyra Design System Colors
val Accent         = Color(0xFFFF6B35)   // vibrant orange-red accent
val AccentDark     = Color(0xFFCC4A1A)
val AccentLight    = Color(0xFFFF9164)
val Purple         = Color(0xFF9B7DFF)   // kept for backward compat
val PurpleLight    = Color(0xFFB8A1FF)
val PurpleDark     = Color(0xFF7C5CE0)
val BackgroundDark = Color(0xFF0A0A0F)   // near-black deep background
val SurfaceDark    = Color(0xFF12121A)
val SurfaceCard    = Color(0xFF1A1A24)
val SurfaceElevated= Color(0xFF22222E)
val CardDark       = Color(0xFF1A1A24)
val TextPrimary    = Color(0xFFF5F4FF)
val TextSecondary  = Color(0xFF8884A0)
val TextMuted      = Color(0xFF4E4A66)
val ErrorRed       = Color(0xFFFF5252)
val Divider        = Color(0xFF1E1E2C)

private val NoctyraColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentDark,
    secondary = Purple,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceElevated,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    outline = Color(0xFF2A2A38)
)

private val NoctyraTypography = Typography(
    headlineLarge  = TextStyle(fontWeight = FontWeight.Black,    fontSize = 30.sp, color = TextPrimary),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 24.sp, color = TextPrimary),
    headlineSmall  = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 20.sp, color = TextPrimary),
    titleLarge     = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = TextPrimary),
    titleMedium    = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary),
    titleSmall     = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 13.sp, color = TextPrimary),
    bodyLarge      = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 16.sp, color = TextPrimary),
    bodyMedium     = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 14.sp, color = TextSecondary),
    bodySmall      = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 12.sp, color = TextSecondary),
    labelLarge     = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary),
    labelMedium    = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 11.sp, color = TextSecondary),
    labelSmall     = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 10.sp, color = TextMuted)
)

private val NoctyraShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(10.dp),
    medium     = RoundedCornerShape(14.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NoctyraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NoctyraColorScheme,
        typography  = NoctyraTypography,
        shapes      = NoctyraShapes,
        content     = content
    )
}
