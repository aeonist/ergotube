package app.freshtuber.data.downloads

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import app.freshtuber.domain.model.*
import kotlinx.serialization.Serializable

enum class DownloadKind {
    Video, Audio;

    fun storageId(videoId: String): String = if (this == Video) videoId else "$videoId-audio"

    companion object {
        fun fromInput(input: String?): DownloadKind = entries.firstOrNull { it.name == input } ?: Video
    }
}

enum class DownloadStatus { Queued, Downloading, Paused, Complete, Failed, Deleting }

@Serializable
data class OfflineMetadata(val description: String?, val views: String?, val date: String?, val descriptionLinks: List<TextLink> = emptyList())

/** Only local paths and non-secret stream fingerprints are persisted, never signed URLs. */
@Entity(tableName = "downloads", primaryKeys = ["videoId", "kind"])
data class Download(
    val videoId: String,
    val title: String,
    val channelName: String? = null,
    val channelId: String? = null,
    val thumbnailUrl: String? = null,
    val status: String = DownloadStatus.Queued.name,
    val workId: String,
    val requestedHeight: Int = 720,
    val height: Int = 0,
    val durationSeconds: Long = 0,
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
    val videoFingerprint: String? = null,
    val audioFingerprint: String? = null,
    val videoMime: String? = null,
    val audioMime: String? = null,
    val codecMime: String? = null,
    val separateAudio: Boolean = false,
    val subtitleMime: String? = null,
    val subtitleLanguage: String? = null,
    val subtitleLabel: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val kind: DownloadKind = DownloadKind.Video,
) {
    val state: DownloadStatus get() = DownloadStatus.valueOf(status)
    fun summary(thumbnail: String? = thumbnailUrl) = VideoSummary(videoId, title, channelName = channelName,
        channelId = channelId, thumbnailUrl = thumbnail, durationSeconds = durationSeconds)
    fun details(related: List<VideoSummary>) = VideoDetails(videoId, title, null, null, null,
        channelId?.let { ChannelSummary(it, channelName.orEmpty()) }, null, null, LikeStatus.Indifferent,
        null, null, null, null, null, null, related, null)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads WHERE kind = :kind ORDER BY createdAt DESC") fun observe(kind: DownloadKind): Flow<List<Download>>
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC") suspend fun all(): List<Download>
    @Query("SELECT * FROM downloads WHERE videoId = :id AND kind = :kind") suspend fun get(id: String, kind: DownloadKind = DownloadKind.Video): Download?
    @Upsert suspend fun save(download: Download)
    @Query("DELETE FROM downloads WHERE videoId = :id AND kind = :kind") suspend fun delete(id: String, kind: DownloadKind = DownloadKind.Video)
    @Query("UPDATE downloads SET status = :state WHERE videoId = :id AND kind = :kind") suspend fun state(id: String, state: String, kind: DownloadKind = DownloadKind.Video)
    @Query("UPDATE downloads SET bytesDone = :done, bytesTotal = :total WHERE videoId = :id AND kind = :kind AND workId = :work AND status = 'Downloading'")
    suspend fun progress(id: String, work: String, done: Long, total: Long, kind: DownloadKind = DownloadKind.Video)
}

/** A retry may resume only the exact same representation, not another codec at the same height. */
object DownloadFiles {
    fun validId(id: String) = id.matches(Regex("[A-Za-z0-9_-]{11}"))
    fun fingerprint(url: String): String {
        val query = java.net.URI(url).rawQuery.orEmpty().split('&').map { it.substringBefore('=') to it.substringAfter('=', "") }.toMap()
        return listOf("itag", "clen", "lmt").joinToString(":") { query[it].orEmpty() }
    }
    fun canResume(old: String?, next: String): Boolean = old != null && old == next && next.split(':').take(2).all { it.isNotEmpty() }
}
