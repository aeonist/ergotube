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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A newer ErgoTube published on GitHub. [build] is 0 when the release names no build number;
 * [key] tells releases apart (its tag). [url] opens the APK itself, or else the release page.
 */
data class AppUpdate(val build: Int, val name: String, val url: String, val key: String = "build$build")

/** What a GitHub release says, reduced to what the banner needs. Pure, for tests. */
object ReleaseInfo {
    /** «build32», «build 32», «Build-32», «сборка 32» in a tag, a title or an APK's file name. */
    private val build = Regex("""(?i)(?:build|сборк[аиу])[-_ .#№]*(\d{1,6})""")

    /** The build number a release names in its tag, title or APK names ("build28", "ErgoTube-build32-release.apk"). */
    fun buildOf(vararg texts: String?): Int? =
        texts.filterNotNull().firstNotNullOfOrNull { build.find(it)?.groupValues?.get(1)?.toIntOrNull() }

    /**
     * The newest release (GitHub's /releases list, drafts skipped) that is newer than this app:
     * one whose number is above [current], or, when a release names no number anywhere, one
     * published after [installedAt] (when this build was put on the phone). [debug] prefers a
     * debug APK, so an update replaces the same app instead of installing a second one.
     */
    fun newer(releases: JsonArray, current: Int, installedAt: Long = Long.MAX_VALUE, debug: Boolean = false, includePre: Boolean = true): AppUpdate? =
        releases.mapNotNull { element ->
            val release = element as? JsonObject ?: return@mapNotNull null
            fun text(key: String) = release[key]?.jsonPrimitive?.contentOrNull
            if (text("draft") == "true" || (!includePre && text("prerelease") == "true")) return@mapNotNull null
            val apks = (release["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .filter { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk", ignoreCase = true) == true }
            fun nameOf(asset: JsonObject) = asset["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val number = buildOf(text("tag_name"), text("name")) ?: buildOf(*apks.map(::nameOf).toTypedArray())
            val published = text("published_at")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
            when {
                number != null && number <= current -> return@mapNotNull null
                // Without a number only the date can tell; a little margin for a release made from this very build.
                number == null && published <= installedAt.coerceAtMost(Long.MAX_VALUE - MARGIN_MS) + MARGIN_MS -> return@mapNotNull null
            }
            val flavour = if (debug) "debug" else "release"
            val apk = apks.firstOrNull { nameOf(it).contains(flavour, ignoreCase = true) }
                ?: apks.firstOrNull { !nameOf(it).contains(if (debug) "release" else "debug", ignoreCase = true) }
                ?: apks.firstOrNull()
            val url = apk?.get("browser_download_url")?.jsonPrimitive?.contentOrNull ?: text("html_url") ?: return@mapNotNull null
            val tag = text("tag_name").orEmpty()
            published to AppUpdate(number ?: 0, text("name")?.takeIf(String::isNotBlank) ?: tag, url, tag.ifEmpty { url })
        }.maxWithOrNull(compareBy<Pair<Long, AppUpdate>> { it.second.build }.thenBy { it.first })?.second

    private const val MARGIN_MS = 10 * 60 * 1000L
}

/**
 * Looks for a newer ErgoTube on GitHub Releases each time the app starts, and again when it
 * comes back after a while. The request goes to api.github.com only, carries no account data,
 * and a failure just means no banner.
 */
@Singleton
class AppUpdates @Inject constructor(
    @param:ApplicationContext private val context: Context,
    okHttp: OkHttpClient,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val client = okHttp.newBuilder().apply { interceptors().clear() }.callTimeout(15, TimeUnit.SECONDS).build()
    private val memory by lazy { context.getSharedPreferences("app_update", Context.MODE_PRIVATE) }
    private val checking = Mutex()
    private val _available = MutableStateFlow<AppUpdate?>(null)
    val available: StateFlow<AppUpdate?> = _available.asStateFlow()
    private val _collapsed = MutableStateFlow(false)
    /** «Позже» folds the banner into a corner chip for this release; a newer release opens it again. */
    val collapsed: StateFlow<Boolean> = _collapsed.asStateFlow()

    /** When this build was put on the phone; a release without a number counts as newer after it. */
    private val installedAt: Long by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrDefault(Long.MAX_VALUE)
    }

    /** On every start: the last answer shows at once, a fresh one follows. */
    fun start() {
        restore()
        refresh(force = true)
    }

    /** Back in the app: asks again when the last look is older than [RECHECK_MS]. */
    fun refresh(force: Boolean = false) {
        scope.launch {
            checking.withLock {
                if (!force && System.currentTimeMillis() - memory.getLong(CHECKED, 0) < RECHECK_MS) return@withLock
                try { check() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* No banner change this time. */ }
            }
        }
    }

    private fun restore() {
        val build = memory.getInt(BUILD, 0)
        val key = memory.getString(KEY, null) ?: return
        // A numbered release counts until this build reaches it; an unnumbered one until a newer install.
        val stillNewer = if (build > 0) build > BuildConfig.VERSION_CODE else memory.getLong(SEEN_INSTALL, 0) == installedAt
        if (!stillNewer) return
        _available.value = AppUpdate(build, memory.getString(NAME, "").orEmpty(), memory.getString(URL, "").orEmpty(), key)
        _collapsed.value = memory.getString(COLLAPSED, null) == key
    }

    private suspend fun check() = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=15")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "ErgoTube").build()
        val body = client.newCall(request).execute().use { if (it.isSuccessful) it.body.string() else null } ?: return@withContext
        val releases = Json.parseToJsonElement(body) as? JsonArray ?: return@withContext
        val update = ReleaseInfo.newer(releases, BuildConfig.VERSION_CODE, installedAt, debug = BuildConfig.DEBUG)
        memory.edit().putLong(CHECKED, System.currentTimeMillis()).putInt(BUILD, update?.build ?: 0).putString(KEY, update?.key)
            .putString(NAME, update?.name).putString(URL, update?.url).putLong(SEEN_INSTALL, installedAt).apply()
        _available.value = update
        _collapsed.value = update != null && memory.getString(COLLAPSED, null) == update.key
    }

    fun collapse(collapsed: Boolean) {
        val key = _available.value?.key ?: return
        _collapsed.value = collapsed
        memory.edit().putString(COLLAPSED, if (collapsed) key else null).apply()
    }

    private companion object {
        const val RECHECK_MS = 30 * 60 * 1000L
        const val CHECKED = "checked_at"
        const val BUILD = "build"
        const val KEY = "release_key"
        const val NAME = "name"
        const val URL = "url"
        const val SEEN_INSTALL = "seen_install"
        const val COLLAPSED = "collapsed_release"
    }
}
