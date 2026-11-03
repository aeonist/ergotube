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
    ): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        put("User-Agent", WebClient.USER_AGENT)
        put("Accept-Language", language)
        put("X-YouTube-Client-Name", WebClient.ID)
        put("X-YouTube-Client-Version", WebClient.VERSION)
        put("Origin", WebClient.ORIGIN)
        put("Referer", WebClient.REFERER)
        visitorData?.takeIf { it.isNotBlank() }?.let { put("X-Goog-Visitor-Id", it) }

        if (profile == null || profile.cookies.isEmpty()) return@buildMap

        put("Cookie", profile.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        val sync = DataSyncId.parse(profile.dataSyncId)
        SapisidHash.header(
            cookies = profile.cookies,
            origin = WebClient.ORIGIN,
            timestampSeconds = timestampSeconds,
            userSessionId = sync.userSessionId,
        )?.let {
            put("Authorization", it)
            put("X-Origin", WebClient.ORIGIN)
        }
        put("X-Goog-AuthUser", profile.sessionIndex.toString())
        profile.activePageId?.let { put("X-Goog-PageId", it) }
        put("X-Youtube-Bootstrap-Logged-In", "true")
    }
}
