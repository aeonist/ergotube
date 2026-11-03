package app.freshtuber.data.feed

import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.account.AccountState
import app.freshtuber.data.extractor.ExtractorSearchSource
import app.freshtuber.domain.model.ChannelSummary
import app.freshtuber.domain.model.FeedItem
import app.freshtuber.domain.model.FeedPage
import app.freshtuber.innertube.InnerTubeClient
import app.freshtuber.innertube.bestThumbnailUrl
import app.freshtuber.innertube.bool
import app.freshtuber.innertube.findAll
import app.freshtuber.innertube.obj
import app.freshtuber.innertube.parse.FeedParser
import app.freshtuber.innertube.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** A YouTube page that is a list: a browse id, or a continuation token of a list already open. */
sealed interface FeedSource {
    data class Browse(val browseId: String, val params: String? = null) : FeedSource
    data class Search(val query: String) : FeedSource
    data class Kiosk(val kiosk: ExtractorSearchSource.Kiosk) : FeedSource
}

data class ChannelHeader(val channel: ChannelSummary, val subscribed: Boolean?)

/**
 * Lists from YouTube. Signed in, they come from InnerTube as the account; signed out, from
 * NewPipeExtractor (search, trending) or anonymous InnerTube (public playlists, channels).
 */
@Singleton
class FeedRepository @Inject constructor(
    private val innerTube: InnerTubeClient,
    private val accounts: AccountRepository,
    private val extractor: ExtractorSearchSource,
) {
    val isSignedIn: Boolean get() = accounts.state.value is AccountState.SignedIn

    suspend fun load(source: FeedSource): FeedPage = withContext(Dispatchers.IO) {
        when (source) {
            is FeedSource.Browse -> parseBrowse(source,
                innerTube.post("browse", buildJsonObject {
                    put("browseId", source.browseId)
                    source.params?.let { put("params", it) }
                }),
            )
            is FeedSource.Search -> if (isSignedIn) {
                FeedParser.parse(innerTube.post("search", buildJsonObject { put("query", source.query) }))
            } else {
                extractor.search(source.query)
            }
            is FeedSource.Kiosk -> extractor.kiosk(source.kiosk)
        }
    }

    /** Next page of [source] (or a filter chip's view of it) from a continuation token. */
    suspend fun loadMore(source: FeedSource, token: String): FeedPage = withContext(Dispatchers.IO) {
        when (source) {
            is FeedSource.Search -> if (isSignedIn) {
                FeedParser.parse(innerTube.post("search", continuation(token)))
            } else {
                extractor.searchMore(token)
            }
            else -> parseBrowse(source, innerTube.post("browse", continuation(token)))
        }
    }

    private fun parseBrowse(source: FeedSource, root: JsonObject): FeedPage =
        if (source is FeedSource.Browse && source.browseId == HOME) FeedParser.parseHome(root)
        else FeedParser.parse(root)

    /** Channel page: header plus the "Videos" tab. */
    suspend fun channel(channelId: String): Pair<ChannelHeader?, FeedPage> = withContext(Dispatchers.IO) {
        val root = innerTube.post("browse", buildJsonObject {
            put("browseId", channelId)
            put("params", CHANNEL_VIDEOS_TAB)
        })
        parseChannelHeader(channelId, root) to FeedParser.parse(root)
    }

    private fun parseChannelHeader(channelId: String, root: JsonObject): ChannelHeader? {
        val meta = root.findAll("channelMetadataRenderer").firstOrNull() ?: return null
        val name = meta.str("title") ?: return null
        val subscribed = root.findAll("subscriptionStateEntity").firstNotNullOfOrNull { it.bool("subscribed") }
            ?: root.findAll("subscribeButtonRenderer").firstNotNullOfOrNull { it.bool("subscribed") }
        return ChannelHeader(
            channel = ChannelSummary(
                id = meta.str("externalId") ?: channelId,
                name = name,
                avatarUrl = meta.obj("avatar").bestThumbnailUrl(),
            ),
            subscribed = subscribed,
        )
    }

    private fun continuation(token: String) = buildJsonObject { put("continuation", token) }

    companion object {
        const val HOME = "FEwhat_to_watch"
        const val SUBSCRIPTIONS = "FEsubscriptions"
        const val CHANNELS = "FEchannels"
        const val HISTORY = "FEhistory"
        const val PLAYLISTS = "FEplaylist_aggregation"
        const val WATCH_LATER = "VLWL"
        const val LIKED = "VLLL"

        /** Protobuf params for a channel's "Videos" tab, as youtube.com sends them. */
        const val CHANNEL_VIDEOS_TAB = "EgZ2aWRlb3PyBgQKAjoA"

        fun playlist(id: String) = FeedSource.Browse(if (id.startsWith("VL")) id else "VL$id")
    }
}

/** Drops Shorts from regular feeds when the user keeps them in their own tab. */
fun FeedPage.withoutShorts(): FeedPage =
    copy(items = items.filterNot { it is FeedItem.Video && it.video.isShort })
