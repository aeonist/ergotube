package app.freshtuber.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Full-screen fan buttons never overlap each other or leave the screen. */
class FanLayoutTest {
    /** What is drawn: [w]×[h] around the centre; back/forward are bare 32 dp icons. */
    private data class Box(val name: String, val x: Float, val y: Float, val w: Float, val h: Float) {
        private val cx get() = x + w / 2
        private val cy get() = y + h / 2
        /** Round shapes: closer than their radii plus a small gap. */
        fun overlaps(o: Box) = kotlin.math.hypot(cx - o.cx, cy - o.cy) < maxOf(w, h) / 2 + maxOf(o.w, o.h) / 2 + 4f
    }

    private fun boxes(g: FanGeometry): List<Box> {
        fun at(name: String, r: Float, u: Float, w: Float, h: Float) = g.position(r, u).let { Box(name, it.x - w / 2, it.y - h / 2, w, h) }
        val ring = FanGeometry.outerSlots(listOf("speed", "quality", "cc", "next"))
        val widths = mapOf("speed" to 56f, "quality" to 48f, "cc" to 44f, "next" to 44f)
        return listOf(
            at("back", g.main, 0.14f, 32f, 32f), at("play", g.main, 0.5f, 68f, 68f), at("forward", g.main, 0.86f, 32f, 32f),
            at("exit", g.inner, 0.5f, 44f, 44f),
        ) + ring.map { (key, u) -> at(key, g.outer, u, widths.getValue(key), 44f) }
    }

    @Test fun noOverlapOnPhones() {
        // Landscape phones from small to large (dp, after system insets), both hands.
        for ((w, h) in listOf(640f to 330f, 780f to 360f, 914f to 380f, 960f to 411f)) for (left in listOf(false, true)) {
            val all = boxes(FanGeometry(w, h, left))
            for (i in all.indices) for (j in i + 1 until all.size)
                assertFalse("${all[i].name} overlaps ${all[j].name} at ${w}x$h left=$left", all[i].overlaps(all[j]))
            all.forEach { assertTrue("${it.name} leaves the screen at ${w}x$h", it.x >= 0f && it.y >= 0f && it.x + it.w <= w && it.y + it.h <= h) }
        }
    }
}
