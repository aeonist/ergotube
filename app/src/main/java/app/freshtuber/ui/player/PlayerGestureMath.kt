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

/** A visible slice of the timeline that keeps the initial touch at the same screen position. */
data class TimelineWindow private constructor(private val start: Float, private val span: Float) {
    /** Share of the whole video that the visible slice covers. */
    val spanFraction: Float get() = span
    fun fractionAt(position: Float): Float = start + position.coerceIn(0f, 1f) * span
    fun positionOf(fraction: Float): Float = (fraction - start) / span

    companion object {
        val Full = TimelineWindow(0f, 1f)

        fun zoomAt(touch: Float, durationMs: Long): TimelineWindow {
            val span = if (durationMs > 60_000) 1f / 3.5f else 1f / 1.5f
            return TimelineWindow(touch.coerceIn(0f, 1f) * (1f - span), span)
        }

        /** A window of [span] of the video whose point [touch] sits on [fraction]: re-zooming mid-drag without a jump. */
        fun around(fraction: Float, touch: Float, span: Float): TimelineWindow =
            TimelineWindow((fraction - touch.coerceIn(0f, 1f) * span).coerceIn(0f, 1f - span), span)
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

/**
 * Full-screen scrubbing at a fixed ratio: the handle starts where it is (nothing jumps to the
 * finger) and always moves [RATIO] as far as the finger, whatever its speed. A finger that runs
 * 30 px a second along the timeline moves the handle 10 px a second, so every frame is easy to hit
 * and the handle never surprises by speeding up.
 */
class SlowScrub(start: Float) {
    /** Position in the video, 0..1. */
    var fraction = start.coerceIn(0f, 1f)
        private set

    /** The finger moved [delta] along a timeline [track] long (the whole video), both in one unit. */
    fun move(delta: Float, track: Float): Float {
        if (track <= 0f || delta == 0f) return fraction
        fraction = (fraction + delta / track * RATIO).coerceIn(0f, 1f)
        return fraction
    }

    companion object {
        /** Handle travel per finger travel. */
        const val RATIO = 1f / 3f
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

    /** The same by the play button's real edges: left of it skips back, right of it forward. */
    fun zone(x: Float, playLeft: Float, playRight: Float): PadZone = when {
        x < playLeft -> PadZone.Back
        x > playRight -> PadZone.Forward
        else -> PadZone.Play
    }

    /** What a drag became, or null while it is still undecided. Sizes in dp. */
    fun decide(dx: Float, dy: Float): PadMove? = when {
        abs(dx) > SEEK_SLOP_DP && abs(dx) > abs(dy) * 1.2f -> PadMove.Seek
        abs(dy) > VERTICAL_SLOP_DP && abs(dy) > abs(dx) -> PadMove.Vertical
        else -> null
    }

    /** A drag of ([dx], [dy]) dp that pulls the page down toward the mini-player, as the video does. */
    fun pullsDown(dx: Float, dy: Float): Boolean = dy > 0f && decide(dx, dy) == PadMove.Vertical

    /**
     * What a gesture that starts on [onButton] may become: a button keeps its tap, but a downward
     * pull from it still drags the page; seeking, holding and swiping up stay off buttons.
     */
    fun allowedFrom(onButton: Boolean, dx: Float, dy: Float): Boolean = !onButton || pullsDown(dx, dy)
}

/** "+20 с", "−1:20": signed seconds, minutes when it is a minute or more. */
fun signedSeek(seconds: Long): String {
    val sign = if (seconds < 0) "−" else "+"
    val a = abs(seconds)
    return if (a >= 60) "$sign${a / 60}:${(a % 60).toString().padStart(2, '0')}" else "$sign$a"
}
