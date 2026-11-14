package app.freshtuber.innertube.parse

import app.freshtuber.domain.model.FeedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Channel `browse` responses: MrBeast videos (hl=ru) and MKBHD Home/Shorts/Playlists (hl=en). */
class ChannelTabsParserTest {

    @Test
    fun `tabs keep YouTube's order and skip what the app cannot list`() {
        val tabs = ChannelTabsParser.parse(gzFixture("channel_shorts"))
        assertEquals(
            listOf(ChannelTabKind.Home, ChannelTabKind.Videos, ChannelTabKind.Shorts, ChannelTabKind.Live, ChannelTabKind.Playlists),
            tabs.map { it.kind },
        )
        assertEquals(ChannelTabKind.Shorts, tabs.single { it.selected }.kind)
    }

    @Test
    fun `kind comes from the url, not the localized title`() {
        val tabs = ChannelTabsParser.parse(gzFixture("channel_videos"))
        assertEquals(listOf(ChannelTabKind.Home, ChannelTabKind.Videos, ChannelTabKind.Shorts), tabs.map { it.kind })
        assertEquals("Видео", tabs.single { it.selected }.title)
    }

    @Test
    fun `params are sent percent-decoded`() {
        val tabs = ChannelTabsParser.parse(gzFixture("channel_videos")).associateBy { it.kind }
        assertEquals("EgZ2aWRlb3PyBgQKAjoA", tabs.getValue(ChannelTabKind.Videos).params)
        assertEquals("EgZzaG9ydHPyBgUKA5oBAA==", tabs.getValue(ChannelTabKind.Shorts).params)
        assertEquals("EghmZWF0dXJlZPIGBAoCMgA=", tabs.getValue(ChannelTabKind.Home).params)
        assertEquals("a+b/c=", ChannelTabsParser.decode("a+b/c%3D"))
    }

    @Test
    fun `each tab's list parses into cards`() {
        val shorts = FeedParser.parse(gzFixture("channel_shorts")).items.filterIsInstance<FeedItem.Video>()
        assertTrue(shorts.size >= 30)
        assertTrue(shorts.all { it.video.isShort })

        val playlists = FeedParser.parse(gzFixture("channel_playlists")).items.filterIsInstance<FeedItem.Playlist>()
        assertTrue(playlists.size >= 10)
        assertTrue(playlists.all { it.playlist.title.isNotBlank() })

        val home = FeedParser.parse(gzFixture("channel_featured")).items
        assertTrue(home.count { it is FeedItem.Video } >= 20)
    }
}
