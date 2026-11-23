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
                    onExit = {}, onNext = {}, onQuality = {}, onSpeed = {}, onCaptions = {}, onBoost = { boosts++ })
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
    /** A 16:9 picture on a wider screen sticks to the left and right edges once the pinch gets near them. */
    @Test fun pinchNearTheEdgesSticksThereAndAWiderPinchLeaves() {
        var sticks = 0
        val zoom = VideoZoom()
        compose.setContent {
            // 400 × 180: the fitted picture is 320 wide, so it fills the width at 1.25×.
            Box(Modifier.size(400.dp, 180.dp).testTag("wide").videoZoom(zoom, true, 16f / 9f, onStick = { sticks++ }))
        }
        compose.onNodeWithTag("wide").performTouchInput {
            down(0, Offset(width * 0.4f, height * 0.5f)); down(1, Offset(width * 0.6f, height * 0.5f))
            // 1.3× apart: a little past the fill scale, still inside the hold.
            moveTo(0, Offset(width * 0.37f, height * 0.5f)); moveTo(1, Offset(width * 0.63f, height * 0.5f))
            up(0); up(1)
        }
        // Pixel rounding of the box moves the fill scale a little off 1.25, so compare with the real size.
        val px = compose.onNodeWithTag("wide").fetchSemanticsNode().size
        val fill = VideoZoom.fillScale(Offset(px.width.toFloat(), px.height.toFloat()), 16f / 9f)
        compose.runOnIdle {
            assertEquals(1.25f, fill, 0.01f); assertEquals(fill, zoom.scale, 0.001f); assertEquals(0f, zoom.offset.x, 0.001f)
            assertTrue(zoom.filled); assertEquals(1, sticks)
        }
        compose.onNodeWithTag("wide").performTouchInput {
            down(0, Offset(width * 0.4f, height * 0.5f)); down(1, Offset(width * 0.6f, height * 0.5f))
            moveTo(0, Offset(width * 0.35f, height * 0.5f)); moveTo(1, Offset(width * 0.65f, height * 0.5f))
            up(0); up(1)
        }
        compose.runOnIdle { assertTrue(zoom.scale > 1.5f); assertFalse(zoom.filled); assertEquals(1, sticks) }
    }
}
