package app.freshtuber.ui.music

import app.freshtuber.data.music.MusicSource
import app.freshtuber.data.music.MusicTrack

/** What «Любимое» shows: everything, only downloaded songs, or only files from the phone. */
enum class LibraryFilter { All, Downloaded, Phone }

enum class LibrarySort { New, Title, Artist }

/** Pure rules of the music library list, kept apart from the screens so they can be tested. */
object MusicLibraryLogic {
    /** A song that plays without a network: a phone file, a file with a uri, or a finished download. */
    fun isLocal(track: MusicTrack, downloadedIds: Set<String>): Boolean =
        track.source == MusicSource.Phone || track.uri != null || track.videoId in downloadedIds

    /** [visible] songs and how many cloud songs are hidden because there is no network. */
    data class Visible(val tracks: List<MusicTrack>, val hidden: Int)

    fun visible(tracks: List<MusicTrack>, online: Boolean, downloadedIds: Set<String>): Visible {
        if (online) return Visible(tracks, 0)
        val (local, cloud) = tracks.partition { isLocal(it, downloadedIds) }
        return Visible(local, cloud.size)
    }

    fun filter(tracks: List<MusicTrack>, filter: LibraryFilter, downloadedIds: Set<String>): List<MusicTrack> = when (filter) {
        LibraryFilter.All -> tracks
        LibraryFilter.Downloaded -> tracks.filter { it.isYouTube && it.videoId in downloadedIds }
        LibraryFilter.Phone -> tracks.filter { it.source == MusicSource.Phone }
    }

    fun sort(tracks: List<MusicTrack>, sort: LibrarySort): List<MusicTrack> = when (sort) {
        LibrarySort.New -> tracks.sortedByDescending { it.addedAt }
        LibrarySort.Title -> tracks.sortedBy { it.title.lowercase() }
        LibrarySort.Artist -> tracks.sortedBy { (it.artist ?: "").lowercase() }
    }

    /** The heart is on while the song is in the one library. */
    fun hearted(track: MusicTrack, libraryIds: Set<String>): Boolean = track.id in libraryIds
}
