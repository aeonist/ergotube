package app.freshtuber.ui.player

import android.os.SystemClock
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pulling the portrait video down, as in YouTube: the whole watch page follows the finger,
 * shrinks a little and fades, then either drops into the mini-player or springs back.
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
    private var job: Job? = null
    /** Where the dragged element sits, to measure the finger on screen while the page moves. */
    internal var coords: LayoutCoordinates? = null

    /** 0 at rest, 1 when pulled a whole page height. */
    val progress: Float get() = if (heightPx <= 0f) 0f else (offsetPx / heightPx).coerceIn(0f, 1f)

    /** Applies the page transform (follow the finger, shrink toward the top centre, fade). */
    fun apply(layer: GraphicsLayerScope) {
        val p = progress
        if (p <= 0f) return
        val s = scaleFor(p)
        layer.transformOrigin = TransformOrigin(0.5f, 0f)
        layer.translationY = offsetPx
        layer.scaleX = s
        layer.scaleY = s
        layer.alpha = alphaFor(p)
    }

    /** Where [rect] (root px, inside a page [widthPx] wide at the top of the screen) is drawn now. */
    fun transform(rect: Rect, widthPx: Float): Rect {
        val p = progress
        if (p <= 0f) return rect
        val s = scaleFor(p)
        val cx = widthPx / 2f
        return Rect(cx + (rect.left - cx) * s, rect.top * s + offsetPx, cx + (rect.right - cx) * s, rect.bottom * s + offsetPx)
    }

    internal fun follow(distancePx: Float) {
        if (dismissing) return
        job?.cancel()
        offsetPx = resist(distancePx.coerceAtLeast(0f), heightPx)
    }

    /** Finger lifted after a drag of [distancePx] at [velocityPxPerSec] downward. */
    internal fun release(distancePx: Float, velocityPxPerSec: Float, density: Float, onMinimize: () -> Unit) {
        if (dismissing) return
        val minimize = heightPx > 0f && shouldMinimize(distancePx / density, velocityPxPerSec / density, heightPx / density)
        if (minimize) dismiss(onMinimize, velocityPxPerSec) else settle()
    }

    /** Sends the page down into the mini-player, then hands over to [onMinimize] (the pad swipe and buttons use this too). */
    fun dismiss(onMinimize: () -> Unit, velocityPxPerSec: Float = 0f) {
        if (dismissing) return
        if (heightPx <= 0f) { onMinimize(); return }
        dismissing = true
        job?.cancel()
        job = scope.launch {
            animate(offsetPx, heightPx * DISMISS_PROGRESS, velocityPxPerSec.coerceAtLeast(0f),
                tween(DISMISS_MS, easing = FastOutLinearInEasing)) { v, _ -> offsetPx = v }
            lastDismissAt = SystemClock.uptimeMillis()
            onMinimize()
            // Normally the page is gone by now. If it stayed (nothing to go back to), bring it back.
            delay(700)
            dismissing = false
            offsetPx = 0f
        }
    }

    /** Springs back to rest. */
    fun settle() {
        if (dismissing || offsetPx == 0f) return
        job?.cancel()
        job = scope.launch {
            animate(offsetPx, 0f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 600f)) { v, _ -> offsetPx = v }
        }
    }

    /** Back to rest at once (fullscreen, picture-in-picture). */
    fun reset() {
        job?.cancel()
        dismissing = false
        offsetPx = 0f
    }

    companion object {
        /** A release past this share of the page height minimizes. */
        const val DISTANCE_SHARE = 0.25f
        /** A downward fling this fast (dp/s) minimizes whatever the distance. */
        const val FLING_DP_S = 1200f
        /** Flicking back up this fast (dp/s) cancels even past the distance. */
        const val CANCEL_DP_S = -600f
        /** The shortest movement a fling still counts for, dp. */
        const val MIN_FLING_DP = 12f
        const val DISMISS_MS = 180
        const val DISMISS_PROGRESS = 0.62f
        /** The page ends this small at [SHRINK_SPAN] of the height and stays there. */
        const val MIN_SCALE = 0.9f
        const val SHRINK_SPAN = 0.4f
        const val MIN_ALPHA = 0.35f
        const val FADE_SPAN = 0.62f

        /** Last time a drag or flick handed the page to the mini-player, for the navigation exit. */
        @Volatile var lastDismissAt = 0L
            private set

        /** The page has just been dragged off: its navigation exit should only finish a short fade. */
        fun recentlyDismissed(now: Long = SystemClock.uptimeMillis()): Boolean = now - lastDismissAt < 600

        fun shouldMinimize(distanceDp: Float, velocityDpPerSec: Float, heightDp: Float): Boolean {
            if (distanceDp <= 0f) return false
            if (velocityDpPerSec >= FLING_DP_S && distanceDp >= MIN_FLING_DP) return true
            if (velocityDpPerSec <= CANCEL_DP_S) return false
            return heightDp > 0f && distanceDp >= heightDp * DISTANCE_SHARE
        }

        /** Slight resistance: the page lags the finger more the further it goes. */
        fun resist(distancePx: Float, heightPx: Float): Float =
            if (heightPx <= 0f || distancePx <= 0f) distancePx.coerceAtLeast(0f) else distancePx / (1f + distancePx / (heightPx * 2.5f))

        fun scaleFor(progress: Float): Float = 1f - (1f - MIN_SCALE) * (progress / SHRINK_SPAN).coerceIn(0f, 1f)

        fun alphaFor(progress: Float): Float = 1f - (1f - MIN_ALPHA) * (progress / FADE_SPAN).coerceIn(0f, 1f)

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
 * Lets a mostly vertical downward drag on this element pull the page (see [MinimizeDrag]).
 * Two fingers, events already used (zoom pan, seek bars) and sideways or upward moves are left alone.
 */
fun Modifier.minimizeDrag(drag: MinimizeDrag, enabled: Boolean, onMinimize: () -> Unit): Modifier {
    if (!enabled) return this
    return onGloballyPositioned { drag.coords = it }.pointerInput(drag) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (drag.dismissing) return@awaitEachGesture
            val coords = drag.coords ?: return@awaitEachGesture
            val startRoot = coords.localToRoot(down.position)
            val slop = viewConfiguration.touchSlop
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, startRoot)
            // Phase 1: wait until the finger has clearly moved, then decide whether it is ours.
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.size > 1) return@awaitEachGesture
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (change.isConsumed) return@awaitEachGesture
                val now = coords.localToRoot(change.position)
                val dx = now.x - startRoot.x
                val dy = now.y - startRoot.y
                tracker.addPosition(change.uptimeMillis, now)
                if (!change.pressed) {
                    // So quick that the finger was already up: a flick, judged by its speed alone.
                    if ((abs(dx) > slop || abs(dy) > slop) && MinimizeDrag.startsPull(dx, dy)) {
                        change.consume()
                        val velocity = MinimizeDrag.flingVelocity(tracker.calculateVelocity().y, dy, change.uptimeMillis - down.uptimeMillis)
                        drag.release(dy, velocity, density, onMinimize)
                    }
                    return@awaitEachGesture
                }
                if (abs(dx) > slop || abs(dy) > slop) {
                    if (!MinimizeDrag.startsPull(dx, dy)) return@awaitEachGesture
                    break
                }
            }
            // Phase 2: the page follows the finger. Measured on screen, since the page itself moves.
            var distance = 0f
            var upAt = down.uptimeMillis
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || event.changes.size > 1) { drag.settle(); return@awaitEachGesture }
                val root = coords.localToRoot(change.position)
                distance = root.y - startRoot.y
                if (change.pressed) {
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, root)
                    drag.follow(distance)
                } else {
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, root)
                    upAt = change.uptimeMillis
                    break
                }
            }
            // A very quick flick gives the tracker too few samples; its average speed stands in.
            val velocity = MinimizeDrag.flingVelocity(tracker.calculateVelocity().y, distance, upAt - down.uptimeMillis)
            drag.release(distance, velocity, density, onMinimize)
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
