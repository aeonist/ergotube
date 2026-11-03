package app.freshtuber.core.network

import okhttp3.HttpUrl

/**
 * Supplies the `Cookie` header for outgoing extractor requests.
 *
 * The account implementation arrives in stage 2. Callers must still check [CookieHostPolicy]
 * before attaching anything: cookies must never leave youtube.com / googlevideo.com.
 */
fun interface CookieProvider {
    fun cookieHeaderFor(url: HttpUrl): String?
}

/** Anonymous mode: no account cookies at all. */
object NoCookies : CookieProvider {
    override fun cookieHeaderFor(url: HttpUrl): String? = null
}

/** The only hosts that may ever receive account cookies. */
object CookieHostPolicy {
    private val allowedDomains = listOf("youtube.com", "googlevideo.com")

    fun isAllowed(host: String): Boolean {
        val normalized = host.lowercase().trimEnd('.')
        return allowedDomains.any { domain ->
            normalized == domain || normalized.endsWith(".$domain")
        }
    }
}

object CookieHeader {
    /** "a=1; b=2" to a map. Values stay sensitive. */
    fun parse(header: String?): Map<String, String> {
        if (header.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        header.split(';').forEach { part ->
            val pair = part.trim()
            val name = pair.substringBefore('=').trim()
            if (name.isNotEmpty() && '=' in pair) result[name] = pair.substringAfter('=').trim()
        }
        return result
    }

    /**
     * Merges several `Cookie` header values into one. When the same cookie name appears more
     * than once, the later value wins, so request-specific cookies can override stored ones.
     */
    fun merge(vararg headers: String?): String? {
        val merged = LinkedHashMap<String, String>()
        headers.filterNotNull().forEach { header ->
            header.split(';').forEach { part ->
                val pair = part.trim()
                if (pair.isEmpty()) return@forEach
                val name = pair.substringBefore('=').trim()
                if (name.isNotEmpty()) merged[name] = pair
            }
        }
        return merged.values.joinToString("; ").ifEmpty { null }
    }
}
