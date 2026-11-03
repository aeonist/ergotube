package app.freshtuber.ui.radial

import org.junit.Assert.*
import org.junit.Test

class TouchRadialMenuTest {
    @Test fun `feed actions stay equally far from the pressed dots and inside the viewport`() {
        for (center in listOf(ArcPoint(324f, 80f), ArcPoint(324f, 350f), ArcPoint(324f, 620f), ArcPoint(36f, 350f))) {
            for (size in listOf(64f, 72f)) for (count in listOf(4, 5)) {
            val points = checkNotNull(touchMenuPoints(360f, 700f, center, count, size))
            val distance = center.distance(points.first())
            points.forEach { point ->
                assertEquals(distance, center.distance(point), 0.01f)
                assertTrue(point.x - size / 2 >= 8 && point.x + size / 2 <= 352)
                assertTrue(point.y - size / 2 >= 8 && point.y + size / 2 <= 692)
            }
            points.zipWithNext().forEach { (a, b) -> assertTrue(kotlin.math.max(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.y - b.y)) >= size + 8) }
            }
        }
    }
}
