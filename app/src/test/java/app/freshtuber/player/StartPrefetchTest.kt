package app.freshtuber.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.UriUtil
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test

class StartPrefetchTest {
    private val url = "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1&itag=137&clen=5000000&sig=x"

    private fun track(height: Int, bitrate: Int, itag: Int = height, ranges: SegmentRanges? = SegmentRanges(0, 740, 741, 2100)) =
        VideoTrack(height, 30, "$url&h=$height", "video/mp4", "${height}p", "video/avc", bitrate, 0, itag, "avc1", ranges)

    @Test fun `header and index are one read from the header start`() {
        assertEquals(StartPrefetch.Range(url, 0, 2101), StartPrefetch.header(url, SegmentRanges(0, 740, 741, 2100)))
        // A gap between them is read too, so both of the player's reads hit the cache.
        assertEquals(StartPrefetch.Range(url, 0, 3001), StartPrefetch.header(url, SegmentRanges(0, 740, 900, 3000)))
        assertNull(StartPrefetch.header(url, null))
        assertNull(StartPrefetch.header(url, SegmentRanges(0, 740, 741, StartPrefetch.MAX_HEADER_BYTES + 10)))
    }

    @Test fun `first video is the best that fits the player's share of the speed`() {
        val set = listOf(track(1080, 4_000_000), track(720, 2_000_000), track(480, 1_000_000), track(360, 500_000))
        // 0.7 × 3 Mbps = 2.1 Mbps: 720p, with 480p as the fallback.
        assertEquals(listOf(720, 480), StartPrefetch.firstVideoTracks(set, 1080, 3_000_000).map { it.height })
        // The preferred-quality cap wins over a fast connection.
        assertEquals(listOf(480, 360), StartPrefetch.firstVideoTracks(set, 480, 50_000_000).map { it.height })
        // Too slow for anything: the lowest, alone.
        assertEquals(listOf(360), StartPrefetch.firstVideoTracks(set, 1080, 100_000).map { it.height })
        assertTrue(StartPrefetch.firstVideoTracks(emptyList(), 1080, 1).isEmpty())
    }

    @Test fun `dash start covers the likely videos and the audio, without ranges skipped`() {
        val audio = AudioTrack("$url&audio", "audio/mp4", "mp4a", 128_000, 140, SegmentRanges(0, 600, 601, 1500))
        val set = listOf(track(720, 2_000_000), track(480, 1_000_000, ranges = null))
        val ranges = StartPrefetch.dash(set, audio, 1080, 10_000_000)
        assertEquals(listOf("$url&h=720", "$url&audio"), ranges.map { it.url })
        assertEquals(listOf(2101L, 1501L), ranges.map { it.length })
    }

    @Test fun `short start reads the first bytes, never past the file`() {
        val small = "https://rr1---sn-x.googlevideo.com/videoplayback?clen=1000"
        assertEquals(listOf(StartPrefetch.Range(url, 0, StartPrefetch.SHORT_VIDEO_BYTES), StartPrefetch.Range(small, 0, 1000)),
            StartPrefetch.progressive(url, small, null))
        assertEquals(listOf(StartPrefetch.Range(url, 0, StartPrefetch.SHORT_MUXED_BYTES)), StartPrefetch.progressive(null, null, url))
        assertTrue(StartPrefetch.progressive(null, null, null).isEmpty())
    }

    @Test fun `only googlevideo hosts are opened ahead`() {
        assertEquals("rr3---sn-abc.googlevideo.com", StartPrefetch.host(url))
        assertNull(StartPrefetch.host("https://www.youtube.com/watch?v=x"))
        assertNull(StartPrefetch.host("https://googlevideo.com.evil.example/videoplayback"))
        assertNull(StartPrefetch.host("http://rr3---sn-abc.googlevideo.com/videoplayback"))
    }

    @Test fun `a host is opened once per window and again after a failure or the window`() {
        var now = 0L
        val gate = HostGate(windowMs = 60_000) { now }
        assertTrue(gate.claim("a"))
        assertFalse(gate.claim("a"))
        assertTrue(gate.claim("b"))
        now = 59_999
        assertFalse(gate.claim("a"))
        now = 60_000
        assertTrue(gate.claim("a"))
        gate.forget("a")
        assertTrue(gate.claim("a"))
    }

    @Test fun `one start prefetch at a time, kept for the same video`() {
        val slot = StartSlot()
        val first = Job()
        assertTrue(slot.start("a") { first })
        assertFalse(slot.start("a") { error("must not start twice") })
        assertSame(first, slot.active("a"))
        val second = Job()
        assertTrue(slot.start("b") { second })
        assertTrue(first.isCancelled)
        assertNull(slot.active("a"))
        // Opening "b" keeps it; opening another video cancels it.
        slot.cancelOthers("b")
        assertTrue(second.isActive)
        slot.cancelOthers("c")
        assertTrue(second.isCancelled)
    }

    @Test fun `a press that became a scroll cancels only its own video`() {
        val slot = StartSlot()
        val job = Job()
        slot.start("a") { job }
        slot.cancel("b")
        assertTrue(job.isActive)
        slot.cancel("a")
        assertTrue(job.isCancelled)
        // A finished job can be started again.
        assertTrue(slot.start("a") { Job() })
    }

    /**
     * The DASH player looks a stream up in the cache under its resolved BaseURL (the manifest
     * gives no cache key; DashUtil.resolveCacheKey): the same string the prefetch writes under.
     */
    @OptIn(UnstableApi::class)
    @Test fun `dash cache key is the stream URL the prefetch uses`() {
        val manifest = "data:application/dash+xml;base64,PE1QRC8+"
        val baseUrl = UriUtil.resolve(manifest, url)
        assertEquals(url, UriUtil.resolve(baseUrl, ""))
    }
}
