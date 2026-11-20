package app.freshtuber.player

import kotlinx.coroutines.Job

/**
 * The pure decisions behind starting a video sooner, kept apart for tests: which bytes of
 * which files to fetch into the playback cache before the player asks for them.
 *
 * A DASH stream starts with two small reads, its header (`initRange`) and its index
 * (`indexRange`), before the first fragment; a Short plays one progressive file from byte 0.
 * Both are fetched under the same URL the player will use, so the player's cache key (the
 * URL, see [StartPrefetchTest]) finds them.
 */
object StartPrefetch {

    /** [length] bytes of [url] starting at [position]. */
    data class Range(val url: String, val position: Long, val length: Long)

    /** First bytes of a Short's video file: its header and about the first second. */
    const val SHORT_VIDEO_BYTES = 256L * 1024
    const val SHORT_AUDIO_BYTES = 64L * 1024
    const val SHORT_MUXED_BYTES = 320L * 1024
    /** A header and index above this are unusual; the player then reads them itself. */
    const val MAX_HEADER_BYTES = 256L * 1024
    /** Share of the measured speed Media3's adaptive selection spends (its default). */
    const val BANDWIDTH_FRACTION = 0.7

    /** The header and the index of a stream as one read (whatever lies between them too). */
    fun header(url: String, ranges: SegmentRanges?): Range? {
        val r = ranges ?: return null
        if (r.initStart < 0) return null
        val length = r.indexEnd - r.initStart + 1
        if (length <= 0 || length > MAX_HEADER_BYTES) return null
        return Range(url, r.initStart, length)
    }

    /**
     * The video streams the player most likely opens first: the highest bitrate within
     * [capHeight] that fits [BANDWIDTH_FRACTION] of [bitrateEstimate] (what Media3's adaptive
     * selection does), plus the one below it in case the estimate is a little high.
     */
    fun firstVideoTracks(adaptive: List<VideoTrack>, capHeight: Int, bitrateEstimate: Long): List<VideoTrack> {
        val allowed = adaptive.filter { it.height <= capHeight }.ifEmpty { adaptive }.sortedBy { it.bitrate }
        if (allowed.isEmpty()) return emptyList()
        val budget = bitrateEstimate * BANDWIDTH_FRACTION
        val index = allowed.indexOfLast { it.bitrate <= budget }.coerceAtLeast(0)
        return listOfNotNull(allowed[index], allowed.getOrNull(index - 1))
    }

    /** Header + index of the likely first video streams and of the audio stream. */
    fun dash(adaptive: List<VideoTrack>, audio: AudioTrack, capHeight: Int, bitrateEstimate: Long): List<Range> =
        (firstVideoTracks(adaptive, capHeight, bitrateEstimate).map { header(it.url, it.ranges) } + header(audio.url, audio.ranges))
            .filterNotNull()
            .distinctBy { it.url }

    /** The first bytes of a progressive start (a Short): video + audio, or the combined file. */
    fun progressive(videoUrl: String?, audioUrl: String?, muxedUrl: String?): List<Range> = when {
        videoUrl != null && audioUrl != null -> listOf(first(videoUrl, SHORT_VIDEO_BYTES), first(audioUrl, SHORT_AUDIO_BYTES))
        muxedUrl != null -> listOf(first(muxedUrl, SHORT_MUXED_BYTES))
        else -> emptyList()
    }

    /** [bytes] from the start, but never past the file's `clen`. */
    fun first(url: String, bytes: Long): Range = Range(url, 0, minOf(bytes, ChunkPlan.streamLength(url) ?: bytes))

    /** The googlevideo host serving [url]; null for anything else (no other host is pre-opened). */
    fun host(url: String): String? {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        return uri.host?.takeIf { it.endsWith(".googlevideo.com") }
    }
}

/**
 * Opens each googlevideo host at most once per [windowMs]: within it the connection is still
 * in the pool (googlevideo closes idle connections well before OkHttp's five minutes, so the
 * window is shorter than the pool's own keep-alive).
 */
class HostGate(private val windowMs: Long = 60_000L, private val clock: () -> Long = System::currentTimeMillis) {
    private val opened = HashMap<String, Long>()

    /** True when [host] should be opened now; it then counts as open for [windowMs]. */
    @Synchronized
    fun claim(host: String): Boolean {
        val now = clock()
        opened.entries.removeAll { now - it.value >= windowMs }
        if (host in opened) return false
        opened[host] = now
        return true
    }

    /** A failed attempt: the next one may try again at once. */
    @Synchronized
    fun forget(host: String) { opened.remove(host) }
}

/**
 * The one start prefetch in flight: a press warms one video at a time. Starting the same video
 * again keeps the running job; another video replaces it.
 */
class StartSlot {
    private var id: String? = null
    private var job: Job? = null

    /** Runs [launch] for [videoId] unless that video is already being fetched. */
    @Synchronized
    fun start(videoId: String, launch: () -> Job): Boolean {
        if (id == videoId && job?.isActive == true) return false
        job?.cancel()
        id = videoId
        job = launch()
        return true
    }

    /** The running job for [videoId], if any. */
    @Synchronized
    fun active(videoId: String): Job? = job?.takeIf { id == videoId && it.isActive }

    /** Cancels the fetch of [videoId] (a press that became a scroll). */
    @Synchronized
    fun cancel(videoId: String) { if (id == videoId) clear() }

    /** Another video is opening: the fetch for a different one only competes with it. */
    @Synchronized
    fun cancelOthers(videoId: String) { if (id != null && id != videoId) clear() }

    @Synchronized
    fun cancelAll() = clear()

    private fun clear() { job?.cancel(); job = null; id = null }
}
