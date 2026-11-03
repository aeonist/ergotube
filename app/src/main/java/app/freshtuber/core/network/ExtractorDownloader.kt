package app.freshtuber.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NewPipeExtractor's HTTP layer on top of OkHttp.
 *
 * Nothing here is logged: request headers may carry account cookies.
 */
@Singleton
class ExtractorDownloader @Inject constructor(
    private val client: OkHttpClient,
    private val cookieProvider: CookieProvider,
) : Downloader() {

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val url = request.url().toHttpUrl()
        val method = request.httpMethod()
        val headers = request.headers()

        val contentType = headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value?.firstOrNull()
            ?.toMediaTypeOrNull()
        val body = request.dataToSend()?.toRequestBody(contentType)
            ?: if (method in METHODS_WITH_BODY) ByteArray(0).toRequestBody(contentType) else null

        val builder = okhttp3.Request.Builder()
            .url(url)
            .method(method, body)
            .header("User-Agent", UserAgents.DESKTOP_BROWSER)

        // The extractor sets its own User-Agent for mobile clients, so request headers win.
        var requestCookies: String? = null
        headers.forEach { (name, values) ->
            if (name.equals("Cookie", ignoreCase = true)) {
                requestCookies = values.joinToString("; ")
            } else {
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }
        }

        val storedCookies = if (CookieHostPolicy.isAllowed(url.host)) {
            cookieProvider.cookieHeaderFor(url)
        } else {
            null
        }
        CookieHeader.merge(storedCookies, requestCookies)?.let { builder.header("Cookie", it) }

        client.newCall(builder.build()).execute().use { response ->
            val finalUrl = response.request.url
            if (response.code == 429 || finalUrl.isCaptchaPage()) {
                throw ReCaptchaException("reCAPTCHA challenge requested", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                finalUrl.toString(),
            )
        }
    }

    private fun HttpUrl.isCaptchaPage(): Boolean =
        host.endsWith("google.com") && encodedPath.startsWith("/sorry")

    private companion object {
        val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH")
    }
}
