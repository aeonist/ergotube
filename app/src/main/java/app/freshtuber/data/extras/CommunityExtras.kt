package app.freshtuber.data.extras

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** DeArrow's community title and thumbnail for a video. */
data class Branding(val title: String?, val thumbnailUrl: String?)

/**
 * Community services. Only video ids (or a hash prefix of one) are sent; never cookies or
 * account data. Failures are silent: these are extras on top of YouTube's own data.
 */
@Singleton
class CommunityExtras @Inject constructor(private val okHttp: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val brandingCache = ConcurrentHashMap<String, Branding>()

    /** Return YouTube Dislike estimate. */
    suspend fun dislikes(videoId: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val root = get("https://returnyoutubedislikeapi.com/votes?videoId=$videoId") as? JsonObject
            (root?.get("dislikes") as? JsonPrimitive)?.longOrNull
        }.getOrNull()
    }

    /**
     * DeArrow branding through the hash-prefix API, so the server does not learn which video
     * is being looked at. Null when there is no better title or thumbnail.
     */
    suspend fun branding(videoId: String): Branding? {
        brandingCache[videoId]?.let { return it.takeIf { b -> b.title != null || b.thumbnailUrl != null } }
        return withContext(Dispatchers.IO) {
            runCatching {
                val prefix = sha256Hex(videoId).take(4)
                val all = get("https://sponsor.ajay.app/api/branding/$prefix") as? JsonObject
                val branding = DeArrowRules.pick(videoId, all?.get(videoId))
                brandingCache[videoId] = branding
                branding.takeIf { it.title != null || it.thumbnailUrl != null }
            }.getOrNull()
        }
    }

    /**
     * SponsorBlock segments, through the hash-prefix API like DeArrow: "skip" segments of
     * [SPONSOR_CATEGORIES], in seconds. Only [SkipSegment.skipped] ones are jumped over.
     */
    suspend fun sponsorSegments(videoId: String): List<SkipSegment> = withContext(Dispatchers.IO) {
        runCatching {
            val prefix = sha256Hex(videoId).take(4)
            val categories = SPONSOR_CATEGORIES.joinToString(",", "[", "]") { "\"$it\"" }
            val url = "https://sponsor.ajay.app/api/skipSegments/$prefix?categories=" +
                java.net.URLEncoder.encode(categories, "UTF-8")
            SponsorBlockRules.parse(videoId, get(url))
        }.getOrDefault(emptyList())
    }

    private fun get(url: String): JsonElement? {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return json.parseToJsonElement(response.body.string())
        }
    }

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).joinToString("") { "%02x".format(it) }
}

/** A marked part of a video, in seconds. */
data class SkipSegment(val start: Double, val end: Double, val category: String) {
    /** Sponsors and reminders are jumped over; intros and endings are only shown on the timeline. */
    val skipped: Boolean get() = category in SKIPPED_CATEGORIES
}

/** Categories skipped automatically, as ReVanced does by default. */
val SKIPPED_CATEGORIES = setOf("sponsor", "selfpromo", "interaction")

/** Categories asked from SponsorBlock: the skipped ones plus intros ("intro") and endings ("outro"). */
val SPONSOR_CATEGORIES = listOf("sponsor", "selfpromo", "interaction", "intro", "outro")

object SponsorBlockRules {
    /** The hash-prefix response lists several videos; pick ours and keep skippable segments. */
    fun parse(videoId: String, root: JsonElement?): List<SkipSegment> {
        val videos = root as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        val video = videos.mapNotNull { it as? JsonObject }
            .firstOrNull { (it["videoID"] as? JsonPrimitive)?.contentOrNull == videoId } ?: return emptyList()
        val segments = video["segments"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return segments.mapNotNull { element ->
            val segment = element as? JsonObject ?: return@mapNotNull null
            val action = (segment["actionType"] as? JsonPrimitive)?.contentOrNull ?: "skip"
            if (action != "skip") return@mapNotNull null
            val range = segment["segment"] as? kotlinx.serialization.json.JsonArray ?: return@mapNotNull null
            val start = (range.getOrNull(0) as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
            val end = (range.getOrNull(1) as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
            val category = (segment["category"] as? JsonPrimitive)?.contentOrNull ?: "sponsor"
            if (end - start < 1.0) null else SkipSegment(start, end, category)
        }.sortedBy { it.start }
    }

    /** The segment [positionSeconds] is inside of, if any (with a short lead-in tolerance). */
    fun segmentAt(segments: List<SkipSegment>, positionSeconds: Double): SkipSegment? =
        segments.firstOrNull { positionSeconds >= it.start && positionSeconds < it.end - 0.5 }
}

/** DeArrow's display rules, kept pure for tests. */
object DeArrowRules {

    fun pick(videoId: String, entry: JsonElement?): Branding {
        val obj = entry as? JsonObject ?: return Branding(null, null)
        val title = (obj["titles"] as? kotlinx.serialization.json.JsonArray).orEmpty()
            .asSequence()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { usable(it) && it.string("title") != null }
            ?.string("title")
            ?.let(::cleanTitle)
        val time = (obj["thumbnails"] as? kotlinx.serialization.json.JsonArray).orEmpty()
            .asSequence()
            .mapNotNull { it as? JsonObject }
            .firstOrNull { usable(it) && (it["timestamp"] as? JsonPrimitive)?.contentOrNull != null }
            ?.let { (it["timestamp"] as JsonPrimitive).contentOrNull }
        return Branding(
            title = title,
            thumbnailUrl = time?.let { "https://dearrow-thumb.ajay.app/api/v1/getThumbnail?videoID=$videoId&time=$it" },
        )
    }

    /** Replacements only (not the original), and only those the community has not voted down. */
    private fun usable(item: JsonObject): Boolean {
        val original = (item["original"] as? JsonPrimitive)?.booleanOrNull == true
        val locked = (item["locked"] as? JsonPrimitive)?.booleanOrNull == true
        val votes = (item["votes"] as? JsonPrimitive)?.longOrNull ?: 0
        return !original && (locked || votes >= 0)
    }

    /** DeArrow marks words that must keep their case with a leading ">". */
    fun cleanTitle(title: String): String = title.replace(Regex("(^|\\s)>(\\S)"), "$1$2").trim()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
