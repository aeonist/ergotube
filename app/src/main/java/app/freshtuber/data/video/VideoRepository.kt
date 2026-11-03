package app.freshtuber.data.video

import app.freshtuber.data.account.ActionGuard
import app.freshtuber.domain.model.CommentsPage
import app.freshtuber.domain.model.PlaylistOption
import app.freshtuber.domain.model.VideoDetails
import app.freshtuber.innertube.InnerTubeClient
import app.freshtuber.innertube.InnerTubeCommand
import app.freshtuber.innertube.InnerTubeException
import app.freshtuber.innertube.WebClient
import app.freshtuber.innertube.path
import app.freshtuber.innertube.str
import app.freshtuber.innertube.parse.CommentsParser
import app.freshtuber.innertube.parse.PlaylistPickerParser
import app.freshtuber.innertube.parse.VideoPageParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** The watch page: details, comments and the account actions on them. */
@Singleton
class VideoRepository @Inject constructor(
    private val innerTube: InnerTubeClient,
    private val guard: ActionGuard,
) {

    suspend fun details(videoId: String): VideoDetails = withContext(Dispatchers.IO) {
        val root = innerTube.post("next", buildJsonObject { put("videoId", videoId) })
        VideoPageParser.parse(videoId, root) ?: throw InnerTubeException.BadResponse()
    }

    /** Comments, replies and their next pages all come from a continuation token. */
    suspend fun comments(token: String): CommentsPage = withContext(Dispatchers.IO) {
        CommentsParser.parse(innerTube.post("next", buildJsonObject { put("continuation", token) }))
    }

    suspend fun playlistOptions(videoId: String): List<PlaylistOption> = withContext(Dispatchers.IO) {
        val root = innerTube.post("playlist/get_add_to_playlist", buildJsonObject {
            put("videoIds", buildJsonArray { add(JsonPrimitive(videoId)) })
        })
        PlaylistPickerParser.parse(videoId, root)
    }

    /**
     * Adds the video to the signed-in account's watch history, the way youtube.com does: ask the
     * player endpoint for its stats URL and ping it once. Silent on failure.
     */
    suspend fun reportWatched(videoId: String) {
        runCatching {
            val player = innerTube.post("player", buildJsonObject {
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            })
            val statsUrl = player.path("playbackTracking", "videostatsPlaybackUrl").str("baseUrl") ?: return
            innerTube.ping(
                statsUrl,
                mapOf("ver" to "2", "c" to WebClient.NAME, "cver" to WebClient.VERSION, "cpn" to clientPlaybackNonce()),
            )
        }
    }

    /** The 16-character playback id YouTube's players generate. */
    private fun clientPlaybackNonce(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val random = java.security.SecureRandom()
        return buildString { repeat(16) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }

    /** Runs an account action under the careful-mode limits. */
    suspend fun perform(command: InnerTubeCommand) {
        guard.run {
            withContext(Dispatchers.IO) { innerTube.post(command.path, command.body) }
        }
    }
}
