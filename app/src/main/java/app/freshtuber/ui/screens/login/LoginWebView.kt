package app.freshtuber.ui.screens.login

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.freshtuber.core.network.CookieHeader
import app.freshtuber.data.account.LoginCapture
import app.freshtuber.data.account.LoginCaptureRules
import java.util.Locale

/** Callbacks from the login WebView. All are called on the main thread. */
interface LoginWebViewEvents {
    fun onCaptured(capture: LoginCapture)
    fun onRejected()
    fun onLoadFailed()
    fun onProgress(percent: Int)
    fun onCanGoBack(canGoBack: Boolean)
}

/**
 * Google sign-in page. Once youtube.com shows the user signed in, it reads the youtube.com
 * cookies plus visitorData / DATASYNC_ID / SESSION_INDEX from the page config and reports them.
 *
 * [compatMode] presents the WebView as mobile Chrome, for when Google rejects embedded browsers.
 */
@Composable
fun LoginWebView(
    compatMode: Boolean,
    events: LoginWebViewEvents,
    onWebView: (WebView?) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                configure(context, compatMode)
                webViewClient = LoginClient(events)
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        events.onProgress(newProgress)
                    }
                }
                onWebView(this)
                loadUrl(LoginCaptureRules.loginUrl(Locale.getDefault().toLanguageTag()))
            }
        },
        onRelease = { webView ->
            onWebView(null)
            webView.stopLoading()
            webView.destroy()
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configure(context: Context, compatMode: Boolean) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.setSupportMultipleWindows(false)
    settings.javaScriptCanOpenWindowsAutomatically = false

    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

    // Current WebView no longer sends X-Requested-With (the app's package name), so nothing to strip.
    if (compatMode) presentAsChrome(context)
}

/** Same engine, Chrome's identity: drops the "; wv" marker and the "Android WebView" brand. */
private fun WebView.presentAsChrome(context: Context) {
    val major = WebViewCompat.getCurrentWebViewPackage(context)?.versionName
        ?.substringBefore('.')
        ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        ?: "140"
    settings.userAgentString =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/$major.0.0.0 Mobile Safari/537.36"
    if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
        fun brand(name: String, version: String) = UserAgentMetadata.BrandVersion.Builder()
            .setBrand(name)
            .setMajorVersion(version)
            .setFullVersion("$version.0.0.0")
            .build()
        val metadata = UserAgentMetadata.Builder(WebSettingsCompat.getUserAgentMetadata(settings))
            .setBrandVersionList(
                listOf(brand("Google Chrome", major), brand("Chromium", major), brand("Not_A Brand", "24")),
            )
            .setFullVersion("$major.0.0.0")
            .build()
        WebSettingsCompat.setUserAgentMetadata(settings, metadata)
    }
}

private class LoginClient(private val events: LoginWebViewEvents) : WebViewClient() {
    private var captured = false

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        // Stay on the web: no intent:// or other app links from inside the sign-in flow.
        val scheme = request.url.scheme
        return scheme != "https" && scheme != "http"
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        val uri = url?.let(Uri::parse) ?: return
        if (LoginCaptureRules.isRejectedPage(uri.host, uri.path)) events.onRejected()
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        events.onCanGoBack(view.canGoBack())
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame && !captured) events.onLoadFailed()
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (captured) return
        val uri = url?.let(Uri::parse) ?: return
        if (!LoginCaptureRules.isYouTubePage(uri.host)) return
        val cookies = CookieHeader.parse(CookieManager.getInstance().getCookie(YOUTUBE_URL))
        if (!LoginCaptureRules.isSignedIn(cookies)) return
        captured = true
        readConfig(view, attempt = 0)
    }

    /** The page config may need a moment after load; retry a few times before going without it. */
    private fun readConfig(view: WebView, attempt: Int) {
        view.evaluateJavascript(LoginCaptureRules.READ_CONFIG_JS) { raw ->
            val config = LoginCaptureRules.parseConfigResult(raw)
            if (config?.visitorData == null && attempt < CONFIG_RETRIES) {
                view.postDelayed({ readConfig(view, attempt + 1) }, CONFIG_RETRY_DELAY_MS)
                return@evaluateJavascript
            }
            CookieManager.getInstance().flush()
            val cookies = CookieHeader.parse(CookieManager.getInstance().getCookie(YOUTUBE_URL))
            events.onCaptured(
                LoginCapture(
                    cookies = cookies,
                    visitorData = config?.visitorData,
                    dataSyncId = config?.dataSyncId,
                    sessionIndex = config?.sessionIndex ?: 0,
                ),
            )
        }
    }

    private companion object {
        const val YOUTUBE_URL = "https://www.youtube.com"
        const val CONFIG_RETRIES = 6
        const val CONFIG_RETRY_DELAY_MS = 500L
    }
}
