package app.freshtuber.domain.model

import app.freshtuber.innertube.InnerTubeCommand

enum class LikeStatus { Like, Dislike, Indifferent }

/** Everything on a video page except the player. */
data class VideoDetails(
    val id: String,
    val title: String,
    val viewsText: String?,
    val dateText: String?,
    val description: String?,
    val channel: ChannelSummary?,
    val likeCountText: String?,
    /** The count YouTube shows once the user's like is counted in. */
    val likeCountTextLiked: String?,
    val likeStatus: LikeStatus,
    /** Null when YouTube did not say (signed out). */
    val subscribed: Boolean?,
    val likeCommand: InnerTubeCommand?,
    val dislikeCommand: InnerTubeCommand?,
    val removeLikeCommand: InnerTubeCommand?,
    val subscribeCommand: InnerTubeCommand?,
    val unsubscribeCommand: InnerTubeCommand?,
    val related: List<VideoSummary>,
    val commentsContinuation: String?,
    val descriptionLinks: List<TextLink> = emptyList(),
)

data class Comment(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val authorChannelId: String?,
    val text: String,
    val publishedText: String?,
    val likeCountText: String?,
    val likeCountTextLiked: String?,
    val replyCountText: String?,
    val isLiked: Boolean,
    val isByCreator: Boolean,
    val likeCommand: InnerTubeCommand?,
    val unlikeCommand: InnerTubeCommand?,
    /** Present when the signed-in user may reply; needs the reply text added. */
    val replyCommand: InnerTubeCommand?,
    val repliesContinuation: String?,
    val links: List<TextLink> = emptyList(),
)

data class CommentSortOption(val title: String, val token: String, val selected: Boolean)

data class CommentsPage(
    val countText: String?,
    /** Present when the signed-in user may comment; needs the text added. */
    val createCommand: InnerTubeCommand?,
    val sortOptions: List<CommentSortOption>,
    val comments: List<Comment>,
    val continuation: String?,
)

/** One playlist in the "save to playlist" picker. */
data class PlaylistOption(
    val id: String,
    val title: String,
    val containsVideo: Boolean,
    val addCommand: InnerTubeCommand,
    val removeCommand: InnerTubeCommand,
)
