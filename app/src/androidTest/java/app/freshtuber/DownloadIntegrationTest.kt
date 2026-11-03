package app.freshtuber

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.background.BackgroundDependencies
import app.freshtuber.data.downloads.*
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.domain.model.VideoSummary
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DownloadIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository get() = EntryPointAccessors.fromApplication(context.applicationContext, BackgroundDependencies::class.java).downloads()

    @Test fun realDownloadPersistsCompleteMediaAndOpensWithoutResolvingNetwork() = runBlocking {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        delay(1500)
        val settings = AppSettings(context)
        val original = settings.downloadQuality.first()
        settings.setDownloadQuality(360)
        try {
            val id = "dQw4w9WgXcQ"
            repository.enqueue(VideoSummary(id, "YouTube test video"))
            val result = withTimeout(180_000) { repository.items.first { all -> all.any { it.videoId == id && it.state in listOf(DownloadStatus.Complete, DownloadStatus.Failed) } }.first { it.videoId == id } }
            assertEquals("Download failed: ${result.error}", DownloadStatus.Complete, result.state)
            assertTrue(result.bytesDone > 10_000)
            assertEquals(result.bytesTotal, result.bytesDone)
            assertNotNull(repository.available(id))
            val offline = repository.localVideo(id)!!
            assertEquals(result.title, offline.title)
            val video = offline.videoCandidates.firstOrNull()?.url ?: offline.muxedUrl!!
            assertEquals("file", java.net.URI(video).scheme)
            assertTrue(File(java.net.URI(video)).length() > 10_000)
            offline.audioUrl?.let { assertTrue(File(java.net.URI(it)).length() > 1000) }
            // A duplicate click cannot start another copy or overwrite the completed media.
            assertFalse(repository.enqueue(VideoSummary(id, "Duplicate")))
        } finally { settings.setDownloadQuality(original) }
    }
    @Test fun pauseResumeAndDeleteRealPartialDownload() = runBlocking {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        delay(1000)
        val settings = AppSettings(context)
        val original = settings.downloadQuality.first()
        val id = "5_Fayfx2NUM"
        settings.setDownloadQuality(144)
        try {
            repository.delete(id)
            withTimeout(20_000) { repository.items.first { it.none { entry -> entry.videoId == id } } }
            // Dismissing the caller after the tap must not cancel durable registration.
            val registration = launch(start = CoroutineStart.UNDISPATCHED) { repository.enqueue(VideoSummary(id, "Download recovery test")) }
            registration.cancelAndJoin()
            val started = withTimeout(90_000) { repository.items.first { all -> all.any { it.videoId == id && it.bytesDone > 0 } }.first { it.videoId == id } }
            assertEquals(DownloadStatus.Downloading, started.state)
            repository.pause(id)
            delay(1000)
            val paused = repository.items.first().first { it.videoId == id }
            assertEquals(DownloadStatus.Paused, paused.state)
            val partial = File(context.filesDir, "downloads/$id/video.part")
            assertTrue("A pause should retain the partial media", partial.length() > 0)
            val retained = partial.length()
            delay(800)
            assertEquals("Paused files must stop growing", retained, partial.length())
            repository.resume(id)
            val resumed = repository.items.first().first { it.videoId == id }
            assertNotEquals(paused.workId, resumed.workId)
            val complete = withTimeout(180_000) { repository.items.first { all -> all.any { it.videoId == id && it.state in listOf(DownloadStatus.Complete, DownloadStatus.Failed) } }.first { it.videoId == id } }
            assertEquals("Resume failed: ${complete.error}", DownloadStatus.Complete, complete.state)
            assertTrue(complete.bytesDone >= retained)
            assertNotNull(repository.details(id))
            repository.delete(id)
            withTimeout(20_000) { repository.items.first { all -> all.none { it.videoId == id } } }
            assertFalse(File(context.filesDir, "downloads/$id").exists())
            val notifications = context.getSystemService(android.app.NotificationManager::class.java)
            withTimeout(2000) { while (notifications.activeNotifications.any { it.id == "complete-$id".hashCode() }) delay(20) }
            assertFalse("Deletion must remove the stale offline notification", notifications.activeNotifications.any { it.id == "complete-$id".hashCode() })
        } finally { settings.setDownloadQuality(original) }
    }

}
