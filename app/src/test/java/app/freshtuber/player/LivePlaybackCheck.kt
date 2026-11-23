package app.freshtuber.player

import app.freshtuber.core.network.ExtractorDownloader
import app.freshtuber.core.network.NoCookies
import app.freshtuber.core.newpipe.NewPipeInitializer
import app.freshtuber.innertube.parse.PlayerResponseParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Live check against real YouTube, for the daily "did YouTube break playback" run.
 *
 * For each video it tries both ways [StreamResolver] gets streams: the quick visionOS `player`
 * request ([VisionOsPlayer] + [QuickStart]) and NewPipeExtractor's full extraction, then
 * downloads the first 64 KB of the chosen audio and video streams through [GoogleVideoInterceptor],
 * exactly as the player asks for them. A video fails only when neither way gives bytes (the app
 * could not play it); one way failing is a warning. A live stream must give an HLS manifest.
 *
 * Off unless ERGOTUBE_LIVE=1 (see scripts/check-playback.sh), so CI never touches the network.
 * The report goes to app/build/playback-check/report.txt.
 */
class LivePlaybackCheck {

    @Test
    fun youtubePlaybackStillWorks() {
        val report = StringBuilder()
        val failures = mutableListOf<String>()
        for ((id, what) in VIDEOS) {
            report.appendLine("== $id ($what)")
            val quick = step(report, "quick (visionOS player)") { quick(id) }
            val full = step(report, "full (NewPipeExtractor ${extractorVersion()})") { full(id) }
            if (!quick && !full) failures += "$id ($what): no way to get playable streams"
            else if (!quick || !full) report.appendLine("   WARN: only one way works, playback still possible")
        }
        // Live: a fixed id ends sooner or later, so take whatever 24/7 channel is on air now.
        val liveId = LIVE_CHANNELS.firstNotNullOfOrNull { onAir(it) }
        if (liveId == null) {
            report.appendLine("== live: WARN none of ${LIVE_CHANNELS.joinToString()} is on air now, live not checked")
        } else {
            report.appendLine("== $liveId (live now)")
            if (!step(report, "live HLS manifest") { live(liveId) }) failures += "$liveId (live): no HLS manifest"
        }

        val verdict = if (failures.isEmpty()) "RESULT: OK" else "RESULT: BROKEN\n" + failures.joinToString("\n") { " - $it" }
        report.appendLine().appendLine(verdict)
        File("build/playback-check").apply { mkdirs() }.resolve("report.txt").writeText(report.toString())
        println(report)
        if (failures.isNotEmpty()) fail(verdict)
    }

    /** Runs one way, writes OK/FAIL with the reason, never throws. */
    private fun step(report: StringBuilder, name: String, block: () -> String): Boolean = try {
        report.appendLine("   OK   $name: ${block()}"); true
    } catch (e: Throwable) {
        report.appendLine("   FAIL $name: ${e.javaClass.name}: ${e.message?.take(400)}")
        generateSequence(e.cause) { it.cause }.take(3).forEach { report.appendLine("        caused by ${it.javaClass.name}: ${it.message?.take(300)}") }
        false
    }

    private fun quick(id: String): String {
        val cpn = YoutubeParsingHelper.generateContentPlaybackNonce()
        val raw = VisionOsPlayer().player(id, cpn)
        val parsed = PlayerResponseParser.parse(Json.parseToJsonElement(com.grack.nanojson.JsonWriter.string(raw)) as JsonObject)
        val resolved = QuickStart.resolved(parsed, id, cpn) { true } ?: error(
            "declined: status=${parsed.status} reason=${parsed.reason} formats=${parsed.adaptive.size} " +
                "ciphered=${parsed.adaptive.count { it.ciphered }} throttled=${parsed.adaptive.count { it.url?.contains("&n=") == true }}",
        )
        return bytes(resolved.audioUrl, resolved.videoCandidates.minByOrNull { it.bitrate }?.url ?: resolved.muxedUrl)
    }

    private fun full(id: String): String {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
        val audio = StreamChoice.bestAudio(info.audioStreams)?.content
        val video = StreamChoice.candidates(info.videoOnlyStreams) { true }.minByOrNull { it.bitrate }?.url
        if (audio == null || video == null) error("no streams: audio=${info.audioStreams.size} videoOnly=${info.videoOnlyStreams.size}")
        return bytes(audio, video)
    }

    /** The video a channel's /live page points to right now, or null when it is off air. */
    private fun onAir(channel: String): String? = runCatching {
        plain.newCall(Request.Builder().url("https://www.youtube.com/$channel/live")
            .header("User-Agent", app.freshtuber.core.network.UserAgents.DESKTOP_BROWSER)
            .header("Cookie", "CONSENT=YES+; SOCS=CAI").build()).execute().use { r ->
            val html = r.body.string()
            if (!html.contains("\"isLive\":true") && !html.contains("\"isLiveNow\":true")) return@use null
            Regex("<link rel=\"canonical\" href=\"https://www\\.youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11})\"").find(html)?.groupValues?.get(1)
        }
    }.getOrNull()

    private fun live(id: String): String {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$id")
        val hls = info.hlsUrl?.takeIf { it.isNotEmpty() } ?: error("no hlsUrl, streamType=${info.streamType}")
        plain.newCall(Request.Builder().url(hls).build()).execute().use { r ->
            val body = r.body.string()
            if (r.code != 200 || !body.startsWith("#EXTM3U")) error("manifest HTTP ${r.code}: ${body.take(80)}")
        }
        return "manifest OK"
    }

    /** First 64 KB of the audio and the video stream, through the player's own interceptor. */
    private fun bytes(audio: String?, video: String?): String {
        audio ?: error("no audio URL"); video ?: error("no video URL")
        return listOf("audio" to audio, "video" to video).joinToString(", ") { (kind, url) ->
            streams.newCall(Request.Builder().url(url).header("Range", "bytes=0-65535").build()).execute().use { r ->
                val n = r.body.bytes().size
                if (r.code !in 200..299 || n < 1024) error("$kind HTTP ${r.code}, $n bytes")
                "$kind ${r.code} ${n / 1024} KB"
            }
        }
    }

    private fun extractorVersion() = runCatching { org.schabi.newpipe.extractor.NewPipe::class.java.`package`?.implementationVersion }.getOrNull() ?: "v0.26.5"

    companion object {
        private val plain = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        private val streams = plain.newBuilder().addInterceptor(GoogleVideoInterceptor()).build()

        /** Stable public videos of different kinds. */
        val VIDEOS = listOf(
            "jNQXAC9IVRw" to "first YouTube video, short",
            "dQw4w9WgXcQ" to "music video",
            "aqz-KE-bpKQ" to "long 4K film",
        )
        /** 24/7 live channels; the first one on air is checked. */
        val LIVE_CHANNELS = listOf("@LofiGirl", "@aljazeeraenglish", "@SkyNews", "@NASA")

        @BeforeClass @JvmStatic
        fun setUp() {
            assumeTrue("live check runs only with ERGOTUBE_LIVE=1", System.getenv("ERGOTUBE_LIVE") == "1")
            NewPipeInitializer.init(ExtractorDownloader(plain, NoCookies), Locale.US)
        }
    }
}
