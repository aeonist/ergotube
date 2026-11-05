package app.freshtuber.ui.theme

import androidx.compose.ui.graphics.Color

// Material roles come from liquidColorScheme (LiquidColors.kt); brand red stays for progress,
// live badges and the logo.

/** Colors that are not Material roles. */
object BrandColors {
    val Red = Color(0xFFFF0033)
    val OnRed = Color(0xFFFFFFFF)
    /** Duration and status badges sit on thumbnails, so they stay dark in both themes. */
    val BadgeBackground = Color(0xCC0F0F0F)
    val BadgeText = Color(0xFFFFFFFF)
}
