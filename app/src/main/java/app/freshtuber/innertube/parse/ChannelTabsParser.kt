package app.freshtuber.innertube.parse

import app.freshtuber.innertube.arr
import app.freshtuber.innertube.bool
import app.freshtuber.innertube.findAll
import app.freshtuber.innertube.obj
import app.freshtuber.innertube.path
import app.freshtuber.innertube.str
import kotlinx.serialization.json.JsonElement

/** Channel sections the app can show as a list. Posts, Store, Search and the like are left out. */
enum class ChannelTabKind(val slug: String) {
    Home("featured"),
    Videos("videos"),
    Shorts("shorts"),
    Live("streams"),
    Releases("releases"),
    Podcasts("podcasts"),
    Playlists("playlists"),
    Courses("courses");

    companion object {
        fun ofSlug(slug: String): ChannelTabKind? = entries.firstOrNull { it.slug == slug }
    }
}

/** One channel section as YouTube lists it: its browse params and whether this response is it. */
data class ChannelTab(val kind: ChannelTabKind, val title: String, val params: String, val selected: Boolean)

/**
 * The section tabs of a channel `browse` response, in YouTube's order. The kind comes from the
 * tab's web URL (`/@name/videos`), which does not depend on the interface language. Params are
 * URL-encoded in the response and are sent decoded, as youtube.com does.
 */
object ChannelTabsParser {

    fun parse(root: JsonElement): List<ChannelTab> {
        val tabs = root.path("contents", "twoColumnBrowseResultsRenderer").arr("tabs")
            ?: root.findAll("twoColumnBrowseResultsRenderer").firstOrNull().arr("tabs")
            ?: return emptyList()
        return tabs.mapNotNull { tab ->
            val renderer = tab.obj("tabRenderer") ?: return@mapNotNull null
            val endpoint = renderer.obj("endpoint")
            val url = endpoint.path("commandMetadata", "webCommandMetadata").str("url") ?: return@mapNotNull null
            val kind = ChannelTabKind.ofSlug(url.substringBefore('?').trimEnd('/').substringAfterLast('/'))
                ?: return@mapNotNull null
            val params = endpoint.obj("browseEndpoint").str("params") ?: return@mapNotNull null
            ChannelTab(
                kind = kind,
                title = renderer.str("title") ?: kind.name,
                params = decode(params),
                selected = renderer.bool("selected") == true,
            )
        }.distinctBy { it.kind }
    }

    /** Percent escapes only: a `+` in base64 must stay a `+`, not become a space. */
    internal fun decode(params: String): String =
        Regex("%([0-9A-Fa-f]{2})").replace(params) { it.groupValues[1].toInt(16).toChar().toString() }
}
