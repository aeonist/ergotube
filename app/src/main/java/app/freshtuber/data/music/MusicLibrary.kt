package app.freshtuber.data.music

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.withTransaction
import app.freshtuber.data.local.AppDatabase
import app.freshtuber.domain.model.VideoSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

enum class MusicSource { Phone, YouTube }

/**
 * A song: a file on the phone (`phone:…`) or a YouTube video played as audio (`yt:<id>`).
 * One library holds both; [saved] puts it in the library, [favorite] in «Любимое» (local only).
 */
@Serializable
@Entity(tableName = "music_tracks", indices = [Index("uri", unique = true)])
data class MusicTrack(
    @PrimaryKey val id: String,
    val source: MusicSource,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val artwork: String? = null,
    val durationMs: Long = 0,
    val uri: String? = null,
    val saved: Boolean = false,
    val lastPlayedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    @ColumnInfo(defaultValue = "0") val addedAt: Long = 0,
) {
    val videoId get() = id.removePrefix("yt:")
    val isYouTube get() = source == MusicSource.YouTube
    fun videoSummary() = VideoSummary(videoId, title, channelName = artist, thumbnailUrl = artwork, durationSeconds = durationMs / 1000)

    companion object {
        fun youtube(video: VideoSummary) = MusicTrack(
            "yt:${video.id}", MusicSource.YouTube, video.title, video.channelName?.let(MusicNames::artist),
            artwork = video.thumbnailUrl, durationMs = (video.durationSeconds ?: 0) * 1000,
        )
    }
}

/** A playlist may mix phone files and YouTube songs; [source] is kept only for old rows. */
@Entity(tableName = "music_playlists")
data class MusicPlaylist(@PrimaryKey val id: String, val name: String, val source: MusicSource = MusicSource.YouTube)

@Entity(
    tableName = "music_playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(entity = MusicPlaylist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MusicTrack::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("trackId")],
)
data class MusicPlaylistTrack(val playlistId: String, val trackId: String, val addedAt: Long)

@Entity(tableName = "music_state")
data class MusicStoredState(@PrimaryKey val key: String, val value: String)

data class PlaylistCount(val id: String, val name: String, val count: Int)

@Dao
interface MusicDao {
    @Query("SELECT * FROM music_tracks WHERE saved OR favorite ORDER BY addedAt DESC, title COLLATE NOCASE")
    fun library(): Flow<List<MusicTrack>>

    @Query("SELECT * FROM music_tracks WHERE favorite ORDER BY addedAt DESC")
    fun favorites(): Flow<List<MusicTrack>>

    @Query("SELECT * FROM music_tracks WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun recentlyPlayed(limit: Int): Flow<List<MusicTrack>>

    /** YouTube songs only: phone-file tags never leave the phone, not even for suggestions. */
    @Query("SELECT * FROM music_tracks WHERE source = 'YouTube' AND lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<MusicTrack>

    @Query("SELECT * FROM music_tracks WHERE (saved OR favorite OR lastPlayedAt > 0) AND (title LIKE :q OR artist LIKE :q OR album LIKE :q) ORDER BY source = 'YouTube', saved DESC, title COLLATE NOCASE LIMIT 40")
    suspend fun search(q: String): List<MusicTrack>

    @Query("SELECT * FROM music_tracks WHERE id = :id")
    suspend fun track(id: String): MusicTrack?

    @Query("SELECT * FROM music_tracks WHERE id IN (:ids)")
    suspend fun tracks(ids: List<String>): List<MusicTrack>

    @Query("SELECT * FROM music_tracks WHERE uri = :uri")
    suspend fun trackByUri(uri: String): MusicTrack?

    @Upsert suspend fun putTrack(track: MusicTrack)

    @Query("UPDATE music_tracks SET saved = 0, favorite = 0 WHERE id = :id")
    suspend fun unsave(id: String)

    @Query("DELETE FROM music_tracks WHERE id = :id")
    suspend fun removeTrack(id: String)

    @Query("SELECT p.id AS id, p.name AS name, COUNT(t.trackId) AS count FROM music_playlists p LEFT JOIN music_playlist_tracks t ON p.id = t.playlistId GROUP BY p.id ORDER BY p.name COLLATE NOCASE")
    fun playlists(): Flow<List<PlaylistCount>>

    @Query("SELECT * FROM music_playlists WHERE id = :id")
    suspend fun playlist(id: String): MusicPlaylist?

    @Upsert suspend fun putPlaylist(playlist: MusicPlaylist)

    @Query("DELETE FROM music_playlists WHERE id = :id")
    suspend fun removePlaylist(id: String)

    @Query("SELECT t.* FROM music_tracks t JOIN music_playlist_tracks p ON t.id = p.trackId WHERE p.playlistId = :id ORDER BY p.addedAt, p.trackId")
    fun playlistTracks(id: String): Flow<List<MusicTrack>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putPlaylistTrack(track: MusicPlaylistTrack)

    @Query("DELETE FROM music_playlist_tracks WHERE playlistId = :playlist AND trackId = :track")
    suspend fun removePlaylistTrack(playlist: String, track: String)

    @Query("SELECT value FROM music_state WHERE `key` = :key")
    suspend fun readState(key: String): String?

    @Upsert suspend fun putState(state: MusicStoredState)
}

data class MusicImportResult(val added: Int, val unavailable: Int)

@Singleton
class MusicLibrary @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {
    private val dao get() = database.music()
    private val resolver get() = context.contentResolver
    val tracks get() = dao.library()
    val favorites get() = dao.favorites()
    val playlists get() = dao.playlists()
    fun recentlyPlayed(limit: Int = 30) = dao.recentlyPlayed(limit)

    fun playlistTracks(id: String) = dao.playlistTracks(id)
    suspend fun playlist(id: String) = dao.playlist(id)
    suspend fun track(id: String) = dao.track(id)
    suspend fun tracks(ids: List<String>) = if (ids.isEmpty()) emptyList() else dao.tracks(ids)
    suspend fun recent(limit: Int) = dao.recent(limit)
    suspend fun search(query: String): List<MusicTrack> {
        val q = query.trim().replace("%", "").replace("_", "")
        return if (q.isEmpty()) emptyList() else dao.search("%$q%")
    }

    /** The stored copy, so favourite and library flags win over a fresh search result. */
    suspend fun known(track: MusicTrack): MusicTrack = dao.track(track.id) ?: track

    suspend fun save(track: MusicTrack) = database.withTransaction {
        val old = dao.track(track.id)
        dao.putTrack((old ?: track).copy(saved = true, addedAt = old?.addedAt?.takeIf { it > 0 } ?: System.currentTimeMillis()))
    }

    /** «Любимое» only lives on the phone; it never likes the video on YouTube. Returns the new state. */
    suspend fun toggleFavorite(track: MusicTrack): Boolean = database.withTransaction {
        val old = dao.track(track.id) ?: track
        val favorite = !old.favorite
        dao.putTrack(old.copy(favorite = favorite, saved = old.saved || favorite,
            addedAt = old.addedAt.takeIf { it > 0 } ?: System.currentTimeMillis()))
        favorite
    }

    suspend fun remove(track: MusicTrack) {
        if (track.source != MusicSource.Phone) {
            dao.unsave(track.id)
            return
        }
        dao.removeTrack(track.id)
        withContext(Dispatchers.IO) {
            track.uri?.let(Uri::parse)?.takeUnless(DocumentsContract::isTreeUri)?.let {
                runCatching { resolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
        }
    }

    suspend fun recordPlay(track: MusicTrack) = database.withTransaction {
        dao.putTrack((dao.track(track.id) ?: track).copy(lastPlayedAt = System.currentTimeMillis()))
    }

    suspend fun createPlaylist(name: String) =
        MusicPlaylist(UUID.randomUUID().toString(), name.trim()).also { dao.putPlaylist(it) }

    suspend fun renamePlaylist(id: String, name: String) { dao.playlist(id)?.let { dao.putPlaylist(it.copy(name = name.trim())) } }
    suspend fun removePlaylist(id: String) = dao.removePlaylist(id)
    suspend fun removePlaylistTrack(playlist: String, track: String) = dao.removePlaylistTrack(playlist, track)

    suspend fun addToPlaylist(playlistId: String, track: MusicTrack) = database.withTransaction {
        if (dao.playlist(playlistId) == null) return@withTransaction
        val existing = dao.track(track.id)
        if (existing == null || !existing.saved) dao.putTrack((existing ?: track).copy(saved = true,
            addedAt = existing?.addedAt?.takeIf { it > 0 } ?: System.currentTimeMillis()))
        dao.putPlaylistTrack(MusicPlaylistTrack(playlistId, track.id, System.currentTimeMillis()))
    }

    suspend inline fun <reified T> readState(key: String): T? =
        readRawState(key)?.let { runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<T>(it) }.getOrNull() }

    suspend inline fun <reified T> writeState(key: String, value: T) = writeRawState(key, Json.encodeToString(value))
    suspend fun readRawState(key: String) = dao.readState(key)
    suspend fun writeRawState(key: String, value: String) = dao.putState(MusicStoredState(key, value))

    suspend fun importFiles(uris: List<Uri>): MusicImportResult = withContext(Dispatchers.IO) {
        import(uris.distinct()) { uri -> keepReadAccess(uri) }
    }

    suspend fun importFolder(tree: Uri): MusicImportResult = withContext(Dispatchers.IO) {
        if (!keepReadAccess(tree)) return@withContext MusicImportResult(0, 1)
        val pending = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        val tracks = mutableListOf<Uri>()
        pending.add(DocumentsContract.getTreeDocumentId(tree))
        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            val parent = pending.removeFirst()
            if (!visited.add(parent)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE)
            resolver.query(children, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val mime = cursor.getString(1).orEmpty()
                    when {
                        mime == DocumentsContract.Document.MIME_TYPE_DIR -> pending.add(id)
                        mime.startsWith("audio/") || mime == "application/ogg" ->
                            tracks += DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    }
                }
            }
        }
        import(tracks) { true }
    }

    private fun keepReadAccess(uri: Uri): Boolean = runCatching {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.isSuccess

    private suspend fun import(uris: List<Uri>, grant: (Uri) -> Boolean): MusicImportResult {
        var added = 0
        var unavailable = 0
        for (uri in uris) {
            coroutineContext.ensureActive()
            if (dao.trackByUri(uri.toString()) != null) continue
            if (!grant(uri)) { unavailable++; continue }
            val track = runCatching { inspect(uri, "phone:${UUID.randomUUID()}") }.getOrNull()
            if (track == null) unavailable++ else {
                dao.putTrack(track)
                added++
            }
        }
        return MusicImportResult(added, unavailable)
    }

    private fun inspect(uri: Uri, id: String): MusicTrack? {
        val tags = MediaMetadataRetriever()
        try {
            tags.setDataSource(context, uri)
            if (tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes") return null
            val title = tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank)
                ?: resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0)?.substringBeforeLast('.') else null
                } ?: uri.lastPathSegment.orEmpty()
            val artwork = runCatching { tags.embeddedPicture?.let { saveArtwork(id, it) } }.getOrNull()
            return MusicTrack(
                id, MusicSource.Phone, title,
                artist = tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                artwork = artwork,
                durationMs = tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                uri = uri.toString(), saved = true, addedAt = System.currentTimeMillis(),
            )
        } finally {
            tags.release()
        }
    }

    private fun saveArtwork(id: String, bytes: ByteArray): String? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 768) options.inSampleSize *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val file = File(File(context.filesDir, "music-artwork").apply { mkdirs() }, "${id.substringAfter(':')}.jpg")
        return try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            Uri.fromFile(file).toString()
        } finally {
            bitmap.recycle()
        }
    }
}

/** Tidies YouTube names into song and artist, for display and for the lyrics lookup. */
object MusicNames {
    private val noise = Regex(
        """\s*[\(\[][^\)\]]*(official|video|audio|lyric|lyrics|visualizer|clip|клип|премьера|hd|4k|remaster(ed)?\s*\d*|mv)[^\)\]]*[\)\]]""",
        RegexOption.IGNORE_CASE,
    )

    fun artist(channel: String): String = channel.removeSuffix(" - Topic").removeSuffix("VEVO").removeSuffix(" ").trim().ifEmpty { channel }

    /** "Artist - Song (Official Video)" → ("Artist", "Song"); the channel is the fallback artist. */
    fun split(title: String, channel: String?): Pair<String?, String> {
        val clean = title.replace(noise, "").replace(Regex("\\s+"), " ").trim()
        val dash = Regex("\\s[-–—]\\s").find(clean)
        val fromTitle = dash?.let { clean.substring(0, it.range.first).trim() to clean.substring(it.range.last + 1).trim() }
        val artist = channel?.let(::artist)
        return when {
            fromTitle != null && fromTitle.first.isNotEmpty() && fromTitle.second.isNotEmpty() -> fromTitle.first to fromTitle.second
            else -> artist to clean
        }
    }
}
