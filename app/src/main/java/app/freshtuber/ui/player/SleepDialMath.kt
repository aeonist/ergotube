package app.freshtuber.ui.player

import kotlin.math.PI
import kotlin.math.atan2

/** Ellipse-normalized rotation; crossing the top seam must never jump by an hour. */
internal object SleepDialMath {
    fun angle(x: Float, y: Float, radiusX: Float, radiusY: Float): Float =
        atan2(y / radiusY, x / radiusX)

    fun turn(minutes: Float, previous: Float, next: Float): Float {
        val tau = (2 * PI).toFloat()
        var delta = next - previous
        if (delta > PI) delta -= tau
        if (delta < -PI) delta += tau
        return (minutes + delta / tau * 60f).coerceIn(1f, 180f)
    }
}
