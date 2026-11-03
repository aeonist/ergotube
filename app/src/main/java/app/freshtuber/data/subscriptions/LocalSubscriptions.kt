package app.freshtuber.data.subscriptions

import androidx.room.withTransaction
import app.freshtuber.data.local.AppDatabase
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.background.AppNotifications
import app.freshtuber.domain.model.ChannelSummary
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalSubscriptions @Inject constructor(private val db: AppDatabase, private val client: OkHttpClient,
    private val settings: AppSettings, private val notifications: AppNotifications) {
    private val dao get() = db.localSubscriptions()
    val channels get() = dao.channels()
    val videos get() = dao.videos()
    fun channel(id: String?) = if (id == null) flowOf(null) else dao.observe(id)
    suspend fun follow(channel: ChannelSummary) {
        require(channel.id.matches(Regex("UC[A-Za-z0-9_-]{22}")))
        if (dao.get(channel.id) == null) dao.save(LocalChannel(channel.id, channel.name, channel.avatarUrl, addedAt = System.currentTimeMillis()))
    }
    suspend fun toggle(channel: ChannelSummary) { if (dao.get(channel.id) == null) follow(channel) else dao.remove(channel.id) }
    suspend fun remove(id: String) = dao.remove(id)
    suspend fun notify(channel: ChannelSummary, enabled: Boolean) { follow(channel); dao.notifications(channel.id, enabled) }
    suspend fun refresh(notify: Boolean = false): Int = coroutineScope {
        val limit = Semaphore(3)
        dao.allChannels().map { channel -> async {
            limit.withPermit {
                try { refreshChannel(channel, notify); 0 }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { dao.get(channel.id)?.let { dao.save(it.copy(syncFailed = true)) }; 1 }
            }
        } }.awaitAll().sum().also { dao.trim(System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000) }
    }
    private suspend fun refreshChannel(channel: LocalChannel, notify: Boolean) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://www.youtube.com/feeds/videos.xml?channel_id=${channel.id}").build()
        val entries = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("RSS unavailable")
            val body = response.body
            if (body.contentLength() > 2_000_000) throw IOException("RSS too large")
            val source = body.source()
            val buffer = okio.Buffer()
            while (source.read(buffer, 8192) != -1L) { if (buffer.size > 2_000_000) throw IOException("RSS too large") }
            RssParser.parse(buffer.readUtf8(), channel.id, channel.name)
        }
        var fresh = emptyList<LocalVideo>()
        db.withTransaction {
            val current = dao.get(channel.id) ?: return@withTransaction
            val inserted = dao.insertVideos(entries)
            if (current.initialized) fresh = entries.filterIndexed { index, video -> inserted[index] != -1L && video.publishedAt >= current.addedAt }
            dao.save(current.copy(initialized = true, lastCheckedAt = System.currentTimeMillis(), syncFailed = false))
        }
        if (notify && dao.get(channel.id)?.notifications == true && settings.notificationsEnabled.first()) {
            val shorts = settings.shortsInFeeds.first()
            fresh.filter { shorts || !it.isShort }.take(5).forEach(notifications::video)
        }
    }
}
