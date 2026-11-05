package app.freshtuber.data.downloads

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.room.withTransaction
import androidx.work.*
import app.freshtuber.background.DownloadWorker
import app.freshtuber.core.di.ApplicationScope
import app.freshtuber.data.local.AppDatabase
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.domain.model.VideoSummary
import app.freshtuber.domain.model.ChannelSummary
import app.freshtuber.player.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@androidx.annotation.OptIn(UnstableApi::class)
@Singleton
class Downloads @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val resolver: StreamResolver,
    private val settings: AppSettings,
    private val videos: app.freshtuber.data.video.VideoRepository,
    private val network: app.freshtuber.core.connectivity.NetworkMonitor,
    okHttp: OkHttpClient,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val dao get() = database.downloads()
    val items = dao.observe()
    fun isOnline(): Boolean = network.online.value
    private val manager get() = WorkManager.getInstance(context)
    private val controls = Mutex()
    private val transfers = Semaphore(2)
    private val fileLocks = ConcurrentHashMap<String, Mutex>()
    private val client = okHttp.newBuilder().apply { interceptors().clear() }.addInterceptor(GoogleVideoInterceptor()).build()
    private val root = File(context.filesDir, "downloads")
    private fun folder(id: String): File { require(DownloadFiles.validId(id)); return File(root, id) }
    private fun workName(id: String) = "download-$id"

    /** Repairs interrupted work and applies a changed Wi-Fi policy to the persistent queue. */
    fun start() {
        scope.launch {
            var previousWifi: Boolean? = null
            settings.downloadWifiOnly.distinctUntilChanged().collect { wifi ->
                controls.withLock {
                    dao.all().filter { it.state in listOf(DownloadStatus.Queued, DownloadStatus.Downloading) }.forEach { item ->
                        val info = runCatching { manager.getWorkInfoById(UUID.fromString(item.workId)).get(10, TimeUnit.SECONDS) }.getOrNull()
                        if (info == null || info.state.isFinished || (previousWifi != null && previousWifi != wifi && info.state == WorkInfo.State.RUNNING)) enqueueLocked(item)
                        else manager.updateWork(request(item, wifi)).get(10, TimeUnit.SECONDS)
                    }
                    dao.all().filter { it.state == DownloadStatus.Deleting }.forEach { removeFiles(it.videoId) }
                }
                previousWifi = wifi
            }
        }
    }

    suspend fun enqueue(video: VideoSummary): Boolean = scope.async(Dispatchers.IO) {
        require(DownloadFiles.validId(video.id))
        controls.withLock {
            val old = dao.get(video.id)
            if (old != null && old.state in listOf(DownloadStatus.Complete, DownloadStatus.Queued, DownloadStatus.Downloading, DownloadStatus.Deleting)) return@withLock false
            enqueueLocked(old ?: Download(video.id, video.title, video.channelName, video.channelId, video.thumbnailUrl,
                workId = UUID.randomUUID().toString(), requestedHeight = settings.downloadQuality.first()))
            true
        }
    }.await()

    private suspend fun enqueueLocked(item: Download) {
        val next = item.copy(status = DownloadStatus.Queued.name, error = null, workId = UUID.randomUUID().toString())
        dao.save(next)
        manager.enqueueUniqueWork(workName(item.videoId), ExistingWorkPolicy.REPLACE, request(next, settings.downloadWifiOnly.first()))
    }

    private fun request(item: Download, wifi: Boolean) = OneTimeWorkRequestBuilder<DownloadWorker>()
        .setId(UUID.fromString(item.workId))
        .setInputData(workDataOf("videoId" to item.videoId))
        .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifi) NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresStorageNotLow(true).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
        .build()

    suspend fun pause(id: String) = scope.async(Dispatchers.IO) {
        controls.withLock {
            val item = dao.get(id) ?: return@withLock
            if (item.state !in listOf(DownloadStatus.Downloading, DownloadStatus.Queued)) return@withLock
            dao.state(id, DownloadStatus.Paused.name)
            manager.cancelUniqueWork(workName(id))
        }
    }.await()

    suspend fun resume(id: String) = scope.async(Dispatchers.IO) {
        controls.withLock { dao.get(id)?.takeIf { it.state in listOf(DownloadStatus.Paused, DownloadStatus.Failed) }?.let { enqueueLocked(it) } }
    }.await()

    suspend fun failedBeforeTransfer(id: String, work: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.get(id)?.takeIf { it.workId == work && it.state in listOf(DownloadStatus.Queued, DownloadStatus.Downloading) }
                ?.let { dao.save(it.copy(status = DownloadStatus.Failed.name, error = "background")) }
        }
    }

    fun delete(id: String) {
        scope.launch {
            controls.withLock {
                dao.state(id, DownloadStatus.Deleting.name)
                manager.cancelUniqueWork(workName(id))
                removeFiles(id)
            }
        }
    }

    private suspend fun removeFiles(id: String) = fileLocks.getOrPut(id) { Mutex() }.withLock {
        if (folder(id).deleteRecursively()) {
            // The file lock also orders this after the worker's completion callback.
            context.getSystemService(android.app.NotificationManager::class.java).cancel("complete-$id".hashCode())
            dao.delete(id)
        }
        else dao.get(id)?.let { dao.save(it.copy(status = DownloadStatus.Failed.name, error = "storage")) }
    }

    suspend fun available(id: String): Download? = withContext(Dispatchers.IO) {
        val item = dao.get(id)?.takeIf { it.state == DownloadStatus.Complete } ?: return@withContext null
        if (File(folder(id), "video.media").isFile && (!item.separateAudio || File(folder(id), "audio.media").isFile)) item
        else { dao.save(item.copy(status = DownloadStatus.Failed.name, error = "storage")); null }
    }

    fun thumbnail(item: Download): String? = File(folder(item.videoId), "thumbnail.jpg").takeIf { it.isFile }?.toURI()?.toString() ?: item.thumbnailUrl

    suspend fun localVideo(id: String): ResolvedVideo? = available(id)?.let { item ->
        val dir = folder(id)
        val video = File(dir, "video.media").toURI().toString()
        ResolvedVideo(id, item.title, item.channelName, thumbnail(item), item.durationSeconds, false, null,
            if (item.separateAudio) listOf(VideoTrack(item.height, 30, video, item.videoMime, "${item.height}p", item.codecMime)) else emptyList(),
            if (item.separateAudio) File(dir, "audio.media").toURI().toString() else null, item.audioMime,
            if (item.separateAudio) null else video,
            if (item.subtitleMime != null && File(dir, "subtitle").isFile) listOf(SubtitleTrack(File(dir, "subtitle").toURI().toString(), item.subtitleMime, item.subtitleLanguage.orEmpty(), item.subtitleLabel.orEmpty(), false)) else emptyList(),
            item.channelId?.let { ChannelSummary(it, item.channelName.orEmpty()) })
    }

    suspend fun related(except: String) = withContext(Dispatchers.IO) { dao.all().filter { it.videoId != except && it.state == DownloadStatus.Complete }.map { it.summary(thumbnail(it)) } }

    suspend fun details(id: String) = withContext(Dispatchers.IO) {
        available(id)?.let { item ->
            val metadata = runCatching {
                val file = File(folder(id), "metadata.json")
                if (file.length() > 1024 * 1024) null else Json.decodeFromString<OfflineMetadata>(file.readText())
            }.getOrNull()
            item.details(related(id)).copy(description = metadata?.description, viewsText = metadata?.views, dateText = metadata?.date, descriptionLinks = metadata?.descriptionLinks.orEmpty())
        }
    }

    /** Runs under WorkManager cancellation; database generations prevent stale workers overwriting retries. */
    suspend fun transfer(id: String, workId: String, progress: (Download) -> Unit) = withContext(Dispatchers.IO) {
        transfers.withPermit {
        fileLocks.getOrPut(id) { Mutex() }.withLock {
            val original = dao.get(id)?.takeIf { it.workId == workId && it.state in listOf(DownloadStatus.Queued, DownloadStatus.Downloading) } ?: return@withLock
            var item = original.copy(status = DownloadStatus.Downloading.name, error = null)
            updateCurrent(item)
            progress(item)
            try {
                resolver.invalidate(id)
                val video = resolver.resolve(id)
                if (video.isLive) throw UnsupportedDownload()
                val tracks = StreamChoice.videoTracks(video.videoCandidates)
                val track = tracks.getOrNull(StreamChoice.pick(tracks, item.requestedHeight))?.takeIf { video.audioUrl != null }
                val videoUrl = track?.url ?: video.muxedUrl ?: throw UnsupportedDownload()
                val audioUrl = if (track != null) video.audioUrl else null
                val dir = folder(id).apply { mkdirs() }
                val vf = DownloadFiles.fingerprint(videoUrl)
                val af = audioUrl?.let(DownloadFiles::fingerprint)
                if (!DownloadFiles.canResume(item.videoFingerprint, vf)) { File(dir, "video.part").delete(); File(dir, "video.media").delete() }
                if (!DownloadFiles.canResume(item.audioFingerprint, af.orEmpty())) { File(dir, "audio.part").delete(); File(dir, "audio.media").delete() }
                val total = (ChunkPlan.streamLength(videoUrl) ?: 0L) + (audioUrl?.let(ChunkPlan::streamLength) ?: 0L)
                val existing = listOf("video.part", "audio.part", "video.media", "audio.media").sumOf { File(dir, it).length() }
                if (dir.usableSpace < (total - existing).coerceAtLeast(0) + 16L * 1024 * 1024) throw StorageFull()
                item = item.copy(title = video.title, channelName = video.channelName, channelId = video.channel?.id ?: item.channelId,
                    thumbnailUrl = video.thumbnailUrl, height = track?.height ?: 0, durationSeconds = video.durationSeconds,
                    videoFingerprint = vf, audioFingerprint = af, videoMime = track?.mimeType, audioMime = video.audioMimeType,
                    codecMime = track?.codecMime, separateAudio = audioUrl != null, bytesTotal = total, bytesDone = existing)
                updateCurrent(item)
                var lastUpdate = 0L
                suspend fun report() {
                    val done = listOf("video.part", "audio.part", "video.media", "audio.media").sumOf { File(dir, it).length() }
                    if (System.currentTimeMillis() - lastUpdate > 750) {
                        lastUpdate = System.currentTimeMillis()
                        dao.progress(id, workId, done, total)
                        progress(item.copy(bytesDone = done))
                    }
                }
                copyStream(videoUrl, File(dir, "video.part"), File(dir, "video.media"), ::report)
                if (audioUrl != null) copyStream(audioUrl, File(dir, "audio.part"), File(dir, "audio.media"), ::report)
                val details = try { withTimeoutOrNull(6_000) { videos.details(id) } }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                if (details != null) {
                    item = item.copy(channelId = details.channel?.id ?: item.channelId, channelName = details.channel?.name ?: item.channelName)
                    runCatching {
                        val temp = File(dir, "metadata.json.part")
                        temp.writeText(Json.encodeToString(OfflineMetadata(details.description?.take(200_000), details.viewsText, details.dateText, details.descriptionLinks)))
                        temp.renameTo(File(dir, "metadata.json"))
                    }
                }
                // Artwork and a preferred-language caption are optional; media stays usable without them.
                video.thumbnailUrl?.let { runCatching { copySmall(it, File(dir, "thumbnail.jpg"), 5L * 1024 * 1024) }.onFailure { if (it is CancellationException) throw it } }
                val language = context.resources.configuration.locales[0].language
                val subtitle = video.subtitles.firstOrNull { it.language.substringBefore('-') == language } ?: video.subtitles.firstOrNull { it.language.startsWith("en") }
                var caption = false
                if (subtitle != null) try { copySmall(subtitle.url, File(dir, "subtitle"), 8L * 1024 * 1024); caption = true } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                val done = File(dir, "video.media").length() + File(dir, "audio.media").length()
                item = item.copy(status = DownloadStatus.Complete.name, bytesDone = done, bytesTotal = done,
                    subtitleMime = subtitle?.mimeType?.takeIf { caption }, subtitleLanguage = subtitle?.language?.takeIf { caption }, subtitleLabel = subtitle?.label?.takeIf { caption })
                updateCurrent(item)
                progress(item)
            } catch (e: CancellationException) {
                withContext(NonCancellable) { updateCurrent(item.copy(status = DownloadStatus.Queued.name, bytesDone = mediaBytes(folder(id)))) }
                throw e
            } catch (e: Exception) {
                if (app.freshtuber.BuildConfig.DEBUG) android.util.Log.w("Downloads", "Transfer failed: ${e.javaClass.simpleName}; cause=${e.cause?.javaClass?.simpleName}")
                val error = when (e) { is UnsupportedDownload, is org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException -> "unsupported"; is StorageFull -> "space"; is java.io.FileNotFoundException -> "storage"; else -> "network" }
                updateCurrent(item.copy(status = DownloadStatus.Failed.name, error = error, bytesDone = mediaBytes(folder(id))))
                throw e
            }
        }
        }
    }

    private suspend fun updateCurrent(item: Download) = database.withTransaction {
        val current = dao.get(item.videoId)
        if (current?.workId == item.workId && current.state in listOf(DownloadStatus.Queued, DownloadStatus.Downloading)) dao.save(item)
    }

    private fun mediaBytes(dir: File) = listOf("video.part", "audio.part", "video.media", "audio.media").sumOf { File(dir, it).length() }

    private suspend fun copyStream(url: String, part: File, target: File, report: suspend () -> Unit) {
        if (target.isFile) return
        val length = ChunkPlan.streamLength(url)
        if (length != null && part.length() > length) part.delete()
        if (length == null || part.length() != length) {
            val actual = ChunkedDataSource(OkHttpDataSource.Factory(client).createDataSource())
            try {
                actual.open(DataSpec.Builder().setUri(url).setPosition(part.length()).build())
                java.io.FileOutputStream(part, true).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = actual.read(buffer, 0, buffer.size)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                        report()
                    }
                    output.fd.sync()
                }
            } finally { actual.close() }
        }
        if (length != null && part.length() != length) throw IOException("Incomplete media")
        currentCoroutineContext().ensureActive()
        if (!part.renameTo(target)) throw IOException("Cannot finalize media")
    }

    private suspend fun copySmall(url: String, file: File, limit: Long) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Optional resource unavailable")
            val body = response.body
            val temp = File(file.parentFile, file.name + ".part")
            try {
                body.byteStream().use { input -> temp.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024); var total = 0L
                    while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n == -1) break; total += n; if (total > limit) throw IOException("Resource too large"); output.write(buffer, 0, n) }
                } }
                if (!temp.renameTo(file)) throw IOException("Cannot finalize resource")
            } finally { temp.delete() }
        }
    }
}

class UnsupportedDownload : IOException("This stream cannot be downloaded")
class StorageFull : IOException("Not enough storage")
