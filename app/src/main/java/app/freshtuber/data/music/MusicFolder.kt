package app.freshtuber.data.music

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import app.freshtuber.core.di.ApplicationScope
import app.freshtuber.data.downloads.Download
import app.freshtuber.data.downloads.DownloadKind
import app.freshtuber.data.downloads.DownloadStatus
import app.freshtuber.data.downloads.Downloads
import app.freshtuber.data.downloads.EXPORTED
import app.freshtuber.data.settings.AppFeature
import app.freshtuber.data.settings.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/** Rewrites a downloaded audio stream into a file other players open; the sound is not re-encoded. */
object AudioRemux {
    /** Returns the file extension written ("m4a" or "ogg"), or null when this stream cannot be rewritten. */
    fun remux(source: File, target: File): String? {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(source.path)
            val index = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            val format = extractor.getTrackFormat(index)
            val (container, extension) = when (format.getString(MediaFormat.KEY_MIME)) {
                MediaFormat.MIMETYPE_AUDIO_AAC -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 to "m4a"
                MediaFormat.MIMETYPE_AUDIO_OPUS -> if (Build.VERSION.SDK_INT >= 29) MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG to "ogg" else return null
                else -> return null
            }
            extractor.selectTrack(index)
            muxer = MediaMuxer(target.path, container)
            val track = muxer.addTrack(format)
            muxer.start()
            val size = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
            val buffer = ByteBuffer.allocate(maxOf(size, 256 * 1024))
            val info = MediaCodec.BufferInfo()
            var samples = 0
            while (true) {
                val read = extractor.readSampleData(buffer, 0)
                if (read < 0) break
                info.set(0, read, extractor.sampleTime, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(track, buffer, info)
                samples++
                extractor.advance()
            }
            if (samples == 0) return null
            muxer.stop()
            return extension
        } catch (e: Exception) {
            if (app.freshtuber.BuildConfig.DEBUG) android.util.Log.w("MusicFolder", "remux failed: ${e.javaClass.simpleName}: ${e.message?.take(160)}")
            target.delete()
            return null
        } finally {
            runCatching { muxer?.release() }
            extractor.release()
        }
    }
}

/** What deleting a song's file from the phone needs next. */
sealed interface FileDeletion {
    data object Done : FileDeletion
    /**
     * Android asks the listener first, in its own dialog (a file the app did not make). With
     * [retry] (Android 10) the file is deleted only by asking again after the yes.
     */
    data class Consent(val sender: android.content.IntentSender, val retry: Boolean) : FileDeletion
    /** Android 9 and older: the storage permission comes first. */
    data object Permission : FileDeletion
    data object Failed : FileDeletion
}

/** Phone files the listener cleared away as duplicates; a new look through the folder leaves them out. */
@Serializable
data class HiddenFolderSongs(val uris: Set<String> = emptySet())

/**
 * The phone's Music folder, both ways. Out: each downloaded song becomes a tagged file in
 * Music/ErgoTube, so any player finds it (the app then plays that file and keeps no second copy).
 * In: with «Показывать треки из Music» on, songs lying in Music show under «С телефона». The
 * app's own files carry a mark and never come back as phone files or as a second copy.
 */
@Singleton
class MusicFolder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloads: Downloads,
    private val library: MusicLibrary,
    private val settings: AppSettings,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val resolver get() = context.contentResolver
    private val work = Mutex()

    @OptIn(FlowPreview::class)
    fun start() {
        if (Build.VERSION.SDK_INT < 29) return
        scope.launch(Dispatchers.IO) {
            combine(downloads.audioItems, settings.preferences.map { it.enabled(AppFeature.MusicToFolder) }.distinctUntilChanged()) { items, on -> if (on) items else emptyList() }
                .debounce(1_500)
                .collect { items ->
                    items.filter { it.state == DownloadStatus.Complete }.forEach { item ->
                        try { work.withLock { export(item) } } catch (e: CancellationException) { throw e } catch (e: Exception) {
                            // Stays inside the app; tried again later.
                            if (app.freshtuber.BuildConfig.DEBUG) android.util.Log.w("MusicFolder", "export failed: ${e.javaClass.simpleName}: ${e.message?.take(160)}")
                        }
                    }
                }
        }
        scope.launch(Dispatchers.IO) {
            settings.preferences.map { it.enabled(AppFeature.MusicShowFolder) }.distinctUntilChanged().collect { on ->
                try { if (on) scan() else library.scanned().forEach { library.forget(it.id) } }
                catch (e: CancellationException) { throw e } catch (_: Exception) { }
            }
        }
    }

    fun canRead(): Boolean = context.checkSelfPermission(readPermission) == PackageManager.PERMISSION_GRANTED

    @RequiresApi(29)
    private suspend fun export(item: Download) {
        val dir = downloads.audioFolder(item.videoId)
        val source = File(dir, "audio.media")
        val pointer = File(dir, EXPORTED)
        if (pointer.isFile) {
            // Downloaded again over a song that is in the folder already: one copy is enough.
            if (source.isFile) { if (library.readable(runCatching { pointer.readText().trim() }.getOrNull())) source.delete() else pointer.delete() }
            if (pointer.isFile) return
        }
        if (!source.isFile || File(dir, SKIPPED).isFile) return
        val temp = File(dir, "export.tmp")
        try {
            val extension = AudioRemux.remux(source, temp) ?: run { File(dir, SKIPPED).createNewFile(); return }
            val (artist, title) = MusicNames.split(item.title, item.channelName)
            if (extension == "m4a") RandomAccessFile(temp, "rw").use { file ->
                M4aTags.write(file.channel, M4aTags.Tags(title, artist, cover = cover(File(dir, "thumbnail.jpg")), marker = M4aTags.MARKER_PREFIX + item.videoId))
            }
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, MusicFolderNames.fileName(artist, title, extension))
                put(MediaStore.Audio.Media.MIME_TYPE, if (extension == "m4a") "audio/mp4" else "audio/ogg")
                put(MediaStore.Audio.Media.RELATIVE_PATH, MusicFolderNames.FOLDER)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return
            try {
                resolver.openOutputStream(uri)?.use { output -> temp.inputStream().use { it.copyTo(output) } } ?: throw java.io.IOException("No output")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            // Deleted while it was being copied: the new file must not outlive the download.
            if (downloads.available(item.videoId, DownloadKind.Audio) == null) { runCatching { resolver.delete(uri, null, null) }; return }
            pointer.writeText(uri.toString())
            source.delete()
        } finally {
            temp.delete()
        }
    }

    /** The video thumbnail as a square cover, small enough to sit inside the song file. */
    private fun cover(file: File): ByteArray? = runCatching {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val options = BitmapFactory.Options()
        while (minOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize.coerceAtLeast(1) > 1200) options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
        val full = BitmapFactory.decodeFile(file.path, options) ?: return null
        val side = minOf(full.width, full.height)
        val square = Bitmap.createBitmap(full, (full.width - side) / 2, (full.height - side) / 2, side, side)
        val scaled = if (side > 600) Bitmap.createScaledBitmap(square, 600, 600, true) else square
        ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 86, it) }.toByteArray()
    }.getOrNull()

    /**
     * Looks through the phone's Music folder and lists its songs under «С телефона». Returns
     * how many were added. Needs audio access; without it nothing changes.
     */
    suspend fun scan(): Int = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext 0
        work.withLock {
            val own = downloads.exportedAudio().values.toSet()
            val hidden = library.readState<HiddenFolderSongs>(HIDDEN_KEY)?.uris.orEmpty()
            val seen = mutableSetOf<String>()
            var added = 0
            val modern = Build.VERSION.SDK_INT >= 29
            val collection = if (modern) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            @Suppress("DEPRECATION") val pathColumn = if (modern) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
            val columns = arrayOf(MediaStore.Audio.Media._ID, pathColumn, MediaStore.Audio.Media.DATE_ADDED)
            val where = if (modern) "$pathColumn LIKE 'Music/%'" else "$pathColumn LIKE '%/Music/%'"
            resolver.query(collection, columns, where, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(0)
                    val path = cursor.getString(1).orEmpty()
                    val uri = ContentUris.withAppendedId(collection, mediaId)
                    val address = uri.toString()
                    seen += "phone:ms$mediaId"
                    if (address in own || address in hidden || ContentUris.withAppendedId(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), mediaId).toString() in own) continue
                    if (library.knownUri(address)) continue
                    if (path.contains(MusicFolderNames.FOLDER)) {
                        // Our own file: it is a download, never a phone file. After a reinstall it comes back as its YouTube song.
                        val videoId = runCatching { resolver.openFileDescriptor(uri, "r")?.use { FileInputStream(it.fileDescriptor).channel.use(M4aTags::videoId) } }.getOrNull()
                        if (videoId != null) { if (videoId !in downloads.exportedAudio()) library.adopt(videoId, uri); continue }
                    }
                    if (library.addScanned(uri, mediaId, cursor.getLong(2) * 1000)) added++
                }
            }
            // Files that left the folder leave the list.
            library.scanned().filter { it.id !in seen }.forEach { library.forget(it.id) }
            added
        }
    }

    /**
     * Deletes the song file behind [address] from the phone: a Music folder file through
     * MediaStore (Android asks the listener first), a picked file through its own provider.
     */
    suspend fun deleteFile(address: String): FileDeletion = withContext(Dispatchers.IO) {
        val uri = Uri.parse(address)
        if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
            if (runCatching { android.provider.DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)) return@withContext FileDeletion.Done
            // A file picked from the phone's storage is a media file too: MediaStore can delete it.
            val media = if (Build.VERSION.SDK_INT >= 29) runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull() else null
            return@withContext media?.let(::deleteMedia) ?: FileDeletion.Failed
        }
        deleteMedia(uri)
    }

    private fun deleteMedia(uri: Uri): FileDeletion = when {
        Build.VERSION.SDK_INT >= 30 -> runCatching { FileDeletion.Consent(MediaStore.createDeleteRequest(resolver, listOf(uri)).intentSender, retry = false) }
            .getOrDefault(FileDeletion.Failed)
        Build.VERSION.SDK_INT == 29 -> try {
            if (resolver.delete(uri, null, null) > 0) FileDeletion.Done else FileDeletion.Failed
        } catch (e: android.app.RecoverableSecurityException) {
            FileDeletion.Consent(e.userAction.actionIntent.intentSender, retry = true)
        } catch (_: Exception) { FileDeletion.Failed }
        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED -> FileDeletion.Permission
        else -> try { if (resolver.delete(uri, null, null) > 0) FileDeletion.Done else FileDeletion.Failed } catch (_: Exception) { FileDeletion.Failed }
    }

    /** Takes duplicates out of the library (files stay on the phone). Returns how many were removed. */
    suspend fun removeDuplicates(): Int = withContext(Dispatchers.IO) {
        work.withLock {
            val tracks = library.all()
            val extras = MusicDuplicates.find(tracks, downloads.exportedAudio().keys + downloadedIds())
            val hidden = library.readState<HiddenFolderSongs>(HIDDEN_KEY)?.uris.orEmpty().toMutableSet()
            extras.forEach { (remove, keep) ->
                if (remove.favorite && !keep.favorite) library.toggleFavorite(library.track(keep.id) ?: keep)
                if (remove.id.startsWith("phone:ms")) remove.uri?.let(hidden::add)
                library.remove(remove)
            }
            if (extras.isNotEmpty()) library.writeState(HIDDEN_KEY, HiddenFolderSongs(hidden))
            extras.size
        }
    }

    private suspend fun downloadedIds(): Set<String> =
        downloads.audioItems.first().filter { it.state == DownloadStatus.Complete }.map { it.videoId }.toSet()

    companion object {
        /** Marks a download whose stream could not be rewritten, so it is not tried on every start. */
        private const val SKIPPED = "audio.noexport"
        private const val HIDDEN_KEY = "folder_hidden"
        val readPermission: String get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
