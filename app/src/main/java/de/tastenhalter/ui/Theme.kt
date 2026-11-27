package de.tastenhalter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.tastenhalter.tuning.Beat

/** Farben wie in Stimmlicht: warmes Schwarz, warmes Weiß, Orange = eng, Blau = weit. */
object Palette {
    val Ground = Color(0xFF0C0B0A)
    val Text = Color(0xFFF3EEE6)
    val Muted = Color(0xFF9C958B)
    val Orange = Color(0xFFFFAB52)
    val Blue = Color(0xFF7AB8FF)
    val Pure = Color(0xFFEFE2CC)
    val Danger = Color(0xFFE5484D)
    val Ok = Color(0xFF7BC88F)

    /** Flächen: Weiß mit wenig Deckkraft auf dem Grund. */
    val Surface = Color.White.copy(alpha = 0.045f)
    val SurfaceStrong = Color.White.copy(alpha = 0.085f)
    val Line = Color.White.copy(alpha = 0.10f)
    val LineStrong = Color.White.copy(alpha = 0.16f)

    fun accent(beat: Beat): Color = when (beat) {
        Beat.ENG -> Orange
        Beat.WEIT -> Blue
        Beat.REIN -> Pure
    }

    /** Licht: Kern, Mitte, Rand und Schein je Richtung (wie in Stimmlicht). */
    data class Light(val core: Color, val mid: Color, val rim: Color, val glow: Color)

    fun light(beat: Beat): Light = when (beat) {
        Beat.ENG -> Light(Color(0xFFFFE0B3), Color(0xFFFFA53F), Color(0xFFFF8514), Color(255, 140, 26))
        Beat.WEIT -> Light(Color(0xFFDCECFF), Color(0xFF66AFFF), Color(0xFF2687FF), Color(47, 140, 255))
        Beat.REIN -> Light(Color(0xFFFFFAF2), Color(0xFFF4E6CF), Color(0xFFE6CFAB), Color(255, 236, 205))
    }
}

val Serif = FontFamily.Serif

private val scheme = darkColorScheme(
    primary = Palette.Orange,
    onPrimary = Color(0xFF241400),
    primaryContainer = Color(0xFF3A2A16),
    onPrimaryContainer = Palette.Text,
    secondary = Palette.Blue,
    onSecondary = Color(0xFF06182B),
    background = Palette.Ground,
    onBackground = Palette.Text,
    surface = Palette.Ground,
    onSurface = Palette.Text,
    surfaceVariant = Color(0xFF1A1917),
    onSurfaceVariant = Palette.Muted,
    surfaceContainerLowest = Color(0xFF080807),
    surfaceContainerLow = Color(0xFF121110),
    surfaceContainer = Color(0xFF121110),
    surfaceContainerHigh = Color(0xFF1A1917),
    surfaceContainerHighest = Color(0xFF22211E),
    outline = Color(0xFF3A3834),
    outlineVariant = Color(0xFF2A2826),
    error = Palette.Danger,
    onError = Color.White,
    secondaryContainer = Color(0xFF2A2622),
    onSecondaryContainer = Palette.Text,
)

private val base = Typography()
private val typography = Typography(
    displaySmall = TextStyle(fontFamily = Serif, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = base.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    titleSmall = base.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.4.sp),
    labelSmall = base.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
)

/** Tonnamen kursiv in Serifenschrift, wie in Stimmlicht. */
val NoteStyle = TextStyle(fontFamily = Serif, fontStyle = FontStyle.Italic)

@Composable
fun TastenhalterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
