package app.freshtuber.innertube.parse

import app.freshtuber.domain.model.ChannelSummary
import app.freshtuber.domain.model.FeedFilter
import app.freshtuber.domain.model.FeedItem
import app.freshtuber.domain.model.FeedPage
import app.freshtuber.domain.model.PlaylistSummary
import app.freshtuber.domain.model.VideoSummary
import app.freshtuber.innertube.arr
import app.freshtuber.innertube.bestSourceUrl
import app.freshtuber.innertube.bestThumbnailUrl
import app.freshtuber.innertube.bool
import app.freshtuber.innertube.findAll
import app.freshtuber.innertube.int
import app.freshtuber.innertube.obj
import app.freshtuber.innertube.path
import app.freshtuber.innertube.str
import app.freshtuber.innertube.text
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Turns any browse / search / continuation response into a [FeedPage].
 *
 * It walks the whole tree and picks up every item renderer it knows, old (`videoRenderer`)
 * and new (`lockupViewModel`), instead of following fixed paths: YouTube moves these around
 * between feeds and over time, while the item renderers themselves change much less.
 */
object FeedParser {

    fun parse(root: JsonElement): FeedPage {
        val items = mutableListOf<FeedItem>()
        val filters = mutableListOf<FeedFilter>()
        var continuation: String? = null

        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> for ((key, value) in element) {
                    when (key) {
                        in VIDEO_RENDERERS -> parseVideoRenderer(value)?.let { items += FeedItem.Video(it) }
                        "lockupViewModel" -> parseLockup(value)?.let(items::add)
                        "shortsLockupViewModel" -> parseShortsLockup(value)?.let { items += FeedItem.Video(it) }
                        "reelItemRenderer" -> parseReelItem(value)?.let { items += FeedItem.Video(it) }
                        "channelRenderer", "gridChannelRenderer" -> parseChannel(value)?.let { items += FeedItem.Channel(it) }
                        "continuationItemRenderer", "continuationItemViewModel" ->
                            continuationToken(value)?.let { continuation = it }
                        "chipCloudChipRenderer", "chipViewModel" -> parseChip(value)?.let(filters::add)
                        else -> walk(value)
                    }
                }
                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }

        walk(root)
        return FeedPage(
            items = items.distinctBy { it.key },
            continuation = continuation,
            filters = filters.distinctBy { it.title },
        )
    }

    /** Mixes are watch-next playlist panels, not browse playlists. */
    fun parseMix(root: JsonElement): FeedPage {
        val panel = root.path("contents", "twoColumnWatchNextResults", "playlist", "playlist")
            ?: root.findAll("playlistPanelRenderer").firstOrNull() ?: return FeedPage(emptyList())
        val page = parse(panel)
        val last = panel.findAll("playlistPanelVideoRenderer").lastOrNull()
        val endpoint = last.path("navigationEndpoint", "watchEndpoint") as? JsonObject
        val playlistId = endpoint.str("playlistId") ?: panel.str("playlistId")
        val videoId = endpoint.str("videoId") ?: last.str("videoId")
        val next = if (playlistId != null && videoId != null && page.items.size > 1) kotlinx.serialization.json.buildJsonObject {
            put("playlistId", kotlinx.serialization.json.JsonPrimitive(playlistId))
            put("videoId", kotlinx.serialization.json.JsonPrimitive(videoId))
            endpoint?.get("index")?.let { put("playlistIndex", it) }
            endpoint?.get("params")?.let { put("params", it) }
        }.toString() else null
        return page.copy(continuation = next)
    }

    /** Home owns only the main grid, never videos nested in editorial or Shorts shelves.
     * RichGrid and append/reload commands match YouTube.js HomeFeed's page containers.
     */
    fun parseHome(root: JsonElement): FeedPage {
        val grid = root.obj("contents").findAll("richGridRenderer").firstOrNull()
        val containers = if (grid != null) listOf(grid) else {
            listOf("onResponseReceivedActions", "onResponseReceivedEndpoints", "onResponseReceivedCommands")
                .flatMap { key -> root.arr(key).orEmpty() }
                .flatMap { action ->
                    listOfNotNull(action.obj("appendContinuationItemsAction"), action.obj("reloadContinuationItemsCommand"))
                }
        }
        val mainItems = containers.flatMap { container ->
            (container.arr("contents") ?: container.arr("continuationItems")).orEmpty()
        }.map { item -> item.obj("richItemRenderer")?.obj("content") ?: item }.filter { item ->
            item.obj("continuationItemRenderer") != null ||
                item.obj("continuationItemViewModel") != null || item.obj("lockupViewModel") != null ||
                item.obj("videoRenderer") != null
        }
        return parse(JsonObject(mapOf(
            "header" to (grid?.obj("header") ?: root.obj("header") ?: JsonObject(emptyMap())),
            "contents" to JsonArray(mainItems),
        )))
    }

    /** First `continuationCommand.token` under [element]. */
    fun continuationToken(element: JsonElement?): String? =
        element.findAll("continuationCommand").firstNotNullOfOrNull { it.str("token") }

    // --- Old renderers: videoRenderer, compactVideoRenderer, gridVideoRenderer, playlistVideoRenderer.

    internal fun parseVideoRenderer(v: JsonElement): VideoSummary? {
        val id = v.str("videoId") ?: return null
        val title = v.obj("title").text() ?: v.obj("headline").text() ?: return null
        val owner = v.obj("ownerText") ?: v.obj("shortBylineText") ?: v.obj("longBylineText")
        val timeStatus = v.findAll("thumbnailOverlayTimeStatusRenderer").firstOrNull()
        val isLive = v.arr("badges").orEmpty().any { it.path("metadataBadgeRenderer").str("style") == "BADGE_STYLE_TYPE_LIVE_NOW" } ||
            timeStatus.str("style") == "LIVE"
        val details = listOfNotNull(
            v.obj("viewCountText").text() ?: v.obj("shortViewCountText").text(),
            v.obj("publishedTimeText").text(),
        ).ifEmpty { listOfNotNull(v.obj("videoInfo").text()) }
        return VideoSummary(
            id = id,
            title = title,
            channelName = owner.text(),
            channelId = owner.findAll("browseEndpoint").firstNotNullOfOrNull { it.str("browseId") },
            channelAvatarUrl = v.path("channelThumbnailSupportedRenderers", "channelThumbnailWithLinkRenderer", "thumbnail").bestThumbnailUrl()
                ?: v.obj("channelThumbnail").bestThumbnailUrl(),
            thumbnailUrl = v.obj("thumbnail").bestThumbnailUrl(),
            durationText = if (isLive) null else v.obj("lengthText").text() ?: timeStatus.obj("text").text(),
            detailText = details.joinToString(" • ").ifEmpty { null },
            isLive = isLive,
            watchedPercent = v.findAll("thumbnailOverlayResumePlaybackRenderer").firstOrNull().int("percentDurationWatched"),
            notInterested = notInterested(v),
        )
    }

    // --- New view models: lockupViewModel (videos, playlists, mixes, podcasts).

    internal fun parseLockup(v: JsonElement): FeedItem? {
        val id = v.str("contentId") ?: return null
        val meta = v.path("metadata", "lockupMetadataViewModel")
        val title = meta.obj("title").text() ?: return null
        val avatar = meta.path("image", "decoratedAvatarViewModel")
        val rows = meta.path("metadata", "contentMetadataViewModel").arr("metadataRows").orEmpty()
        val badges = v.obj("contentImage").findAll("thumbnailBadgeViewModel")

        // With an avatar (or a channel link in the first row) that row is the channel's name.
        val firstRowLinksChannel = rows.firstOrNull().findAll("browseEndpoint").isNotEmpty()
        val channelInFirstRow = avatar != null || firstRowLinksChannel
        val channelName = if (channelInFirstRow) rows.firstOrNull().rowParts().firstOrNull() else null
        val detailRows = if (channelInFirstRow) rows.drop(1) else rows
        val detail = detailRows.flatMap { it.rowParts() }.joinToString(" • ").ifEmpty { null }

        return when (v.str("contentType")) {
            "LOCKUP_CONTENT_TYPE_VIDEO" -> {
                val liveBadge = badges.any { it.str("badgeStyle")?.contains("LIVE") == true }
                FeedItem.Video(
                    VideoSummary(
                        id = id,
                        title = title,
                        channelName = channelName,
                        channelId = (avatar ?: rows.firstOrNull()).findAll("browseEndpoint")
                            .firstNotNullOfOrNull { it.str("browseId") },
                        channelAvatarUrl = avatar.path("avatar", "avatarViewModel", "image").bestSourceUrl(),
                        thumbnailUrl = v.path("contentImage", "thumbnailViewModel", "image").bestSourceUrl(),
                        durationText = if (liveBadge) null else badges.firstNotNullOfOrNull { it.str("text") },
                        detailText = detail,
                        isLive = liveBadge,
                        watchedPercent = v.findAll("thumbnailOverlayProgressBarViewModel").firstOrNull().int("startPercent"),
                        notInterested = notInterested(v),
                    ),
                )
            }
            "LOCKUP_CONTENT_TYPE_PLAYLIST", "LOCKUP_CONTENT_TYPE_PODCAST", "LOCKUP_CONTENT_TYPE_ALBUM" -> FeedItem.Playlist(
                PlaylistSummary(
                    id = id,
                    title = title,
                    thumbnailUrl = v.obj("contentImage").findAll("image").firstNotNullOfOrNull { it.bestSourceUrl() },
                    videoCountText = badges.firstNotNullOfOrNull { it.str("text") },
                    channelName = channelName ?: rows.firstOrNull().rowParts().firstOrNull(),
                    seedVideoId = v.findAll("watchEndpoint").firstNotNullOfOrNull { it.str("videoId") },
                ),
            )
            else -> null
        }
    }

    /** Texts of one metadata row; a view count with only an icon uses its spoken label. */
    private fun JsonElement?.rowParts(): List<String> =
        arr("metadataParts").orEmpty().mapNotNull { part ->
            val content = part.obj("text").text() ?: return@mapNotNull null
            val spoken = part.str("accessibilityLabel")
            if (part.obj("leadingIcon") != null && spoken != null) spoken else content
        }

    // --- Shorts.

    internal fun parseShortsLockup(v: JsonElement): VideoSummary? {
        val reel = v.findAll("reelWatchEndpoint").firstOrNull()
        val id = reel.str("videoId") ?: v.str("entityId")?.removePrefix("shorts-shelf-item-") ?: return null
        val overlay = v.obj("overlayMetadata")
        return VideoSummary(
            id = id,
            title = overlay.obj("primaryText").text() ?: v.str("accessibilityText") ?: return null,
            thumbnailUrl = v.path("thumbnail").bestSourceUrl() ?: reel.obj("thumbnail").bestThumbnailUrl(),
            detailText = overlay.obj("secondaryText").text(),
            isShort = true,
        )
    }

    internal fun parseReelItem(v: JsonElement): VideoSummary? {
        val id = v.str("videoId") ?: return null
        return VideoSummary(
            id = id,
            title = v.obj("headline").text() ?: return null,
            thumbnailUrl = v.obj("thumbnail").bestThumbnailUrl(),
            detailText = v.obj("viewCountText").text(),
            isShort = true,
        )
    }

    // --- Channels and filter chips.

    internal fun parseChannel(v: JsonElement): ChannelSummary? {
        val id = v.str("channelId") ?: return null
        val name = v.obj("title").text() ?: return null
        // YouTube puts the handle in subscriberCountText and the subscribers in videoCountText.
        val detail = listOfNotNull(v.obj("subscriberCountText").text(), v.obj("videoCountText").text())
            .joinToString(" • ").ifEmpty { null }
        return ChannelSummary(id, name, v.obj("thumbnail").bestThumbnailUrl(), detail)
    }

    internal fun parseChip(v: JsonElement): FeedFilter? {
        val title = v.obj("text").text() ?: v.str("text") ?: return null
        val selected = v.bool("isSelected") == true || v.bool("selected") == true
        return FeedFilter(title, continuationToken(v), selected)
    }

    /** Match the action's icon, never localized text or an unrelated feedback token. */
    private fun notInterested(v: JsonElement): app.freshtuber.innertube.InnerTubeCommand? {
        fun action(element: JsonElement): JsonObject? {
            when (element) {
                is JsonObject -> {
                    element.values.forEach { child -> action(child)?.let { return it } }
                    val icons = element.findAll("icon") + element.findAll("leadingIcon")
                    if (icons.any { it.str("iconType") == "NOT_INTERESTED" || it.str("iconName") == "NOT_INTERESTED" } &&
                        element.findAll("feedbackEndpoint").isNotEmpty()) return element
                }
                is JsonArray -> element.forEach { child -> action(child)?.let { return it } }
                else -> Unit
            }
            return null
        }
        val item = action(v) ?: return null
        val endpoint = item.findAll("feedbackEndpoint").firstOrNull() ?: return null
        val token = endpoint.str("feedbackToken") ?: return null
        return app.freshtuber.innertube.InnerTubeCommand("feedback", kotlinx.serialization.json.buildJsonObject {
            put("feedbackTokens", JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive(token))))
            put("shouldMerge", kotlinx.serialization.json.JsonPrimitive(endpoint.bool("shouldMerge") == true))
            put("isFeedbackTokenUnencrypted", kotlinx.serialization.json.JsonPrimitive(endpoint.bool("isFeedbackTokenUnencrypted") == true))
        })
    }

    private val VIDEO_RENDERERS = setOf(
        "videoRenderer",
        "compactVideoRenderer",
        "gridVideoRenderer",
        "playlistVideoRenderer",
        "playlistPanelVideoRenderer",
        "videoWithContextRenderer",
    )
}
