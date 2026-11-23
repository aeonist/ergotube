package app.freshtuber.innertube

import app.freshtuber.core.network.UserAgents
import app.freshtuber.data.account.AccountProfile

/** The desktop web client this app speaks as. Version checked against yt-dlp and Metrolist (2026-07). */
object WebClient {
    const val NAME = "WEB"
    const val ID = "1"
    const val VERSION = "2.20260708.00.00"
    const val ORIGIN = "https://www.youtube.com"
    const val REFERER = "https://www.youtube.com/"
    const val API_BASE = "https://www.youtube.com/youtubei/v1/"
    const val USER_AGENT = UserAgents.DESKTOP_BROWSER
}

internal const val MUSIC_ORIGIN = "https://music.youtube.com"

enum class BrowseClient(val clientName: String, val id: String, val version: String, val origin: String) {
    Web(WebClient.NAME, WebClient.ID, WebClient.VERSION, WebClient.ORIGIN),
    Music("WEB_REMIX", "67", "1.20260121.03.00", MUSIC_ORIGIN),
}

/**
 * Builds request headers. Pure, so it can be unit-tested.
 *
 * [profile] null means an anonymous request: no cookies and no Authorization.
 */
object InnerTubeHeaders {

    fun build(
        profile: AccountProfile?,
        visitorData: String?,
        language: String,
        timestampSeconds: Long,
        client: BrowseClient = BrowseClient.Web,
    ): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        put("User-Agent", WebClient.USER_AGENT)
        put("Accept-Language", language)
        put("X-YouTube-Client-Name", client.id)
        put("X-YouTube-Client-Version", client.version)
        put("Origin", client.origin)
        put("Referer", "${client.origin}/")
        visitorData?.takeIf { it.isNotBlank() }?.let { put("X-Goog-Visitor-Id", it) }

        if (profile == null || profile.cookies.isEmpty()) return@buildMap

        put("Cookie", profile.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        val sync = DataSyncId.parse(profile.dataSyncId)
        SapisidHash.header(
            cookies = profile.cookies,
            origin = client.origin,
            timestampSeconds = timestampSeconds,
            userSessionId = sync.userSessionId,
        )?.let {
            put("Authorization", it)
            put("X-Origin", client.origin)
        }
        put("X-Goog-AuthUser", profile.sessionIndex.toString())
        profile.activePageId?.let { put("X-Goog-PageId", it) }
        put("X-Youtube-Bootstrap-Logged-In", "true")
    }
}
