package app.freshtuber

import android.content.ComponentName
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.lifecycle.Lifecycle
import app.freshtuber.background.BackgroundDependencies
import app.freshtuber.data.downloads.DownloadStatus
import app.freshtuber.data.settings.*
import app.freshtuber.domain.model.VideoSummary
import app.freshtuber.player.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BackgroundPlaybackIntegrationTest {
    @Test fun backgroundSettingPausesOnlyWhenDisabled() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val settings = AppSettings(context)
        val original = settings.preferences.first()
        val id = "dQw4w9WgXcQ"
        val repository = EntryPointAccessors.fromApplication(context.applicationContext, BackgroundDependencies::class.java).downloads()
        fun foreground() = context.startActivity(Intent(context, MainActivity::class.java).putExtra("videoId", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        foreground()
        delay(1000)
        repository.enqueue(VideoSummary(id, "Background playback test"))
        val result = withTimeout(180_000) { repository.items.first { all -> all.any { it.videoId == id && it.state in listOf(DownloadStatus.Complete, DownloadStatus.Failed) } }.first { it.videoId == id } }
        assertEquals(DownloadStatus.Complete, result.state)
        var future: ListenableFuture<MediaController>? = null
        instrumentation.runOnMainSync { future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync() }
        val controller = future!!.get(10, TimeUnit.SECONDS)
        fun playing(): Boolean { var value = false; instrumentation.runOnMainSync { value = controller.isPlaying }; return value }
        suspend fun visibleActivity(): MainActivity = withTimeout(10_000) {
            var activity: MainActivity? = null
            while (activity == null) {
                instrumentation.runOnMainSync { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull() }
                if (activity == null) delay(50)
            }
            activity!!
        }
        suspend fun goHome(activity: MainActivity) {
            instrumentation.waitForIdleSync()
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent 3")).use { it.readBytes() }
            withTimeout(10_000) {
                var visible = true
                while (visible) {
                    instrumentation.runOnMainSync { visible = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
                    if (visible) delay(50)
                }
            }
        }
        try {
            settings.setFeature(AppFeature.PictureInPicture, false)
            settings.setFeature(AppFeature.BackgroundPlayback, true)
            foreground()
            instrumentation.runOnMainSync { controller.play() }
            withTimeout(25_000) { while (!playing()) delay(200) }
            goHome(visibleActivity())
            assertTrue("Enabled background playback must continue", playing())
            foreground()
            settings.setFeature(AppFeature.BackgroundPlayback, false)
            goHome(visibleActivity())
            withTimeout(5000) { while (playing()) delay(50) }
            assertFalse("Disabled background playback must pause", playing())
        } finally {
            settings.setFeature(AppFeature.PictureInPicture, original.enabled(AppFeature.PictureInPicture))
            settings.setFeature(AppFeature.BackgroundPlayback, original.enabled(AppFeature.BackgroundPlayback))
            instrumentation.runOnMainSync { controller.pause(); controller.release() }
        }
    }
}
