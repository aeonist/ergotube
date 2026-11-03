package app.freshtuber.data.shorts

import app.freshtuber.innertube.CallAs
import app.freshtuber.innertube.InnerTubeClient
import app.freshtuber.innertube.InnerTubeException
import app.freshtuber.innertube.arr
import app.freshtuber.innertube.bestThumbnailUrl
import app.freshtuber.innertube.findAll
import app.freshtuber.innertube.obj
import app.freshtuber.innertube.parse.FeedParser
import app.freshtuber.innertube.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class ShortItem(val videoId: String, val thumbnailUrl: String?)

data class ShortsPage(val items: List<ShortItem>, val next: String?)

/**
 * The Shorts tab: YouTube's own endless sequence. It is seeded from a Short on the user's
 * home feed when signed in, otherwise from a Shorts search.
 */
@Singleton
class ShortsRepository @Inject constructor(private val innerTube: InnerTubeClient) {

    suspend fun first(): ShortsPage = withContext(Dispatchers.IO) {
        val seed = seedFrom(innerTube.post("browse", buildJsonObject { put("browseId", "FEwhat_to_watch") }))
            ?: seedFrom(innerTube.post("search", buildJsonObject { put("query", "#shorts") }, CallAs.Anonymous))
            ?: throw InnerTubeException.BadResponse()
        sequence(seed)
    }

    suspend fun more(next: String): ShortsPage = withContext(Dispatchers.IO) { sequence(next) }

    private suspend fun sequence(params: String): ShortsPage {
        val root = innerTube.post("reel/reel_watch_sequence", buildJsonObject { put("sequenceParams", params) })
        return ShortsSequenceParser.parse(root)
    }

    private fun seedFrom(root: JsonElement): String? =
        root.findAll("reelWatchEndpoint").firstNotNullOfOrNull { it.str("sequenceParams") }
}

object ShortsSequenceParser {
    fun parse(root: JsonElement): ShortsPage {
        val items = root.arr("entries").orEmpty().mapNotNull { entry ->
            val reel = entry.findAll("reelWatchEndpoint").firstOrNull() ?: return@mapNotNull null
            val id = reel.str("videoId") ?: return@mapNotNull null
            ShortItem(id, reel.obj("thumbnail").bestThumbnailUrl())
        }.distinctBy { it.videoId }
        return ShortsPage(items, FeedParser.continuationToken(root.obj("continuationEndpoint")))
    }
}
