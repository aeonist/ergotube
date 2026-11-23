package app.freshtuber.data.feed

import app.freshtuber.data.account.AccountRepository
import app.freshtuber.data.account.AccountState
import app.freshtuber.data.extractor.ExtractorSearchSource
import app.freshtuber.domain.model.ChannelSummary
import app.freshtuber.domain.model.FeedItem
import app.freshtuber.domain.model.FeedPage
import app.freshtuber.domain.model.PlaylistSummary
import app.freshtuber.innertube.CallAs
import app.freshtuber.innertube.InnerTubeClient
import app.freshtuber.innertube.bestThumbnailUrl
import app.freshtuber.innertube.bool
import app.freshtuber.innertube.findAll
import app.freshtuber.innertube.obj
import app.freshtuber.innertube.parse.ChannelTab
import app.freshtuber.innertube.parse.ChannelTabKind
import app.freshtuber.innertube.parse.ChannelTabsParser
import app.freshtuber.innertube.parse.FeedParser
import app.freshtuber.innertube.parse.PlayerResponseParser
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
    data class Mix(val playlistId: String, val seedVideoId: String? = null) : FeedSource
    data class Search(val query: String) : FeedSource
    data class Kiosk(val kiosk: ExtractorSearchSource.Kiosk) : FeedSource
}

data class ChannelHeader(val channel: ChannelSummary, val subscribed: Boolean?, val facts: app.freshtuber.innertube.parse.ChannelFacts? = null)

/** One section of a channel: its header, the sections it offers and this section's first page. */
data class ChannelPage(val header: ChannelHeader?, val tabs: List<ChannelTab>, val page: FeedPage)

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
            is FeedSource.Mix -> FeedParser.parseMix(innerTube.post("next", buildJsonObject {
                put("playlistId", source.playlistId)
                source.seedVideoId?.let { put("videoId", it) }
            }))
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
            is FeedSource.Mix -> FeedParser.parseMix(innerTube.post("next", kotlinx.serialization.json.Json.parseToJsonElement(token) as JsonObject))
            is FeedSource.Search -> if (isSignedIn) {
                FeedParser.parse(innerTube.post("search", continuation(token)))
            } else {
                extractor.searchMore(token)
            }
            else -> parseBrowse(source, innerTube.post("browse", continuation(token)))
        }
    }

    /** The ordinary WEB player returns category metadata even when it supplies no streams. */
    suspend fun musicCategory(videoId: String): String? = PlayerResponseParser.category(
        innerTube.post("player", buildJsonObject { put("videoId", videoId) }, CallAs.Anonymous),
    )

    /**
     * Mix cards of the ordinary YouTube Home, kept apart by where they were found: the Home
     * page itself and its «Музыка» filter. A mix shown in both is listed under Home only.
     */
    suspend fun musicMixCards(callAs: CallAs): MusicMixCards {
        val home = innerTube.post("browse", buildJsonObject { put("browseId", HOME) }, callAs)
        val direct = FeedParser.parseMusicMixes(home)
        val music = FeedParser.parseHome(home).filters.firstOrNull {
            it.title.equals("Music", true) || it.title.equals("Музыка", true)
        }?.token ?: return MusicMixCards(direct, emptyList())
        return try {
            val fromMusic = FeedParser.parseMusicMixes(innerTube.post("browse", continuation(music), callAs))
            MusicMixCards(direct, fromMusic.filter { m -> direct.none { it.id == m.id } })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (direct.isEmpty()) throw e
            MusicMixCards(direct, emptyList())
        }
    }

    suspend fun likedSongs(profileId: String, token: String?): FeedPage {
        val body = token?.let(::continuation) ?: buildJsonObject { put("browseId", "VLLM") }
        val response = innerTube.post("browse", body, CallAs.Profile(profileId), client = app.freshtuber.innertube.BrowseClient.Music)
        return app.freshtuber.innertube.parse.LikedSongsParser.parse(response)
    }

    suspend fun musicMix(mix: PlaylistSummary, token: String?, callAs: CallAs): FeedPage {
        val body = if (token != null) kotlinx.serialization.json.Json.parseToJsonElement(token) as JsonObject
        else buildJsonObject {
            put("playlistId", mix.id.removePrefix("VL"))
            mix.seedVideoId?.let { put("videoId", it) }
            mix.mixParams?.let { put("params", it) }
        }
        return FeedParser.parseMix(innerTube.post("next", body, callAs))
    }

    private fun parseBrowse(source: FeedSource, root: JsonObject): FeedPage =
        if (source is FeedSource.Browse && source.browseId == HOME) FeedParser.parseHome(root)
        else FeedParser.parse(root)

    /** Channel page: header, the section tabs and one section ([params]; "Videos" by default). */
    suspend fun channel(channelId: String, params: String = CHANNEL_VIDEOS_TAB): ChannelPage = withContext(Dispatchers.IO) {
        val root = innerTube.post("browse", buildJsonObject {
            put("browseId", channelId)
            put("params", params)
        })
        val tabs = ChannelTabsParser.parse(root)
        val page = FeedParser.parse(root)
        // The channel's Home is a fixed set of shelves; its continuations belong to single shelves
        // and would append them to the end of the page, so it does not page.
        val home = tabs.firstOrNull { it.selected }?.kind == ChannelTabKind.Home
        ChannelPage(parseChannelHeader(channelId, root), tabs, if (home) page.copy(continuation = null) else page)
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
            facts = app.freshtuber.innertube.parse.ChannelHeaderParser.facts(root),
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

        fun playlist(id: String, seedVideoId: String? = null): FeedSource {
            val plain = id.removePrefix("VL")
            return if (plain.startsWith("RD")) FeedSource.Mix(plain, seedVideoId) else FeedSource.Browse(if (id.startsWith("VL")) id else "VL$id")
        }
    }
}

/** Drops Shorts from regular feeds when the user keeps them in their own tab. */
fun FeedPage.withoutShorts(): FeedPage =
    copy(items = items.filterNot { it is FeedItem.Video && it.video.isShort })

/** Mixes found on the Home page itself and those under its «Музыка» filter. */
data class MusicMixCards(val home: List<PlaylistSummary>, val music: List<PlaylistSummary>)
