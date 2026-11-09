package app.freshtuber

import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.player.PlaybackService
import app.freshtuber.data.settings.AppFeature
import app.freshtuber.data.settings.AppSettings
import app.freshtuber.data.settings.UiStyle
import app.freshtuber.domain.model.VideoSummary
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class FullscreenNavigationIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun nextAndSystemPreviousKeepFullscreen(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val settings = AppSettings(context)
        val original = settings.preferences.first()
        val first = "dQw4w9WgXcQ"
        val second = "jNQXAC9IVRw"
        val third = "aqz-KE-bpKQ"
        settings.markWelcomeDone()
        settings.setFeature(AppFeature.LiquidFlat, true)
        settings.setStyle(UiStyle.Ergonomic)
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("videoId", first)).use { scenario ->
                var future: ListenableFuture<MediaController>? = null
                scenario.onActivity {
                    val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                    future = MediaController.Builder(context, token).buildAsync()
                }
                val media = checkNotNull(future).get(10, TimeUnit.SECONDS)
                fun waitForVideo(id: String) = compose.waitUntil(35_000) {
                    var matches = false
                    scenario.onActivity { matches = media.currentMediaItem?.mediaId == id }
                    matches
                }
                fun node(label: Int) = compose.onNodeWithContentDescription(context.getString(label))
                try {
                    waitForVideo(first)
                    compose.waitUntil(10_000) {
                        compose.onAllNodesWithContentDescription(context.getString(R.string.player_fullscreen)).fetchSemanticsNodes().size == 1
                    }
                    node(R.string.player_fullscreen).performClick()
                    node(R.string.player_exit_fullscreen).assertExists()
                    for (id in listOf(second, third)) {
                        scenario.onActivity { it.playerController.setQueue(listOf(VideoSummary(id, id))) }
                        node(R.string.player_next).performClick()
                        waitForVideo(id)
                        node(R.string.player_fullscreen).assertDoesNotExist()
                        node(R.string.player_exit_fullscreen).assertExists()
                    }
                    scenario.onActivity { media.seekToPrevious() }
                    waitForVideo(second)
                    scenario.onActivity {
                        assertEquals(listOf(first), it.playerController.state.value.history)
                    }
                    node(R.string.player_exit_fullscreen).assertExists()
                } finally {
                    scenario.onActivity { it.playerController.stop(); media.release() }
                }
            }
        } finally {
            settings.setFeature(AppFeature.LiquidFlat, original.enabled(AppFeature.LiquidFlat))
            settings.setStyle(original.style)
        }
    }
}

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
