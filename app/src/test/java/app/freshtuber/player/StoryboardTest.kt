package app.freshtuber.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryboardTest {
    private val board = Storyboard(listOf("p0", "p1"), total = 30, perRow = 5, perColumn = 5, frameWidth = 160, frameHeight = 90, frameMs = 2000)

    @Test fun picksTheFrameCellAndPage() {
        assertEquals(Storyboard.Frame("p0", 0, 0, 160, 90), board.frameAt(0))
        assertEquals(Storyboard.Frame("p0", 160 * 2, 90, 160, 90), board.frameAt(7 * 2000L + 500))
        assertEquals(Storyboard.Frame("p1", 0, 0, 160, 90), board.frameAt(25 * 2000L))
        // Past the end it stays on the last frame.
        assertEquals(Storyboard.Frame("p1", 160 * 4, 0, 160, 90), board.frameAt(10_000_000L))
        assertNull(board.copy(urls = emptyList()).frameAt(0))
    }

    @Test fun picksTheSharpestSmallSet() {
        val small = board.copy(frameWidth = 80)
        val big = board.copy(frameWidth = 640)
        assertEquals(board, Storyboard.pick(listOf(small, board, big)))
        assertEquals(big, Storyboard.pick(listOf(big)))
        assertNull(Storyboard.pick(emptyList()))
    }

    @Test fun chaptersAreSortedAndStartAtZero() {
        val raw = listOf(Chapter(120, "B"), Chapter(5, "A"), Chapter(120, "dup"), Chapter(900, "outside"), Chapter(300, " "))
        assertEquals(listOf(Chapter(0, "A"), Chapter(120, "B")), Chapter.normalized(raw, 600))
        assertEquals(emptyList<Chapter>(), Chapter.normalized(listOf(Chapter(0, "only")), 600))
        assertEquals(Chapter(120, "B"), Chapter.at(listOf(Chapter(0, "A"), Chapter(120, "B")), 200))
    }
}
