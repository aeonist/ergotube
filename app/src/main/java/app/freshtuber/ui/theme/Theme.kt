package app.freshtuber.ui.theme

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * App theme. Without wallpaper colors the scheme is the liquid-light one: its accent hue follows
 * the frame in focus ([hue], degrees) and every change fades over about 700 ms.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    amoled: Boolean = false,
    serif: Boolean = true,
    hue: Float = 62f,
    content: @Composable () -> Unit,
) {
    val darkness by animateFloatAsState(if (darkTheme) 1f else 0f, tween(700), label = "dark")
    // Hue moves along the shorter way round the circle in a few steps over 700 ms. Every step
    // recomposes everything that reads a color, so it is not animated frame by frame.
    var shownHue by remember { mutableFloatStateOf(hue) }
    LaunchedEffect(hue) {
        val start = shownHue
        val delta = ((hue - start + 540f) % 360f) - 180f
        for (step in 1..HueSteps) {
            delay(700L / HueSteps)
            shownHue = start + delta * FastOutSlowInEasing.transform(step / HueSteps.toFloat())
        }
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (amoled && darkTheme) scheme.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black) else scheme
        }
        else -> liquidColorScheme(((shownHue % 360f) + 360f) % 360f, darkness, amoled)
    }
    val typography = remember(serif) { appTypography(serif) }
    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}

private const val HueSteps = 6

val LocalAppPreferences = androidx.compose.runtime.staticCompositionLocalOf { app.freshtuber.data.settings.AppPreferences() }
