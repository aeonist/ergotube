package app.freshtuber.ui.player

import kotlin.math.abs
import kotlin.math.sign

/**
 * Taps on one side add up, like double taps on the video in YouTube: −10, −20, −30. A tap on
 * the other side, or a pause longer than [windowMs], starts over.
 */
class SeekBurst(private val windowMs: Long = 900) {
    var total = 0
        private set
    private var direction = 0
    private var lastAt = Long.MIN_VALUE

    /** Adds [seconds] (negative for back) at [nowMs] and returns the running total. */
    fun add(seconds: Int, nowMs: Long): Int {
        val d = seconds.sign
        total = if (d == direction && lastAt != Long.MIN_VALUE && nowMs - lastAt < windowMs) total + seconds else seconds
        direction = d
        lastAt = nowMs
        return total
    }
}

/** Horizontal drag scrubbing on the pad. Lifting the finger higher makes it five times finer. */
object Scrub {
    const val FINE_FACTOR = 0.2f
    /** How far the finger must rise above where it started, in dp, to scrub finely. */
    const val FINE_LIFT_DP = 48f

    /** Seconds covered by one full pad width: 30% of the video, between 1.5 and 10 minutes. */
    fun spanSeconds(durationSeconds: Float): Float = (durationSeconds * 0.3f).coerceIn(90f, 600f)

    fun isFine(startY: Float, y: Float, liftPx: Float): Boolean = startY - y > liftPx

    /** New position after the finger moved [dxPx] across a pad [widthPx] wide. */
    fun step(positionSeconds: Float, dxPx: Float, widthPx: Float, durationSeconds: Float, fine: Boolean): Float {
        if (widthPx <= 0f || durationSeconds <= 0f) return positionSeconds
        val span = spanSeconds(durationSeconds) * if (fine) FINE_FACTOR else 1f
        return (positionSeconds + dxPx / widthPx * span).coerceIn(0f, durationSeconds)
    }
}

enum class PadZone { Back, Play, Forward }

enum class PadMove { Seek, Vertical }

object PadGestures {
    const val HOLD_MS = 450L
    const val SEEK_SLOP_DP = 10f
    const val VERTICAL_SLOP_DP = 24f
    const val SWIPE_DP = 30f

    /** The pad's main area is three zones: left third back, middle play, right third forward. */
    fun zone(fractionX: Float): PadZone = when {
        fractionX < 0.32f -> PadZone.Back
        fractionX > 0.68f -> PadZone.Forward
        else -> PadZone.Play
    }

    /** What a drag became, or null while it is still undecided. Sizes in dp. */
    fun decide(dx: Float, dy: Float): PadMove? = when {
        abs(dx) > SEEK_SLOP_DP && abs(dx) > abs(dy) * 1.2f -> PadMove.Seek
        abs(dy) > VERTICAL_SLOP_DP && abs(dy) > abs(dx) -> PadMove.Vertical
        else -> null
    }
}

/** "+20 с", "−1:20": signed seconds, minutes when it is a minute or more. */
fun signedSeek(seconds: Long): String {
    val sign = if (seconds < 0) "−" else "+"
    val a = abs(seconds)
    return if (a >= 60) "$sign${a / 60}:${(a % 60).toString().padStart(2, '0')}" else "$sign$a"
}
