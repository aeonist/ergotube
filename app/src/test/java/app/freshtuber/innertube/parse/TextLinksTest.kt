package app.freshtuber.innertube.parse

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.zip.GZIPInputStream

class TextLinksTest {
    @Test fun timesRespectBoundariesAndHours() {
        assertEquals(3723L, TextLinks.timestamp("1:02:03"))
        assertEquals(5400L, TextLinks.timestamp("90:00"))
        assertNull(TextLinks.timestamp("1:72:00"))
        val spans = TextLinks.scan("🙂 1:23 and 1:02:03, bad 1:90 and name12:34")
        assertEquals(listOf(83L, 3723L), spans.map { it.seconds })
        assertEquals(3, spans.first().start)
    }
    @Test fun endpointsRetainTheFullTargetBehindTruncatedText() {
        val value = "site: example.com/…"
        val source = Json.parseToJsonElement("""{"commandRuns":[{"startIndex":6,"length":13,"onTap":{"innertubeCommand":{"urlEndpoint":{"url":"https://example.com/full/path"}}}}]}""")
        val links = TextLinks.attributed(source, value)
        assertEquals("https://example.com/full/path", links.single().url)
        assertEquals(value.length, links.single().end)
    }
    @Test fun redirectsAreUnwrappedAndUnsafeSchemesRemainPlainText() {
        assertEquals("https://example.com/a", TextLinks.safeUrl("https://www.youtube.com/redirect?q=https%3A%2F%2Fexample.com%2Fa"))
        assertNull(TextLinks.safeUrl("javascript:alert(1)"))
        assertNull(TextLinks.safeUrl("https://www.youtube.com/redirect?q=javascript%3Aalert(1)"))
    }
    @Test fun currentVideoLinksSeekButOtherVideoLinksOpenTheirTarget() {
        val current = TextLinks.scan("https://youtu.be/abc?t=83 2:04 https://youtube.com/watch?v=other&t=20", "abc")
        assertEquals(83L, current[0].seconds)
        assertEquals(124L, current[1].seconds)
        assertEquals("https://youtube.com/watch?v=other&t=20", current[2].url)
        val explicit = listOf(app.freshtuber.domain.model.TextLink(0, 4, url = "https://www.youtube.com/watch?v=abc&t=55s"))
        assertEquals(55L, TextLinks.scan("0:55", "abc", explicit).single().seconds)
    }
    @Test fun savedYouTubeDescriptionKeepsRealUrls() {
        val json = javaClass.getResourceAsStream("/innertube/next.json.gz")!!.use { GZIPInputStream(it).bufferedReader().readText() }
        val details = VideoPageParser.parse("dQw4w9WgXcQ", Json.parseToJsonElement(json))!!
        assertTrue(details.descriptionLinks.any { it.url == "https://linktr.ee/rickastleynever" })
        assertTrue(details.descriptionLinks.all { it.end <= details.description!!.length })
    }
}
