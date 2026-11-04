package app.freshtuber.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGestureMathTest {
    @Test fun tapsOnOneSideAddUp() {
        val burst = SeekBurst(windowMs = 900)
        assertEquals(10, burst.add(10, 0))
        assertEquals(20, burst.add(10, 500))
        assertEquals(30, burst.add(10, 1300))
        // The other side starts over.
        assertEquals(-10, burst.add(-10, 1400))
        assertEquals(-20, burst.add(-10, 1500))
        // A long pause starts over.
        assertEquals(-10, burst.add(-10, 3000))
    }

    @Test fun scrubSpanIsAThirdOfTheVideoWithinLimits() {
        assertEquals(90f, Scrub.spanSeconds(60f), 0.01f)
        assertEquals(150f, Scrub.spanSeconds(500f), 0.01f)
        assertEquals(600f, Scrub.spanSeconds(10_800f), 0.01f)
    }

    @Test fun liftingTheFingerScrubsFiveTimesFiner() {
        val coarse = Scrub.step(100f, 100f, 1000f, 1000f, fine = false)
        val fine = Scrub.step(100f, 100f, 1000f, 1000f, fine = true)
        assertEquals(130f, coarse, 0.01f)
        assertEquals(106f, fine, 0.01f)
        assertTrue(Scrub.isFine(startY = 500f, y = 400f, liftPx = 48f))
        assertFalse(Scrub.isFine(startY = 500f, y = 470f, liftPx = 48f))
        assertEquals(0f, Scrub.step(5f, -500f, 1000f, 1000f, fine = false), 0.01f)
    }

    @Test fun padZonesAndDragDecision() {
        assertEquals(PadZone.Back, PadGestures.zone(0.1f))
        assertEquals(PadZone.Play, PadGestures.zone(0.5f))
        assertEquals(PadZone.Forward, PadGestures.zone(0.9f))
        assertEquals(PadMove.Seek, PadGestures.decide(20f, 5f))
        assertEquals(PadMove.Vertical, PadGestures.decide(5f, -30f))
        assertNull(PadGestures.decide(6f, 6f))
    }

    @Test fun signedSeekLabels() {
        assertEquals("+20", signedSeek(20))
        assertEquals("−1:20", signedSeek(-80))
    }

    @Test fun fanSitsInTheHandCornerAndRunsClockwise() {
        val right = FanGeometry(800f, 400f, left = false)
        assertEquals(296f, right.radius, 0.01f)
        // Right hand: u = 0 lies along the bottom toward the left, u = 1 straight up.
        assertEquals(800f - 100f, right.position(100f, 0f).x, 0.01f)
        assertEquals(400f - 100f, right.position(100f, 1f).y, 0.01f)
        val left = FanGeometry(800f, 400f, left = true)
        // Left hand: u = 0 straight up, u = 1 along the bottom toward the right.
        assertEquals(400f - 100f, left.position(100f, 0f).y, 0.01f)
        assertEquals(100f, left.position(100f, 1f).x, 0.01f)
        for (geo in listOf(right, left)) for (u in listOf(0.1f, 0.5f, 0.9f)) {
            val p = geo.position(150f, u)
            val (r, back) = geo.polar(p.x, p.y)
            assertEquals(150f, r, 0.01f)
            assertEquals(u, back, 0.001f)
        }
        assertTrue(right.inFan(780f, 390f))
        assertFalse(right.inFan(100f, 390f))
    }

    @Test fun arcMappingLeavesMarginsAtBothEnds() {
        assertEquals(FanGeometry.U0, FanGeometry.toArc(0f), 0.0001f)
        assertEquals(FanGeometry.U1, FanGeometry.toArc(1f), 0.0001f)
        assertEquals(0.5f, FanGeometry.fromArc(FanGeometry.toArc(0.5f)), 0.0001f)
        assertEquals(0f, FanGeometry.fromArc(0f), 0.0001f)
    }

    @Test fun buttonFullscreenIsLeftByTurningThePhoneBack() {
        // Not yet held sideways: staying upright keeps fullscreen.
        assertEquals(OrientationLock.Landscape to false,
            FullscreenOrientation.next(OrientationLock.Landscape, false, PhysicalOrientation.Portrait))
        val (lock, seen) = FullscreenOrientation.next(OrientationLock.Landscape, false, PhysicalOrientation.Landscape)
        assertEquals(OrientationLock.Landscape, lock)
        assertTrue(seen)
        assertEquals(OrientationLock.None to false, FullscreenOrientation.next(lock, seen, PhysicalOrientation.Portrait))
        // Closed in landscape: portrait holds until the phone is upright.
        assertEquals(OrientationLock.Portrait to false, FullscreenOrientation.next(OrientationLock.Portrait, false, PhysicalOrientation.Landscape))
        assertEquals(OrientationLock.None to false, FullscreenOrientation.next(OrientationLock.Portrait, false, PhysicalOrientation.Portrait))
        assertEquals(PhysicalOrientation.Landscape, FullscreenOrientation.physical(90))
        assertEquals(PhysicalOrientation.Portrait, FullscreenOrientation.physical(350))
        assertNull(FullscreenOrientation.physical(45))
        assertNull(FullscreenOrientation.physical(-1))
    }
}
