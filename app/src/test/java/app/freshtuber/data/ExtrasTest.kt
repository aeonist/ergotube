package app.freshtuber.data

import app.freshtuber.data.extras.DeArrowRules
import app.freshtuber.data.shorts.ShortsSequenceParser
import app.freshtuber.innertube.parse.gzFixture
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeArrowRulesTest {

    @Test
    fun `picks the first replacement the community did not vote down`() {
        val entry = Json.parseToJsonElement(
            """
            {"titles":[
               {"title":"Bad title","original":false,"votes":-2,"locked":false},
               {"title":"How the >iPhone 17 battery really holds up","original":false,"votes":3,"locked":false},
               {"title":"ORIGINAL CLICKBAIT!!!","original":true,"votes":0,"locked":false}],
             "thumbnails":[
               {"timestamp":null,"original":true,"votes":0,"locked":false},
               {"timestamp":42.5,"original":false,"votes":0,"locked":false}]}
            """.trimIndent(),
        )
        val branding = DeArrowRules.pick("abc", entry)
        assertEquals("How the iPhone 17 battery really holds up", branding.title)
        assertEquals("https://dearrow-thumb.ajay.app/api/v1/getThumbnail?videoID=abc&time=42.5", branding.thumbnailUrl)
    }

    @Test
    fun `locked submissions win even with negative votes, originals never`() {
        val entry = Json.parseToJsonElement(
            """{"titles":[{"title":"Locked one","original":false,"votes":-5,"locked":true}],
                "thumbnails":[{"timestamp":10,"original":true,"votes":9,"locked":true}]}""",
        )
        val branding = DeArrowRules.pick("v", entry)
        assertEquals("Locked one", branding.title)
        assertNull(branding.thumbnailUrl)
    }

    @Test
    fun `missing entry gives nothing`() {
        val branding = DeArrowRules.pick("v", null)
        assertNull(branding.title)
        assertNull(branding.thumbnailUrl)
    }

    @Test
    fun `case markers are removed`() {
        assertEquals("Why this works", DeArrowRules.cleanTitle("Why >this works"))
        assertEquals("iOS tips", DeArrowRules.cleanTitle(">iOS tips"))
        assertEquals("a > b", DeArrowRules.cleanTitle("a > b"))
    }
}

class ChannelIdFromUrlTest {

    @Test
    fun `extracts the bare channel id`() {
        assertEquals(
            "UCuAXFkgsw1L7xaCfnd5JJOw",
            app.freshtuber.data.extractor.channelIdFromUrl("https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw"),
        )
        assertNull(app.freshtuber.data.extractor.channelIdFromUrl("https://www.youtube.com/@RickAstleyYT"))
        assertNull(app.freshtuber.data.extractor.channelIdFromUrl(null))
    }
}

class ShortsSequenceParserTest {

    @Test
    fun `sequence gives short ids with thumbnails and a next token`() {
        val page = ShortsSequenceParser.parse(gzFixture("reel_seq"))
        assertEquals(25, page.items.size)
        assertEquals("8oAwgW1CK24", page.items.first().videoId)
        assertTrue(page.items.all { it.thumbnailUrl != null })
        assertNotNull(page.next)
    }
}
