package app.freshtuber.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * Kept separate from playback: changing the framing never changes play/pause.
 *
 * Near the scale where the picture reaches the screen's edges on both sides (zoom to fill, as on
 * a screen longer than the video) the picture sticks there: the fingers have to keep pinching
 * past [STICK] to leave it, and released inside it the picture stays filled.
 */
class VideoZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    /** The scale the fingers asked for; [scale] is this or the fill scale it sticks to. */
    private var raw = 1f
    /** Held at the fill scale. */
    var filled = false
        private set

    /** Returns true when the picture has just stuck to the edges. */
    fun transform(zoom: Float, pan: Offset, focus: Offset, viewport: Offset, aspect: Float): Boolean {
        raw = (raw * zoom).coerceIn(1f, 8f)
        val fill = fillScale(viewport, aspect)
        val stick = fill > 1.02f && abs(raw / fill - 1f) < STICK
        val next = if (stick) fill else raw
        val around = focus - viewport / 2f
        val translated = (offset - around) * (next / scale) + around + pan
        val fitWidth = minOf(viewport.x, viewport.y * aspect)
        val fitHeight = fitWidth / aspect
        val limitX = ((fitWidth * next - viewport.x) / 2f).coerceAtLeast(0f)
        val limitY = ((fitHeight * next - viewport.y) / 2f).coerceAtLeast(0f)
        offset = if (next == 1f) Offset.Zero else Offset(translated.x.coerceIn(-limitX, limitX), translated.y.coerceIn(-limitY, limitY))
        scale = next
        val stuck = stick && !filled
        filled = stick
        return stuck
    }

    /** Fingers lifted: the next pinch starts from what is shown; a barely zoomed picture goes back to fit. */
    fun release() {
        if (scale < 1.04f) { scale = 1f; offset = Offset.Zero }
        raw = scale
    }

    companion object {
        /** How far (as a fraction of the fill scale) the picture holds on to the edges. */
        const val STICK = 0.08f

        /** Scale at which a fitted picture of [aspect] covers the whole [viewport]. */
        fun fillScale(viewport: Offset, aspect: Float): Float {
            if (viewport.x <= 0f || viewport.y <= 0f || aspect <= 0f) return 1f
            val fitWidth = minOf(viewport.x, viewport.y * aspect)
            val fitHeight = fitWidth / aspect
            return maxOf(viewport.x / fitWidth, viewport.y / fitHeight)
        }
    }
}

/** Initial pass lets a pinch cancel taps in either classic or ergonomic video controls. */
fun Modifier.videoZoom(zoom: VideoZoom, enabled: Boolean, aspect: Float, panAllowed: (Offset, Offset) -> Boolean = { _, _ -> true },
    onStick: () -> Unit = {}): Modifier = if (!enabled) this else pointerInput(zoom, aspect) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var transformed = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val fingers = event.changes.count { it.pressed }
            val multi = fingers >= 2
            val moved = event.changes.firstOrNull { it.id == down.id }?.let { (it.position - down.position).getDistance() > viewConfiguration.touchSlop } == true
            if (multi || (zoom.scale > 1f && moved && panAllowed(down.position, Offset(size.width.toFloat(), size.height.toFloat())))) transformed = true
            if (transformed) {
                if (fingers > 0 && zoom.transform(if (multi) event.calculateZoom() else 1f, event.calculatePan(),
                    event.calculateCentroid(useCurrent = false), Offset(size.width.toFloat(), size.height.toFloat()), aspect)) onStick()
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (transformed) zoom.release()
    }
}
