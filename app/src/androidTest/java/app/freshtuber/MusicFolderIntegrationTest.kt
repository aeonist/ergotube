package app.freshtuber

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.background.BackgroundDependencies
import app.freshtuber.data.downloads.Download
import app.freshtuber.data.downloads.DownloadKind
import app.freshtuber.data.downloads.DownloadStatus
import app.freshtuber.data.music.AudioRemux
import app.freshtuber.data.music.M4aTags
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/** A downloaded song becomes a tagged file in Music/ErgoTube and keeps playing from there. */
@RunWith(AndroidJUnit4::class)
class MusicFolderIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val dependencies get() = EntryPointAccessors.fromApplication(context.applicationContext, BackgroundDependencies::class.java)

    private fun asset(name: String, target: File) = instrumentation.context.assets.open(name).use { input -> target.outputStream().use { input.copyTo(it) } }

    @Test fun aDownloadedSongMovesToTheMusicFolderWithTagsAndIsRemovedWithTheDownload() = runBlocking {
        val downloads = dependencies.downloads()
        val id = "tESTsong_01"
        // Only an existing leftover is deleted: a delete that lands late would take the new row with it.
        if (dependencies.database().downloads().get(id, DownloadKind.Audio) != null) {
            downloads.delete(id, DownloadKind.Audio)
            withTimeout(20_000) { while (dependencies.database().downloads().get(id, DownloadKind.Audio) != null) delay(200) }
        }
        val dir = downloads.audioFolder(id).apply { deleteRecursively(); mkdirs() }
        val source = File(dir, "audio.media")
        asset("song-dash.m4a", source)
        Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }
            .let { bitmap -> File(dir, "thumbnail.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        dependencies.database().downloads().save(Download(id, "Test Artist - Test Song (Official Video)", "Test Artist",
            status = DownloadStatus.Complete.name, workId = UUID.randomUUID().toString(), kind = DownloadKind.Audio,
            bytesDone = source.length(), bytesTotal = source.length(), audioMime = "audio/mp4"))
        try {
            val address = withTimeout(40_000) {
                var found: String? = null
                while (found == null) { found = downloads.exportedAudio()[id]; if (found == null) delay(300) }
                found!!
            }
            val uri = Uri.parse(address)
            assertFalse("The app keeps no second copy", source.exists())
            context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.RELATIVE_PATH), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals("Test Artist - Test Song.m4a", it.getString(0))
                assertTrue(it.getString(1).startsWith("Music/ErgoTube"))
            }
            val tags = MediaMetadataRetriever()
            try {
                tags.setDataSource(context, uri)
                assertEquals("Test Song", tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
                assertEquals("Test Artist", tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                val length = tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
                assertTrue("length $length", length in 2_500..3_600)
                assertNotNull("cover", tags.embeddedPicture)
            } finally { tags.release() }
            context.contentResolver.openFileDescriptor(uri, "r")!!.use { descriptor ->
                assertEquals(id, FileInputStream(descriptor.fileDescriptor).channel.use(M4aTags::videoId))
            }
            assertNotNull(downloads.available(id, DownloadKind.Audio))
            assertEquals(address, downloads.localAudio(id)!!.second)
        } finally {
            downloads.delete(id, DownloadKind.Audio)
        }
        withTimeout(20_000) { while (dependencies.database().downloads().get(id, DownloadKind.Audio) != null) delay(200) }
        val left = context.contentResolver.query(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ?", arrayOf("Test Artist - Test Song.m4a"), null)!!.use { it.count }
        assertEquals("Deleting the download removes the file from Music", 0, left)
    }

    @Test fun anOpusStreamBecomesAnOggFile() {
        val source = File(context.cacheDir, "song.webm")
        val target = File(context.cacheDir, "song.out")
        asset("song.webm", source)
        try {
            assertEquals("ogg", AudioRemux.remux(source, target))
            val tags = MediaMetadataRetriever()
            try {
                tags.setDataSource(target.path)
                assertTrue(tags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() in 2_500..3_600)
            } finally { tags.release() }
        } finally { source.delete(); target.delete() }
    }
}
