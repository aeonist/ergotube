package app.freshtuber.player

import app.freshtuber.data.extras.SkipSegment
import app.freshtuber.data.extras.SponsorBlockRules
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamChoiceTest {

    private fun track(height: Int, codec: String, fps: Int = 30, bitrate: Int = 1) =
        VideoTrack(height, fps, "u-$height-$codec-$fps", null, StreamChoice.label(height, fps), codec, bitrate)

    private val avc = "video/avc"
    private val vp9 = "video/x-vnd.on2.vp9"
    private val av1 = "video/av01"

    @Test
    fun `one track per height, H264 up to 1080p and VP9 above`() {
        val tracks = StreamChoice.videoTracks(
            listOf(track(1080, avc), track(1080, vp9), track(2160, vp9), track(2160, av1), track(720, avc, fps = 60), track(720, avc)),
        )
        assertEquals(listOf(2160, 1080, 720), tracks.map { it.height })
        assertEquals(vp9, tracks[0].codecMime)
        assertEquals(avc, tracks[1].codecMime)
        assertEquals(60, tracks[2].fps)
        assertEquals("720p60", tracks[2].label)
    }

    @Test
    fun `a codec that failed is not chosen again`() {
        val tracks = StreamChoice.videoTracks(listOf(track(1080, avc), track(1080, vp9)), blocked = setOf(avc))
        assertEquals(listOf(vp9), tracks.map { it.codecMime })
    }

    @Test
    fun `pick takes the best quality not above the preference`() {
        val tracks = StreamChoice.videoTracks(listOf(track(2160, vp9), track(1080, avc), track(480, avc)))
        assertEquals(1, StreamChoice.pick(tracks, 1080))
        assertEquals(2, StreamChoice.pick(tracks, 720))
        assertEquals(2, StreamChoice.pick(tracks, 144))
        assertEquals(-1, StreamChoice.pick(emptyList(), 1080))
    }

    @Test
    fun `pick works for vertical Shorts tracks`() {
        val vertical1080 = VideoTrack(1920, 30, "u-1080", null, StreamChoice.label(1920, 30, 1080), avc, 1, width = 1080)
        val vertical720 = VideoTrack(1280, 30, "u-720", null, StreamChoice.label(1280, 30, 720), avc, 1, width = 720)
        val vertical480 = VideoTrack(854, 30, "u-480", null, StreamChoice.label(854, 30, 480), avc, 1, width = 480)
        val tracks = listOf(vertical1080, vertical720, vertical480)

        assertEquals("1080p", vertical1080.label)
        assertEquals("720p", vertical720.label)
        assertEquals("480p", vertical480.label)

        assertEquals(0, StreamChoice.pick(tracks, 1080))
        assertEquals(1, StreamChoice.pick(tracks, 720))
        assertEquals(2, StreamChoice.pick(tracks, 480))
    }
}

class SponsorBlockRulesTest {

    @Test
    fun `keeps our video's skip segments, sorted, ignoring tiny and non-skip ones`() {
        val root = Json.parseToJsonElement(
            """
            [{"videoID":"other","segments":[{"segment":[1,50],"category":"sponsor","actionType":"skip"}]},
             {"videoID":"vid","segments":[
                {"segment":[120.5,180],"category":"selfpromo","actionType":"skip"},
                {"segment":[10,40],"category":"sponsor","actionType":"skip"},
                {"segment":[60,60.4],"category":"sponsor","actionType":"skip"},
                {"segment":[70,90],"category":"sponsor","actionType":"mute"}]}]
            """.trimIndent(),
        )
        val segments = SponsorBlockRules.parse("vid", root)
        assertEquals(listOf(10.0 to 40.0, 120.5 to 180.0), segments.map { it.start to it.end })
    }

    @Test
    fun `segment lookup`() {
        val segments = listOf(SkipSegment(10.0, 40.0, "sponsor"))
        assertEquals(10.0, SponsorBlockRules.segmentAt(segments, 12.0)?.start)
        assertNull(SponsorBlockRules.segmentAt(segments, 9.9))
        assertNull(SponsorBlockRules.segmentAt(segments, 39.8))
    }

    @Test
    fun `intros and endings are marked on the timeline but never skipped`() {
        val segments = listOf(SkipSegment(0.0, 8.0, "intro"), SkipSegment(10.0, 40.0, "sponsor"), SkipSegment(90.0, 100.0, "outro"))
        assertEquals(listOf("sponsor"), segments.filter { it.skipped }.map { it.category })
        val spans = app.freshtuber.ui.player.SegmentMarks.spans(segments, 100_000)
        assertEquals(3, spans.size)
        assertEquals(0.1f, spans[1].first, 0.0001f)
        assertEquals(1f, spans[2].second, 0.0001f)
        assertEquals(emptyList<Any>(), app.freshtuber.ui.player.SegmentMarks.spans(segments, 0))
    }
}

class ChunkPlanTest {

    @Test
    fun `stream length comes from clen on googlevideo only`() {
        assertEquals(25L, ChunkPlan.streamLength("https://rr1---sn-x.googlevideo.com/videoplayback?itag=137&clen=25&dur=1"))
        assertNull(ChunkPlan.streamLength("https://rr1---sn-x.googlevideo.com/videoplayback?itag=137"))
        assertNull(ChunkPlan.streamLength("https://example.com/videoplayback?clen=25"))
        assertNull(ChunkPlan.streamLength("not a url"))
    }

    @Test
    fun `a read is split into bounded chunks covering every byte`() {
        assertEquals(listOf(0L to 10L, 10L to 10L, 20L to 5L), ChunkPlan.plan(0, 25, 10))
        assertEquals(listOf(17L to 8L), ChunkPlan.plan(17, 25, 10))
        assertEquals(emptyList<Pair<Long, Long>>(), ChunkPlan.plan(25, 25, 10))
    }
}
