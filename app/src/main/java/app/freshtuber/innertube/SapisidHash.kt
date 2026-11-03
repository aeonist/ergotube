package app.freshtuber.innertube

import java.security.MessageDigest

/**
 * The Authorization header YouTube's web client sends for cookie sessions.
 *
 * Each part is `SCHEME timestamp_sha1hex`, where the hash covers "timestamp sid origin".
 * When the user session id is known (from DATASYNC_ID) the hash covers
 * "userSessionId timestamp sid origin" and the part gets a `_u` suffix.
 * Matches yt-dlp's `_get_sid_authorization_header`.
 */
object SapisidHash {

    /** One part, e.g. `SAPISIDHASH 1700000000_a9c9…`. */
    fun part(
        scheme: String,
        sid: String,
        origin: String,
        timestampSeconds: Long,
        userSessionId: String? = null,
    ): String {
        val hashInput = buildString {
            if (!userSessionId.isNullOrEmpty()) append(userSessionId).append(' ')
            append(timestampSeconds).append(' ').append(sid).append(' ').append(origin)
        }
        val suffix = if (!userSessionId.isNullOrEmpty()) "_u" else ""
        return "$scheme ${timestampSeconds}_${sha1Hex(hashInput)}$suffix"
    }

    /**
     * Full header value from youtube.com cookies, or null when the jar has no SAPISID-type cookie.
     * SAPISID falls back to __Secure-3PAPISID, the same way YouTube does.
     */
    fun header(
        cookies: Map<String, String>,
        origin: String,
        timestampSeconds: Long,
        userSessionId: String? = null,
    ): String? {
        val onePartyApisid = cookies["__Secure-1PAPISID"]?.takeIf { it.isNotEmpty() }
        val threePartyApisid = cookies["__Secure-3PAPISID"]?.takeIf { it.isNotEmpty() }
        val sapisid = cookies["SAPISID"]?.takeIf { it.isNotEmpty() } ?: threePartyApisid
        val parts = listOfNotNull(
            sapisid?.let { part("SAPISIDHASH", it, origin, timestampSeconds, userSessionId) },
            onePartyApisid?.let { part("SAPISID1PHASH", it, origin, timestampSeconds, userSessionId) },
            threePartyApisid?.let { part("SAPISID3PHASH", it, origin, timestampSeconds, userSessionId) },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    internal fun sha1Hex(input: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(input.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
}

/** DATASYNC_ID is "userSessionId||" for the main channel and "pageId||userSessionId" for a brand channel. */
data class DataSyncId(val delegatedSessionId: String?, val userSessionId: String?) {
    companion object {
        fun parse(raw: String?): DataSyncId {
            if (raw.isNullOrBlank()) return DataSyncId(null, null)
            val first = raw.substringBefore("||")
            val second = raw.substringAfter("||", missingDelimiterValue = "")
            return if (second.isNotEmpty()) {
                DataSyncId(first.ifEmpty { null }, second)
            } else {
                DataSyncId(null, first.ifEmpty { null })
            }
        }
    }
}
