package app.freshtuber.innertube.parse

import app.freshtuber.data.feed.FeedRepository
import app.freshtuber.data.feed.FeedSource
import app.freshtuber.domain.model.FeedItem
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class MixAndFeedActionsTest {
    @Test fun `mix opens watch next and retains its seed`() {
        assertEquals(FeedSource.Mix("RDseed", "seed"), FeedRepository.playlist("RDseed", "seed"))
        assertEquals(FeedSource.Browse("VLPLnormal"), FeedRepository.playlist("PLnormal"))
    }

    @Test fun `real mix response yields videos and next page endpoint without related results`() {
        val page = FeedParser.parseMix(gzFixture("mix_next"))
        val videos = page.items.filterIsInstance<FeedItem.Video>()
        assertEquals(25, videos.size)
        assertEquals("dQw4w9WgXcQ", videos.first().video.id)
        assertTrue(videos.all { it.video.title.isNotBlank() })
        val next = Json.parseToJsonElement(checkNotNull(page.continuation)).jsonObject
        assertEquals("RDdQw4w9WgXcQ", next["playlistId"]?.jsonPrimitive?.content)
        assertEquals(videos.last().video.id, next["videoId"]?.jsonPrimitive?.content)
        assertTrue("playlistIndex" in next)
        assertFalse("index" in next)
    }

    @Test fun `not interested replays only the matching menu command`() {
        val fixture = javaClass.getResource("/innertube/feed_actions.json")!!.readText()
        val video = (FeedParser.parse(Json.parseToJsonElement(fixture)).items.single() as FeedItem.Video).video
        assertEquals("feedback", video.notInterested?.path)
        assertEquals("fixture-not-interested", video.notInterested?.body?.get("feedbackTokens")?.jsonArray?.single()?.jsonPrimitive?.content)
    }
    @Test fun `mix continuation contains more distinct videos`() {
        val first = FeedParser.parseMix(gzFixture("mix_next")).items.map { it.key }.toSet()
        val more = FeedParser.parseMix(gzFixture("mix_more"))
        assertEquals(25, more.items.size)
        assertTrue(more.items.any { it.key !in first })
        assertNotNull(more.continuation)
    }

    @Test fun `view model menu commands match the same not interested icon`() {
        val fixture = javaClass.getResource("/innertube/feed_actions.json")!!.readText()
            .replace("menuServiceItemRenderer", "listItemViewModel").replace("iconType", "iconName")
        val video = (FeedParser.parse(Json.parseToJsonElement(fixture)).items.single() as FeedItem.Video).video
        assertEquals("fixture-not-interested", video.notInterested?.body?.get("feedbackTokens")?.jsonArray?.single()?.jsonPrimitive?.content)
    }

}
