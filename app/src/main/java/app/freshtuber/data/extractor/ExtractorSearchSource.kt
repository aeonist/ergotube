package app.freshtuber.data.extractor

import app.freshtuber.domain.model.FeedItem
import app.freshtuber.domain.model.FeedPage
import app.freshtuber.domain.model.VideoSummary
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anonymous YouTube data through NewPipeExtractor: search and the trending kiosks.
 * Blocking: call it off the main thread.
 */
@Singleton
class ExtractorSearchSource @Inject constructor() {

    /** NewPipe pages are objects; screens get an opaque token for them. */
    private data class SearchRequest(val query: String, val songs: Boolean = false) {
        fun extractor(): SearchExtractor = if (songs) {
            ServiceList.YouTube.getSearchExtractor(query, listOf(MUSIC_SONGS), "")
        } else ServiceList.YouTube.getSearchExtractor(query)
    }

    private val pages = ConcurrentHashMap<String, Pair<SearchRequest, Page>>()

    fun search(query: String): FeedPage = search(SearchRequest(query))
    fun searchSongs(query: String): FeedPage = search(SearchRequest(query, songs = true))

    private fun search(request: SearchRequest): FeedPage {
        val extractor = request.extractor()
        try {
            extractor.fetchPage()
        } catch (_: SearchExtractor.NothingFoundException) {
            return FeedPage(emptyList())
        }
        return extractor.initialPage.toFeedPage(request)
    }

    fun searchMore(token: String): FeedPage {
        val (request, page) = pages[token] ?: return FeedPage(emptyList())
        val result = request.extractor().getPage(page).toFeedPage(request)
        pages.remove(token)
        return result
    }

    /** One of [Kiosk]: a single page of trending videos. */
    fun kiosk(kiosk: Kiosk): FeedPage {
        val extractor = ServiceList.YouTube.kioskList.getExtractorById(kiosk.id, null)
        extractor.fetchPage()
        return FeedPage(extractor.initialPage.items.videoItems())
    }

    private fun ListExtractor.InfoItemsPage<*>.toFeedPage(request: SearchRequest): FeedPage {
        val token = nextPage?.let { page ->
            UUID.randomUUID().toString().also { pages[it] = request to page }
        }
        return FeedPage(items.videoItems(), continuation = token)
    }

    private fun List<*>.videoItems(): List<FeedItem> =
        filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toVideoSummary() }
            .distinctBy { it.id }
            .map(FeedItem::Video)

    /** Kiosks NewPipeExtractor offers for YouTube (general trending is gone since 2025). */
    enum class Kiosk(val id: String) {
        Music("trending_music"),
        Gaming("trending_gaming"),
        Podcasts("trending_podcasts_episodes"),
        Movies("trending_movies_and_shows"),
        Live("live"),
    }
}

/**
 * The bare "UC…" id from a channel URL. NewPipe's own id is "channel/UC…", which InnerTube
 * rejects as a browse id.
 */
internal fun channelIdFromUrl(url: String?): String? =
    url?.let { CHANNEL_ID.find(it)?.groupValues?.get(1) }

private val CHANNEL_ID = Regex("/channel/(UC[\\w-]{22})")

internal fun StreamInfoItem.toVideoSummary(): VideoSummary? {
    val id = runCatching { ServiceList.YouTube.streamLHFactory.getId(url) }.getOrNull()
        ?: return null
    val live = streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM
    return VideoSummary(
        id = id,
        title = name,
        channelName = uploaderName,
        channelId = channelIdFromUrl(uploaderUrl),
        channelAvatarUrl = uploaderAvatars.maxByOrNull { it.height }?.url,
        thumbnailUrl = thumbnails.maxByOrNull { it.height }?.url,
        durationSeconds = duration.takeIf { it > 0 && !live },
        viewCount = viewCount.takeIf { it >= 0 },
        // The extractor reads YouTube in English only; the UI formats this date in its own language.
        uploadedAtMillis = uploadDate?.instant?.toEpochMilli(),
        isLive = live,
        isShort = isShortFormContent,
    )
}
