package app.freshtuber.ui.player

import android.os.SystemClock
import android.os.Build
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp
import app.freshtuber.ui.motion.Motion
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.composed
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pulling the portrait video down, as in YouTube: the whole watch page follows the finger,
 * recedes into a blur (slightly smaller, fainter), then either blurs away into the mini-player
 * or springs back into focus. Nothing slides off: the app's motion language is the blur.
 * The decision and the curves are plain functions in the companion so they can be unit tested.
 */
@Stable
class MinimizeDrag internal constructor(private val scope: CoroutineScope) {
    /** How far the page is pulled down on screen, in px (after resistance). */
    var offsetPx by mutableFloatStateOf(0f)
        private set
    /** Height of the page the drag is measured against, in px. */
    var heightPx by mutableFloatStateOf(0f)
    /** The page is on its way to the mini-player; further gestures are ignored. */
    var dismissing by mutableStateOf(false)
        private set
    /** 0..1 while the page blurs away on its way out (after a release past the threshold). */
    var exit by mutableFloatStateOf(0f)
        private set
    /**
     * Whether the page is still the one on top after [dismiss] handed over (it had nowhere to go):
     * only then does it come back into focus. A page that left stays blurred away until it is gone.
     */
    var stillShown: () -> Boolean = { true }
    /** Where the finger went down, as a share of the page height: the page shrinks around it. */
    private var pivotY by mutableFloatStateOf(0f)
    private var job: Job? = null
    /** Room below the finger where the current gesture started, px (the whole page until known). */
    private var roomPx by mutableFloatStateOf(Float.NaN)
    private val room: Float get() = if (roomPx.isNaN()) heightPx else roomPx
    /** One dp in px, known from the first gesture. */
    private var dpPx = 1f

    /** The release distance for this gesture, px (see [closeDistanceDp]). */
    private val closePx: Float get() = closeDistanceDp(heightPx, room, dpPx)

    /**
     * How far the page looks pulled: [DISTANCE_SHARE] exactly at the release distance, so a pull
     * from near the bottom shrinks and fades as much at its (shorter) threshold as one from the top.
     */
    val progress: Float get() = if (heightPx <= 0f) 0f else progressFor(offsetPx, closePx)

    /** How far toward its release distance the page is pulled, 0..1 (1 right at it and beyond). */
    val pull: Float get() = if (heightPx <= 0f) 0f else pullFor(offsetPx, closePx)

    /** A finger went down at [startYPx] on the page: the room below it sets the release distance. */
    internal fun begin(startYPx: Float, density: Float) {
        dpPx = density
        if (dismissing || offsetPx != 0f) return
        roomPx = (heightPx - startYPx).coerceIn(0f, heightPx)
        pivotY = if (heightPx > 0f) (startYPx / heightPx).coerceIn(0f, 1f) else 0f
    }

    /**
     * Applies the page transform. Read only here, inside graphicsLayer, so a drag redraws one layer
     * and never recomposes the page. The page follows the finger and shrinks around the point under
     * it (that point stays under the finger), blurs and fades; on the way out it blurs away.
     */
    fun apply(layer: GraphicsLayerScope) {
        val e = exit
        val p = pull
        if (offsetPx <= 0f && e <= 0f) return
        val s = scaleFor(p, e)
        layer.transformOrigin = TransformOrigin(0.5f, pivotY)
        layer.translationY = offsetPx
        layer.scaleX = s
        layer.scaleY = s
        layer.alpha = alphaFor(p, e)
        if (Build.VERSION.SDK_INT >= 31) {
            val r = with(layer) { blurDpFor(p, e).dp.toPx() }
            if (r > 0.5f) layer.renderEffect = BlurEffect(r, r, TileMode.Decal)
        }
    }

    /** Where [rect] (root px, inside a page [widthPx] wide at the top of the screen) is drawn now. */
    fun transform(rect: Rect, widthPx: Float): Rect {
        if (offsetPx <= 0f && exit <= 0f) return rect
        val s = scaleFor(pull, exit)
        val cx = widthPx / 2f
        val cy = pivotY * heightPx
        return Rect(cx + (rect.left - cx) * s, cy + (rect.top - cy) * s + offsetPx, cx + (rect.right - cx) * s, cy + (rect.bottom - cy) * s + offsetPx)
    }

    internal fun follow(distancePx: Float) {
        if (dismissing) return
        job?.cancel()
        offsetPx = resist(distancePx.coerceAtLeast(0f), heightPx)
    }

    /** Finger lifted after a drag of [distancePx] at [velocityPxPerSec] downward. */
    internal fun release(distancePx: Float, velocityPxPerSec: Float, density: Float, onMinimize: () -> Unit) {
        if (dismissing) return
        val minimize = heightPx > 0f && shouldMinimize(distancePx / density, velocityPxPerSec / density, heightPx / density, room / density)
        if (minimize) dismiss(onMinimize, velocityPxPerSec) else settle()
    }

    /** Sends the page down into the mini-player, then hands over to [onMinimize] (the pad swipe and buttons use this too). */
    fun dismiss(onMinimize: () -> Unit, @Suppress("UNUSED_PARAMETER") velocityPxPerSec: Float = 0f) {
        if (dismissing) return
        if (heightPx <= 0f) { onMinimize(); return }
        dismissing = true
        job?.cancel()
        // Leave at once: the screen below and the mini-player come into focus while this page
        // blurs away on top of them. Leaving only after the blur showed an empty background for
        // a moment and then popped the feed in, which read as a stutter.
        lastDismissAt = SystemClock.uptimeMillis()
        onMinimize()
        job = scope.launch {
            // The page stays where the finger left it and blurs away there; it does not fly off.
            animate(exit, 1f, animationSpec = tween(DISMISS_MS, easing = Motion.Decelerate)) { v, _ -> exit = v }
            // Normally the page is gone by now. If it stayed (nothing to go back to), bring it back into focus.
            delay(460)
            if (!stillShown()) return@launch
            dismissing = false
            val from = offsetPx
            animate(1f, 0f, animationSpec = tween(Motion.ENTER_MS, easing = Motion.Decelerate)) { v, _ -> exit = v; offsetPx = from * v }
        }
    }

    /** Springs back to rest. */
    fun settle() {
        if (dismissing || offsetPx == 0f) return
        job?.cancel()
        job = scope.launch {
            // Critically damped: back into focus without a visible bounce.
            animate(offsetPx, 0f, animationSpec = spring(dampingRatio = 1f, stiffness = 520f, visibilityThreshold = 0.5f)) { v, _ -> offsetPx = v }
        }
    }

    /** Back to rest at once (fullscreen, picture-in-picture). */
    fun reset() {
        job?.cancel()
        dismissing = false
        offsetPx = 0f
        exit = 0f
    }

    companion object {
        /** A release past this share of the page height minimizes (from the top of the page). */
        const val DISTANCE_SHARE = 0.25f
        /** ...or past this share of the room left below the finger, whichever is shorter. */
        const val ROOM_SHARE = 0.5f
        /** Never less than this, dp, so a small slip does not close the page... */
        const val MIN_DISTANCE_DP = 72f
        /** ...unless the room itself is that short: then this share of it, at least [FLOOR_DP]. */
        const val MAX_ROOM_SHARE = 0.8f
        const val FLOOR_DP = 40f
        /** A downward fling this fast (dp/s) minimizes whatever the distance. */
        const val FLING_DP_S = 900f
        /** Flicking back up this fast (dp/s) cancels even past the distance. */
        const val CANCEL_DP_S = -600f
        /** The shortest movement a fling still counts for, dp. */
        const val MIN_FLING_DP = 12f
        /** The blur-away after a release, ms. */
        const val DISMISS_MS = 240
        /** At the release distance the page is this small, this faint and this blurred (dp)... */
        const val PULL_SCALE = 0.94f
        const val PULL_ALPHA = 0.7f
        const val PULL_BLUR_DP = 14f
        /** ...and blurring away it ends this small and this blurred, fully transparent. */
        const val EXIT_SCALE = 0.88f
        const val EXIT_BLUR_DP = 28f

        /** Last time a drag or flick handed the page to the mini-player, for the navigation exit. */
        @Volatile var lastDismissAt = 0L
            private set

        /** The page has just been dragged off: its navigation exit should only finish a short fade. */
        fun recentlyDismissed(now: Long = SystemClock.uptimeMillis()): Boolean = now - lastDismissAt < 600

        /**
         * The release distance for a pull that started with [roomDp] of the page below the finger:
         * min(a quarter of the page, half the room), never under [MIN_DISTANCE_DP], and never more
         * than [MAX_ROOM_SHARE] of the room (at least [FLOOR_DP]) so it is always reachable.
         * Works in any unit; [unit] is one dp in it (1 for dp, the density for px).
         */
        fun closeDistanceDp(heightDp: Float, roomDp: Float, unit: Float): Float {
            if (heightDp <= 0f) return 0f
            val room = roomDp.coerceIn(0f, heightDp)
            val wanted = maxOf(minOf(heightDp * DISTANCE_SHARE, room * ROOM_SHARE), MIN_DISTANCE_DP * unit)
            return minOf(wanted, maxOf(room * MAX_ROOM_SHARE, FLOOR_DP * unit))
        }

        /** The look of a page pulled [offset] when it closes at [close]: [DISTANCE_SHARE] right at it. */
        fun progressFor(offset: Float, close: Float): Float =
            if (close <= 0f) 0f else (offset / close * DISTANCE_SHARE).coerceIn(0f, 1f)

        fun shouldMinimize(distanceDp: Float, velocityDpPerSec: Float, heightDp: Float, roomDp: Float = heightDp): Boolean {
            if (distanceDp <= 0f) return false
            if (velocityDpPerSec >= FLING_DP_S && distanceDp >= MIN_FLING_DP) return true
            if (velocityDpPerSec <= CANCEL_DP_S) return false
            return heightDp > 0f && distanceDp >= closeDistanceDp(heightDp, roomDp, 1f)
        }

        /** Slight resistance: the page lags the finger more the further it goes. */
        fun resist(distancePx: Float, heightPx: Float): Float =
            if (heightPx <= 0f || distancePx <= 0f) distancePx.coerceAtLeast(0f) else distancePx / (1f + distancePx / (heightPx * 2.5f))

        /** How far toward the release distance [close] a page pulled [offset] is, 0..1. */
        fun pullFor(offset: Float, close: Float): Float = if (close <= 0f) 0f else (offset / close).coerceIn(0f, 1f)

        private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

        /** Page scale for a pull of [pull] (0..1) and a blur-away of [exit] (0..1). */
        fun scaleFor(pull: Float, exit: Float = 0f): Float = mix(mix(1f, PULL_SCALE, pull), EXIT_SCALE, exit)

        fun alphaFor(pull: Float, exit: Float = 0f): Float = mix(mix(1f, PULL_ALPHA, pull), 0f, exit)

        /** Blur radius in dp. */
        fun blurDpFor(pull: Float, exit: Float = 0f): Float = mix(mix(0f, PULL_BLUR_DP, pull), EXIT_BLUR_DP, exit)

        /** The tracker's velocity, or for a gesture under 250 ms the average speed when that is higher (px/s). */
        fun flingVelocity(trackedPxPerSec: Float, distancePx: Float, durationMs: Long): Float {
            if (durationMs !in 1 until 250) return trackedPxPerSec
            return maxOf(trackedPxPerSec, distancePx * 1000f / durationMs)
        }

        /**
         * A list inside the page scrolled by the finger: how much of [availableY] (px, + is down)
         * the page takes, given how far the finger has pulled it already ([pulledPx]). Down is
         * the page's only once the list has nothing left to scroll (it is then "available");
         * up first lifts a pulled page back before the list scrolls again.
         */
        fun listTakes(availableY: Float, pulledPx: Float, afterList: Boolean): Float = when {
            afterList -> availableY.coerceAtLeast(0f)
            pulledPx > 0f && availableY < 0f -> maxOf(availableY, -pulledPx)
            else -> 0f
        }

        /** Whether a movement of ([dx], [dy]) past touch slop starts the pull. */
        fun startsPull(dx: Float, dy: Float): Boolean = dy > 0f && dy > abs(dx) * 1.2f
    }
}

@Composable
fun rememberMinimizeDrag(): MinimizeDrag {
    val scope = rememberCoroutineScope()
    return remember { MinimizeDrag(scope) }
}

/**
 * The whole-page pull: a mostly vertical downward drag that starts anywhere on this element
 * pulls the page (see [MinimizeDrag]). Put it on the page root BEFORE the page's
 * `graphicsLayer { drag.apply(this) }`, so positions here are screen positions.
 *
 * It watches the Main pass, after the page's own children: anything they already used (zoom pan,
 * seek bars, pad scrubbing, a list scroll, two fingers) and sideways or upward moves are left
 * alone. Once a downward move past touch slop is unclaimed, the pull takes it and consumes every
 * later event in the Initial pass, so buttons cancel their press and the pad drops its gesture.
 * Scrolling lists inside the page pull through the [MinimizeNestedScroll] attached here too:
 * a list scrolls first and hands the rest of a downward drag to the page once it is at its top.
 */
fun Modifier.minimizeDrag(drag: MinimizeDrag, enabled: Boolean, onMinimize: () -> Unit): Modifier {
    if (!enabled) return this
    return composed {
        val density = LocalDensity.current.density
        val latest by rememberUpdatedState(onMinimize)
        val lists = remember(drag, density) { MinimizeNestedScroll(drag, density) { latest() } }
        nestedScroll(lists).pointerInput(drag) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (drag.dismissing) return@awaitEachGesture
                val start = down.position
                // The room below this finger sets how far it has to go (lists use it as well).
                drag.begin(start.y, density)
                val slop = viewConfiguration.touchSlop
                var origin = 0f
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, start)
                // Phase 1: wait until the finger has clearly moved, then decide whether it is ours.
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.size > 1) return@awaitEachGesture
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (change.isConsumed) return@awaitEachGesture
                    val dx = change.position.x - start.x
                    val dy = change.position.y - start.y
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) {
                        // So quick that the finger was already up: a flick, judged by its speed alone.
                        if ((abs(dx) > slop || abs(dy) > slop) && MinimizeDrag.startsPull(dx, dy)) {
                            change.consume()
                            val velocity = MinimizeDrag.flingVelocity(tracker.calculateVelocity().y, dy, change.uptimeMillis - down.uptimeMillis)
                            drag.release(dy, velocity, density, latest)
                        }
                        return@awaitEachGesture
                    }
                    if (abs(dx) > slop || abs(dy) > slop) {
                        if (!MinimizeDrag.startsPull(dx, dy)) return@awaitEachGesture
                        change.consume()
                        // The page starts moving from here, so it does not jump by the slop.
                        origin = dy
                        break
                    }
                }
                // Phase 2: the page follows the finger; taken first so nothing under it acts as well.
                var distance = 0f
                var upAt = down.uptimeMillis
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || event.changes.size > 1) { drag.settle(); return@awaitEachGesture }
                    change.consume()
                    distance = change.position.y - start.y
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (change.pressed) drag.follow(distance - origin) else { upAt = change.uptimeMillis; break }
                }
                // A very quick flick gives the tracker too few samples; its average speed stands in.
                val velocity = MinimizeDrag.flingVelocity(tracker.calculateVelocity().y, distance, upAt - down.uptimeMillis)
                drag.release(distance, velocity, density, latest)
            }
        }
    }
}

/**
 * The pull for scrolling lists inside the page (lyrics, the queue): while a list is not at its
 * top the finger scrolls it; once it is, the rest of a downward drag pulls the page, and the
 * release decides as for [minimizeDrag]. Attach with `Modifier.nestedScroll` around the lists.
 */
class MinimizeNestedScroll(private val drag: MinimizeDrag, private val density: Float, private val onMinimize: () -> Unit) : NestedScrollConnection {
    /** The finger's movement as the list saw it while pulling, px; the page's own offset is added back. */
    private var local = 0f
    private val pulled get() = if (local > 0f) local + drag.offsetPx else 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || drag.dismissing) return Offset.Zero
        val take = MinimizeDrag.listTakes(available.y, pulled, afterList = false)
        if (take == 0f) return Offset.Zero
        local = (local + take).coerceAtLeast(0f)
        drag.follow(if (local > 0f) local + drag.offsetPx else 0f)
        return Offset(0f, take)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || drag.dismissing) return Offset.Zero
        val take = MinimizeDrag.listTakes(available.y, pulled, afterList = true)
        if (take == 0f) return Offset.Zero
        // The list moves with the page, so it reports the finger less the page's own movement.
        val finger = local + take + drag.offsetPx
        local += take
        drag.follow(finger)
        return Offset(0f, take)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (local <= 0f) return Velocity.Zero
        val distance = pulled
        local = 0f
        drag.release(distance, available.y, density, onMinimize)
        // A page let go does not also fling the list.
        return available
    }
}
