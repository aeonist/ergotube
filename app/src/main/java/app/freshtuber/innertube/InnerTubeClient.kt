package app.freshtuber.innertube

import app.freshtuber.core.network.CookieHostPolicy
import app.freshtuber.data.account.AccountProfile
import app.freshtuber.data.account.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/** Who a request is made as. */
sealed interface CallAs {
    /** The active account if someone is signed in, otherwise anonymous. */
    data object ActiveAccount : CallAs

    data object Anonymous : CallAs

    /** A specific stored profile, used while a new login is being verified. */
    data class Profile(val id: String) : CallAs
}

sealed class InnerTubeException(message: String) : IOException(message) {
    class Http(val code: Int) : InnerTubeException("InnerTube HTTP $code")
    class RateLimited : InnerTubeException("YouTube asked for a captcha")
    class SessionExpired : InnerTubeException("YouTube no longer accepts this sign-in")
    class BadResponse : InnerTubeException("Unexpected InnerTube response")
}

/**
 * Thin client for `youtube.com/youtubei/v1/` as the desktop web client.
 *
 * It keeps the stored session fresh: rotated cookies from `Set-Cookie` and new `visitorData`
 * are written back. Nothing here is logged.
 */
@Singleton
class InnerTubeClient @Inject constructor(
    private val okHttp: OkHttpClient,
    private val sessions: SessionStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var anonymousVisitorData: String? = null

    /**
     * POSTs [body] (without `context`, which is added here) to [endpoint], e.g. "browse".
     * [asChannel] false omits the brand channel identity, as the account switcher requires.
     */
    suspend fun post(
        endpoint: String,
        body: JsonObject = JsonObject(emptyMap()),
        callAs: CallAs = CallAs.ActiveAccount,
        asChannel: Boolean = true,
    ): JsonObject = withContext(Dispatchers.IO) {
        sessions.load()
        val url = (WebClient.API_BASE + endpoint).toHttpUrl().newBuilder()
            .addQueryParameter("prettyPrint", "false")
            .build()
        val profile = resolveProfile(callAs, url)?.let { if (asChannel) it else it.copy(activePageId = null) }
        val visitorData = profile?.visitorData ?: anonymousVisitorData
        val locale = Locale.getDefault()

        val payload = buildJsonObject {
            put("context", context(locale, visitorData, profile?.activePageId))
            body.forEach { (key, value) -> put(key, value) }
        }
        val headers = InnerTubeHeaders.build(
            profile = profile,
            visitorData = visitorData,
            language = locale.toLanguageTag(),
            timestampSeconds = System.currentTimeMillis() / 1000,
        )
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()

        okHttp.newCall(request).execute().use { response ->
            if (profile != null) rotateCookies(profile.id, url, response.headers)
            when {
                response.code == 429 -> throw InnerTubeException.RateLimited()
                profile != null && response.code == 401 -> throw InnerTubeException.SessionExpired()
                !response.isSuccessful -> throw InnerTubeException.Http(response.code)
            }
            val root = runCatching { json.parseToJsonElement(response.body.string()) }.getOrNull() as? JsonObject
                ?: throw InnerTubeException.BadResponse()
            rememberVisitorData(profile?.id, root)
            if (profile != null && ResponseContext.isLoggedOut(root)) {
                throw InnerTubeException.SessionExpired()
            }
            root
        }
    }

    /**
     * Sends a playback statistics ping (`/api/stats/playback`) as the active account; this is
     * what adds a video to the account's watch history. Only youtube.com stats URLs are accepted.
     */
    suspend fun ping(statsUrl: String, extraParams: Map<String, String>) = withContext(Dispatchers.IO) {
        val parsed = statsUrl.toHttpUrl()
        require(parsed.isHttps && CookieHostPolicy.isAllowed(parsed.host) && parsed.encodedPath.startsWith("/api/stats/")) {
            "Not a YouTube stats URL"
        }
        val profile = resolveProfile(CallAs.ActiveAccount, parsed) ?: return@withContext
        val url = parsed.newBuilder().apply { extraParams.forEach { (k, v) -> setQueryParameter(k, v) } }.build()
        val headers = InnerTubeHeaders.build(
            profile = profile,
            visitorData = profile.visitorData,
            language = Locale.getDefault().toLanguageTag(),
            timestampSeconds = System.currentTimeMillis() / 1000,
        )
        val request = Request.Builder().url(url).get()
            .apply { headers.filterKeys { it != "Content-Type" }.forEach { (name, value) -> header(name, value) } }
            .build()
        okHttp.newCall(request).execute().use { response ->
            rotateCookies(profile.id, url, response.headers)
        }
    }

    private fun resolveProfile(callAs: CallAs, url: HttpUrl): AccountProfile? {
        // Account cookies only ever go to youtube.com.
        if (!CookieHostPolicy.isAllowed(url.host)) return null
        val stored = sessions.current
        return when (callAs) {
            CallAs.Anonymous -> null
            CallAs.ActiveAccount -> stored.active
            is CallAs.Profile -> stored.profiles.firstOrNull { it.id == callAs.id }
        }
    }

    private suspend fun rotateCookies(profileId: String, url: HttpUrl, headers: okhttp3.Headers) {
        val setCookies = headers.values("Set-Cookie")
        if (setCookies.isEmpty()) return
        val now = System.currentTimeMillis()
        sessions.updateProfile(profileId) { profile ->
            val rotated = CookieRotation.apply(profile.cookies, url.host, setCookies, now)
            if (rotated === profile.cookies) profile else profile.copy(cookies = rotated)
        }
    }

    private suspend fun rememberVisitorData(profileId: String?, root: JsonObject) {
        val fresh = ResponseContext.visitorData(root) ?: return
        if (profileId == null) {
            anonymousVisitorData = fresh
        } else {
            sessions.updateProfile(profileId) { profile ->
                if (profile.visitorData == fresh) profile else profile.copy(visitorData = fresh)
            }
        }
    }

    private fun context(locale: Locale, visitorData: String?, onBehalfOfUser: String?) = buildJsonObject {
        putJsonObject("client") {
            put("clientName", WebClient.NAME)
            put("clientVersion", WebClient.VERSION)
            put("hl", locale.language.ifEmpty { "en" })
            put("gl", locale.country.takeIf { it.length == 2 } ?: "US")
            visitorData?.let { put("visitorData", it) }
            put("userAgent", WebClient.USER_AGENT)
            put("platform", "DESKTOP")
            put("originalUrl", WebClient.REFERER)
            put("utcOffsetMinutes", TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000)
        }
        putJsonObject("user") {
            put("lockedSafetyMode", false)
            onBehalfOfUser?.let { put("onBehalfOfUser", it) }
        }
        putJsonObject("request") {
            put("useSsl", true)
            putJsonArray("internalExperimentFlags") {}
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

/** Fields every InnerTube response carries in `responseContext`. */
object ResponseContext {
    fun visitorData(root: JsonObject): String? =
        root.obj("responseContext").str("visitorData")?.takeIf { it.isNotBlank() }

    /** YouTube answers 200 but marks the response logged out when cookies stopped working. */
    fun isLoggedOut(root: JsonObject): Boolean =
        root.path("responseContext", "mainAppWebResponseContext").bool("loggedOut") == true
}
