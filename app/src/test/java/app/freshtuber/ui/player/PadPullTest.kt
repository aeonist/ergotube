package app.freshtuber.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pad keeps scrubbing, taps and swipe up; a downward drag from it is the page-wide pull. */
class PadPullTest {
    @Test fun padGesturesStayAsTheyWere() {
        assertEquals(PadMove.Seek, PadGestures.decide(30f, 12f))      // scrubbing
        assertEquals(PadMove.Vertical, PadGestures.decide(3f, -40f))  // swipe up to full screen
        assertNull(PadGestures.decide(2f, 15f))                       // still a tap or a hold
    }

    @Test fun aDownwardDragFromThePadIsThePagePullBeforeThePadDecides() {
        // The page takes a mostly downward move at touch slop (a few dp), well before the pad's
        // own 24 dp vertical slop, so the pad never sees a downward swipe of its own.
        assertTrue(MinimizeDrag.startsPull(dx = 2f, dy = 9f))
        assertNull(PadGestures.decide(2f / 2.75f, 9f / 2.75f))
        // Scrubbing sideways is never a pull.
        assertFalse(MinimizeDrag.startsPull(dx = 12f, dy = 4f))
    }

    @Test fun aPullFromThePadNearTheBottomIsReachable() {
        val heightDp = 800f
        // The pad's main row sits at about 85 % of the page: 120 dp of room below the finger.
        val room = heightDp * 0.15f
        val close = MinimizeDrag.closeDistanceDp(heightDp, room, 1f)
        assertTrue(close < room)
        assertTrue(MinimizeDrag.shouldMinimize(distanceDp = 80f, velocityDpPerSec = 200f, heightDp = heightDp, roomDp = room))
        assertFalse(MinimizeDrag.shouldMinimize(distanceDp = 30f, velocityDpPerSec = 200f, heightDp = heightDp, roomDp = room))
    }
}
