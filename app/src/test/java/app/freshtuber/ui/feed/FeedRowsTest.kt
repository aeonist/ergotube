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
}
