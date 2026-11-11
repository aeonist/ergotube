package app.freshtuber.data.music

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** One line of lyrics; [timeMs] is null for plain (unsynced) text. */
@Serializable
data class LyricLine(val timeMs: Long?, val text: String)

@Serializable
data class Lyrics(val lines: List<LyricLine>) {
    val synced get() = lines.any { it.timeMs != null }

    /** Index of the line sung at [positionMs], or -1 before the first one. */
    fun lineAt(positionMs: Long): Int {
        if (!synced) return -1
        var index = -1
        lines.forEachIndexed { i, line -> if (line.timeMs != null && line.timeMs <= positionMs) index = i }
        return index
    }
}

/** LRC text as LRCLIB returns it: `[mm:ss.xx] words`, several stamps per line allowed. */
object Lrc {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordStamp = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    fun parse(text: String): Lyrics {
        val lines = mutableListOf<LyricLine>()
        text.lineSequence().forEach { raw ->
            val stamps = stamp.findAll(raw).toList()
            if (stamps.isEmpty()) return@forEach
            // Enhanced LRC times every word as <mm:ss.xx>; the line keeps only its own time.
            val words = raw.substring(stamps.last().range.last + 1).replace(wordStamp, "").replace(Regex("\\s+"), " ").trim()
            stamps.forEach { m ->
                val minutes = m.groupValues[1].toLong()
                val seconds = m.groupValues[2].toLong()
                val fraction = m.groupValues[3].let { f -> if (f.isEmpty()) 0L else f.padEnd(3, '0').take(3).toLong() }
                lines += LyricLine((minutes * 60 + seconds) * 1000 + fraction, words)
            }
        }
        return Lyrics(lines.sortedBy { it.timeMs })
    }

    fun plain(text: String): Lyrics = Lyrics(text.lines().map { LyricLine(null, it.trim()) }.dropWhile { it.text.isEmpty() }.dropLastWhile { it.text.isEmpty() })
}

/**
 * Lyrics from LRCLIB (lrclib.net), an open service without accounts. Only the artist, the
 * song title, the album and the length go there; nothing that identifies the listener.
 */
@Singleton
class MusicLyrics @Inject constructor(okHttp: OkHttpClient) {
    /** No app interceptors: nothing of the YouTube session can reach another host. */
    private val client = okHttp.newBuilder().apply { interceptors().clear(); networkInterceptors().clear() }.build()
    private val cache = LruCache<String, Lyrics>(40)
    private val missing = LruCache<String, Boolean>(80)
    private val json = Json { ignoreUnknownKeys = true }

    /** Null when nothing was found; throws on network trouble. */
    suspend fun find(track: MusicTrack): Lyrics? = withContext(Dispatchers.IO) {
        val (artist, title) = if (track.isYouTube) MusicNames.split(track.title, track.artist) else (track.artist to track.title)
        if (artist.isNullOrBlank() || title.isBlank()) return@withContext null
        val seconds = track.durationMs / 1000
        val key = "$artist|$title|$seconds"
        cache.get(key)?.let { return@withContext it }
        if (missing.get(key) == true) return@withContext null
        val exact = get(artist, title, track.album?.takeIf { !track.isYouTube }, seconds)
            ?: if (track.album != null && !track.isYouTube) get(artist, title, null, seconds) else null
        val found = exact ?: search(artist, title, seconds)
        if (found == null) missing.put(key, true) else cache.put(key, found)
        found
    }

    private fun get(artist: String, title: String, album: String?, seconds: Long): Lyrics? {
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("track_name", title)
            .apply { album?.let { addQueryParameter("album_name", it) } }
            .apply { if (seconds > 0) addQueryParameter("duration", seconds.toString()) }
            .build()
        val body = request(url.toString()) ?: return null
        return lyricsOf(json.parseToJsonElement(body) as? JsonObject ?: return null)
    }

    /** Fuzzy fallback: the closest length within 4 s among the search results. */
    private fun search(artist: String, title: String, seconds: Long): Lyrics? {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("track_name", title)
            .build()
        val body = request(url.toString()) ?: return null
        val results = (json.parseToJsonElement(body) as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        val best = results.filter { seconds <= 0 || abs((it["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0) - seconds) <= 4 }
            .sortedByDescending { it["syncedLyrics"]?.jsonPrimitive?.contentOrNull != null }
            .firstOrNull() ?: return null
        return lyricsOf(best)
    }

    private fun lyricsOf(item: JsonObject): Lyrics? {
        item["syncedLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { Lrc.parse(it) }?.takeIf { it.lines.isNotEmpty() }?.let { return it }
        return item["plainLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { Lrc.plain(it) }
    }

    private fun request(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", "ErgoTube (Android music player)").build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw java.io.IOException("lrclib ${response.code}")
            return response.body.string()
        }
    }
}
