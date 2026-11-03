package app.freshtuber.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The category standard at full craft: near-black neutrals, white as the interactive color,
// blue for links, and brand red kept for progress, live badges, the logo and the menu anchor.

/** Colors that are not Material roles. */
object BrandColors {
    val Red = Color(0xFFFF0033)
    val OnRed = Color(0xFFFFFFFF)
    /** Duration and status badges sit on thumbnails, so they stay dark in both themes. */
    val BadgeBackground = Color(0xCC0F0F0F)
    val BadgeText = Color(0xFFFFFFFF)
}

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFF1F1F1),
    onPrimary = Color(0xFF0F0F0F),
    primaryContainer = Color(0xFF272727),
    onPrimaryContainer = Color(0xFFF1F1F1),
    secondary = Color(0xFFAAAAAA),
    onSecondary = Color(0xFF0F0F0F),
    secondaryContainer = Color(0xFF272727),
    onSecondaryContainer = Color(0xFFF1F1F1),
    tertiary = Color(0xFF3EA6FF),
    onTertiary = Color(0xFF0F0F0F),
    tertiaryContainer = Color(0xFF263850),
    onTertiaryContainer = Color(0xFFD6E9FF),
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF0F0F0F),
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF272727),
    onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceTint = Color(0xFF0F0F0F),
    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF181818),
    surfaceContainer = Color(0xFF212121),
    surfaceContainerHigh = Color(0xFF272727),
    surfaceContainerHighest = Color(0xFF3F3F3F),
    inverseSurface = Color(0xFFF1F1F1),
    inverseOnSurface = Color(0xFF0F0F0F),
    outline = Color(0xFF5A5A5A),
    outlineVariant = Color(0xFF303030),
    error = Color(0xFFFF6B63),
    onError = Color(0xFF0F0F0F),
    scrim = Color(0xFF000000),
)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF0F0F0F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF2F2F2),
    onPrimaryContainer = Color(0xFF0F0F0F),
    secondary = Color(0xFF606060),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF2F2F2),
    onSecondaryContainer = Color(0xFF0F0F0F),
    tertiary = Color(0xFF065FD4),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDEF1FF),
    onTertiaryContainer = Color(0xFF002A5C),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F0F0F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF606060),
    surfaceTint = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F9F9),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFE9E9E9),
    surfaceContainerHighest = Color(0xFFE0E0E0),
    inverseSurface = Color(0xFF0F0F0F),
    inverseOnSurface = Color(0xFFF1F1F1),
    outline = Color(0xFF909090),
    outlineVariant = Color(0xFFE5E5E5),
    error = Color(0xFFCC0000),
    onError = Color(0xFFFFFFFF),
    scrim = Color(0xFF000000),
)
