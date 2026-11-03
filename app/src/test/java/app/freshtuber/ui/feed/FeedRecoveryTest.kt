package app.freshtuber.ui.feed

import app.freshtuber.domain.model.*
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FeedRecoveryTest {
    @Test fun `failed refresh preserves an already visible feed`() = runTest {
        var offline = false
        val row = FeedItem.Video(VideoSummary("video", "Cached video"))
        val pager = FeedPager(backgroundScope, first = { if (offline) throw IOException() else FeedPage(listOf(row)) }, more = { FeedPage(emptyList()) })
        pager.refresh(); runCurrent()
        assertEquals(listOf(row), pager.state.value.items)
        offline = true
        pager.refresh(); runCurrent()
        assertEquals(listOf(row), pager.state.value.items)
        assertEquals(LoadError.Network, pager.state.value.error)
        assertFalse(pager.state.value.loading)
    }
    @Test fun `failed filter switch never mislabels old results as the new filter`() = runTest {
        val row = FeedItem.Video(VideoSummary("video", "Old filter"))
        val pager = FeedPager(backgroundScope, first = { FeedPage(listOf(row)) }, more = { throw IOException() })
        pager.refresh(); runCurrent()
        pager.selectFilter(FeedFilter("New filter", "next", false)); runCurrent()
        assertTrue(pager.state.value.items.isEmpty())
        assertEquals(LoadError.Network, pager.state.value.error)
    }
}
