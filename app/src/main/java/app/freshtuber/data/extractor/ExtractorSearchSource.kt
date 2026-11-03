package app.freshtuber.data.extractor

import app.freshtuber.domain.model.FeedItem
import app.freshtuber.domain.model.FeedPage
import app.freshtuber.domain.model.VideoSummary
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
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
    private val pages = ConcurrentHashMap<String, Pair<String, Page>>()

    fun search(query: String): FeedPage {
        val extractor = ServiceList.YouTube.getSearchExtractor(query)
        try {
            extractor.fetchPage()
        } catch (_: SearchExtractor.NothingFoundException) {
            return FeedPage(emptyList())
        }
        return extractor.initialPage.toFeedPage(query)
    }

    fun searchMore(token: String): FeedPage {
        val (query, page) = pages.remove(token) ?: return FeedPage(emptyList())
        return ServiceList.YouTube.getSearchExtractor(query).getPage(page).toFeedPage(query)
    }

    /** One of [Kiosk]: a single page of trending videos. */
    fun kiosk(kiosk: Kiosk): FeedPage {
        val extractor = ServiceList.YouTube.kioskList.getExtractorById(kiosk.id, null)
        extractor.fetchPage()
        return FeedPage(extractor.initialPage.items.videoItems())
    }

    private fun ListExtractor.InfoItemsPage<*>.toFeedPage(query: String): FeedPage {
        val token = nextPage?.let { page ->
            UUID.randomUUID().toString().also { pages[it] = query to page }
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
