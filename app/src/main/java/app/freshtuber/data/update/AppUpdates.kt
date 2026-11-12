package app.freshtuber.data.update

import android.content.Context
import app.freshtuber.BuildConfig
import app.freshtuber.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A newer build published on GitHub. [url] opens its release page or the APK itself. */
data class AppUpdate(val build: Int, val name: String, val url: String)

/** What a GitHub release says, reduced to what the banner needs. Pure, for tests. */
object ReleaseInfo {
    private val build = Regex("""(?i)build[-_ ]?(\d{1,6})""")

    /** The build number a release names in its tag or title ("build28", "v1.3-build28"). */
    fun buildOf(tag: String?, name: String?): Int? =
        listOfNotNull(tag, name).firstNotNullOfOrNull { build.find(it)?.groupValues?.get(1)?.toIntOrNull() }

    /** The newest release (GitHub's /releases list) with a build above [current]; drafts are skipped. */
    fun newer(releases: JsonArray, current: Int, includePre: Boolean = true): AppUpdate? = releases.mapNotNull { element ->
        val release = element as? JsonObject ?: return@mapNotNull null
        fun text(key: String) = release[key]?.jsonPrimitive?.contentOrNull
        if (text("draft") == "true" || (!includePre && text("prerelease") == "true")) return@mapNotNull null
        val number = buildOf(text("tag_name"), text("name")) ?: return@mapNotNull null
        if (number <= current) return@mapNotNull null
        val apk = (release["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true && it["name"]?.jsonPrimitive?.contentOrNull?.contains("release") == true }
            ?: (release["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
        val url = apk?.get("browser_download_url")?.jsonPrimitive?.contentOrNull ?: text("html_url") ?: return@mapNotNull null
        AppUpdate(number, text("name")?.takeIf(String::isNotBlank) ?: text("tag_name").orEmpty(), url)
    }.maxByOrNull { it.build }
}

/**
 * Looks for a newer ErgoTube on GitHub Releases, at most every six hours. The request goes to
 * api.github.com only, carries no account data, and a failure just means no banner.
 */
@Singleton
class AppUpdates @Inject constructor(
    @param:ApplicationContext private val context: Context,
    okHttp: OkHttpClient,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val client = okHttp.newBuilder().apply { interceptors().clear() }.callTimeout(15, TimeUnit.SECONDS).build()
    private val memory by lazy { context.getSharedPreferences("app_update", Context.MODE_PRIVATE) }
    private val _available = MutableStateFlow<AppUpdate?>(null)
    val available: StateFlow<AppUpdate?> = _available.asStateFlow()
    private val _collapsed = MutableStateFlow(false)
    /** «Позже» folds the banner into a corner chip for this build; a newer build opens it again. */
    val collapsed: StateFlow<Boolean> = _collapsed.asStateFlow()

    fun start() {
        restore()
        scope.launch {
            if (System.currentTimeMillis() - memory.getLong(CHECKED, 0) < CHECK_EVERY_MS && memory.contains(BUILD)) return@launch
            try { check() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* No banner this time. */ }
        }
    }

    private fun restore() {
        val build = memory.getInt(BUILD, 0)
        if (build > BuildConfig.VERSION_CODE) {
            _available.value = AppUpdate(build, memory.getString(NAME, "").orEmpty(), memory.getString(URL, "").orEmpty())
            _collapsed.value = memory.getInt(COLLAPSED, 0) == build
        }
    }

    private suspend fun check() = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=10")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "ErgoTube").build()
        val body = client.newCall(request).execute().use { if (it.isSuccessful) it.body.string() else null } ?: return@withContext
        val update = ReleaseInfo.newer(Json.parseToJsonElement(body) as? JsonArray ?: return@withContext, BuildConfig.VERSION_CODE)
        memory.edit().putLong(CHECKED, System.currentTimeMillis()).putInt(BUILD, update?.build ?: 0)
            .putString(NAME, update?.name).putString(URL, update?.url).apply()
        _available.value = update
        _collapsed.value = update != null && memory.getInt(COLLAPSED, 0) == update.build
    }

    fun collapse(collapsed: Boolean) {
        val build = _available.value?.build ?: return
        _collapsed.value = collapsed
        memory.edit().putInt(COLLAPSED, if (collapsed) build else 0).apply()
    }

    private companion object {
        const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
        const val CHECKED = "checked_at"
        const val BUILD = "build"
        const val NAME = "name"
        const val URL = "url"
        const val COLLAPSED = "collapsed_build"
    }
}
