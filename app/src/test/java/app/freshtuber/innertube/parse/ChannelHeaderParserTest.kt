package app.freshtuber.innertube.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.serialization.json.buildJsonObject

/** The channel header line: @handle, subscribers and videos, as YouTube words them. */
class ChannelHeaderParserTest {

    @Test
    fun `subscribers and videos in the interface language`() {
        val facts = ChannelHeaderParser.facts(gzFixture("channel_videos"))!!
        assertEquals("@MrBeast", facts.handle)
        assertEquals("524 млн подписчиков", facts.subscribers)
        assertEquals("1 тыс. видео", facts.videos)
    }

    @Test
    fun `english header`() {
        assertEquals(ChannelFacts("@mkbhd", "21.4M subscribers", "1.8K videos"), ChannelHeaderParser.facts(gzFixture("channel_featured")))
    }

    @Test
    fun `no header, no facts`() {
        assertNull(ChannelHeaderParser.facts(buildJsonObject { }))
    }
}
