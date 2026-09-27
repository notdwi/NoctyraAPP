package com.noctyra.app.ui.theme

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

// ── Noctyra Design System ── Rosa + Preto ──────────────────────────────────
val Pink           = Color(0xFFE91E8C)   // rosa vibrante principal
val PinkLight      = Color(0xFFFF5CBE)   // rosa claro
val PinkDark       = Color(0xFFB5006B)   // rosa escuro
val PinkContainer  = Color(0xFF3A0025)   // fundo de containers rosa
val Purple         = Color(0xFF9B7DFF)   // mantido por compat.
val PurpleLight    = Color(0xFFB8A1FF)
val PurpleDark     = Color(0xFF7C5CE0)

val BackgroundDark  = Color(0xFF080810)   // quase-preto fundo principal
val SurfaceDark     = Color(0xFF0F0F1A)   // superfície sutil
val SurfaceCard     = Color(0xFF15152A)   // card normal
val SurfaceElevated = Color(0xFF1E1E35)   // card elevado
val CardDark        = Color(0xFF15152A)
val Accent          = Pink                // compat alias

val TextPrimary   = Color(0xFFF2F0FF)
val TextSecondary = Color(0xFF8884A0)
val TextMuted     = Color(0xFF44415A)
val ErrorRed      = Color(0xFFFF5252)
val Divider       = Color(0xFF1A1A30)

private val NoctyraColorScheme = darkColorScheme(
    primary          = Pink,
    onPrimary        = Color.White,
    primaryContainer = PinkContainer,
    secondary        = PinkLight,
    background       = BackgroundDark,
    surface          = SurfaceDark,
    surfaceVariant   = SurfaceElevated,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error            = ErrorRed,
    outline          = Color(0xFF2A2A45)
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
