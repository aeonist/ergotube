package app.freshtuber.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A ready-made InnerTube request, usually replayed from a command YouTube put in a response
 * (a like button's `likeEndpoint`, a subscribe button's `subscribeEndpoint`, …). Replaying the
 * page's own command keeps the opaque `params` YouTube expects.
 */
data class InnerTubeCommand(val path: String, val body: JsonObject) {

    /** Comments and replies take the user's text in `commentText`. */
    fun withCommentText(text: String): InnerTubeCommand =
        copy(body = JsonObject(body + ("commentText" to JsonPrimitive(text))))

    companion object {
        /** `likeEndpoint` {status, target, likeParams|dislikeParams|removeLikeParams}. */
        fun fromLike(endpoint: JsonElement?): InnerTubeCommand? {
            val target = endpoint.obj("target") ?: return null
            val (path, paramsKey) = when (endpoint.str("status")) {
                "LIKE" -> "like/like" to "likeParams"
                "DISLIKE" -> "like/dislike" to "dislikeParams"
                "INDIFFERENT" -> "like/removelike" to "removeLikeParams"
                else -> return null
            }
            return InnerTubeCommand(path, buildJsonObject {
                put("target", target)
                endpoint.str(paramsKey)?.let { put("params", it) }
            })
        }

        fun fromSubscribe(endpoint: JsonElement?): InnerTubeCommand? =
            channelCommand("subscription/subscribe", endpoint)

        fun fromUnsubscribe(endpoint: JsonElement?): InnerTubeCommand? =
            channelCommand("subscription/unsubscribe", endpoint)

        /** Used when the page offers no subscribe command (values from YouTube.js). */
        fun subscribe(channelId: String) = InnerTubeCommand("subscription/subscribe", buildJsonObject {
            put("channelIds", buildJsonArray { add(JsonPrimitive(channelId)) })
            put("params", "EgIIAhgA")
        })

        fun unsubscribe(channelId: String) = InnerTubeCommand("subscription/unsubscribe", buildJsonObject {
            put("channelIds", buildJsonArray { add(JsonPrimitive(channelId)) })
            put("params", "CgIIAhgA")
        })

        /** Used when the page offers no like command. */
        fun like(videoId: String, status: String): InnerTubeCommand? = fromLike(buildJsonObject {
            put("status", status)
            put("target", buildJsonObject { put("videoId", videoId) })
        })

        /** `performCommentActionEndpoint` {action | actions}. */
        fun fromCommentAction(endpoint: JsonElement?): InnerTubeCommand? {
            val actions = endpoint.arr("actions") ?: endpoint.str("action")?.let { JsonArray(listOf(JsonPrimitive(it))) }
            return actions?.let { InnerTubeCommand("comment/perform_comment_action", buildJsonObject { put("actions", it) }) }
        }

        fun fromCreateComment(endpoint: JsonElement?): InnerTubeCommand? =
            endpoint.str("createCommentParams")?.let {
                InnerTubeCommand("comment/create_comment", buildJsonObject { put("createCommentParams", it) })
            }

        fun fromCreateReply(endpoint: JsonElement?): InnerTubeCommand? =
            endpoint.str("createReplyParams")?.let {
                InnerTubeCommand("comment/create_comment_reply", buildJsonObject { put("createReplyParams", it) })
            }

        /** `playlistEditEndpoint` {playlistId, actions, params}. */
        fun fromPlaylistEdit(endpoint: JsonElement?): InnerTubeCommand? {
            val playlistId = endpoint.str("playlistId") ?: return null
            val actions = endpoint.arr("actions") ?: return null
            return InnerTubeCommand("browse/edit_playlist", buildJsonObject {
                put("playlistId", playlistId)
                put("actions", actions)
                endpoint.str("params")?.let { put("params", it) }
            })
        }

        fun addToPlaylist(playlistId: String, videoId: String) = InnerTubeCommand("browse/edit_playlist", buildJsonObject {
            put("playlistId", playlistId)
            put("actions", buildJsonArray {
                add(buildJsonObject {
                    put("action", "ACTION_ADD_VIDEO")
                    put("addedVideoId", videoId)
                })
            })
        })

        fun removeFromPlaylist(playlistId: String, videoId: String) = InnerTubeCommand("browse/edit_playlist", buildJsonObject {
            put("playlistId", playlistId)
            put("actions", buildJsonArray {
                add(buildJsonObject {
                    put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID")
                    put("removedVideoId", videoId)
                })
            })
        })

        private fun channelCommand(path: String, endpoint: JsonElement?): InnerTubeCommand? {
            val channelIds = endpoint.arr("channelIds")?.takeIf { it.isNotEmpty() } ?: return null
            return InnerTubeCommand(path, buildJsonObject {
                put("channelIds", channelIds)
                endpoint.str("params")?.let { put("params", it) }
            })
        }
    }
}
