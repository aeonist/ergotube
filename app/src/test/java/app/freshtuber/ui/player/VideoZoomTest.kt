package app.freshtuber.ui.player

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoZoomTest {
    @org.junit.Before
    fun forget() { VideoZoom.keepFilled = false }

    // A 20:9 phone held sideways and a 16:9 video: the picture leaves room at the left and right.
    private val screen = Offset(2400f, 1080f)
    private val aspect = 16f / 9f
    private val center = screen / 2f
    private val fill = 2400f / (1080f * aspect)

    @Test
    fun fillScaleCoversTheLongSide() {
        assertEquals(1.25f, VideoZoom.fillScale(screen, aspect), 0.001f)
        assertEquals(1f, VideoZoom.fillScale(Offset(1920f, 1080f), aspect), 0.001f)
    }

    @Test
    fun pinchNearFillSticksToTheEdges() {
        val zoom = VideoZoom()
        assertTrue(zoom.transform(fill * 0.96f, Offset.Zero, center, screen, aspect))
        assertEquals(fill, zoom.scale, 0.0001f)
        assertEquals(0f, zoom.offset.x, 0.0001f)
        // Still inside the hold: no second tick.
        assertFalse(zoom.transform(1.05f, Offset.Zero, center, screen, aspect))
        assertEquals(fill, zoom.scale, 0.0001f)
    }

    @Test
    fun pinchingPastTheHoldLeavesIt() {
        val zoom = VideoZoom()
        zoom.transform(fill, Offset.Zero, center, screen, aspect)
        zoom.transform(1.2f, Offset.Zero, center, screen, aspect)
        assertEquals(fill * 1.2f, zoom.scale, 0.0001f)
        assertFalse(zoom.filled)
    }

    @Test
    fun releasedAtFillTheNextPinchStartsThere() {
        val zoom = VideoZoom()
        zoom.transform(fill * 1.05f, Offset.Zero, center, screen, aspect)
        zoom.release()
        assertEquals(fill, zoom.scale, 0.0001f)
        zoom.transform(1.1f, Offset.Zero, center, screen, aspect)
        assertEquals(fill * 1.1f, zoom.scale, 0.0001f)
    }

    @Test
    fun barelyZoomedGoesBackToFit() {
        val zoom = VideoZoom()
        zoom.transform(1.03f, Offset(40f, 0f), center, screen, aspect)
        zoom.release()
        assertEquals(1f, zoom.scale, 0f)
        assertEquals(Offset.Zero, zoom.offset)
    }

    @Test
    fun aPictureThatAlreadyFillsDoesNotStick() {
        val zoom = VideoZoom()
        assertFalse(zoom.transform(1.02f, Offset.Zero, Offset(960f, 540f), Offset(1920f, 1080f), aspect))
        assertEquals(1.02f, zoom.scale, 0.0001f)
    }

    @Test
    fun aFilledPictureIsHeldAtTheCentre() {
        val zoom = VideoZoom()
        zoom.transform(fill * 0.97f, Offset(0f, 30f), Offset(center.x, center.y + 200f), screen, aspect)
        assertTrue(zoom.filled)
        assertEquals(Offset.Zero, zoom.offset)
    }

    @Test
    fun aZoomedPictureSnapsToTheCentreLine() {
        val zoom = VideoZoom()
        zoom.transform(fill * 1.5f, Offset.Zero, center, screen, aspect)
        zoom.transform(1f, Offset(0f, 20f), center, screen, aspect)
        assertEquals(0f, zoom.offset.y, 0f)
        // A slow drag in small steps still leaves the centre once it is far enough.
        repeat(10) { zoom.transform(1f, Offset(0f, 5f), center, screen, aspect) }
        assertEquals(70f, zoom.offset.y, 0.001f)
    }

    @Test
    fun theNextPictureOpensFilledAfterAZoomedOne() {
        val first = VideoZoom()
        first.transform(fill, Offset.Zero, center, screen, aspect)
        first.release()
        val next = VideoZoom()
        next.layout(screen, aspect)
        assertEquals(fill, next.scale, 0.0001f)
        assertTrue(next.filled)
        // Pinched back to fit: the one after opens fitted.
        next.transform(0.5f, Offset.Zero, center, screen, aspect)
        next.release()
        val third = VideoZoom()
        third.layout(screen, aspect)
        assertEquals(1f, third.scale, 0f)
    }

    @Test
    fun anOpenedFilledPictureFollowsItsShape() {
        VideoZoom.keepFilled = true
        val zoom = VideoZoom()
        zoom.layout(screen, aspect)
        assertEquals(fill, zoom.scale, 0.0001f)
        // The real picture turns out to be as wide as the screen: nothing to fill.
        zoom.layout(screen, screen.x / screen.y)
        assertEquals(1f, zoom.scale, 0f)
        assertFalse(zoom.filled)
    }
}
