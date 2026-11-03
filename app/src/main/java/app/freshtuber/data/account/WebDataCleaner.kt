package app.freshtuber.data.account

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import coil3.SingletonImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Wipes what the login WebView and the image cache leave behind. */
@Singleton
class WebDataCleaner @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Cookies, DOM storage, form data and the WebView HTTP cache. */
    suspend fun clearWebViewData() = withContext(Dispatchers.Main) {
        val cookies = CookieManager.getInstance()
        suspendCancellableCoroutine { continuation ->
            cookies.removeAllCookies { continuation.resume(Unit) }
        }
        cookies.flush()
        WebStorage.getInstance().deleteAllData()
        WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
        runCatching {
            WebView(context).apply {
                clearCache(true)
                clearFormData()
                destroy()
            }
        }
    }

    /** App cache directory (thumbnails, avatars) and the in-memory image cache. */
    suspend fun clearCaches() {
        withContext(Dispatchers.Main) {
            SingletonImageLoader.get(context).memoryCache?.clear()
        }
        withContext(Dispatchers.IO) {
            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
    }
}
