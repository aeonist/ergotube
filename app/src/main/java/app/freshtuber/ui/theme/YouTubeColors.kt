package app.freshtuber.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * YouTube's own flat palette (the Android app, 2024–2026): #0F0F0F night or white day, grey
 * chips and greys for secondary text. Selected chips are the inverse (white on dark).
 */
fun youTubeColorScheme(dark: Boolean, black: Boolean = false): ColorScheme = if (dark) {
    val ground = if (black) Color.Black else Color(0xFF0F0F0F)
    darkColorScheme(
        primary = Color(0xFFF1F1F1), onPrimary = Color(0xFF0F0F0F),
        primaryContainer = Color(0xFF3F3F3F), onPrimaryContainer = Color(0xFFF1F1F1),
        inversePrimary = Color(0xFF0F0F0F),
        secondary = Color(0xFFAAAAAA), onSecondary = Color(0xFF0F0F0F),
        secondaryContainer = Color(0xFF3F3F3F), onSecondaryContainer = Color(0xFFF1F1F1),
        tertiary = Color(0xFF3EA6FF), onTertiary = Color(0xFF0F0F0F),
        tertiaryContainer = Color(0xFF263850), onTertiaryContainer = Color(0xFFDCEBFF),
        background = ground, onBackground = Color(0xFFF1F1F1),
        surface = ground, onSurface = Color(0xFFF1F1F1),
        surfaceVariant = Color(0xFF272727), onSurfaceVariant = Color(0xFFAAAAAA),
        surfaceTint = Color.Transparent,
        inverseSurface = Color(0xFFF1F1F1), inverseOnSurface = Color(0xFF0F0F0F),
        surfaceBright = Color(0xFF3F3F3F), surfaceDim = ground,
        surfaceContainerLowest = ground,
        surfaceContainerLow = Color(0xFF181818),
        surfaceContainer = Color(0xFF212121),
        surfaceContainerHigh = Color(0xFF272727),
        surfaceContainerHighest = Color(0xFF3F3F3F),
        outline = Color(0xFF717171), outlineVariant = Color(0xFF3F3F3F),
        error = Color(0xFFFF4E45), onError = Color.White,
        scrim = Color.Black,
    )
} else lightColorScheme(
    primary = Color(0xFF0F0F0F), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E5E5), onPrimaryContainer = Color(0xFF0F0F0F),
    inversePrimary = Color(0xFFF1F1F1),
    secondary = Color(0xFF606060), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5E5), onSecondaryContainer = Color(0xFF0F0F0F),
    tertiary = Color(0xFF065FD4), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDEF1FF), onTertiaryContainer = Color(0xFF032F6B),
    background = Color.White, onBackground = Color(0xFF0F0F0F),
    surface = Color.White, onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFF2F2F2), onSurfaceVariant = Color(0xFF606060),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF0F0F0F), inverseOnSurface = Color(0xFFF1F1F1),
    surfaceBright = Color.White, surfaceDim = Color(0xFFE5E5E5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F9F9),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFE5E5E5),
    surfaceContainerHighest = Color(0xFFD9D9D9),
    outline = Color(0xFF909090), outlineVariant = Color(0xFFE5E5E5),
    error = Color(0xFFCC0000), onError = Color.White,
    scrim = Color.Black,
)
