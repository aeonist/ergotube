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

/** Kept separate from playback: changing the framing never changes play/pause. */
class VideoZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    fun transform(zoom: Float, pan: Offset, focus: Offset, viewport: Offset, aspect: Float) {
        val next = (scale * zoom).coerceIn(1f, 8f)
        val around = focus - viewport / 2f
        val translated = (offset - around) * (next / scale) + around + pan
        val fitWidth = minOf(viewport.x, viewport.y * aspect)
        val fitHeight = fitWidth / aspect
        val limitX = ((fitWidth * next - viewport.x) / 2f).coerceAtLeast(0f)
        val limitY = ((fitHeight * next - viewport.y) / 2f).coerceAtLeast(0f)
        offset = if (next == 1f) Offset.Zero else Offset(translated.x.coerceIn(-limitX, limitX), translated.y.coerceIn(-limitY, limitY))
        scale = next
    }
}

/** Initial pass lets a pinch cancel taps in either classic or ergonomic video controls. */
fun Modifier.videoZoom(zoom: VideoZoom, enabled: Boolean, aspect: Float, panAllowed: (Offset, Offset) -> Boolean = { _, _ -> true }): Modifier = if (!enabled) this else pointerInput(zoom, aspect) {
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
                if (fingers > 0) zoom.transform(if (multi) event.calculateZoom() else 1f, event.calculatePan(),
                    event.calculateCentroid(useCurrent = false), Offset(size.width.toFloat(), size.height.toFloat()), aspect)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
