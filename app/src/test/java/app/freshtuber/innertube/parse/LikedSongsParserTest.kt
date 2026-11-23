package app.freshtuber.innertube.parse

import app.freshtuber.domain.model.FeedItem
import app.freshtuber.innertube.InnerTubeException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class LikedSongsParserTest {
    @Test fun readsOnlyTheAccountPlaylistAndItsContinuation() {
        val text = javaClass.getResource("/innertube/music_likes.json")!!.readText()
        val page = LikedSongsParser.parse(Json.parseToJsonElement(text).jsonObject)
        val song = (page.items.single() as FeedItem.Video).video
        assertEquals("song-id", song.id)
        assertEquals("Artist", song.channelName)
        assertEquals(184L, song.durationSeconds)
        assertEquals("next-page", page.continuation)
    }

    @Test fun emptyLastPageIsValid() {
        val text = """{"onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[]}}]}"""
        val page = LikedSongsParser.parse(Json.parseToJsonElement(text).jsonObject)
        assertTrue(page.items.isEmpty())
        assertNull(page.continuation)
    }

    @Test(expected = InnerTubeException.BadResponse::class)
    fun unrecognizedResponseMustNotEraseTheCachedPlaylist() {
        LikedSongsParser.parse(Json.parseToJsonElement("{}").jsonObject)
    }
}
