package app.freshtuber.data.account

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder

/** Pure rules for the login WebView, kept apart from Android views so they can be tested. */
object LoginCaptureRules {

    /** The same sign-in link youtube.com uses, landing on the mobile site after login. */
    fun loginUrl(language: String): String =
        "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true" +
            "&hl=" + URLEncoder.encode(language, "UTF-8") +
            "&continue=" + URLEncoder.encode(
                "https://www.youtube.com/signin?action_handle_signin=true&app=m&next=https%3A%2F%2Fm.youtube.com%2F",
                "UTF-8",
            )

    /** Signed in as far as cookies tell: a SAPISID-type cookie exists for youtube.com. */
    fun isSignedIn(cookies: Map<String, String>): Boolean =
        SIGNED_IN_COOKIES.any { !cookies[it].isNullOrEmpty() }

    fun isYouTubePage(host: String?): Boolean =
        host != null && (host == "youtube.com" || host.endsWith(".youtube.com"))

    /** Google's "This browser or app may not be secure" page. */
    fun isRejectedPage(host: String?, path: String?): Boolean =
        host == "accounts.google.com" && path != null &&
            (path.contains("/signin/rejected") || path.contains("disallowed_useragent"))

    /** Reads what YouTube's page config knows about the session. Runs in the page. */
    const val READ_CONFIG_JS =
        "(function(){var c=(window.ytcfg&&window.ytcfg.data_)||(window.yt&&window.yt.config_)||{};" +
            "return JSON.stringify({v:c.VISITOR_DATA||null,d:c.DATASYNC_ID||null," +
            "s:(c.SESSION_INDEX!=null?String(c.SESSION_INDEX):null),l:(c.LOGGED_IN===true)});})()"

    data class PageConfig(
        val visitorData: String?,
        val dataSyncId: String?,
        val sessionIndex: Int,
        val loggedIn: Boolean,
    )

    /** `evaluateJavascript` hands back the returned string JSON-encoded once more. */
    fun parseConfigResult(raw: String?): PageConfig? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            val inner = (Json.parseToJsonElement(raw) as JsonPrimitive).content
            val obj = Json.parseToJsonElement(inner) as JsonObject
            PageConfig(
                visitorData = (obj["v"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
                dataSyncId = (obj["d"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
                sessionIndex = (obj["s"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
                loggedIn = (obj["l"] as? JsonPrimitive)?.booleanOrNull == true,
            )
        }.getOrNull()
    }

    private val SIGNED_IN_COOKIES = listOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID")
}
