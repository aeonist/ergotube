package app.freshtuber.player.music

import app.freshtuber.data.music.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicHandOverTest {
    private fun handOver(tap: Long = 83_000) = MusicHandOver("yt:abc", "abc", tap)

    @Test fun theQueueStandsAtTheSecondOfTheTap() {
        val pending = handOver()
        assertEquals(83_000, pending.positionMs)
        // So the queue made from it resumes there, not from 0.
        val queue = MusicQueue.radio(MusicTrack.youtube(app.freshtuber.domain.model.VideoSummary("abc", "Song")), null, null)
            .copy(positionMs = pending.positionMs)
        assertEquals(83_000, queue.positionMs)
    }

    @Test fun theSoundStartsWhereTheVideoHasGotToMeanwhile() {
        assertEquals(87_500, handOver().startMs(videoMediaId = "abc", videoPositionMs = 87_500))
    }

    @Test fun aVideoGoneOrChangedFallsBackToTheTap() {
        assertEquals(83_000, handOver().startMs(videoMediaId = null, videoPositionMs = 0))
        assertEquals(83_000, handOver().startMs(videoMediaId = "other", videoPositionMs = 12_000))
        assertEquals(83_000, handOver().startMs(videoMediaId = "abc", videoPositionMs = 0))
    }

    @Test fun aSeekMadeMeanwhileWins() {
        val pending = handOver()
        pending.seek(30_000)
        assertEquals(30_000, pending.positionMs)
        assertEquals(30_000, pending.startMs(videoMediaId = "abc", videoPositionMs = 90_000))
        pending.seek(-5)
        assertEquals(0, pending.positionMs)
    }

    @Test fun itPlaysUnlessPausedMeanwhile() {
        val pending = handOver()
        assertTrue(pending.play)
        pending.play = false
        assertEquals(false, pending.play)
    }
}
