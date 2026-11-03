package app.freshtuber.ui.radial

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class RadialGeometryTest {
    @Test fun `hands mirror the same arc`() {
        val right = RadialGeometry.layout(412f, 800f, false, ThumbArc(), true)
        val left = RadialGeometry.layout(412f, 800f, true, ThumbArc(), true)
        right.points(7).zip(left.points(7)).forEach { (r, l) ->
            assertEquals(412f, r.x + l.x, 0.001f)
            assertEquals(r.y, l.y, 0.001f)
        }
    }

    @Test fun `all visible targets clear edges and each other on narrow windows`() {
        for (width in listOf(320f, 360f, 412f, 600f)) {
            for (height in listOf(260f, 500f, 800f)) {
                for (left in listOf(false, true)) {
                    for (arc in listOf(ThumbArc(), ThumbArc(160f, 70f), ThumbArc(260f, 90f))) {
                        val layout = RadialGeometry.layout(width, height, left, arc, true)
                        for (outer in listOf(false, true)) {
                            val radius = if (outer) 32f else 24f
                            val points = layout.points(layout.capacity(outer), outer)
                            points.forEach { p ->
                                assertTrue("$width x $height: $p", p.x - radius >= 8 && p.x + radius <= width - 8)
                                assertTrue(p.y - radius >= 8 && p.y + radius <= height - 8)
                            }
                            points.zipWithNext().forEach { (a, b) -> assertTrue(a.distance(b) >= 2 * radius + 7.99f) }
                        }
                    }
                }
            }
        }
    }

    @Test fun `large-font outer targets fit without shrinking touch areas`() {
        for (target in listOf(84f, 96f)) {
            val layout = RadialGeometry.layout(352f, 800f, false, ThumbArc(), true, target)
            val points = layout.points(layout.capacity(true), true)
            points.forEach { p ->
                assertTrue(p.x - target / 2 >= 8f)
                assertTrue(p.x + target / 2 <= 344f)
                assertTrue(p.y + target / 2 <= 792f)
            }
            points.zipWithNext().forEach { (a, b) -> assertTrue(a.distance(b) >= target + 15.99f) }
            assertTrue(layout.outerRadius - layout.radius >= 24f + target / 2 + 7.99f)
        }
    }

    @Test fun `portrait player keeps three radial controls above its timeline`() {
        val layout = RadialGeometry.layout(352f, 154f, false, ThumbArc(), false)
        assertTrue(layout.capacity() >= 3)
        layout.points(3).forEach { assertTrue(it.y - 24f >= 8f) }
    }

    @Test fun `hit testing rejects the gaps and canceled gestures`() {
        val points = listOf(ArcPoint(10f, 10f), ArcPoint(100f, 10f))
        assertEquals(0, RadialGeometry.hit(ArcPoint(20f, 10f), points))
        assertEquals(1, RadialGeometry.hit(ArcPoint(100f, 30f), points))
        assertNull(RadialGeometry.hit(ArcPoint(55f, 10f), points))
        assertNull(RadialGeometry.hit(ArcPoint(-200f, 10f), points))
        assertNull(RadialGeometry.hit(ArcPoint(1f, 1f), emptyList()))
    }

    private fun trace(anchor: ArcPoint, radius: Float, left: Boolean): List<ArcPoint> = (0..20).map { index ->
        val angle = index * PI / 40
        ArcPoint(anchor.x + (if (left) 1 else -1) * radius * cos(angle).toFloat(), anchor.y - radius * sin(angle).toFloat())
    }

    @Test fun `calibration fits the median reach for either hand`() {
        val anchor = ArcPoint(300f, 500f)
        for (left in listOf(false, true)) {
            val result = RadialGeometry.calibrate(anchor, trace(anchor, 204f, left), left)!!
            assertEquals(204f, result.radius, 0.01f)
            assertEquals(90f, result.sweep, 0.01f)
        }
    }

    @Test fun `calibration ignores outliers and refuses short or wrong-side strokes`() {
        val anchor = ArcPoint(300f, 500f)
        val good = trace(anchor, 210f, false)
        assertEquals(210f, RadialGeometry.calibrate(anchor, good + ArcPoint(0f, 500f), false)!!.radius, 0.01f)
        assertNull(RadialGeometry.calibrate(anchor, good.take(4), false))
        assertNull(RadialGeometry.calibrate(anchor, good.take(9), false))
        assertNull(RadialGeometry.calibrate(anchor, good, true))
    }

    @Test fun `invalid saved calibration falls back to safe values`() {
        assertEquals(ThumbArc(), ThumbArc(Float.NaN, Float.POSITIVE_INFINITY).normalized())
        assertEquals(ThumbArc(80f, 90f), ThumbArc(-1f, 400f).normalized())
    }
    @Test fun `calibration recovers independent axes for both hands`() {
        val anchor = ArcPoint(340f, 700f)
        for (left in listOf(false, true)) {
            val samples = (0..40).map { index ->
                val t = index * PI / 80
                ArcPoint(anchor.x + (if (left) 1 else -1) * 185f * cos(t).toFloat(), anchor.y - 330f * sin(t).toFloat())
            }
            val result = RadialGeometry.calibrate(anchor, samples, left)!!
            assertEquals(185f, result.radius, 0.1f)
            assertEquals(330f, result.verticalRadius, 0.1f)
        }
    }

    @Test fun `taller reach changes vertical placement without moving side endpoint`() {
        val a = RadialGeometry.layout(412f, 800f, false, ThumbArc(180f, 90f, 200f), true)
        val b = RadialGeometry.layout(412f, 800f, false, ThumbArc(180f, 90f, 340f), true)
        assertEquals(a.points(5, true).first(), b.points(5, true).first())
        assertTrue(b.points(5, true).last().y < a.points(5, true).last().y - 100)
    }

    @Test fun `elliptical targets fit edges and do not overlap`() {
        for (left in listOf(false, true)) for (width in listOf(320f, 412f)) {
            val layout = RadialGeometry.layout(width, 680f, left, ThumbArc(180f, 90f, 380f), true, 84f)
            val points = layout.points(layout.capacity(true), true)
            points.forEach { p ->
                assertTrue(p.x - 42f >= 8f && p.x + 42f <= width - 8f)
                assertTrue(p.y - 42f >= 8f && p.y + 42f <= 672f)
            }
            points.zipWithNext().forEach { (a, b) -> assertTrue(a.distance(b) >= 100f) }
        }
    }
    @Test fun `single level keeps fixed anchor and separates actual square targets`() {
        val layout = RadialGeometry.singleLevelLayout(412f, 800f, false, ThumbArc(), 72f)
        assertTrue(layout.capacity(true) >= 3)
        val points = layout.points(layout.capacity(true), true)
        assertEquals(220f, layout.anchor.x - points.first().x, 0.1f)
        assertTrue(layout.anchor.y - points.last().y <= 300f)
        points.zipWithNext().forEach { (a, b) -> assertTrue(abs(a.x - b.x) >= 80f || abs(a.y - b.y) >= 80f) }
        val large = RadialGeometry.singleLevelLayout(412f, 800f, false, ThumbArc(180f, 90f, 340f), 104f)
        assertEquals(layout.anchor, large.anchor)
        large.points(large.capacity(true), true).forEach { p ->
            assertTrue(p.x - 52f >= 8f && p.x + 52f <= 404f)
            assertTrue(p.y - 52f >= 8f && p.y + 52f <= 792f)
        }
    }
    @Test fun `short pages fill consecutive slots from the bottom`() {
        val layout = RadialGeometry.singleLevelLayout(412f, 800f, false, ThumbArc(), 64f)
        val full = layout.points(layout.capacity(true), true)
        assertEquals(full.take(2), layout.points(2, true))
        assertEquals(full.take(1), layout.points(1, true))
        full.zipWithNext().forEach { (a, b) -> assertTrue(a.y > b.y) }
    }

    @Test fun `single level paginates before rectangular hit regions overlap`() {
        for (width in listOf(320f, 360f, 412f)) for (height in listOf(260f, 500f, 800f)) {
            for (left in listOf(false, true)) for (target in listOf(72f, 94f, 128f)) {
                val layout = RadialGeometry.singleLevelLayout(width, height, left, ThumbArc(180f, 90f, 340f), target)
                if (layout.capacity(true) < 3) continue // The UI uses its list fallback.
                val points = layout.points(layout.capacity(true), true)
                points.forEach { p ->
                    assertTrue(p.x - target / 2 >= 7.99f && p.x + target / 2 <= width - 7.99f)
                    assertTrue(p.y - target / 2 >= 7.99f && p.y + target / 2 <= height - 7.99f)
                }
                points.zipWithNext().forEach { (a, b) ->
                    assertTrue(abs(a.x - b.x) >= target + 7.99f || abs(a.y - b.y) >= target + 7.99f)
                }
            }
        }
    }
    @Test fun `custom launcher origin stays fixed across target sizes`() {
        val origin = ArcPoint(330f, 590f)
        for (target in listOf(48f, 56f, 64f)) {
            val layout = RadialGeometry.singleLevelLayout(412f, 800f, false, ThumbArc(), target, origin)
            assertEquals(origin, layout.anchor)
        }
    }

    @Test fun `six slots fit default reach at accessible size for both hands`() {
        for (left in listOf(false, true)) {
            val layout = RadialGeometry.singleLevelLayout(360f, 760f, left, ThumbArc(), 48f)
            assertTrue(layout.capacity(true) >= 6)
            val points = layout.points(6, true)
            assertEquals(points.take(5), layout.points(5, true))
            points.zipWithNext().forEach { (a, b) ->
                assertTrue(abs(a.x - b.x) >= 55.99f || abs(a.y - b.y) >= 55.99f)
            }
        }
    }

    @Test fun `menu stays an accessible six slot arc at every reach extreme`() {
        for (width in listOf(320f, 360f, 412f, 760f)) for (height in listOf(260f, 500f, 760f)) {
            for (left in listOf(false, true)) for (x in listOf(80f, 160f, 300f)) for (y in listOf(80f, 160f, 420f)) for (sweep in listOf(70f, 90f)) {
                val layout = RadialGeometry.menuLayout(width, height, left, ThumbArc(x, sweep, y))
                assertTrue("$width $height $x $y: $layout", layout.capacity(true) >= 6)
                assertTrue(layout.outerTarget >= 48f)
                val points = layout.points(6, true)
                assertEquals(points.take(3), layout.points(3, true))
                points.forEach { p ->
                    assertTrue(p.x - layout.outerTarget / 2 >= 7.99f && p.x + layout.outerTarget / 2 <= width - 7.99f)
                    assertTrue(p.y - layout.outerTarget / 2 >= 7.99f && p.y + layout.outerTarget / 2 <= height - 7.99f)
                    assertTrue(p.distance(layout.anchor) >= layout.outerTarget / 2 + 36f)
                }
            }
        }
    }

}
