package com.noctyra.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Pink = Color(0xFFEC268F)
val PinkLight = Color(0xFFFF5CB4)
val PinkDark = Color(0xFFB0106A)
val PinkContainer = Color(0xFF3A0A26)

val BackgroundDark = Color(0xFF0A0A10)
val SurfaceDark = Color(0xFF101017)
val SurfaceCard = Color(0xFF15151D)
val SurfaceElevated = Color(0xFF1C1C26)
val BottomBarColor = Color(0xFF121219)
val CardBorder = Color(0xFF272733)

val TextPrimary = Color(0xFFF5F5FA)
val TextSecondary = Color(0xFFA6A6B5)
val TextMuted = Color(0xFF5E5E6E)
val ErrorRed = Color(0xFFFF5470)
val SuccessGreen = Color(0xFF3DDC97)

val TopScrim = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))
val BottomFade = Brush.verticalGradient(0f to Color.Transparent, 0.6f to BackgroundDark.copy(alpha = 0.35f), 1f to BackgroundDark)
val LeftScrim = Brush.horizontalGradient(0f to BackgroundDark.copy(alpha = 0.7f), 0.65f to Color.Transparent)
val CardBottomFade = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))

private val NoctyraColorScheme = darkColorScheme(
    primary = Pink,
    onPrimary = Color.White,
    primaryContainer = PinkContainer,
    secondary = PinkLight,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceElevated,
    surfaceContainer = SurfaceElevated,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    outline = CardBorder
)

private val NoctyraTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 38.sp, color = TextPrimary),
    headlineLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 32.sp, color = TextPrimary),
    headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = TextPrimary),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp, color = TextPrimary),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp, color = TextPrimary),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = TextSecondary),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, color = TextSecondary),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, color = TextSecondary),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TextMuted)
)

private val NoctyraShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
