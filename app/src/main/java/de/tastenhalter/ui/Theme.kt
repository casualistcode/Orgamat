package de.tastenhalter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Gold = Color(0xFFFFC857)
val Danger = Color(0xFFD32F2F)
val DangerBright = Color(0xFFFF5449)
val Ok = Color(0xFF43A047)
val Warn = Color(0xFFFFB300)
val PanelColor = Color(0xFF1C1E23)
val BarColor = Color(0xFF0C0D10)
val Outline = Color(0xFF4A4D55)

private val scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF2A1E00),
    primaryContainer = Color(0xFF4A3A10),
    onPrimaryContainer = Color(0xFFFFE3A3),
    secondary = Color(0xFF9CC3E6),
    onSecondary = Color(0xFF0B2236),
    background = Color(0xFF111215),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF16171B),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = PanelColor,
    onSurfaceVariant = Color(0xFFC9C9CF),
    error = DangerBright,
    onError = Color.Black,
    outline = Outline,
)

private val base = Typography()
private val typography = Typography(
    headlineLarge = base.headlineLarge.copy(fontSize = 34.sp),
    headlineMedium = base.headlineMedium.copy(fontSize = 30.sp),
    titleLarge = base.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 19.sp, lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 17.sp, lineHeight = 23.sp),
    bodySmall = base.bodySmall.copy(fontSize = 15.sp, lineHeight = 20.sp),
    labelLarge = base.labelLarge.copy(fontSize = 18.sp),
)

@Composable
fun TastenhalterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
