package app.freshtuber.domain.model

/** One video in a feed or search result, independent of where it came from. */
data class VideoSummary(
    val id: String,
    val title: String,
    val channelName: String? = null,
    val channelId: String? = null,
    val channelAvatarUrl: String? = null,
    val thumbnailUrl: String? = null,
    /** Ready-to-show length from YouTube ("12:49"). */
    val durationText: String? = null,
    /** Length in seconds when the source gives numbers (NewPipeExtractor). */
    val durationSeconds: Long? = null,
    /** Ready-to-show details from YouTube ("42 млн просмотров • 1 день назад"). */
    val detailText: String? = null,
    val viewCount: Long? = null,
    /** Upload time; may be approximate ("8 years ago" parsed into a date). */
    val uploadedAtMillis: Long? = null,
    val isLive: Boolean = false,
    val isShort: Boolean = false,
    /** How much of it the signed-in user watched, 0..100. */
    val watchedPercent: Int? = null,
    val notInterested: app.freshtuber.innertube.InnerTubeCommand? = null,
)

data class PlaylistSummary(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    /** "12 видео" */
    val videoCountText: String? = null,
    val channelName: String? = null,
    val seedVideoId: String? = null,
)

data class ChannelSummary(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    /** Handle or subscriber count, as YouTube shows it. */
    val detailText: String? = null,
)

sealed interface FeedItem {
    val key: String

    data class Video(val video: VideoSummary) : FeedItem {
        override val key: String get() = "v:${video.id}"
    }

    data class Playlist(val playlist: PlaylistSummary) : FeedItem {
        override val key: String get() = "p:${playlist.id}"
    }

    data class Channel(val channel: ChannelSummary) : FeedItem {
        override val key: String get() = "c:${channel.id}"
    }
}

/** A feed filter ("Все", "Музыка", …). [token] null means the feed's default view. */
data class FeedFilter(val title: String, val token: String?, val selected: Boolean)

data class FeedPage(
    val items: List<FeedItem>,
    /** Token for the next page, null at the end. */
    val continuation: String? = null,
    val filters: List<FeedFilter> = emptyList(),
)
