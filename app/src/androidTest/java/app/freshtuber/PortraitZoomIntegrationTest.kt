package app.freshtuber

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
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

class PortraitZoomIntegrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pinchPanAndSingleTapsNeverPauseOrMinimize() {
        var pauses = 0
        var minimizes = 0
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, method, args ->
            when (method.name) {
                "isPlaying", "getPlayWhenReady" -> true
                "getPlaybackState" -> Player.STATE_READY
                "getCurrentPosition" -> 0L
                "getDuration", "getBufferedPosition" -> 60_000L
                "pause" -> { pauses++; null }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                "toString" -> "Gesture test player"
                else -> null
            }
        } as Player
        val zoom = VideoZoom()
        compose.setContent {
            val pull = rememberMinimizeDrag()
            Box(Modifier.size(320.dp, 180.dp).onSizeChanged { pull.heightPx = it.height * 4f }.testTag("video")
                .minimizeDrag(pull, enabled = true, onMinimize = { minimizes++ }).videoZoom(zoom, true, 16f / 9f)) {
                PortraitVideoLayer(player, PlayerState())
            }
        }
        compose.onNodeWithTag("video").performTouchInput {
            down(0, Offset(width * 0.4f, height * 0.5f))
            down(1, Offset(width * 0.6f, height * 0.5f))
            moveTo(0, Offset(width * 0.2f, height * 0.5f))
            moveTo(1, Offset(width * 0.8f, height * 0.5f))
            up(0); up(1)
        }
        compose.runOnIdle { assertTrue(zoom.scale > 1f); assertEquals(0, pauses); assertEquals(0, minimizes) }
        compose.onNodeWithTag("video").performTouchInput { swipe(center, center + Offset(0f, height * 0.4f)) }
        compose.runOnIdle { assertEquals(0, pauses); assertEquals(0, minimizes) }
        compose.onNodeWithTag("video").performTouchInput { click(center) }
        compose.onNodeWithTag("video").performTouchInput { click(Offset(width * 0.15f, height * 0.5f)) }
        Thread.sleep(400)
        compose.onNodeWithTag("video").performTouchInput { click(Offset(width * 0.85f, height * 0.5f)) }
        Thread.sleep(400)
        compose.runOnIdle { assertEquals(0, pauses) }
    }

    @Test fun pullingTheUnzoomedVideoDownMinimizesAndAShortSlowPullDoesNot() {
        var minimizes = 0
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, method, args ->
            when (method.name) {
                "isPlaying", "getPlayWhenReady" -> false
                "getPlaybackState" -> Player.STATE_READY
                "getCurrentPosition" -> 0L
                "getDuration", "getBufferedPosition" -> 60_000L
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> null
            }
        } as Player
        lateinit var pull: MinimizeDrag
        compose.setContent {
            pull = rememberMinimizeDrag()
            Box(Modifier.size(320.dp, 600.dp).onSizeChanged { pull.heightPx = it.height.toFloat() }.testTag("video")
                .minimizeDrag(pull, enabled = true, onMinimize = { minimizes++ })) {
                PortraitVideoLayer(player, PlayerState())
            }
        }
        // A slow pull of a tenth of the page springs back.
        compose.onNodeWithTag("video").performTouchInput { swipeDown(height * 0.2f, height * 0.3f, durationMillis = 1200) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, minimizes); assertEquals(0f, pull.offsetPx, 0.5f) }
        // Past a quarter of the page it drops into the mini-player.
        compose.onNodeWithTag("video").performTouchInput { swipeDown(height * 0.1f, height * 0.5f, durationMillis = 1200) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, minimizes) }
    }
}
