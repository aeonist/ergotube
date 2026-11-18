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
    }

    @Test fun upwardNeverMinimizes() {
        assertFalse(MinimizeDrag.shouldMinimize(distanceDp = -200f, velocityDpPerSec = 3000f, heightDp = height))
        assertFalse(MinimizeDrag.shouldMinimize(distanceDp = 0f, velocityDpPerSec = 0f, heightDp = height))
    }

    @Test fun flickingBackUpCancelsAFarDrag() {
        assertFalse(MinimizeDrag.shouldMinimize(distanceDp = 300f, velocityDpPerSec = -900f, heightDp = height))
    }

    @Test fun pageShrinksToNinetyPercentOverFortyPercentAndFades() {
        assertEquals(1f, MinimizeDrag.scaleFor(0f), 0.0001f)
        assertEquals(0.95f, MinimizeDrag.scaleFor(0.2f), 0.0001f)
        assertEquals(0.9f, MinimizeDrag.scaleFor(0.4f), 0.0001f)
        assertEquals(0.9f, MinimizeDrag.scaleFor(0.9f), 0.0001f)
        assertEquals(1f, MinimizeDrag.alphaFor(0f), 0.0001f)
        assertTrue(MinimizeDrag.alphaFor(0.3f) < 1f)
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
}
