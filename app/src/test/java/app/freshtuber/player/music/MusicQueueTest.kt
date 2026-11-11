package app.freshtuber.player.music

import app.freshtuber.data.music.MusicSource
import app.freshtuber.data.music.MusicTrack
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MusicQueueTest {
    private fun song(n: Int) = MusicTrack("yt:song$n", MusicSource.YouTube, "Song $n", durationMs = 60_000)
    private val album = (1..6).map(::song)

    @Test fun startCountsEarlierSongsAsPlayedAndQueuesTheRest() {
        val q = MusicQueue.start(album, 2, "Album", null)
        assertEquals("yt:song3", q.track?.id)
        assertEquals(listOf("yt:song4", "yt:song5", "yt:song6"), q.collection.map { it.id })
        assertEquals(listOf("yt:song1", "yt:song2"), q.history.map { it.track.id })
        assertEquals(3 to 6, q.collectionPosition)
    }

    @Test fun addedSongsPlayFirstThenTheCollectionThenRadio() {
        var q = MusicQueue.start(album.take(2), 0, "Album", null).playNext(song(10)).withRadio(listOf(song(20)))
        q = q.advance()!!
        assertEquals("yt:song10", q.track?.id)
        assertEquals(QueueLayer.Added, q.current?.layer)
        q = q.advance()!!
        assertEquals("yt:song2", q.track?.id)
        q = q.advance()!!
        assertEquals("yt:song20", q.track?.id)
        assertEquals(QueueLayer.Radio, q.current?.layer)
        assertNull(q.advance())
    }

    @Test fun shuffleTouchesOnlyTheCollectionAndSwitchesBackToItsOrder() {
        val q = MusicQueue.start(album, 0, "Album", null).playNext(song(10))
        val shuffled = q.withShuffle(true)
        assertEquals(listOf("yt:song10"), shuffled.added.map { it.id })
        assertEquals(q.collection.map { it.id }.toSet(), shuffled.collection.map { it.id }.toSet())
        assertEquals(q.track, shuffled.track)
        assertEquals(q.collection, shuffled.withShuffle(false).collection)
    }

    @Test fun repeatOneStaysButNextLeavesIt() {
        val q = MusicQueue.start(album, 0, null, null).copy(repeat = RepeatMode.One)
        assertEquals(q.track, q.next)
        assertEquals(q.track, q.advance(explicit = false)?.track)
        assertEquals("yt:song2", q.advance(explicit = true)?.track?.id)
    }

    @Test fun repeatAllStartsTheCollectionAgain() {
        var q = MusicQueue.start(album.take(2), 0, null, null).copy(repeat = RepeatMode.All, endless = false)
        q = q.advance()!!
        q = q.advance()!!
        assertEquals("yt:song1", q.track?.id)
        assertFalse(q.needsRadio)
    }

    @Test fun theSameSongNeverPlaysTwiceInARow() {
        val q = MusicQueue.start(listOf(song(1)), 0, null, null).playNext(song(1)).copy(collection = listOf(song(1), song(2)))
        assertEquals("yt:song2", q.advance()?.track?.id)
    }

    @Test fun backReturnsTheCurrentSongToTheFrontOfItsLayer() {
        val q = MusicQueue.start(album, 1, null, null)
        val back = q.back()!!
        assertEquals("yt:song1", back.track?.id)
        assertEquals("yt:song2", back.collection.first().id)
    }

    @Test fun jumpSkipsTheSongsBeforeTheChosenOne() {
        val q = MusicQueue.start(album, 0, null, null).playNext(song(10))
        val jumped = q.jump(2)!!
        assertEquals("yt:song3", jumped.track?.id)
        assertTrue(jumped.added.isEmpty())
        assertEquals(listOf("yt:song4", "yt:song5", "yt:song6"), jumped.collection.map { it.id })
    }

    @Test fun removeRestoreAndMoveStayWithinALayer() {
        val q = MusicQueue.start(album, 0, null, null)
        val removed = q.remove(QueueLayer.Collection, 1)
        assertEquals(listOf("yt:song2", "yt:song4", "yt:song5", "yt:song6"), removed.collection.map { it.id })
        assertEquals(q.collection, removed.restore(QueueLayer.Collection, 1, song(3)).collection)
        assertEquals(listOf("yt:song3", "yt:song4", "yt:song2", "yt:song5", "yt:song6"), q.move(QueueLayer.Collection, 0, 2).collection.map { it.id })
    }

    @Test fun radioSkipsSongsAlreadyQueuedOrJustPlayed() {
        val q = MusicQueue.start(album.take(2), 1, null, null)
        assertTrue(q.needsRadio)
        val withRadio = q.withRadio(listOf(song(1), song(2), song(7), song(7)))
        assertEquals(listOf("yt:song7"), withRadio.radio.map { it.id })
        assertTrue(q.withEndless(false).radio.isEmpty())
        assertFalse(q.withEndless(false).needsRadio)
    }

    @Test fun survivesSerialization() {
        val q = MusicQueue.start(album, 3, "Микс дня", null).playNext(song(10)).copy(repeat = RepeatMode.All, positionMs = 12_345)
        assertEquals(q, Json.decodeFromString<MusicQueue>(Json.encodeToString(q)))
    }
}
