package app.freshtuber.innertube.parse

import app.freshtuber.domain.model.ChannelSummary
import app.freshtuber.domain.model.Comment
import app.freshtuber.domain.model.CommentSortOption
import app.freshtuber.domain.model.CommentsPage
import app.freshtuber.domain.model.FeedItem
import app.freshtuber.domain.model.LikeStatus
import app.freshtuber.domain.model.PlaylistOption
import app.freshtuber.domain.model.VideoDetails
import app.freshtuber.innertube.InnerTubeCommand
import app.freshtuber.innertube.arr
import app.freshtuber.innertube.bestThumbnailUrl
import app.freshtuber.innertube.bool
import app.freshtuber.innertube.findAll
import app.freshtuber.innertube.obj
import app.freshtuber.innertube.path
import app.freshtuber.innertube.str
import app.freshtuber.innertube.text
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Parses the `next` response of a video (watch page). */
object VideoPageParser {

    fun parse(videoId: String, root: JsonElement): VideoDetails? {
        val primary = root.findAll("videoPrimaryInfoRenderer").firstOrNull()
        val secondary = root.findAll("videoSecondaryInfoRenderer").firstOrNull()
        val title = primary.obj("title").text() ?: return null

        val owner = secondary.findAll("videoOwnerRenderer").firstOrNull()
        val ownerTitle = owner.obj("title")
        val channelId = ownerTitle.findAll("browseEndpoint").firstNotNullOfOrNull { it.str("browseId") }
        val channel = if (channelId != null && ownerTitle.text() != null) {
            ChannelSummary(
                id = channelId,
                name = ownerTitle.text()!!,
                avatarUrl = owner.obj("thumbnail").bestThumbnailUrl(),
                detailText = owner.obj("subscriberCountText").text(),
            )
        } else {
            null
        }

        val likeBar = primary.findAll("segmentedLikeDislikeButtonViewModel").firstOrNull()
        val likeToggle = likeBar.path("likeButtonViewModel", "likeButtonViewModel", "toggleButtonViewModel", "toggleButtonViewModel")
        val likeEndpoints = likeBar.findAll("likeEndpoint")
            .filter { it.path("target").str("videoId") == videoId }
        fun likeCommand(status: String) =
            likeEndpoints.firstOrNull { it.str("status") == status }?.let(InnerTubeCommand::fromLike)

        val subscribed = root.findAll("subscriptionStateEntity").firstNotNullOfOrNull { it.bool("subscribed") }
            ?: secondary.findAll("subscribeButtonRenderer").firstNotNullOfOrNull { it.bool("subscribed") }

        val related = root.path("contents", "twoColumnWatchNextResults", "secondaryResults")
            ?.let { FeedParser.parse(it).items.filterIsInstance<FeedItem.Video>().map { item -> item.video } }
            .orEmpty()

        val commentsSection = root.findAll("itemSectionRenderer")
            .firstOrNull { it.str("sectionIdentifier") == "comment-item-section" }

        val descriptionSource = root.findAll("attributedDescription").firstOrNull { it.str("content") != null } ?: secondary.obj("description")
        val description = descriptionSource.str("content") ?: descriptionSource.text()
        return VideoDetails(
            id = videoId,
            title = title,
            viewsText = primary.path("viewCount", "videoViewCountRenderer", "viewCount").text()
                ?: primary.path("viewCount", "videoViewCountRenderer", "shortViewCount").text(),
            dateText = primary.obj("relativeDateText").text() ?: primary.obj("dateText").text(),
            description = description,
            descriptionLinks = description?.let { TextLinks.attributed(descriptionSource, it, videoId) }.orEmpty(),
            channel = channel,
            likeCountText = likeToggle.path("defaultButtonViewModel", "buttonViewModel").str("title"),
            likeCountTextLiked = likeToggle.path("toggledButtonViewModel", "buttonViewModel").str("title"),
            likeStatus = when (likeBar.findAll("likeStatusEntity").firstNotNullOfOrNull { it.str("likeStatus") }) {
                "LIKE" -> LikeStatus.Like
                "DISLIKE" -> LikeStatus.Dislike
                else -> LikeStatus.Indifferent
            },
            subscribed = subscribed,
            likeCommand = likeCommand("LIKE"),
            dislikeCommand = likeCommand("DISLIKE"),
            removeLikeCommand = likeCommand("INDIFFERENT"),
            subscribeCommand = secondary.findAll("subscribeEndpoint").firstNotNullOfOrNull(InnerTubeCommand::fromSubscribe),
            unsubscribeCommand = secondary.findAll("unsubscribeEndpoint").firstNotNullOfOrNull(InnerTubeCommand::fromUnsubscribe),
            related = related,
            commentsContinuation = FeedParser.continuationToken(commentsSection),
        )
    }
}

/**
 * Parses a comments (or replies) continuation response. Text, author and counters live in
 * `frameworkUpdates` entities, linked from each `commentViewModel` by key.
 */
object CommentsParser {

    fun parse(root: JsonElement): CommentsPage {
        val entities = HashMap<String, JsonObject>()
        root.findAll("mutations").forEach { list ->
            (list as? JsonArray)?.forEach { mutation ->
                val key = mutation.str("entityKey") ?: return@forEach
                mutation.obj("payload")?.let { entities[key] = it }
            }
        }

        val endpoints = root.arr("onResponseReceivedEndpoints").orEmpty()
        val itemLists = endpoints.mapNotNull {
            it.path("reloadContinuationItemsCommand").arr("continuationItems")
                ?: it.path("appendContinuationItemsAction").arr("continuationItems")
        }
        val items = itemLists.flatten()

        val comments = items.mapNotNull { item ->
            val thread = item.obj("commentThreadRenderer")
            val viewModel = (thread ?: item).findAll("commentViewModel").firstOrNull()
                ?.let { it.obj("commentViewModel") ?: it }
                ?: return@mapNotNull null
            val replies = thread.path("replies", "commentRepliesRenderer")
            parseComment(viewModel, entities, FeedParser.continuationToken(replies))
        }

        val header = root.findAll("commentsHeaderRenderer").firstOrNull()
        val sortOptions = header.findAll("sortFilterSubMenuRenderer").firstOrNull().arr("subMenuItems").orEmpty()
            .mapNotNull { option ->
                val title = option.str("title") ?: return@mapNotNull null
                val token = FeedParser.continuationToken(option.obj("serviceEndpoint")) ?: return@mapNotNull null
                CommentSortOption(title, token, option.bool("selected") == true)
            }

        val lastItem = items.lastOrNull()
        val continuation = if (lastItem?.obj("continuationItemRenderer") != null) {
            FeedParser.continuationToken(lastItem)
        } else {
            null
        }

        return CommentsPage(
            countText = header.obj("countText").text(),
            createCommand = header.findAll("createCommentEndpoint").firstNotNullOfOrNull(InnerTubeCommand::fromCreateComment),
            sortOptions = sortOptions,
            comments = comments,
            continuation = continuation,
        )
    }

    private fun parseComment(
        viewModel: JsonElement,
        entities: Map<String, JsonObject>,
        repliesContinuation: String?,
    ): Comment? {
        val entity = viewModel.str("commentKey")?.let(entities::get).obj("commentEntityPayload") ?: return null
        val properties = entity.obj("properties")
        val author = entity.obj("author")
        val toolbar = entity.obj("toolbar")
        val state = viewModel.str("toolbarStateKey")?.let(entities::get).obj("engagementToolbarStateEntityPayload")
        val surface = viewModel.str("toolbarSurfaceKey")?.let(entities::get).obj("engagementToolbarSurfaceEntityPayload")

        fun commentAction(key: String) = surface.obj(key).findAll("performCommentActionEndpoint")
            .firstNotNullOfOrNull(InnerTubeCommand::fromCommentAction)

        return Comment(
            id = properties.str("commentId") ?: viewModel.str("commentId") ?: return null,
            authorName = author.str("displayName") ?: properties.str("authorButtonA11y") ?: return null,
            authorAvatarUrl = author.str("avatarThumbnailUrl"),
            authorChannelId = author.str("channelId"),
            text = properties.obj("content").str("content") ?: return null,
            links = properties.obj("content").str("content")?.let { TextLinks.attributed(properties.obj("content"), it) }.orEmpty(),
            publishedText = properties.str("publishedTime"),
            likeCountText = toolbar.str("likeCountNotliked")?.takeIf { it.isNotBlank() },
            likeCountTextLiked = toolbar.str("likeCountLiked")?.takeIf { it.isNotBlank() },
            replyCountText = toolbar.str("replyCount")?.takeIf { it.isNotBlank() && it != "0" },
            isLiked = state.str("likeState") == "TOOLBAR_LIKE_STATE_LIKED",
            isByCreator = author.bool("isCreator") == true,
            likeCommand = commentAction("likeCommand"),
            unlikeCommand = commentAction("unlikeCommand"),
            replyCommand = surface.obj("replyCommand").findAll("createCommentReplyEndpoint")
                .firstNotNullOfOrNull(InnerTubeCommand::fromCreateReply),
            repliesContinuation = repliesContinuation,
        )
    }
}

/** Parses `playlist/get_add_to_playlist`. */
object PlaylistPickerParser {

    fun parse(videoId: String, root: JsonElement): List<PlaylistOption> =
        root.findAll("playlistAddToOptionRenderer").mapNotNull { option ->
            val id = option.str("playlistId") ?: return@mapNotNull null
            PlaylistOption(
                id = id,
                title = option.obj("title").text() ?: return@mapNotNull null,
                containsVideo = option.str("containsSelectedVideos") == "ALL",
                addCommand = option.findAll("addServiceEndpoint").firstNotNullOfOrNull {
                        InnerTubeCommand.fromPlaylistEdit(it.obj("playlistEditEndpoint"))
                    }
                    ?: InnerTubeCommand.addToPlaylist(id, videoId),
                removeCommand = option.findAll("removeServiceEndpoint").firstNotNullOfOrNull {
                    InnerTubeCommand.fromPlaylistEdit(it.obj("playlistEditEndpoint"))
                } ?: InnerTubeCommand.removeFromPlaylist(id, videoId),
            )
        }
}
