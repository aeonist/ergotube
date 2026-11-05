package app.freshtuber.ui.player

import androidx.compose.ui.graphics.Color
import app.freshtuber.data.extras.SkipSegment

/**
 * SponsorBlock segments on timelines, in SponsorBlock's own colours so they read the same as in
 * its browser extension: sponsor green, self-promotion yellow, reminders violet, intro cyan,
 * ending blue (lightened so it shows on a dark bar).
 */
object SegmentMarks {
    fun color(category: String): Color = when (category) {
        "sponsor" -> Color(0xFF00D400)
        "selfpromo" -> Color(0xFFFFFF00)
        "interaction" -> Color(0xFFCC00FF)
        "intro" -> Color(0xFF00FFFF)
        "outro" -> Color(0xFF4F6BFF)
        else -> Color(0xFF00D400)
    }

    /** Start and end of each segment as fractions of [durationMs], clipped to 0..1. */
    fun spans(segments: List<SkipSegment>, durationMs: Long): List<Triple<Float, Float, Color>> {
        if (durationMs <= 0) return emptyList()
        return segments.mapNotNull { s ->
            val a = (s.start * 1000 / durationMs).toFloat().coerceIn(0f, 1f)
            val b = (s.end * 1000 / durationMs).toFloat().coerceIn(0f, 1f)
            if (b - a <= 0f) null else Triple(a, b, color(s.category))
        }
    }
}
