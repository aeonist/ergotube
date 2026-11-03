package app.freshtuber

import android.content.ComponentName
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.player.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlayerNavigationIntegrationTest {
    @Test fun notificationIntentSwitchesToAnotherVideoOnAnOpenPlayer() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun open(id: String) = context.startActivity(Intent(context, MainActivity::class.java).putExtra("videoId", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        open("dQw4w9WgXcQ")
        var future: ListenableFuture<MediaController>? = null
        instrumentation.runOnMainSync { future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync() }
        val controller = future!!.get(10, TimeUnit.SECONDS)
        fun current(): String? { var id: String? = null; instrumentation.runOnMainSync { id = controller.currentMediaItem?.mediaId }; return id }
        try {
            withTimeout(45_000) { while (current() != "dQw4w9WgXcQ") delay(100) }
            open("jNQXAC9IVRw")
            withTimeout(45_000) { while (current() != "jNQXAC9IVRw") delay(100) }
            assertEquals("jNQXAC9IVRw", current())
            open("jNQXAC9IVRw")
            delay(500)
            assertEquals("Repeating the same notification must retain the current video", "jNQXAC9IVRw", current())
        } finally { instrumentation.runOnMainSync { controller.pause(); controller.release() } }
    }
}
