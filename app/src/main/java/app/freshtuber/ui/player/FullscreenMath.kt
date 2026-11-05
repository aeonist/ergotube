package app.freshtuber.ui.player

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The fullscreen fan: a quarter circle in the bottom corner on the working-hand side. Positions
 * along it are `u` from 0 to 1, clockwise for both hands, so back is at the start and forward at
 * the end. All sizes in one unit (the app uses dp).
 */
class FanGeometry(val width: Float, val height: Float, val left: Boolean) {
    /** Outer radius of the touch area; small enough to leave most of the picture to taps. */
    val radius = (minOf(width, height) * 0.62f).coerceIn(200f, 250f)
    /** Radius of the scrub arc. */
    val track = radius - 30f
    /** Touches this close inside the arc count as on the arc. */
    val band = 34f
    val main = radius * 0.53f
    val inner = radius * 0.25f
    val outer = radius + 30f

    /** Point at distance [r] from the corner, at position [u] along the quarter. */
    fun position(r: Float, u: Float): Offset {
        val a = u * PI.toFloat() / 2
        return if (left) Offset(r * sin(a), height - r * cos(a)) else Offset(width - r * cos(a), height - r * sin(a))
    }

    /** Distance from the corner and position along the quarter (clamped to 0..1). */
    fun polar(x: Float, y: Float): Pair<Float, Float> {
        val dx = if (left) x else width - x
        val dy = height - y
        val angle = if (left) atan2(dx, dy) else atan2(dy, dx)
        return hypot(dx, dy) to (angle / (PI.toFloat() / 2)).coerceIn(0f, 1f)
    }

    fun inFan(x: Float, y: Float): Boolean = polar(x, y).first <= radius && y <= height && (if (left) x >= 0 else x <= width)
    fun onTrack(r: Float): Boolean = r >= track - band

    /** Middle of the free part of the screen, for the bubble and the preview. */
    val freeCenterX get() = when {
        // Upright, the fan spans most of the width; the bubble and preview sit above it, centered.
        upright -> width / 2
        left -> radius + (width - radius) / 2
        else -> (width - radius) / 2
    }

    /** Taller than wide: a vertical video shown full screen upright. */
    val upright get() = width - radius < 220f

    companion object {
        const val U0 = 0.05f
        const val U1 = 0.95f
        /** Video fraction to position on the arc. */
        fun toArc(fraction: Float): Float = U0 + fraction.coerceIn(0f, 1f) * (U1 - U0)
        /** Position on the arc to video fraction. */
        fun fromArc(u: Float): Float = ((u - U0) / (U1 - U0)).coerceIn(0f, 1f)
        /** Back, play, forward on the middle ring. */
        fun zone(u: Float): PadZone = when { u < 0.33f -> PadZone.Back; u > 0.67f -> PadZone.Forward; else -> PadZone.Play }
        const val FINE_DISTANCE = 48f
        const val IDLE_MS = 3_000L
    }
}

/** Who holds the screen orientation while the player is open. */
enum class OrientationLock { None, Landscape, Portrait }

enum class PhysicalOrientation { Portrait, Landscape }

object FullscreenOrientation {
    /** Classifies a sensor angle in degrees; null between the clear positions. */
    fun physical(degrees: Int): PhysicalOrientation? = when {
        degrees < 0 -> null
        degrees <= 30 || degrees >= 330 -> PhysicalOrientation.Portrait
        degrees in 60..120 || degrees in 240..300 -> PhysicalOrientation.Landscape
        else -> null
    }

    /**
     * Next lock after the phone was physically turned. A fullscreen opened by the button is
     * left by turning the phone back upright once it was really held sideways; a fullscreen
     * closed by the button in landscape stays portrait until the phone is upright.
     */
    fun next(lock: OrientationLock, seenLandscape: Boolean, physical: PhysicalOrientation?): Pair<OrientationLock, Boolean> = when (lock) {
        OrientationLock.Landscape -> when {
            physical == PhysicalOrientation.Landscape -> lock to true
            physical == PhysicalOrientation.Portrait && seenLandscape -> OrientationLock.None to false
            else -> lock to seenLandscape
        }
        OrientationLock.Portrait -> if (physical == PhysicalOrientation.Portrait) OrientationLock.None to false else lock to false
        OrientationLock.None -> lock to false
    }
}

/**
 * The fullscreen controls arrive layer by layer out of the corner on one timeline: the shade,
 * the arc drawing itself, the three main buttons one after another, then chips and text. Hiding
 * runs the same timeline backward, faster, so the outer layers leave first.
 */
object FanMotion {
    const val IN_MS = 520
    const val OUT_MS = 300

    /** Progress 0..1 of a layer that starts [startMs] into the timeline at overall progress [t]. */
    fun stage(t: Float, startMs: Int, lengthMs: Int = 220): Float = ((t * IN_MS - startMs) / lengthMs).coerceIn(0f, 1f)
}
