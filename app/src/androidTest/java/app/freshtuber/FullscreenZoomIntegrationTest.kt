package app.freshtuber

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.freshtuber.player.PlayerState
import app.freshtuber.ui.player.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

class FullscreenZoomIntegrationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun pinchOverTheFanDoesNotSeekPauseOrBoost() {
        var pauses = 0; var seeks = 0; var boosts = 0
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, method, args -> when (method.name) {
            "isPlaying", "getPlayWhenReady" -> true
            "getPlaybackState" -> Player.STATE_READY
            "getCurrentPosition" -> 30_000L
            "getDuration", "getBufferedPosition" -> 60_000L
            "pause" -> { pauses++; null }
            "seekTo" -> { seeks++; null }
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.get(0)
            "toString" -> "Fullscreen test player"
            else -> null
        } } as Player
        val zoom = VideoZoom(); val ui = PadUi()
        compose.setContent {
            Box(Modifier.size(360.dp, 540.dp).testTag("full").videoZoom(zoom, true, 16f / 9f)) {
                FullscreenPlayer(player, PlayerState(), ui, StoryboardPages(), left = true, gesturesEnabled = true,
                    captionsOn = false, captionsAvailable = false, hasNext = false, showRotateHint = false,
                    onExit = {}, onNext = {}, onQuality = {}, onSpeed = {}, onCaptions = {}, onBoost = { boosts++ }, onRetry = {})
            }
        }
        compose.onNodeWithTag("full").performTouchInput {
            down(0, Offset(width * 0.25f, height * 0.70f)); down(1, Offset(width * 0.36f, height * 0.80f))
            moveTo(0, Offset(width * 0.12f, height * 0.55f)); moveTo(1, Offset(width * 0.65f, height * 0.85f))
            up(0); up(1)
        }
        compose.runOnIdle {
            assertTrue(zoom.scale > 1f); assertEquals(0, pauses); assertEquals(0, seeks); assertEquals(0, boosts); assertNull(ui.scrubMs)
        }
        compose.onNodeWithTag("full").performTouchInput { swipe(Offset(width * 0.5f, height * 0.15f), Offset(width * 0.55f, height * 0.25f)) }
        compose.runOnIdle { assertEquals(0, pauses); assertEquals(0, seeks) }
    }
}
