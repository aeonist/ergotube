package app.freshtuber

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.common.Player
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.freshtuber.data.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MiniPlayerContinuityIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun returningFromHomeKeepsTheMediaSourceAndPosition(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation(); val context = instrumentation.targetContext
        val settings = AppSettings(context); val original = settings.preferences.first()
        settings.markWelcomeDone(); settings.setFeature(AppFeature.LiquidFlat, true); settings.setFeature(AppFeature.Autoplay, false)
        settings.setFeature(AppFeature.MiniPlayerTapOpens, true)
        var activity: MainActivity? = null
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("videoId", "dQw4w9WgXcQ")).use { scenario ->
                scenario.onActivity { activity = it }
                compose.waitForIdle()
                withTimeout(60_000) { while (true) {
                    var ready = false; instrumentation.runOnMainSync { ready = activity!!.playerController.player.playbackState == Player.STATE_READY }
                    if (ready) break; delay(100)
                } }
                var transitions = 0
                val listener = object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { transitions++ }
                }
                var before = 0L
                instrumentation.runOnMainSync { activity!!.playerController.player.addListener(listener); before = activity!!.playerController.player.currentPosition }
                instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").close()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("mini").fetchSemanticsNodes().isNotEmpty() }
                Thread.sleep(1_000)
                compose.onNodeWithTag("mini").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.25f)) }
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("video:time").fetchSemanticsNodes().isNotEmpty() }
                Thread.sleep(800)
                instrumentation.runOnMainSync {
                    val player = activity!!.playerController.player
                    assertEquals("dQw4w9WgXcQ", player.currentMediaItem?.mediaId)
                    assertEquals("A mini-player return must not prepare a new source", 0, transitions)
                    assertTrue(player.currentPosition >= before - 500)
                    assertNull(activity!!.playerController.state.value.loadingId)
                    assertNull(activity!!.playerController.state.value.error)
                    player.removeListener(listener)
                }
                compose.onNodeWithTag("video:time").assertExists()
                compose.onAllNodesWithTag("pad:time").assertCountEquals(0)
                compose.onNodeWithTag("player:track").performTouchInput { down(center); moveTo(centerRight) }
                compose.onNodeWithTag("pad:time").assertExists()
                compose.onNodeWithTag("video:time").assertExists()
                compose.onNodeWithTag("player:track").performTouchInput { up() }
                compose.waitUntil(5_000) { compose.onAllNodesWithTag("pad:time").fetchSemanticsNodes().isEmpty() }
                compose.onAllNodesWithTag("pad:time").assertCountEquals(0)
                compose.onNodeWithTag("video:time").assertExists()
            }
        } finally {
            instrumentation.runOnMainSync { activity?.playerController?.stop() }
            settings.setFeature(AppFeature.LiquidFlat, original.enabled(AppFeature.LiquidFlat)); settings.setFeature(AppFeature.Autoplay, original.enabled(AppFeature.Autoplay))
            settings.setFeature(AppFeature.MiniPlayerTapOpens, original.enabled(AppFeature.MiniPlayerTapOpens))
        }
    }
}
