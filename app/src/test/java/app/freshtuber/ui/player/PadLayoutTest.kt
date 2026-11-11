package app.freshtuber.ui.player

import app.freshtuber.ui.feed.FanMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PadLayoutTest {
    /** Inner width of the pad: the screen without the pad's 14 dp margins and 6 dp padding on each side. */
    private fun inner(screenDp: Float) = screenDp - 2 * 14 - 2 * 6

    @Test fun everyButtonKeepsAComfortableWidthOnFoldedAndRegularPhones() {
        // 280: the narrowest folded cover screens; 344: Galaxy Z Fold cover; 360-412: regular phones.
        listOf(280f, 300f, 317f, 344f, 360f, 393f, 412f).forEach { screen ->
            val width = inner(screen)
            assertTrue("side buttons at $screen dp", PadLayout.sideButton(width) >= 36f)
            assertTrue("skip zones at $screen dp are ${PadLayout.zoneWidth(width)}", PadLayout.zoneWidth(width) >= 26f)
            assertTrue("play zone at $screen dp", PadLayout.zoneWidth(width) * 1.2f >= 32f)
        }
    }

    @Test fun theRemoteIsTwoRows() {
        assertTrue(PadLayout.TRACK_ROW_DP + PadLayout.BUTTON_ROW_DP <= 82)
    }

    @Test fun tapsAreJudgedByThePlayButtonsRealPlace() {
        assertEquals(PadZone.Back, PadGestures.zone(120f, 150f, 210f))
        assertEquals(PadZone.Play, PadGestures.zone(180f, 150f, 210f))
        assertEquals(PadZone.Forward, PadGestures.zone(240f, 150f, 210f))
    }

    @Test fun fanItemsComeOutOneAfterAnotherAndAllArrive() {
        val count = 6
        val half = (0 until count).map { FanMotion.itemProgress(0.5f, it, count) }
        assertEquals(half.sortedDescending(), half)
        assertTrue(half.first() > half.last())
        (0 until count).forEach { assertEquals(1f, FanMotion.itemProgress(1f, it, count), 0.0001f) }
        (0 until count).forEach { assertEquals(0f, FanMotion.itemProgress(0f, it, count), 0.0001f) }
        assertEquals(1f, FanMotion.backOut(1f), 0.0001f)
        assertTrue(FanMotion.backOut(0.8f) > 1f)
    }
}
