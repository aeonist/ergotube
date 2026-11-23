package app.freshtuber.innertube.parse

import app.freshtuber.domain.model.FeedItem
import app.freshtuber.domain.model.FeedPage
import app.freshtuber.domain.model.VideoSummary
import app.freshtuber.innertube.*
import kotlinx.serialization.json.JsonObject

/** Only playlist rows count. Suggested songs live on separate shelves. */
object LikedSongsParser {
    fun parse(root: JsonObject): FeedPage {
        val shelf = root.findAll("musicPlaylistShelfRenderer").firstOrNull()
            ?: root.path("continuationContents", "musicPlaylistShelfContinuation")
        val rows = shelf.arr("contents")
            ?: root.findAll("appendContinuationItemsAction").firstNotNullOfOrNull { it.arr("continuationItems") }
            ?: throw InnerTubeException.BadResponse()
        val tracks = rows.mapNotNull { row ->
            val song = row.obj("musicResponsiveListItemRenderer") ?: return@mapNotNull null
            val columns = song.arr("flexColumns").orEmpty()
            fun column(index: Int) = columns.getOrNull(index).path("musicResponsiveListItemFlexColumnRenderer", "text")
            val title = column(0).text() ?: return@mapNotNull null
            val id = song.obj("playlistItemData").str("videoId")
                ?: column(0).findAll("watchEndpoint").firstNotNullOfOrNull { it.str("videoId") } ?: return@mapNotNull null
            val thumb = song.path("thumbnail", "musicThumbnailRenderer", "thumbnail").bestThumbnailUrl()
            val time = song.arr("fixedColumns")?.firstOrNull().path("musicResponsiveListItemFixedColumnRenderer", "text").text()
            val seconds = time?.split(':')?.takeIf { p -> p.all { it.toLongOrNull() != null } }?.fold(0L) { t, p -> t * 60 + p.toLong() }
            FeedItem.Video(VideoSummary(id, title, channelName = column(1).text(), thumbnailUrl = thumb, durationSeconds = seconds))
        }
        val next = rows.lastOrNull().path("continuationItemRenderer", "continuationEndpoint", "continuationCommand").str("token")
            ?: shelf.arr("continuations")?.firstOrNull()?.obj("nextContinuationData").str("continuation")
        return FeedPage(items = tracks, continuation = next)
    }
}
