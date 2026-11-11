package app.freshtuber.data.music

import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.account.AccountState
import app.freshtuber.data.extractor.ExtractorSearchSource
import app.freshtuber.data.feed.FeedRepository
import app.freshtuber.domain.model.FeedItem
import app.freshtuber.domain.model.PlaylistSummary
import app.freshtuber.domain.model.VideoSummary
import app.freshtuber.innertube.CallAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class MusicAccount(val id: String, val channel: String?) {
    companion object {
        fun from(state: AccountState): MusicAccount? =
            (state as? AccountState.SignedIn)?.active?.let { MusicAccount(it.id, it.activePageId) }
    }
}

@Serializable
data class MusicJamCursor(val mix: PlaylistSummary, val account: MusicAccount?, val continuation: String? = null)

data class MusicJamPage(val tracks: List<MusicTrack>, val next: MusicJamCursor?)

@Serializable
data class DailyMusicMix(val date: String, val personalized: Boolean, val tracks: List<MusicTrack>)

@Singleton
class MusicDiscovery @Inject constructor(
    private val library: MusicLibrary,
    private val feeds: FeedRepository,
    private val extractor: ExtractorSearchSource,
    private val accounts: AccountRepository,
) {
    private val mixLock = Mutex()
    val accountState get() = accounts.state

    fun currentCursor(mix: PlaylistSummary) = MusicJamCursor(mix, MusicAccount.from(accountState.value))

    /** Mix cards of the ordinary YouTube Home: the profile's own when signed in. */
    suspend fun discover(account: MusicAccount?): List<PlaylistSummary> {
        if (MusicAccount.from(accountState.value) != account) return emptyList()
        val result = feeds.musicMixCards(account?.let { CallAs.Profile(it.id) } ?: CallAs.Anonymous)
        return MusicJams.order(result.takeIf { MusicAccount.from(accountState.value) == account }.orEmpty(), signedIn = account != null)
    }

    suspend fun load(cursor: MusicJamCursor): MusicJamPage {
        val profile = cursor.account
        if (profile != null && MusicAccount.from(accountState.value) != profile) return MusicJamPage(emptyList(), null)
        val page = feeds.musicMix(cursor.mix, cursor.continuation, profile?.let { CallAs.Profile(it.id) } ?: CallAs.Anonymous)
        val tracks = musicTracks(page.items.filterIsInstance<FeedItem.Video>().map { it.video })
        if (profile != null && MusicAccount.from(accountState.value) != profile) return MusicJamPage(emptyList(), null)
        return MusicJamPage(tracks, page.continuation?.takeIf { it != cursor.continuation }?.let { cursor.copy(continuation = it) })
    }

    suspend fun daily(): DailyMusicMix = mixLock.withLock {
        val today = LocalDate.now().toString()
        val cached = library.readState<DailyMusicMix>("daily_mix")
        val recent = library.recent(12)
        if (cached?.date == today && (cached.personalized || recent.isEmpty())) return@withLock cached
        val tracks = if (recent.isEmpty()) withContext(Dispatchers.IO) {
            extractor.kiosk(ExtractorSearchSource.Kiosk.Music).items.filterIsInstance<FeedItem.Video>().map { it.video }
                .filterNot { it.isShort || it.isLive }.take(20).map(MusicTrack::youtube)
        } else {
            val similar = try {
                val seed = recent.first()
                val source = PlaylistSummary("RD${seed.videoId}", seed.title, seed.artwork, seedVideoId = seed.videoId)
                val page = feeds.musicMix(source, null, CallAs.Anonymous)
                musicTracks(page.items.filterIsInstance<FeedItem.Video>().map { it.video }.take(12))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            dailyMixOrder(recent, similar)
        }
        DailyMusicMix(today, recent.isNotEmpty(), tracks).also {
            if (it.tracks.isNotEmpty()) library.writeState("daily_mix", it)
        }
    }

    /**
     * Songs like [seed], from YouTube's own radio for it (the RD mix of the watch page). Runs
     * anonymously; the mix is music already, so its entries are not checked one by one.
     */
    suspend fun radio(seed: MusicTrack): List<MusicTrack> {
        if (!seed.isYouTube) return emptyList()
        val source = PlaylistSummary("RD${seed.videoId}", seed.title, seed.artwork, seedVideoId = seed.videoId)
        val page = feeds.musicMix(source, null, CallAs.Anonymous)
        return page.items.filterIsInstance<FeedItem.Video>().map { it.video }
            .filterNot { it.isLive || it.isShort || it.id == seed.videoId }
            .distinctBy(VideoSummary::id).map(MusicTrack::youtube)
    }

    /** Songs from the phone library that match, then YouTube songs. */
    suspend fun searchYouTube(query: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        extractor.searchSongs(query).items.filterIsInstance<FeedItem.Video>().map { it.video }
            .filterNot { it.isLive || it.isShort }.map(MusicTrack::youtube)
    }

    /** Mixes are music already; live streams and Shorts are left out. */
    private fun musicTracks(videos: List<VideoSummary>): List<MusicTrack> =
        videos.distinctBy(VideoSummary::id).filterNot { it.isLive || it.isShort }.map(MusicTrack::youtube)
}

internal fun dailyMixOrder(recent: List<MusicTrack>, similar: List<MusicTrack>): List<MusicTrack> {
    val new = similar.filter { next -> recent.none { it.id == next.id } }
    return (0 until maxOf(recent.size, new.size)).flatMap { index ->
        listOfNotNull(recent.getOrNull(index), new.getOrNull(index))
    }.distinctBy(MusicTrack::id).take(20)
}
