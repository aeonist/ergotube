package app.freshtuber.ui.feed

import app.freshtuber.domain.model.FeedItem
import app.freshtuber.domain.model.VideoSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedRowsTest {
    private fun video(id: String, short: Boolean = false) = FeedItem.Video(VideoSummary(id = id, title = id, isShort = short))

    @Test fun runsOfShortsBecomeShelves() {
        val rows = FeedRows.of(listOf(video("a"), video("s1", true), video("s2", true), video("b"), video("s3", true)), minRun = 1)
        assertEquals(listOf("v:a", "shelf:s1", "v:b", "shelf:s3"), rows.map { it.key })
        assertEquals(listOf("s1", "s2"), (rows[1] as FeedRow.Shelf).shorts.map { it.id })
    }

    @Test fun shortRunsBelowTheMinimumStayItems() {
        val rows = FeedRows.of(listOf(video("s1", true), video("a"), video("s2", true), video("s3", true)), minRun = 2)
        assertEquals(listOf("v:s1", "v:a", "shelf:s2"), rows.map { it.key })
    }

    @Test fun zeroKeepsEverything() {
        val items = listOf(video("s1", true), video("s2", true))
        assertEquals(items.map { it.key }, FeedRows.of(items, minRun = 0).map { it.key })
    }

    @Test fun wideScreensPutVideosSideBySideButKeepShelvesWhole() {
        val rows = FeedRows.grid(FeedRows.of(listOf(video("a"), video("b"), video("c"), video("s1", true), video("s2", true), video("d")), minRun = 2), columns = 2)
        assertEquals(listOf("grid:v:a", "grid:v:c", "shelf:s1", "grid:v:d"), rows.map { it.key })
        assertEquals(listOf("v:a", "v:b"), (rows[0] as FeedRow.Grid).items.map { it.key })
        assertEquals(6, FeedRows.grid(FeedRows.of(listOf(video("a")), 2), columns = 1).size + 5)
    }
}
