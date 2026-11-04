package app.freshtuber.ui.feed

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FanMathTest {
    private val dead = 30f

    @Test fun flicksPickTheFourDirections() {
        assertEquals(FanAction.WatchLater, FanMath.pick(0f, -100f, dead))
        assertEquals(FanAction.Download, FanMath.pick(100f, 0f, dead))
        assertEquals(FanAction.NotInterested, FanMath.pick(-100f, 0f, dead))
        assertEquals(FanAction.Share, FanMath.pick(0f, 100f, dead))
    }

    @Test fun flicksNeedNotBePrecise() {
        // 40° off straight up still counts as up.
        assertEquals(FanAction.WatchLater, FanMath.pick(64f, -77f, dead))
        assertEquals(FanAction.Download, FanMath.pick(90f, -40f, dead))
    }

    @Test fun deadZoneAndDiagonalsPickNothing() {
        assertNull(FanMath.pick(10f, -20f, dead))
        // Exactly between up and right is 45° from both, outside the 43° tolerance.
        assertNull(FanMath.pick(100f, -100f, dead))
    }

    @Test fun unavailableActionsAreNeverPicked() {
        val noHide = FanAction.entries.toSet() - FanAction.NotInterested
        assertNull(FanMath.pick(-100f, 0f, dead, available = noHide))
        assertEquals(FanAction.Share, FanMath.pick(0f, 100f, dead, available = noHide))
    }

    @Test fun centerStaysOnScreen() {
        val c = FanMath.clampCenter(5f, 2300f, 1080f, 2400f, 257f, 94f, 252f, 94f)
        assertEquals(94f + 257f, c.x, 0.01f)
        assertEquals(2400f - 94f - 257f, c.y, 0.01f)
        assertEquals(Offset(540f, 1200f), FanMath.clampCenter(540f, 1200f, 1080f, 2400f, 257f, 94f, 252f, 94f))
    }

    @Test fun hostReportsStillMovedAndPicked() {
        val host = FanHost()
        host.start(Offset(500f, 1000f), FanAction.entries.toSet())
        assertEquals(FanResult.Still, host.finish(cancelled = false))

        host.start(Offset(500f, 1000f), FanAction.entries.toSet())
        host.move(Offset(500f, 900f))
        assertEquals(FanResult.Picked(FanAction.WatchLater), host.finish(cancelled = false))

        host.start(Offset(500f, 1000f), FanAction.entries.toSet())
        host.move(Offset(600f, 900f))
        host.move(Offset(500f, 1005f))
        assertEquals("moved back into the dead zone cancels", FanResult.None, host.finish(cancelled = false))

        host.start(Offset(500f, 1000f), FanAction.entries.toSet())
        host.move(Offset(500f, 1200f))
        assertEquals(FanResult.None, host.finish(cancelled = true))
    }
}
