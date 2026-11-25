package app.freshtuber.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinimizeDragTest {
    private val height = 800f

    @Test fun slowShortDragSpringsBack() {
        assertFalse(MinimizeDrag.shouldMinimize(distanceDp = 120f, velocityDpPerSec = 300f, heightDp = height))
    }

    @Test fun pastQuarterOfTheHeightMinimizes() {
        assertTrue(MinimizeDrag.shouldMinimize(distanceDp = 210f, velocityDpPerSec = 0f, heightDp = height))
    }

    @Test fun fastShortFlickMinimizes() {
        assertTrue(MinimizeDrag.shouldMinimize(distanceDp = 40f, velocityDpPerSec = 1500f, heightDp = height))
        // A fast fling counts from anywhere on the page, the bottom included.
        assertTrue(MinimizeDrag.shouldMinimize(distanceDp = 20f, velocityDpPerSec = 950f, heightDp = height, roomDp = 60f))
        assertTrue(MinimizeDrag.shouldMinimize(distanceDp = 20f, velocityDpPerSec = 950f, heightDp = height, roomDp = 700f))
    }

    @Test fun aPullFromNearTheBottomNeedsOnlyPartOfTheRoomBelowTheFinger() {
        // Start at 85 % of the height: 120 dp below the finger, a quarter of the page (200 dp)
        // would be out of reach. Half the room is 60 dp, raised to the 72 dp minimum.
        val room = height * 0.15f
        assertEquals(72f, MinimizeDrag.closeDistanceDp(height, room, 1f), 0.01f)
        assertTrue(MinimizeDrag.shouldMinimize(distanceDp = 80f, velocityDpPerSec = 100f, heightDp = height, roomDp = room))
        assertFalse(MinimizeDrag.shouldMinimize(distanceDp = 60f, velocityDpPerSec = 100f, heightDp = height, roomDp = room))
    }

    @Test fun aTinySlowDragNeverMinimizes() {
        for (room in listOf(40f, 120f, 400f, 800f)) {
            assertFalse(MinimizeDrag.shouldMinimize(distanceDp = 20f, velocityDpPerSec = 150f, heightDp = height, roomDp = room))
        }
    }

    @Test fun aPullFromTheTopNeedsTheUsualQuarter() {
        val room = height - 60f
        assertEquals(200f, MinimizeDrag.closeDistanceDp(height, room, 1f), 0.01f)
        assertFalse(MinimizeDrag.shouldMinimize(distanceDp = 150f, velocityDpPerSec = 0f, heightDp = height, roomDp = room))
        assertTrue(MinimizeDrag.shouldMinimize(distanceDp = 205f, velocityDpPerSec = 0f, heightDp = height, roomDp = room))
    }

    @Test fun theCloseDistanceAlwaysFitsInTheRoom() {
        // At the very bottom (50 dp left) the 72 dp minimum would be unreachable: 80 % of the room.
        assertEquals(40f, MinimizeDrag.closeDistanceDp(height, 50f, 1f), 0.01f)
        for (room in listOf(60f, 100f, 150f, 300f, 800f)) {
            assertTrue(MinimizeDrag.closeDistanceDp(height, room, 1f) < room)
        }
        // In px the dp minimums scale with the density.
        assertEquals(72f * 3f, MinimizeDrag.closeDistanceDp(height * 3f, 120f * 3f, 3f), 0.01f)
    }

    @Test fun thePageLooksAsPulledAtEveryCloseDistance() {
        // Right at the release distance the page has the same shrink and fade, wherever it started.
        assertEquals(MinimizeDrag.DISTANCE_SHARE, MinimizeDrag.progressFor(72f, 72f), 0.0001f)
        assertEquals(MinimizeDrag.DISTANCE_SHARE, MinimizeDrag.progressFor(200f, 200f), 0.0001f)
        assertEquals(MinimizeDrag.progressFor(36f, 72f), MinimizeDrag.progressFor(100f, 200f), 0.0001f)
        assertEquals(1f, MinimizeDrag.progressFor(10_000f, 72f), 0f)
        assertEquals(0f, MinimizeDrag.progressFor(10f, 0f), 0f)
    }

    @Test fun upwardNeverMinimizes() {
        assertFalse(MinimizeDrag.shouldMinimize(distanceDp = -200f, velocityDpPerSec = 3000f, heightDp = height))
        assertFalse(MinimizeDrag.shouldMinimize(distanceDp = 0f, velocityDpPerSec = 0f, heightDp = height))
        assertFalse(MinimizeDrag.shouldMinimize(distanceDp = -100f, velocityDpPerSec = 2000f, heightDp = height, roomDp = 100f))
    }

    @Test fun flickingBackUpCancelsAFarDrag() {
        assertFalse(MinimizeDrag.shouldMinimize(distanceDp = 300f, velocityDpPerSec = -900f, heightDp = height))
    }

    @Test fun pulledToTheThresholdThePageRecedes() {
        assertEquals(1f, MinimizeDrag.scaleFor(0f), 0.0001f)
        assertEquals(1f, MinimizeDrag.alphaFor(0f), 0.0001f)
        assertEquals(0.94f, MinimizeDrag.scaleFor(1f), 0.0001f)
        assertEquals(0.7f, MinimizeDrag.alphaFor(1f), 0.0001f)
        // Past the threshold it stays there.
        assertEquals(1f, MinimizeDrag.pullFor(500f, 100f), 0f)
        assertEquals(0.5f, MinimizeDrag.pullFor(50f, 100f), 0.0001f)
    }

    @Test fun releasedItFadesAwayWhereverItWas() {
        for (pull in listOf(0f, 0.3f, 1f)) {
            assertEquals(0.9f, MinimizeDrag.scaleFor(pull, 1f), 0.0001f)
            assertEquals(0f, MinimizeDrag.alphaFor(pull, 1f), 0.0001f)
        }
        assertTrue(MinimizeDrag.alphaFor(1f, 0.5f) < 0.7f)
    }

    @Test fun releasedThePageKeepsTheFingersSpeed() {
        val h = 2400f
        // Ease-out starts at twice the average speed: travel * 2 / duration == finger speed.
        val travel = MinimizeDrag.exitTravel(3000f, 600f, h)
        assertEquals(3000f, travel * 2f / (MinimizeDrag.DISMISS_MS / 1000f), 1f)
        // A slow release still glides a little, and nothing goes past the bottom.
        assertEquals(h * MinimizeDrag.MIN_EXIT_SHARE, MinimizeDrag.exitTravel(0f, 600f, h), 0.01f)
        assertEquals(100f, MinimizeDrag.exitTravel(20000f, h - 100f, h), 0.01f)
        assertEquals(0f, MinimizeDrag.easeOut(0f), 0f)
        assertEquals(1f, MinimizeDrag.easeOut(1f), 0f)
    }

    @Test fun resistanceLagsTheFingerSlightly() {
        val pulled = MinimizeDrag.resist(200f, 2000f)
        assertTrue(pulled < 200f && pulled > 180f)
        assertEquals(0f, MinimizeDrag.resist(-50f, 2000f), 0f)
    }

    @Test fun onlyMostlyDownwardMovesStartThePull() {
        assertTrue(MinimizeDrag.startsPull(dx = 5f, dy = 30f))
        assertFalse(MinimizeDrag.startsPull(dx = 30f, dy = 20f))
        assertFalse(MinimizeDrag.startsPull(dx = 0f, dy = -30f))
    }

    @Test fun aQuickFlickCountsByItsAverageSpeed() {
        assertEquals(3000f, MinimizeDrag.flingVelocity(0f, 180f, 60), 0.1f)
        assertEquals(500f, MinimizeDrag.flingVelocity(500f, 180f, 900), 0.1f)
    }

    @Test fun aListScrollsUntilItsTopThenThePageTakesTheRestDown() {
        // Before the list: down is the list's.
        assertEquals(0f, MinimizeDrag.listTakes(40f, pulledPx = 0f, afterList = false), 0f)
        // After the list: what it could not scroll down pulls the page; up stays with the list.
        assertEquals(25f, MinimizeDrag.listTakes(25f, pulledPx = 0f, afterList = true), 0f)
        assertEquals(0f, MinimizeDrag.listTakes(-25f, pulledPx = 0f, afterList = true), 0f)
    }

    @Test fun movingUpLiftsAPulledPageBeforeTheListScrolls() {
        assertEquals(-30f, MinimizeDrag.listTakes(-30f, pulledPx = 100f, afterList = false), 0f)
        // Only as much as the page was pulled; the rest scrolls the list.
        assertEquals(-20f, MinimizeDrag.listTakes(-50f, pulledPx = 20f, afterList = false), 0f)
        assertEquals(0f, MinimizeDrag.listTakes(-50f, pulledPx = 0f, afterList = false), 0f)
    }
}
