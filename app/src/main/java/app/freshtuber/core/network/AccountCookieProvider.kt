package app.freshtuber.core.network

import app.freshtuber.data.account.SessionStore
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Account cookies for NewPipeExtractor, but only inside [withAccountCookies] on the calling
 * thread. Playback stays anonymous by default; this is the age-restricted retry.
 * The extractor makes its requests synchronously on the caller's thread, so a thread-local
 * switch scopes the cookies to exactly one extraction.
 */
@Singleton
class AccountCookieProvider @Inject constructor(
    private val sessions: SessionStore,
) : CookieProvider {

    private val enabled = ThreadLocal<Boolean>()

    override fun cookieHeaderFor(url: HttpUrl): String? {
        if (enabled.get() != true) return null
        val cookies = sessions.current.active?.cookies ?: return null
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }.ifEmpty { null }
    }

    fun <T> withAccountCookies(block: () -> T): T {
        enabled.set(true)
        try {
            return block()
        } finally {
            enabled.remove()
        }
    }
}
