package app.freshtuber.innertube

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Applies `Set-Cookie` headers from a youtube.com response to a stored cookie jar.
 *
 * Expired cookies are removed; new and changed ones are stored; cookies for any domain other
 * than youtube.com are ignored. Returns [current] itself when nothing changes, so callers can
 * skip a write.
 */
object CookieRotation {

    data class SetCookie(val name: String, val value: String, val domain: String?, val expired: Boolean)

    fun apply(
        current: Map<String, String>,
        host: String,
        setCookieHeaders: List<String>,
        nowMillis: Long,
    ): Map<String, String> {
        if (setCookieHeaders.isEmpty()) return current
        val updated = LinkedHashMap(current)
        for (header in setCookieHeaders) {
            val cookie = parse(header, nowMillis) ?: continue
            val domain = (cookie.domain ?: host).trimStart('.').lowercase()
            if (domain != "youtube.com" && !domain.endsWith(".youtube.com")) continue
            if (cookie.expired) updated.remove(cookie.name) else updated[cookie.name] = cookie.value
        }
        return if (updated == current) current else updated
    }

    fun parse(header: String, nowMillis: Long): SetCookie? {
        val parts = header.split(';')
        val pair = parts.first().trim()
        val name = pair.substringBefore('=', missingDelimiterValue = "").trim()
        if (name.isEmpty()) return null
        val value = pair.substringAfter('=').trim()

        var domain: String? = null
        var maxAge: Long? = null
        var expiresAt: Long? = null
        for (attribute in parts.drop(1)) {
            val key = attribute.substringBefore('=').trim().lowercase()
            val attrValue = attribute.substringAfter('=', missingDelimiterValue = "").trim()
            when (key) {
                "domain" -> domain = attrValue.ifEmpty { null }
                "max-age" -> maxAge = attrValue.toLongOrNull()
                "expires" -> expiresAt = parseHttpDate(attrValue)
            }
        }
        // Max-Age wins over Expires (RFC 6265, 5.3).
        val expired = when {
            maxAge != null -> maxAge <= 0
            expiresAt != null -> expiresAt <= nowMillis
            else -> false
        }
        return SetCookie(name, value, domain, expired)
    }

    /** "Wed, 21 Oct 2026 07:28:00 GMT" or the older "Mon, 01-Jan-1990 00:00:00 GMT". */
    private fun parseHttpDate(raw: String): Long? = runCatching {
        val normalized = raw.replace('-', ' ')
        ZonedDateTime.parse(normalized, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrNull()
}
