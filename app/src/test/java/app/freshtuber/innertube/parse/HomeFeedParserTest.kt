package app.freshtuber.innertube.parse

import app.freshtuber.domain.model.FeedItem
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class HomeFeedParserTest {
    @Test fun `home keeps only recommendations and main continuation`() {
        val raw = checkNotNull(javaClass.getResourceAsStream("/innertube/home_grid.json")).bufferedReader().readText()
        val page = FeedParser.parseHome(Json.parseToJsonElement(raw))
        assertEquals(listOf("recommended"), page.items.filterIsInstance<FeedItem.Video>().map { it.video.id })
        assertEquals("home-more", page.continuation)
        assertEquals(listOf("All", "Music"), page.filters.map { it.title })
    }

    @Test fun `continuation and filter replacement skip shelves too`() {
        for (command in listOf("appendContinuationItemsAction", "reloadContinuationItemsCommand")) {
            val page = FeedParser.parseHome(Json.parseToJsonElement("""
                {"onResponseReceivedActions":[{"$command":{"continuationItems":[
                  {"richItemRenderer":{"content":{"videoRenderer":{"videoId":"next","title":{"simpleText":"Next"}}}}},
                  {"richSectionRenderer":{"content":{"richShelfRenderer":{"contents":[
                    {"videoRenderer":{"videoId":"shelf","title":{"simpleText":"Shelf"}}},
                    {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"shelf-more"}}}}
                  ]}}}},
                  {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"next-page"}}}}
                ]}}]}
            """))
            assertEquals(listOf("next"), page.items.filterIsInstance<FeedItem.Video>().map { it.video.id })
            assertEquals("next-page", page.continuation)
        }
    }

    @Test fun `signed out home stays empty`() {
        val page = FeedParser.parseHome(gzFixture("home_anon"))
        assertTrue(page.items.isEmpty())
        assertNull(page.continuation)
    }
}
