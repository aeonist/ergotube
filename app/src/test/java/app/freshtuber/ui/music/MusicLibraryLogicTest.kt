package app.freshtuber.ui.music

import app.freshtuber.data.music.MusicSource
import app.freshtuber.data.music.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicLibraryLogicTest {
    private val cloud = MusicTrack("yt:a", MusicSource.YouTube, "Beta", "Zed", addedAt = 3)
    private val saved = MusicTrack("yt:b", MusicSource.YouTube, "Alpha", "Yan", addedAt = 2)
    private val phone = MusicTrack("phone:c", MusicSource.Phone, "Gamma", "Xi", uri = "content://x", addedAt = 1)
    private val all = listOf(cloud, saved, phone)

    @Test fun onlineShowsEverything() {
        val view = MusicLibraryLogic.visible(all, online = true, downloadedIds = emptySet())
        assertEquals(all, view.tracks)
        assertEquals(0, view.hidden)
    }

    @Test fun offlineHidesCloudSongsAndCountsThem() {
        val view = MusicLibraryLogic.visible(all, online = false, downloadedIds = setOf("b"))
        assertEquals(listOf(saved, phone), view.tracks)
        assertEquals(1, view.hidden)
    }

    @Test fun filtersPickDownloadedAndPhone() {
        assertEquals(listOf(saved), MusicLibraryLogic.filter(all, LibraryFilter.Downloaded, setOf("b")))
        assertEquals(listOf(phone), MusicLibraryLogic.filter(all, LibraryFilter.Phone, setOf("b")))
        assertEquals(all, MusicLibraryLogic.filter(all, LibraryFilter.All, emptySet()))
    }

    @Test fun sortsByTitleArtistAndNewest() {
        assertEquals(listOf(saved, cloud, phone), MusicLibraryLogic.sort(all, LibrarySort.Title))
        assertEquals(listOf(phone, saved, cloud), MusicLibraryLogic.sort(all, LibrarySort.Artist))
        assertEquals(listOf(cloud, saved, phone), MusicLibraryLogic.sort(all.reversed(), LibrarySort.New))
    }

    @Test fun heartFollowsLibraryMembership() {
        assertTrue(MusicLibraryLogic.hearted(cloud, setOf("yt:a")))
        assertFalse(MusicLibraryLogic.hearted(saved, setOf("yt:a")))
    }
}
