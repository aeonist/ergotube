package app.freshtuber.data.subscriptions

import org.junit.Assert.*
import org.junit.Test

class RssParserTest {
    @Test fun `real anonymous atom feed produces sorted playable videos`() {
        val xml = javaClass.getResource("/rss/channel.xml")!!.readText()
        val videos = RssParser.parse(xml, "UCsXVk37bltHxD1rDPwtNM8Q", "Kurzgesagt")
        assertTrue(videos.size >= 10)
        assertEquals(videos.size, videos.map { it.id }.distinct().size)
        assertTrue(videos.all { it.id.length == 11 && it.title.isNotBlank() && it.publishedAt > 0 })
        assertEquals(videos.sortedByDescending { it.publishedAt }, videos)
        assertTrue(videos.all { it.thumbnailUrl?.startsWith("https://") == true })
        assertTrue(videos.any { it.isShort })
        assertTrue(videos.any { !it.isShort })
    }
    @Test(expected = IllegalArgumentException::class) fun `rss rejects external and internal dtds`() {
        RssParser.parse("<!DOCTYPE feed [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><feed>&x;</feed>", "id", "name")
    }
    @Test fun `malformed entries do not become broken playable rows`() {
        val xml = "<feed xmlns:yt='http://www.youtube.com/xml/schemas/2015'><entry><yt:videoId>bad</yt:videoId><title>Bad</title><published>invalid</published></entry></feed>"
        assertTrue(RssParser.parse(xml, "id", "name").isEmpty())
    }
}
