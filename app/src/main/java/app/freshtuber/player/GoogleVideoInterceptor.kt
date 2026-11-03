package app.freshtuber.player

import app.freshtuber.core.network.UserAgents
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.concurrent.atomic.AtomicLong

/**
 * Requests to googlevideo.com shaped the way YouTube's own players make them (as NewPipe's
 * YoutubeHttpDataSource does): the byte range goes in a `range` query parameter instead of the
 * Range header, a request number `rn` is added, and the request is a POST with a two-byte body.
 *
 * With `range` YouTube answers 200 for a partial body, so 200 is turned into 206 for Media3,
 * which would otherwise skip the requested number of bytes a second time.
 */
class GoogleVideoInterceptor : Interceptor {

    private val requestNumber = AtomicLong(0)

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url
        if (!url.host.endsWith("googlevideo.com") || !url.encodedPath.startsWith("/videoplayback")) {
            return chain.proceed(original)
        }

        val range = original.header("Range")?.let(::parseRange)
        val newUrl = url.newBuilder().apply {
            if (url.queryParameter("rn") == null) addQueryParameter("rn", requestNumber.getAndIncrement().toString())
            if (range != null) addQueryParameter("range", range)
        }.build()

        val raw = newUrl.toString()
        val request: Request = original.newBuilder()
            .url(newUrl)
            .removeHeader("Range")
            .header(
                "User-Agent",
                if (YoutubeParsingHelper.isVisionOsStreamingUrl(raw)) YoutubeParsingHelper.getVisionOsUserAgent(null)
                else UserAgents.DESKTOP_BROWSER,
            )
            .header("TE", "trailers")
            .apply {
                if (YoutubeParsingHelper.isWebStreamingUrl(raw)) {
                    header("Origin", "https://www.youtube.com")
                    header("Referer", "https://www.youtube.com/")
                    header("Sec-Fetch-Dest", "empty")
                    header("Sec-Fetch-Mode", "cors")
                    header("Sec-Fetch-Site", "cross-site")
                }
            }
            .post(POST_BODY.toRequestBody())
            .build()

        val response = chain.proceed(request)
        return if (range != null && response.code == 200) response.newBuilder().code(206).build() else response
    }

    /** "bytes=100-" or "bytes=100-199" to "100-" / "100-199". */
    private fun parseRange(header: String): String? =
        header.removePrefix("bytes=").takeIf { it.isNotEmpty() && it != "0-" }

    private companion object {
        val POST_BODY = byteArrayOf(0x78, 0)
    }
}
