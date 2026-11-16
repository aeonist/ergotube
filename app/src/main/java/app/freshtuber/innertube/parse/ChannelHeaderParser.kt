package app.freshtuber.innertube.parse

import app.freshtuber.innertube.arr
import app.freshtuber.innertube.findAll
import app.freshtuber.innertube.obj
import app.freshtuber.innertube.text
import kotlinx.serialization.json.JsonElement

/** What a channel header says under the name: the @handle, then subscribers and videos. */
data class ChannelFacts(val handle: String?, val subscribers: String?, val videos: String?)

/**
 * Reads the channel header of a `browse` response. Current youtube.com sends a
 * `pageHeaderViewModel` whose metadata rows are `[@handle]` and `[subscribers, videos]`, already
 * in the interface language; the older `c4TabbedHeaderRenderer` is a fallback.
 */
object ChannelHeaderParser {

    fun facts(root: JsonElement): ChannelFacts? {
        val rows = root.findAll("pageHeaderViewModel").firstOrNull()
            .obj("metadata").obj("contentMetadataViewModel").arr("metadataRows")
        if (rows != null) {
            val parts = rows.flatMap { row -> row.arr("metadataParts").orEmpty().mapNotNull { it.obj("text").text()?.trim()?.ifEmpty { null } } }
            val handle = parts.firstOrNull { it.startsWith("@") }
            val rest = parts.filter { it != handle }
            if (parts.isNotEmpty()) return ChannelFacts(handle, rest.getOrNull(0), rest.getOrNull(1))
        }
        val c4 = root.findAll("c4TabbedHeaderRenderer").firstOrNull() ?: return null
        val handle = c4.obj("channelHandleText").text()
        val subscribers = c4.obj("subscriberCountText").text()
        val videos = c4.obj("videosCountText").text()
        return if (handle == null && subscribers == null && videos == null) null else ChannelFacts(handle, subscribers, videos)
    }
}
