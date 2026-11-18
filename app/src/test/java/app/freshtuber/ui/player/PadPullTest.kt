package app.freshtuber.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pad's downward swipe pulls the page like the video; other pad gestures stay as they were. */
class PadPullTest {
    @Test fun mostlyVerticalDownwardMovePulls() {
        assertTrue(PadGestures.pullsDown(5f, 30f))
        assertTrue(PadGestures.pullsDown(-20f, 26f))
    }

    @Test fun sidewaysUpwardAndTinyMovesDoNotPull() {
        assertFalse(PadGestures.pullsDown(30f, 12f))      // scrubbing
        assertEquals(PadMove.Seek, PadGestures.decide(30f, 12f))
        assertFalse(PadGestures.pullsDown(3f, -40f))      // swipe up stays full screen
        assertEquals(PadMove.Vertical, PadGestures.decide(3f, -40f))
        assertFalse(PadGestures.pullsDown(2f, 15f))       // still a tap or a hold
        assertNull(PadGestures.decide(2f, 15f))
        assertFalse(PadGestures.pullsDown(30f, 30f))      // an exact diagonal is undecided, not a pull
    }

    @Test fun buttonsKeepTheirTapButLetAPullThrough() {
        assertTrue(PadGestures.allowedFrom(onButton = true, dx = 4f, dy = 40f))
        assertFalse(PadGestures.allowedFrom(onButton = true, dx = 40f, dy = 4f))
        assertFalse(PadGestures.allowedFrom(onButton = true, dx = 0f, dy = -40f))
        assertTrue(PadGestures.allowedFrom(onButton = false, dx = 40f, dy = 4f))
        assertTrue(PadGestures.allowedFrom(onButton = false, dx = 0f, dy = -40f))
    }

    @Test fun aPadPullUsesTheVideoPullThresholds() {
        val heightDp = 800f
        // A pad swipe the old one-shot rule minimized (past 30 dp) now springs back when slow and short.
        assertFalse(MinimizeDrag.shouldMinimize(distanceDp = 60f, velocityDpPerSec = 200f, heightDp = heightDp))
        // A quarter of the page or a fast flick still drops it.
        assertTrue(MinimizeDrag.shouldMinimize(distanceDp = 200f, velocityDpPerSec = 0f, heightDp = heightDp))
        assertTrue(MinimizeDrag.shouldMinimize(distanceDp = 40f, velocityDpPerSec = 1400f, heightDp = heightDp))
        // A pull starts at the pad's 24 dp vertical slop, which is already past the video's fling minimum.
        assertTrue(PadGestures.VERTICAL_SLOP_DP >= MinimizeDrag.MIN_FLING_DP)
    }
}
