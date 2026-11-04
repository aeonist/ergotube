package app.freshtuber.ui.player

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class MiniCornersTest {
    private val area = Offset(1000f, 2000f)
    private val window = Offset(400f, 225f)

    @Test fun defaultIsBottomOnTheWorkingHand() {
        assertEquals(3, MiniCorners.default(left = false))
        assertEquals(2, MiniCorners.default(left = true))
    }

    @Test fun cornersKeepMarginsAndBottomClearance() {
        assertEquals(Offset(30f, 30f), MiniCorners.position(0, area, window, 30f, 300f))
        assertEquals(Offset(570f, 30f), MiniCorners.position(1, area, window, 30f, 300f))
        assertEquals(Offset(30f, 1475f), MiniCorners.position(2, area, window, 30f, 300f))
        assertEquals(Offset(570f, 1475f), MiniCorners.position(3, area, window, 30f, 300f))
    }

    @Test fun nearestCornerFollowsTheCenter() {
        assertEquals(0, MiniCorners.nearest(Offset(100f, 100f), area))
        assertEquals(1, MiniCorners.nearest(Offset(900f, 100f), area))
        assertEquals(2, MiniCorners.nearest(Offset(100f, 1900f), area))
        assertEquals(3, MiniCorners.nearest(Offset(900f, 1900f), area))
    }
}
