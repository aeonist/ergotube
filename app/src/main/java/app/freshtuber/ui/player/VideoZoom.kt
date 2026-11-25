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
import androidx.compose.ui.layout.layout
import kotlin.math.abs

/**
 * Kept separate from playback: changing the framing never changes play/pause.
 *
 * Near the scale where the picture reaches the screen's edges on both sides (zoom to fill, as on
 * a screen longer than the video) the picture sticks there: the fingers have to keep pinching
 * past [STICK] to leave it, and released inside it the picture stays filled. A filled picture is
 * held at the centre, and a zoomed one snaps back to the centre line it is dragged close to.
 *
 * The choice carries over: once a picture is left zoomed, the next fullscreen video opens filled
 * ([keepFilled]) until the fingers pinch a picture back to fit.
 */
class VideoZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    /** Where the fingers moved the picture; [offset] is this or the centre line it snaps to. */
    private var rawOffset = Offset.Zero
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
        val translated = (rawOffset - around) * (next / scale) + around + pan
        val fitWidth = minOf(viewport.x, viewport.y * aspect)
        val fitHeight = fitWidth / aspect
        val limitX = ((fitWidth * next - viewport.x) / 2f).coerceAtLeast(0f)
        val limitY = ((fitHeight * next - viewport.y) / 2f).coerceAtLeast(0f)
        rawOffset = if (next == 1f || stick) Offset.Zero else Offset(translated.x.coerceIn(-limitX, limitX), translated.y.coerceIn(-limitY, limitY))
        offset = Offset(centred(rawOffset.x, viewport.x), centred(rawOffset.y, viewport.y))
        scale = next
        val stuck = stick && !filled
        filled = stick
        return stuck
    }

    /** Fingers lifted: the next pinch starts from what is shown; a barely zoomed picture goes back to fit. */
    fun release() {
        if (scale < 1.04f) { scale = 1f; offset = Offset.Zero }
        raw = scale
        rawOffset = offset
        touched = true
        keepFilled = scale > 1f
    }

    /** Set once the fingers have framed this picture; from then on only they change it. */
    private var touched = false

    /**
     * The box measured [viewport] for a picture of [aspect]. An untouched picture opens filled when
     * the last one was left zoomed, and a filled one follows the fill scale as the size or the
     * picture's shape becomes known.
     */
    fun layout(viewport: Offset, aspect: Float) {
        val fill = fillScale(viewport, aspect)
        val autoFill = !touched && keepFilled
        if (!filled && !autoFill) return
        if (fill > 1.02f) {
            if (scale != fill) scale = fill
            raw = fill
            rawOffset = Offset.Zero
            if (offset != Offset.Zero) offset = Offset.Zero
            filled = true
        } else if (filled) {
            scale = 1f; raw = 1f; offset = Offset.Zero; rawOffset = Offset.Zero; filled = false
        }
    }

    /** Within [CENTER_SNAP] of the viewport's size an offset snaps to the centre line. */
    private fun centred(value: Float, size: Float): Float = if (abs(value) < size * CENTER_SNAP) 0f else value

    companion object {
        /** How far (as a fraction of the fill scale) the picture holds on to the edges. */
        const val STICK = 0.08f
        /** How close (as a fraction of the screen) a zoomed picture's centre snaps to the screen's centre. */
        const val CENTER_SNAP = 0.03f

        /** The last picture was left zoomed: open the next fullscreen picture filled. Lasts while the app runs. */
        @Volatile var keepFilled = false

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
    onStick: () -> Unit = {}): Modifier = if (!enabled) this else layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    zoom.layout(Offset(placeable.width.toFloat(), placeable.height.toFloat()), aspect)
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}.pointerInput(zoom, aspect) {
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
