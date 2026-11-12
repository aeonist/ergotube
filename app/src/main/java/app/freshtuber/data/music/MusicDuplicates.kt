package app.freshtuber.data.music

import kotlin.math.abs

/**
 * The same song twice in the library: usually a file from the phone next to the YouTube song it
 * was downloaded from. Songs match by name, artist and length; nothing is decided by file.
 */
object MusicDuplicates {
    /** A song to take out of the library and the one that stays in its place. */
    data class Extra(val remove: MusicTrack, val keep: MusicTrack)

    private const val LENGTH_SLACK_MS = 3_000L

    private fun plain(text: String?) = text.orEmpty().lowercase().filter(Char::isLetterOrDigit)

    private class Named(val track: MusicTrack, val title: String, val artist: String)

    private fun same(a: Named, b: Named): Boolean {
        val artists = a.artist == b.artist || a.artist.isEmpty() || b.artist.isEmpty()
        val known = a.track.durationMs > 0 && b.track.durationMs > 0
        return when {
            !artists -> false
            known -> abs(a.track.durationMs - b.track.durationMs) <= LENGTH_SLACK_MS
            // Without both lengths only a full name-and-artist match counts.
            else -> a.artist.isNotEmpty() && a.artist == b.artist
        }
    }

    /**
     * [downloaded] are YouTube ids kept on the phone. The copy that stays is the downloaded
     * YouTube song, then any YouTube song, then a phone file; among equals, the favourite and
     * the one added first.
     */
    fun find(tracks: List<MusicTrack>, downloaded: Set<String> = emptySet()): List<Extra> {
        fun rank(track: MusicTrack) = when {
            track.isYouTube && track.videoId in downloaded -> 0
            track.isYouTube -> 1
            else -> 2
        }
        val order = compareBy<MusicTrack>({ rank(it) }, { !it.favorite }, { if (it.addedAt > 0) it.addedAt else Long.MAX_VALUE }, { it.id })
        val extras = mutableListOf<Extra>()
        tracks.map { track ->
            val (artist, title) = MusicNames.split(track.title, track.artist)
            Named(track, plain(title), plain(artist))
        }.filter { it.title.isNotEmpty() }.groupBy { it.title }.values.forEach { group ->
            val keepers = mutableListOf<Named>()
            group.sortedWith { a, b -> order.compare(a.track, b.track) }.forEach { candidate ->
                val keeper = keepers.firstOrNull { same(it, candidate) }
                if (keeper == null) keepers += candidate else extras += Extra(candidate.track, keeper.track)
            }
        }
        return extras
    }
}

/** «Мой джем» comes first and stands apart from the other mixes of the Home page. */
object MusicJams {
    /** YouTube's id of the listener's own endless mix. */
    const val MY_MIX = "RDMM"
    private val names = setOf("мой джем", "my mix", "мой микс", "my supermix", "мой супермикс")

    fun isMine(jam: app.freshtuber.domain.model.PlaylistSummary) = jam.id == MY_MIX || jam.title.trim().lowercase() in names

    /**
     * Signed in, the listener's own mix leads even when the Home page did not show it this time
     * (YouTube answers its id directly); the other mixes follow in the page's order.
     */
    /** «Мой джем» first wherever it was found, then each source's own mixes without it. */
    fun shelves(home: List<app.freshtuber.domain.model.PlaylistSummary>, music: List<app.freshtuber.domain.model.PlaylistSummary>, signedIn: Boolean): MusicJamShelves {
        val mine = order(home + music, signedIn).firstOrNull(::isMine)
        return MusicJamShelves(mine, home.filterNot(::isMine).take(12), music.filterNot(::isMine).filterNot { m -> home.any { it.id == m.id } }.take(12))
    }

    fun order(jams: List<app.freshtuber.domain.model.PlaylistSummary>, signedIn: Boolean, myTitle: String = "Мой джем"): List<app.freshtuber.domain.model.PlaylistSummary> {
        val mine = jams.firstOrNull(::isMine) ?: if (signedIn) app.freshtuber.domain.model.PlaylistSummary(MY_MIX, myTitle, null) else null
        return listOfNotNull(mine) + jams.filterNot(::isMine)
    }
}

/** Names of the files written to the phone's Music folder. */
object MusicFolderNames {
    const val FOLDER = "Music/ErgoTube"

    /** "Artist - Song.m4a", without characters a file name cannot hold. */
    fun fileName(artist: String?, title: String, extension: String): String {
        fun clean(text: String) = text.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), " ").replace(Regex("\\s+"), " ").trim().trim('.')
        val name = listOfNotNull(artist?.let(::clean)?.takeIf(String::isNotEmpty), clean(title).ifEmpty { "ErgoTube" }).joinToString(" - ")
        return name.take(120).trim() + "." + extension
    }
}

/** The jams of the Listen screen, each shelf labelled by where YouTube showed it. */
data class MusicJamShelves(
    val mine: app.freshtuber.domain.model.PlaylistSummary? = null,
    val home: List<app.freshtuber.domain.model.PlaylistSummary> = emptyList(),
    val music: List<app.freshtuber.domain.model.PlaylistSummary> = emptyList(),
) {
    val isEmpty get() = mine == null && home.isEmpty() && music.isEmpty()
}
